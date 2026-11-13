package io.github.alighaziam.eom.outbox.infrastructure;

import io.github.alighaziam.eom.outbox.IntegrationEvent;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "messaging")
public class OutboxEntity {
    private static final int MAX_ATTEMPTS = 12;

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 160)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "aggregate_type", nullable = false, length = 120)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "correlation_id", nullable = false, length = 120)
    private String correlationId;

    @Column(name = "causation_id", length = 120)
    private String causationId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "locked_by", length = 80)
    private String lockedBy;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    protected OutboxEntity() {
    }

    public OutboxEntity(UUID id, String type, int version, Instant occurred,
                        String aggregateType, String aggregateId, long aggregateVersion,
                        String correlationId, String causationId, String payload, Instant created) {
        this.eventId = id;
        this.eventType = type;
        this.schemaVersion = version;
        this.occurredAt = occurred;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.payload = payload;
        this.createdAt = created;
        this.nextAttemptAt = created;
    }

    public IntegrationEvent toEvent() {
        return new IntegrationEvent(eventId, eventType, schemaVersion, occurredAt, aggregateType,
                aggregateId, aggregateVersion, correlationId, causationId, payload);
    }

    public void published(Instant at) {
        publishedAt = at;
        lastError = null;
        clearLease();
    }

    public void failed(String error, Instant at) {
        attempts++;
        lastError = error == null ? "Unknown relay failure"
                : error.substring(0, Math.min(1000, error.length()));
        clearLease();

        if (attempts >= MAX_ATTEMPTS) {
            deadLetteredAt = at;
            return;
        }

        long delaySeconds = Math.min(3600L, 1L << Math.min(attempts, 12));
        nextAttemptAt = at.plusSeconds(delaySeconds);
    }

    private void clearLease() {
        lockedBy = null;
        lockedUntil = null;
    }

    public void requeue(Instant at) {
        deadLetteredAt = null;
        publishedAt = null;
        attempts = 0;
        lastError = null;
        nextAttemptAt = at;
        clearLease();
    }

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getDeadLetteredAt() { return deadLetteredAt; }
    public String getLockedBy() { return lockedBy; }
}
