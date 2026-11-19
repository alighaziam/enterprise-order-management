package io.github.alighaziam.eom.payment.application;

import java.time.Instant;

/**
 * Sanitized result of a read-only lookup at the payment provider.
 *
 * @param state provider's observed operation state
 * @param providerReference provider-side reference, when available
 * @param providerStatusCode short provider status/code; never a raw response
 * @param observedAt time at which the provider state was observed
 * @param authoritativeNotFound true only when the provider guarantees that
 *                              this idempotency key was never accepted
 */
public record ProviderOperationLookup(
        ProviderOperationState state,
        String providerReference,
        String providerStatusCode,
        Instant observedAt,
        boolean authoritativeNotFound) {

    public ProviderOperationLookup {
        if (state == null || observedAt == null) {
            throw new IllegalArgumentException("Provider lookup state and observation time are required.");
        }
        if (providerReference != null
                && !providerReference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,119}")) {
            throw new IllegalArgumentException(
                    "Provider reference must be a safe identifier token of at most 120 characters.");
        }
        if (providerStatusCode != null
                && !providerStatusCode.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}")) {
            throw new IllegalArgumentException(
                    "Provider status code must be a safe identifier token of at most 80 characters.");
        }
        if (authoritativeNotFound && state != ProviderOperationState.NOT_FOUND) {
            throw new IllegalArgumentException("Only a NOT_FOUND result can be authoritative not-found.");
        }
    }
}
