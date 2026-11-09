package io.github.alighaziam.eom.inventory.api;
import io.github.alighaziam.eom.inventory.application.*;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/inventory")
public class InventoryController{
 private final InventoryService service;
 public InventoryController(InventoryService service){this.service=service;}
 @PostMapping("/stock") @ResponseStatus(HttpStatus.CREATED)
 public StockResponse initialize(@Valid @RequestBody InitializeStockRequest r){return StockResponse.from(service.initializeStock(r.productId(),r.quantity()));}
 @PostMapping("/reservations")
 public StockResponse reserve(@Valid @RequestBody ReserveStockRequest r){return StockResponse.from(service.reserve(r.productId(),r.quantity()));}
 @GetMapping("/{productId}") public StockResponse get(@PathVariable UUID productId){return StockResponse.from(service.find(productId));}
 @ExceptionHandler({InsufficientStockException.class,IllegalArgumentException.class,IllegalStateException.class})
 @ResponseStatus(HttpStatus.CONFLICT) ProblemDetail conflict(RuntimeException e){return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,e.getMessage());}
 @ExceptionHandler(StockItemNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
 ProblemDetail notFound(StockItemNotFoundException e){return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,e.getMessage());}
 public record InitializeStockRequest(@NotNull UUID productId,@Min(0) int quantity){}
 public record ReserveStockRequest(@NotNull UUID productId,@Min(1) int quantity){}
 public record StockResponse(UUID productId,int onHandQuantity,int reservedQuantity,int availableQuantity,Instant updatedAt){
  static StockResponse from(StockItem s){return new StockResponse(s.productId(),s.onHandQuantity(),s.reservedQuantity(),s.availableQuantity(),s.updatedAt());}
 }
}