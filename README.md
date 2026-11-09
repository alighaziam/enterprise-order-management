# Enterprise Order Management

A portfolio-grade enterprise order management system built as a modular monolith using Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.2, PostgreSQL 17, and Flyway.

## Architecture and module ownership

Each module owns its domain and persistence. Cross-module collaboration uses published root-package APIs; no module reads another module's repositories or tables.

| Module | Responsibility | Implemented capability |
|---|---|---|
| customer | Customer profiles | Create/retrieve customers |
| catalog | Product information and pricing | Product CRUD and active-product query |
| inventory | Stock and reservations | Atomic stock reservation, release, confirmation |
| order | Purchase lifecycle | Order snapshots and reserve/confirm/cancel |
| payment | Payment lifecycle | Authorization, capture, void, refund; sandbox adapter |
| shipping | Fulfillment lifecycle | Immutable destination snapshot, shipment status transitions |
| outbox | Reliable event handoff | Transactional outbox, polling relay, inbox deduplication |
| checkout | Process orchestration | Persisted checkout workflow and compensation |

## Order and checkout consistency

A normal order placement reserves inventory and writes order state in one PostgreSQL transaction. Confirming an order consumes all reservations; cancelling releases them. Repeated confirm/cancel operations are idempotent and order transitions are locked.

The checkout orchestration persists a workflow checkpoint at each step:

1. Place order and reserve stock.
2. Authorize payment using a stable idempotency key.
3. Capture payment.
4. Confirm the order and consume reserved stock.
5. Create shipment from a delivery-address snapshot.

If authorization/capture fails, the workflow voids an authorization when possible and releases the reservation. If order confirmation fails after capture, it attempts a refund and reservation release. If shipment creation fails after payment and stock commit, the workflow remains FULFILLMENT_PENDING; it does not silently undo completed financial/inventory actions. Failed compensations are recorded as COMPENSATION_REQUIRED for operator reconciliation.

The initial payment adapter is deterministic and sandbox-only. It does not connect to a bank, PSP, card network, or real money. It does not collect or store card PAN/CVV. The shipment carrier reference is also a local sandbox placeholder; no carrier API is called.

## Transactional outbox and inbox

Business changes and outgoing event rows are saved in the same local database transaction. The scheduled relay polls bounded batches and publishes versioned IntegrationEvent objects to Spring's in-process event publisher. Successful publication marks the row delivered; failed attempts are counted and retained for retry. Rows reaching 12 failed attempts remain visible for manual inspection. No external broker is configured.

The envelope carries event ID, type, schema version, occurrence time, aggregate identity/version, correlation/causation IDs, and payload. Delivery is at-least-once: a crash after publication but before commit can cause a duplicate. Consumers should use InboxService.processOnce so the business handler and deduplication marker commit in one transaction. The inbox primary key is consumer plus event ID.

This relay is a single-deployment polling implementation, not a multi-node broker-grade dispatcher: it does not yet implement SKIP LOCKED/lease-based row claiming, per-aggregate ordering, backoff scheduling, metrics, or an external dead-letter queue. Those are explicit production-hardening items before running multiple application replicas or relying on the event stream for external side effects.

## APIs

| Method | Endpoint | Purpose |
|---|---|---|
| POST | /api/v1/customers | Create customer |
| GET | /api/v1/customers/{id} | Retrieve customer |
| POST | /api/v1/products | Create product |
| GET | /api/v1/products/{id} | Retrieve product |
| POST | /api/v1/inventory/stock | Initialize stock |
| GET | /api/v1/inventory/{productId} | Read stock |
| POST | /api/v1/inventory/reservations | Reserve stock |
| POST | /api/v1/orders | Place order |
| GET | /api/v1/orders/{orderId} | Retrieve order |
| POST | /api/v1/orders/{orderId}/confirm | Confirm order |
| POST | /api/v1/orders/{orderId}/cancel | Cancel order |
| POST | /api/v1/payments | Authorize sandbox payment (idempotency key required) |
| POST | /api/v1/payments/{id}/capture | Capture sandbox payment |
| POST | /api/v1/payments/{id}/void | Void authorization |
| POST | /api/v1/payments/{id}/refund | Refund captured payment |
| GET | /api/v1/payments/{id} | Retrieve payment |
| POST | /api/v1/shipments | Create shipment with address snapshot |
| GET | /api/v1/shipments/{id} | Retrieve shipment |
| POST | /api/v1/shipments/{id}/status | Advance shipment status |
| POST | /api/v1/checkouts | Run the persisted checkout workflow |
| GET | /api/v1/checkouts/{id} | Retrieve workflow and current status |

Example checkout request:

    {
      "customerId": "00000000-0000-0000-0000-000000000001",
      "lines": [{"productId": "00000000-0000-0000-0000-000000000002", "quantity": 2}],
      "recipientName": "Jane Doe",
      "addressLine1": "Main Street 1",
      "city": "Berlin",
      "postalCode": "10115",
      "countryCode": "DE",
      "phone": "+49000000000",
      "idempotencyKey": "checkout-unique-request-001"
    }

## Run locally

    docker compose up -d
    mvn spring-boot:run

Application: http://localhost:8080

Run verification:

    mvn clean verify

GitHub Actions runs Maven verification against PostgreSQL 17.

## Architecture decisions

- ADR-0001: docs/architecture/ADR-0001-payment-shipping-outbox-saga.md

## Status

- [x] Repository bootstrap, Spring Boot skeleton, PostgreSQL Compose
- [x] Customer and Catalog modules with persistence and tests
- [x] Inventory reservation lifecycle and tests
- [x] Order lifecycle, snapshots, orchestration, and integration tests
- [x] Payment module with idempotent sandbox authorization, capture, void, refund
- [x] Shipping module with address snapshots and guarded state transitions
- [x] Transactional outbox, scheduled relay, event envelope, inbox deduplication helper
- [x] Persisted checkout workflow with compensation paths
- [x] Flyway schema and Spring Modulith boundary verification
- [x] PostgreSQL-backed integration tests and GitHub Actions verification
- [ ] Production-grade relay leasing/SKIP LOCKED, exponential retry scheduling, metrics, and broker adapter
- [ ] Real payment provider and carrier integrations (require separate credentials, security review, and approval)
- [ ] Automated recovery worker for FULFILLMENT_PENDING and COMPENSATION_REQUIRED
