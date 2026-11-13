package io.github.alighaziam.eom.payment.application;
import io.github.alighaziam.eom.outbox.OutboxOperations; import io.github.alighaziam.eom.payment.*; import io.github.alighaziam.eom.payment.infrastructure.*; import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.math.BigDecimal; import java.time.Instant; import java.util.List; import java.util.UUID;
@Service public class PaymentService implements PaymentOperations {
 private final PaymentRepository repository; private final SandboxPaymentGateway gateway; private final OutboxOperations outbox;
 public PaymentService(PaymentRepository r,SandboxPaymentGateway g,OutboxOperations o){repository=r;gateway=g;outbox=o;}
 @Transactional public PaymentIntent authorize(UUID orderId,BigDecimal amount,String currency,String key){
 if(orderId==null||amount==null||amount.signum()<=0||currency==null||!currency.matches("[A-Z]{3}")||key==null||key.isBlank()||key.length()>120)throw new IllegalArgumentException("Order, positive amount, ISO currency, and idempotency key are required.");
 var old=repository.findByIdempotencyKey(key);if(old.isPresent()){var p=old.get().toDomain();if(!p.orderId().equals(orderId)||p.amount().compareTo(amount)!=0||!p.currency().equals(currency))throw new IllegalStateException("Idempotency key reused with different payment details.");return p;}
 UUID id=UUID.randomUUID();Instant now=Instant.now();String ref=gateway.authorize(id,amount,currency);var saved=repository.save(new PaymentIntentEntity(id,orderId,amount,currency,key,ref,now));
 outbox.append("PaymentAuthorized","PaymentIntent",id.toString(),1,orderId.toString(),null,"{\"paymentId\":\""+id+"\"}");return saved.toDomain();
 }
 @Transactional public PaymentIntent capture(UUID id){var e=find(id);var p=e.toDomain();if(p.status()==PaymentStatus.CAPTURED)return p;if(p.status()!=PaymentStatus.AUTHORIZED)throw new IllegalStateException("Only authorized payments can be captured.");gateway.capture(p.providerReference());e.status(PaymentStatus.CAPTURED,Instant.now());outbox.append("PaymentCaptured","PaymentIntent",id.toString(),2,p.orderId().toString(),null,"{\"paymentId\":\""+id+"\"}");return e.toDomain();}
 @Transactional public PaymentIntent voidAuthorization(UUID id){var e=find(id);var p=e.toDomain();if(p.status()==PaymentStatus.VOIDED)return p;if(p.status()!=PaymentStatus.AUTHORIZED)throw new IllegalStateException("Only authorized payments can be voided.");gateway.voidAuthorization(p.providerReference());e.status(PaymentStatus.VOIDED,Instant.now());outbox.append("PaymentVoided","PaymentIntent",id.toString(),3,p.orderId().toString(),null,"{\"paymentId\":\""+id+"\"}");return e.toDomain();}
 @Transactional public PaymentIntent refund(UUID id){var e=find(id);var p=e.toDomain();if(p.status()==PaymentStatus.REFUNDED)return p;if(p.status()!=PaymentStatus.CAPTURED)throw new IllegalStateException("Only captured payments can be refunded.");e.status(PaymentStatus.REFUND_PENDING,Instant.now());gateway.refund(p.providerReference());e.status(PaymentStatus.REFUNDED,Instant.now());outbox.append("PaymentRefunded","PaymentIntent",id.toString(),4,p.orderId().toString(),null,"{\"paymentId\":\""+id+"\"}");return e.toDomain();}
 @Transactional(readOnly=true) public PaymentIntent get(UUID id){return find(id).toDomain();}
 @Transactional(readOnly=true) public List<PaymentIntent> list(){return repository.findTop40ByOrderByCreatedAtDesc().stream().map(PaymentIntentEntity::toDomain).toList();}
 @Transactional(readOnly=true) public Page<PaymentIntent> search(String q, String status, int page, int size){
  PaymentStatus parsed=status==null||status.isBlank()?null:PaymentStatus.valueOf(status);
  return repository.search(q==null||q.isBlank()?null:q.trim(), parsed, PageRequest.of(Math.max(page,0), Math.min(Math.max(size,1),100), Sort.by(Sort.Direction.DESC,"createdAt"))).map(PaymentIntentEntity::toDomain);
 }
 private PaymentIntentEntity find(UUID id){return repository.findById(id).orElseThrow(()->new PaymentNotFoundException(id));}
}