package io.github.alighaziam.eom.checkout.api;
import io.github.alighaziam.eom.checkout.*;import io.github.alighaziam.eom.checkout.application.CheckoutNotFoundException;import io.github.alighaziam.eom.order.OrderOperations;import io.github.alighaziam.eom.shipping.DeliveryAddress;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.http.HttpStatus;import org.springframework.http.ProblemDetail;import org.springframework.web.bind.annotation.*;import java.util.List;import java.util.UUID;
@RestController @RequestMapping("/api/v1/checkouts")public class CheckoutController{
 private final CheckoutOperations service;public CheckoutController(CheckoutOperations s){service=s;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)public CheckoutOperations.CheckoutResult checkout(@Valid @RequestBody Request r){return service.checkout(new CheckoutOperations.CheckoutRequest(r.customerId(),r.lines().stream().map(x->new OrderOperations.OrderLineRequest(x.productId(),x.quantity())).toList(),new DeliveryAddress(r.recipientName(),r.addressLine1(),r.addressLine2(),r.city(),r.postalCode(),r.countryCode(),r.phone()),r.idempotencyKey()));}
 @GetMapping("/{id}")public CheckoutOperations.CheckoutResult get(@PathVariable UUID id){return service.get(id);}
 @ExceptionHandler(CheckoutNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
 ProblemDetail notFound(CheckoutNotFoundException e){return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
 ProblemDetail badRequest(IllegalArgumentException e){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage());}
 public record Line(@NotNull UUID productId,@Min(1)int quantity){}
 public record Request(@NotNull UUID customerId,@NotEmpty List<@Valid Line> lines,@NotBlank @Size(max=180)String recipientName,@NotBlank @Size(max=240)String addressLine1,@Size(max=240)String addressLine2,@NotBlank @Size(max=120)String city,@Size(max=40)String postalCode,@NotBlank @Pattern(regexp="[A-Z]{2}")String countryCode,@Size(max=40)String phone,@NotBlank @Size(max=120)String idempotencyKey){}
}