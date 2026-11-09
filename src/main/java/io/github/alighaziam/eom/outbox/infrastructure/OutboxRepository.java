package io.github.alighaziam.eom.outbox.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    @Modifying
    @Query(value = """
            WITH candidates AS (
                SELECT event_id
                FROM messaging.outbox_events
                WHERE published_at IS NULL
                  AND dead_lettered_at IS NULL
                  AND attempts < 12
                  AND next_attempt_at <= :now
                  AND (locked_until IS NULL OR locked_until < :now)
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE messaging.outbox_events AS event
               SET locked_by = :workerId,
                   locked_until = :leaseUntil
              FROM candidates
             WHERE event.event_id = candidates.event_id
            """, nativeQuery = true)
    int claimBatch(@Param("workerId") String workerId,
                   @Param("now") Instant now,
                   @Param("leaseUntil") Instant leaseUntil,
                   @Param("batchSize") int batchSize);

    List<OutboxEntity> findByLockedByOrderByCreatedAtAsc(String lockedBy);
}
