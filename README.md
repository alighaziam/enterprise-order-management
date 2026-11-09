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
| payment | Payment lifecycle and provider integration | Boundary and lifecycle designed; implementation pending |
| shipping | Shipment lifecycle and carrier integration | Boundary and lifecycle designed; implementation pending |

## Order lifecycle and consistency

Orders use three states: **RESERVED**, **CONFIRMED**, and **CANCELLED**. Placing an order validates the customer and active products, checks a single currency per order, snapshots SKU/name/price, and creates an inventory reservation for every line. The order stores each reservation ID so later operations target the exact reserved quantity.

Confirming an order confirms every reservation and consumes stock. Cancelling a reserved order releases every reservation. Repeated confirmation or cancellation is idempotent; invalid state transitions are rejected. Order transitions use a pessimistic row lock. Order persistence and inventory operations participate in the same PostgreSQL transaction in this modular-monolith deployment, so a failed line operation rolls back the complete use case rather than leaving partial stock changes.

This local transaction boundary is intentional for the current deployment. If Inventory or Order is later extracted into a separate service/database, this orchestration must be redesigned as a durable saga with an outbox, retries, compensation, and idempotent message handling. A local Spring transaction must not be mistaken for a distributed transaction.

## Target payment and fulfillment workflow

The intended business flow is **order reservation → payment authorization → payment capture → inventory confirmation → shipment creation → shipment dispatch/delivery**. This is a target workflow, not a claim that payment or shipping is already implemented.

- Payment owns payment intents, provider references, idempotency keys, and payment state. The planned states are REQUESTED, AUTHORIZED, CAPTURED, FAILED, and REFUNDED. Authorization and capture are separate operations.
- Shipping owns shipment records, immutable delivery-address snapshots, carrier references, and shipment state. A shipment is created only after the order has reached the agreed paid/confirmed milestone.
- Order owns the overall purchase lifecycle and coordinates the workflow; it does not own payment-provider or carrier persistence.
- Payment and shipping expose published module APIs and emit business events. Other modules must not access their repositories or tables.
- A sandbox/fake payment gateway is the only initial adapter. No real money movement or external carrier integration is enabled by this design.

### Saga and compensation policy

The future orchestrator will persist a workflow instance and advance it from durable events/commands. Every step must be idempotent and correlate to a stable order/workflow ID.

| Failure point | Required compensation |
|---|---|
| Payment authorization fails | Release inventory reservation; mark workflow/order failed or cancelled |
| Payment capture fails after authorization | Void authorization where supported; release reservation |
| Inventory confirmation fails after capture | Retry safely; if terminal, refund captured payment and release/resolve reservation |
| Shipment creation fails after payment and inventory commit | Retry shipment creation; do not silently reverse a fulfilled financial/stock step |
| Shipment dispatch/delivery fails | Keep shipment in an operational exception state; handle return/refund as a separate business process |

Compensation is a new business action, not a database rollback. Refund/void outcomes must be recorded and reconciled; a timeout from a provider is an unknown outcome and must be queried/reconciled before issuing a potentially duplicate financial command.

## Transactional outbox design

Business state changes and their outgoing event records will be written in the same local database transaction. A separate relay will claim pending outbox rows, publish them, and mark them delivered. Delivery is **at least once**, not exactly once: consumers must deduplicate by event ID and persist their inbox/processed-message marker in the same transaction as their business effect.

The event envelope will contain:
- `eventId` (UUID), `eventType`, `schemaVersion`, `occurredAt`
- `aggregateType`, `aggregateId`, and monotonic aggregate sequence/version
- `correlationId`, optional `causationId`, and a bounded payload

The relay must use bounded batches, retry with backoff, expose attempt/error/age metrics, and move repeatedly failing records to an operationally visible dead-letter state. Publishing and marking delivered cannot be made atomic with a generic external broker; duplicate publication is expected and handled by consumers. Ordering is guaranteed only per aggregate where the chosen relay/broker strategy explicitly enforces it.

The initial implementation will use PostgreSQL polling and no Kafka dependency. A broker can be introduced when throughput, independent deployment, or operational requirements justify it.

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
3. Cross-module calls use only published root-package interfaces or documented events.
4. Store product and price snapshots on the order; do not recalculate historical orders from the current catalog.
5. Use local ACID transactions only while modules share the same database and process.
6. Before service extraction, use a durable saga/outbox, retries, compensation, idempotent consumers, and reconciliation.
7. Extract a microservice only when independent deployment, scaling, or ownership justifies its operational cost.
8. Never treat a provider timeout as proof that a payment command failed; reconcile unknown outcomes.

## Architecture decisions

- [ADR-0001: Payment, Shipping, Outbox, and Saga boundaries](docs/architecture/ADR-0001-payment-shipping-outbox-saga.md)

## Status

- [x] Repository bootstrap, PostgreSQL Compose, and Spring Boot skeleton
- [x] Customer API, persistence, uniqueness, and tests
- [x] Catalog API, persistence, uniqueness, and tests
- [x] Inventory stock API and durable reservation lifecycle
- [x] Order module with published module APIs, immutable line snapshots, and lifecycle
- [x] Order reserve/confirm/cancel orchestration and integration tests
- [x] Flyway migrations and Spring Modulith boundary verification
- [x] PostgreSQL-backed integration tests and GitHub Actions verification
- [x] Payment/shipping ownership, target lifecycle, compensation, and outbox architecture documented
- [ ] Transactional outbox persistence, relay, and consumer idempotency
- [ ] Payment module with idempotent sandbox gateway
- [ ] Shipping module and fulfillment lifecycle
- [ ] Durable saga orchestration, retries, and reconciliation
