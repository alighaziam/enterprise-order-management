# Enterprise Order Management

A portfolio-grade enterprise order management system built as a modular monolith using Java 25, Spring Boot 4.1.2, Spring Modulith 2.1.2, PostgreSQL 17, and Flyway.

## Architecture and module ownership

Each module owns its domain and persistence. Cross-module collaboration uses published root-package APIs; no module reads another module's repositories or tables.

| Module | Responsibility | Implemented capability |
|---|---|---|
| customer | Customer profiles | Create/retrieve customers |
| catalog | Product information and pricing | Product CRUD and active-product query |
| inventory | Stock and reservations | Atomic stock reservation, release, confirmation; active catalog validation and locked manual adjustments |
| order | Purchase lifecycle | Order snapshots, reserve/confirm/cancel, transactional lifecycle events |
| payment | Payment lifecycle | Atomic idempotency-key claim, one active payment per order, order-total validation, locked capture/void/refund; sandbox adapter |
| shipping | Fulfillment lifecycle | Immutable destination snapshot, confirmed-order validation, shipment status transitions, monotonic event versions |
| outbox | Reliable event handoff | Transactional outbox, leased polling relay, retry backoff, dead-letter state, inbox deduplication |
| checkout | Process orchestration | Persisted checkout workflow and compensation |

## Order and checkout consistency

A normal order placement reserves inventory and writes order state plus an `OrderPlaced` outbox event in one PostgreSQL transaction. An order can be confirmed only after a captured payment exists; confirmation consumes all reservations and publishes `OrderConfirmed`. Cancellation is blocked while a payment is active; after it is voided or refunded, cancellation releases reservations and publishes `OrderCancelled`. Manual stock adjustments lock the stock row so they cannot overwrite a concurrent reservation. Repeated confirm/cancel operations are idempotent and order transitions are locked.

The checkout orchestration persists a workflow checkpoint and immutable delivery-address snapshot at each step. Each checkpoint update and its versioned outbox event are committed in the same short PostgreSQL transaction. Payment has two execution modes: the default deterministic sandbox preserves the legacy synchronous path; setting `app.payment.command-execution.enabled=true` persists AUTHORIZE/CAPTURE/VOID/REFUND commands and lets a worker call the provider after the claim transaction commits, then applies the result in a separate lease-guarded transaction. This split-phase foundation is wired through all four payment lifecycle entry points, but no live PSP adapter or provider lookup implementation is included. Do not connect a remote PSP until authoritative operation lookup, signed webhook/replay protection, provider contract tests and secret management are complete. Shipment creation currently uses only the local sandbox; a real carrier adapter must likewise avoid remote calls inside database transactions. Workflow state writes use row-level locking so competing recovery workers cannot silently overwrite a newer checkpoint:

1. Place order and reserve stock.
2. Authorize payment using a stable idempotency key.
3. Capture payment.
4. Confirm the order and consume reserved stock.
5. Create shipment from a delivery-address snapshot.

If authorization/capture fails, the workflow voids an authorization when possible and releases the reservation. If order confirmation fails after capture, it attempts a refund and reservation release. If shipment creation fails after payment and stock commit, the workflow remains FULFILLMENT_PENDING; it does not silently undo completed financial/inventory actions. Failed compensations are recorded as COMPENSATION_REQUIRED for operator reconciliation. Shipment-creation failures retain the address snapshot and are retried by a scheduled worker with capped exponential delay; shipment creation is idempotent by order ID and rejects a replay that changes the delivery destination.

Payment idempotency keys are claimed with an atomic insert-on-conflict operation before authorization, so concurrent requests with the same key resolve to one payment intent. Authorization is permitted only for a reserved order and only when the amount and currency exactly match the persisted order total. A partial unique index enforces one active payment per order, and order cancellation is blocked until an active payment is voided or refunded. The initial payment adapter is deterministic and sandbox-only. It does not connect to a bank, PSP, card network, or real money. It does not collect or store card PAN/CVV. The shipment carrier reference is also a local sandbox placeholder; no carrier API is called.

## Transactional outbox and inbox

