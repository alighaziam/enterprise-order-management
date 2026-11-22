package io.github.alighaziam.eom.operations;

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
        Sort stableNewestFirst = Sort.by(Sort.Direction.DESC, "occurredAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
        String normalizedQuery = query == null ? null : query.trim();
        if (normalizedQuery != null && normalizedQuery.length() > 200) {
            throw new IllegalArgumentException("Audit search query must not exceed 200 characters.");
        }
        if (normalizedQuery != null && normalizedQuery.isBlank()) {
            normalizedQuery = null;
        }
        return repository.search(normalizedQuery,
                        normalizedOutcome,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), stableNewestFirst))
                .map(row -> new AuditEntry(row.getId(), row.getActor(), row.getAction(),
                        row.getTargetType(), row.getTargetId(), row.getOutcome(),
                        row.getDetail(), row.getOccurredAt()));
    }

    public record AuditEntry(UUID id, String actor, String action, String targetType,
                             String targetId, String outcome, String detail, Instant occurredAt) {
    }
}
