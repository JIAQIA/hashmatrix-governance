package io.hashmatrix.governance.infra.persistence;

import io.hashmatrix.governance.domain.metadata.AssetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 元数据资产持久化实体（governance 首张业务表，M2 #27/WP1）。
 *
 * <p>JPA 实体属 infra 关注点，置于 {@code infra.persistence}，使 {@code domain} 维持纯领域记录
 * （{@link io.hashmatrix.governance.domain.metadata.AssetSummary} 等无持久化注解）；
 * 实体 ↔ 领域 DTO 的映射由 infra 适配器（#28 {@code PostgresMetadataAdapter}）承担。
 *
 * <p>多租户：行级 {@code tenant_id} 隔离（主仓 Disc #58 / D9）——写落当前租户、读强制过滤。
 * {@code tags}/{@code attributes} 以 JSONB 落库（{@link SqlTypes#JSON}），承接 R2 可扩展。
 */
@Entity
@Table(name = "metadata_asset")
public class MetadataAssetEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 63)
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private AssetType type;

    @Column(name = "owner")
    private String owner;

    /** 业务编码；与 {@code tenant_id} 构成业务唯一键 {@code (tenant_id, code)} 做登记去重（可空，#29 / V2）。 */
    @Column(name = "code", length = 255)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA 用。 */
    protected MetadataAssetEntity() {}

    private MetadataAssetEntity(
            UUID id,
            String tenantId,
            String name,
            AssetType type,
            String owner,
            String code,
            List<String> tags,
            Map<String, Object> attributes) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.type = type;
        this.owner = owner;
        this.code = code;
        this.tags = (tags == null) ? new ArrayList<>() : new ArrayList<>(tags);
        this.attributes = (attributes == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }

    /**
     * 登记一条新资产（id 服务端生成 UUID，对齐主仓 Disc #53 写入面轮廓）。
     *
     * @param tenantId   归属租户（当前租户，非空）
     * @param name       资产名
     * @param type       资产类型
     * @param owner      归属（可空）
     * @param code       业务编码（可空，去重键）
     * @param tags       标签集合（{@code null} 归一化为空）
     * @param attributes 可扩展属性（{@code null} 归一化为空）
     */
    public static MetadataAssetEntity register(
            String tenantId,
            String name,
            AssetType type,
            String owner,
            String code,
            List<String> tags,
            Map<String, Object> attributes) {
        return new MetadataAssetEntity(
                UUID.randomUUID(), tenantId, name, type, owner, code, tags, attributes);
    }

    /** 无业务编码的登记便捷重载（{@code code=null}，不参与去重）。 */
    public static MetadataAssetEntity register(
            String tenantId,
            String name,
            AssetType type,
            String owner,
            List<String> tags,
            Map<String, Object> attributes) {
        return register(tenantId, name, type, owner, null, tags, attributes);
    }

    /**
     * 全量编辑可写字段（{@code id} / {@code tenant_id} 不可改，保隔离与幂等）。
     *
     * @param name       资产名
     * @param type       资产类型
     * @param owner      归属（可空）
     * @param code       业务编码（可空）
     * @param tags       标签集合（{@code null} 归一化为空）
     * @param attributes 可扩展属性（{@code null} 归一化为空）
     */
    public void update(
            String name,
            AssetType type,
            String owner,
            String code,
            List<String> tags,
            Map<String, Object> attributes) {
        this.name = name;
        this.type = type;
        this.owner = owner;
        this.code = code;
        this.tags = (tags == null) ? new ArrayList<>() : new ArrayList<>(tags);
        this.attributes = (attributes == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public AssetType getType() {
        return type;
    }

    public String getOwner() {
        return owner;
    }

    public String getCode() {
        return code;
    }

    public List<String> getTags() {
        return tags;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
