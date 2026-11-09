package io.github.alighaziam.eom.outbox.infrastructure;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="inbox_messages",schema="messaging") public class InboxEntity {
 @Id private String id; @Column(name="event_id",nullable=false) private UUID eventId; @Column(name="consumer_name",nullable=false,length=120) private String consumerName; @Column(name="processed_at",nullable=false) private Instant processedAt;
 protected InboxEntity(){} public InboxEntity(String id,UUID eventId,String consumerName,Instant processedAt){this.id=id;this.eventId=eventId;this.consumerName=consumerName;this.processedAt=processedAt;}
}