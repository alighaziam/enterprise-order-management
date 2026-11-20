package io.github.alighaziam.eom.payment;

/** Read-only operational view of durable payment gateway commands. */
public interface PaymentOperationMonitoring {
    long count(PaymentOperationStatus status);

    /** Age in seconds of the oldest operation currently in the supplied status. */
    long oldestAgeSeconds(PaymentOperationStatus status);

    /** Number of operations whose worker lease has expired but has not been recovered yet. */
    long expiredLeaseCount();
}
