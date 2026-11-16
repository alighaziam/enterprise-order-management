package io.github.alighaziam.eom.outbox;

import io.github.alighaziam.eom.outbox.application.OutboxRelay;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxClaimService;
import io.github.alighaziam.eom.outbox.infrastructure.OutboxEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    @Test
    void recordsFailureThroughTheLeaseOwnerAfterPublisherThrows() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
        Instant created = Instant.now();
        OutboxEntity event = new OutboxEntity(UUID.randomUUID(), "OrderPlaced", 1, created,
                "Order", "order-1", 1, "correlation-1", null, "{}", created);

        when(claimService.claimBatch(10)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publish(any(IntegrationEvent.class));

        new OutboxRelay(claimService, publisher).publishPending();

        verify(claimService).markFailed(eq(event.getEventId()), isNull(),
                eq("broker unavailable"), any(Instant.class));
    }

    @Test
    void acknowledgesEventOnlyThroughItsClaimLease() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
        Instant created = Instant.now();
        OutboxEntity event = new OutboxEntity(UUID.randomUUID(), "OrderPlaced", 1, created,
                "Order", "order-1", 1, "correlation-1", null, "{}", created);

        when(claimService.claimBatch(10)).thenReturn(List.of(event));

        new OutboxRelay(claimService, publisher).publishPending();

        verify(publisher).publish(any(IntegrationEvent.class));
        verify(claimService).markPublished(eq(event.getEventId()), isNull(), any(Instant.class));
        verify(claimService, never()).markFailed(any(), any(), any(), any());
    }
}
