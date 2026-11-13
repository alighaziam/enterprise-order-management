package io.github.alighaziam.eom.outbox.application;
import io.github.alighaziam.eom.outbox.OutboxOperations; import io.github.alighaziam.eom.outbox.infrastructure.*; import org.springframework.data.domain.Page; import org.springframework.data.domain.PageRequest; import org.springframework.data.domain.Sort; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.Instant; import java.util.List; import java.util.UUID;
@Service public class OutboxService implements OutboxOperations {
 private final OutboxRepository repository; public OutboxService(OutboxRepository repository){this.repository=repository;}
 @Transactional public UUID append(String type,String aggregateType,String aggregateId,long version,String correlationId,String causationId,String payload){
 UUID id=UUID.randomUUID(); Instant now=Instant.now(); repository.save(new OutboxEntity(id,type,1,now,aggregateType,aggregateId,version,correlationId==null?aggregateId:correlationId,causationId,payload==null?"{}":payload,now)); return id;
 }
 @Transactional(readOnly=true) public List<OutboxView> recent(){return repository.findTop40ByOrderByCreatedAtDesc().stream().map(OutboxService::view).toList();}
 @Transactional(readOnly=true) public Page<OutboxView> search(String q, String state, int page, int size){
  return repository.search(q==null||q.isBlank()?null:q.trim(), state==null||state.isBlank()?null:state, PageRequest.of(Math.max(page,0), Math.min(Math.max(size,1),100), Sort.by(Sort.Direction.DESC,"createdAt"))).map(OutboxService::view);
 }
 @Transactional(readOnly=true) public long deadLetterCount(){return repository.countByDeadLetteredAtIsNotNull();}
 @Transactional public OutboxView replay(UUID id){
  OutboxEntity row=repository.findById(id).orElseThrow(()->new OutboxEventNotFoundException(id));
  if(row.getDeadLetteredAt()==null) throw new IllegalStateException("Only dead-lettered events can be replayed.");
  row.requeue(Instant.now());
  return view(repository.save(row));
 }
 private static OutboxView view(OutboxEntity row){return new OutboxView(row.getEventId(),row.getEventType(),row.getAggregateType(),row.getAggregateId(),row.getAttempts(),row.getLastError(),row.getCreatedAt(),row.getPublishedAt(),row.getDeadLetteredAt());}
 public record OutboxView(UUID eventId,String eventType,String aggregateType,String aggregateId,int attempts,String lastError,Instant createdAt,Instant publishedAt,Instant deadLetteredAt){}
 public static class OutboxEventNotFoundException extends RuntimeException { public OutboxEventNotFoundException(UUID id){super("Outbox event not found: "+id);} }
}