package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationMonitoring;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class PaymentOperationMonitoringService implements PaymentOperationMonitoring {

    private final PaymentGatewayOperationRepository operations;

    public PaymentOperationMonitoringService(PaymentGatewayOperationRepository operations) {
        this.operations = operations;
    }

    @Override
    @Transactional(readOnly = true)
    public long count(PaymentOperationStatus status) {
        requireStatus(status);
        return operations.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public long oldestAgeSeconds(PaymentOperationStatus status) {
        requireStatus(status);
        return operations.findOldestUpdatedAtByStatus(status)
                .map(updatedAt -> Math.max(0L, Duration.between(updatedAt, Instant.now()).getSeconds()))
                .orElse(0L);
    }

    private static void requireStatus(PaymentOperationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Payment operation status is required.");
        }
    }
}
