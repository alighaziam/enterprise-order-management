package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import io.github.alighaziam.eom.payment.application.PaymentGatewayCommandExecutionWorker;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.payment.command-execution.enabled=true",
        "app.payment.command-execution.initial-delay=PT1H",
        "app.payment.command-execution.fixed-delay=PT1H"
})
class PaymentSplitPhaseIntegrationTest {

    @Autowired private PaymentOperations payments;
    @Autowired private PaymentGatewayOperationRepository operationRepository;
    @Autowired private PaymentGatewayCommandExecutionWorker commandWorker;
    @Autowired private OutboxRepository outbox;
    @Autowired private OrderOperations orders;
    @Autowired private CustomerService customers;
    @Autowired private ProductService products;
    @Autowired private InventoryService inventory;

    @Test
    void authorizationCaptureAndRefundCommitCommandsThenApplyProviderResults() {
        BigDecimal amount = new BigDecimal("42.50");
        OrderOperations.OrderSummary order = createReservedOrder(amount);

        PaymentIntent requested = payments.authorize(order.id(), amount, order.currency(),
                "split-flow-" + UUID.randomUUID());
        assertEquals(PaymentStatus.REQUESTED, requested.status());
        assertEquals(PaymentOperationStatus.PENDING, operation(requested.id(), PaymentOperationType.AUTHORIZE).getStatus());

        assertEquals(1, commandWorker.executeBatch(Instant.now()));
        PaymentIntent authorized = payments.get(requested.id());
        assertEquals(PaymentStatus.AUTHORIZED, authorized.status());
        assertTrue(authorized.providerReference().startsWith("sandbox-"));

        PaymentIntent captureQueued = payments.capture(requested.id());
        assertEquals(PaymentStatus.AUTHORIZED, captureQueued.status());
        assertEquals(PaymentOperationStatus.PENDING, operation(requested.id(), PaymentOperationType.CAPTURE).getStatus());
        assertEquals(1, commandWorker.executeBatch(Instant.now()));
        assertEquals(PaymentStatus.CAPTURED, payments.get(requested.id()).status());

        PaymentIntent refundQueued = payments.refund(requested.id());
        assertEquals(PaymentStatus.REFUND_PENDING, refundQueued.status());
        assertEquals(PaymentOperationStatus.PENDING, operation(requested.id(), PaymentOperationType.REFUND).getStatus());
        assertEquals(1, commandWorker.executeBatch(Instant.now()));
        assertEquals(PaymentStatus.REFUNDED, payments.get(requested.id()).status());

        List<String> eventTypes = outbox.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(
                        "PaymentIntent", requested.id().toString()).stream()
                .map(event -> event.getEventType()).toList();
        assertEquals(List.of("PaymentAuthorized", "PaymentCaptured", "PaymentRefunded"), eventTypes);
    }

    @Test
    void voidAuthorizationUsesItsOwnPersistedIdempotencyKey() {
        BigDecimal amount = new BigDecimal("16.25");
        OrderOperations.OrderSummary order = createReservedOrder(amount);
        PaymentIntent requested = payments.authorize(order.id(), amount, order.currency(),
                "split-void-" + UUID.randomUUID());

        assertEquals(1, commandWorker.executeBatch(Instant.now()));
        assertEquals(PaymentStatus.AUTHORIZED, payments.get(requested.id()).status());

        PaymentIntent queued = payments.voidAuthorization(requested.id());
        assertEquals(PaymentStatus.AUTHORIZED, queued.status());
        var operation = operation(requested.id(), PaymentOperationType.VOID);
        assertEquals(PaymentOperationStatus.PENDING, operation.getStatus());
        assertEquals(requested.id() + ":VOID", operation.getIdempotencyKey());

        assertEquals(1, commandWorker.executeBatch(Instant.now()));
        assertEquals(PaymentStatus.VOIDED, payments.get(requested.id()).status());
    }

    @Test
    void concurrentCaptureRequestsPersistOnlyOneCommandAndOneOutboxTransition() throws Exception {
        BigDecimal amount = new BigDecimal("33.75");
        OrderOperations.OrderSummary order = createReservedOrder(amount);
        PaymentIntent requested = payments.authorize(order.id(), amount, order.currency(),
                "split-race-" + UUID.randomUUID());
        assertEquals(1, commandWorker.executeBatch(Instant.now()));
        assertEquals(PaymentStatus.AUTHORIZED, payments.get(requested.id()).status());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> capture(requested.id(), ready, start));
            var second = executor.submit(() -> capture(requested.id(), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both capture requests should be ready");
            start.countDown();

            assertEquals(PaymentStatus.AUTHORIZED, first.get(15, TimeUnit.SECONDS).status());
            assertEquals(PaymentStatus.AUTHORIZED, second.get(15, TimeUnit.SECONDS).status());
        }

        PaymentGatewayOperationEntity command = operation(requested.id(), PaymentOperationType.CAPTURE);
        assertEquals(PaymentOperationStatus.PENDING, command.getStatus());
        assertEquals(0, command.getAttempts());
        assertEquals(1, commandWorker.executeBatch(Instant.now()));
        assertEquals(PaymentStatus.CAPTURED, payments.get(requested.id()).status());

        List<String> eventTypes = outbox.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(
                        "PaymentIntent", requested.id().toString()).stream()
                .map(event -> event.getEventType()).toList();
        assertEquals(List.of("PaymentAuthorized", "PaymentCaptured"), eventTypes);
    }

    private PaymentIntent capture(UUID paymentId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent capture start");
        }
        return payments.capture(paymentId);
    }

    private PaymentGatewayOperationEntity operation(UUID paymentId, PaymentOperationType type) {
        return operationRepository.findByPaymentIdAndOperationType(paymentId, type).orElseThrow();
    }

    private OrderOperations.OrderSummary createReservedOrder(BigDecimal amount) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Split Phase Customer", "split-" + suffix + "@example.com");
        var product = products.create("SPLIT-" + suffix, "Split Phase Product", "Test item", amount, "EUR");
        inventory.initializeStock(product.id(), 2);
        return orders.placeForCheckout(customer.id(),
                List.of(new OrderOperations.OrderLineRequest(product.id(), 1)));
    }
}
