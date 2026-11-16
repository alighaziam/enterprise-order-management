package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutSagaClaimService {
    private static final long LEASE_SECONDS = 120;
    private static final long STALE_SECONDS = 120;

    private final CheckoutWorkflowRepository workflows;

    public CheckoutSagaClaimService(CheckoutWorkflowRepository workflows) {
        this.workflows = workflows;
    }

    @Transactional
    public List<ClaimedSaga> claimBatch(Instant now, int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Batch size must be between 1 and 100.");
        }
        String workerId = UUID.randomUUID().toString();
        workflows.claimStaleSaga(workerId, now, now.plusSeconds(LEASE_SECONDS),
                now.minusSeconds(STALE_SECONDS), batchSize);
        return workflows.findByFulfillmentLockedByOrderByUpdatedAtAsc(workerId).stream()
                .map(workflow -> new ClaimedSaga(workflow.getId(), workflow.getOrderId(),
                        workflow.getPaymentId(), workflow.getIdempotencyKey(),
                        workflow.getStatus(), workerId))
                .toList();
    }

    public record ClaimedSaga(UUID workflowId, UUID orderId, UUID paymentId,
                              String idempotencyKey,
                              io.github.alighaziam.eom.checkout.CheckoutStatus status,
                              String workerId) { }
}
