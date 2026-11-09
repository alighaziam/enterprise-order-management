package io.github.alighaziam.eom.outbox.application;
import io.github.alighaziam.eom.outbox.IntegrationEvent; import io.github.alighaziam.eom.outbox.infrastructure.*; import org.springframework.context.ApplicationEventPublisher; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional; import java.time.Instant;
@Component public class OutboxRelay {
 private final OutboxRepository repository; private final ApplicationEventPublisher publisher;
 public OutboxRelay(OutboxRepository r,ApplicationEventPublisher p){repository=r;publisher=p;}
 @Scheduled(fixedDelayString="${app.outbox.poll-interval-ms:1000}") @Transactional public void publishPending(){
 for(OutboxEntity row:repository.findTop50ByPublishedAtIsNullAndAttemptsLessThanOrderByCreatedAtAsc(12)){
 try{IntegrationEvent event=row.toEvent();publisher.publishEvent(event);row.published(Instant.now());}
 catch(RuntimeException ex){row.failed(ex.getMessage());}
 }}
}