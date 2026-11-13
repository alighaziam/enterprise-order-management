package io.github.alighaziam.eom.checkout.infrastructure;
import io.github.alighaziam.eom.checkout.*;import io.github.alighaziam.eom.shipping.DeliveryAddress;import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity @Table(name="workflows",schema="checkout") public class CheckoutWorkflowEntity {
 @Id private UUID id;@Column(name="order_id",nullable=false)private UUID orderId;@Column(name="payment_id")private UUID paymentId;@Column(name="shipment_id")private UUID shipmentId;@Enumerated(EnumType.STRING)@Column(nullable=false,length=32)private CheckoutStatus status;@Column(name="idempotency_key",nullable=false,unique=true,length=120)private String idempotencyKey;@Column(name="last_error",length=1000)private String lastError;@Column(name="created_at",nullable=false)private Instant createdAt;@Column(name="updated_at",nullable=false)private Instant updatedAt;
 @Column(name="recipient_name",length=180)private String recipientName;@Column(name="address_line1",length=240)private String addressLine1;@Column(name="address_line2",length=240)private String addressLine2;@Column(length=120)private String city;@Column(name="postal_code",length=40)private String postalCode;@Column(name="country_code",length=2)private String countryCode;@Column(length=40)private String phone;@Column(name="fulfillment_attempts",nullable=false)private int fulfillmentAttempts;@Column(name="next_attempt_at")private Instant nextAttemptAt;
 protected CheckoutWorkflowEntity(){}
 public CheckoutWorkflowEntity(UUID id,UUID order,String key,DeliveryAddress a,Instant now){this.id=id;orderId=order;idempotencyKey=key;status=CheckoutStatus.ORDER_RESERVED;createdAt=now;updatedAt=now;recipientName=a.recipientName();addressLine1=a.addressLine1();addressLine2=a.addressLine2();city=a.city();postalCode=a.postalCode();countryCode=a.countryCode();phone=a.phone();}
 public void payment(UUID id,CheckoutStatus s,Instant now){paymentId=id;status=s;updatedAt=now;}
 public void shipment(UUID id,Instant now){shipmentId=id;status=CheckoutStatus.COMPLETED;lastError=null;nextAttemptAt=null;updatedAt=now;}
 public void status(CheckoutStatus s,String error,Instant now){status=s;lastError=truncate(error);updatedAt=now;}
 public void scheduleFulfillmentRetry(String error,Instant now){status=CheckoutStatus.FULFILLMENT_PENDING;lastError=truncate(error);fulfillmentAttempts++;long delay=Math.min(3600L,1L<<Math.min(fulfillmentAttempts,12));nextAttemptAt=now.plusSeconds(delay);updatedAt=now;}
 public CheckoutOperations.CheckoutResult toResult(){return new CheckoutOperations.CheckoutResult(id,orderId,paymentId,shipmentId,status,lastError);}
 public UUID getId(){return id;}public UUID getOrderId(){return orderId;}public UUID getPaymentId(){return paymentId;}public CheckoutStatus getStatus(){return status;}
 public DeliveryAddress getDeliveryAddress(){return new DeliveryAddress(recipientName,addressLine1,addressLine2,city,postalCode,countryCode,phone);}
 public Instant getNextAttemptAt(){return nextAttemptAt;}
 private String truncate(String s){return s==null?null:s.substring(0,Math.min(1000,s.length()));}
}