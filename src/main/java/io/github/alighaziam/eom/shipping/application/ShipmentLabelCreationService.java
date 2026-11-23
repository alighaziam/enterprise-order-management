package io.github.alighaziam.eom.shipping.application;

import io.github.alighaziam.eom.outbox.OutboxOperations;
import io.github.alighaziam.eom.shipping.infrastructure.ShipmentEntity;
import io.github.alighaziam.eom.shipping.infrastructure.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Short database transactions for claiming and finalizing carrier label requests. */
@Service
public class ShipmentLabelCreationService {

    private static final long LEASE_SECONDS = 120;

    private final ShipmentRepository shipments;
    private final OutboxOperations outbox;

    public ShipmentLabelCreationService(ShipmentRepository shipments, OutboxOperations outbox) {
        this.shipments = shipments;
        this.outbox = outbox;
    }

    @Transactional
    public List<ShipmentEntity> claimBatch(int batchSize, Instant now) {
        if (batchSize < 1 || batchSize > 100 || now == null) {
            throw new IllegalArgumentException("Batch size must be between 1 and 100 and time is required.");
        }
        String workerId = UUID.randomUUID().toString();
        shipments.claimPendingLabels(workerId, now, now.plusSeconds(LEASE_SECONDS), batchSize);
        return shipments.findByLabelLockedByOrderByCreatedAtAsc(workerId);
    }

    @Transactional
    public boolean markCreated(UUID shipmentId, String workerId, String carrierReference, Instant now) {
        ShipmentEntity shipment = ownedShipment(shipmentId, workerId, now);
        if (shipment == null) return false;
        shipment.labelCreated(carrierReference, workerId, now);
        shipments.saveAndFlush(shipment);
        outbox.append("ShipmentLabelCreated", "Shipment", shipmentId.toString(),
                shipment.getEventVersion(), shipment.getOrderId().toString(), null,
                "{\"shipmentId\":\"" + shipmentId + "\",\"carrierReference\":\"" + carrierReference + "\"}");
        return true;
    }

    @Transactional
    public boolean markRetry(UUID shipmentId, String workerId, String reason, Instant now) {
        ShipmentEntity shipment = ownedShipment(shipmentId, workerId, now);
        if (shipment == null) return false;
        shipment.labelRetry(reason, workerId, now);
        shipments.saveAndFlush(shipment);
        return true;
    }

    private ShipmentEntity ownedShipment(UUID id, String workerId, Instant now) {
        if (id == null || workerId == null || workerId.isBlank() || now == null) return null;
        ShipmentEntity shipment = shipments.findByIdForUpdate(id).orElse(null);
        return shipment != null && shipment.ownsLabelLease(workerId, now) ? shipment : null;
    }
}
