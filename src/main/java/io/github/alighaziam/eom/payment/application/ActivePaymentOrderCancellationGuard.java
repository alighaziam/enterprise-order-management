package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.order.OrderCancellationGuard;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Prevents the order module from releasing reserved stock while an active
 * payment intent still exists. The order module depends only on its own port.
 */
@Component
public class ActivePaymentOrderCancellationGuard implements OrderCancellationGuard {

    private final PaymentRepository payments;

    public ActivePaymentOrderCancellationGuard(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public void assertCanCancel(UUID orderId) {
        if (payments.findActiveByOrderIdForUpdate(orderId, List.of(
                PaymentStatus.REQUESTED,
                PaymentStatus.AUTHORIZED,
                PaymentStatus.CAPTURED,
                PaymentStatus.REFUND_PENDING)).isPresent()) {
            throw new IllegalStateException(
                    "An order with an active payment cannot be cancelled. Void or refund the payment first.");
        }
    }
}
