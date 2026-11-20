package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import io.github.alighaziam.eom.payment.PaymentIntent;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.github.alighaziam.eom.payment.PaymentOperationType;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import io.github.alighaziam.eom.payment.infrastructure.PaymentIntentEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceSplitPhaseTest {

    private PaymentRepository payments;
    private PaymentGatewayOperationRepository operations;
    private PaymentGateway gateway;
    private OutboxOperations outbox;
    private OrderOperations orders;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        operations = mock(PaymentGatewayOperationRepository.class);
        gateway = mock(PaymentGateway.class);
        outbox = mock(OutboxOperations.class);
        orders = mock(OrderOperations.class);
        service = new PaymentService(payments, operations, gateway, outbox, orders);
        ReflectionTestUtils.setField(service, "splitPhaseEnabled", true);
    }

    @Test
    void authorizationPersistsPendingCommandAndReturnsRequestedPaymentWithoutCallingProvider() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("27.40");
        String key = "split-auth-" + UUID.randomUUID();
        Instant now = Instant.now();
        PaymentIntentEntity entity = new PaymentIntentEntity(paymentId, orderId, amount, "EUR", key, null, now);

        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(orders.orderDetails(orderId)).thenReturn(Optional.of(
                new OrderOperations.OrderDetails(orderId, amount, "EUR", OrderState.RESERVED)));
        when(payments.existsByOrderIdAndStatusIn(eq(orderId), anyCollection())).thenReturn(false);
        when(payments.insertIfAbsent(any(), eq(orderId), eq(amount), eq("EUR"), eq(key), any())).thenReturn(1);
        when(payments.findById(any())).thenReturn(Optional.of(entity));

        PaymentIntent result = service.authorize(orderId, amount, "EUR", key);

        assertEquals(PaymentStatus.REQUESTED, result.status());
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentGatewayOperationEntity.class);
        verify(operations).saveAndFlush(captor.capture());
        PaymentGatewayOperationEntity command = captor.getValue();
        assertEquals(paymentId, command.getPaymentId());
        assertEquals(PaymentOperationType.AUTHORIZE, command.getOperationType());
        assertEquals(PaymentOperationStatus.PENDING, command.getStatus());
        assertEquals(key, command.getIdempotencyKey());
        assertEquals(64, command.getRequestFingerprint().length());
        verifyNoInteractions(gateway, outbox);
    }

    @Test
    void authorizationReplayReturnsTheExistingRequestedPaymentWithoutCreatingAnotherCommand() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("27.40");
        String key = "split-replay-" + UUID.randomUUID();
        PaymentIntentEntity existing = new PaymentIntentEntity(paymentId, orderId, amount, "EUR", key,
                null, Instant.now());
        when(payments.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        PaymentIntent result = service.authorize(orderId, amount, "EUR", key);

        assertEquals(paymentId, result.id());
        assertEquals(PaymentStatus.REQUESTED, result.status());
        verifyNoInteractions(operations, gateway, outbox, orders);
    }

    @Test
    void captureAndVoidPersistCommandsWithoutHoldingThePaymentTransitionOpenOnProviderIo() {
        UUID captureId = UUID.randomUUID();
        UUID voidId = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentIntentEntity capturedTarget = authorizedPayment(captureId, now);
        PaymentIntentEntity voidTarget = authorizedPayment(voidId, now);
        when(payments.findByIdForUpdate(captureId)).thenReturn(Optional.of(capturedTarget));
        when(payments.findByIdForUpdate(voidId)).thenReturn(Optional.of(voidTarget));
        when(operations.findByPaymentIdAndOperationType(any(), any())).thenReturn(Optional.empty());

        PaymentIntent captureResult = service.capture(captureId);
        PaymentIntent voidResult = service.voidAuthorization(voidId);

        assertEquals(PaymentStatus.AUTHORIZED, captureResult.status());
        assertEquals(PaymentStatus.AUTHORIZED, voidResult.status());
        verify(operations, times(2)).saveAndFlush(any(PaymentGatewayOperationEntity.class));
        verifyNoInteractions(gateway, outbox);
    }

    @Test
    void refundMovesPaymentToRefundPendingAndPersistsCommandBeforeProviderExecution() {
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentIntentEntity entity = authorizedPayment(paymentId, now);
        entity.status(PaymentStatus.CAPTURED, now);
        when(payments.findByIdForUpdate(paymentId)).thenReturn(Optional.of(entity));
        when(operations.findByPaymentIdAndOperationType(paymentId, PaymentOperationType.REFUND))
                .thenReturn(Optional.empty());

        PaymentIntent result = service.refund(paymentId);

        assertEquals(PaymentStatus.REFUND_PENDING, result.status());
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentGatewayOperationEntity.class);
        verify(operations).saveAndFlush(captor.capture());
        assertEquals(PaymentOperationType.REFUND, captor.getValue().getOperationType());
        assertEquals(PaymentOperationStatus.PENDING, captor.getValue().getStatus());
        verifyNoInteractions(gateway, outbox);
    }


    @Test
    void captureIsRejectedWhileVoidCommandForSamePaymentIsUnresolved() {
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentIntentEntity entity = authorizedPayment(paymentId, now);
        when(payments.findByIdForUpdate(paymentId)).thenReturn(Optional.of(entity));
        when(operations.findByPaymentIdAndOperationType(paymentId, PaymentOperationType.CAPTURE))
                .thenReturn(Optional.empty());
        when(operations.existsByPaymentIdAndOperationTypeNotAndStatusIn(
                eq(paymentId), eq(PaymentOperationType.CAPTURE), anyList())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.capture(paymentId));

        verify(operations, never()).saveAndFlush(any(PaymentGatewayOperationEntity.class));
        verifyNoInteractions(gateway, outbox);
    }

    @Test
    void voidIsRejectedWhileCaptureCommandForSamePaymentIsUnresolved() {
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentIntentEntity entity = authorizedPayment(paymentId, now);
        when(payments.findByIdForUpdate(paymentId)).thenReturn(Optional.of(entity));
        when(operations.findByPaymentIdAndOperationType(paymentId, PaymentOperationType.VOID))
                .thenReturn(Optional.empty());
        when(operations.existsByPaymentIdAndOperationTypeNotAndStatusIn(
                eq(paymentId), eq(PaymentOperationType.VOID), anyList())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.voidAuthorization(paymentId));

        verify(operations, never()).saveAndFlush(any(PaymentGatewayOperationEntity.class));
        verifyNoInteractions(gateway, outbox);
    }

    private static PaymentIntentEntity authorizedPayment(UUID id, Instant now) {
        PaymentIntentEntity entity = new PaymentIntentEntity(id, UUID.randomUUID(),
                new BigDecimal("27.40"), "EUR", "payment-" + id, null, now);
        entity.authorized("provider-ref-" + id, now);
        return entity;
    }
}
