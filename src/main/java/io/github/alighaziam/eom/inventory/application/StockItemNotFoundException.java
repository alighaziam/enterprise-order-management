package io.github.alighaziam.eom.inventory.application;
import java.util.UUID;
public class StockItemNotFoundException extends RuntimeException{
 public StockItemNotFoundException(UUID id){super("Stock item not found for product: "+id);}
}