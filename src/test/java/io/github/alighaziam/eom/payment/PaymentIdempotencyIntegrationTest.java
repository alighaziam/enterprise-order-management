package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentIdempotencyIntegrationTest {

    @Autowired
    private PaymentOperations payments;

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private OrderOperations orders;

    @Autowired
    private CustomerService customers;

    @Autowired
    private ProductService products;

    @Autowired
    private InventoryService inventory;

    @Test
    void concurrentAuthorizationRequestsWithTheSameKeyCreateOnePayment() throws Exception {
        BigDecimal amount = new BigDecimal("42.50");
        OrderOperations.OrderSummary order = createReservedOrder(amount);
        String key = "parallel-payment-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> authorize(order.id(), amount, key, ready, start));
            var second = executor.submit(() -> authorize(order.id(), amount, key, ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both authorization requests should be ready");
            start.countDown();

            PaymentIntent firstResult = first.get(15, TimeUnit.SECONDS);
            PaymentIntent secondResult = second.get(15, TimeUnit.SECONDS);

            assertEquals(firstResult.id(), secondResult.id());
            assertEquals(PaymentStatus.AUTHORIZED, firstResult.status());
            assertEquals(PaymentStatus.AUTHORIZED, secondResult.status());
            assertEquals(firstResult.id(), repository.findByIdempotencyKey(key).orElseThrow().toDomain().id());
        }
    }

    @Test
    void concurrentCaptureRequestsPublishOnlyOneCaptureTransition() throws Exception {
        BigDecimal amount = new BigDecimal("19.50");
        OrderOperations.OrderSummary order = createReservedOrder(amount);
        PaymentIntent payment = payments.authorize(order.id(), amount, order.currency(),
                "capture-race-" + UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> capture(payment.id(), ready, start));
            var second = executor.submit(() -> capture(payment.id(), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both capture requests should be ready");
            start.countDown();

            assertEquals(PaymentStatus.CAPTURED, first.get(15, TimeUnit.SECONDS).status());
            assertEquals(PaymentStatus.CAPTURED, second.get(15, TimeUnit.SECONDS).status());
        }

        var events = outbox.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(
                "PaymentIntent", payment.id().toString());
        assertEquals(List.of("PaymentAuthorized", "PaymentCaptured"),
                events.stream().map(event -> event.getEventType()).toList());
        assertEquals(List.of(1L, 2L),
                events.stream().map(event -> event.getAggregateVersion()).toList());
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithDifferentPaymentDetails() {
        BigDecimal amount = new BigDecimal("12.00");
        OrderOperations.OrderSummary order = createReservedOrder(amount);
        String key = "payment-payload-" + UUID.randomUUID();

        payments.authorize(order.id(), amount, order.currency(), key);

        assertThrows(IllegalStateException.class, () ->
                payments.authorize(order.id(), new BigDecimal("13.00"), order.currency(), key));
    }

    @Test
    void preventsTwoActivePaymentsForTheSameOrder() {
        BigDecimal amount = new BigDecimal("31.00");
        OrderOperations.OrderSummary order = createReservedOrder(amount);

        payments.authorize(order.id(), amount, order.currency(), "first-payment-" + UUID.randomUUID());

        assertThrows(IllegalStateException.class, () ->
                payments.authorize(order.id(), amount, order.currency(), "second-payment-" + UUID.randomUUID()));
    }

    @Test
    void rejectsAmountThatDoesNotMatchTheReservedOrder() {
        BigDecimal amount = new BigDecimal("25.00");
        OrderOperations.OrderSummary order = createReservedOrder(amount);

        assertThrows(IllegalArgumentException.class, () ->
                payments.authorize(order.id(), new BigDecimal("24.99"), order.currency(),
                        "wrong-total-" + UUID.randomUUID()));
    }

    @Test
    void rejectsAmountsThatExceedTheDatabasePrecisionAndScale() {
        UUID orderId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                payments.authorize(orderId, new BigDecimal("1000000000000000.00"), "EUR",
                        "too-many-integer-digits-" + UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () ->
                payments.authorize(orderId, new BigDecimal("1.00001"), "EUR",
                        "too-many-fractional-digits-" + UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () ->
                payments.authorize(orderId, new BigDecimal("0.0001"), "EUR",
                        "valid-precision-" + UUID.randomUUID()));
    }

    @Test
    void rejectsAuthorizationForAnOrderThatIsNoLongerReserved() {
        BigDecimal amount = new BigDecimal("25.00");
        OrderOperations.OrderSummary order = createReservedOrder(amount);
        PaymentIntent payment = payments.authorize(order.id(), amount, order.currency(),
                "confirm-order-" + UUID.randomUUID());
        payments.capture(payment.id());
        orders.confirmForCheckout(order.id());

        assertEquals(OrderState.CONFIRMED, orders.state(order.id()));
        assertThrows(IllegalStateException.class, () ->
                payments.authorize(order.id(), amount, order.currency(), "confirmed-" + UUID.randomUUID()));
    }

    private OrderOperations.OrderSummary createReservedOrder(BigDecimal amount) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Payment Customer", "payment-" + suffix + "@example.com");
        var product = products.create("PAY-" + suffix, "Payment Product", "Test item", amount, "EUR");
        inventory.initializeStock(product.id(), 2);
        return orders.placeForCheckout(customer.id(),
                List.of(new OrderOperations.OrderLineRequest(product.id(), 1)));
    }

    private PaymentIntent capture(UUID paymentId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent capture");
        }
        return payments.capture(paymentId);
    }

    private PaymentIntent authorize(UUID orderId, BigDecimal amount, String key,
                                    CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent start");
        }
        return payments.authorize(orderId, amount, "EUR", key);
    }
}