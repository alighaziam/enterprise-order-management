package io.github.alighaziam.eom;

import io.github.alighaziam.eom.checkout.CheckoutOperations;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import io.github.alighaziam.eom.payment.PaymentOperationMonitoring;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
class OperationsHealthIndicator implements HealthIndicator {
    private final OutboxOperations outbox;
    private final CheckoutOperations checkout;
    private final PaymentOperationMonitoring payments;

    OperationsHealthIndicator(OutboxOperations outbox,
                              CheckoutOperations checkout,
                              PaymentOperationMonitoring payments) {
        this.outbox = outbox;
        this.checkout = checkout;
        this.payments = payments;
    }

    @Override
    public Health health() {
        Health.Builder health = Health.up()
                .withDetail("deadLetters", outbox.deadLetterCount())
                .withDetail("compensationRequired", checkout.compensationRequiredCount());

        for (PaymentOperationStatus status : PaymentOperationStatus.values()) {
            health.withDetail("paymentOperations." + status.name().toLowerCase(Locale.ROOT),
                    payments.count(status));
        }
        return health.build();
    }
}
