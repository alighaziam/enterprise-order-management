package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.payment.application.PaymentGatewayReconciliation;
import io.github.alighaziam.eom.payment.application.PaymentGatewayReconciliationWorker;
import io.github.alighaziam.eom.payment.application.ProviderOperationLookup;
import io.github.alighaziam.eom.payment.application.ProviderOperationState;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationClaimService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationReconciliationService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentIntentEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentGatewayReconciliationWorkerTest {

    private static PaymentIntentEntity payment(UUID paymentId, Instant now) {
        return new PaymentIntentEntity(paymentId, UUID.randomUUID(),
                new BigDecimal("12.50"), "EUR", "payment-key-" + paymentId, null, now);
    }

    @Test
    void passesPersistedAmountAndCurrencyToReadOnlyProviderLookup() {
        Instant now = Instant.now();
        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGatewayReconciliation gateway = mock(PaymentGatewayReconciliation.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        UUID operationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        when(operation.getId()).thenReturn(operationId);
        when(operation.getPaymentId()).thenReturn(paymentId);
        when(operation.getLockedBy()).thenReturn(workerId);
        when(operation.getOperationType()).thenReturn(PaymentOperationType.AUTHORIZE);
        when(operation.getIdempotencyKey()).thenReturn("stable-operation-key");
        when(operation.getProviderReference()).thenReturn(null);
        when(claims.claimUnknownBatch(eq(1), any(Instant.class))).thenReturn(List.of(operation), List.of());
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment(paymentId, now)));
        when(gateway.lookup(PaymentOperationType.AUTHORIZE, "stable-operation-key", null,
                new BigDecimal("12.50"), "EUR"))
                .thenReturn(new ProviderOperationLookup(ProviderOperationState.INDETERMINATE,
                        null, "TIMEOUT", now, false));
        when(results.apply(eq(operationId), eq(workerId), any(), any(Instant.class))).thenReturn(true);

        PaymentGatewayReconciliationWorker worker =
                new PaymentGatewayReconciliationWorker(claims, gateway, payments, results);

        assertEquals(1, worker.reconcileBatch(now));
        verify(claims).recoverExpiredProcessing(now);
        verify(claims, times(2)).claimUnknownBatch(eq(1), any(Instant.class));
        verify(payments).findById(paymentId);
        verify(gateway).lookup(PaymentOperationType.AUTHORIZE, "stable-operation-key", null,
                new BigDecimal("12.50"), "EUR");
        verify(results).apply(eq(operationId), eq(workerId), any(ProviderOperationLookup.class), any(Instant.class));
        verifyNoMoreInteractions(claims);
    }

    @Test
    void reconciliationPersistenceFailureIsNotMisclassifiedAsProviderLookupFailure() {
        Instant now = Instant.now();
        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGatewayReconciliation gateway = mock(PaymentGatewayReconciliation.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        UUID operationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        when(operation.getId()).thenReturn(operationId);
        when(operation.getPaymentId()).thenReturn(paymentId);
        when(operation.getLockedBy()).thenReturn(workerId);
        when(operation.getOperationType()).thenReturn(PaymentOperationType.AUTHORIZE);
        when(operation.getIdempotencyKey()).thenReturn("stable-operation-key");
        when(operation.getProviderReference()).thenReturn(null);
        when(claims.claimUnknownBatch(eq(1), any(Instant.class))).thenReturn(List.of(operation), List.of());
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment(paymentId, now)));
        when(gateway.lookup(eq(PaymentOperationType.AUTHORIZE), eq("stable-operation-key"), isNull(),
                eq(new BigDecimal("12.50")), eq("EUR")))
                .thenReturn(new ProviderOperationLookup(ProviderOperationState.SUCCEEDED,
                        "provider-reference", "APPROVED", now, false));
        when(results.apply(eq(operationId), eq(workerId), any(), any(Instant.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        PaymentGatewayReconciliationWorker worker =
                new PaymentGatewayReconciliationWorker(claims, gateway, payments, results);

        assertThrows(IllegalStateException.class, () -> worker.reconcileBatch(now));
        verify(results, never()).markUnknown(any(), anyString(), anyString(), any(Instant.class));
        verify(gateway).lookup(eq(PaymentOperationType.AUTHORIZE), eq("stable-operation-key"), isNull(),
                eq(new BigDecimal("12.50")), eq("EUR"));
    }

    @Test
    void lookupFailureLeavesOperationUnknownAndNeverPersistsExceptionText() {
        Instant now = Instant.now();
        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGatewayReconciliation gateway = mock(PaymentGatewayReconciliation.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        UUID operationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        when(operation.getId()).thenReturn(operationId);
        when(operation.getPaymentId()).thenReturn(paymentId);
        when(operation.getLockedBy()).thenReturn(workerId);
        when(operation.getOperationType()).thenReturn(PaymentOperationType.REFUND);
        when(operation.getIdempotencyKey()).thenReturn("stable-refund-key");
        when(operation.getProviderReference()).thenReturn("provider-ref");
        when(claims.claimUnknownBatch(eq(1), any(Instant.class))).thenReturn(List.of(operation), List.of());
        when(payments.findById(paymentId)).thenReturn(Optional.of(payment(paymentId, now)));
        when(gateway.lookup(eq(PaymentOperationType.REFUND), eq("stable-refund-key"), eq("provider-ref"),
                eq(new BigDecimal("12.50")), eq("EUR")))
                .thenThrow(new IllegalStateException("secret=do-not-persist"));
        when(results.markUnknown(eq(operationId), eq(workerId), anyString(), any(Instant.class))).thenReturn(true);

        PaymentGatewayReconciliationWorker worker =
                new PaymentGatewayReconciliationWorker(claims, gateway, payments, results);

        assertEquals(1, worker.reconcileBatch(now));
        verify(gateway).lookup(PaymentOperationType.REFUND, "stable-refund-key", "provider-ref",
                new BigDecimal("12.50"), "EUR");
        verify(results).markUnknown(eq(operationId), eq(workerId),
                eq("Provider reconciliation lookup failed without a definitive outcome."), any(Instant.class));
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void missingPaymentIntentSkipsProviderLookupAndKeepsOperationUnknown() {
        Instant now = Instant.now();
        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGatewayReconciliation gateway = mock(PaymentGatewayReconciliation.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        UUID operationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        when(operation.getId()).thenReturn(operationId);
        when(operation.getPaymentId()).thenReturn(paymentId);
        when(operation.getLockedBy()).thenReturn(workerId);
        when(claims.claimUnknownBatch(eq(1), any(Instant.class))).thenReturn(List.of(operation), List.of());
        when(payments.findById(paymentId)).thenReturn(Optional.empty());
        when(results.markUnknown(eq(operationId), eq(workerId), anyString(), any(Instant.class))).thenReturn(true);

        PaymentGatewayReconciliationWorker worker =
                new PaymentGatewayReconciliationWorker(claims, gateway, payments, results);

        assertEquals(1, worker.reconcileBatch(now));
        verify(gateway, never()).lookup(any(), anyString(), any(), any(), anyString());
        verify(results).markUnknown(eq(operationId), eq(workerId),
                eq("Payment intent is missing; provider reconciliation lookup was not attempted."),
                any(Instant.class));
        verifyNoInteractions(gateway);
    }
}
