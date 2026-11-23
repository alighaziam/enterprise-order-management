package io.github.alighaziam.eom.shipping.infrastructure;

import io.github.alighaziam.eom.shipping.DeliveryAddress;
import io.github.alighaziam.eom.shipping.Shipment;
import io.github.alighaziam.eom.shipping.ShipmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments", schema = "shipping")
public class ShipmentEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShipmentStatus status;

    @Column(name = "recipient_name", nullable = false, length = 180)
    private String recipientName;

    @Column(name = "address_line1", nullable = false, length = 240)
    private String addressLine1;

    @Column(name = "address_line2", length = 240)
    private String addressLine2;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(name = "postal_code", length = 40)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(length = 40)
    private String phone;

    @Column(name = "carrier_reference", length = 120)
    private String carrierReference;

    @Column(name = "label_attempts", nullable = false)
    private int labelAttempts;

    @Column(name = "label_next_attempt_at", nullable = false)
    private Instant labelNextAttemptAt;

    @Column(name = "label_locked_by", length = 36)
    private String labelLockedBy;

    @Column(name = "label_locked_until")
    private Instant labelLockedUntil;

    @Column(name = "last_label_error", length = 500)
    private String lastLabelError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "event_version", nullable = false)
    private long eventVersion;

    protected ShipmentEntity() {
    }

    public ShipmentEntity(UUID id, UUID orderId, DeliveryAddress address, String carrierReference, Instant now) {
        if (id == null || orderId == null || address == null || now == null) {
            throw new IllegalArgumentException("Shipment, order, destination, and creation time are required.");
        }
        this.id = id;
        this.orderId = orderId;
        this.status = carrierReference == null ? ShipmentStatus.PENDING : ShipmentStatus.LABEL_CREATED;
        this.recipientName = address.recipientName();
        this.addressLine1 = address.addressLine1();
        this.addressLine2 = address.addressLine2();
        this.city = address.city();
        this.postalCode = address.postalCode();
        this.countryCode = address.countryCode();
        this.phone = address.phone();
        this.carrierReference = carrierReference;
        this.labelAttempts = 0;
        this.labelNextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
        this.eventVersion = carrierReference == null ? 1 : 2;
    }

    public Shipment toDomain() {
        return new Shipment(id, orderId, status,
                new DeliveryAddress(recipientName, addressLine1, addressLine2, city, postalCode, countryCode, phone),
                carrierReference, createdAt, updatedAt);
    }

    public void claimLabelCreation(String workerId, Instant leaseUntil, Instant now) {
        if (status != ShipmentStatus.PENDING || carrierReference != null
                || workerId == null || workerId.isBlank() || leaseUntil == null
                || now == null || !leaseUntil.isAfter(now)
                || (labelLockedUntil != null && labelLockedUntil.isAfter(now))) {
            throw new IllegalStateException("Only an available pending shipment can be claimed for label creation.");
        }
        labelLockedBy = workerId;
        labelLockedUntil = leaseUntil;
        labelAttempts++;
        updatedAt = now;
    }

    public boolean ownsLabelLease(String workerId, Instant now) {
        return workerId != null && now != null && workerId.equals(labelLockedBy)
                && labelLockedUntil != null && labelLockedUntil.isAfter(now)
                && status == ShipmentStatus.PENDING && carrierReference == null;
    }

    public void labelCreated(String reference, String workerId, Instant now) {
        requireLabelLease(workerId, now);
        if (reference == null || !reference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,119}")) {
            throw new IllegalArgumentException("Carrier reference must be a safe identifier token.");
        }
        carrierReference = reference;
        status = ShipmentStatus.LABEL_CREATED;
        lastLabelError = null;
        clearLabelLease();
        eventVersion++;
        updatedAt = now;
    }

    public void labelRetry(String reason, String workerId, Instant now) {
        requireLabelLease(workerId, now);
        lastLabelError = sanitize(reason);
        int exponent = Math.min(Math.max(labelAttempts - 1, 0), 7);
        long delay = Math.min(3600L, 30L << exponent);
        labelNextAttemptAt = now.plusSeconds(delay);
        clearLabelLease();
        updatedAt = now;
    }

    public void status(ShipmentStatus next, Instant now) {
        status = next;
        eventVersion++;
        updatedAt = now;
    }

    private void requireLabelLease(String workerId, Instant now) {
        if (!ownsLabelLease(workerId, now)) {
            throw new IllegalStateException("Shipment label-creation lease is not owned or has expired.");
        }
    }

    private void clearLabelLease() {
        labelLockedBy = null;
        labelLockedUntil = null;
    }

    private static String sanitize(String reason) {
        if (reason == null || reason.isBlank()) return "Carrier label creation failed; retry is scheduled.";
        String normalized = reason.replaceAll("[\\p{Cntrl}]", " ").trim();
        return normalized.substring(0, Math.min(500, normalized.length()));
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public ShipmentStatus getStatus() { return status; }
    public String getCarrierReference() { return carrierReference; }
    public int getLabelAttempts() { return labelAttempts; }
    public Instant getLabelNextAttemptAt() { return labelNextAttemptAt; }
    public String getLabelLockedBy() { return labelLockedBy; }
    public Instant getLabelLockedUntil() { return labelLockedUntil; }
    public String getLastLabelError() { return lastLabelError; }
    public long getEventVersion() { return eventVersion; }
}
