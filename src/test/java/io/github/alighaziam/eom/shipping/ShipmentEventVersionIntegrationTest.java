package io.github.alighaziam.eom.shipping;

import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ShipmentEventVersionIntegrationTest {

    @Autowired
    private ShippingOperations shipping;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private CustomerService customers;

    @Autowired
    private ProductService products;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private OrderOperations orders;

    @Test
    void shipmentEventsUseStrictlyIncreasingAggregateVersionsAndRequireConfirmedOrders() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Shipment Customer", "shipment-" + suffix + "@example.com");
        var product = products.create("SHP-" + suffix, "Shipment Product", "Test item",
                new BigDecimal("8.00"), "EUR");
        inventory.initializeStock(product.id(), 5);

        var order = orders.placeForCheckout(customer.id(),
                List.of(new OrderOperations.OrderLineRequest(product.id(), 1)));
        DeliveryAddress destination = new DeliveryAddress("Recipient", "Main Street 1", null,
                "Berlin", "10115", "DE", "+491234567");

        assertThrows(IllegalStateException.class, () -> shipping.create(order.id(), destination),
                "A reserved order must not be fulfilled.");

        orders.confirmForCheckout(order.id());
        var shipment = shipping.create(order.id(), destination);

        assertEquals(shipment.id(), shipping.create(order.id(), destination).id());
        assertThrows(IllegalStateException.class, () -> shipping.create(order.id(),
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
