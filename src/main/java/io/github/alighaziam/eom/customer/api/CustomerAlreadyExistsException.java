package io.github.alighaziam.eom.customer.api;
public class CustomerAlreadyExistsException extends RuntimeException {
 public CustomerAlreadyExistsException(String email) { super("A customer with email already exists: " + email); }
}