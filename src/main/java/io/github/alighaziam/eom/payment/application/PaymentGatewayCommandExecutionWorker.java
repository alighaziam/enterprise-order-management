package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentIntent;
import io.github.alighaziam.eom.payment.PaymentOperationType;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationClaimService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationReconciliationService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Executes persisted financial commands after their claim transaction has committed.
 *
 * The provider call is deliberately made without a surrounding database transaction.
 * Any provider exception is treated as an unknown outcome and handed to reconciliation;
 * this worker never retries or creates a second financial command.
 */
@Component
@ConditionalOnProperty(name = "app.payment.command-execution.enabled", havingValue = "true")
public class PaymentGatewayCommandExecutionWorker {

    private static final int BATCH_SIZE = 20;

    private final PaymentGatewayOperationClaimService claims;
    private final PaymentGateway gateway;
    private final PaymentRepository payments;
    private final PaymentGatewayOperationReconciliationService results;

    public PaymentGatewayCommandExecutionWorker(
            PaymentGatewayOperationClaimService claims,
            PaymentGateway gateway,
            PaymentRepository payments,
            PaymentGatewayOperationReconciliationService results) {
        this.claims = claims;
        this.gateway = gateway;
        this.payments = payments;
        this.results = results;
    }

    @Scheduled(initialDelayString = "${app.payment.command-execution.initial-delay:PT1S}",
            fixedDelayString = "${app.payment.command-execution.fixed-delay:PT1S}")
    public void executeScheduledBatch() {
        executeBatch(Instant.now());
    }

    public int executeBatch(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Command execution time is required.");
        }

        // Expired command leases are never returned directly to PENDING or blindly resubmitted.
        claims.recoverExpiredProcessing(now);
        List<PaymentGatewayOperationEntity> batch = claims.claimPendingBatch(BATCH_SIZE, now);
        int completed = 0;
        for (PaymentGatewayOperationEntity operation : batch) {
            String workerId = operation.getLockedBy();
            try {
                PaymentIntent payment = payments.findById(operation.getPaymentId())
                        .map(entity -> entity.toDomain())
                        .orElse(null);
                if (payment == null) {
                    if (results.rejectBeforeSubmission(operation.getId(), workerId,
                            "Payment intent is missing; provider command was not submitted.", Instant.now())) {
                        completed++;
                    }
                    continue;
                }

                String preconditionFailure = preconditionFailure(operation, payment);
                if (preconditionFailure != null) {
                    if (results.rejectBeforeSubmission(operation.getId(), workerId, preconditionFailure, Instant.now())) {
                        completed++;
                    }
                    continue;
                }

                String providerReference = execute(operation, payment);
                ProviderOperationLookup observation = new ProviderOperationLookup(
                        ProviderOperationState.SUCCEEDED, providerReference, "EXECUTED", Instant.now(), false);
                if (results.apply(operation.getId(), workerId, observation, Instant.now())) {
                    completed++;
                }
            } catch (RuntimeException executionFailure) {
                // A timeout may occur after the provider accepted the command. Never mark it failed or retry blindly.
                if (results.markUnknown(operation.getId(), workerId,
                        "Provider command execution ended without a definitive outcome.", Instant.now())) {
                    completed++;
                }
            }
        }
        return completed;
    }

    private static String preconditionFailure(PaymentGatewayOperationEntity operation, PaymentIntent payment) {
        return switch (operation.getOperationType()) {
            case AUTHORIZE -> payment.status() == PaymentStatus.REQUESTED
                    ? null : "Authorization command is incompatible with the current payment state; provider command was not submitted.";
            case CAPTURE -> payment.status() == PaymentStatus.AUTHORIZED && hasProviderReference(payment)
                    ? null : "Capture command requires an authorized payment and provider reference; provider command was not submitted.";
            case VOID -> payment.status() == PaymentStatus.AUTHORIZED && hasProviderReference(payment)
                    ? null : "Void command requires an authorized payment and provider reference; provider command was not submitted.";
            case REFUND -> payment.status() == PaymentStatus.REFUND_PENDING && hasProviderReference(payment)
                    ? null : "Refund command requires a refund-pending payment and provider reference; provider command was not submitted.";
        };
    }

    private String execute(PaymentGatewayOperationEntity operation, PaymentIntent payment) {
        return switch (operation.getOperationType()) {
            case AUTHORIZE -> gateway.authorize(payment.id(), payment.amount(), payment.currency(),
                    operation.getIdempotencyKey());
            case CAPTURE -> {
                gateway.capture(payment.providerReference(), operation.getIdempotencyKey());
                yield null;
            }
            case VOID -> {
                gateway.voidAuthorization(payment.providerReference(), operation.getIdempotencyKey());
                yield null;
            }
            case REFUND -> {
                gateway.refund(payment.providerReference(), operation.getIdempotencyKey());
                yield null;
            }
        };
    }

    private static boolean hasProviderReference(PaymentIntent payment) {
        return payment.providerReference() != null && !payment.providerReference().isBlank();
    }
}
