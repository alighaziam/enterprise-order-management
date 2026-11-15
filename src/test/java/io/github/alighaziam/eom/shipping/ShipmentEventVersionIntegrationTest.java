package io.github.alighaziam.eom.shipping;

import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ShipmentEventVersionIntegrationTest {

    @Autowired
    private ShippingOperations shipping;

    @Autowired
    private OutboxRepository outbox;

    @Test
    void shipmentEventsUseStrictlyIncreasingAggregateVersions() {
        UUID orderId = UUID.randomUUID();
        DeliveryAddress destination = new DeliveryAddress("Recipient", "Main Street 1", null,
                "Berlin", "10115", "DE", "+491234567");
        var shipment = shipping.create(orderId, destination);

        assertEquals(shipment.id(), shipping.create(orderId, destination).id());
        assertThrows(IllegalStateException.class, () -> shipping.create(orderId,
                new DeliveryAddress("Other Recipient", "Other Street 2", null,
                        "Berlin", "10117", "DE", "+491234567")));

        shipping.updateStatus(shipment.id(), ShipmentStatus.LABEL_CREATED);
        shipping.updateStatus(shipment.id(), ShipmentStatus.READY_FOR_DISPATCH);

        List<OutboxEntity> events = outbox.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(
                "Shipment", shipment.id().toString());

        assertEquals(List.of("ShipmentCreated", "ShipmentLABEL_CREATED", "ShipmentREADY_FOR_DISPATCH"),
                events.stream().map(OutboxEntity::getEventType).toList());
        assertEquals(List.of(1L, 2L, 3L),
                events.stream().map(OutboxEntity::getAggregateVersion).toList());
    }
}
