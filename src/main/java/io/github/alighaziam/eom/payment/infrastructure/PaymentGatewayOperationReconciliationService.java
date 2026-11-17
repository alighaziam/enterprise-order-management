package io.github.alighaziam.eom.payment.infrastructure;

import io.github.alighaziam.eom.outbox.OutboxOperations;
import io.github.alighaziam.eom.payment.PaymentOperationType;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.payment.application.ProviderOperationLookup;
import io.github.alighaziam.eom.payment.application.ProviderOperationState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies a read-only provider observation in a short transaction.
 * The provider network call must have completed before this service is invoked.
 */
@Service
public class PaymentGatewayOperationReconciliationService {

    private final PaymentGatewayOperationRepository operations;
    private final PaymentRepository payments;
    private final OutboxOperations outbox;

    public PaymentGatewayOperationReconciliationService(
            PaymentGatewayOperationRepository operations,
            PaymentRepository payments,
            OutboxOperations outbox) {
        this.operations = operations;
        this.payments = payments;
        this.outbox = outbox;
    }

    @Transactional
    public boolean apply(UUID operationId, String workerId, ProviderOperationLookup lookup, Instant now) {
        if (lookup == null) {
            return markUnknown(operationId, workerId, "Provider returned no reconciliation result.", now);
        }

        PaymentGatewayOperationEntity operation = ownedOperation(operationId, workerId, now);
        if (operation == null) return false;

        if (lookup.state() == ProviderOperationState.PENDING
                || lookup.state() == ProviderOperationState.INDETERMINATE
                || lookup.state() == ProviderOperationState.NOT_FOUND) {
            String reason = switch (lookup.state()) {
                case PENDING -> "Provider still reports the operation as pending.";
                case INDETERMINATE -> "Provider lookup did not establish a final outcome.";
                case NOT_FOUND -> lookup.authoritativeNotFound()
                        ? "Provider authoritatively reports no accepted operation; automatic resubmission is disabled."
                        : "Provider lookup returned a non-authoritative not-found result.";
                default -> throw new IllegalStateException("Unexpected provider lookup state.");
            };
            operation.unknown(reason, workerId, now);
            operations.saveAndFlush(operation);
            return true;
        }

        PaymentIntentEntity payment = payments.findByIdForUpdate(operation.getPaymentId()).orElse(null);
        if (payment == null) {
            operation.unknown("Payment intent no longer exists; manual investigation is required.", workerId, now);
            operations.saveAndFlush(operation);
            return true;
        }

        String providerReference = lookup.providerReference() != null
                ? lookup.providerReference() : operation.getProviderReference();

        if (lookup.state() == ProviderOperationState.SUCCEEDED) {
            if (!applySuccessfulPaymentTransition(operation, payment, providerReference, now)) {
                operation.unknown("Provider reports success but local payment state is incompatible or required provider reference is missing; manual investigation is required.",
                        workerId, now);
                operations.saveAndFlush(operation);
                return true;
            }
            operation.succeeded(providerReference, workerId, now);
            payments.saveAndFlush(payment);
            operations.saveAndFlush(operation);
            appendSuccessEvent(operation, payment);
            return true;
        }

        if (lookup.state() == ProviderOperationState.FAILED) {
            if (!applyFailedPaymentTransition(operation, payment, now)) {
                operation.unknown("Provider reports failure but local payment state is incompatible; manual investigation is required.",
                        workerId, now);
                operations.saveAndFlush(operation);
                return true;
            }
            operation.failed("Provider confirmed operation failure: "
                    + (lookup.providerStatusCode() == null ? "unspecified" : lookup.providerStatusCode()),
                    workerId, now);
            payments.saveAndFlush(payment);
            operations.saveAndFlush(operation);
            return true;
        }

        throw new IllegalStateException("Unsupported provider reconciliation state.");
    }

    @Transactional
    public boolean markUnknown(UUID operationId, String workerId, String reason, Instant now) {
        PaymentGatewayOperationEntity operation = ownedOperation(operationId, workerId, now);
        if (operation == null) return false;
        operation.unknown(reason, workerId, now);
        operations.saveAndFlush(operation);
        return true;
    }

    private PaymentGatewayOperationEntity ownedOperation(UUID id, String workerId, Instant now) {
        if (id == null || workerId == null || workerId.isBlank() || now == null) return null;
        PaymentGatewayOperationEntity operation = operations.findByIdForUpdate(id).orElse(null);
        return operation != null && operation.ownsLease(workerId, now) ? operation : null;
    }

    private static boolean applySuccessfulPaymentTransition(PaymentGatewayOperationEntity operation,
                                                             PaymentIntentEntity payment,
                                                             String providerReference,
                                                             Instant now) {
        PaymentOperationType type = operation.getOperationType();
        PaymentStatus status = payment.toDomain().status();
        return switch (type) {
            case AUTHORIZE -> {
                if (status != PaymentStatus.REQUESTED
                        || providerReference == null || providerReference.isBlank()) yield false;
                payment.authorized(providerReference, now);
                yield true;
            }
            case CAPTURE -> {
                if (status != PaymentStatus.AUTHORIZED) yield false;
                payment.status(PaymentStatus.CAPTURED, now);
                yield true;
            }
            case VOID -> {
                if (status != PaymentStatus.AUTHORIZED) yield false;
                payment.status(PaymentStatus.VOIDED, now);
                yield true;
            }
            case REFUND -> {
                if (status != PaymentStatus.REFUND_PENDING) yield false;
                payment.status(PaymentStatus.REFUNDED, now);
                yield true;
            }
        };
    }

    private static boolean applyFailedPaymentTransition(PaymentGatewayOperationEntity operation,
                                                         PaymentIntentEntity payment, Instant now) {
        PaymentStatus status = payment.toDomain().status();
        return switch (operation.getOperationType()) {
            case AUTHORIZE -> {
                if (status != PaymentStatus.REQUESTED) yield false;
                payment.status(PaymentStatus.FAILED, now);
                yield true;
            }
            case CAPTURE, VOID -> status == PaymentStatus.AUTHORIZED;
            case REFUND -> {
                if (status != PaymentStatus.REFUND_PENDING) yield false;
                payment.status(PaymentStatus.CAPTURED, now);
                yield true;
            }
        };
    }

    private void appendSuccessEvent(PaymentGatewayOperationEntity operation, PaymentIntentEntity payment) {
        String eventType;
        long version;
        switch (operation.getOperationType()) {
            case AUTHORIZE -> { eventType = "PaymentAuthorized"; version = 1; }
            case CAPTURE -> { eventType = "PaymentCaptured"; version = 2; }
            case VOID -> { eventType = "PaymentVoided"; version = 3; }
            case REFUND -> { eventType = "PaymentRefunded"; version = 4; }
            default -> throw new IllegalStateException("Unsupported payment operation type.");
        }
        outbox.append(eventType, "PaymentIntent", payment.toDomain().id().toString(), version,
                payment.toDomain().orderId().toString(), null,
                "{\"paymentId\":\"" + payment.toDomain().id() + "\"}");
    }
}