Business changes and outgoing event rows are saved in the same local database transaction. The scheduled relay claims bounded batches in a short database transaction using PostgreSQL `FOR UPDATE SKIP LOCKED` and a 120-second lease. Publication happens after the claim transaction, so a slow in-process listener does not hold database row locks. A second application instance skips rows already leased by another worker.

Failed publications increment the attempt counter and are rescheduled with exponential backoff (2 seconds initially, capped at 1 hour). After 12 failed attempts, a row is marked with `dead_lettered_at` and is excluded from automatic claims for operator inspection. Expired leases make abandoned claims eligible again. Successful publication records `published_at` and clears the lease.

The envelope carries event ID, type, schema version, occurrence time, aggregate identity/version, correlation/causation IDs, and payload. Delivery remains at-least-once: a process crash after publication but before `published_at` is committed can cause a duplicate. Consumers should use InboxService.processOnce so the business handler and deduplication marker commit in one transaction. The inbox uses an atomic PostgreSQL insert-on-conflict claim, preventing concurrent deliveries from both running the same consumer handler. The inbox primary key is consumer plus event ID.

The default transport is the in-process Spring event publisher. An optional Kafka transport is available for broker-backed deployments: set `OUTBOX_TRANSPORT=kafka`, set `KAFKA_BOOTSTRAP_SERVERS` (use `kafka:9092` from Compose or `localhost:9092` from the host), and start the optional broker with `docker compose --profile broker up -d --build`. The default topic is `eom.outbox.events`; messages are keyed by aggregate type and ID, and the relay waits for the broker acknowledgement before marking an outbox row published. Delivery remains at-least-once, so consumers must deduplicate with the inbox helper. A claim skips a later event while any earlier event for the same aggregate is still unpublished, including events that are leased, waiting for backoff, or dead-lettered. Dead letters can be replayed from the operator desk. Actuator health reports dead-letter and compensation counts at `/actuator/health`; metrics are at `/actuator/metrics`. A lease expiry during a very slow publish can still result in duplicate delivery; consumers must remain idempotent.

For operational inspection of dead letters:

    SELECT event_id, event_type, aggregate_type, aggregate_id, attempts,
           last_error, dead_lettered_at
    FROM messaging.outbox_events
    WHERE dead_lettered_at IS NOT NULL
    ORDER BY dead_lettered_at DESC;

## Payment provider integration

The payment module uses a provider-neutral adapter contract. The deterministic sandbox is the only included implementation; see [Payment Gateway Integration](docs/architecture/PAYMENT-GATEWAY-INTEGRATION.md) before adding a PSP adapter. Setting `PAYMENT_PROVIDER` to an unsupported value intentionally prevents startup rather than silently falling back to sandbox.

## APIs

| Method | Endpoint | Purpose |
|---|---|---|
| POST | /api/v1/customers | Create customer |
| GET | /api/v1/customers | List recent customers |
| GET | /api/v1/customers/{id} | Retrieve customer |
| POST | /api/v1/products | Create product |
| GET | /api/v1/products | List recent products |
| GET | /api/v1/products/{id} | Retrieve product |
| POST | /api/v1/inventory/stock | Initialize stock |
| GET | /api/v1/inventory | List stock |
| GET | /api/v1/inventory/{productId} | Read stock |
| POST | /api/v1/inventory/reservations | Reserve stock |
| POST | /api/v1/orders | Place order |
| GET | /api/v1/orders | List recent orders |
| GET | /api/v1/orders/{orderId} | Retrieve order |
| POST | /api/v1/orders/{orderId}/confirm | Confirm order |
| POST | /api/v1/orders/{orderId}/cancel | Cancel order |
| POST | /api/v1/payments | Authorize sandbox payment (idempotency key required) |
| POST | /api/v1/payments/{id}/capture | Capture sandbox payment |
| POST | /api/v1/payments/{id}/void | Void authorization |
| POST | /api/v1/payments/{id}/refund | Refund captured payment |
| GET | /api/v1/payments | List recent payments |
| GET | /api/v1/payments/{id} | Retrieve payment |
| POST | /api/v1/shipments | Create shipment with address snapshot |
| GET | /api/v1/shipments | List recent shipments |
| GET | /api/v1/shipments/{id} | Retrieve shipment |
| POST | /api/v1/shipments/{id}/status | Advance shipment status |
| POST | /api/v1/checkouts | Run the persisted checkout workflow |
| GET | /api/v1/checkouts | List recent workflows |
| GET | /api/v1/checkouts/{id} | Retrieve workflow and current status |
| POST | /api/v1/checkouts/{id}/reconcile | Retry local compensation for COMPENSATION_REQUIRED |
| GET | /api/v1/outbox/events | List recent outbox events |
| POST | /api/v1/outbox/events/{eventId}/replay | Replay a dead-lettered event |
| GET | /api/v1/operations/audit | Search operator recovery/replay audit history (operations.admin) |
| GET | /actuator/health | Process health, dead letters, and compensation counts |
| GET | /swagger-ui.html | OpenAPI UI |

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

