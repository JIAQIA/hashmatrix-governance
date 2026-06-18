package io.hashmatrix.governance.api;

import io.hashmatrix.governance.app.ConnectivityService;
import io.hashmatrix.governance.domain.InfraStatus;
import io.hashmatrix.starter.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础设施自检 API：演示分层骨架与公共能力复用——
 * starter-tenant 透传租户 → starter-web 统一返回 → app/infra 探测 PG/ES → starter-audit 记审计。
 *
 * <p>引擎业务 API（元模型 typedef/实例/检索）在 #1，本基座只暴露连通自检。
 */
@RestController
@RequestMapping("/api/governance")
public class ProbeController {

    private final ConnectivityService connectivityService;

    public ProbeController(ConnectivityService connectivityService) {
        this.connectivityService = connectivityService;
    }

    /** 在当前租户隔离边界内自检 PG/ES 连通。 */
    @GetMapping("/probe")
    public ApiResponse<InfraStatus> probe() {
        return ApiResponse.ok(connectivityService.probe());
    }
}
