package io.github.alighaziam.eom.inventory.application;
import io.github.alighaziam.eom.inventory.InventoryOperations;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class InventoryModuleApi implements InventoryOperations {
 private final InventoryService service;
 public InventoryModuleApi(InventoryService service){this.service=service;}
 public UUID reserve(UUID productId,int quantity){return service.reserve(productId,quantity).id();}
 public void confirm(UUID reservationId){service.confirm(reservationId);}
 public void release(UUID reservationId){service.release(reservationId);}
}
