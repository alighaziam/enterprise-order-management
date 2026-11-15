package io.github.alighaziam.eom.outbox.infrastructure;

import io.github.alighaziam.eom.outbox.IntegrationEvent;
import io.github.alighaziam.eom.outbox.OutboxEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "kafka")
public class KafkaOutboxEventPublisher implements OutboxEventPublisher {

    private final KafkaTemplate<Object, Object> kafka;
    private final String topic;
    private final long timeoutMillis;

    public KafkaOutboxEventPublisher(
            KafkaTemplate<Object, Object> kafka,
            @Value("${app.outbox.kafka.topic:eom.outbox.events}") String topic,
            @Value("${app.outbox.kafka.publish-timeout-ms:10000}") long timeoutMillis) {
        this.kafka = kafka;
        this.topic = topic;
        this.timeoutMillis = Math.max(1, timeoutMillis);
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

    private static String toJson(IntegrationEvent event) {
        return "{"
                + "\"eventId\":" + quote(event.eventId().toString()) + ","
                + "\"eventType\":" + quote(event.eventType()) + ","
                + "\"schemaVersion\":" + event.schemaVersion() + ","
                + "\"occurredAt\":" + quote(event.occurredAt().toString()) + ","
                + "\"aggregateType\":" + quote(event.aggregateType()) + ","
                + "\"aggregateId\":" + quote(event.aggregateId()) + ","
                + "\"aggregateVersion\":" + event.aggregateVersion() + ","
                + "\"correlationId\":" + quote(event.correlationId()) + ","
                + "\"causationId\":" + quote(event.causationId()) + ","
                + "\"payload\":" + quote(event.payload())
                + "}";
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
