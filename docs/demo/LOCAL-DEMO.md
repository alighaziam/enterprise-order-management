# Local Demo Runbook

This runbook is a repeatable 10–15 minute walkthrough for reviewers and interviewers. It demonstrates the architecture through observable behavior rather than slides alone.

## 1. Prerequisites

- Docker Engine / Docker Desktop with Compose.
- `curl` and `jq`.
- Java 25 and Maven, only when running `mvn clean verify`.

## 2. Start the application

From the repository root:

```bash
powershell -File docker/prepare.ps1
docker compose up -d --build
```

Wait until `docker compose ps` shows the application as running. Stop the stack with `docker compose down`. Base URL:

```bash
export BASE_URL=http://localhost:8080
```

Run the automated verification separately:

```bash
mvn clean verify
```

## 3. Happy-path checkout

The following script creates a customer and product, initializes stock, and starts a checkout. It uses generated IDs so it can be run against a clean local database.

```bash
set -euo pipefail

CUSTOMER=$(curl -fsS -X POST "$BASE_URL/api/v1/customers" \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Demo Customer","email":"demo.customer@example.test"}')
CUSTOMER_ID=$(echo "$CUSTOMER" | jq -r '.id')

PRODUCT=$(curl -fsS -X POST "$BASE_URL/api/v1/products" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"DEMO-001","name":"Architecture Demo Item","description":"Local demo product","unitPrice":49.90,"currency":"EUR"}')
PRODUCT_ID=$(echo "$PRODUCT" | jq -r '.id')

curl -fsS -X POST "$BASE_URL/api/v1/inventory/stock" \
  -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":10}" | jq

CHECKOUT=$(curl -fsS -X POST "$BASE_URL/api/v1/checkouts" \
  -H 'Content-Type: application/json' \
  -d "{
    \"customerId\":\"$CUSTOMER_ID\",
    \"lines\":[{\"productId\":\"$PRODUCT_ID\",\"quantity\":2}],
    \"recipientName\":\"Demo Customer\",
    \"addressLine1\":\"Example Street 10\",
    \"addressLine2\":null,
    \"city\":\"Berlin\",
    \"postalCode\":\"10115\",
    \"countryCode\":\"DE\",
    \"phone\":\"+49000000000\",
    \"idempotencyKey\":\"demo-checkout-$(date +%s)\"
  }")
echo "$CHECKOUT" | jq
CHECKOUT_ID=$(echo "$CHECKOUT" | jq -r '.id')
curl -fsS "$BASE_URL/api/v1/checkouts/$CHECKOUT_ID" | jq
```

Inspect the returned workflow status. The expected successful terminal state is the implementation's completed/fulfilled state; if the workflow reports fulfillment pending, inspect the scheduled recovery behavior and logs rather than assuming shipment creation succeeded.

## 4. What to explain while demoing

1. **Why a modular monolith?** One deployable unit keeps operational complexity low while module boundaries preserve ownership and extraction options.
2. **Why reserve stock before payment?** It prevents overselling during the payment attempt; the reservation is released if payment cannot complete.
3. **Why capture and confirm are separate?** They are distinct business side effects with different failure and compensation paths.
4. **Why persist the address snapshot?** A later customer profile edit must not change the destination of an already accepted checkout.
5. **Why outbox?** It commits business state and the event record atomically, avoiding a dual-write gap.
6. **Why at-least-once?** A crash after listener publication but before the published marker can cause redelivery; inbox deduplication is therefore required.
7. **Why no Kafka yet?** The current use case does not establish a throughput or independent-deployment requirement that justifies operating a broker.

## 5. Failure scenarios to discuss

- **Insufficient stock:** lower available stock below the requested quantity; order placement should fail without creating a partially reserved order.
- **Duplicate checkout request:** repeat the same checkout request with the same idempotency key; it should return the existing persisted workflow rather than create a second order/payment.
- **Payment failure:** use the sandbox gateway's supported failure behavior only after confirming its configuration; demonstrate reservation release/authorization void and inspect the persisted workflow.
- **Shipment creation failure:** demonstrate that the paid/confirmed order remains fulfillment-pending and that the scheduled worker retries from the stored address snapshot.
- **Outbox retry / dead letter:** use the dedicated outbox tests as the safe reproducible demonstration. Do not corrupt a shared database or manually edit production-like rows to force a failure.

## 6. Evidence and limitations

- CI: [latest workflow run](https://github.com/alighaziam/enterprise-order-management/actions).
- Architecture: [overview](../architecture/OVERVIEW.md) and [ADR-0001](../architecture/ADR-0001-payment-shipping-outbox-saga.md).
- The repository's automated tests exercise PostgreSQL integration, module boundaries, checkout lifecycle, outbox claiming, retry/dead-letter behavior, and failure paths.
- The sandbox gateway is not a PSP. No real card data or money is involved.
- A successful CI run is not a substitute for load testing, security review, production observability, or a live multi-instance deployment test.
