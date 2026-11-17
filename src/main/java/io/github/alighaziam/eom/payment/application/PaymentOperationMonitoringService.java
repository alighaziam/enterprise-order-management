package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationMonitoring;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOperationMonitoringService implements PaymentOperationMonitoring {

    private final PaymentGatewayOperationRepository operations;

    public PaymentOperationMonitoringService(PaymentGatewayOperationRepository operations) {
        this.operations = operations;
    }

    @Override
    @Transactional(readOnly = true)
    public long count(PaymentOperationStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Payment operation status is required.");
        }
        return operations.countByStatus(status);
    }
}
