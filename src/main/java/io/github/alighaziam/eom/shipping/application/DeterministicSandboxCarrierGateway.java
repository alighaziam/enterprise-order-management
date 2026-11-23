package io.github.alighaziam.eom.shipping.application;

import io.github.alighaziam.eom.shipping.CarrierGateway;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DeterministicSandboxCarrierGateway implements CarrierGateway {

    @Override
    public String createShipmentReference(UUID shipmentId, UUID orderId, DeliveryAddress destination) {
        if (shipmentId == null || orderId == null || destination == null) {
            throw new IllegalArgumentException("Shipment, order, and destination are required.");
        }
        // shipmentId is the idempotency key: every retry returns the same reference.
        return "sandbox-shipment-" + shipmentId;
    }
}
