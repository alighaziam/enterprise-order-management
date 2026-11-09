package io.github.alighaziam.eom;

import io.github.alighaziam.eom.catalog.application.ProductService;
import io.github.alighaziam.eom.catalog.domain.Product;
import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.inventory.application.InventoryService;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EnterpriseOrderManagementIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Test
    void persistsCustomerProductAndStockAcrossModules() {
        String unique = UUID.randomUUID().toString();

        var customer = customerService.create("Integration Customer", "customer-" + unique + "@example.com");
        assertNotNull(customerService.findById(customer.id()).orElseThrow());

        Product product = productService.create(
                "SKU-" + unique.substring(0, 8),
                "Integration Product",
                "Created by the application integration test",
                new BigDecimal("29.9900"),
                "USD"
        );
        assertNotNull(productService.findById(product.id()).orElseThrow());

        inventoryService.initializeStock(product.id(), 8);
        StockItem reserved = inventoryService.reserve(product.id(), 3);

        assertEquals(8, reserved.onHandQuantity());
        assertEquals(3, reserved.reservedQuantity());
        assertEquals(5, reserved.availableQuantity());
    }
}
