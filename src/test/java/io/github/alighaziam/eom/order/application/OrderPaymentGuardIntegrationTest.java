package io.github.alighaziam.eom.order.application;

import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.order.OrderState;
import io.github.alighaziam.eom.payment.PaymentOperations;
import io.github.alighaziam.eom.payment.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderPaymentGuardIntegrationTest {

    @Autowired
    private OrderOperations orders;

    @Autowired
    private PaymentOperations payments;

    @Autowired
    private CustomerService customers;

    @Autowired
    private ProductService products;

    @Autowired
    private InventoryService inventory;

    @Test
    void orderCannotBeConfirmedBeforePaymentCapture() {
        var order = createReservedOrder();

        assertThrows(IllegalStateException.class, () -> orders.confirmForCheckout(order.id()));
        assertEquals(OrderState.RESERVED, orders.state(order.id()));
    }

    @Test
    void orderCannotBeCancelledWhileAnAuthorizationIsActive() {
        var order = createReservedOrder();
        var payment = payments.authorize(order.id(), order.totalAmount(), order.currency(),
                "cancel-guard-" + UUID.randomUUID());

        assertEquals(PaymentStatus.AUTHORIZED, payment.status());
        assertThrows(IllegalStateException.class, () -> orders.cancelForCheckout(order.id()));
        assertEquals(OrderState.RESERVED, orders.state(order.id()));

        payments.voidAuthorization(payment.id());
        assertEquals(OrderState.CANCELLED, orders.cancelForCheckout(order.id()).status());
    }

    @Test
    void capturedPaymentAllowsOrderConfirmation() {
        var order = createReservedOrder();
        var payment = payments.authorize(order.id(), order.totalAmount(), order.currency(),
                "confirm-guard-" + UUID.randomUUID());
        payments.capture(payment.id());

        assertEquals(OrderState.CONFIRMED, orders.confirmForCheckout(order.id()).status());
    }

    private OrderOperations.OrderSummary createReservedOrder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var customer = customers.create("Guard Customer", "guard-" + suffix + "@example.com");
        var product = products.create("GRD-" + suffix, "Guard Product", "Test item",
                new BigDecimal("17.25"), "EUR");
        inventory.initializeStock(product.id(), 3);
        return orders.placeForCheckout(customer.id(),
                List.of(new OrderOperations.OrderLineRequest(product.id(), 1)));
    }
}
