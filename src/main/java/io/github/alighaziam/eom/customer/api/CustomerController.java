package io.github.alighaziam.eom.customer.api;

import io.github.alighaziam.eom.customer.application.CustomerService;
import io.github.alighaziam.eom.customer.domain.Customer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = customerService.create(request.fullName(), request.email());
        return CustomerResponse.from(customer);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable UUID id) {
        Customer customer = customerService.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        return CustomerResponse.from(customer);
    }

    public record CreateCustomerRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 254) String email
    ) {}

    public record CustomerResponse(UUID id, String fullName, String email, Instant createdAt) {
        static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.id(), customer.fullName(), customer.email(), customer.createdAt());
        }
    }
}
