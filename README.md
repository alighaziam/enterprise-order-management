# Enterprise Order Management

A portfolio-grade enterprise order management system built as a **modular monolith** using Java 25, Spring Boot, Spring Modulith, PostgreSQL, and Flyway.

## Architecture

The application is one deployable system with explicit business capability boundaries. Each module owns its domain model and persistence. Cross-module collaboration uses published module APIs or domain events—not another module's repositories or tables.

| Module | Responsibility | Current scope |
|---|---|---|
| customer | Customer profiles | Create and retrieve customers; published existence query |
| catalog | Product information and pricing | Create and retrieve products; published active-product query |
| inventory | Stock availability and reservations | Initialize, reserve, release, and confirm stock |
| order | Order lifecycle and orchestration | Place, retrieve, confirm, and cancel orders |
| payment | Payment authorization and capture | Planned |
| shipping | Shipment and delivery lifecycle | Planned |

## Order lifecycle and consistency

Orders use three states: **RESERVED**, **CONFIRMED**, and **CANCELLED**. Placing an order validates the customer and active products, checks a single currency per order, snapshots SKU/name/price, and creates an inventory reservation for every line. The order stores each reservation ID so later operations target the exact reserved quantity.

Confirming an order confirms every reservation and consumes stock. Cancelling a reserved order releases every reservation. Repeated confirmation or cancellation is idempotent; invalid state transitions are rejected. Order transitions use a pessimistic row lock. Order persistence and inventory operations participate in the same PostgreSQL transaction in this modular-monolith deployment, so a failed line operation rolls back the complete use case rather than leaving partial stock changes.

This local transaction boundary is intentional for the current deployment. If Inventory or Order is later extracted into a separate service/database, this orchestration must be redesigned as a durable saga with an outbox, retries, compensation, and idempotent message handling. A local Spring transaction must not be mistaken for a distributed transaction.

## Technology

- Java 25
- Spring Boot 4.1.2
- Spring Modulith 2.1.2
- PostgreSQL 17
- Flyway
- Maven

## Run locally

```bash
docker compose up -d
mvn spring-boot:run
```

Application: http://localhost:8080

## API

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/v1/customers` | Create customer |
| GET | `/api/v1/customers/{id}` | Retrieve customer |
| POST | `/api/v1/products` | Create product |
| GET | `/api/v1/products/{id}` | Retrieve product |
| POST | `/api/v1/inventory/stock` | Initialize stock |
| GET | `/api/v1/inventory/{productId}` | Read stock availability |
| POST | `/api/v1/inventory/reservations` | Create pending reservation |
| POST | `/api/v1/inventory/reservations/{reservationId}/release` | Release reservation |
| POST | `/api/v1/inventory/reservations/{reservationId}/confirm` | Consume reserved stock |
| POST | `/api/v1/orders` | Place an order and reserve all line quantities |
| GET | `/api/v1/orders/{orderId}` | Retrieve order and line snapshots |
| POST | `/api/v1/orders/{orderId}/confirm` | Confirm order and consume reserved stock |
| POST | `/api/v1/orders/{orderId}/cancel` | Cancel order and release reservations |

Example order request:

```json
{
  "customerId": "00000000-0000-0000-0000-000000000001",
  "lines": [
    {
      "productId": "00000000-0000-0000-0000-000000000002",
      "quantity": 2
    }
  ]
}
```

A successful placement returns an order ID, status `RESERVED`, total, immutable product/price snapshots, and the reservation ID for each line.

## Build and test

```bash
mvn clean verify
```

GitHub Actions runs Maven verification against PostgreSQL 17. Tests cover domain transitions, module boundaries, persistence, reservation lifecycle, and order-to-inventory integration.

## Design rules

1. A module owns its entities, repositories, and schema.
2. No cross-module repository or table access.
3. Cross-module calls use only published root-package interfaces.
4. Store product and price snapshots on the order; do not recalculate historical orders from the current catalog.
5. Use local ACID transactions only while modules share the same database and process.
6. Use a durable saga/outbox before splitting this workflow across services.
7. Extract a microservice only when independent deployment, scaling, or ownership justifies its operational cost.

## Status

- [x] Repository bootstrap, PostgreSQL Compose, and Spring Boot skeleton
- [x] Customer API, persistence, uniqueness, and tests
- [x] Catalog API, persistence, uniqueness, and tests
- [x] Inventory stock API and durable reservation lifecycle
- [x] Order module with published module APIs, immutable line snapshots, and lifecycle
- [x] Order reserve/confirm/cancel orchestration and integration tests
- [x] Flyway migrations and Spring Modulith boundary verification
- [x] PostgreSQL-backed integration tests and GitHub Actions verification
- [ ] Payment and shipping integration boundaries
- [ ] Transactional outbox and distributed saga
