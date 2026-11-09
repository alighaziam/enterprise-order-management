package io.github.alighaziam.eom.catalog.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record Product(UUID id,String sku,String name,String description,BigDecimal unitPrice,String currency,boolean active,Instant createdAt) {}