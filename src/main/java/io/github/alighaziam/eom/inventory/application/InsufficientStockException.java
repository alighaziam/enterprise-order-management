package io.github.alighaziam.eom.inventory.application;
import java.util.UUID;
public class InsufficientStockException extends RuntimeException{
 public InsufficientStockException(UUID id,int quantity){super("Insufficient available stock for product "+id+" (requested "+quantity+").");}
}