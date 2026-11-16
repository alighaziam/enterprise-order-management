package io.github.alighaziam.eom.outbox.infrastructure;

import io.github.alighaziam.eom.outbox.IntegrationEvent;
import io.github.alighaziam.eom.outbox.OutboxEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "kafka")
public class KafkaOutboxEventPublisher implements OutboxEventPublisher {

    private static final long MAX_PUBLISH_TIMEOUT_MILLIS = 10_000;

    private final KafkaTemplate<Object, Object> kafka;
    private final JsonMapper jsonMapper;
    private final String topic;
    private final long timeoutMillis;

    public KafkaOutboxEventPublisher(
            KafkaTemplate<Object, Object> kafka,
            JsonMapper jsonMapper,
            @Value("${app.outbox.kafka.topic:eom.outbox.events}") String topic,
            @Value("${app.outbox.kafka.publish-timeout-ms:10000}") long timeoutMillis) {
        this.kafka = kafka;
        this.jsonMapper = jsonMapper;
        this.topic = topic;
        this.timeoutMillis = Math.max(1, Math.min(MAX_PUBLISH_TIMEOUT_MILLIS, timeoutMillis));
    }

    @Override
    public void publish(IntegrationEvent event) {
        String key = event.aggregateType() + ":" + event.aggregateId();
        String json = toJson(event);
        try {
            kafka.send(topic, key, json).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kafka acknowledgement.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Kafka did not acknowledge the outbox event.", exception);
        }
    }

    private String toJson(IntegrationEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId());
        envelope.put("eventType", event.eventType());
        envelope.put("schemaVersion", event.schemaVersion());
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("aggregateType", event.aggregateType());
        envelope.put("aggregateId", event.aggregateId());
        envelope.put("aggregateVersion", event.aggregateVersion());
        envelope.put("correlationId", event.correlationId());
        envelope.put("causationId", event.causationId());
        // Keep payload as a JSON string for wire compatibility with existing consumers.
        envelope.put("payload", event.payload());
        try {
            return jsonMapper.writeValueAsString(envelope);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the outbox event envelope.", exception);
        }
    }
}
