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
        // Recover abandoned PROCESSING/RECONCILING leases before claiming UNKNOWN work.
        claims.recoverExpiredProcessing(now);
        int completed = 0;
        for (int claimed = 0; claimed < BATCH_SIZE; claimed++) {
            // Claim just in time so a slow provider lookup cannot consume leases
            // for operations that are still waiting behind earlier lookups.
            List<PaymentGatewayOperationEntity> batch =
                    claims.claimUnknownBatch(1, Instant.now());
            if (batch.isEmpty()) {
                break;
            }
            PaymentGatewayOperationEntity operation = batch.get(0);
            ProviderOperationLookup lookup;
            try {
                lookup = gateway.lookup(operation.getOperationType(),
                        operation.getIdempotencyKey(), operation.getProviderReference());
            } catch (RuntimeException lookupFailure) {
                // Never persist provider exception text: it may contain credentials or raw response data.
                if (results.markUnknown(operation.getId(), operation.getLockedBy(),
                        "Provider reconciliation lookup failed without a definitive outcome.", Instant.now())) {
                    completed++;
                }
                continue;
            }

            // Keep persistence/reconciliation failures outside the provider-I/O catch.
            // A database failure must surface to the scheduler/monitoring layer, not be
            // misclassified as a provider lookup failure or silently converted to UNKNOWN.
            if (results.apply(operation.getId(), operation.getLockedBy(), lookup, Instant.now())) {
                completed++;
            }
        }
        return completed;
    }
}
