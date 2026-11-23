package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationType;

import java.math.BigDecimal;

/**
 * Read-only provider query used to reconcile an operation whose outcome is
 * unknown. Implementations must never submit, retry, capture, void, or refund
 * money from this method.
 *
 * The stable idempotency key is the primary lookup key. A provider reference
 * is an optional secondary locator. Expected amount and currency are loaded
 * from the persisted payment intent and must be checked against the provider
 * observation before a definitive result is returned.
 */
public interface PaymentGatewayReconciliation {

    /**
     * Looks up the provider-side outcome of an existing operation.
     *
     * A timeout, connection failure, malformed response, or inconclusive
     * provider response must not be reported as NOT_FOUND. Implementations
     * should return INDETERMINATE when the outcome cannot be established.
     *
     * NOT_FOUND may be marked authoritative only when the provider contract
     * guarantees that the idempotency key was never accepted. Eventual
     * consistency or a temporarily missing lookup result is not authoritative.
     *
     * The adapter must compare the provider's amount and currency with the
     * expected persisted values. A mismatch must return INDETERMINATE (or a
     * provider-specific definitive failure only when the contract guarantees
     * that no financial operation was applied); it must never report success.
     *
     * @param operationType operation being reconciled
     * @param idempotencyKey exact stable key used for the original submission
     * @param providerReference optional provider reference already persisted
     * @param expectedAmount amount from the persisted payment intent
     * @param expectedCurrency ISO-4217 currency from the persisted payment intent
     */
    ProviderOperationLookup lookup(
            PaymentOperationType operationType,
            String idempotencyKey,
            String providerReference,
            BigDecimal expectedAmount,
            String expectedCurrency);
}
