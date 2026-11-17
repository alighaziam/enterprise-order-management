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
                && (providerReference.isBlank() || providerReference.length() > 120
                    || containsControlCharacter(providerReference))) {
            throw new IllegalArgumentException("Provider reference must be a safe value of at most 120 characters.");
        }
        if (providerStatusCode != null
                && (providerStatusCode.isBlank() || providerStatusCode.length() > 80
                    || containsControlCharacter(providerStatusCode))) {
            throw new IllegalArgumentException("Provider status code must be a safe value of at most 80 characters.");
        }
        if (authoritativeNotFound && state != ProviderOperationState.NOT_FOUND) {
            throw new IllegalArgumentException("Only a NOT_FOUND result can be authoritative not-found.");
        }
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
