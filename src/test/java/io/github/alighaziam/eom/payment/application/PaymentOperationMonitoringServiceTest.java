package io.github.alighaziam.eom.payment.application;

import io.github.alighaziam.eom.payment.PaymentOperationMonitoring;
import io.github.alighaziam.eom.payment.PaymentOperationStatus;
import io.github.alighaziam.eom.payment.infrastructure.PaymentGatewayOperationRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentOperationMonitoringServiceTest {

    @Test
    void returnsPersistedCountForRequestedStatus() {
        PaymentGatewayOperationRepository repository = mock(PaymentGatewayOperationRepository.class);
        when(repository.countByStatus(PaymentOperationStatus.UNKNOWN)).thenReturn(3L);

        PaymentOperationMonitoringService service = new PaymentOperationMonitoringService(repository);

        assertEquals(3L, service.count(PaymentOperationStatus.UNKNOWN));
        verify(repository).countByStatus(PaymentOperationStatus.UNKNOWN);
    }

    @Test
    void rejectsMissingStatusWithoutQueryingRepository() {
        PaymentGatewayOperationRepository repository = mock(PaymentGatewayOperationRepository.class);
        PaymentOperationMonitoringService service = new PaymentOperationMonitoringService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.count(null));
        verifyNoInteractions(repository);
    }
}
