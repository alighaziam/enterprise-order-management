package io.github.alighaziam.eom.shipping.application;

import io.github.alighaziam.eom.shipping.CarrierGateway;
import io.github.alighaziam.eom.shipping.infrastructure.ShipmentEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Executes carrier I/O outside database transactions. Retries always reuse the
 * persisted shipment ID as the provider idempotency key.
 */
@Component
public class ShipmentLabelCreationWorker {

    private static final int BATCH_SIZE = 20;

    private final ShipmentLabelCreationService commands;
    private final CarrierGateway carrier;

    public ShipmentLabelCreationWorker(ShipmentLabelCreationService commands, CarrierGateway carrier) {
        this.commands = commands;
        this.carrier = carrier;
    }

    @Scheduled(
            fixedDelayString = "${app.shipping.label-worker.fixed-delay:PT30S}",
            initialDelayString = "${app.shipping.label-worker.initial-delay:PT15S}")
    public void processScheduledBatch() {
        processBatch(Instant.now());
    }

    public int processBatch(Instant now) {
        List<ShipmentEntity> claimed = commands.claimBatch(BATCH_SIZE, now);
        int completed = 0;
        for (ShipmentEntity shipment : claimed) {
            String reference;
            try {
                reference = carrier.createShipmentReference(shipment.getId(), shipment.getOrderId(),
                        shipment.toDomain().destination());
            } catch (RuntimeException providerFailure) {
                // Do not persist provider exception text; it may contain secrets or raw responses.
                if (commands.markRetry(shipment.getId(), shipment.getLabelLockedBy(),
                        "Carrier label creation failed; retry will reuse the shipment idempotency key.",
                        Instant.now())) {
                    completed++;
                }
                continue;
            }

            if (reference == null || !reference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,119}")) {
                if (commands.markRetry(shipment.getId(), shipment.getLabelLockedBy(),
                        "Carrier returned an invalid shipment reference; retry is scheduled.",
                        Instant.now())) {
                    completed++;
                }
                continue;
            }

            // Keep finalization outside the carrier-I/O catch: a database failure must surface.
            if (commands.markCreated(shipment.getId(), shipment.getLabelLockedBy(), reference, Instant.now())) {
                completed++;
            }
        }
        return completed;
    }
}
