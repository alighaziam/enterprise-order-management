package io.github.alighaziam.eom;

import io.github.alighaziam.eom.checkout.CheckoutOperations;
import io.github.alighaziam.eom.outbox.OutboxOperations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
class OperationsHealthIndicator implements HealthIndicator {
    private final OutboxOperations outbox;
    private final CheckoutOperations checkout;

    OperationsHealthIndicator(OutboxOperations outbox, CheckoutOperations checkout) {
        this.outbox = outbox;
        this.checkout = checkout;
    }

    @Override
    public Health health() {
        return Health.up()
                .withDetail("deadLetters", outbox.deadLetterCount())
                .withDetail("compensationRequired", checkout.compensationRequiredCount())
                .build();
    }
}
