package io.hashmatrix.governance.domain;

/**
 * 基础设施连通快照：本仓工程基座对 PostgreSQL / Elasticsearch 的可达性自检结果。
 *
 * @param tenantSchema  当前租户隔离 schema（见 {@link TenantSchema}）
 * @param postgres      PostgreSQL 是否可达（含 JSONB 能力探针）
 * @param elasticsearch Elasticsearch 是否可达
 */
public record InfraStatus(String tenantSchema, boolean postgres, boolean elasticsearch) {

    /** 两路基础设施均可达。 */
    public boolean healthy() {
        return postgres && elasticsearch;
    }
}
