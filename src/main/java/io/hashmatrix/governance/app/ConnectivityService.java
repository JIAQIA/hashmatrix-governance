package io.hashmatrix.governance.app;

import io.hashmatrix.governance.domain.InfraStatus;
import io.hashmatrix.governance.domain.port.InfraConnectivityPort;
import io.hashmatrix.starter.audit.AuditEvent;
import io.hashmatrix.starter.audit.AuditRecorder;
import org.springframework.stereotype.Service;

/**
 * 连通自检应用服务：在当前租户上下文内探测基础设施，并复用 {@code starter-audit} 记审计。
 *
 * <p>审计事件由 {@link AuditEvent#of} 自动加盖当前租户（{@code starter-tenant}），跨租户绝不串。
 */
@Service
public class ConnectivityService {

    private final InfraConnectivityPort connectivity;
    private final AuditRecorder auditRecorder;

    public ConnectivityService(InfraConnectivityPort connectivity, AuditRecorder auditRecorder) {
        this.connectivity = connectivity;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 探测基础设施连通并记录审计。
     *
     * @return 连通快照
     */
    public InfraStatus probe() {
        InfraStatus status = connectivity.probe();
        auditRecorder.record(
                AuditEvent.of(
                        "system",
                        "INFRA_PROBE",
                        status.tenantSchema(),
                        status.healthy() ? AuditEvent.Outcome.SUCCESS : AuditEvent.Outcome.FAILURE,
                        null));
        return status;
    }
}
