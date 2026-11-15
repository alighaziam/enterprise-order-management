package io.github.alighaziam.eom.outbox.infrastructure;

import io.github.alighaziam.eom.outbox.IntegrationEvent;
import io.github.alighaziam.eom.outbox.OutboxEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "in-process", matchIfMissing = true)
public class InProcessOutboxEventPublisher implements OutboxEventPublisher {

    private final ApplicationEventPublisher publisher;

    public InProcessOutboxEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(IntegrationEvent event) {
        publisher.publishEvent(event);
    }
}
