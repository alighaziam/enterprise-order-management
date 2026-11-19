package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.payment.application.ProviderOperationLookup;
import io.github.alighaziam.eom.payment.application.ProviderOperationState;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ProviderOperationLookupTest {

    private final Instant observedAt = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void acceptsAnInconclusiveNotFoundResultWithoutTreatingItAsAuthoritative() {
        var result = new ProviderOperationLookup(
                ProviderOperationState.NOT_FOUND, null, "NOT_VISIBLE_YET", observedAt, false);

        assertEquals(ProviderOperationState.NOT_FOUND, result.state());
        assertFalse(result.authoritativeNotFound());
    }

    @Test
    void acceptsAuthoritativeNotFoundOnlyWhenExplicitlyGuaranteedByTheAdapter() {
        var result = new ProviderOperationLookup(
                ProviderOperationState.NOT_FOUND, null, "NO_SUCH_OPERATION", observedAt, true);

        assertTrue(result.authoritativeNotFound());
    }

    @Test
    void rejectsAuthoritativeFlagForAnyStateOtherThanNotFound() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationLookup(
                ProviderOperationState.SUCCEEDED, "provider-123", "APPROVED", observedAt, true));
    }

    @Test
    void rejectsUnsafeProviderReferencesAndStatusCodes() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationLookup(
                ProviderOperationState.SUCCEEDED, "provider\n123", "APPROVED", observedAt, false));
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationLookup(
                ProviderOperationState.FAILED, null, "DECLINED\tRETRY", observedAt, false));
    }

    @Test
    void rejectsProviderMetadataThatCouldContainHeadersOrRawResponseText() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationLookup(
                ProviderOperationState.SUCCEEDED, "ref?access_token=secret", "APPROVED", observedAt, false));
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationLookup(
                ProviderOperationState.FAILED, null, "Bearer secret-token", observedAt, false));
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationLookup(
                ProviderOperationState.SUCCEEDED, "provider reference with spaces", "APPROVED", observedAt, false));
    }

    @Test
    void requiresAnObservedStateAndTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationLookup(
                null, null, null, observedAt, false));
        assertThrows(IllegalArgumentException.class, () -> new ProviderOperationLookup(
                ProviderOperationState.INDETERMINATE, null, null, null, false));
    }
}
