package io.github.alighaziam.eom.outbox;

import io.github.alighaziam.eom.outbox.application.InboxService;
import io.github.alighaziam.eom.outbox.infrastructure.InboxRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InboxServiceTest {

    @Test
    void processesAnEventOnlyOncePerConsumer() {
        InboxRepository repository = mock(InboxRepository.class);
        UUID event = UUID.randomUUID();
        when(repository.insertIfAbsent(eq("orders:" + event), eq(event), eq("orders"), any()))
                .thenReturn(1, 0);

        InboxService service = new InboxService(repository);
        Runnable handler = mock(Runnable.class);

        assertTrue(service.processOnce(event, "orders", handler));
        assertFalse(service.processOnce(event, "orders", handler));

        verify(handler, times(1)).run();
        verify(repository, times(2)).insertIfAbsent(eq("orders:" + event), eq(event), eq("orders"), any());
        verifyNoMoreInteractions(handler);
    }

    @Test
    void scopesDeduplicationToTheConsumer() {
        InboxRepository repository = mock(InboxRepository.class);
        UUID event = UUID.randomUUID();
        when(repository.insertIfAbsent(anyString(), eq(event), anyString(), any())).thenReturn(1);

        InboxService service = new InboxService(repository);

        assertTrue(service.processOnce(event, "orders", () -> { }));
        assertTrue(service.processOnce(event, "analytics", () -> { }));

        verify(repository).insertIfAbsent(eq("orders:" + event), eq(event), eq("orders"), any());
        verify(repository).insertIfAbsent(eq("analytics:" + event), eq(event), eq("analytics"), any());
    }

    @Test
    void rejectsInvalidInputBeforeClaimingAnEvent() {
        InboxRepository repository = mock(InboxRepository.class);
        InboxService service = new InboxService(repository);

        assertThrows(IllegalArgumentException.class,
                () -> service.processOnce(null, "orders", () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> service.processOnce(UUID.randomUUID(), " ", () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> service.processOnce(UUID.randomUUID(), "orders", null));

        verifyNoInteractions(repository);
    }

    @Test
    void propagatesHandlerFailureSoTheInboxTransactionCanRollBack() {
        InboxRepository repository = mock(InboxRepository.class);
        UUID event = UUID.randomUUID();
        when(repository.insertIfAbsent(anyString(), eq(event), eq("orders"), any())).thenReturn(1);
        InboxService service = new InboxService(repository);
        Runnable handler = () -> { throw new IllegalStateException("consumer failed"); };

        assertThrows(IllegalStateException.class, () -> service.processOnce(event, "orders", handler));
        verify(repository).insertIfAbsent(eq("orders:" + event), eq(event), eq("orders"), any());
    }
}
