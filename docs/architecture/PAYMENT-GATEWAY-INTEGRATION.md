# Payment Gateway Integration

## Current state

The payment domain is provider-neutral at the application boundary. `PaymentService` depends on `PaymentGateway`; the only shipped adapter is `DeterministicSandboxPaymentGateway`. The default provider is `sandbox`.

```dotenv
PAYMENT_PROVIDER=sandbox
```

The application deliberately fails to start if a non-sandbox provider is selected before its adapter is implemented. It never silently falls back to a fake payment when a production provider was requested.

## Adapter contract

Add a provider-specific implementation of `PaymentGateway` in the payment infrastructure layer and register it only for its explicit provider key using Spring conditional configuration. Keep PSP SDK types, HTTP clients, credentials, and provider-specific response models inside that adapter. Do not leak them into the domain, checkout, or API modules.

The adapter must:
- Send the persisted payment ID or operation ID as the PSP idempotency key on every retry.
- Enforce connect/read/overall timeouts and bounded retries. Never retry a financial command with a new key.
- Treat timeouts and connection loss after submission as **unknown outcome**, not as a decline. Reconcile by provider reference/idempotency key before retrying.
- Validate amount, currency, merchant account, and payment ownership against the persisted payment intent.
- Store only provider references and sanitized status/error codes. Never store PAN, CVV, full payment credentials, or raw authorization headers.
- Make capture, void, and refund idempotent and auditable.
- Verify callback/webhook signatures against the raw request body, enforce timestamp/replay protection, and deduplicate provider event IDs before applying state transitions.
- Keep secrets in deployment secret management; never commit them to `.env.example`.

## Persisted operation foundation

`payment.gateway_operations` stores operation type, stable idempotency key, request fingerprint, attempt count, scheduling timestamp, provider reference, and a worker lease. Pending work is claimed in bounded batches with `FOR UPDATE SKIP LOCKED`. A worker may finalize an operation only while it owns an unexpired lease.

An expired `PROCESSING` lease is moved to `UNKNOWN`; it is never silently returned to `PENDING`. Unknown operations have a separate, exclusive `RECONCILING` claim path. If a reconciliation worker itself loses its lease, the operation returns to `UNKNOWN` for another provider lookup. Reconciliation attempts use bounded exponential backoff (30 seconds up to one hour), and the claim query selects only due `UNKNOWN` operations to avoid repeatedly polling a provider at a fixed high rate.

## Provider reconciliation query contract

The application port `PaymentGatewayReconciliation` defines a read-only lookup by operation type and the exact stable idempotency key used for the original command; an existing provider reference may be supplied as a secondary locator. It cannot authorize, capture, void, refund, or resubmit money.

The normalized result distinguishes `SUCCEEDED`, `FAILED`, `PENDING`, `NOT_FOUND`, and `INDETERMINATE`. Provider references and status codes are length-bounded and reject control characters before they can be persisted or logged.

A `NOT_FOUND` result is **not** automatically permission to retry. The adapter may mark it authoritative only when the PSP contract guarantees that the key was never accepted. Eventual-consistency gaps, timeouts, connection failures, malformed responses, and inconclusive lookups must remain non-authoritative or `INDETERMINATE`. Any future retry must reuse the original idempotency key and be decided by a separate, tested state transition.

## Reconciliation worker

When (and only when) a `PaymentGatewayReconciliation` provider bean is installed, the scheduled worker:
1. Claims a bounded batch of `UNKNOWN` operations and commits the `RECONCILING` leases.
2. Calls the provider's read-only lookup **after** the claim transaction has completed and without holding payment or operation row locks.
3. Applies the provider observation in a separate short transaction guarded by the same unexpired worker lease.
4. Atomically updates the operation and corresponding payment state for confirmed success/failure, and emits the existing payment success event through the transactional outbox.
5. Returns `PENDING`, `INDETERMINATE`, all `NOT_FOUND` outcomes, and lookup exceptions to `UNKNOWN`. Even authoritative `NOT_FOUND` does not trigger a financial resubmission; automatic retry is deliberately not implemented.

If the local payment state is incompatible with a provider-confirmed final result, the operation remains `UNKNOWN` for manual investigation rather than overwriting a potentially newer payment state. A stale worker cannot persist a result after lease expiry.

The worker is dormant in the current repository because no provider lookup adapter is installed. The deterministic sandbox remains the only active payment adapter, and its synchronous behavior is unchanged.

## Provider mutation idempotency contract

Every mutating gateway operation now requires a stable idempotency key, not only authorization. The command worker passes the exact key persisted on the operation for capture, void, and refund as well. The legacy synchronous sandbox path derives deterministic keys from payment ID and operation type. A production adapter must transmit these keys to the provider unchanged and prove duplicate submission behavior with provider contract tests.

