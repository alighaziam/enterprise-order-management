package io.github.alighaziam.eom.operations.application;

import io.github.alighaziam.eom.operations.infrastructure.OperatorActionEntity;
import io.github.alighaziam.eom.operations.infrastructure.OperatorActionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OperatorAuditService {

    private final OperatorActionRepository repository;

    public OperatorAuditService(OperatorActionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String actor, String action, String targetType, String targetId,
                       String outcome, String detail) {
        repository.saveAndFlush(new OperatorActionEntity(UUID.randomUUID(), actor, action,
                targetType, targetId, outcome, detail, Instant.now()));
    }

    @Transactional(readOnly = true)
    public Page<AuditEntry> search(String query, String outcome, int page, int size) {
        String normalizedOutcome = outcome == null || outcome.isBlank()
                ? null : outcome.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalizedOutcome != null && !normalizedOutcome.matches("SUCCEEDED|FAILED")) {
            throw new IllegalArgumentException("Audit outcome must be SUCCEEDED or FAILED.");
        }
        return repository.search(query == null || query.isBlank() ? null : query.trim(),
                        normalizedOutcome,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(row -> new AuditEntry(row.getId(), row.getActor(), row.getAction(),
                        row.getTargetType(), row.getTargetId(), row.getOutcome(),
                        row.getDetail(), row.getOccurredAt()));
    }

    public record AuditEntry(UUID id, String actor, String action, String targetType,
                             String targetId, String outcome, String detail, Instant occurredAt) {
    }
}
