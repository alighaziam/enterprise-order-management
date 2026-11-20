package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationMonitoring;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PaymentOperationMetricsTest {

    @Test
    void registersOneBoundedGaugeForEveryPaymentOperationStatus() {
        PaymentOperationMonitoring monitoring = mock(PaymentOperationMonitoring.class);
        when(monitoring.count(PaymentOperationStatus.UNKNOWN)).thenReturn(3L);
        when(monitoring.count(PaymentOperationStatus.PENDING)).thenReturn(7L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new PaymentOperationMetrics(monitoring, registry);

            assertEquals(PaymentOperationStatus.values().length,
                    registry.find(PaymentOperationMetrics.OPERATIONS_METRIC).gauges().size());
            assertEquals(3.0, registry.get(PaymentOperationMetrics.OPERATIONS_METRIC)
                    .tag("status", "unknown").gauge().value());
            assertEquals(7.0, registry.get(PaymentOperationMetrics.OPERATIONS_METRIC)
                    .tag("status", "pending").gauge().value());
            verify(monitoring).count(PaymentOperationStatus.UNKNOWN);
            verify(monitoring).count(PaymentOperationStatus.PENDING);
        } finally {
            registry.close();
        }
    }
}
