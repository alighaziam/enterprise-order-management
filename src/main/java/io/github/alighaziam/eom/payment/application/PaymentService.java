package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import io.github.alighaziam.eom.payment.PaymentIntent;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.github.alighaziam.eom.payment.PaymentOperationType;
import io.github.alighaziam.eom.payment.PaymentOperations;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import io.github.alighaziam.eom.payment.infrastructure.PaymentIntentEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService implements PaymentOperations {

    private static final List<PaymentStatus> ACTIVE_STATUSES = List.of(
            PaymentStatus.REQUESTED,
            PaymentStatus.AUTHORIZED,
            PaymentStatus.CAPTURED,
            PaymentStatus.REFUND_PENDING);

    private final PaymentRepository repository;
    private final PaymentGatewayOperationRepository gatewayOperations;
    private final PaymentGateway gateway;
    private final OutboxOperations outbox;
    private final OrderOperations orders;

    @Value("${app.payment.command-execution.enabled:false}")
    private boolean splitPhaseEnabled;

    public PaymentService(PaymentRepository repository,
                          PaymentGatewayOperationRepository gatewayOperations,
                          PaymentGateway gateway,
                          OutboxOperations outbox,
                          OrderOperations orders) {
        this.repository = repository;
        this.gatewayOperations = gatewayOperations;
        this.gateway = gateway;
        this.outbox = outbox;
        this.orders = orders;
    }

    @Override
    @Transactional
    public PaymentIntent authorize(UUID orderId, BigDecimal amount, String currency, String idempotencyKey) {
        validateAuthorizationRequest(orderId, amount, currency, idempotencyKey);

        var replay = repository.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return validateReplay(replay.get(), orderId, amount, currency);
        }

        OrderOperations.OrderDetails order = orders.orderDetails(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order does not exist: " + orderId));
        if (order.state() != OrderState.RESERVED) {
            throw new IllegalStateException("Payment can only be authorized for a reserved order.");
        }
        if (order.totalAmount().compareTo(amount) != 0 || !order.currency().equals(currency)) {
            throw new IllegalArgumentException("Payment amount and currency must match the order total.");
        }

        var replayAfterOrderLock = repository.findByIdempotencyKey(idempotencyKey);
        if (replayAfterOrderLock.isPresent()) {
            return validateReplay(replayAfterOrderLock.get(), orderId, amount, currency);
        }

        if (repository.existsByOrderIdAndStatusIn(orderId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("An active payment already exists for this order.");
        }

        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        int inserted;
        try {
            inserted = repository.insertIfAbsent(paymentId, orderId, amount, currency, idempotencyKey, now);
        } catch (DataIntegrityViolationException activePaymentConflict) {
            throw new IllegalStateException("An active payment already exists for this order.",
                    activePaymentConflict);
        }

        if (inserted == 0) {
            PaymentIntentEntity existing = repository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment idempotency claim could not be resolved."));
            return validateReplay(existing, orderId, amount, currency);
        }

        PaymentIntentEntity entity = repository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment intent reservation was not persisted."));

        if (splitPhaseEnabled) {
            enqueueOperation(entity, PaymentOperationType.AUTHORIZE, idempotencyKey, now);
            return entity.toDomain();
        }

        String providerReference = gateway.authorize(paymentId, amount, currency, idempotencyKey);
        entity.authorized(providerReference, Instant.now());
        PaymentIntentEntity saved = repository.saveAndFlush(entity);
        outbox.append("PaymentAuthorized", "PaymentIntent", paymentId.toString(), 1,
                orderId.toString(), null, "{\"paymentId\":\"" + paymentId + "\"}");
        return saved.toDomain();
    }

    @Override
    @Transactional
    public PaymentIntent capture(UUID id) {
        PaymentIntentEntity entity = findForUpdate(id);
        PaymentIntent payment = entity.toDomain();
        if (payment.status() == PaymentStatus.CAPTURED) return payment;
        if (payment.status() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Only authorized payments can be captured.");
        }

        if (splitPhaseEnabled) {
            if (hasExistingOperation(id, PaymentOperationType.CAPTURE)) return entity.toDomain();
            enqueueOperation(entity, PaymentOperationType.CAPTURE, operationKey(id, PaymentOperationType.CAPTURE),
                    Instant.now());
            return entity.toDomain();
        }

        gateway.capture(payment.providerReference(), operationKey(id, PaymentOperationType.CAPTURE));
        entity.status(PaymentStatus.CAPTURED, Instant.now());
        outbox.append("PaymentCaptured", "PaymentIntent", id.toString(), 2,
                payment.orderId().toString(), null, "{\"paymentId\":\"" + id + "\"}");
        return entity.toDomain();
    }

    @Override
    @Transactional
    public PaymentIntent voidAuthorization(UUID id) {
        PaymentIntentEntity entity = findForUpdate(id);
        PaymentIntent payment = entity.toDomain();
        if (payment.status() == PaymentStatus.VOIDED) return payment;
        if (payment.status() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Only authorized payments can be voided.");
        }

        if (splitPhaseEnabled) {
            if (hasExistingOperation(id, PaymentOperationType.VOID)) return entity.toDomain();
            enqueueOperation(entity, PaymentOperationType.VOID, operationKey(id, PaymentOperationType.VOID),
                    Instant.now());
            return entity.toDomain();
        }

        gateway.voidAuthorization(payment.providerReference(), operationKey(id, PaymentOperationType.VOID));
        entity.status(PaymentStatus.VOIDED, Instant.now());
        outbox.append("PaymentVoided", "PaymentIntent", id.toString(), 3,
                payment.orderId().toString(), null, "{\"paymentId\":\"" + id + "\"}");
        return entity.toDomain();
    }

    @Override
    @Transactional
    public PaymentIntent refund(UUID id) {
        PaymentIntentEntity entity = findForUpdate(id);
        PaymentIntent payment = entity.toDomain();
        if (payment.status() == PaymentStatus.REFUNDED) return payment;

        if (splitPhaseEnabled) {
            if (payment.status() == PaymentStatus.REFUND_PENDING
                    && hasExistingOperation(id, PaymentOperationType.REFUND)) return entity.toDomain();
            if (payment.status() != PaymentStatus.CAPTURED) {
                throw new IllegalStateException("Only captured payments can be refunded.");
            }
            Instant now = Instant.now();
            entity.status(PaymentStatus.REFUND_PENDING, now);
            enqueueOperation(entity, PaymentOperationType.REFUND, operationKey(id, PaymentOperationType.REFUND), now);
            return entity.toDomain();
        }

        if (payment.status() != PaymentStatus.CAPTURED) {
            throw new IllegalStateException("Only captured payments can be refunded.");
        }
        entity.status(PaymentStatus.REFUND_PENDING, Instant.now());
        gateway.refund(payment.providerReference(), operationKey(id, PaymentOperationType.REFUND));
        entity.status(PaymentStatus.REFUNDED, Instant.now());
        outbox.append("PaymentRefunded", "PaymentIntent", id.toString(), 4,
                payment.orderId().toString(), null, "{\"paymentId\":\"" + id + "\"}");
        return entity.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentIntent get(UUID id) {
        return find(id).toDomain();
    }

    @Transactional(readOnly = true)
    public List<PaymentIntent> list() {
        return repository.findTop40ByOrderByCreatedAtDesc().stream()
                .map(PaymentIntentEntity::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public Page<PaymentIntent> search(String query, String status, int page, int size) {
        PaymentStatus parsed = status == null || status.isBlank() ? null : PaymentStatus.valueOf(status);
        return repository.search(query == null || query.isBlank() ? null : query.trim(), parsed,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(PaymentIntentEntity::toDomain);
    }

    private void enqueueOperation(PaymentIntentEntity payment, PaymentOperationType type,
                                  String idempotencyKey, Instant now) {
        PaymentIntent snapshot = payment.toDomain();
        String fingerprint = fingerprint(type + "|" + snapshot.id() + "|" + snapshot.amount().toPlainString()
                + "|" + snapshot.currency() + "|" + String.valueOf(snapshot.providerReference()));
        gatewayOperations.saveAndFlush(new PaymentGatewayOperationEntity(
                UUID.randomUUID(), snapshot.id(), type, idempotencyKey, fingerprint, now));
    }

    private boolean hasExistingOperation(UUID paymentId, PaymentOperationType type) {
        var existing = gatewayOperations.findByPaymentIdAndOperationType(paymentId, type);
        if (existing.isEmpty()) return false;
        PaymentOperationStatus status = existing.get().getStatus();
        if (status == PaymentOperationStatus.FAILED) {
            throw new IllegalStateException("A previous payment operation failed; manual review is required.");
        }
        return true;
    }

    private static String operationKey(UUID paymentId, PaymentOperationType type) {
        return paymentId + ":" + type.name();
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private static void validateAuthorizationRequest(UUID orderId, BigDecimal amount,
                                                     String currency, String idempotencyKey) {
        if (orderId == null || amount == null || amount.signum() <= 0
                || !fitsDatabaseAmount(amount)
                || currency == null || !currency.matches("[A-Z]{3}")
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
            throw new IllegalArgumentException(
                    "Order, positive amount with at most 15 integer and 4 fractional digits, ISO currency, and idempotency key are required.");
        }
    }

    private static boolean fitsDatabaseAmount(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        int integerDigits = normalized.precision() - normalized.scale();
        return normalized.scale() <= 4 && integerDigits <= 15;
    }

    private PaymentIntent validateReplay(PaymentIntentEntity entity, UUID orderId,
                                         BigDecimal amount, String currency) {
        PaymentIntent previous = entity.toDomain();
        if (!previous.orderId().equals(orderId) || previous.amount().compareTo(amount) != 0
                || !previous.currency().equals(currency)) {
            throw new IllegalStateException("Idempotency key reused with different payment details.");
        }
        if (previous.status() == PaymentStatus.REQUESTED && !splitPhaseEnabled) {
            throw new IllegalStateException("Payment authorization is already being processed.");
        }
        return previous;
    }

    private PaymentIntentEntity findForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private PaymentIntentEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }
}