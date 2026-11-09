package io.github.alighaziam.eom.outbox.application;
import io.github.alighaziam.eom.outbox.infrastructure.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.Instant; import java.util.UUID;
@Service public class InboxService {
 private final InboxRepository repository; public InboxService(InboxRepository r){repository=r;}
 @Transactional public boolean processOnce(UUID eventId,String consumer,Runnable handler){
 String id=consumer+":"+eventId;if(repository.existsById(id))return false;handler.run();repository.saveAndFlush(new InboxEntity(id,eventId,consumer,Instant.now()));return true;
 }}