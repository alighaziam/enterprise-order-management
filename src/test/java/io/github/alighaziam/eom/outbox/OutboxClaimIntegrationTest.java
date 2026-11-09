package io.github.alighaziam.eom.outbox;

import io.github.alighaziam.eom.outbox.infrastructure.OutboxClaimService;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OutboxClaimIntegrationTest {

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private OutboxClaimService claimService;

    @Test
    void aClaimedEventIsNotClaimedAgainWhileItsLeaseIsActive() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        repository.saveAndFlush(new OutboxEntity(eventId, "ClaimTest", 1, now,
                "Test", eventId.toString(), 1, eventId.toString(), null, "{}", now));

        try {
            var firstClaim = claimService.claimBatch(50);
            assertTrue(firstClaim.stream().anyMatch(event -> event.getEventId().equals(eventId)));

            var secondClaim = claimService.claimBatch(50);
            assertFalse(secondClaim.stream().anyMatch(event -> event.getEventId().equals(eventId)));
        } finally {
            repository.deleteById(eventId);
        }
    }
}
