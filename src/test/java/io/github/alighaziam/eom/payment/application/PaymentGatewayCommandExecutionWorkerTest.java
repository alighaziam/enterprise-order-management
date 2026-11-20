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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentGatewayCommandExecutionWorkerTest {

    @Test
    void dispatchesEachPersistedOperationTypeUsingTheExistingPaymentReference() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        UUID paymentId = UUID.randomUUID();
        PaymentIntent payment = new PaymentIntent(paymentId, UUID.randomUUID(),
                new BigDecimal("18.50"), "EUR", PaymentStatus.AUTHORIZED,
                "payment-key", "provider-ref-123", null, now, now);

        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);

        List<PaymentGatewayOperationEntity> operations = List.of(
                operation(paymentId, PaymentOperationType.AUTHORIZE, "authorize-key"),
                operation(paymentId, PaymentOperationType.CAPTURE, "capture-key"),
                operation(paymentId, PaymentOperationType.VOID, "void-key"),
                operation(paymentId, PaymentOperationType.REFUND, "refund-key"));
        when(claims.claimPendingBatch(20, now)).thenReturn(operations);
        when(payments.findById(paymentId)).thenReturn(Optional.of(mockPaymentEntity(payment)));
        when(gateway.authorize(paymentId, payment.amount(), payment.currency(), "authorize-key"))
                .thenReturn("provider-auth-456");
        when(results.apply(any(), anyString(), any(ProviderOperationLookup.class), any(Instant.class)))
                .thenReturn(true);

        PaymentGatewayCommandExecutionWorker worker =
                new PaymentGatewayCommandExecutionWorker(claims, gateway, payments, results);

        assertEquals(4, worker.executeBatch(now));

        InOrder claimOrder = inOrder(claims);
        claimOrder.verify(claims).recoverExpiredProcessing(now);
        claimOrder.verify(claims).claimPendingBatch(20, now);
        verify(gateway).authorize(paymentId, payment.amount(), payment.currency(), "authorize-key");
        verify(gateway).capture("provider-ref-123");
        verify(gateway).voidAuthorization("provider-ref-123");
        verify(gateway).refund("provider-ref-123");
        verify(results, times(4)).apply(any(), anyString(), any(ProviderOperationLookup.class), any(Instant.class));
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void missingPaymentIsNotSubmittedAndOperationRemainsUnknown() {
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
        when(results.markUnknown(eq(operation.getId()), eq(workerId), contains("Payment intent is missing"),
                any(Instant.class))).thenReturn(true);

        PaymentGatewayCommandExecutionWorker worker =
                new PaymentGatewayCommandExecutionWorker(claims, gateway, payments, results);

        assertEquals(1, worker.executeBatch(now));
        verifyNoInteractions(gateway);
        verify(results).markUnknown(eq(operation.getId()), eq(workerId),
                eq("Payment intent is missing; provider command was not submitted."), any(Instant.class));
    }

    @Test
    void providerExceptionIsRecordedAsUnknownWithoutPersistingExceptionText() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        UUID paymentId = UUID.randomUUID();
        PaymentIntent payment = new PaymentIntent(paymentId, UUID.randomUUID(),
                new BigDecimal("18.50"), "EUR", PaymentStatus.REQUESTED,
                "payment-key", null, null, now, now);
        PaymentGatewayOperationEntity operation =
                operation(paymentId, PaymentOperationType.AUTHORIZE, "authorize-key");
        String workerId = operation.getLockedBy();

        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        when(claims.claimPendingBatch(20, now)).thenReturn(List.of(operation));
        when(payments.findById(paymentId)).thenReturn(Optional.of(mockPaymentEntity(payment)));
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
