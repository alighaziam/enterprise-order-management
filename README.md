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
| outbox | Reliable event handoff | Transactional outbox, leased polling relay, retry backoff, dead-letter state, inbox deduplication |
| checkout | Process orchestration | Persisted checkout workflow and compensation |

## Order and checkout consistency

A normal order placement reserves inventory and writes order state in one PostgreSQL transaction. Confirming an order consumes all reservations; cancelling releases them. Repeated confirm/cancel operations are idempotent and order transitions are locked.

The checkout orchestration persists a workflow checkpoint and immutable delivery-address snapshot at each step:

1. Place order and reserve stock.
2. Authorize payment using a stable idempotency key.
3. Capture payment.
4. Confirm the order and consume reserved stock.
5. Create shipment from a delivery-address snapshot.

If authorization/capture fails, the workflow voids an authorization when possible and releases the reservation. If order confirmation fails after capture, it attempts a refund and reservation release. If shipment creation fails after payment and stock commit, the workflow remains FULFILLMENT_PENDING; it does not silently undo completed financial/inventory actions. Failed compensations are recorded as COMPENSATION_REQUIRED for operator reconciliation. Shipment-creation failures retain the address snapshot and are retried by a scheduled worker with capped exponential delay; shipment creation is idempotent by order ID.

The initial payment adapter is deterministic and sandbox-only. It does not connect to a bank, PSP, card network, or real money. It does not collect or store card PAN/CVV. The shipment carrier reference is also a local sandbox placeholder; no carrier API is called.

## Transactional outbox and inbox

Business changes and outgoing event rows are saved in the same local database transaction. The scheduled relay claims bounded batches in a short database transaction using PostgreSQL `FOR UPDATE SKIP LOCKED` and a 120-second lease. Publication happens after the claim transaction, so a slow in-process listener does not hold database row locks. A second application instance skips rows already leased by another worker.

Failed publications increment the attempt counter and are rescheduled with exponential backoff (2 seconds initially, capped at 1 hour). After 12 failed attempts, a row is marked with `dead_lettered_at` and is excluded from automatic claims for operator inspection. Expired leases make abandoned claims eligible again. Successful publication records `published_at` and clears the lease.

The envelope carries event ID, type, schema version, occurrence time, aggregate identity/version, correlation/causation IDs, and payload. Delivery remains at-least-once: a process crash after publication but before `published_at` is committed can cause a duplicate. Consumers should use InboxService.processOnce so the business handler and deduplication marker commit in one transaction. The inbox primary key is consumer plus event ID.

This is a database-leased in-process relay, not a complete external messaging platform. It does not yet provide per-aggregate ordering, external broker delivery, metrics/alerting, or an operator UI for replaying dead letters. A lease expiry during a very slow publish can still result in duplicate delivery; consumers must remain idempotent.

For operational inspection of dead letters:

    SELECT event_id, event_type, aggregate_type, aggregate_id, attempts,
           last_error, dead_lettered_at
    FROM messaging.outbox_events
    WHERE dead_lettered_at IS NOT NULL
    ORDER BY dead_lettered_at DESC;

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
- [x] Transactional outbox, leased SKIP LOCKED batch claiming, exponential retry backoff, dead-letter state
- [x] Inbox deduplication helper and at-least-once delivery documentation
- [x] Outbox failure-injection, retry/dead-letter, and PostgreSQL lease integration tests
- [x] Persisted checkout workflow with compensation paths
- [x] Flyway schema and Spring Modulith boundary verification
- [x] PostgreSQL-backed integration tests and GitHub Actions verification
- [ ] Per-aggregate event ordering, metrics/alerting, external broker adapter, and dead-letter replay UI
- [ ] Real payment provider and carrier integrations (require separate credentials, security review, and approval)
- [x] Scheduled fulfillment recovery for FULFILLMENT_PENDING using persisted address snapshots and exponential retry delay
- [ ] Operator reconciliation workflow for COMPENSATION_REQUIRED (requires provider-side outcome verification)
