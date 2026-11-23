package io.github.alighaziam.eom.shipping;

import java.util.UUID;

/**
 * Carrier boundary. Implementations must treat shipmentId as the stable provider
 * idempotency key so retries after an ambiguous timeout cannot create duplicate labels.
 * Calls are made by ShipmentLabelCreationWorker, never inside the shipment database
 * transaction. A network-backed adapter must reconcile unknown outcomes before using
 * any provider that does not guarantee idempotent create requests.
 */
public interface CarrierGateway {
    String createShipmentReference(UUID shipmentId, UUID orderId, DeliveryAddress destination);
}
