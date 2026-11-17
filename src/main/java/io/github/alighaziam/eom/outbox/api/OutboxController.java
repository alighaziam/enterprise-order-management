package io.github.alighaziam.eom.outbox.api;

import io.github.alighaziam.eom.operations.application.OperatorAuditService;
import io.github.alighaziam.eom.outbox.application.OutboxService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outbox/events")
public class OutboxController {
    private final OutboxService outbox;
    private final OperatorAuditService audit;

    public OutboxController(OutboxService outbox, OperatorAuditService audit) {
        this.outbox = outbox;
        this.audit = audit;
    }

    @GetMapping
    public Page<OutboxService.OutboxView> recent(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "8") int size,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String state) {
        return outbox.search(q, state, page, size);
    }

    @PostMapping("/{eventId}/replay")
    public OutboxService.OutboxView replay(@PathVariable UUID eventId, Authentication authentication) {
        OutboxService.OutboxView replayed;
        try {
            replayed = outbox.replay(eventId);
        } catch (RuntimeException failure) {
            audit.record(actor(authentication), "OUTBOX_REPLAY", "OutboxEvent", eventId.toString(),
                    "FAILED", "Replay failed: " + failure.getClass().getSimpleName());
            throw failure;
        }
        audit.record(actor(authentication), "OUTBOX_REPLAY", "OutboxEvent", eventId.toString(),
                "SUCCEEDED", "Dead-letter event requeued for delivery.");
        return replayed;
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "unknown" : authentication.getName();
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
