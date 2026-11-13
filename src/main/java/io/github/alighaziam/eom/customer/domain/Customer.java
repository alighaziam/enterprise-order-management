package io.github.alighaziam.eom.customer.domain;

import java.time.Instant;
import java.util.UUID;

public record Customer(UUID id, String fullName, String email, boolean active, Instant createdAt) {}
