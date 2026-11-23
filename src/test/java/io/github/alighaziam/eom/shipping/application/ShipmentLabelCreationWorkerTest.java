package io.github.alighaziam.eom.shipping.application;

import io.github.alighaziam.eom.shipping.CarrierGateway;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import io.github.alighaziam.eom.shipping.Shipment;
import io.github.alighaziam.eom.shipping.ShipmentStatus;
import io.github.alighaziam.eom.shipping.infrastructure.ShipmentEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShipmentLabelCreationWorkerTest {

    private static final DeliveryAddress ADDRESS = new DeliveryAddress(
            "Recipient", "Main Street 1", null, "Berlin", "10115", "DE", null);

    private static ShipmentEntity shipment(UUID id, UUID orderId, String workerId, Instant now) {
        ShipmentEntity entity = mock(ShipmentEntity.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getOrderId()).thenReturn(orderId);
        when(entity.getLabelLockedBy()).thenReturn(workerId);
        when(entity.toDomain()).thenReturn(new Shipment(id, orderId, ShipmentStatus.PENDING,
                ADDRESS, null, now, now));
        return entity;
    }

    @Test
    void callsCarrierWithStableShipmentIdAndFinalizesOutsideProviderCatch() {
        Instant now = Instant.now();
        UUID shipmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        ShipmentLabelCreationService commands = mock(ShipmentLabelCreationService.class);
        CarrierGateway carrier = mock(CarrierGateway.class);
        ShipmentEntity shipment = shipment(shipmentId, orderId, workerId, now);
        when(commands.claimBatch(20, now)).thenReturn(List.of(shipment));
        when(carrier.createShipmentReference(shipmentId, orderId, ADDRESS))
                .thenReturn("carrier-ref-123");
        when(commands.markCreated(shipmentId, workerId, "carrier-ref-123", any(Instant.class)))
                .thenReturn(true);

        ShipmentLabelCreationWorker worker = new ShipmentLabelCreationWorker(commands, carrier);

        assertEquals(1, worker.processBatch(now));
        verify(carrier).createShipmentReference(shipmentId, orderId, ADDRESS);
        verify(commands).markCreated(eq(shipmentId), eq(workerId), eq("carrier-ref-123"), any(Instant.class));
        verify(commands, never()).markRetry(any(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void providerFailureSchedulesRetryWithStableIdempotencyKey() {
        Instant now = Instant.now();
        UUID shipmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        ShipmentLabelCreationService commands = mock(ShipmentLabelCreationService.class);
        CarrierGateway carrier = mock(CarrierGateway.class);
        ShipmentEntity shipment = shipment(shipmentId, orderId, workerId, now);
        when(commands.claimBatch(20, now)).thenReturn(List.of(shipment));
        when(carrier.createShipmentReference(shipmentId, orderId, ADDRESS))
                .thenThrow(new IllegalStateException("provider secret must not be persisted"));
        when(commands.markRetry(eq(shipmentId), eq(workerId), anyString(), any(Instant.class)))
                .thenReturn(true);

        ShipmentLabelCreationWorker worker = new ShipmentLabelCreationWorker(commands, carrier);

        assertEquals(1, worker.processBatch(now));
        verify(commands).markRetry(eq(shipmentId), eq(workerId),
                eq("Carrier label creation failed; retry will reuse the shipment idempotency key."),
                any(Instant.class));
        verify(commands, never()).markCreated(any(), anyString(), anyString(), any(Instant.class));
    }

    @Test
    void persistenceFailureIsNotMisclassifiedAsCarrierFailure() {
        Instant now = Instant.now();
        UUID shipmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String workerId = UUID.randomUUID().toString();
        ShipmentLabelCreationService commands = mock(ShipmentLabelCreationService.class);
        CarrierGateway carrier = mock(CarrierGateway.class);
        ShipmentEntity shipment = shipment(shipmentId, orderId, workerId, now);
        when(commands.claimBatch(20, now)).thenReturn(List.of(shipment));
        when(carrier.createShipmentReference(shipmentId, orderId, ADDRESS)).thenReturn("carrier-ref-123");
        when(commands.markCreated(eq(shipmentId), eq(workerId), eq("carrier-ref-123"), any(Instant.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        ShipmentLabelCreationWorker worker = new ShipmentLabelCreationWorker(commands, carrier);

        assertThrows(IllegalStateException.class, () -> worker.processBatch(now));
        verify(commands, never()).markRetry(any(), anyString(), anyString(), any(Instant.class));
    }
}
