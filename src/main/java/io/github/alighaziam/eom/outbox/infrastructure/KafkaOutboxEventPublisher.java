package io.github.alighaziam.eom.outbox.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final String topic;
    private final long timeoutMillis;

    public KafkaOutboxEventPublisher(
            KafkaTemplate<Object, Object> kafka,
            ObjectMapper objectMapper,
            @Value("${app.outbox.kafka.topic:eom.outbox.events}") String topic,
            @Value("${app.outbox.kafka.publish-timeout-ms:10000}") long timeoutMillis) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.timeoutMillis = Math.max(1, timeoutMillis);
    }

    @Override
    public void publish(IntegrationEvent event) {
        String key = event.aggregateType() + ":" + event.aggregateId();
        try {
            String json = objectMapper.writeValueAsString(event);
            kafka.send(topic, key, json).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox event could not be serialized.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kafka acknowledgement.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Kafka did not acknowledge the outbox event.", exception);
        }
    }
}
