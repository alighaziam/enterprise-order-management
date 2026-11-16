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
 * The orchestrator invokes local sandbox adapters separately; this service
 * owns only the short workflow/outbox transaction.
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
    public boolean recordSagaPayment(UUID workflowId, String workerId, UUID paymentId,
                                     CheckoutStatus status, Instant now) {
        if (status != CheckoutStatus.PAYMENT_AUTHORIZED && status != CheckoutStatus.PAYMENT_CAPTURED) {
            throw new IllegalArgumentException("Payment checkpoint status is not supported.");
        }
        CheckoutWorkflowEntity workflow = claimed(workflowId, workerId, now);
        if (workflow == null) return false;
        workflow.payment(paymentId, status, now);
        persistAndPublish(workflow, "Checkout" + status.name(),
                "{\"paymentId\":\"" + paymentId + "\"}");
        return true;
    }

    @Transactional
    public boolean recordSagaStatus(UUID workflowId, String workerId, CheckoutStatus status, Instant now) {
        if (status != CheckoutStatus.ORDER_CONFIRMED) {
            throw new IllegalArgumentException("Saga recovery status is not supported.");
        }
        CheckoutWorkflowEntity workflow = claimed(workflowId, workerId, now);
        if (workflow == null) return false;
        workflow.status(status, null, now);
        persistAndPublish(workflow, "Checkout" + status.name(),
                "{\"orderId\":\"" + workflow.getOrderId() + "\",\"status\":\"" + status.name() + "\"}");
        return true;
    }

    @Transactional
    public boolean releaseSagaClaim(UUID workflowId, String workerId, String error, Instant now) {
        CheckoutWorkflowEntity workflow = claimed(workflowId, workerId, now);
        if (workflow == null) return false;
        workflow.releaseSagaClaim(error, now);
        workflows.saveAndFlush(workflow);
        return true;
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

    private CheckoutWorkflowEntity claimed(UUID id, String workerId, Instant now) {
        if (workerId == null || workerId.isBlank() || now == null) return null;
        return workflows.findByIdAndFulfillmentLockedByAndFulfillmentLeaseUntilAfter(id, workerId, now)
                .orElse(null);
    }

    private CheckoutWorkflowEntity find(UUID id) {
        return workflows.findByIdForUpdate(id).orElseThrow(() -> new CheckoutNotFoundException(id));
    }
}
