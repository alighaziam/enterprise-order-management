package io.github.alighaziam.eom.shipping.api;
import io.github.alighaziam.eom.shipping.*; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/api/v1/shipments") public class ShippingController {
 private final ShippingOperations service;public ShippingController(ShippingOperations s){service=s;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Shipment create(@Valid @RequestBody CreateRequest r){return service.create(r.orderId(),new DeliveryAddress(r.recipientName(),r.addressLine1(),r.addressLine2(),r.city(),r.postalCode(),r.countryCode(),r.phone()));}
 @GetMapping("/{id}") public Shipment get(@PathVariable UUID id){return service.get(id);}
 @PostMapping("/{id}/status") public Shipment update(@PathVariable UUID id,@Valid @RequestBody StatusRequest r){return service.updateStatus(id,r.status());}
 public record CreateRequest(@NotNull UUID orderId,@NotBlank @Size(max=180) String recipientName,@NotBlank @Size(max=240) String addressLine1,@Size(max=240) String addressLine2,@NotBlank @Size(max=120) String city,@Size(max=40) String postalCode,@NotBlank @Pattern(regexp="[A-Z]{2}") String countryCode,@Size(max=40) String phone){}
 public record StatusRequest(@NotNull ShipmentStatus status){}
}