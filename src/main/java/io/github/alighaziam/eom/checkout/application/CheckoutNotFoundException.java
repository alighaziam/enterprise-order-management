package io.github.alighaziam.eom.checkout.application;
import java.util.UUID;
public class CheckoutNotFoundException extends RuntimeException {
 public CheckoutNotFoundException(UUID id){super("Checkout workflow not found: "+id);}
}
