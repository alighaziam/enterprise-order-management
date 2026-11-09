package io.github.alighaziam.eom.outbox.application;
import io.github.alighaziam.eom.outbox.OutboxOperations; import io.github.alighaziam.eom.outbox.infrastructure.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.Instant; import java.util.UUID;
@Service public class OutboxService implements OutboxOperations {
 private final OutboxRepository repository; public OutboxService(OutboxRepository repository){this.repository=repository;}
 @Transactional public UUID append(String type,String aggregateType,String aggregateId,long version,String correlationId,String causationId,String payload){
 UUID id=UUID.randomUUID(); Instant now=Instant.now(); repository.save(new OutboxEntity(id,type,1,now,aggregateType,aggregateId,version,correlationId==null?aggregateId:correlationId,causationId,payload==null?"{}":payload,now)); return id;
 }} 