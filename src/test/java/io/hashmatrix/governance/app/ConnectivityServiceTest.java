package io.hashmatrix.governance.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hashmatrix.governance.domain.InfraStatus;
import io.hashmatrix.governance.domain.port.InfraConnectivityPort;
import io.hashmatrix.starter.audit.AuditEvent;
import io.hashmatrix.starter.audit.AuditRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectivityServiceTest {

    @Mock private InfraConnectivityPort connectivity;
    @Mock private AuditRecorder auditRecorder;

    @Test
    void returnsProbeAndAuditsSuccessWhenHealthy() {
        when(connectivity.probe()).thenReturn(new InfraStatus("gov_acme", true, true));
        ConnectivityService service = new ConnectivityService(connectivity, auditRecorder);

        InfraStatus status = service.probe();

        assertThat(status.healthy()).isTrue();
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("INFRA_PROBE");
        assertThat(captor.getValue().target()).isEqualTo("gov_acme");
        assertThat(captor.getValue().outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
    }

    @Test
    void auditsFailureWhenAnyInfraDown() {
        when(connectivity.probe()).thenReturn(new InfraStatus("gov_acme", true, false));
        ConnectivityService service = new ConnectivityService(connectivity, auditRecorder);

        service.probe();

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditRecorder).record(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(AuditEvent.Outcome.FAILURE);
    }

    @Test
    void recordsExactlyOneAuditPerProbe() {
        when(connectivity.probe()).thenReturn(new InfraStatus("gov_acme", true, true));
        ConnectivityService service = new ConnectivityService(connectivity, auditRecorder);

        service.probe();

        verify(auditRecorder).record(any(AuditEvent.class));
    }
}
