package io.github.alighaziam.eom.outbox;
import java.util.UUID;
public interface OutboxOperations {
 UUID append(String eventType,String aggregateType,String aggregateId,long aggregateVersion,String correlationId,String causationId,String payload);
 long deadLetterCount();
}