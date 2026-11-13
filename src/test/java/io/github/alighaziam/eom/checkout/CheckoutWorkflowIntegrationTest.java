package io.github.alighaziam.eom.checkout;

import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import io.github.alighaziam.eom.shipping.ShippingOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CheckoutWorkflowIntegrationTest {
    @Autowired CustomerService customers;
    @Autowired ProductService products;
    @Autowired InventoryService inventory;
    @Autowired CheckoutOperations checkout;
    @Autowired ShippingOperations shipping;

    @Test
    void completesSandboxCheckoutAndPersistsWorkflowAndShipment() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Checkout Customer", "checkout-" + suffix + "@example.com");
        var product = products.create("CHK-" + suffix, "Checkout Product", "Checkout integration",
                new BigDecimal("12.50"), "EUR");
        inventory.initializeStock(product.id(), 5);

        var request = new CheckoutOperations.CheckoutRequest(customer.id(),
                List.of(new io.github.alighaziam.eom.order.OrderOperations.OrderLineRequest(product.id(), 2)),
                new DeliveryAddress("Recipient", "Main Street 1", null, "Berlin", "10115", "DE", "+491234567"),
                "checkout-test-" + suffix);
        var result = checkout.checkout(request);

        assertEquals(CheckoutStatus.COMPLETED, result.status());
        assertNotNull(result.paymentId());
        assertNotNull(result.shipmentId());
        assertEquals(3, inventory.find(product.id()).onHandQuantity());
        assertEquals(result.orderId(), shipping.get(result.shipmentId()).orderId());
        assertEquals(CheckoutStatus.COMPLETED, checkout.get(result.workflowId()).status());

        var replay = checkout.checkout(request);
        assertEquals(result.workflowId(), replay.workflowId());
        assertEquals(result.orderId(), replay.orderId());
        assertEquals(3, inventory.find(product.id()).onHandQuantity(),
                "A replay must not reserve stock or create a second order");

        var differentRequest = new CheckoutOperations.CheckoutRequest(customer.id(),
                request.lines(),
                new DeliveryAddress("Different Recipient", "Other Street 9", null, "Berlin", "10117", "DE", "+491111111"),
                request.idempotencyKey());
        assertThrows(IllegalStateException.class, () -> checkout.checkout(differentRequest),
                "Reusing a key with a different payload must be rejected");
        assertEquals(3, inventory.find(product.id()).onHandQuantity());
    }
}
