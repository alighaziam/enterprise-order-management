package io.github.alighaziam.eom.catalog.api;
import io.github.alighaziam.eom.catalog.application.*;
import io.github.alighaziam.eom.catalog.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/products")
public class ProductController {
 private final ProductService service;
 public ProductController(ProductService service){this.service=service;}
 @GetMapping public Page<ProductResponse> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="8") int size,@RequestParam(required=false) String q,@RequestParam(required=false) Boolean active){return service.search(q,active,page,size).map(ProductResponse::from);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public ProductResponse create(@Valid @RequestBody CreateProductRequest r){
  return ProductResponse.from(service.create(r.sku(),r.name(),r.description(),r.unitPrice(),r.currency()));
 }
 @GetMapping("/{id}") public ProductResponse get(@PathVariable UUID id){
  return ProductResponse.from(service.findById(id).orElseThrow(()->new ProductNotFoundException(id)));
 }
 @PutMapping("/{id}") public ProductResponse update(@PathVariable UUID id,@Valid @RequestBody UpdateProductRequest r){
  return ProductResponse.from(service.update(id,r.name(),r.description(),r.unitPrice(),r.currency()));
 }
 @PostMapping("/{id}/active") public ProductResponse setActive(@PathVariable UUID id,@RequestBody ActiveRequest r){
  return ProductResponse.from(service.setActive(id,r.active()));
 }
 @ExceptionHandler(ProductNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
 ProblemDetail notFound(ProductNotFoundException e){return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler({DuplicateSkuException.class,IllegalArgumentException.class}) @ResponseStatus(HttpStatus.CONFLICT)
 ProblemDetail conflict(RuntimeException e){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage());}
 public record CreateProductRequest(@NotBlank @Size(max=64) String sku,@NotBlank @Size(max=180) String name,
  @Size(max=1000) String description,@NotNull @DecimalMin("0.0001") @Digits(integer=15,fraction=4) BigDecimal unitPrice,
  @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency){}
 public record UpdateProductRequest(@NotBlank @Size(max=180) String name,@Size(max=1000) String description,
  @NotNull @DecimalMin("0.0001") @Digits(integer=15,fraction=4) BigDecimal unitPrice,@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency){}
 public record ActiveRequest(boolean active){}
 public record ProductResponse(UUID id,String sku,String name,String description,BigDecimal unitPrice,String currency,boolean active,Instant createdAt){
  static ProductResponse from(Product p){return new ProductResponse(p.id(),p.sku(),p.name(),p.description(),p.unitPrice(),p.currency(),p.active(),p.createdAt());}
 }
}