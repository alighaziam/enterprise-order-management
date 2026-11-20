package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationMonitoring;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Exposes durable payment-operation backlog counts with a bounded status tag.
 * The status set is an enum, so this meter cannot create unbounded cardinality.
 */
@Component
public class PaymentOperationMetrics {

    public static final String OPERATIONS_METRIC = "eom.payment.operations";

    public PaymentOperationMetrics(PaymentOperationMonitoring monitoring, MeterRegistry registry) {
        for (PaymentOperationStatus status : PaymentOperationStatus.values()) {
            Gauge.builder(OPERATIONS_METRIC, monitoring, source -> source.count(status))
                    .description("Persisted payment gateway operations by lifecycle status")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
    }
}
