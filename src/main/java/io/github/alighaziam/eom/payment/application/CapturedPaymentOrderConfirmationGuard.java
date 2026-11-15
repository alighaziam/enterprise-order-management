package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.order.OrderConfirmationGuard;
import io.github.alighaziam.eom.payment.PaymentStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Keeps order confirmation behind successful payment capture without making
 * the order module depend on payment persistence or implementation classes.
 */
@Component
public class CapturedPaymentOrderConfirmationGuard implements OrderConfirmationGuard {

    private final PaymentRepository payments;

    public CapturedPaymentOrderConfirmationGuard(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    public void assertCanConfirm(UUID orderId) {
        if (!payments.existsByOrderIdAndStatusIn(orderId, List.of(PaymentStatus.CAPTURED))) {
            throw new IllegalStateException("An order can only be confirmed after its payment is captured.");
        }
    }
}
