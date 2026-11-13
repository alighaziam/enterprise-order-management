package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.checkout.CheckoutOperations.CheckoutRequest;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowEntity;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
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
            validateReplay(existing.get(), request);
            return new ReservationResult(existing.get(), false, null);
        }

        var order = orders.placeForCheckout(request.customerId(), request.lines());
        Instant now = Instant.now();
        var workflow = workflows.saveAndFlush(new CheckoutWorkflowEntity(
                UUID.randomUUID(), order.id(), request.idempotencyKey(), fingerprint(request),
                request.deliveryAddress(), now));
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

    public void validateReplay(CheckoutWorkflowEntity existing, CheckoutRequest request) {
        String stored = existing.getRequestFingerprint();
        if (stored != null && !"legacy".equals(stored)
                && !MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
                        fingerprint(request).getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException(
                    "This idempotency key has already been used with a different checkout request.");
        }
    }

    private static String fingerprint(CheckoutRequest request) {
        String lines = request.lines().stream()
                .map(line -> line.productId() + ":" + line.quantity())
                .collect(Collectors.joining("\n"));
        String canonical = request.customerId() + "\n" + lines + "\n" + request.deliveryAddress();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record ReservationResult(
            CheckoutWorkflowEntity workflow, boolean created, OrderOperations.OrderSummary order) { }
}
