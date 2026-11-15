package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutFulfillmentClaimService {

    private static final long LEASE_SECONDS = 120;
    private final CheckoutWorkflowRepository workflows;

    public CheckoutFulfillmentClaimService(CheckoutWorkflowRepository workflows) {
        this.workflows = workflows;
    }

    @Transactional
    public List<ClaimedFulfillment> claimBatch(Instant now, int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Batch size must be between 1 and 100.");
        }

        String workerId = UUID.randomUUID().toString();
        workflows.claimDueFulfillment(workerId, now, now.plusSeconds(LEASE_SECONDS), batchSize);
        return workflows.findByFulfillmentLockedByOrderByNextAttemptAtAsc(workerId).stream()
                .map(workflow -> new ClaimedFulfillment(
                        workflow.getId(), workflow.getOrderId(), workflow.getDeliveryAddress(), workerId))
                .toList();
    }

    public record ClaimedFulfillment(
            UUID workflowId,
            UUID orderId,
            DeliveryAddress deliveryAddress,
            String workerId) {
    }
}
