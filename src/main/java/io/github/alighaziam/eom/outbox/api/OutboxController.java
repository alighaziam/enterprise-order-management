package io.github.alighaziam.eom.outbox.api;

import io.github.alighaziam.eom.outbox.application.OutboxService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outbox/events")
public class OutboxController {
    private final OutboxService outbox;

    public OutboxController(OutboxService outbox) {
        this.outbox = outbox;
    }

    @GetMapping
    public Page<OutboxService.OutboxView> recent(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "8") int size,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String state) {
        return outbox.search(q, state, page, size);
    }

    @PostMapping("/{eventId}/replay")
    public OutboxService.OutboxView replay(@PathVariable UUID eventId) {
        return outbox.replay(eventId);
    }

    @ExceptionHandler(OutboxService.OutboxEventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail notFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail conflict(IllegalStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
