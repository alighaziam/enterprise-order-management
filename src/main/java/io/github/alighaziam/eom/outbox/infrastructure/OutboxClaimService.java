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

        Instant now = Instant.now();
        String workerId = UUID.randomUUID().toString();
        repository.claimBatch(workerId, now, now.plusSeconds(LEASE_SECONDS), batchSize);
        return repository.findByLockedByOrderByCreatedAtAsc(workerId);
    }
}
