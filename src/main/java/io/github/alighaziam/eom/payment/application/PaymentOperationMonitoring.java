package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationStatus;

/**
 * Read-only operational view of durable payment gateway commands.
 */
public interface PaymentOperationMonitoring {
    long count(PaymentOperationStatus status);
}
