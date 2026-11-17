package io.github.alighaziam.eom.operations.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operator_actions", schema = "operations")
public class OperatorActionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String actor;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", nullable = false, length = 80)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 120)
    private String targetId;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OperatorActionEntity() {
    }

    public OperatorActionEntity(UUID id, String actor, String action, String targetType,
                                String targetId, String outcome, String detail, Instant occurredAt) {
        this.id = require(actor, 200, "actor");
        this.actor = normalize(actor, 200);
        this.action = normalize(action, 80);
        this.targetType = normalize(targetType, 80);
        this.targetId = normalize(targetId, 120);
        this.outcome = normalize(outcome, 16);
        this.detail = normalize(detail, 500);
        this.occurredAt = occurredAt;
        if (id == null || occurredAt == null
                || !this.action.matches("[A-Z_]+")
                || !this.outcome.matches("SUCCEEDED|FAILED")) {
            throw new IllegalArgumentException("A valid operator audit entry is required.");
        }
        this.id = id;
    }

    private static String require(String value, int max, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException("A valid " + field + " is required.");
        }
        return value;
    }

    private static String normalize(String value, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Audit fields cannot be blank.");
        }
        String clean = value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
        if (clean.isBlank() || clean.length() > max) {
            throw new IllegalArgumentException("Audit field exceeds its allowed length.");
        }
        return clean;
    }

    public UUID getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getOutcome() { return outcome; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
