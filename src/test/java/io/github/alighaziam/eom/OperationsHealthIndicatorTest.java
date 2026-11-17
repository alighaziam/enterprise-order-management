package io.github.alighaziam.eom;

import io.github.alighaziam.eom.checkout.CheckoutOperations;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.github.alighaziam.eom.payment.application.PaymentOperationMonitoring;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class OperationsHealthIndicatorTest {

    @Test
    void exposesDeadLettersCompensationAndAllPaymentOperationStates() {
        OutboxOperations outbox = mock(OutboxOperations.class);
        CheckoutOperations checkout = mock(CheckoutOperations.class);
        PaymentOperationMonitoring payments = mock(PaymentOperationMonitoring.class);
        when(outbox.deadLetterCount()).thenReturn(2L);
        when(checkout.compensationRequiredCount()).thenReturn(1L);
        when(payments.count(any())).thenAnswer(invocation -> {
            PaymentOperationStatus status = invocation.getArgument(0);
            return status == PaymentOperationStatus.UNKNOWN ? 4L : 0L;
        });

        var health = new OperationsHealthIndicator(outbox, checkout, payments).health();

        assertEquals(2L, health.getDetails().get("deadLetters"));
        assertEquals(1L, health.getDetails().get("compensationRequired"));
        assertEquals(4L, health.getDetails().get("paymentOperations.unknown"));
        for (PaymentOperationStatus status : PaymentOperationStatus.values()) {
            verify(payments).count(status);
        }
    }
}
