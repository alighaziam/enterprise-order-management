package io.github.alighaziam.eom.payment.application;
import org.springframework.stereotype.Component; import java.math.BigDecimal; import java.util.UUID;
@Component public class DeterministicSandboxPaymentGateway implements SandboxPaymentGateway {
 public String authorize(UUID id,BigDecimal amount,String currency){if(amount==null||amount.signum()<=0||currency==null||!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Invalid sandbox payment request.");return "sandbox-"+id;}
 public void capture(String r){check(r);} public void voidAuthorization(String r){check(r);} public void refund(String r){check(r);}
 private void check(String r){if(r==null||!r.startsWith("sandbox-"))throw new IllegalArgumentException("Unknown sandbox payment reference.");}
}