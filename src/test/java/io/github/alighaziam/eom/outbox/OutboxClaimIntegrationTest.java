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

    @Test
    void aLaterEventWaitsUntilTheEarlierEventForTheSameAggregateIsPublished() {
        Instant due = Instant.now().plusSeconds(3600);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String aggregate = "order-" + first;
        repository.saveAndFlush(new OutboxEntity(first, "OrderPlaced", 1, due,
                "Order", aggregate, 1, aggregate, null, "{}", due));
        repository.saveAndFlush(new OutboxEntity(second, "OrderConfirmed", 1, due.plusSeconds(1),
                "Order", aggregate, 2, aggregate, null, "{}", due.plusSeconds(1)));
        Instant asOf = due.plusSeconds(10);

        try {
            var claimed = claimService.claimBatch(50, asOf);
            assertTrue(claimed.stream().anyMatch(event -> event.getEventId().equals(first)));
            assertFalse(claimed.stream().anyMatch(event -> event.getEventId().equals(second)));

            OutboxEntity head = repository.findById(first).orElseThrow();
            head.published(asOf);
            repository.saveAndFlush(head);

            var next = claimService.claimBatch(50, asOf);
            assertTrue(next.stream().anyMatch(event -> event.getEventId().equals(second)));
        } finally {
            repository.deleteById(first);
            repository.deleteById(second);
        }
    }

    @Test
    void aDeadLetterBlocksLaterEventsForTheSameAggregate() {
        Instant due = Instant.now().plusSeconds(3600);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String aggregate = "order-" + second;
        OutboxEntity blocked = new OutboxEntity(first, "OrderPlaced", 1, due,
                "Order", aggregate, 1, aggregate, null, "{}", due);
        for (int attempt = 0; attempt < 12; attempt++) {
            blocked.failed("forced", due);
        }
        repository.saveAndFlush(blocked);
        repository.saveAndFlush(new OutboxEntity(second, "OrderConfirmed", 1, due.plusSeconds(1),
                "Order", aggregate, 2, aggregate, null, "{}", due.plusSeconds(1)));

        try {
            var claimed = claimService.claimBatch(50, due.plusSeconds(10));
            assertFalse(claimed.stream().anyMatch(event -> event.getEventId().equals(second)));
        } finally {
            repository.deleteById(first);
            repository.deleteById(second);
        }
    }
}
