package io.github.alighaziam.eom.shipping;
import java.util.UUID;
public interface ShippingOperations {Shipment create(UUID orderId,DeliveryAddress address);Shipment updateStatus(UUID shipmentId,ShipmentStatus status);Shipment get(UUID shipmentId);}