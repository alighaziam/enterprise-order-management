package io.github.alighaziam.eom.payment.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "sandbox", matchIfMissing = true)
public class DeterministicSandboxPaymentGateway implements SandboxPaymentGateway {

    @Override
    public String authorize(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {
        if (paymentId == null || amount == null || amount.signum() <= 0
                || currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid sandbox payment request.");
        }
        validateIdempotencyKey(idempotencyKey);
        return "sandbox-" + paymentId;
    }

    @Override
    public void capture(String reference, String idempotencyKey) {
        check(reference);
        validateIdempotencyKey(idempotencyKey);
    }

    @Override
    public void voidAuthorization(String reference, String idempotencyKey) {
        check(reference);
        validateIdempotencyKey(idempotencyKey);
    }

    @Override
    public void refund(String reference, String idempotencyKey) {
        check(reference);
        validateIdempotencyKey(idempotencyKey);
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw new IllegalArgumentException("A stable payment operation idempotency key is required.");
        }
    }

    private static void check(String reference) {
        if (reference == null || !reference.startsWith("sandbox-")) {
            throw new IllegalArgumentException("Unknown sandbox payment reference.");
        }
    }
}
