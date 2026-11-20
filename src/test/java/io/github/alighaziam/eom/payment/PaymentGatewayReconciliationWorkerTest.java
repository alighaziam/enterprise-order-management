package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.payment.application.PaymentGatewayReconciliation;
import io.github.alighaziam.eom.payment.application.PaymentGatewayReconciliationWorker;
import io.github.alighaziam.eom.payment.application.ProviderOperationLookup;
import io.github.alighaziam.eom.payment.application.ProviderOperationState;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationClaimService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationReconciliationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentGatewayReconciliationWorkerTest {

    @Test
    void recoversExpiredLeasesBeforeClaimingAndPerformsLookupOutsideClaimService() {
        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGatewayReconciliation gateway = mock(PaymentGatewayReconciliation.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        UUID operationId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        when(operation.getId()).thenReturn(operationId);
        when(operation.getLockedBy()).thenReturn(workerId);
        when(operation.getOperationType()).thenReturn(PaymentOperationType.AUTHORIZE);
        when(operation.getIdempotencyKey()).thenReturn("stable-operation-key");
        when(operation.getProviderReference()).thenReturn(null);
        when(claims.claimUnknownBatch(eq(1), any(Instant.class))).thenReturn(List.of(operation), List.of());
        when(gateway.lookup(PaymentOperationType.AUTHORIZE, "stable-operation-key", null))
                .thenReturn(new ProviderOperationLookup(ProviderOperationState.INDETERMINATE,
                        null, "TIMEOUT", Instant.now(), false));
        when(results.apply(eq(operationId), eq(workerId), any(), any(Instant.class))).thenReturn(true);

        PaymentGatewayReconciliationWorker worker =
                new PaymentGatewayReconciliationWorker(claims, gateway, results);

        assertEquals(1, worker.reconcileBatch(Instant.now()));
        verify(claims).recoverExpiredProcessing(any(Instant.class));
        verify(claims, times(2)).claimUnknownBatch(eq(1), any(Instant.class));
        verify(gateway).lookup(PaymentOperationType.AUTHORIZE, "stable-operation-key", null);
        verify(results).apply(eq(operationId), eq(workerId), any(ProviderOperationLookup.class), any(Instant.class));
        verifyNoMoreInteractions(claims);
    }

    @Test
    void reconciliationPersistenceFailureIsNotMisclassifiedAsProviderLookupFailure() {
        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGatewayReconciliation gateway = mock(PaymentGatewayReconciliation.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        UUID operationId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        when(operation.getId()).thenReturn(operationId);
        when(operation.getLockedBy()).thenReturn(workerId);
        when(operation.getOperationType()).thenReturn(PaymentOperationType.AUTHORIZE);
        when(operation.getIdempotencyKey()).thenReturn("stable-operation-key");
        when(operation.getProviderReference()).thenReturn(null);
        when(claims.claimUnknownBatch(eq(1), any(Instant.class))).thenReturn(List.of(operation), List.of());
        when(gateway.lookup(PaymentOperationType.AUTHORIZE, "stable-operation-key", null))
                .thenReturn(new ProviderOperationLookup(ProviderOperationState.SUCCEEDED,
                        "provider-reference", "APPROVED", Instant.now(), false));
        when(results.apply(eq(operationId), eq(workerId), any(), any(Instant.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        PaymentGatewayReconciliationWorker worker =
                new PaymentGatewayReconciliationWorker(claims, gateway, results);

        assertThrows(IllegalStateException.class, () -> worker.reconcileBatch(Instant.now()));
        verify(results, never()).markUnknown(any(), anyString(), anyString(), any(Instant.class));
        verify(gateway).lookup(PaymentOperationType.AUTHORIZE, "stable-operation-key", null);
    }

    @Test
    void lookupFailureLeavesOperationUnknownAndNeverPersistsExceptionText() {
        PaymentGatewayOperationClaimService claims = mock(PaymentGatewayOperationClaimService.class);
        PaymentGatewayReconciliation gateway = mock(PaymentGatewayReconciliation.class);
        PaymentGatewayOperationReconciliationService results =
                mock(PaymentGatewayOperationReconciliationService.class);
        UUID operationId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        when(operation.getId()).thenReturn(operationId);
        when(operation.getLockedBy()).thenReturn(workerId);
        when(operation.getOperationType()).thenReturn(PaymentOperationType.REFUND);
        when(operation.getIdempotencyKey()).thenReturn("stable-refund-key");
        when(operation.getProviderReference()).thenReturn("provider-ref");
        when(claims.claimUnknownBatch(eq(1), any(Instant.class))).thenReturn(List.of(operation), List.of());
        when(gateway.lookup(PaymentOperationType.REFUND, "stable-refund-key", "provider-ref"))
                .thenThrow(new IllegalStateException("secret=do-not-persist"));
        when(results.markUnknown(eq(operationId), eq(workerId), anyString(), any(Instant.class))).thenReturn(true);

        PaymentGatewayReconciliationWorker worker =
                new PaymentGatewayReconciliationWorker(claims, gateway, results);

        assertEquals(1, worker.reconcileBatch(Instant.now()));
        verify(gateway).lookup(PaymentOperationType.REFUND, "stable-refund-key", "provider-ref");
        verify(results).markUnknown(eq(operationId), eq(workerId),
                eq("Provider reconciliation lookup failed without a definitive outcome."), any(Instant.class));
        verifyNoMoreInteractions(gateway);
    }
}
