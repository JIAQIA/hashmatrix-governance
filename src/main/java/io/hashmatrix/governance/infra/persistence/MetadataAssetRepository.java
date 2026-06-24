package io.hashmatrix.governance.infra.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 元数据资产持久化（PostgreSQL，schema 由 Flyway 管理）。
 *
 * <p>所有读路径以租户为第一约束（D9）：查询一律带 {@code tenantId}，绝不返回跨租户资产。
 * 供 #28 {@code PostgresMetadataAdapter}（search 真查）与 #29 写端点消费。
 */
public interface MetadataAssetRepository extends JpaRepository<MetadataAssetEntity, UUID> {

    /** 按租户列出全部资产（创建时间倒序），租户隔离边界内的检索基础。 */
    List<MetadataAssetEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /** 按 id + 租户取单条：确保跨租户不可越权读到他人资产（D9）。 */
    Optional<MetadataAssetEntity> findByIdAndTenantId(UUID id, String tenantId);

    /** 登记去重：当前租户下是否已存在该业务 code（业务唯一键 {@code (tenant_id, code)}）。 */
    boolean existsByTenantIdAndCode(String tenantId, String code);

    /** 编辑去重：当前租户下、**排除自身 id** 后是否已有该 code 占用（避免改 code 撞他人）。 */
    boolean existsByTenantIdAndCodeAndIdNot(String tenantId, String code, UUID id);
}
