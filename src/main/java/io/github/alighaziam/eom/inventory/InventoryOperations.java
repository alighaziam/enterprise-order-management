package io.github.alighaziam.eom.inventory;
import java.util.UUID;
public interface InventoryOperations { UUID reserve(UUID productId,int quantity); void confirm(UUID reservationId); void release(UUID reservationId); }
