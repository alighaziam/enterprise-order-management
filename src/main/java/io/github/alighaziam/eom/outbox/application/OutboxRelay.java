package io.github.alighaziam.eom.outbox.application;

import io.github.alighaziam.eom.outbox.infrastructure.OutboxClaimService;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OutboxRelay {
    private final OutboxClaimService claimService;
    private final OutboxRepository repository;
    private final ApplicationEventPublisher publisher;

    public OutboxRelay(OutboxClaimService claimService, OutboxRepository repository,
                       ApplicationEventPublisher publisher) {
        this.claimService = claimService;
        this.repository = repository;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        for (OutboxEntity row : claimService.claimBatch(50)) {
            boolean published = false;
            try {
                publisher.publishEvent(row.toEvent());
                published = true;
            } catch (RuntimeException exception) {
                row.failed(exception.getMessage(), Instant.now());
            }

            if (published) {
                row.published(Instant.now());
            }
            repository.saveAndFlush(row);
        }
    }
}
