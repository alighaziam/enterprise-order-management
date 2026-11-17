package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationClaimService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Reconciles unknown provider operations without resubmitting financial commands.
 * Claiming and finalizing are separate transactions; provider I/O runs between them.
 */
@Component
@ConditionalOnBean(PaymentGatewayReconciliation.class)
public class PaymentGatewayReconciliationWorker {

    private static final int BATCH_SIZE = 20;

    private final PaymentGatewayOperationClaimService claims;
    private final PaymentGatewayReconciliation gateway;
    private final PaymentGatewayOperationReconciliationService results;

    public PaymentGatewayReconciliationWorker(PaymentGatewayOperationClaimService claims,
                                              PaymentGatewayReconciliation gateway,
                                              PaymentGatewayOperationReconciliationService results) {
        this.claims = claims;
        this.gateway = gateway;
        this.results = results;
    }

    @Scheduled(fixedDelayString = "${app.payment.reconciliation.fixed-delay:PT30S}")
    public void reconcileScheduledBatch() {
        reconcileBatch(Instant.now());
    }

    public int reconcileBatch(Instant now) {
        List<PaymentGatewayOperationEntity> batch = claims.claimUnknownBatch(BATCH_SIZE, now);
        int completed = 0;
        for (PaymentGatewayOperationEntity operation : batch) {
            try {
                ProviderOperationLookup lookup = gateway.lookup(operation.getOperationType(),
                        operation.getIdempotencyKey(), operation.getProviderReference());
                if (results.apply(operation.getId(), operation.getLockedBy(), lookup, Instant.now())) {
                    completed++;
                }
            } catch (RuntimeException lookupFailure) {
                String message = lookupFailure.getMessage();
                String safeMessage = message == null || message.isBlank()
                        ? "Provider reconciliation lookup failed without a definitive outcome."
                        : "Provider reconciliation lookup failed: " + message;
                if (results.markUnknown(operation.getId(), operation.getLockedBy(), safeMessage, Instant.now())) {
                    completed++;
                }
            }
        }
        return completed;
    }
}
