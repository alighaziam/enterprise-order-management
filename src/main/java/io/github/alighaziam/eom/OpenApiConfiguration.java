package io.github.alighaziam.eom;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
class OpenApiConfiguration {
    @Bean
    OpenAPI enterpriseOrderManagementOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enterprise Order Management")
                        .version("0.1.0")
                        .description("Customers, catalog, inventory, orders, sandbox payments, shipping, checkout, and the transactional outbox."))
                .components(new Components().addSecuritySchemes("wso2", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("WSO2 access token. Paste the token only; Swagger adds the Bearer prefix.")))
                .tags(List.of(
                        tag("Customers", "Customer profiles."),
                        tag("Catalog", "Products and prices."),
                        tag("Inventory", "Stock and reservations."),
                        tag("Orders", "Order snapshots, confirmation, and cancellation."),
                        tag("Payments", "Sandbox authorization, capture, void, and refund. No card data is accepted."),
                        tag("Shipments", "Delivery snapshots and shipment status."),
                        tag("Checkout", "Persisted checkout workflow and compensation."),
                        tag("Outbox", "Transactional outbox inspection and dead-letter replay.")));
    }

    @Bean
    OpenApiCustomizer operationCatalog() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                String scope = requiredScope(method.name(), path);
                operation.setTags(List.of(tagName(path)));
                operation.setSummary(summary(method.name(), path));
                operation.setDescription("Requires scope " + scope + ".");
                operation.setSecurity(List.of(new SecurityRequirement().addList("wso2")));
            }));
        };
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private static String requiredScope(String method, String path) {
        if (path.contains("/reconcile") || path.endsWith("/replay") || (path.contains("/outbox") && !"GET".equals(method))) {
            return "operations.admin";
        }
        if (path.contains("/outbox")) return "outbox.read";
        boolean read = "GET".equals(method);
        if (path.contains("/customers")) return read ? "customers.read" : "customers.write";
        if (path.contains("/products")) return read ? "catalog.read" : "catalog.write";
        if (path.contains("/inventory")) return read ? "inventory.read" : "inventory.write";
        if (path.contains("/orders")) return read ? "orders.read" : "orders.write";
        if (path.contains("/payments")) return read ? "payments.read" : "payments.write";
        if (path.contains("/shipments")) return read ? "shipping.read" : "shipping.write";
        if (path.contains("/checkouts")) return read ? "checkout.read" : "checkout.write";
        return "operations.admin";
    }

    private static String tagName(String path) {
        if (path.startsWith("/api/v1/customers")) return "Customers";
        if (path.startsWith("/api/v1/products")) return "Catalog";
        if (path.startsWith("/api/v1/inventory")) return "Inventory";
        if (path.startsWith("/api/v1/orders")) return "Orders";
        if (path.startsWith("/api/v1/payments")) return "Payments";
        if (path.startsWith("/api/v1/shipments")) return "Shipments";
        if (path.startsWith("/api/v1/checkouts")) return "Checkout";
        if (path.startsWith("/api/v1/outbox")) return "Outbox";
        return "Operations";
    }

    private static String summary(String method, String path) {
        String action = switch (pathAction(method, path)) {
            case "list" -> "List";
            case "retrieve" -> "Retrieve";
            case "confirm" -> "Confirm";
            case "cancel" -> "Cancel";
            case "capture" -> "Capture";
            case "void" -> "Void";
            case "refund" -> "Refund";
            case "status" -> "Advance";
            case "replay" -> "Replay";
            case "reconcile" -> "Reconcile";
            case "reserve" -> "Reserve";
            case "stock" -> "Initialize";
            default -> "POST".equals(method) ? "Create" : method;
        };
        return action + " " + resource(path);
    }

    private static String pathAction(String method, String path) {
        if ("GET".equals(method)) return path.contains("{") ? "retrieve" : "list";
        if (path.endsWith("/confirm")) return "confirm";
        if (path.endsWith("/cancel")) return "cancel";
        if (path.endsWith("/capture")) return "capture";
        if (path.endsWith("/void")) return "void";
        if (path.endsWith("/refund")) return "refund";
        if (path.endsWith("/status")) return "status";
        if (path.endsWith("/replay")) return "replay";
        if (path.endsWith("/reconcile")) return "reconcile";
        if (path.endsWith("/reservations")) return "reserve";
        if (path.endsWith("/stock")) return "stock";
        return "create";
    }

    private static String resource(String path) {
        if (path.contains("/customers")) return "customers";
        if (path.contains("/products")) return "products";
        if (path.contains("/reservations")) return "a stock reservation";
        if (path.contains("/stock")) return "opening stock";
        if (path.contains("/inventory")) return "stock";
        if (path.contains("/orders")) return "orders";
        if (path.contains("/payments")) return "a sandbox payment";
        if (path.contains("/shipments")) return "shipments";
        if (path.contains("/reconcile")) return "a checkout that needs compensation";
        if (path.contains("/checkouts")) return "checkouts";
        if (path.contains("/replay")) return "a dead-lettered event";
        if (path.contains("/outbox")) return "outbox events";
        return "the resource";
    }
}
