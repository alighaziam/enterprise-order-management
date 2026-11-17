package io.github.alighaziam.eom.operations.api;

import io.github.alighaziam.eom.operations.application.OperatorAuditService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/audit")
public class OperatorAuditController {

    private final OperatorAuditService audit;

    public OperatorAuditController(OperatorAuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public Page<OperatorAuditService.AuditEntry> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String outcome) {
        return audit.search(q, outcome, page, size);
    }
}
