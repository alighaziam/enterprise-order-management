package io.github.alighaziam.eom.order;

import java.util.UUID;

/**
 * Port for validating cross-module constraints before an order is cancelled.
 * Implementations live in the owning capability modules.
 */
public interface OrderCancellationGuard {
    void assertCanCancel(UUID orderId);
}
