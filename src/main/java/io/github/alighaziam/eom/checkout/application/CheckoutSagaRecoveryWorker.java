package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.checkout.CheckoutStatus;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.payment.PaymentIntent;
import io.github.alighaziam.eom.payment.PaymentOperations;
import io.github.alighaziam.eom.shipping.ShippingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class CheckoutSagaRecoveryWorker {

    private final CheckoutSagaClaimService claims;
    private final CheckoutWorkflowStateService states;
    private final CheckoutFulfillmentStateService fulfillmentStates;
    private final CheckoutWorkflowRepository workflows;
    private final OrderOperations orders;
    private final PaymentOperations payments;
    private final ShippingOperations shipping;

    public CheckoutSagaRecoveryWorker(CheckoutSagaClaimService claims,
                                     CheckoutWorkflowStateService states,
                                     CheckoutFulfillmentStateService fulfillmentStates,
                                     CheckoutWorkflowRepository workflows,
                                     OrderOperations orders,
                                     PaymentOperations payments,
                                     ShippingOperations shipping) {
        this.claims = claims;
        this.states = states;
        this.fulfillmentStates = fulfillmentStates;
        this.workflows = workflows;
        this.orders = orders;
        this.payments = payments;
        this.shipping = shipping;
    }

    @Scheduled(fixedDelayString = "${app.checkout.saga-recovery-poll-ms:5000}")
    public void recoverStaleSagaStages() {
        Instant now = Instant.now();
        for (var claim : claims.claimBatch(now, 20)) {
            try {
                recover(claim);
            } catch (RuntimeException failure) {
                states.releaseSagaClaim(claim.workflowId(), claim.workerId(),
                        failure.getMessage(), Instant.now());
            }
        }
    }

    private void recover(CheckoutSagaClaimService.ClaimedSaga claim) {
        PaymentIntent payment;
        if (claim.status() == CheckoutStatus.ORDER_RESERVED) {
            var order = orders.orderDetails(claim.orderId())
                    .orElseThrow(() -> new IllegalStateException("Reserved checkout order no longer exists."));
            payment = payments.authorize(claim.orderId(), order.totalAmount(), order.currency(),
                    paymentIdempotencyKey(claim.idempotencyKey()));
            if (!states.recordSagaPayment(claim.workflowId(), claim.workerId(), payment.id(),
                    CheckoutStatus.PAYMENT_AUTHORIZED, Instant.now())) return;
        } else {
            if (claim.paymentId() == null) {
                throw new IllegalStateException("Checkout payment checkpoint is missing its payment ID.");
            }
            payment = payments.get(claim.paymentId());
        }

        if (claim.status() == CheckoutStatus.ORDER_RESERVED
                || claim.status() == CheckoutStatus.PAYMENT_AUTHORIZED) {
            payment = payments.capture(payment.id());
            if (!states.recordSagaPayment(claim.workflowId(), claim.workerId(), payment.id(),
                    CheckoutStatus.PAYMENT_CAPTURED, Instant.now())) return;
        }

        orders.confirmForCheckout(claim.orderId());
        if (!states.recordSagaStatus(claim.workflowId(), claim.workerId(),
                CheckoutStatus.ORDER_CONFIRMED, Instant.now())) return;

        var workflow = workflows.findById(claim.workflowId())
                .orElseThrow(() -> new IllegalStateException("Checkout workflow disappeared during recovery"));
        var shipment = shipping.create(claim.orderId(), workflow.getDeliveryAddress());
        fulfillmentStates.complete(claim.workflowId(), claim.workerId(), shipment.id(), Instant.now());
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
}
