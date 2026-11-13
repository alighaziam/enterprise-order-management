package io.github.alighaziam.eom.outbox.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                FROM messaging.outbox_events AS candidate
                WHERE candidate.published_at IS NULL
                  AND candidate.dead_lettered_at IS NULL
                  AND candidate.attempts < 12
                  AND candidate.next_attempt_at <= :now
                  AND (candidate.locked_until IS NULL OR candidate.locked_until < :now)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM messaging.outbox_events AS earlier
                      WHERE earlier.aggregate_type = candidate.aggregate_type
                        AND earlier.aggregate_id = candidate.aggregate_id
                        AND earlier.published_at IS NULL
                        AND earlier.event_id <> candidate.event_id
                        AND (
                            earlier.aggregate_version < candidate.aggregate_version
                            OR (
                                earlier.aggregate_version = candidate.aggregate_version
                                AND (
                                    earlier.created_at < candidate.created_at
                                    OR (earlier.created_at = candidate.created_at AND earlier.event_id < candidate.event_id)
                                )
                            )
                        )
                  )
                ORDER BY candidate.aggregate_type, candidate.aggregate_id, candidate.aggregate_version, candidate.created_at
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

    List<OutboxEntity> findTop40ByOrderByCreatedAtDesc();

    long countByDeadLetteredAtIsNotNull();

    @Query("""
            select e from OutboxEntity e
            where (:q is null or :q = '' or lower(e.eventType) like lower(concat('%', :q, '%'))
                or lower(e.aggregateType) like lower(concat('%', :q, '%'))
                or lower(e.aggregateId) like lower(concat('%', :q, '%')))
              and (:state is null or :state = ''
                or (:state = 'dead' and e.deadLetteredAt is not null)
                or (:state = 'published' and e.publishedAt is not null)
                or (:state = 'pending' and e.publishedAt is null and e.deadLetteredAt is null))
            """)
    Page<OutboxEntity> search(@Param("q") String q, @Param("state") String state, Pageable pageable);
}
