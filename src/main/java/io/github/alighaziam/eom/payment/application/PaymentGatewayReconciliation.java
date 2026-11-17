package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationType;

/**
 * Read-only provider query used to reconcile an operation whose outcome is
 * unknown. Implementations must never submit, retry, capture, void, or refund
 * money from this method.
 *
 * The stable idempotency key is the primary lookup key. A provider reference
 * is an optional secondary locator. Network calls must be made outside a
 * database transaction and without holding payment/operation row locks.
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
     * @param operationType operation being reconciled
     * @param idempotencyKey exact stable key used for the original submission
     * @param providerReference optional provider reference already persisted
     */
    ProviderOperationLookup lookup(
            PaymentOperationType operationType,
            String idempotencyKey,
            String providerReference);
}
