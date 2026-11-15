package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentIdempotencyIntegrationTest {

    @Autowired
    private PaymentOperations payments;

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private OutboxRepository outbox;

    @MockitoBean
    private OrderOperations orders;

    @Test
    void concurrentAuthorizationRequestsWithTheSameKeyCreateOnePayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = "parallel-payment-" + UUID.randomUUID();
        BigDecimal amount = new BigDecimal("42.50");
        stubReservedOrder(orderId, amount);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> authorize(orderId, amount, key, ready, start));
            var second = executor.submit(() -> authorize(orderId, amount, key, ready, start));

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
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("19.50");
        stubReservedOrder(orderId, amount);
        PaymentIntent payment = payments.authorize(orderId, amount, "EUR",
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
        assertEquals(java.util.List.of("PaymentAuthorized", "PaymentCaptured"),
                events.stream().map(event -> event.getEventType()).toList());
        assertEquals(java.util.List.of(1L, 2L),
                events.stream().map(event -> event.getAggregateVersion()).toList());
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithDifferentPaymentDetails() {
        String key = "payment-payload-" + UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        stubReservedOrder(orderId, new BigDecimal("12.00"));

        payments.authorize(orderId, new BigDecimal("12.00"), "EUR", key);

        assertThrows(IllegalStateException.class, () ->
                payments.authorize(orderId, new BigDecimal("13.00"), "EUR", key));
    }

    @Test
    void preventsTwoActivePaymentsForTheSameOrder() {
        UUID orderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("31.00");
        stubReservedOrder(orderId, amount);

        payments.authorize(orderId, amount, "EUR", "first-payment-" + UUID.randomUUID());

        assertThrows(IllegalStateException.class, () ->
                payments.authorize(orderId, amount, "EUR", "second-payment-" + UUID.randomUUID()));
    }

    @Test
    void rejectsAmountThatDoesNotMatchTheReservedOrder() {
        UUID orderId = UUID.randomUUID();
        stubReservedOrder(orderId, new BigDecimal("25.00"));

        assertThrows(IllegalArgumentException.class, () ->
                payments.authorize(orderId, new BigDecimal("24.99"), "EUR", "wrong-total-" + UUID.randomUUID()));
    }

    @Test
    void rejectsAuthorizationForAnOrderThatIsNoLongerReserved() {
        UUID orderId = UUID.randomUUID();
        when(orders.orderDetails(orderId)).thenReturn(Optional.of(
                new OrderOperations.OrderDetails(orderId, new BigDecimal("25.00"), "EUR", OrderState.CONFIRMED)));

        assertThrows(IllegalStateException.class, () ->
                payments.authorize(orderId, new BigDecimal("25.00"), "EUR", "confirmed-" + UUID.randomUUID()));
    }

    private PaymentIntent capture(UUID paymentId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for concurrent capture");
        }
        return payments.capture(paymentId);
    }

    private void stubReservedOrder(UUID orderId, BigDecimal amount) {
        when(orders.orderDetails(orderId)).thenReturn(Optional.of(
                new OrderOperations.OrderDetails(orderId, amount, "EUR", OrderState.RESERVED)));
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
