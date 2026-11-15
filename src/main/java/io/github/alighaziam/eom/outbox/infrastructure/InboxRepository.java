package io.github.alighaziam.eom.outbox.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface InboxRepository extends JpaRepository<InboxEntity, String> {

    @Modifying
    @Query(value = """
            INSERT INTO messaging.inbox_messages (id, event_id, consumer_name, processed_at)
            VALUES (:id, :eventId, :consumer, :processedAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") String id,
                       @Param("eventId") UUID eventId,
                       @Param("consumer") String consumer,
                       @Param("processedAt") Instant processedAt);
}
