package io.github.alighaziam.eom.shipping.application;
import java.util.UUID;
public class ShipmentNotFoundException extends RuntimeException {
 public ShipmentNotFoundException(UUID id){super("Shipment not found: "+id);}
}
