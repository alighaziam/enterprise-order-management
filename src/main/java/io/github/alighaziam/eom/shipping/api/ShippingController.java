package io.github.alighaziam.eom.shipping.api;
import io.github.alighaziam.eom.shipping.*; import io.github.alighaziam.eom.shipping.application.ShipmentNotFoundException; import io.github.alighaziam.eom.shipping.application.ShippingService; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.data.domain.Page; import org.springframework.http.HttpStatus; import org.springframework.http.ProblemDetail; import org.springframework.web.bind.annotation.*; import java.util.List; import java.util.UUID;
@RestController @RequestMapping("/api/v1/shipments") public class ShippingController {
 private final ShippingService service;public ShippingController(ShippingService s){service=s;}
 @GetMapping public Page<Shipment> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="8") int size,@RequestParam(required=false) String q,@RequestParam(required=false) String status){return service.search(q,status,page,size);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Shipment create(@Valid @RequestBody CreateRequest r){return service.create(r.orderId(),new DeliveryAddress(r.recipientName(),r.addressLine1(),r.addressLine2(),r.city(),r.postalCode(),r.countryCode(),r.phone()));}
 @GetMapping("/{id}") public Shipment get(@PathVariable UUID id){return service.get(id);}
 @PostMapping("/{id}/status") public Shipment update(@PathVariable UUID id,@Valid @RequestBody StatusRequest r){return service.updateStatus(id,r.status());}
 @ExceptionHandler(ShipmentNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
 ProblemDetail notFound(ShipmentNotFoundException e){return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.CONFLICT)
 ProblemDetail conflict(IllegalStateException e){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage());}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
 ProblemDetail badRequest(IllegalArgumentException e){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,e.getMessage());}
 public record CreateRequest(@NotNull UUID orderId,@NotBlank @Size(max=180) String recipientName,@NotBlank @Size(max=240) String addressLine1,@Size(max=240) String addressLine2,@NotBlank @Size(max=120) String city,@Size(max=40) String postalCode,@NotBlank @Pattern(regexp="[A-Z]{2}") String countryCode,@Size(max=40) String phone){}
 public record StatusRequest(@NotNull ShipmentStatus status){}
}