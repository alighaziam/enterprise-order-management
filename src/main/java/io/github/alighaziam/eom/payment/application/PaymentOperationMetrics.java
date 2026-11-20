package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationMonitoring;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Exposes payment-operation backlog counts, oldest in-status age, and expired leases.
 * All labels are bounded by the PaymentOperationStatus enum.
 */
@Component
public class PaymentOperationMetrics {

    public static final String OPERATIONS_METRIC = "eom.payment.operations";
    public static final String OLDEST_AGE_METRIC = "eom.payment.operations.oldest.age";
    public static final String EXPIRED_LEASES_METRIC = "eom.payment.operations.expired.leases";
    private static final List<PaymentOperationStatus> AGE_TRACKED_STATUSES =
            List.of(PaymentOperationStatus.PENDING, PaymentOperationStatus.UNKNOWN,
                    PaymentOperationStatus.RECONCILING);

    public PaymentOperationMetrics(PaymentOperationMonitoring monitoring, MeterRegistry registry) {
        for (PaymentOperationStatus status : PaymentOperationStatus.values()) {
            Gauge.builder(OPERATIONS_METRIC, monitoring, source -> source.count(status))
                    .description("Persisted payment gateway operations by lifecycle status")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
        for (PaymentOperationStatus status : AGE_TRACKED_STATUSES) {
            Gauge.builder(OLDEST_AGE_METRIC, monitoring, source -> source.oldestAgeSeconds(status))
                    .description("Age in seconds of the oldest payment operation in its current status")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
        Gauge.builder(EXPIRED_LEASES_METRIC, monitoring, PaymentOperationMonitoring::expiredLeaseCount)
                .description("Payment operations with expired worker leases awaiting recovery")
                .register(registry);
    }
}