Copy `.env.example` to `.env` and set a strong local `DB_PASSWORD` plus the exact WSO2 issuer/JWKS URLs. The operator desk requires a WSO2 bearer access token; paste it into the token field in the top bar and choose Apply token. The token is kept in session storage only. Prepare the packaged application once, then bring PostgreSQL and the application up or down:

    powershell -File docker/prepare.ps1
    docker compose up -d --build
    docker compose down

Run `docker/prepare.ps1` again after Java or page changes, then `docker compose up -d --build`. The desk is at http://localhost:8080 and Swagger at http://localhost:8080/swagger-ui.html. On a phone, the same desk keeps a sideways section list, full-width controls, and can be added to the Android home screen. `docker compose down` keeps the database volume. Add `-v` when the volume should be removed too.

Run verification on the host, with Java 25 and Maven:

    mvn clean verify

GitHub Actions runs Maven verification against PostgreSQL 17.

## Architecture documentation and demo

- [Architecture overview](docs/architecture/OVERVIEW.md) — module boundaries, runtime topology, consistency, event delivery, and trade-offs.
- [Architecture decision record](docs/architecture/ADR-0001-payment-shipping-outbox-saga.md) — payment, shipping, outbox, and saga decisions.
- [Local demo runbook](docs/demo/LOCAL-DEMO.md) — reproducible end-to-end checkout walkthrough and failure scenarios.

## Status

- [x] Repository bootstrap, Spring Boot skeleton, PostgreSQL Compose
- [x] Customer and Catalog modules with persistence and tests
- [x] Inventory reservation lifecycle and tests
- [x] Order lifecycle, snapshots, orchestration, and integration tests
- [x] Payment module with concurrency-safe idempotent sandbox authorization, capture, void, refund
- [x] Opt-in persisted split-phase AUTHORIZE/CAPTURE/VOID/REFUND command execution with leases and UNKNOWN outcome handling
- [x] Payment operation status API, PostgreSQL-backed concurrency/lease recovery tests, backlog/age/expired-lease metrics and Prometheus alerts/runbook
- [x] Shipping module with address snapshots and guarded state transitions
- [x] Transactional outbox, leased SKIP LOCKED batch claiming, exponential retry backoff, dead-letter state
- [x] Inbox deduplication helper and at-least-once delivery documentation
- [x] Outbox failure-injection, retry/dead-letter, and PostgreSQL lease integration tests
- [x] Persisted checkout workflow with compensation paths
- [x] Flyway schema and Spring Modulith boundary verification
- [x] PostgreSQL-backed integration tests and GitHub Actions verification
- [x] Per-aggregate event ordering, actuator health and metrics, and dead-letter replay on the operator desk
- [x] Optional Kafka outbox adapter with aggregate-key ordering and acknowledgement handling
- [ ] Real payment provider and carrier integrations (require separate credentials, security review, and approval)
- [x] Scheduled fulfillment recovery for FULFILLMENT_PENDING using persisted address snapshots and exponential retry delay
- [x] Versioned Order, Checkout, and Shipment lifecycle events written transactionally to the outbox
- [x] Operator reconciliation for COMPENSATION_REQUIRED when the order is still reserved; a confirmed captured sale is left intact
- [x] Order confirmation/cancellation guards backed by locked payment state and a one-active-payment-per-order constraint
- [x] Dashboard attention queues and operational counts across filtered result sets
- [x] Non-root application container with read-only root filesystem and dropped Linux capabilities
- [x] Operator audit trail for checkout compensation and dead-letter replay, with admin-only searchable history
- [x] Stable operator audit pagination and bounded search query input

