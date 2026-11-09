# ADR-0001: Payment, Shipping, Transactional Outbox, and Saga Boundaries

- **Status:** Accepted
- **Date:** 2026-11-09
- **Context:** Enterprise Order Management modular monolith

## Context

The current system places an order by reserving inventory and persists order/inventory changes in one PostgreSQL transaction. This is correct for the current single-process, single-database deployment. Payment providers and carriers, however, are external systems with independent failure modes, timeouts, and state. A local database transaction cannot atomically commit with those systems.

The next capability increment must establish clear ownership and reliable event publication without prematurely introducing microservices or a broker.

## Decision

### 1. Keep the deployment modular-monolithic

Customer, Catalog, Inventory, Order, Payment, Shipping, and Messaging/Outbox remain modules in one deployable application and one PostgreSQL database for now. Each module owns its domain objects, repositories, and schema. Cross-module collaboration uses published module APIs or versioned events only.

No module may read or write another module's tables or repositories.

### 2. Domain ownership

| Capability | Owns | Does not own |
|---|---|---|
| Order | Purchase lifecycle, order total and line snapshots, workflow correlation | Payment-provider credentials/transactions, carrier operations |
| Payment | Payment intent, idempotency key, provider reference, authorization/capture/void/refund state | Order line or inventory persistence |
| Shipping | Shipment, destination snapshot, carrier reference, tracking and delivery state | Payment or inventory persistence |
| Outbox/Messaging | Event envelope, delivery attempts, relay status, consumer inbox/deduplication | Business decisions and aggregate state |

Payment and Shipping publish narrow root-package APIs. Their persistence remains private.

### 3. Payment lifecycle and safety

Payment states: REQUESTED, AUTHORIZED, CAPTURED, FAILED, VOIDED, REFUND_PENDING, REFUNDED. State transitions are explicit and validated. Authorization and capture are separate commands.

Every provider command has a stable idempotency key derived from a persisted payment operation, not a transient HTTP request. Store provider references and sanitized outcomes; never persist card PAN/CVV or log secrets. The initial adapter is a deterministic sandbox/fake gateway. Real provider credentials and money movement are out of scope until separately approved.

A provider timeout is an **unknown outcome**, not a failure. Query/reconcile by idempotency key or provider reference before retrying a non-idempotent financial operation. Refund and void are separate compensating actions and must be auditable.

### 4. Shipping lifecycle and safety

Shipping owns an immutable destination/contact snapshot captured for fulfillment, shipment identity, carrier reference, and tracking state. Suggested states: PENDING, LABEL_CREATED, READY_FOR_DISPATCH, DISPATCHED, IN_TRANSIT, DELIVERED, DELIVERY_FAILED, RETURNED, CANCELLED.

Create a shipment only after the workflow reaches its explicit paid-and-stock-committed milestone. Carrier calls use idempotency where supported. A shipment failure is an operational fulfillment exception; it must not silently mutate payment or order history. Returns and refunds are separate workflows.

### 5. Target orchestration

The target happy path is:

1. Order is placed and inventory is reserved.
2. Payment intent is created and authorized.
3. Payment is captured according to the chosen business policy.
4. Inventory reservation is confirmed.
5. Order advances to its paid/confirmed state.
6. Shipping creates a shipment from the address snapshot.
7. Shipment is dispatched and eventually delivered.

The exact capture-versus-stock-confirm sequence must be revisited if inventory confirmation can fail irrecoverably. In this portfolio implementation, the saga must define explicit compensation and reconciliation for every failure window rather than assuming a distributed rollback.

### 6. Compensation matrix

| Failure | Action |
|---|---|
| Authorization rejected | Release reservation; fail/cancel workflow |
| Capture rejected after authorization | Void authorization; release reservation |
| Capture outcome unknown | Reconcile provider before issuing another capture |
| Inventory confirmation fails after capture | Retry idempotently; on terminal failure refund captured payment and resolve reservation safely |
| Shipment creation fails | Retry shipment creation; keep paid order and committed stock visible as fulfillment-pending |
| Delivery fails | Raise fulfillment exception; handle return/refund through a separate process |

A compensation is a new business command with its own durable result, not a rollback of an already committed external side effect.

### 7. Transactional outbox and inbox

Write aggregate changes and outgoing outbox events in the same PostgreSQL transaction. The relay polls in bounded batches, claims rows safely across concurrent workers, publishes, then records delivery. A crash after publish but before marking delivery can cause a duplicate. Therefore delivery is at least once.

Each event has a UUID event ID, event type, schema version, occurrence timestamp, aggregate type/ID/version, correlation ID, optional causation ID, and payload. Consumers use an inbox/processed-event table with a unique event ID and commit deduplication marker plus business effect atomically.

The relay uses retry/backoff, attempt count, last error, created/delivered timestamps, and an operational dead-letter state. Per-aggregate ordering must be explicit; global ordering is not promised. Payloads must be versioned, bounded, and free of secrets/payment data.

Initial transport is PostgreSQL polling; do not add Kafka/RabbitMQ until throughput or independent deployment requirements justify it.

### 8. Implementation sequence

1. Add outbox/inbox schema, event envelope, append-only publisher API, and relay with concurrency-safe claiming.
2. Add tests for atomic event persistence, retry, duplicate publication, and consumer deduplication.
3. Add Payment domain/API/persistence plus sandbox gateway and idempotency tests.
4. Add Shipping domain/API/persistence and address snapshot tests.
5. Add durable saga/workflow state, compensation handlers, retry scheduling, and reconciliation tests.
6. Update architecture diagrams, API docs, observability, and operational runbook.

## Consequences

- The system gains clear capability ownership while avoiding premature service decomposition.
- Outbox delivery is at least once; consumers must be idempotent.
- The saga is eventually consistent and requires operational visibility and reconciliation.
- A real payment gateway is deliberately excluded from this stage.
- If modules are later split into separate databases/services, local module calls become versioned messages and each service retains its own outbox/inbox.
