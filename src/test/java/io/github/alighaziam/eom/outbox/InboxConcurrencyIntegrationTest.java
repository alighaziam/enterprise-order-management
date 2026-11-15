package io.github.alighaziam.eom.outbox;

import io.github.alighaziam.eom.outbox.application.InboxService;
import io.github.alighaziam.eom.outbox.infrastructure.InboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InboxConcurrencyIntegrationTest {

    @Autowired
    private InboxService inbox;

    @Autowired
    private InboxRepository repository;

    @Test
    void concurrentDeliveriesInvokeTheConsumerOnlyOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> deliver(eventId, executions, ready, start));
            var second = executor.submit(() -> deliver(eventId, executions, ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both deliveries should be ready");
            start.countDown();

            boolean firstProcessed = first.get(15, TimeUnit.SECONDS);
            boolean secondProcessed = second.get(15, TimeUnit.SECONDS);

            assertNotEquals(firstProcessed, secondProcessed,
                    "Exactly one concurrent delivery should claim the inbox key");
            assertEquals(1, executions.get());
            assertTrue(repository.existsById("orders:" + eventId));
        }
    }

    @Test
    void failedConsumerRollsBackItsInboxClaim() {
        UUID eventId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () ->
                inbox.processOnce(eventId, "orders", () -> {
                    throw new IllegalStateException("simulated consumer failure");
                }));

        assertFalse(repository.existsById("orders:" + eventId),
                "A failed handler must not leave a processed marker behind");
    }

    private boolean deliver(UUID eventId, AtomicInteger executions,
                            CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start", exception);
        }
        return inbox.processOnce(eventId, "orders", executions::incrementAndGet);
    }
}
