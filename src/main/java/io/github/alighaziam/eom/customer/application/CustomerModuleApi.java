package io.github.alighaziam.eom.customer.application;
import io.github.alighaziam.eom.customer.CustomerOperations;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class CustomerModuleApi implements CustomerOperations {
 private final CustomerService service;
 public CustomerModuleApi(CustomerService service){this.service=service;}
 public boolean customerExists(UUID id){return service.findById(id).filter(customer -> customer.active()).isPresent();}
}
