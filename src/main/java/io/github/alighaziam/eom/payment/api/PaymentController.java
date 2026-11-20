package io.github.alighaziam.eom.payment.api;

import io.github.alighaziam.eom.payment.PaymentIntent;
import io.github.alighaziam.eom.payment.PaymentOperationView;
import io.github.alighaziam.eom.payment.application.PaymentNotFoundException;
import io.github.alighaziam.eom.payment.application.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public Page<PaymentIntent> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "8") int size,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(required = false) String status) {
        return payments.search(q, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentIntent authorize(@Valid @RequestBody AuthorizeRequest request) {
        return payments.authorize(request.orderId(), request.amount(),
                request.currency(), request.idempotencyKey());
    }

    @PostMapping("/{id}/capture")
    public PaymentIntent capture(@PathVariable UUID id) {
        return payments.capture(id);
    }

    @PostMapping("/{id}/void")
    public PaymentIntent voidPayment(@PathVariable UUID id) {
        return payments.voidAuthorization(id);
    }

    @PostMapping("/{id}/refund")
    public PaymentIntent refund(@PathVariable UUID id) {
        return payments.refund(id);
    }

    @GetMapping("/{id}")
    public PaymentIntent get(@PathVariable UUID id) {
        return payments.get(id);
    }

    @GetMapping("/{id}/operations")
    public List<PaymentOperationView> operations(@PathVariable UUID id) {
        return payments.operations(id);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail notFound(PaymentNotFoundException exception) {
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

    public record AuthorizeRequest(
            @NotNull UUID orderId,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotBlank @Size(max = 120) String idempotencyKey) {
    }
}
