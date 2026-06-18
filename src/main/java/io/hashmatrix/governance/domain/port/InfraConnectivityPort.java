package io.hashmatrix.governance.domain.port;

import io.hashmatrix.governance.domain.InfraStatus;

/**
 * 基础设施连通端口（领域出站端口，六边形架构）：屏蔽 PostgreSQL / Elasticsearch 客户端细节。
 *
 * <p>实现位于 {@code infra} 层；{@code app} 层经本端口编排自检，不直接耦合具体客户端。
 */
public interface InfraConnectivityPort {

    /**
     * 在当前租户隔离边界内探测基础设施连通性。
     *
     * @return 连通快照
     */
    InfraStatus probe();
}
