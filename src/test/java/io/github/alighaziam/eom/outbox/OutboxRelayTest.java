package io.github.alighaziam.eom.outbox;

import io.github.alighaziam.eom.outbox.application.OutboxRelay;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxClaimService;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    @Test
    void recordsFailureAndPersistsRetryStateWhenPublisherThrows() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxRepository repository = mock(OutboxRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Instant created = Instant.now();
        OutboxEntity event = new OutboxEntity(UUID.randomUUID(), "OrderPlaced", 1, created,
                "Order", "order-1", 1, "correlation-1", null, "{}", created);

        when(claimService.claimBatch(50)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publishEvent(any(IntegrationEvent.class));

        new OutboxRelay(claimService, repository, publisher).publishPending();

        assertEquals(1, event.getAttempts());
        assertNotNull(event.getNextAttemptAt());
        assertNull(event.getPublishedAt());
        verify(repository).saveAndFlush(event);
    }

    @Test
    void marksEventPublishedAfterSuccessfulDispatch() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxRepository repository = mock(OutboxRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        Instant created = Instant.now();
        OutboxEntity event = new OutboxEntity(UUID.randomUUID(), "OrderPlaced", 1, created,
                "Order", "order-1", 1, "correlation-1", null, "{}", created);

        when(claimService.claimBatch(50)).thenReturn(List.of(event));

        new OutboxRelay(claimService, repository, publisher).publishPending();

        assertNotNull(event.getPublishedAt());
        verify(repository).saveAndFlush(event);
    }
}
