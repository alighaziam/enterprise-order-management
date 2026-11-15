package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.checkout.CheckoutStatus;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowEntity;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Commits a checkout workflow checkpoint and its integration event atomically.
 * External payment and carrier calls stay outside these short transactions.
 */
@Service
public class CheckoutWorkflowStateService {

    private final CheckoutWorkflowRepository workflows;
    private final OutboxOperations outbox;

    public CheckoutWorkflowStateService(CheckoutWorkflowRepository workflows, OutboxOperations outbox) {
        this.workflows = workflows;
        this.outbox = outbox;
    }

    @Transactional
    public CheckoutWorkflowEntity recordPayment(UUID workflowId, UUID paymentId,
                                                CheckoutStatus status, Instant now) {
        if (status != CheckoutStatus.PAYMENT_AUTHORIZED && status != CheckoutStatus.PAYMENT_CAPTURED) {
            throw new IllegalArgumentException("Payment checkpoint status is not supported.");
        }
        CheckoutWorkflowEntity workflow = find(workflowId);
        workflow.payment(paymentId, status, now);
        return persistAndPublish(workflow, "Checkout" + status.name(),
                "{\"paymentId\":\"" + paymentId + "\"}");
    }

    @Transactional
    public CheckoutWorkflowEntity recordStatus(UUID workflowId, CheckoutStatus status,
                                               String error, Instant now) {
        CheckoutWorkflowEntity workflow = find(workflowId);
        workflow.status(status, error, now);
        return persistAndPublish(workflow, "Checkout" + status.name(),
                "{\"orderId\":\"" + workflow.getOrderId() + "\",\"status\":\"" + status.name() + "\"}");
    }

    @Transactional
    public CheckoutWorkflowEntity scheduleFulfillmentRetry(UUID workflowId, String error, Instant now) {
        CheckoutWorkflowEntity workflow = find(workflowId);
        workflow.scheduleFulfillmentRetry(error, now);
        return persistAndPublish(workflow, "CheckoutFulfillmentPending",
                "{\"orderId\":\"" + workflow.getOrderId() + "\"}");
    }

    @Transactional
    public CheckoutWorkflowEntity recordShipment(UUID workflowId, UUID shipmentId, Instant now) {
        CheckoutWorkflowEntity workflow = find(workflowId);
        workflow.shipment(shipmentId, now);
        return persistAndPublish(workflow, "CheckoutCompleted",
                "{\"shipmentId\":\"" + shipmentId + "\"}");
    }

    private CheckoutWorkflowEntity persistAndPublish(CheckoutWorkflowEntity workflow,
                                                       String eventType, String payload) {
        workflows.saveAndFlush(workflow);
        outbox.append(eventType, "CheckoutWorkflow", workflow.getId().toString(),
                workflow.getEventVersion(), workflow.getId().toString(), null, payload);
        return workflow;
    }

    private CheckoutWorkflowEntity find(UUID id) {
        return workflows.findByIdForUpdate(id).orElseThrow(() -> new CheckoutNotFoundException(id));
    }
}
