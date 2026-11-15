package io.github.alighaziam.eom.checkout.application;

import io.github.alighaziam.eom.shipping.ShippingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CheckoutRecoveryWorker {

    private final CheckoutFulfillmentClaimService claims;
    private final CheckoutFulfillmentStateService states;
    private final ShippingOperations shipping;

    public CheckoutRecoveryWorker(
            CheckoutFulfillmentClaimService claims,
            CheckoutFulfillmentStateService states,
            ShippingOperations shipping) {
        this.claims = claims;
        this.states = states;
        this.shipping = shipping;
    }

    @Scheduled(fixedDelayString = "${app.checkout.recovery-poll-ms:5000}")
    public void retryPendingFulfillment() {
        Instant now = Instant.now();
        var claimed = claims.claimBatch(now, 20);
        for (var workflow : claimed) {
            try {
                var shipment = shipping.create(workflow.orderId(), workflow.deliveryAddress());
                states.complete(workflow.workflowId(), workflow.workerId(), shipment.id(), Instant.now());
            } catch (RuntimeException exception) {
                states.retry(workflow.workflowId(), workflow.workerId(), exception.getMessage(), Instant.now());
            }
        }
    }
}
