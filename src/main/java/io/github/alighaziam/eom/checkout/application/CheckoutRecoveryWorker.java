package io.github.alighaziam.eom.checkout.application;
import io.github.alighaziam.eom.checkout.CheckoutStatus;import io.github.alighaziam.eom.checkout.infrastructure.*;import io.github.alighaziam.eom.outbox.OutboxOperations;import io.github.alighaziam.eom.shipping.ShippingOperations;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional;import java.time.Instant;
@Component public class CheckoutRecoveryWorker {
 private final CheckoutWorkflowRepository workflows;private final ShippingOperations shipping;private final OutboxOperations outbox;
 public CheckoutRecoveryWorker(CheckoutWorkflowRepository w,ShippingOperations s,OutboxOperations o){workflows=w;shipping=s;outbox=o;}
 @Scheduled(fixedDelayString="${app.checkout.recovery-poll-ms:5000}")
 public void retryPendingFulfillment(){
 var due=workflows.findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByUpdatedAtAsc(CheckoutStatus.FULFILLMENT_PENDING,Instant.now());
 for(var workflow:due){
  try{var shipment=shipping.create(workflow.getOrderId(),workflow.getDeliveryAddress());workflow.shipment(shipment.id(),Instant.now());workflows.saveAndFlush(workflow);outbox.append("CheckoutCompleted","CheckoutWorkflow",workflow.getId().toString(),3,workflow.getId().toString(),null,"{\"shipmentId\":\""+shipment.id()+"\"}");}
  catch(RuntimeException ex){workflow.scheduleFulfillmentRetry(ex.getMessage(),Instant.now());workflows.saveAndFlush(workflow);}
 }
 }
}