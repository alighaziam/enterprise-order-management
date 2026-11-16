package io.github.alighaziam.eom.outbox.infrastructure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxClaimService {
    private static final long LEASE_SECONDS = 120;

    private final OutboxRepository repository;

    public OutboxClaimService(OutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<OutboxEntity> claimBatch(int batchSize) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Batch size must be between 1 and 500.");
        }

        return claimBatch(batchSize, Instant.now());
    }

    @Transactional
    public List<OutboxEntity> claimBatch(int batchSize, Instant now) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Batch size must be between 1 and 500.");
        }
        String workerId = UUID.randomUUID().toString();
        repository.claimBatch(workerId, now, now.plusSeconds(LEASE_SECONDS), batchSize);
        return repository.findByLockedByOrderByCreatedAtAsc(workerId);
    }

    @Transactional
    public boolean markPublished(UUID eventId, String workerId, Instant now) {
        OutboxEntity event = ownedLease(eventId, workerId, now);
        if (event == null) {
            return false;
        }
        event.published(now);
        repository.saveAndFlush(event);
        return true;
    }

    @Transactional
    public boolean markFailed(UUID eventId, String workerId, String error, Instant now) {
        OutboxEntity event = ownedLease(eventId, workerId, now);
        if (event == null) {
            return false;
        }
        event.failed(error, now);
        repository.saveAndFlush(event);
        return true;
    }

    private OutboxEntity ownedLease(UUID eventId, String workerId, Instant now) {
        if (eventId == null || workerId == null || workerId.isBlank() || now == null) {
            return null;
        }
        OutboxEntity event = repository.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !workerId.equals(event.getLockedBy())
                || event.getLockedUntil() == null || !event.getLockedUntil().isAfter(now)
                || event.getPublishedAt() != null || event.getDeadLetteredAt() != null) {
            return null;
        }
        return event;
    }
}
