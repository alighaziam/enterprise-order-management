package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentIntent;
import io.github.alighaziam.eom.payment.PaymentOperationType;
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
 * Any exception is treated as an unknown outcome and handed to reconciliation; this
 * worker never retries or creates a second financial command.
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

    @Scheduled(fixedDelayString = "${app.payment.command-execution.fixed-delay:PT1S}")
    public void executeScheduledBatch() {
        executeBatch(Instant.now());
    }

    public int executeBatch(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Command execution time is required.");
        }

        List<PaymentGatewayOperationEntity> batch = claims.claimPendingBatch(BATCH_SIZE, now);
        int completed = 0;
        for (PaymentGatewayOperationEntity operation : batch) {
            String workerId = operation.getLockedBy();
            try {
                PaymentIntent payment = payments.findById(operation.getPaymentId())
                        .map(entity -> entity.toDomain())
                        .orElse(null);
                if (payment == null) {
                    if (results.markUnknown(operation.getId(), workerId,
                            "Payment intent is missing; provider command was not submitted.", Instant.now())) {
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

    private String execute(PaymentGatewayOperationEntity operation, PaymentIntent payment) {
        return switch (operation.getOperationType()) {
            case AUTHORIZE -> gateway.authorize(payment.id(), payment.amount(), payment.currency(),
                    operation.getIdempotencyKey());
            case CAPTURE -> {
                requireProviderReference(payment);
                gateway.capture(payment.providerReference());
                yield null;
            }
            case VOID -> {
                requireProviderReference(payment);
                gateway.voidAuthorization(payment.providerReference());
                yield null;
            }
            case REFUND -> {
                requireProviderReference(payment);
                gateway.refund(payment.providerReference());
                yield null;
            }
        };
    }

    private static void requireProviderReference(PaymentIntent payment) {
        if (payment.providerReference() == null || payment.providerReference().isBlank()) {
            throw new IllegalStateException("Payment has no provider reference for this operation.");
        }
    }
}
