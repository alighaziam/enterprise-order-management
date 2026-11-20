# Payment operation metrics and alerting

## Metrics endpoint

The application exports Micrometer metrics through:

    GET /actuator/prometheus

The endpoint is included in Actuator web exposure but is **not public**. The
default Spring Security configuration requires the `operations.admin` scope
for every `/actuator/**` endpoint. Prometheus therefore needs a bearer access
token carrying that scope. Use a dedicated, least-privilege machine identity,
rotate its token, mount it as a read-only secret, and never store it in Git,
container images, or plain-text deployment manifests.

The payment backlog metric is:

    eom_payment_operations{status="pending"}
    eom_payment_operations{status="processing"}
    eom_payment_operations{status="unknown"}
    eom_payment_operations{status="reconciling"}
    eom_payment_operations{status="succeeded"}
    eom_payment_operations{status="failed"}

The `status` label is an enum with a fixed, bounded set of values. Payment IDs,
customer IDs, provider references, idempotency keys, and error messages are
never metric labels.

## Prometheus configuration

The example configuration is in `deploy/prometheus/prometheus.yml`; alert
rules are in `deploy/prometheus/payment-alerts.yml`. Adjust the target to the
application's private service DNS name and mount a token file at the configured
path. Keep Prometheus and the application on a private network. If TLS is
terminated at a reverse proxy, scrape the HTTPS endpoint and configure the
trusted CA rather than disabling certificate verification.

The token must be valid for the configured WSO2 issuer and include
`operations.admin`. The scrape target will return 401 for a missing/expired
token and 403 when the token lacks the required scope. Monitor Prometheus
`up{job="enterprise-order-management"}` as a separate scrape-health signal.

## Alert intent and tuning

| Alert | Default condition | Meaning |
|---|---|---|
| `PaymentOperationOutcomeUnknown` | UNKNOWN > 0 for 5m | Provider outcome is ambiguous; operator reconciliation is required. |
| `PaymentOperationPendingBacklog` | PENDING > 25 for 10m | Work is accumulating faster than workers complete it, or workers are unavailable. |
| `PaymentOperationReconciliationBacklog` | RECONCILING > 0 for 10m | A reconciliation claim is not completing normally. |

The pending threshold is an initial operational default, not a universal
capacity limit. Tune it using normal peak traffic, worker batch size, provider
latency, and the business's payment volume. Keep the `for` windows to avoid
paging on short-lived bursts. UNKNOWN is intentionally treated more seriously:
do not issue another financial command just because an operation is UNKNOWN.
First query the provider using its reference/idempotency key and reconcile the
durable operation state.

## Response runbook

1. Confirm the alert is not caused by a scrape failure; check Prometheus
   `up{job="enterprise-order-management"}`.
2. Inspect authenticated `/actuator/metrics/eom.payment.operations` or the
   Prometheus series grouped by `status`.
3. For UNKNOWN operations, use the payment operations API and provider's
   read-only lookup/reconciliation process. Never blindly resubmit capture,
   void, or refund.
4. For PENDING growth, check whether the command worker is enabled and healthy,
   whether its provider adapter is available, and whether PostgreSQL is
   accepting writes.
5. For RECONCILING growth, inspect worker logs and lease recovery. Database
   persistence failures must be investigated separately from provider lookup
   failures.
6. Record the operation/payment identifier only in the restricted operational
   ticket or audit system, never as a metric label.

## Readiness boundary

The repository currently contains the provider-neutral reconciliation
contract and sandbox implementation, but no live PSP integration. The
reconciliation worker is conditional on a provider lookup bean. These alerts
make persisted queue state observable; they do not establish that a production
provider adapter or a continuously running reconciliation worker exists.
A real PSP rollout still requires provider-specific lookup semantics,
credentials, operational ownership, and end-to-end verification.
