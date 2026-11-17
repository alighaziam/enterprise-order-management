package io.github.alighaziam.eom.operations.application;

import io.github.alighaziam.eom.operations.OperatorAuditService;
import io.github.alighaziam.eom.operations.infrastructure.OperatorActionEntity;
import io.github.alighaziam.eom.operations.infrastructure.OperatorActionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperatorAuditServiceTest {

    @Test
    void recordsSanitizedOperatorIdentityAndAction() {
        OperatorActionRepository repository = mock(OperatorActionRepository.class);
        OperatorAuditService service = new OperatorAuditService(repository);

        service.record("ops-user" + (char) 10 + "forged", "OUTBOX_REPLAY", "OutboxEvent",
                UUID.randomUUID().toString(), "SUCCEEDED", "Dead letter requeued.");

        var captor = org.mockito.ArgumentCaptor.forClass(OperatorActionEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals("ops-user forged", captor.getValue().getActor());
        assertEquals("OUTBOX_REPLAY", captor.getValue().getAction());
        assertEquals("SUCCEEDED", captor.getValue().getOutcome());
    }

    @Test
    void rejectsInvalidOutcomeBeforeQueryingRepository() {
        OperatorActionRepository repository = mock(OperatorActionRepository.class);
        OperatorAuditService service = new OperatorAuditService(repository);

        assertThrows(IllegalArgumentException.class,
                () -> service.search(null, "UNKNOWN", 0, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void capsPageSizeAndReturnsNewestAuditEntries() {
        OperatorActionRepository repository = mock(OperatorActionRepository.class);
        OperatorAuditService service = new OperatorAuditService(repository);
        when(repository.search(isNull(), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, -1, 500);

        verify(repository).search(isNull(), isNull(), argThat((Pageable page) ->
                page.getPageNumber() == 0 && page.getPageSize() == 100
                        && page.getSort().getOrderFor("occurredAt").getDirection() == Sort.Direction.DESC));
    }

    @Test
    void sanitizesControlCharactersAndRejectsOversizedAuditFields() {
        OperatorActionEntity sanitized = new OperatorActionEntity(
                UUID.randomUUID(), "user", "OUTBOX_REPLAY", "OutboxEvent", "id",
                "SUCCEEDED", "bad" + (char) 0 + "detail", Instant.now());
        assertEquals("bad detail", sanitized.getDetail());

        assertThrows(IllegalArgumentException.class, () -> new OperatorActionEntity(
                UUID.randomUUID(), "x".repeat(201), "OUTBOX_REPLAY", "OutboxEvent", "id",
                "SUCCEEDED", "ok", Instant.now()));
    }
}
