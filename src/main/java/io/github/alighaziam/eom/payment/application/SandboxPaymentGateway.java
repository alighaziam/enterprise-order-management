package io.github.alighaziam.eom.payment.application;

/**
 * Backward-compatible name for the deterministic sandbox implementation.
 * New application code should depend on {@link PaymentGateway}.
 */
@Deprecated(forRemoval = false)
public interface SandboxPaymentGateway extends PaymentGateway {
}
