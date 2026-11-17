package io.github.alighaziam.eom.checkout.api;

import io.github.alighaziam.eom.checkout.CheckoutOperations;
import io.github.alighaziam.eom.checkout.application.CheckoutNotFoundException;
import io.github.alighaziam.eom.checkout.application.CheckoutService;
import io.github.alighaziam.eom.operations.OperatorAuditService;
import io.github.alighaziam.eom.order.OrderOperations;
import io.github.alighaziam.eom.shipping.DeliveryAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkouts")
public class CheckoutController {

    private final CheckoutService service;
    private final OperatorAuditService audit;

    public CheckoutController(CheckoutService service, OperatorAuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public Page<CheckoutOperations.CheckoutResult> list(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "8") int size,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String status) {
        return service.search(q, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutOperations.CheckoutResult checkout(@Valid @RequestBody Request request) {
        return service.checkout(new CheckoutOperations.CheckoutRequest(request.customerId(),
                request.lines().stream()
                        .map(line -> new OrderOperations.OrderLineRequest(line.productId(), line.quantity()))
                        .toList(),
                new DeliveryAddress(request.recipientName(), request.addressLine1(), request.addressLine2(),
                        request.city(), request.postalCode(), request.countryCode(), request.phone()),
                request.idempotencyKey()));
    }

    @GetMapping("/{id}")
    public CheckoutOperations.CheckoutResult get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/reconcile")
    public CheckoutOperations.CheckoutResult reconcile(@PathVariable UUID id, Authentication authentication) {
        CheckoutOperations.CheckoutResult result;
        try {
            result = service.reconcile(id);
        } catch (RuntimeException failure) {
            audit.record(actor(authentication), "CHECKOUT_RECONCILE", "CheckoutWorkflow", id.toString(),
                    "FAILED", "Reconciliation failed: " + failure.getClass().getSimpleName());
            throw failure;
        }
        audit.record(actor(authentication), "CHECKOUT_RECONCILE", "CheckoutWorkflow", id.toString(),
                "SUCCEEDED", "Operator compensation reconciliation completed.");
        return result;
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return "unknown";
        }
        return authentication.getName();
    }

    @ExceptionHandler(CheckoutNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail notFound(CheckoutNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail conflict(IllegalStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail badRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    public record Line(@NotNull UUID productId, @Min(1) int quantity) {
    }

    public record Request(@NotNull UUID customerId,
                          @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> lines,
                          @NotBlank @Size(max = 180) String recipientName,
                          @NotBlank @Size(max = 240) String addressLine1,
                          @Size(max = 240) String addressLine2,
                          @NotBlank @Size(max = 120) String city,
                          @Size(max = 40) String postalCode,
                          @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
                          @Size(max = 40) String phone,
                          @NotBlank @Size(max = 120) String idempotencyKey) {
    }
}
