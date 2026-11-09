package io.github.alighaziam.eom.order.domain;
import java.math.BigDecimal;
import java.util.UUID;
public record OrderLine(UUID id,UUID productId,String sku,String productName,BigDecimal unitPrice,String currency,int quantity,UUID reservationId){
 public OrderLine{
  if(id==null||productId==null||reservationId==null||sku==null||productName==null||unitPrice==null||currency==null) throw new IllegalArgumentException("Order line fields must not be null.");
  if(quantity<=0||unitPrice.signum()<=0) throw new IllegalArgumentException("Order line quantity and unit price must be positive.");
 }
 public BigDecimal subtotal(){return unitPrice.multiply(BigDecimal.valueOf(quantity));}
}
