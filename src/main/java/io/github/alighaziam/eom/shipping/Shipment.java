package io.github.alighaziam.eom.shipping;
import java.time.Instant; import java.util.UUID;
public record Shipment(UUID id,UUID orderId,ShipmentStatus status,DeliveryAddress destination,String carrierReference,Instant createdAt,Instant updatedAt) {}