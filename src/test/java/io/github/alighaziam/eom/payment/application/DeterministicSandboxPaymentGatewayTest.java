package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicSandboxPaymentGatewayTest {

    private final DeterministicSandboxPaymentGateway gateway = new DeterministicSandboxPaymentGateway();

    @Test
    void authorizeIsIdempotentAndRejectsChangedFinancialContext() {
        UUID paymentId = UUID.randomUUID();
        String reference = gateway.authorize(paymentId, new BigDecimal("10.00"), "EUR", "auth-key");

        assertEquals("sandbox-" + paymentId, reference);
        assertEquals(reference,
                gateway.authorize(paymentId, new BigDecimal("10.00"), "EUR", "auth-key"));
        assertThrows(IllegalStateException.class,
                () -> gateway.authorize(paymentId, new BigDecimal("11.00"), "EUR", "auth-key"));
        assertThrows(IllegalStateException.class,
                () -> gateway.authorize(paymentId, new BigDecimal("10.00"), "USD", "auth-key"));
    }

    @Test
    void reconciliationReturnsSuccessOnlyForMatchingTypeAmountCurrencyAndReference() {
        UUID paymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("10.00");
        String reference = gateway.authorize(paymentId, amount, "EUR", "auth-key");

        ProviderOperationLookup found = gateway.lookup(PaymentOperationType.AUTHORIZE,
                "auth-key", null, amount, "EUR");
        assertEquals(ProviderOperationState.SUCCEEDED, found.state());
        assertEquals(reference, found.providerReference());
        assertEquals("APPROVED", found.providerStatusCode());

        assertEquals(ProviderOperationState.INDETERMINATE,
                gateway.lookup(PaymentOperationType.AUTHORIZE, "auth-key", null,
                        new BigDecimal("10.01"), "EUR").state());
        assertEquals(ProviderOperationState.INDETERMINATE,
                gateway.lookup(PaymentOperationType.AUTHORIZE, "auth-key", null, amount, "USD").state());
        assertEquals(ProviderOperationState.INDETERMINATE,
                gateway.lookup(PaymentOperationType.CAPTURE, "auth-key", null, amount, "EUR").state());
        assertEquals(ProviderOperationState.INDETERMINATE,
                gateway.lookup(PaymentOperationType.AUTHORIZE, "auth-key", "another-ref", amount, "EUR").state());
    }

    @Test
    void missingInMemoryLedgerEntryIsIndeterminateRatherThanAuthoritativeNotFound() {
        ProviderOperationLookup lookup = gateway.lookup(PaymentOperationType.AUTHORIZE,
                "not-retained-after-restart", null, new BigDecimal("10.00"), "EUR");

        assertEquals(ProviderOperationState.INDETERMINATE, lookup.state());
        assertFalse(lookup.authoritativeNotFound());
        assertEquals("SANDBOX_STATE_NOT_RETAINED", lookup.providerStatusCode());
    }

    @Test
    void lifecycleOperationsAreIdempotentAndEnforceLegalTransitions() {
        UUID paymentId = UUID.randomUUID();
        String reference = gateway.authorize(paymentId, new BigDecimal("10.00"), "EUR", "auth-key");

        assertDoesNotThrow(() -> gateway.capture(reference, "capture-key"));
        assertDoesNotThrow(() -> gateway.capture(reference, "capture-key"));
        assertThrows(IllegalStateException.class,
                () -> gateway.voidAuthorization(reference, "void-key"));
        assertDoesNotThrow(() -> gateway.refund(reference, "refund-key"));

        ProviderOperationLookup capture = gateway.lookup(PaymentOperationType.CAPTURE,
                "capture-key", reference, new BigDecimal("10.00"), "EUR");
        ProviderOperationLookup refund = gateway.lookup(PaymentOperationType.REFUND,
                "refund-key", reference, new BigDecimal("10.00"), "EUR");
        assertEquals(ProviderOperationState.SUCCEEDED, capture.state());
        assertEquals("CAPTURED", capture.providerStatusCode());
        assertEquals(ProviderOperationState.SUCCEEDED, refund.state());
        assertEquals("REFUNDED", refund.providerStatusCode());
    }

    @Test
    void voidAndRefundAreIndependentValidLifecyclePaths() {
        UUID voidPaymentId = UUID.randomUUID();
        String voidReference = gateway.authorize(voidPaymentId,
                new BigDecimal("12.00"), "EUR", "void-auth");
        gateway.voidAuthorization(voidReference, "void-key");
        assertEquals("VOIDED", gateway.lookup(PaymentOperationType.VOID, "void-key",
                voidReference, new BigDecimal("12.00"), "EUR").providerStatusCode());

        UUID refundPaymentId = UUID.randomUUID();
        String refundReference = gateway.authorize(refundPaymentId,
                new BigDecimal("14.00"), "EUR", "refund-auth");
        gateway.capture(refundReference, "refund-capture");
        gateway.refund(refundReference, "refund-key");
        assertEquals("REFUNDED", gateway.lookup(PaymentOperationType.REFUND, "refund-key",
                refundReference, new BigDecimal("14.00"), "EUR").providerStatusCode());
    }

    @Test
    void rejectsMissingOrOversizedLifecycleIdempotencyKeys() {
        assertThrows(IllegalArgumentException.class, () -> gateway.capture("sandbox-ref", " "));
        assertThrows(IllegalArgumentException.class,
                () -> gateway.voidAuthorization("sandbox-ref", "x".repeat(121)));
        assertThrows(IllegalArgumentException.class, () -> gateway.refund("sandbox-ref", null));
    }
}