## Security and WSO2 integration

The API is a Spring Security OAuth2 Resource Server. Security is enabled by default and validates JWT signatures using the configured WSO2 JWKS endpoint, plus issuer, expiry and not-before claims. Configure the exact issuer claim emitted by your WSO2 tenant; do not copy the example blindly.

Required authorities are derived from the JWT `scope` or `scp` claim using Spring Security's standard `SCOPE_` mapping:

| API area | Read scope | Write / operator scope |
|---|---|---|
| Customers | `customers.read` | `customers.write` |
| Products | `catalog.read` | `catalog.write` |
| Inventory | `inventory.read` | `inventory.write` |
| Orders | `orders.read` | `orders.write` |
| Payments | `payments.read` | `payments.write` |
| Shipments | `shipping.read` | `shipping.write` |
| Checkouts | `checkout.read` | `checkout.write` |
| Checkout reconciliation, outbox replay, operator audit history, management metrics | — | `operations.admin` |
| Outbox inspection | `outbox.read` | `operations.admin` |

All unmatched API paths are denied. The bearer-token API uses stateless sessions and does not create server-side HTTP sessions. Missing or invalid tokens return HTTP 401, and a valid token without the required scope returns HTTP 403. Both responses use `application/problem+json`. Only health and info are public actuator endpoints; health details are not exposed. Swagger is left reachable for the local operator experience and has an Authorize action for the same bearer token; restrict it at the ingress layer or disable it in a production profile if the API contract is sensitive.

Set `WSO2_ISSUER_URI` and `WSO2_JWKS_URI` to the values for the deployed WSO2 Identity Server, and install the correct CA chain in the JVM truststore. `APP_SECURITY_ENABLED=false` is an explicit test-only bypass; never set it in a deployed environment.

Copy `.env.example` to `.env`, replace the local database password and WSO2 endpoints, then run Compose. PostgreSQL and the application ports are bound to loopback; expose them only through an intentionally configured reverse proxy / network policy.


### Checkout idempotency and transaction boundaries

Checkout first reserves inventory, creates the order/workflow and appends `CheckoutStarted` in one short database transaction. The workflow's unique idempotency-key constraint is the final concurrency arbiter: a competing request that loses the insert race rolls back its local order and inventory reservation and returns the winner's workflow. Payment authorization/capture and shipment creation execute after the reservation transaction commits. The current payment and carrier adapters are local deterministic sandboxes and make no network calls. A real PSP/carrier adapter must use persisted split-phase commands, provider idempotency keys, and outcome reconciliation so remote calls do not hold database locks.

Each new idempotency key is stored with a SHA-256 fingerprint of the customer, ordered line items and delivery address. Replaying the same key with the same request returns the original workflow; reusing it with a different request returns HTTP 409. Existing workflows migrated with the `legacy` fingerprint retain backward-compatible replay behavior.

The opt-in payment command mode stores a stable key and request fingerprint for every financial operation. The worker claims commands just in time, outside-transaction provider I/O is followed by a lease-guarded result transaction, and ambiguous provider outcomes become UNKNOWN rather than being blindly resubmitted. The reconciliation worker is activated only when a provider-specific read-only lookup bean is installed; no live PSP is configured in this repository.


## Payment operation metrics and alerting

The authenticated Prometheus endpoint exports payment operation backlog gauges. Prometheus scrape configuration, alert rules for UNKNOWN/PENDING/RECONCILING operations, secure token handling, threshold tuning, and the operator response runbook are documented in [Payment metrics and alerts](docs/operations/PAYMENT-METRICS-AND-ALERTS.md). Example configuration files are under `deploy/prometheus/`. The scrape identity requires the `operations.admin` scope; do not expose the endpoint publicly.
