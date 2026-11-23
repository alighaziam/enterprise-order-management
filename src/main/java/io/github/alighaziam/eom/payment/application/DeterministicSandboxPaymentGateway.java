package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic, in-process PSP simulator for local development and tests.
 *
 * Its ledger intentionally lives only for the lifetime of this application
 * process. After a restart, a missing ledger entry is INDETERMINATE, never
 * authoritative NOT_FOUND; production adapters must use the provider's
 * durable idempotency/lookup API.
 */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "sandbox", matchIfMissing = true)
public class DeterministicSandboxPaymentGateway implements SandboxPaymentGateway, PaymentGatewayReconciliation {

    private final Map<String, SandboxOperation> operations = new ConcurrentHashMap<>();
    private final Map<String, SandboxOperation> authorizations = new ConcurrentHashMap<>();
    private final Map<String, String> lifecycleByReference = new ConcurrentHashMap<>();

    @Override
    public synchronized String authorize(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {
        if (paymentId == null || amount == null || amount.signum() <= 0
                || currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid sandbox payment request.");
        }
        validateIdempotencyKey(idempotencyKey);
        String reference = "sandbox-" + paymentId;
        SandboxOperation existing = operations.get(idempotencyKey);
        if (existing != null) {
            if (!existing.matches(PaymentOperationType.AUTHORIZE, paymentId, amount, currency, reference)) {
                throw new IllegalStateException("Sandbox idempotency key was reused with a different payment request.");
            }
            return existing.providerReference();
        }
        SandboxOperation operation = new SandboxOperation(PaymentOperationType.AUTHORIZE,
                paymentId, amount, currency, reference, "APPROVED");
        operations.put(idempotencyKey, operation);
        authorizations.put(reference, operation);
        lifecycleByReference.put(reference, "AUTHORIZED");
        return reference;
    }

    @Override
    public synchronized void capture(String reference, String idempotencyKey) {
        recordLifecycleOperation(reference, idempotencyKey, PaymentOperationType.CAPTURE,
                "AUTHORIZED", "CAPTURED");
    }

    @Override
    public synchronized void voidAuthorization(String reference, String idempotencyKey) {
        recordLifecycleOperation(reference, idempotencyKey, PaymentOperationType.VOID,
                "AUTHORIZED", "VOIDED");
    }

    @Override
    public synchronized void refund(String reference, String idempotencyKey) {
        recordLifecycleOperation(reference, idempotencyKey, PaymentOperationType.REFUND,
                "CAPTURED", "REFUNDED");
    }

    private void recordLifecycleOperation(String reference, String idempotencyKey,
                                          PaymentOperationType type, String requiredState, String resultState) {
        check(reference);
        validateIdempotencyKey(idempotencyKey);
        SandboxOperation existing = operations.get(idempotencyKey);
        if (existing != null) {
            if (existing.operationType() != type || !reference.equals(existing.providerReference())) {
                throw new IllegalStateException("Sandbox idempotency key was reused for a different operation.");
            }
            return;
        }
        SandboxOperation authorization = authorizations.get(reference);
        if (authorization == null || !requiredState.equals(lifecycleByReference.get(reference))) {
            throw new IllegalStateException("Sandbox payment lifecycle does not allow this operation.");
        }
        operations.put(idempotencyKey, new SandboxOperation(type, authorization.paymentId(),
                authorization.amount(), authorization.currency(), reference, resultState));
        lifecycleByReference.put(reference, resultState);
    }

    @Override
    public ProviderOperationLookup lookup(PaymentOperationType operationType, String idempotencyKey,
                                          String providerReference, BigDecimal expectedAmount,
                                          String expectedCurrency) {
        Instant observedAt = Instant.now();
        if (operationType == null || idempotencyKey == null || idempotencyKey.isBlank()
                || expectedAmount == null || expectedCurrency == null) {
            return indeterminate(observedAt, "INVALID_LOOKUP_CONTEXT");
        }
        SandboxOperation operation = operations.get(idempotencyKey);
        if (operation == null) {
            // In-memory state may have been lost on restart. Never infer that the PSP
            // did not accept the command from an empty local sandbox ledger.
            return indeterminate(observedAt, "SANDBOX_STATE_NOT_RETAINED");
        }
        if (operation.operationType() != operationType
                || operation.amount().compareTo(expectedAmount) != 0
                || !operation.currency().equals(expectedCurrency)
                || (providerReference != null && !providerReference.equals(operation.providerReference()))) {
            return indeterminate(observedAt, "PAYMENT_CONTEXT_MISMATCH");
        }
        return new ProviderOperationLookup(ProviderOperationState.SUCCEEDED,
                operation.providerReference(), operation.statusCode(), observedAt, false);
    }

    private static ProviderOperationLookup indeterminate(Instant observedAt, String statusCode) {
        return new ProviderOperationLookup(ProviderOperationState.INDETERMINATE,
                null, statusCode, observedAt, false);
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw new IllegalArgumentException("A stable payment operation idempotency key is required.");
        }
    }

    private static void check(String reference) {
        if (reference == null || !reference.startsWith("sandbox-")) {
            throw new IllegalArgumentException("Unknown sandbox payment reference.");
        }
    }

    private record SandboxOperation(PaymentOperationType operationType, UUID paymentId,
                                    BigDecimal amount, String currency,
                                    String providerReference, String statusCode) {
        private boolean matches(PaymentOperationType type, UUID id, BigDecimal expectedAmount,
                                String expectedCurrency, String reference) {
            return operationType == type && paymentId.equals(id)
                    && amount.compareTo(expectedAmount) == 0
                    && currency.equals(expectedCurrency) && providerReference.equals(reference);
        }
    }
}
