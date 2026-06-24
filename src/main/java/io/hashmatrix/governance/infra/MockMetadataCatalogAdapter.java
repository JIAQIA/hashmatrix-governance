package io.hashmatrix.governance.infra;

import io.hashmatrix.governance.domain.metadata.AssetCatalogSearch;
import io.hashmatrix.governance.domain.metadata.AssetSummary;
import io.hashmatrix.governance.domain.metadata.AssetType;
import io.hashmatrix.governance.domain.metadata.MetaSearchResult;
import io.hashmatrix.governance.domain.metadata.SearchQuery;
import io.hashmatrix.governance.domain.port.MetadataCatalogPort;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@link MetadataCatalogPort} 的 **脱敏 mock 适配器**：返回固定脱敏目录，贴合契约
 * {@code governance-metadata-v1} 的 {@code /api/meta/search}，供前端联调/离线演示消费。
 *
 * <p>对所有租户返回**同一脱敏目录**（不做租户隔离），仅适合无库环境联调。自 M2（#28）起，
 * 默认装配 {@code PostgresMetadataAdapter}（真查本租户 PG）；本 mock 降级为 {@code mock} profile，
 * **非默认**——仅在 {@code spring.profiles.active=mock} 时装配，避免与 PG 适配器构成注入歧义。
 *
 * <p>检索/分面/分页逻辑复用 {@link AssetCatalogSearch}（与 PG 适配器同一语义，DRY）。
 *
 * <p>🔴 红线：种子一律虚构脱敏（{@code asset-*} 标识、虚构表名、{@code data-team} 等占位），无任何真实信息。
 */
@Component
@Profile("mock")
public class MockMetadataCatalogAdapter implements MetadataCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(MockMetadataCatalogAdapter.class);

    /** 脱敏 mock 目录（虚构资产）。 */
    private static final List<AssetSummary> SEED =
            List.of(
                    new AssetSummary(
                            "asset-1001", "orders", AssetType.TABLE, "data-team",
                            List.of("finance", "core")),
                    new AssetSummary(
                            "asset-1002", "customers", AssetType.TABLE, "data-team",
                            List.of("crm")),
                    new AssetSummary(
                            "asset-1003", "order_items", AssetType.TABLE, "data-team",
                            List.of("finance")),
                    new AssetSummary(
                            "asset-1004", "daily_revenue", AssetType.VIEW, "bi-team",
                            List.of("finance", "report")),
                    new AssetSummary(
                            "asset-1005", "customer_360", AssetType.DATASET, "bi-team",
                            List.of("crm", "report")),
                    new AssetSummary(
                            "asset-1006", "orders_api", AssetType.API, "platform-team",
                            List.of("serving")),
                    new AssetSummary(
                            "asset-1007", "customer_email", AssetType.COLUMN, "data-team",
                            List.of("pii")));

    @Override
    public MetaSearchResult search(SearchQuery query) {
        String tenant = TenantContextHolder.getTenantId().orElse("public");
        log.debug("meta search (mock) tenant={} q={} type={}", tenant, query.q(), query.type());
        // mock 不做租户隔离：始终在同一脱敏种子集上检索（检索语义复用共享组件）。
        return AssetCatalogSearch.search(SEED, query);
    }
}
