package io.hashmatrix.governance.domain.metadata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 资产登记/编辑的**写入面请求**，对齐契约 {@code governance-metadata-v1} v1.1.0 的 {@code AssetUpsertRequest}。
 *
 * <p>读写 DTO 分离（主仓 Disc #53）：本记录**仅含可写字段**，不复用读侧 {@link AssetSummary}。
 * {@code id} 不在此处——登记时服务端生成 UUID、编辑时由路径携带；{@code tenant_id} 不在此处——由租户上下文恒定（D9）。
 *
 * @param name       资产名（必填）
 * @param type       资产类型（必填）
 * @param owner      归属（可空）
 * @param code       业务编码（可空）；与租户构成业务唯一键 {@code (tenant_id, code)} 做登记去重，省略则不去重
 * @param tags       标签集合（{@code null} 归一化为空列表）
 * @param attributes 可扩展属性（JSONB；{@code null} 归一化为空 map）——预留 #1 元模型引擎 typedef 驱动属性（R2）
 */
public record AssetUpsertRequest(
        @NotBlank String name,
        @NotNull AssetType type,
        String owner,
        String code,
        List<String> tags,
        Map<String, Object> attributes) {

    public AssetUpsertRequest {
        tags = (tags == null) ? List.of() : List.copyOf(tags);
        attributes = (attributes == null) ? Map.of() : Map.copyOf(attributes);
        code = (code == null || code.isBlank()) ? null : code.trim();
    }
}
