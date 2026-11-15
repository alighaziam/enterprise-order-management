package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowEntity;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CheckoutFulfillmentStateService {

    private final CheckoutWorkflowRepository workflows;
    private final OutboxOperations outbox;

    public CheckoutFulfillmentStateService(CheckoutWorkflowRepository workflows, OutboxOperations outbox) {
        this.workflows = workflows;
        this.outbox = outbox;
    }

    @Transactional
    public boolean complete(UUID workflowId, String workerId, UUID shipmentId, Instant now) {
        CheckoutWorkflowEntity workflow = workflows
                .findByIdAndFulfillmentLockedByAndFulfillmentLeaseUntilAfter(workflowId, workerId, now)
                .orElse(null);
        if (workflow == null) {
            return false;
        }

        workflow.shipment(shipmentId, now);
        workflows.saveAndFlush(workflow);
        outbox.append(
                "CheckoutCompleted",
                "CheckoutWorkflow",
                workflow.getId().toString(),
                3,
                workflow.getId().toString(),
                null,
                "{\"shipmentId\":\"" + shipmentId + "\"}");
        return true;
    }

    @Transactional
    public boolean retry(UUID workflowId, String workerId, String error, Instant now) {
        CheckoutWorkflowEntity workflow = workflows
                .findByIdAndFulfillmentLockedByAndFulfillmentLeaseUntilAfter(workflowId, workerId, now)
                .orElse(null);
        if (workflow == null) {
            return false;
        }

        workflow.scheduleFulfillmentRetry(error, now);
        workflows.saveAndFlush(workflow);
        return true;
    }
}
