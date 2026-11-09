package io.github.alighaziam.eom.checkout.application;
import io.github.alighaziam.eom.checkout.*;import io.github.alighaziam.eom.checkout.infrastructure.*;import io.github.alighaziam.eom.order.OrderOperations;import io.github.alighaziam.eom.payment.*;import io.github.alighaziam.eom.shipping.*;import io.github.alighaziam.eom.outbox.OutboxOperations;import org.springframework.stereotype.Service;import java.time.Instant;import java.util.UUID;
@Service public class CheckoutService implements CheckoutOperations {
 private final OrderOperations orders;private final PaymentOperations payments;private final ShippingOperations shipping;private final CheckoutWorkflowRepository workflows;private final OutboxOperations outbox;
 public CheckoutService(OrderOperations o,PaymentOperations p,ShippingOperations s,CheckoutWorkflowRepository w,OutboxOperations b){orders=o;payments=p;shipping=s;workflows=w;outbox=b;}
 public CheckoutResult checkout(CheckoutRequest r){
 if(r==null||r.customerId()==null||r.lines()==null||r.lines().isEmpty()||r.deliveryAddress()==null||r.idempotencyKey()==null||r.idempotencyKey().isBlank()||r.idempotencyKey().length()>120)throw new IllegalArgumentException("Customer, order lines, delivery address, and idempotency key are required.");
 var prior=workflows.findByIdempotencyKey(r.idempotencyKey());if(prior.isPresent())return prior.get().toResult();
 var order=orders.place(r.customerId(),r.lines());Instant now=Instant.now();var w=workflows.saveAndFlush(new CheckoutWorkflowEntity(UUID.randomUUID(),order.id(),r.idempotencyKey(),now));
 outbox.append("CheckoutStarted","CheckoutWorkflow",w.getId().toString(),1,w.getId().toString(),null,"{\"orderId\":\""+order.id()+"\"}");
 PaymentIntent payment=null;
 try{payment=payments.authorize(order.id(),order.totalAmount(),order.currency(),"checkout:"+r.idempotencyKey());w.payment(payment.id(),CheckoutStatus.PAYMENT_AUTHORIZED,Instant.now());workflows.saveAndFlush(w);payment=payments.capture(payment.id());w.payment(payment.id(),CheckoutStatus.PAYMENT_CAPTURED,Instant.now());workflows.saveAndFlush(w);}
 catch(RuntimeException ex){try{if(payment!=null&&payment.status()==PaymentStatus.AUTHORIZED)payments.voidAuthorization(payment.id());orders.cancel(order.id());w.status(CheckoutStatus.FAILED,ex.getMessage(),Instant.now());}catch(RuntimeException compensation){w.status(CheckoutStatus.COMPENSATION_REQUIRED,"Payment step: "+ex.getMessage()+"; compensation: "+compensation.getMessage(),Instant.now());}workflows.saveAndFlush(w);return w.toResult();}
 try{orders.confirm(order.id());w.status(CheckoutStatus.ORDER_CONFIRMED,null,Instant.now());workflows.saveAndFlush(w);}
 catch(RuntimeException ex){try{payments.refund(payment.id());orders.cancel(order.id());w.status(CheckoutStatus.FAILED,"Order confirmation failed; payment refunded. "+ex.getMessage(),Instant.now());}catch(RuntimeException compensation){w.status(CheckoutStatus.COMPENSATION_REQUIRED,"Order confirmation: "+ex.getMessage()+"; compensation: "+compensation.getMessage(),Instant.now());}workflows.saveAndFlush(w);return w.toResult();}
 try{var shipment=shipping.create(order.id(),r.deliveryAddress());w.shipment(shipment.id(),Instant.now());}catch(RuntimeException ex){w.status(CheckoutStatus.FULFILLMENT_PENDING,ex.getMessage(),Instant.now());}
 workflows.saveAndFlush(w);outbox.append("Checkout"+w.toResult().status().name(),"CheckoutWorkflow",w.getId().toString(),2,w.getId().toString(),null,"{\"orderId\":\""+order.id()+"\"}");return w.toResult();
 }
 public CheckoutResult get(UUID id){return workflows.findById(id).orElseThrow(()->new IllegalArgumentException("Checkout workflow not found: "+id)).toResult();}
}