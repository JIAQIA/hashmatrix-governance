package io.hashmatrix.governance.domain.port;

import io.hashmatrix.governance.domain.metadata.AssetSummary;
import io.hashmatrix.governance.domain.metadata.AssetUpsertRequest;
import io.hashmatrix.governance.domain.metadata.MetaSearchResult;
import io.hashmatrix.governance.domain.metadata.SearchQuery;

/**
 * 元数据目录出站端口（六边形架构）：屏蔽元数据来源（M1 mock，后续 PG(JSONB)+ES）。
 *
 * <p>{@code app} 层经本端口检索/写入，不耦合具体来源。默认由 {@code infra} 的 PG 适配器实现（#28/#29）；
 * mock 适配器降级为 {@code mock} profile。所有读写均在**当前租户隔离边界**内（D9）。
 */
public interface MetadataCatalogPort {

    /**
     * 在当前租户隔离边界内检索元数据。
     *
     * @param query 检索条件
     * @return 分页 + 分面结果
     */
    MetaSearchResult search(SearchQuery query);

    /**
     * 在当前租户下登记一条资产（id 服务端生成）。
     *
     * @param request 写入面请求（仅可写字段）
     * @return 登记后的资产摘要（含服务端所发 id）
     */
    AssetSummary register(AssetUpsertRequest request);

    /**
     * 按 id 全量编辑当前租户的一条资产（幂等）。
     *
     * @param id      资产 id（路径携带）
     * @param request 写入面请求
     * @return 更新后的资产摘要
     */
    AssetSummary update(String id, AssetUpsertRequest request);
}
