package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.checkout.CheckoutOperations;
import io.github.alighaziam.eom.checkout.CheckoutStatus;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowEntity;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.payment.PaymentIntent;
import io.github.alighaziam.eom.payment.PaymentOperations;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import io.github.alighaziam.eom.shipping.ShippingOperations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CheckoutService implements CheckoutOperations {

    private final OrderOperations orders;
    private final PaymentOperations payments;
    private final ShippingOperations shipping;
    private final CheckoutWorkflowRepository workflows;
    private final CheckoutReservationService reservations;
    private final CheckoutWorkflowStateService states;

    public CheckoutService(OrderOperations orders,
                           PaymentOperations payments,
                           ShippingOperations shipping,
                           CheckoutWorkflowRepository workflows,
                           CheckoutReservationService reservations,
                           CheckoutWorkflowStateService states) {
        this.orders = orders;
        this.payments = payments;
        this.shipping = shipping;
        this.workflows = workflows;
        this.reservations = reservations;
        this.states = states;
    }

    @Override
    public CheckoutResult checkout(CheckoutRequest request) {
        validate(request);
        request = normalize(request);

        CheckoutReservationService.ReservationResult reservation;
        try {
            reservation = reservations.reserve(request);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            CheckoutWorkflowEntity winner = workflows.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(() -> concurrentDuplicate);
            reservations.validateReplay(winner, request);
            return winner.toResult();
        }

        CheckoutWorkflowEntity workflow = reservation.workflow();
        if (!reservation.created()) {
            return workflow.toResult();
        }

        UUID workflowId = workflow.getId();
        UUID orderId = workflow.getOrderId();
        PaymentIntent payment = null;

        try {
            payment = payments.authorize(orderId, reservation.order().totalAmount(),
                    reservation.order().currency(), paymentIdempotencyKey(request.idempotencyKey()));
            states.recordPayment(workflowId, payment.id(), CheckoutStatus.PAYMENT_AUTHORIZED, Instant.now());

            payment = payments.capture(payment.id());
            states.recordPayment(workflowId, payment.id(), CheckoutStatus.PAYMENT_CAPTURED, Instant.now());
        } catch (RuntimeException paymentFailure) {
            compensatePaymentFailure(workflowId, orderId, payment, paymentFailure);
            return get(workflowId);
        }

        try {
            orders.confirmForCheckout(orderId);
            states.recordStatus(workflowId, CheckoutStatus.ORDER_CONFIRMED, null, Instant.now());
        } catch (RuntimeException confirmationFailure) {
            try {
                payments.refund(payment.id());
                orders.cancelForCheckout(orderId);
                states.recordStatus(workflowId, CheckoutStatus.FAILED,
                        "Order confirmation failed; payment refunded. " + confirmationFailure.getMessage(),
                        Instant.now());
            } catch (RuntimeException compensationFailure) {
                states.recordStatus(workflowId, CheckoutStatus.COMPENSATION_REQUIRED,
                        "Order confirmation: " + confirmationFailure.getMessage()
                                + "; compensation: " + compensationFailure.getMessage(),
                        Instant.now());
            }
            return get(workflowId);
        }

        try {
            var shipment = shipping.create(orderId, request.deliveryAddress());
            return states.recordShipment(workflowId, shipment.id(), Instant.now()).toResult();
        } catch (RuntimeException fulfillmentFailure) {
            return states.scheduleFulfillmentRetry(workflowId, fulfillmentFailure.getMessage(),
                    Instant.now()).toResult();
        }
    }

    private void compensatePaymentFailure(UUID workflowId, UUID orderId,
                                          PaymentIntent payment, RuntimeException failure) {
        try {
            if (payment != null) {
                if (payment.status() == PaymentStatus.AUTHORIZED) {
                    payments.voidAuthorization(payment.id());
                } else if (payment.status() == PaymentStatus.CAPTURED
                        || payment.status() == PaymentStatus.REFUND_PENDING) {
                    payments.refund(payment.id());
                } else if (payment.status() != PaymentStatus.VOIDED
                        && payment.status() != PaymentStatus.REFUNDED
                        && payment.status() != PaymentStatus.FAILED) {
                    throw new IllegalStateException("Payment is " + payment.status()
                            + " and cannot be compensated automatically.");
                }
            }
            orders.cancelForCheckout(orderId);
            states.recordStatus(workflowId, CheckoutStatus.FAILED, failure.getMessage(), Instant.now());
        } catch (RuntimeException compensationFailure) {
            states.recordStatus(workflowId, CheckoutStatus.COMPENSATION_REQUIRED,
                    "Payment step: " + failure.getMessage()
                            + "; compensation: " + compensationFailure.getMessage(),
                    Instant.now());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutResult get(UUID id) {
        return workflows.findById(id).orElseThrow(() -> new CheckoutNotFoundException(id)).toResult();
    }

    @Transactional(readOnly = true)
    public List<CheckoutResult> list() {
        return workflows.findTop40ByOrderByUpdatedAtDesc().stream()
                .map(CheckoutWorkflowEntity::toResult).toList();
    }

    @Transactional(readOnly = true)
    public Page<CheckoutResult> search(String query, String status, int page, int size) {
        CheckoutStatus parsed = status == null || status.isBlank() ? null : CheckoutStatus.valueOf(status);
        return workflows.search(query == null || query.isBlank() ? null : query.trim(), parsed,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(CheckoutWorkflowEntity::toResult);
    }

    @Override
    @Transactional(readOnly = true)
    public long compensationRequiredCount() {
        return workflows.countByStatus(CheckoutStatus.COMPENSATION_REQUIRED);
    }

    @Override
    public CheckoutResult reconcile(UUID id) {
        CheckoutWorkflowEntity workflow = workflows.findById(id)
                .orElseThrow(() -> new CheckoutNotFoundException(id));
        if (workflow.getStatus() != CheckoutStatus.COMPENSATION_REQUIRED) {
            throw new IllegalStateException("Only a workflow that needs compensation can be reconciled.");
        }

        try {
            compensate(workflow);
            return states.recordStatus(id, CheckoutStatus.FAILED,
                    "Compensation completed.", Instant.now()).toResult();
        } catch (RuntimeException exception) {
            states.recordStatus(id, CheckoutStatus.COMPENSATION_REQUIRED,
                    exception.getMessage(), Instant.now());
            throw exception;
        }
    }

    private void compensate(CheckoutWorkflowEntity workflow) {
        PaymentIntent payment = workflow.getPaymentId() == null
                ? null : payments.get(workflow.getPaymentId());
        OrderState order = orders.state(workflow.getOrderId());

        if (order == OrderState.CONFIRMED && payment != null
                && (payment.status() == PaymentStatus.CAPTURED
                || payment.status() == PaymentStatus.REFUND_PENDING)) {
            throw new IllegalStateException("The order is confirmed and the payment is captured. "
                    + "This does not undo a committed sale; continue fulfillment or refund the payment explicitly.");
        }

        if (payment != null && payment.status() == PaymentStatus.AUTHORIZED) {
            payments.voidAuthorization(payment.id());
        } else if (payment != null && payment.status() == PaymentStatus.CAPTURED) {
            payments.refund(payment.id());
        } else if (payment != null && payment.status() != PaymentStatus.VOIDED
                && payment.status() != PaymentStatus.REFUNDED
                && payment.status() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Payment is " + payment.status()
                    + " and cannot be compensated automatically.");
        }

        if (order == OrderState.RESERVED) {
            orders.cancelForCheckout(workflow.getOrderId());
        } else if (order == OrderState.CONFIRMED) {
            throw new IllegalStateException("The order is confirmed. Inspect it before releasing stock.");
        }
    }

    private static String paymentIdempotencyKey(String checkoutKey) {
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(checkoutKey.getBytes(StandardCharsets.UTF_8)));
            return "checkout:" + digest;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void validate(CheckoutRequest request) {
        if (request == null || request.customerId() == null
                || request.lines() == null || request.lines().isEmpty()
                || request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                || request.idempotencyKey().trim().length() > 120) {
            throw new IllegalArgumentException(
                    "Customer, order lines, delivery address, and idempotency key are required.");
        }

        if (request.lines().stream().anyMatch(line -> line == null
                || line.productId() == null || line.quantity() <= 0)) {
            throw new IllegalArgumentException("Every order line requires a product and a positive quantity.");
        }

        DeliveryAddress address = request.deliveryAddress();
        if (address == null || blank(address.recipientName()) || blank(address.addressLine1())
                || blank(address.city()) || address.countryCode() == null
                || !address.countryCode().trim().matches("[A-Za-z]{2}")
                || tooLong(address.recipientName(), 180) || tooLong(address.addressLine1(), 240)
                || tooLong(address.addressLine2(), 240) || tooLong(address.city(), 120)
                || tooLong(address.postalCode(), 40) || tooLong(address.phone(), 40)) {
            throw new IllegalArgumentException("Delivery address fields are missing, invalid, or too long.");
        }
    }

    private static CheckoutRequest normalize(CheckoutRequest request) {
        DeliveryAddress address = request.deliveryAddress();
        DeliveryAddress normalizedAddress = new DeliveryAddress(
                address.recipientName().trim(),
                address.addressLine1().trim(),
                trimToNull(address.addressLine2()),
                address.city().trim(),
                trimToNull(address.postalCode()),
                address.countryCode().trim().toUpperCase(Locale.ROOT),
                trimToNull(address.phone()));
        return new CheckoutRequest(request.customerId(), request.lines(), normalizedAddress,
                request.idempotencyKey().trim());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean tooLong(String value, int maxLength) {
        return value != null && value.trim().length() > maxLength;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
