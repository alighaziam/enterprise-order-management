package io.github.alighaziam.eom.payment.infrastructure;

import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.github.alighaziam.eom.payment.PaymentOperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gateway_operations", schema = "payment")
public class PaymentGatewayOperationEntity {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 16)
    private PaymentOperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentOperationStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_by", length = 36)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PaymentGatewayOperationEntity() {
    }

    public PaymentGatewayOperationEntity(UUID id, UUID paymentId, PaymentOperationType operationType,
                                         String idempotencyKey, String requestFingerprint, Instant now) {
        if (id == null || paymentId == null || operationType == null || idempotencyKey == null
                || idempotencyKey.isBlank() || idempotencyKey.length() > 120
                || requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}") || now == null) {
            throw new IllegalArgumentException("A valid persisted payment operation is required.");
        }
        this.id = id;
        this.paymentId = paymentId;
        this.operationType = operationType;
        this.status = PaymentOperationStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void claim(String workerId, Instant leaseUntil, Instant now) {
        if (status != PaymentOperationStatus.PENDING || workerId == null || workerId.isBlank()
                || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalStateException("Only a pending operation can be claimed with a valid lease.");
        }
        status = PaymentOperationStatus.PROCESSING;
        lockedBy = workerId;
        lockedUntil = leaseUntil;
        attempts++;
        updatedAt = now;
    }

    public boolean ownsLease(String workerId, Instant now) {
        return workerId != null && workerId.equals(lockedBy)
                && lockedUntil != null && lockedUntil.isAfter(now)
                && (status == PaymentOperationStatus.PROCESSING
                    || status == PaymentOperationStatus.RECONCILING);
    }

    public void succeeded(String reference, String workerId, Instant now) {
        requireLease(workerId, now);
        status = PaymentOperationStatus.SUCCEEDED;
        providerReference = reference;
        completedAt = now;
        lastError = null;
        clearLease();
        updatedAt = now;
    }

    public void failed(String error, String workerId, Instant now) {
        requireLease(workerId, now);
        status = PaymentOperationStatus.FAILED;
        lastError = sanitize(error);
        completedAt = now;
        clearLease();
        updatedAt = now;
    }

    public void unknown(String error, String workerId, Instant now) {
        requireLease(workerId, now);
        status = PaymentOperationStatus.UNKNOWN;
        lastError = sanitize(error);
        clearLease();
        updatedAt = now;
    }

    public void markExpiredProcessingUnknown(Instant now) {
        if (status != PaymentOperationStatus.PROCESSING || lockedUntil == null
                || lockedUntil.isAfter(now)) {
            throw new IllegalStateException("Only an expired processing operation can become unknown.");
        }
        status = PaymentOperationStatus.UNKNOWN;
        lastError = "Worker lease expired; provider outcome requires reconciliation.";
        clearLease();
        updatedAt = now;
    }

    private void requireLease(String workerId, Instant now) {
        if (!ownsLease(workerId, now)) {
            throw new IllegalStateException("Payment operation lease is not owned or has expired.");
        }
    }

    private void clearLease() {
        lockedBy = null;
        lockedUntil = null;
    }

    private static String sanitize(String error) {
        if (error == null || error.isBlank()) return "Unspecified provider operation error";
        String normalized = error.replaceAll("[\\r\\n\\t]", " ");
        return normalized.substring(0, Math.min(500, normalized.length()));
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public PaymentOperationType getOperationType() { return operationType; }
    public PaymentOperationStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getProviderReference() { return providerReference; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedUntil() { return lockedUntil; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
