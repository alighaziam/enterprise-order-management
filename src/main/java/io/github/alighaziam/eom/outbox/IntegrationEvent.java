package io.github.alighaziam.eom.outbox;
import java.time.Instant; import java.util.UUID;
public record IntegrationEvent(UUID eventId,String eventType,int schemaVersion,Instant occurredAt,String aggregateType,String aggregateId,long aggregateVersion,String correlationId,String causationId,String payload) {}