package io.github.alighaziam.eom.outbox;

import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEntityTest {

    @Test
    void schedulesCappedExponentialRetriesAndEventuallyDeadLetters() {
        Instant created = Instant.parse("2026-11-09T10:00:00Z");
        OutboxEntity event = new OutboxEntity(UUID.randomUUID(), "OrderPlaced", 1, created,
                "Order", "order-1", 1, "correlation-1", null, "{}", created);

        event.failed("temporary broker outage", created);

        assertEquals(1, event.getAttempts());
        assertEquals(created.plusSeconds(2), event.getNextAttemptAt());
        assertNull(event.getDeadLetteredAt());
        assertNull(event.getLockedBy());

        event.failed("temporary broker outage", created.plusSeconds(2));

        assertEquals(2, event.getAttempts());
        assertEquals(created.plusSeconds(6), event.getNextAttemptAt());

        for (int attempt = 3; attempt <= 12; attempt++) {
            event.failed("permanent failure", created.plusSeconds(attempt * 10L));
        }

        assertEquals(12, event.getAttempts());
        assertNotNull(event.getDeadLetteredAt());
    }

    @Test
    void successfulPublishClearsFailureAndLeaseState() {
        Instant created = Instant.parse("2026-11-09T10:00:00Z");
        OutboxEntity event = new OutboxEntity(UUID.randomUUID(), "OrderPlaced", 1, created,
                "Order", "order-1", 1, "correlation-1", null, "{}", created);

        event.failed("temporary failure", created);
        event.published(created.plusSeconds(5));

        assertEquals(created.plusSeconds(5), event.getPublishedAt());
        assertNull(event.getLockedBy());
        assertNull(event.getDeadLetteredAt());
    }
}
