package io.github.alighaziam.eom.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.alighaziam.eom.outbox.infrastructure.KafkaOutboxEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaOutboxEventPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void publishesValidJsonWithAggregateKeyAndWaitsForBrokerAcknowledgement() throws Exception {
        KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<Object, Object>> acknowledgement = CompletableFuture.completedFuture(null);
        when(kafka.send(eq("eom.outbox.events"), eq("Order:order-1"), any()))
                .thenReturn(acknowledgement);

        KafkaOutboxEventPublisher publisher = new KafkaOutboxEventPublisher(
                kafka, objectMapper, "eom.outbox.events", 1000);
        String payload = "{\"orderId\":\"order-1\",\"note\":\"quoted \\\"value\\\"\\nnext line\"}";
        IntegrationEvent event = new IntegrationEvent(UUID.randomUUID(), "OrderPlaced", 1,
                Instant.parse("2026-11-15T12:00:00Z"), "Order", "order-1", 1,
                "correlation-1", null, payload);

        publisher.publish(event);

        verify(kafka).send(eq("eom.outbox.events"), eq("Order:order-1"), argThat(value -> {
            try {
                JsonNode json = objectMapper.readTree(value.toString());
                return "OrderPlaced".equals(json.path("eventType").asText())
                        && json.path("aggregateVersion").asLong() == 1
                        && json.path("causationId").isNull()
                        && json.path("payload").isTextual()
                        && payload.equals(json.path("payload").asText());
            } catch (Exception invalidJson) {
                return false;
            }
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportsFailureWhenBrokerDoesNotAcknowledge() {
        KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<Object, Object>> failure = new CompletableFuture<>();
        failure.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send(anyString(), any(), any())).thenReturn(failure);

        KafkaOutboxEventPublisher publisher = new KafkaOutboxEventPublisher(
                kafka, objectMapper, "eom.outbox.events", 1000);
        IntegrationEvent event = new IntegrationEvent(UUID.randomUUID(), "OrderPlaced", 1,
                Instant.now(), "Order", "order-1", 1, "correlation-1", null, "{}");

        assertThrows(IllegalStateException.class, () -> publisher.publish(event));
    }
}
