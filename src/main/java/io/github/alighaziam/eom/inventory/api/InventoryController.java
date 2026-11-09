package io.github.alighaziam.eom.inventory.api;

import io.github.alighaziam.eom.inventory.application.*;
import io.github.alighaziam.eom.inventory.domain.Reservation;
import io.github.alighaziam.eom.inventory.domain.ReservationStatus;
import io.github.alighaziam.eom.inventory.domain.StockItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    public StockResponse initialize(@Valid @RequestBody InitializeStockRequest request) {
        return StockResponse.from(service.initializeStock(request.productId(), request.quantity()));
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@Valid @RequestBody ReserveStockRequest request) {
        return ReservationResponse.from(service.reserve(request.productId(), request.quantity()));
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ReservationResponse release(@PathVariable UUID reservationId) {
        return ReservationResponse.from(service.release(reservationId));
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ReservationResponse confirm(@PathVariable UUID reservationId) {
        return ReservationResponse.from(service.confirm(reservationId));
    }

    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable UUID productId) {
        return StockResponse.from(service.find(productId));
    }

    @ExceptionHandler({InsufficientStockException.class, IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail conflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({StockItemNotFoundException.class, ReservationNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail notFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    public record InitializeStockRequest(@NotNull UUID productId, @Min(0) int quantity) {}
    public record ReserveStockRequest(@NotNull UUID productId, @Min(1) int quantity) {}

    public record StockResponse(
            UUID productId, int onHandQuantity, int reservedQuantity, int availableQuantity, Instant updatedAt
    ) {
        static StockResponse from(StockItem stock) {
            return new StockResponse(stock.productId(), stock.onHandQuantity(), stock.reservedQuantity(),
                    stock.availableQuantity(), stock.updatedAt());
        }
    }

    public record ReservationResponse(
            UUID reservationId, UUID productId, int quantity, ReservationStatus status,
            Instant createdAt, Instant updatedAt
    ) {
        static ReservationResponse from(Reservation reservation) {
            return new ReservationResponse(reservation.id(), reservation.productId(), reservation.quantity(),
                    reservation.status(), reservation.createdAt(), reservation.updatedAt());
        }
    }
}
