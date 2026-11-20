package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentIntent;
import io.github.alighaziam.eom.payment.PaymentOperationType;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationClaimService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationReconciliationService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentIntentEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentGatewayCommandExecutionWorkerTest {

    @Test
    void dispatchesEachPersistedOperationTypeOnlyForCompatiblePaymentStates() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        UUID authorizePaymentId = UUID.randomUUID();
        UUID capturePaymentId = UUID.randomUUID();
        UUID voidPaymentId = UUID.randomUUID();
        UUID refundPaymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("18.50");

        Map<UUID, PaymentIntent> snapshots = Map.of(
                authorizePaymentId, payment(authorizePaymentId, amount, PaymentStatus.REQUESTED, null, now),
                capturePaymentId, payment(capturePaymentId, amount, PaymentStatus.AUTHORIZED, "provider-ref-capture", now),
                voidPaymentId, payment(voidPaymentId, amount, PaymentStatus.AUTHORIZED, "provider-ref-void", now),
                refundPaymentId, payment(refundPaymentId, amount, PaymentStatus.REFUND_PENDING, "provider-ref-refund", now));

        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);

        List<PaymentGatewayOperationEntity> operations = List.of(
                operation(authorizePaymentId, PaymentOperationType.AUTHORIZE, "authorize-key"),
                operation(capturePaymentId, PaymentOperationType.CAPTURE, "capture-key"),
                operation(voidPaymentId, PaymentOperationType.VOID, "void-key"),
                operation(refundPaymentId, PaymentOperationType.REFUND, "refund-key"));
        Map<UUID, PaymentIntentEntity> paymentEntities = Map.of(
                authorizePaymentId, mockPaymentEntity(snapshots.get(authorizePaymentId)),
                capturePaymentId, mockPaymentEntity(snapshots.get(capturePaymentId)),
                voidPaymentId, mockPaymentEntity(snapshots.get(voidPaymentId)),
                refundPaymentId, mockPaymentEntity(snapshots.get(refundPaymentId)));
        when(claims.claimPendingBatch(20, now)).thenReturn(operations);
        when(payments.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(paymentEntities.get(invocation.getArgument(0))));
        when(gateway.authorize(authorizePaymentId, amount, "EUR", "authorize-key"))
                .thenReturn("provider-auth-456");
        when(results.apply(any(), anyString(), any(ProviderOperationLookup.class), any(Instant.class)))
                .thenReturn(true);

        PaymentGatewayCommandExecutionWorker worker =
                new PaymentGatewayCommandExecutionWorker(claims, gateway, payments, results);

        assertEquals(4, worker.executeBatch(now));

        InOrder claimOrder = inOrder(claims);
        claimOrder.verify(claims).recoverExpiredProcessing(now);
        claimOrder.verify(claims).claimPendingBatch(20, now);
        verify(gateway).authorize(authorizePaymentId, amount, "EUR", "authorize-key");
        verify(gateway).capture("provider-ref-capture", "capture-key");
        verify(gateway).voidAuthorization("provider-ref-void", "void-key");
        verify(gateway).refund("provider-ref-refund", "refund-key");
        verify(results, times(4)).apply(any(), anyString(), any(ProviderOperationLookup.class), any(Instant.class));
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void missingPaymentIsRejectedWithoutEnteringUnknownReconciliation() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        UUID paymentId = UUID.randomUUID();
        PaymentGatewayOperationEntity operation =
                operation(paymentId, PaymentOperationType.AUTHORIZE, "authorize-key");
        String workerId = operation.getLockedBy();

        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        when(claims.claimPendingBatch(20, now)).thenReturn(List.of(operation));
        when(payments.findById(paymentId)).thenReturn(Optional.empty());
        when(results.rejectBeforeSubmission(eq(operation.getId()), eq(workerId), contains("Payment intent is missing"),
                any(Instant.class))).thenReturn(true);

        PaymentGatewayCommandExecutionWorker worker =
                new PaymentGatewayCommandExecutionWorker(claims, gateway, payments, results);

        assertEquals(1, worker.executeBatch(now));
        verifyNoInteractions(gateway);
        verify(results).rejectBeforeSubmission(eq(operation.getId()), eq(workerId),
                eq("Payment intent is missing; provider command was not submitted."), any(Instant.class));
        verify(results, never()).markUnknown(any(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void incompatiblePaymentStateIsNotSubmittedToProvider() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        UUID paymentId = UUID.randomUUID();
        PaymentIntent payment = payment(paymentId, new BigDecimal("18.50"),
                PaymentStatus.CAPTURED, "provider-ref-123", now);
        PaymentGatewayOperationEntity operation =
                operation(paymentId, PaymentOperationType.CAPTURE, "capture-key");
        String workerId = operation.getLockedBy();

        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        PaymentIntentEntity paymentEntity = mockPaymentEntity(payment);
        when(claims.claimPendingBatch(20, now)).thenReturn(List.of(operation));
        when(payments.findById(paymentId)).thenReturn(Optional.of(paymentEntity));
        when(results.rejectBeforeSubmission(eq(operation.getId()), eq(workerId),
                contains("Capture command requires an authorized payment"), any(Instant.class))).thenReturn(true);

        PaymentGatewayCommandExecutionWorker worker =
                new PaymentGatewayCommandExecutionWorker(claims, gateway, payments, results);

        assertEquals(1, worker.executeBatch(now));
        verifyNoInteractions(gateway);
        verify(results).rejectBeforeSubmission(eq(operation.getId()), eq(workerId),
                eq("Capture command requires an authorized payment and provider reference; provider command was not submitted."),
                any(Instant.class));
        verify(results, never()).markUnknown(any(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void providerExceptionIsRecordedAsUnknownWithoutPersistingExceptionText() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        UUID paymentId = UUID.randomUUID();
        PaymentIntent payment = payment(paymentId, new BigDecimal("18.50"),
                PaymentStatus.REQUESTED, null, now);
        PaymentGatewayOperationEntity operation =
                operation(paymentId, PaymentOperationType.AUTHORIZE, "authorize-key");
        String workerId = operation.getLockedBy();

        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        PaymentIntentEntity paymentEntity = mockPaymentEntity(payment);
        when(claims.claimPendingBatch(20, now)).thenReturn(List.of(operation));
        when(payments.findById(paymentId)).thenReturn(Optional.of(paymentEntity));
        when(gateway.authorize(paymentId, payment.amount(), payment.currency(), "authorize-key"))
                .thenThrow(new IllegalStateException("secret=do-not-persist"));
        when(results.markUnknown(eq(operation.getId()), eq(workerId),
                anyString(), any(Instant.class))).thenReturn(true);

        PaymentGatewayCommandExecutionWorker worker =
                new PaymentGatewayCommandExecutionWorker(claims, gateway, payments, results);

        assertEquals(1, worker.executeBatch(now));
        verify(results).markUnknown(eq(operation.getId()), eq(workerId),
                eq("Provider command execution ended without a definitive outcome."), any(Instant.class));
        verify(gateway).authorize(paymentId, payment.amount(), payment.currency(), "authorize-key");
        verifyNoMoreInteractions(gateway);
    }

    private static PaymentIntent payment(UUID id, BigDecimal amount, PaymentStatus status,
                                         String reference, Instant now) {
        return new PaymentIntent(id, UUID.randomUUID(), amount, "EUR", status,
                "payment-key-" + id, reference, null, now, now);
    }

    private static PaymentGatewayOperationEntity operation(
            UUID paymentId, PaymentOperationType type, String idempotencyKey) {
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        when(operation.getId()).thenReturn(UUID.randomUUID());
        when(operation.getPaymentId()).thenReturn(paymentId);
        when(operation.getOperationType()).thenReturn(type);
        when(operation.getIdempotencyKey()).thenReturn(idempotencyKey);
        when(operation.getLockedBy()).thenReturn(UUID.randomUUID().toString());
        return operation;
    }

    private static PaymentIntentEntity mockPaymentEntity(PaymentIntent payment) {
        PaymentIntentEntity entity = mock(PaymentIntentEntity.class);
        when(entity.toDomain()).thenReturn(payment);
        return entity;
    }
}
