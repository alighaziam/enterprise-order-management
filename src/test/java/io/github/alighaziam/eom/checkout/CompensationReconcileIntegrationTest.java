package io.github.alighaziam.eom.checkout;

import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.checkout.application.CheckoutService;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowEntity;
import io.github.alighaziam.eom.checkout.infrastructure.CheckoutWorkflowRepository;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.payment.PaymentOperations;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CompensationReconcileIntegrationTest {

    @Autowired private CustomerService customers;
    @Autowired private ProductService products;
    @Autowired private InventoryService inventory;
    @Autowired private OrderOperations orders;
    @Autowired private PaymentOperations payments;
    @Autowired private CheckoutService checkout;
    @Autowired private CheckoutWorkflowRepository workflows;

    @Test
    void reconcileVoidsAnAuthorizationAndReleasesTheReservation() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Reconcile Customer", "reconcile-" + suffix + "@example.com");
        var product = products.create("RCN-" + suffix, "Reconcile Product", "Compensation", new BigDecimal("9.00"), "EUR");
        inventory.initializeStock(product.id(), 5);
        var order = orders.placeForCheckout(customer.id(), List.of(new OrderOperations.OrderLineRequest(product.id(), 2)));
        var payment = payments.authorize(order.id(), order.totalAmount(), order.currency(), "reconcile-" + suffix);
        var workflow = stuck(order.id(), payment.id(), suffix);

        var result = checkout.reconcile(workflow.getId());

        assertEquals(CheckoutStatus.FAILED, result.status());
        assertEquals(PaymentStatus.VOIDED, payments.get(payment.id()).status());
        assertEquals(OrderState.CANCELLED, orders.state(order.id()));
        assertEquals(5, inventory.find(product.id()).onHandQuantity());
    }

    @Test
    void reconcileRefusesToUndoAConfirmedCapturedSale() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Committed Customer", "committed-" + suffix + "@example.com");
        var product = products.create("CMT-" + suffix, "Committed Product", "Compensation", new BigDecimal("11.00"), "EUR");
        inventory.initializeStock(product.id(), 4);
        var completed = checkout.checkout(new CheckoutOperations.CheckoutRequest(
                customer.id(),
                List.of(new OrderOperations.OrderLineRequest(product.id(), 1)),
                new DeliveryAddress("Recipient", "Main Street 1", null, "Berlin", "10115", "DE", "+491234567"),
                "committed-" + suffix));
        var workflow = workflows.findById(completed.workflowId()).orElseThrow();
        workflow.status(CheckoutStatus.COMPENSATION_REQUIRED, "forced", Instant.now());
        workflows.saveAndFlush(workflow);

        IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> checkout.reconcile(completed.workflowId()));

        assertEquals(CheckoutStatus.COMPENSATION_REQUIRED, checkout.get(completed.workflowId()).status());
        assertEquals(PaymentStatus.CAPTURED, payments.get(completed.paymentId()).status());
        assertEquals(OrderState.CONFIRMED, orders.state(completed.orderId()));
        assertEquals(true, rejected.getMessage().contains("committed sale"));
    }

    private CheckoutWorkflowEntity stuck(UUID orderId, UUID paymentId, String suffix) {
        Instant now = Instant.now();
        var workflow = new CheckoutWorkflowEntity(UUID.randomUUID(), orderId, "stuck-" + suffix,
                new DeliveryAddress("Recipient", "Main Street 1", null, "Berlin", "10115", "DE", "+491234567"), now);
        workflow.payment(paymentId, CheckoutStatus.PAYMENT_AUTHORIZED, now);
        workflow.status(CheckoutStatus.COMPENSATION_REQUIRED, "forced", now);
        return workflows.saveAndFlush(workflow);
    }
}
