package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationMonitoring;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class PaymentOperationMonitoringService implements PaymentOperationMonitoring {

    private static final List<PaymentOperationStatus> LEASED_STATUSES =
            List.of(PaymentOperationStatus.PROCESSING, PaymentOperationStatus.RECONCILING);

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

    @Override
    @Transactional(readOnly = true)
    public long expiredLeaseCount() {
        return operations.countByStatusInAndLockedUntilLessThanEqual(LEASED_STATUSES, Instant.now());
    }

    private static void requireStatus(PaymentOperationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Payment operation status is required.");
        }
    }
}
