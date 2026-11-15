package io.github.alighaziam.eom.outbox.application;

import io.github.alighaziam.eom.outbox.infrastructure.InboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class InboxService {

    private final InboxRepository repository;

    public InboxService(InboxRepository repository) {
        this.repository = repository;
    }

    /**
     * Executes a consumer at most once per event and consumer name.
     *
     * The unique inbox row is claimed before invoking the handler. The claim
     * and handler share one transaction, so a handler failure rolls the claim
     * back and allows a later delivery to retry. Concurrent deliveries are
     * serialized by the database primary key rather than an unsafe
     * exists-then-insert check.
     */
    @Transactional
    public boolean processOnce(UUID eventId, String consumer, Runnable handler) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event id is required.");
        }
        if (consumer == null || consumer.isBlank() || consumer.length() > 120) {
            throw new IllegalArgumentException("Consumer name must contain between 1 and 120 characters.");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Event handler is required.");
        }

        String id = consumer + ":" + eventId;
        int inserted = repository.insertIfAbsent(id, eventId, consumer, Instant.now());
        if (inserted == 0) {
            return false;
        }

        handler.run();
        return true;
    }
}
