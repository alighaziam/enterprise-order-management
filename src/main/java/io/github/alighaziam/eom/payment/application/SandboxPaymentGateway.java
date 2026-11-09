package io.github.alighaziam.eom.payment.application;
import java.math.BigDecimal; import java.util.UUID;
public interface SandboxPaymentGateway {String authorize(UUID paymentId,BigDecimal amount,String currency);void capture(String reference);void voidAuthorization(String reference);void refund(String reference);}