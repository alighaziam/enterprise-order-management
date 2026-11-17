package io.github.alighaziam.eom.payment.infrastructure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentGatewayOperationClaimService {

    private static final long LEASE_SECONDS = 120;

    private final PaymentGatewayOperationRepository repository;

    public PaymentGatewayOperationClaimService(PaymentGatewayOperationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<PaymentGatewayOperationEntity> claimPendingBatch(int batchSize, Instant now) {
        validateBatch(batchSize, now);
        String workerId = UUID.randomUUID().toString();
        repository.claimPendingBatch(workerId, now, now.plusSeconds(LEASE_SECONDS), batchSize);
        return repository.findByLockedByOrderByCreatedAtAsc(workerId);
    }

    @Transactional
    public List<PaymentGatewayOperationEntity> claimUnknownBatch(int batchSize, Instant now) {
        validateBatch(batchSize, now);
        String workerId = UUID.randomUUID().toString();
        repository.claimUnknownBatch(workerId, now, now.plusSeconds(LEASE_SECONDS), batchSize);
        return repository.findByLockedByOrderByCreatedAtAsc(workerId);
    }

    @Transactional
    public int recoverExpiredProcessing(Instant now) {
        if (now == null) throw new IllegalArgumentException("Recovery time is required.");
        return repository.moveExpiredProcessingToUnknown(now);
    }

    @Transactional
    public boolean markSucceeded(UUID operationId, String workerId, String reference, Instant now) {
        PaymentGatewayOperationEntity operation = ownedOperation(operationId, workerId, now);
        if (operation == null) return false;
        operation.succeeded(reference, workerId, now);
        repository.saveAndFlush(operation);
        return true;
    }

    @Transactional
    public boolean markFailed(UUID operationId, String workerId, String error, Instant now) {
        PaymentGatewayOperationEntity operation = ownedOperation(operationId, workerId, now);
        if (operation == null) return false;
        operation.failed(error, workerId, now);
        repository.saveAndFlush(operation);
        return true;
    }

    @Transactional
    public boolean markUnknown(UUID operationId, String workerId, String error, Instant now) {
        PaymentGatewayOperationEntity operation = ownedOperation(operationId, workerId, now);
        if (operation == null) return false;
        operation.unknown(error, workerId, now);
        repository.saveAndFlush(operation);
        return true;
    }

    private static void validateBatch(int batchSize, Instant now) {
        if (batchSize < 1 || batchSize > 100 || now == null) {
            throw new IllegalArgumentException("Batch size must be between 1 and 100 and time is required.");
        }
    }

    private PaymentGatewayOperationEntity ownedOperation(UUID id, String workerId, Instant now) {
        if (id == null || workerId == null || workerId.isBlank() || now == null) return null;
        PaymentGatewayOperationEntity operation = repository.findByIdForUpdate(id).orElse(null);
        return operation != null && operation.ownsLease(workerId, now) ? operation : null;
    }
}
