package io.github.alighaziam.eom.checkout.application;
import io.github.alighaziam.eom.checkout.*;import io.github.alighaziam.eom.checkout.infrastructure.*;import io.github.alighaziam.eom.order.OrderOperations;import io.github.alighaziam.eom.order.OrderState;import io.github.alighaziam.eom.payment.*;import io.github.alighaziam.eom.shipping.*;import io.github.alighaziam.eom.outbox.OutboxOperations;import org.springframework.data.domain.Page;import org.springframework.data.domain.PageRequest;import org.springframework.data.domain.Sort;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.time.Instant;import java.util.List;import java.util.UUID;
@Service public class CheckoutService implements CheckoutOperations {
 private final OrderOperations orders;private final PaymentOperations payments;private final ShippingOperations shipping;private final CheckoutWorkflowRepository workflows;private final OutboxOperations outbox;private final CheckoutReservationService reservations;
 public CheckoutService(OrderOperations o,PaymentOperations p,ShippingOperations s,CheckoutWorkflowRepository w,OutboxOperations b,CheckoutReservationService reservations){orders=o;payments=p;shipping=s;workflows=w;outbox=b;this.reservations=reservations;}
 public CheckoutResult checkout(CheckoutRequest r){
 if(r==null||r.customerId()==null||r.lines()==null||r.lines().isEmpty()||r.deliveryAddress()==null||r.idempotencyKey()==null||r.idempotencyKey().isBlank()||r.idempotencyKey().length()>120)throw new IllegalArgumentException("Customer, order lines, delivery address, and idempotency key are required.");
 CheckoutReservationService.ReservationResult reservation;
 try {
  reservation=reservations.reserve(r);
 } catch (org.springframework.dao.DataIntegrityViolationException concurrentDuplicate) {
  return workflows.findByIdempotencyKey(r.idempotencyKey())
   .map(CheckoutWorkflowEntity::toResult)
   .orElseThrow(() -> concurrentDuplicate);
 }
 CheckoutWorkflowEntity w=reservation.workflow();
 if(!reservation.created()) return w.toResult();
 var orderId=w.getOrderId();
 PaymentIntent payment=null;
 try{payment=payments.authorize(order.id(),order.totalAmount(),order.currency(),"checkout:"+r.idempotencyKey());w.payment(payment.id(),CheckoutStatus.PAYMENT_AUTHORIZED,Instant.now());workflows.saveAndFlush(w);payment=payments.capture(payment.id());w.payment(payment.id(),CheckoutStatus.PAYMENT_CAPTURED,Instant.now());workflows.saveAndFlush(w);}
 catch(RuntimeException ex){try{if(payment!=null&&payment.status()==PaymentStatus.AUTHORIZED)payments.voidAuthorization(payment.id());orders.cancelForCheckout(order.id());w.status(CheckoutStatus.FAILED,ex.getMessage(),Instant.now());}catch(RuntimeException compensation){w.status(CheckoutStatus.COMPENSATION_REQUIRED,"Payment step: "+ex.getMessage()+"; compensation: "+compensation.getMessage(),Instant.now());}workflows.saveAndFlush(w);return w.toResult();}
 try{orders.confirmForCheckout(order.id());w.status(CheckoutStatus.ORDER_CONFIRMED,null,Instant.now());workflows.saveAndFlush(w);}
 catch(RuntimeException ex){try{payments.refund(payment.id());orders.cancelForCheckout(order.id());w.status(CheckoutStatus.FAILED,"Order confirmation failed; payment refunded. "+ex.getMessage(),Instant.now());}catch(RuntimeException compensation){w.status(CheckoutStatus.COMPENSATION_REQUIRED,"Order confirmation: "+ex.getMessage()+"; compensation: "+compensation.getMessage(),Instant.now());}workflows.saveAndFlush(w);return w.toResult();}
 try{var shipment=shipping.create(order.id(),r.deliveryAddress());w.shipment(shipment.id(),Instant.now());}catch(RuntimeException ex){w.scheduleFulfillmentRetry(ex.getMessage(),Instant.now());}
 workflows.saveAndFlush(w);outbox.append("Checkout"+w.toResult().status().name(),"CheckoutWorkflow",w.getId().toString(),2,w.getId().toString(),null,"{\"orderId\":\""+order.id()+"\"}");return w.toResult();
 }
 public CheckoutResult get(UUID id){return workflows.findById(id).orElseThrow(()->new CheckoutNotFoundException(id)).toResult();}
 public List<CheckoutResult> list(){return workflows.findTop40ByOrderByUpdatedAtDesc().stream().map(CheckoutWorkflowEntity::toResult).toList();}
 @Transactional(readOnly=true) public Page<CheckoutResult> search(String q, String status, int page, int size){
  CheckoutStatus parsed=status==null||status.isBlank()?null:CheckoutStatus.valueOf(status);
  return workflows.search(q==null||q.isBlank()?null:q.trim(), parsed, PageRequest.of(Math.max(page,0), Math.min(Math.max(size,1),100), Sort.by(Sort.Direction.DESC,"updatedAt"))).map(CheckoutWorkflowEntity::toResult);
 }
 @Transactional(readOnly=true) public long compensationRequiredCount(){return workflows.countByStatus(CheckoutStatus.COMPENSATION_REQUIRED);}

 public CheckoutResult reconcile(UUID id){
  var workflow=workflows.findById(id).orElseThrow(()->new CheckoutNotFoundException(id));
  if(workflow.getStatus()!=CheckoutStatus.COMPENSATION_REQUIRED) throw new IllegalStateException("Only a workflow that needs compensation can be reconciled.");
  try{
   compensate(workflow);
   workflow.status(CheckoutStatus.FAILED,"Compensation completed.",Instant.now());
  }catch(RuntimeException ex){
   workflow.status(CheckoutStatus.COMPENSATION_REQUIRED,ex.getMessage(),Instant.now());
   workflows.saveAndFlush(workflow);
   throw ex;
  }
  return workflows.saveAndFlush(workflow).toResult();
 }

 private void compensate(CheckoutWorkflowEntity workflow){
  PaymentIntent payment=workflow.getPaymentId()==null?null:payments.get(workflow.getPaymentId());
  OrderState order=orders.state(workflow.getOrderId());
  if(order==OrderState.CONFIRMED && payment!=null && (payment.status()==PaymentStatus.CAPTURED || payment.status()==PaymentStatus.REFUND_PENDING))
   throw new IllegalStateException("The order is confirmed and the payment is captured. This does not undo a committed sale; continue fulfillment or refund the payment explicitly.");
  if(payment!=null && payment.status()==PaymentStatus.AUTHORIZED) payments.voidAuthorization(payment.id());
  else if(payment!=null && payment.status()==PaymentStatus.CAPTURED) payments.refund(payment.id());
  else if(payment!=null && payment.status()!=PaymentStatus.VOIDED && payment.status()!=PaymentStatus.REFUNDED && payment.status()!=PaymentStatus.FAILED)
   throw new IllegalStateException("Payment is "+payment.status()+" and cannot be compensated automatically.");
  if(order==OrderState.RESERVED) orders.cancelForCheckout(workflow.getOrderId());
  else if(order==OrderState.CONFIRMED) throw new IllegalStateException("The order is confirmed. Inspect it before releasing stock.");
 }
}