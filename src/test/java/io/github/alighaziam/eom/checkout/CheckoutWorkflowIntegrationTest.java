package io.github.alighaziam.eom.checkout;

import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.checkout.application.CheckoutFulfillmentClaimService;
import io.github.alighaziam.eom.checkout.application.CheckoutRecoveryWorker;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowEntity;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import io.github.alighaziam.eom.shipping.ShippingOperations;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.sql.Timestamp;
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
    @Autowired CheckoutWorkflowRepository workflows;
    @Autowired CheckoutFulfillmentClaimService fulfillmentClaims;
    @Autowired CheckoutRecoveryWorker recoveryWorker;
    @Autowired io.github.alighaziam.eom.checkout.application.CheckoutSagaRecoveryWorker sagaRecoveryWorker;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxRepository outbox;


    @Test
    void recoversStaleConfirmedWorkflowWhenProcessCrashesBeforeShipmentIsCreated() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Recovery Customer", "recovery-" + suffix + "@example.com");
        var product = products.create("RCV-" + suffix, "Recovery Product", "Checkout recovery",
                new BigDecimal("8.00"), "EUR");
        inventory.initializeStock(product.id(), 3);
        var request = new CheckoutOperations.CheckoutRequest(customer.id(),
                List.of(new io.github.alighaziam.eom.order.OrderOperations.OrderLineRequest(product.id(), 1)),
                new DeliveryAddress("Recipient", "Recovery Street 1", null, "Berlin", "10115", "DE", null),
                "recovery-" + suffix);
        var result = checkout.checkout(request);
        assertEquals(CheckoutStatus.COMPLETED, result.status());
        UUID originalShipmentId = result.shipmentId();

        // Recreate the durable state left by a process crash after ORDER_CONFIRMED,
        // but before ShippingService.create() committed a shipment.
        jdbc.update("""
                UPDATE checkout.workflows
                   SET status = 'ORDER_CONFIRMED', shipment_id = NULL,
                       updated_at = ?, fulfillment_locked_by = NULL, fulfillment_lease_until = NULL
                 WHERE id = ?
                """, Timestamp.from(Instant.now().minusSeconds(180)), result.workflowId());
        jdbc.update("DELETE FROM shipping.shipments WHERE id = ?", originalShipmentId);

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM shipping.shipments WHERE order_id = ?",
                Integer.class, result.orderId()));

        recoveryWorker.retryPendingFulfillment();

        var recovered = checkout.get(result.workflowId());
        assertEquals(CheckoutStatus.COMPLETED, recovered.status());
        assertNotNull(recovered.shipmentId());
        assertNotEquals(originalShipmentId, recovered.shipmentId());
        assertEquals(result.orderId(), shipping.get(recovered.shipmentId()).orderId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM shipping.shipments WHERE order_id = ?",
                Integer.class, result.orderId()));
    }


    @Test
    void recoversCapturedPaymentAfterProcessCrashBeforeOrderConfirmation() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Saga Recovery Customer", "saga-recovery-" + suffix + "@example.com");
        var product = products.create("SGR-" + suffix, "Saga Recovery Product", "Saga recovery",
                new BigDecimal("13.00"), "EUR");
        inventory.initializeStock(product.id(), 2);
        var request = new CheckoutOperations.CheckoutRequest(customer.id(),
                List.of(new io.github.alighaziam.eom.order.OrderOperations.OrderLineRequest(product.id(), 1)),
                new DeliveryAddress("Recipient", "Saga Recovery Road 2", null, "Berlin", "10115", "DE", null),
                "saga-recovery-" + suffix);
        var result = checkout.checkout(request);
        assertEquals(CheckoutStatus.COMPLETED, result.status());

        jdbc.update("""
                UPDATE checkout.workflows
                   SET status = 'PAYMENT_CAPTURED', shipment_id = NULL,
                       updated_at = ?, fulfillment_locked_by = NULL, fulfillment_lease_until = NULL
                 WHERE id = ?
                """, Timestamp.from(Instant.now().minusSeconds(180)), result.workflowId());
        jdbc.update("DELETE FROM shipping.shipments WHERE id = ?", result.shipmentId());
        // Recreate the durable order state at the crash boundary: payment captured, order not yet confirmed.
        jdbc.update("UPDATE ordering.orders SET status = 'RESERVED' WHERE id = ?", result.orderId());

        sagaRecoveryWorker.recoverStaleSagaStages();

        assertEquals("CONFIRMED", jdbc.queryForObject(
                "SELECT status FROM ordering.orders WHERE id = ?", String.class, result.orderId()));
        var recovered = checkout.get(result.workflowId());
        assertEquals(CheckoutStatus.COMPLETED, recovered.status());
        assertNotNull(recovered.shipmentId());
        assertEquals(result.orderId(), shipping.get(recovered.shipmentId()).orderId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM shipping.shipments WHERE order_id = ?",
                Integer.class, result.orderId()));
    }

    @Test
    void completesSandboxCheckoutAndPersistsWorkflowAndShipment() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Checkout Customer", "checkout-" + suffix + "@example.com");
        var product = products.create("CHK-" + suffix, "Checkout Product", "Checkout integration",
                new BigDecimal("12.50"), "EUR");
        inventory.initializeStock(product.id(), 5);

        String checkoutKeyPrefix = "checkout-" + suffix;
        String checkoutKey = checkoutKeyPrefix + "x".repeat(120 - checkoutKeyPrefix.length());
        var request = new CheckoutOperations.CheckoutRequest(customer.id(),
                List.of(new io.github.alighaziam.eom.order.OrderOperations.OrderLineRequest(product.id(), 2)),
                new DeliveryAddress("Recipient", "Main Street 1", null, "Berlin", "10115", "de", "+491234567"),
                checkoutKey);
        var result = checkout.checkout(request);

        assertEquals(CheckoutStatus.COMPLETED, result.status());
        assertNotNull(result.paymentId());
        assertNotNull(result.shipmentId());
        assertEquals(3, inventory.find(product.id()).onHandQuantity());
        assertEquals(result.orderId(), shipping.get(result.shipmentId()).orderId());
        assertEquals("DE", shipping.get(result.shipmentId()).destination().countryCode());
        assertEquals(CheckoutStatus.COMPLETED, checkout.get(result.workflowId()).status());

        List<OutboxEntity> workflowEvents = outbox.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(
                "CheckoutWorkflow", result.workflowId().toString());
        assertEquals(List.of(
                "CheckoutStarted",
                "CheckoutPAYMENT_AUTHORIZED",
                "CheckoutPAYMENT_CAPTURED",
                "CheckoutORDER_CONFIRMED",
                "CheckoutCompleted"),
                workflowEvents.stream().map(OutboxEntity::getEventType).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L),
                workflowEvents.stream().map(OutboxEntity::getAggregateVersion).toList());

        List<OutboxEntity> orderEvents = outbox.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(
                "Order", result.orderId().toString());
        assertEquals(List.of("OrderPlaced", "OrderConfirmed"),
                orderEvents.stream().map(OutboxEntity::getEventType).toList());
        assertEquals(List.of(1L, 2L),
                orderEvents.stream().map(OutboxEntity::getAggregateVersion).toList());

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
