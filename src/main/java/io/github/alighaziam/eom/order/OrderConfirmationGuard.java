package io.github.alighaziam.eom.order;

import java.util.UUID;

/**
 * Port for validating cross-module payment requirements before stock is committed.
 */
public interface OrderConfirmationGuard {
    void assertCanConfirm(UUID orderId);
}