## PaymentService split-phase orchestration

The opt-in `app.payment.command-execution.enabled=true` flag now switches all four PaymentService lifecycle entry points to persisted commands:

- Authorization commits a REQUESTED payment and a PENDING AUTHORIZE operation together, then returns without provider network I/O.
- Capture and void persist their command while leaving the payment AUTHORIZED until a confirmed result is applied.
- Refund transitions the payment to REFUND_PENDING and persists its REFUND command in the same transaction.
- Repeated lifecycle calls reuse the existing payment/type operation; authorization replay returns the existing REQUESTED payment. A previously FAILED lifecycle command requires manual review rather than silent resubmission.
- Each operation stores a SHA-256 request fingerprint and stable idempotency key. The command worker claims after commit, performs provider I/O outside the transaction, then applies the outcome through the lease-guarded reconciliation service; successful state changes and outbox events commit together.

The flag defaults to false to preserve the existing deterministic synchronous sandbox behavior. Enabling it activates both persisted command creation and the opt-in worker. The worker startup delay is configurable with `app.payment.command-execution.initial-delay` (default PT1S). Do not enable this mode for a live PSP until a real provider adapter, signed webhook verification, and authoritative operation lookup are available. In particular, UNKNOWN outcomes must remain under reconciliation/manual investigation and must never be blindly resubmitted.

## Persisted split-phase payment workflow

The persisted command workflow is now wired through all four `PaymentService` lifecycle entry points:

- AUTHORIZE creates the payment intent and its PENDING command in the same database transaction.
- CAPTURE and VOID persist stable, operation-specific idempotency keys while retaining the AUTHORIZED state until a definitive result is applied.
- REFUND moves the payment to REFUND_PENDING and persists its command atomically.
- The opt-in command worker claims committed commands, calls the provider-neutral gateway outside the database transaction, and applies the result in a separate lease-guarded transaction. Successful payment state transitions and outbox events commit together.
- Provider exceptions and ambiguous outcomes become UNKNOWN; the worker never blindly retries a financial mutation.
- The feature flag `app.payment.command-execution.enabled` defaults to false, preserving the deterministic synchronous sandbox path. When enabled, it activates command creation and execution together.

The split-phase lifecycle has PostgreSQL-backed integration coverage for AUTHORIZE → CAPTURE → REFUND and AUTHORIZE → VOID, including persisted PENDING commands, final states, stable keys, outbox ordering and rejection of conflicting CAPTURE/VOID requests. Locally rejected commands are finalized as FAILED before provider submission; ambiguous provider exceptions remain UNKNOWN for reconciliation. The deterministic sandbox is the only configured provider.

## Remaining requirements before a live PSP

The orchestration boundary is implemented, but this repository is **not production payment processing ready**:

1. Implement a real PSP adapter with explicit connect/read timeouts, TLS and secret-manager integration.
2. Implement authoritative provider operation lookup for UNKNOWN outcomes. Until that exists, UNKNOWN must remain unresolved/manual-review work; never automatically resubmit.
3. Add signed webhook verification, timestamp/replay protection, deduplication and amount/currency/merchant matching.
4. Add provider contract tests for duplicate requests, timeout-after-submit, delayed/out-of-order webhooks, invalid signatures, duplicate delivery, amount/currency mismatch, capture/void/refund replay, and process restart recovery.
5. Validate provider-specific idempotency retention, rate limits, error mapping, reconciliation behavior, operational alerts and disaster recovery in a production-like environment.
6. Enforce mutual exclusion for different unresolved operations on the same payment. PR #50 merged with a row-lock-serialized guard and PostgreSQL integration coverage for both CAPTURE/VOID conflict directions; CI and CodeQL passed on the exact merged PR head.

## Configuration and activation

- `PAYMENT_PROVIDER=sandbox` selects the deterministic sandbox.
- A future provider must have its own explicit adapter and configuration properties, for example merchant/account ID, API base URL, and secret references. No real provider is wired in this repository today.
- Do not set a production provider name until its adapter, webhook verification, reconciliation worker, integration tests, and deployment secrets are complete.
- Never use real card data in this application. Prefer a PSP-hosted checkout/tokenization page so cardholder data does not pass through this service.

## Readiness boundary

The code now has a provider-neutral payment command boundary, durable operation and lease state, and a provider-neutral reconciliation contract/worker. This is **not** a live payment gateway connection or production payment processing enablement. The command executor exists but is opt-in and is not yet connected to payment command creation; provider-specific adapter, webhook verification, and conversion of checkout/payment flows to split-phase processing remain required before a live PSP can safely be activated.
