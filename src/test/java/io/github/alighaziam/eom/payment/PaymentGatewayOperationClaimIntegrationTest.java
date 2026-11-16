package io.github.alighaziam.eom.payment;

import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationClaimService;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationEntity;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PaymentGatewayOperationClaimIntegrationTest {

    @Autowired
    private PaymentGatewayOperationRepository repository;

    @Autowired
    private PaymentGatewayOperationClaimService claims;

    @Test
    void pendingOperationIsClaimedOnlyOnceWhileLeaseIsActive() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        repository.saveAndFlush(operation(id, now));

        try {
            var first = claims.claimPendingBatch(10, now);
            var claimed = first.stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow();
            assertEquals(PaymentOperationStatus.PROCESSING, claimed.getStatus());
            assertEquals(1, claimed.getAttempts());

            var second = claims.claimPendingBatch(10, now.plusSeconds(30));
            assertFalse(second.stream().anyMatch(item -> item.getId().equals(id)));
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    void expiredProcessingLeaseBecomesUnknownAndIsNeverBlindlyRequeued() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        repository.saveAndFlush(operation(id, now));

        try {
            var claimed = claims.claimPendingBatch(10, now).stream()
                    .filter(item -> item.getId().equals(id)).findFirst().orElseThrow();
            assertEquals(1, claims.recoverExpiredProcessing(now.plusSeconds(121)));

            var recovered = repository.findById(id).orElseThrow();
            assertEquals(PaymentOperationStatus.UNKNOWN, recovered.getStatus());
            assertNull(recovered.getLockedBy());
            assertNull(recovered.getLockedUntil());
            assertTrue(claims.claimPendingBatch(10, now.plusSeconds(122)).stream()
                    .noneMatch(item -> item.getId().equals(id)));
            assertFalse(claims.markSucceeded(id, claimed.getLockedBy(), "late-provider-ref",
                    now.plusSeconds(122)));
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    void aWorkerCannotCompleteAnOperationAfterItsLeaseExpires() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        repository.saveAndFlush(operation(id, now));

        try {
            var claimed = claims.claimPendingBatch(10, now).stream()
                    .filter(item -> item.getId().equals(id)).findFirst().orElseThrow();
            assertFalse(claims.markSucceeded(id, claimed.getLockedBy(), "late-ref", now.plusSeconds(121)));
            assertEquals(PaymentOperationStatus.PROCESSING,
                    repository.findById(id).orElseThrow().getStatus());
        } finally {
            repository.deleteById(id);
        }
    }

    private PaymentGatewayOperationEntity operation(UUID id, Instant now) {
        return new PaymentGatewayOperationEntity(id, UUID.randomUUID(), PaymentOperationType.AUTHORIZE,
                "payment-op-" + id, "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", now);
    }
}
