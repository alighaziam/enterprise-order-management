# Enterprise Order Management

A portfolio-grade enterprise order management system built as a **modular monolith** using Java 25, Spring Boot, Spring Modulith, PostgreSQL, and Flyway.

## Architecture

The system is one deployable application with explicit business capability boundaries. Each module owns its domain model and persistence. Cross-module collaboration must happen through published module APIs or domain events—not by accessing another module's repositories or tables.

| Module | Responsibility | Current scope |
|---|---|---|
| customer | Customer profiles and lifecycle | Create and retrieve customers; unique email |
| catalog | Product information and pricing | Create and retrieve products; unique SKU and currency validation |
| inventory | Stock availability and reservations | Initialize stock, read availability, reserve, release, and confirm stock |
| order | Order lifecycle and orchestration | Planned |
| payment | Payment authorization and capture boundary | Planned |
| shipping | Shipment and delivery lifecycle | Planned |

Inventory reservations are persisted in a dedicated ledger. A reservation transitions from **PENDING** to either **RELEASED** or **CONFIRMED**. Release restores available stock; confirmation consumes both on-hand and reserved quantities. Repeated release and confirmation requests are idempotent. Reservation state transitions use a database row lock, while stock changes use conditional SQL updates to preserve invariants under concurrent requests. The stock update and reservation transition share one database transaction.

Long-running workflows will use saga-style orchestration. Reliable asynchronous event publication will use a transactional outbox where required. These patterns will be introduced with concrete use cases rather than added speculatively.

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
| POST | `/api/v1/inventory/stock` | Initialize product stock |
| GET | `/api/v1/inventory/{productId}` | Read stock availability |
| POST | `/api/v1/inventory/reservations` | Create a pending stock reservation |
| POST | `/api/v1/inventory/reservations/{reservationId}/release` | Release a pending reservation |
| POST | `/api/v1/inventory/reservations/{reservationId}/confirm` | Confirm and consume a pending reservation |

Example customer request:

```json
{
  "fullName": "Ada Lovelace",
  "email": "ada@example.com"
}
```

Example product request:

```json
{
  "sku": "KEYBOARD-001",
  "name": "Mechanical Keyboard",
  "description": "Full-size mechanical keyboard",
  "unitPrice": 89.9900,
  "currency": "EUR"
}
```

Example stock reservation:

```json
{
  "productId": "00000000-0000-0000-0000-000000000000",
  "quantity": 2
}
```

Reservation responses include a `reservationId` and a lifecycle status. Persist that identifier to confirm or release the exact reservation later.

## Build and test

```bash
mvn clean verify
```

GitHub Actions runs Maven verification against PostgreSQL 17. The suite includes unit tests, Spring Modulith boundary verification, and PostgreSQL-backed integration tests for Flyway, JPA persistence, and customer/catalog/inventory workflows.

## Design rules

1. Organize code by business module, then by technical responsibility inside that module.
2. A module owns its entities, repositories, and schema.
3. No cross-module repository or table access.
4. Keep module APIs small and explicit.
5. Use domain events for decoupled collaboration.
6. Extract a microservice only when independent deployment, scaling, or ownership justifies its operational cost.

## Status

- [x] Repository bootstrap and PostgreSQL Compose setup
- [x] Spring Boot application skeleton
- [x] Customer API, persistence, duplicate handling, and tests
- [x] Catalog API, persistence, and tests
- [x] Inventory API, persistence, atomic stock reservation, and tests
- [x] Durable reservation ledger with release/confirm lifecycle and idempotency
- [x] Flyway migrations for customer, catalog, and inventory reservations
- [x] Spring Modulith boundary verification
- [x] PostgreSQL-backed inventory lifecycle integration tests
- [x] GitHub Actions Maven verification workflow
- [ ] Order lifecycle and invariants
- [ ] Payment and shipping integration boundaries
- [ ] Transactional outbox and saga workflow
