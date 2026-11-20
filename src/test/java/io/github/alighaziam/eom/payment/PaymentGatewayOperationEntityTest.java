package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayOperationEntityTest {

    private static final String FINGERPRINT =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void rejectsProviderReferencesThatCouldInjectControlCharactersOrExceedStorageContract() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        PaymentGatewayOperationEntity operation = operation(now);
        String workerId = UUID.randomUUID().toString();
        operation.claim(workerId, now.plusSeconds(120), now);

        assertThrows(IllegalArgumentException.class,
                () -> operation.succeeded("provider-ref\r\nforged", workerId, now.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> operation.succeeded("x".repeat(121), workerId, now.plusSeconds(1)));

        assertEquals(PaymentOperationStatus.PROCESSING, operation.getStatus());
        assertTrue(operation.ownsLease(workerId, now.plusSeconds(1)));
    }

    @Test
    void acceptsSafeProviderReferenceAndClearsLeaseOnSuccess() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        PaymentGatewayOperationEntity operation = operation(now);
        String workerId = UUID.randomUUID().toString();
        operation.claim(workerId, now.plusSeconds(120), now);

        operation.succeeded("provider.ref-123:auth", workerId, now.plusSeconds(1));

        assertEquals(PaymentOperationStatus.SUCCEEDED, operation.getStatus());
        assertEquals("provider.ref-123:auth", operation.getProviderReference());
        assertEquals(now.plusSeconds(1), operation.getCompletedAt());
        assertNull(operation.getLockedBy());
        assertNull(operation.getLockedUntil());
        assertFalse(operation.ownsLease(workerId, now.plusSeconds(1)));
    }

    @Test
    void sanitizesAllControlCharactersAndBoundsPersistedErrorText() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        PaymentGatewayOperationEntity operation = operation(now);
        String workerId = UUID.randomUUID().toString();
        operation.claim(workerId, now.plusSeconds(120), now);

        operation.unknown("provider failed\u0000\u001b\r\n" + "x".repeat(600),
                workerId, now.plusSeconds(1));

        assertEquals(PaymentOperationStatus.UNKNOWN, operation.getStatus());
        assertEquals(500, operation.getLastError().length());
        assertFalse(operation.getLastError().chars().anyMatch(Character::isISOControl));
        assertEquals("provider failed", operation.getLastError().substring(0, 15));
        assertNull(operation.getLockedBy());
        assertNull(operation.getLockedUntil());
    }

    @Test
    void rejectsNullLeaseTimesAndExpiredLeases() {
        Instant now = Instant.parse("2026-11-20T10:00:00Z");
        PaymentGatewayOperationEntity operation = operation(now);

        assertThrows(IllegalStateException.class, () -> operation.claim("worker", now, now));
        assertThrows(IllegalStateException.class, () -> operation.claim("worker", now.plusSeconds(1), null));
        assertThrows(IllegalStateException.class, () -> operation.claim("worker", null, now));
    }

    private static PaymentGatewayOperationEntity operation(Instant now) {
        return new PaymentGatewayOperationEntity(UUID.randomUUID(), UUID.randomUUID(),
                PaymentOperationType.AUTHORIZE, "operation-" + UUID.randomUUID(), FINGERPRINT, now);
    }
}
