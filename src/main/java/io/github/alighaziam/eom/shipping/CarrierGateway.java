package io.github.alighaziam.eom.shipping;

import java.util.UUID;

/**
 * Port for shipment creation. Real carrier adapters must use the shipment ID
 * as a provider idempotency key and return only after the provider outcome is known.
 */
public interface CarrierGateway {
    String createShipmentReference(UUID shipmentId, UUID orderId, DeliveryAddress destination);
}
