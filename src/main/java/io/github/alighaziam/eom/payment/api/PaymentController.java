package io.github.alighaziam.eom.payment.api;
import io.github.alighaziam.eom.payment.*; import io.github.alighaziam.eom.payment.application.PaymentNotFoundException; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.HttpStatus; import org.springframework.http.ProblemDetail; import org.springframework.web.bind.annotation.*; import java.math.BigDecimal; import java.util.UUID;
@RestController @RequestMapping("/api/v1/payments") public class PaymentController {
 private final PaymentOperations payments; public PaymentController(PaymentOperations p){payments=p;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public PaymentIntent authorize(@Valid @RequestBody AuthorizeRequest r){return payments.authorize(r.orderId(),r.amount(),r.currency(),r.idempotencyKey());}
 @PostMapping("/{id}/capture") public PaymentIntent capture(@PathVariable UUID id){return payments.capture(id);}
 @PostMapping("/{id}/void") public PaymentIntent voidPayment(@PathVariable UUID id){return payments.voidAuthorization(id);}
 @PostMapping("/{id}/refund") public PaymentIntent refund(@PathVariable UUID id){return payments.refund(id);}
 @GetMapping("/{id}") public PaymentIntent get(@PathVariable UUID id){return payments.get(id);}
 @ExceptionHandler(PaymentNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
 ProblemDetail notFound(PaymentNotFoundException e){return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.CONFLICT)
 ProblemDetail conflict(IllegalStateException e){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage());}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
 ProblemDetail badRequest(IllegalArgumentException e){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage());}
 public record AuthorizeRequest(@NotNull UUID orderId,@NotNull @DecimalMin("0.0001") BigDecimal amount,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@NotBlank @Size(max=120) String idempotencyKey){}
}