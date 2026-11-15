package io.github.alighaziam.eom.outbox;

/**
 * Transport port used by the outbox relay. Implementations must return only
 * after the transport has acknowledged the event.
 */
public interface OutboxEventPublisher {
    void publish(IntegrationEvent event);
}
