package io.github.alighaziam.eom.payment.application;

/**
 * Read-only provider lookup outcomes. NOT_FOUND is not automatically proof
 * that a financial command is safe to submit again.
 */
public enum ProviderOperationState {
    SUCCEEDED,
    FAILED,
    PENDING,
    NOT_FOUND,
    INDETERMINATE
}
