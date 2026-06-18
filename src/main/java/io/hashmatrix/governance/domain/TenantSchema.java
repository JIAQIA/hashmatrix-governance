package io.hashmatrix.governance.domain;

/**
 * 租户数据隔离的 schema 名值对象（多租户 = schema-per-tenant，见架构 05 §5）。
 *
 * <p>由租户标识派生 {@code gov_<sanitized>}：仅保留 {@code [a-z0-9_]}、其余折叠为 {@code _}，
 * 确保可安全用于 {@code SET search_path}（杜绝注入）。治理资产以此为隔离边界，绝不跨租户串。
 *
 * @param name 归一化后的 schema 名，形如 {@code gov_acme}
 */
public record TenantSchema(String name) {

    /** schema 前缀：标识治理分系统的租户库。 */
    public static final String PREFIX = "gov_";

    public TenantSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("schema name must not be blank");
        }
    }

    /**
     * 由租户标识派生隔离 schema。
     *
     * @param tenantId 租户标识，非空白
     * @return 归一化 schema
     */
    public static TenantSchema forTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        String sanitized = tenantId.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return new TenantSchema(PREFIX + sanitized);
    }
}
