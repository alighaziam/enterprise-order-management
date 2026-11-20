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

## Persisted command execution worker

A dormant command executor now claims due `PENDING` operations in bounded batches, recovers expired processing leases before claiming, loads the associated payment snapshot, and dispatches AUTHORIZE/CAPTURE/VOID/REFUND through the provider-neutral `PaymentGateway` port. The claim transaction completes before provider I/O; the normalized success is applied through the existing short-transaction reconciliation service. Any exception is treated as an unknown outcome and never persisted verbatim or blindly retried.

The worker is opt-in through `app.payment.command-execution.enabled=true`; it is disabled by default. This is an execution primitive only: the current `PaymentService` still invokes the sandbox synchronously and does not yet create `gateway_operations` command records. Do not enable the worker in a live environment until command creation and all payment lifecycle entry points are migrated to the split-phase orchestration and verified end-to-end.

## Required workflow change before a live PSP

The current `PaymentService` calls the gateway synchronously while holding a database transaction/row lock. This is acceptable for the deterministic local sandbox, but it is not safe for a remote PSP because network latency and unknown outcomes can hold locks and leave local/provider state divergent.

Before enabling a live adapter, change each remote operation to a persisted split-phase workflow:
1. In a short database transaction, validate and persist an operation record with a stable operation idempotency key and `PENDING` state.
2. Execute the PSP call outside the database transaction.
3. Persist the sanitized response in a second short transaction.
4. On timeout, leave the outcome as `UNKNOWN` and schedule reconciliation; do not mark it failed or issue a second financial operation blindly.
5. Accept signed, deduplicated webhooks as an additional outcome channel; reconcile webhook and polling outcomes idempotently.
6. Publish payment domain events through the transactional outbox only after the local state transition is committed.

Add provider contract tests for duplicate requests, timeout-after-submit, delayed/out-of-order webhooks, invalid signatures, duplicate webhook delivery, amount/currency mismatch, capture/void/refund replay, and reconciliation after process restart.

## Configuration and activation

- `PAYMENT_PROVIDER=sandbox` selects the deterministic sandbox.
- A future provider must have its own explicit adapter and configuration properties, for example merchant/account ID, API base URL, and secret references. No real provider is wired in this repository today.
- Do not set a production provider name until its adapter, webhook verification, reconciliation worker, integration tests, and deployment secrets are complete.
- Never use real card data in this application. Prefer a PSP-hosted checkout/tokenization page so cardholder data does not pass through this service.

## Readiness boundary

The code now has a provider-neutral payment command boundary, durable operation and lease state, and a provider-neutral reconciliation contract/worker. This is **not** a live payment gateway connection or production payment processing enablement. The command executor exists but is opt-in and is not yet connected to payment command creation; provider-specific adapter, webhook verification, and conversion of checkout/payment flows to split-phase processing remain required before a live PSP can safely be activated.
