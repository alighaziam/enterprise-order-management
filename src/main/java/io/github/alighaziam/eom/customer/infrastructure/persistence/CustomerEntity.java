package io.github.alighaziam.eom.customer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers", schema = "customer")
public class CustomerEntity {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomerEntity() {
    }

    CustomerEntity(UUID id, String fullName, String email, boolean active, Instant createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.active = active;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    String getFullName() { return fullName; }
    String getEmail() { return email; }
    boolean isActive() { return active; }
    Instant getCreatedAt() { return createdAt; }
}
