package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.checkout.CheckoutOperations.CheckoutRequest;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowEntity;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserves the idempotency key and all local checkout records in one short
 * database transaction. External payment and shipping calls deliberately
 * happen only after this transaction commits.
 */
@Service
public class CheckoutReservationService {

    private final OrderOperations orders;
    private final CheckoutWorkflowRepository workflows;
    private final OutboxOperations outbox;

    public CheckoutReservationService(
            OrderOperations orders,
            CheckoutWorkflowRepository workflows,
            OutboxOperations outbox) {
        this.orders = orders;
        this.workflows = workflows;
        this.outbox = outbox;
    }

    @Transactional
    public ReservationResult reserve(CheckoutRequest request) {
        var existing = workflows.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return new ReservationResult(existing.get(), false, null);
        }

        var order = orders.placeForCheckout(request.customerId(), request.lines());
        Instant now = Instant.now();
        var workflow = workflows.saveAndFlush(new CheckoutWorkflowEntity(
                UUID.randomUUID(), order.id(), request.idempotencyKey(), request.deliveryAddress(), now));
        outbox.append(
                "CheckoutStarted",
                "CheckoutWorkflow",
                workflow.getId().toString(),
                1,
                workflow.getId().toString(),
                null,
                "{\"orderId\":\"" + order.id() + "\"}");
        return new ReservationResult(workflow, true, order);
    }

    public record ReservationResult(
            CheckoutWorkflowEntity workflow, boolean created, OrderOperations.OrderSummary order) { }
}
