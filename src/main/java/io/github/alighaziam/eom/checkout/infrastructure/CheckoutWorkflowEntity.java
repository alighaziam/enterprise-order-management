package io.github.alighaziam.eom.checkout.infrastructure;

import io.github.alighaziam.eom.checkout.CheckoutOperations;
import io.github.alighaziam.eom.checkout.CheckoutStatus;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflows", schema = "checkout")
public class CheckoutWorkflowEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckoutStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "recipient_name", length = 180)
    private String recipientName;

    @Column(name = "address_line1", length = 240)
    private String addressLine1;

    @Column(name = "address_line2", length = 240)
    private String addressLine2;

    @Column(length = 120)
    private String city;

    @Column(name = "postal_code", length = 40)
    private String postalCode;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(length = 40)
    private String phone;

    @Column(name = "fulfillment_attempts", nullable = false)
    private int fulfillmentAttempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "fulfillment_locked_by", length = 36)
    private String fulfillmentLockedBy;

    @Column(name = "fulfillment_lease_until")
    private Instant fulfillmentLeaseUntil;

    protected CheckoutWorkflowEntity() {
    }

    public CheckoutWorkflowEntity(UUID id, UUID order, String key, DeliveryAddress address, Instant now) {
        this(id, order, key, "legacy", address, now);
    }

    public CheckoutWorkflowEntity(UUID id, UUID order, String key, String fingerprint,
                                  DeliveryAddress address, Instant now) {
        this.id = id;
        this.orderId = order;
        this.idempotencyKey = key;
        this.requestFingerprint = fingerprint;
        this.status = CheckoutStatus.ORDER_RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
        this.recipientName = address.recipientName();
        this.addressLine1 = address.addressLine1();
        this.addressLine2 = address.addressLine2();
        this.city = address.city();
        this.postalCode = address.postalCode();
        this.countryCode = address.countryCode();
        this.phone = address.phone();
    }

    public void payment(UUID id, CheckoutStatus nextStatus, Instant now) {
        paymentId = id;
        status = nextStatus;
        updatedAt = now;
    }

    public void shipment(UUID id, Instant now) {
        shipmentId = id;
        status = CheckoutStatus.COMPLETED;
        lastError = null;
        nextAttemptAt = null;
        clearFulfillmentLease();
        updatedAt = now;
    }

    public void status(CheckoutStatus nextStatus, String error, Instant now) {
        status = nextStatus;
        lastError = truncate(error);
        updatedAt = now;
    }

    public void scheduleFulfillmentRetry(String error, Instant now) {
        status = CheckoutStatus.FULFILLMENT_PENDING;
        lastError = truncate(error);
        fulfillmentAttempts++;
        long delay = Math.min(3600L, 1L << Math.min(fulfillmentAttempts, 12));
        nextAttemptAt = now.plusSeconds(delay);
        clearFulfillmentLease();
        updatedAt = now;
    }

    public CheckoutOperations.CheckoutResult toResult() {
        return new CheckoutOperations.CheckoutResult(id, orderId, paymentId, shipmentId, status, lastError);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public UUID getPaymentId() { return paymentId; }
    public CheckoutStatus getStatus() { return status; }
    public String getFulfillmentLockedBy() { return fulfillmentLockedBy; }
    public Instant getFulfillmentLeaseUntil() { return fulfillmentLeaseUntil; }

    public DeliveryAddress getDeliveryAddress() {
        return new DeliveryAddress(recipientName, addressLine1, addressLine2, city, postalCode, countryCode, phone);
    }

    public Instant getNextAttemptAt() { return nextAttemptAt; }

    private void clearFulfillmentLease() {
        fulfillmentLockedBy = null;
        fulfillmentLeaseUntil = null;
    }

    private String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(1000, value.length()));
    }
}
