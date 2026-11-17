package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.outbox.OutboxOperations;
import io.github.alighaziam.eom.payment.application.ProviderOperationLookup;
import io.github.alighaziam.eom.payment.application.ProviderOperationState;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationReconciliationService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import io.github.alighaziam.eom.payment.infrastructure.PaymentIntentEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentGatewayOperationReconciliationServiceTest {

    @Test
    void confirmedAuthorizationUpdatesPaymentAndOperationAndPublishesOutboxEvent() {
        Instant now = Instant.now();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentIntentEntity payment = new PaymentIntentEntity(paymentId, orderId,
                new BigDecimal("12.50"), "EUR", "payment-key-" + paymentId, null, now);
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        PaymentGatewayOperationRepository operations = mock(PaymentGatewayOperationRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        OutboxOperations outbox = mock(OutboxOperations.class);
        String workerId = UUID.randomUUID().toString();

        when(operation.ownsLease(workerId, now)).thenReturn(true);
        when(operation.getPaymentId()).thenReturn(paymentId);
        when(operation.getOperationType()).thenReturn(PaymentOperationType.AUTHORIZE);
        when(operation.getProviderReference()).thenReturn(null);
        when(operations.findByIdForUpdate(any())).thenReturn(Optional.of(operation));
        when(payments.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        PaymentGatewayOperationReconciliationService service =
                new PaymentGatewayOperationReconciliationService(operations, payments, outbox);
        ProviderOperationLookup lookup = new ProviderOperationLookup(
                ProviderOperationState.SUCCEEDED, "provider-auth-123", "APPROVED", now, false);

        assertTrue(service.apply(UUID.randomUUID(), workerId, lookup, now));
        assertEquals(PaymentStatus.AUTHORIZED, payment.toDomain().status());
        assertEquals("provider-auth-123", payment.toDomain().providerReference());
        verify(operation).succeeded("provider-auth-123", workerId, now);
        verify(payments).saveAndFlush(payment);
        verify(operations).saveAndFlush(operation);
        verify(outbox).append(eq("PaymentAuthorized"), eq("PaymentIntent"), eq(paymentId.toString()),
                eq(1L), eq(orderId.toString()), isNull(), contains(paymentId.toString()));
    }

    @Test
    void authoritativeNotFoundNeverResubmitsOrChangesPaymentState() {
        Instant now = Instant.now();
        UUID paymentId = UUID.randomUUID();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        PaymentGatewayOperationRepository operations = mock(PaymentGatewayOperationRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        OutboxOperations outbox = mock(OutboxOperations.class);
        String workerId = UUID.randomUUID().toString();

        when(operation.ownsLease(workerId, now)).thenReturn(true);
        when(operation.getOperationType()).thenReturn(PaymentOperationType.AUTHORIZE);
        when(operations.findByIdForUpdate(any())).thenReturn(Optional.of(operation));

        PaymentGatewayOperationReconciliationService service =
                new PaymentGatewayOperationReconciliationService(operations, payments, outbox);
        ProviderOperationLookup lookup = new ProviderOperationLookup(
                ProviderOperationState.NOT_FOUND, null, "NOT_FOUND", now, true);

        assertTrue(service.apply(UUID.randomUUID(), workerId, lookup, now));
        verify(operation).unknown(contains("automatic resubmission is disabled"), eq(workerId), eq(now));
        verifyNoInteractions(payments, outbox);
        verify(operations).saveAndFlush(operation);
    }

    @Test
    void expiredWorkerLeaseCannotPersistProviderSuccess() {
        Instant now = Instant.now();
        PaymentGatewayOperationEntity operation = mock(PaymentGatewayOperationEntity.class);
        PaymentGatewayOperationRepository operations = mock(PaymentGatewayOperationRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        OutboxOperations outbox = mock(OutboxOperations.class);
        String workerId = UUID.randomUUID().toString();
        when(operation.ownsLease(workerId, now)).thenReturn(false);
        when(operations.findByIdForUpdate(any())).thenReturn(Optional.of(operation));

        PaymentGatewayOperationReconciliationService service =
                new PaymentGatewayOperationReconciliationService(operations, payments, outbox);
        ProviderOperationLookup lookup = new ProviderOperationLookup(
                ProviderOperationState.SUCCEEDED, "provider-ref", "APPROVED", now, false);

        assertFalse(service.apply(UUID.randomUUID(), workerId, lookup, now));
        verifyNoInteractions(payments, outbox);
        verify(operations, never()).saveAndFlush(any());
    }
}
