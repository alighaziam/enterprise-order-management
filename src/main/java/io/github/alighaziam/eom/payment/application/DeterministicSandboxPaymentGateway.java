package io.github.alighaziam.eom.payment.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class DeterministicSandboxPaymentGateway implements SandboxPaymentGateway {

    @Override
    public String authorize(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {
        if (paymentId == null || amount == null || amount.signum() <= 0
                || currency == null || !currency.matches("[A-Z]{3}")
                || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) {
            throw new IllegalArgumentException("Invalid sandbox payment request.");
        }
        return "sandbox-" + paymentId;
    }

    @Override
    public void capture(String reference) {
        check(reference);
    }

    @Override
    public void voidAuthorization(String reference) {
        check(reference);
    }

    @Override
    public void refund(String reference) {
        check(reference);
    }

    private void check(String reference) {
        if (reference == null || !reference.startsWith("sandbox-")) {
            throw new IllegalArgumentException("Unknown sandbox payment reference.");
        }
    }
}
