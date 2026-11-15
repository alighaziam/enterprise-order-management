package io.github.alighaziam.eom.outbox.application;

import io.github.alighaziam.eom.outbox.OutboxEventPublisher;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxClaimService;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OutboxRelay {

    private static final int BATCH_SIZE = 10;

    private final OutboxClaimService claimService;
    private final OutboxRepository repository;
    private final OutboxEventPublisher publisher;

    public OutboxRelay(OutboxClaimService claimService,
                       OutboxRepository repository,
                       OutboxEventPublisher publisher) {
        this.claimService = claimService;
        this.repository = repository;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPending() {
        for (OutboxEntity row : claimService.claimBatch(BATCH_SIZE)) {
            try {
                publisher.publish(row.toEvent());
                row.published(Instant.now());
            } catch (RuntimeException exception) {
                row.failed(exception.getMessage(), Instant.now());
            }
            repository.saveAndFlush(row);
        }
    }
}
