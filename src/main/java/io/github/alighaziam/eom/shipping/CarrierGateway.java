package io.github.alighaziam.eom.shipping;

import java.util.UUID;

/**
 * Synchronous sandbox port for shipment creation. Before enabling a network-backed
 * carrier, replace this call path with a persisted split-phase command/outcome flow
 * so remote calls do not hold database transactions. Use shipmentId as provider
 * idempotency key and reconcile unknown outcomes before retrying.
 */
public interface CarrierGateway {
    String createShipmentReference(UUID shipmentId, UUID orderId, DeliveryAddress destination);
}
