package io.hashmatrix.governance.infra;

import io.hashmatrix.governance.domain.metadata.AssetCatalogSearch;
import io.hashmatrix.governance.domain.metadata.AssetSummary;
import io.hashmatrix.governance.domain.metadata.MetaSearchResult;
import io.hashmatrix.governance.domain.metadata.SearchQuery;
import io.hashmatrix.governance.domain.port.MetadataCatalogPort;
import io.hashmatrix.governance.infra.persistence.MetadataAssetEntity;
import io.hashmatrix.governance.infra.persistence.MetadataAssetRepository;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link MetadataCatalogPort} 的 **PostgreSQL 适配器**（M2 #28，默认装配）：在**当前租户隔离边界内**
 * 真查 PG（{@link MetadataAssetRepository}），把落库实体映射为契约 {@link AssetSummary} 后，
 * 复用 {@link AssetCatalogSearch} 施加关键字/类型/分面/分页（与 mock 同一检索语义，DRY）。
 *
 * <p><b>D9 租户隔离</b>：仅取当前 {@code X-Tenant-Id} 的资产；**缺租户头则返回空**——读路径不抛 4xx
 * （契约 {@code search} 仅声明 200、tolerant），且绝不跨租户泄漏。租户上下文由网关注入、{@code starter-tenant} 解析。
 *
 * <p>替换原 {@code MockMetadataCatalogAdapter}（已降级为 {@code mock} profile）；{@code api}/{@code app}
 * 层不动（端口隔离，骨架不返工）。M2 走行级 {@code tenant_id} 隔离档（主仓 Disc #58 / arch 05 D9）。
 */
@Component
public class PostgresMetadataAdapter implements MetadataCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(PostgresMetadataAdapter.class);

    private final MetadataAssetRepository repository;

    public PostgresMetadataAdapter(MetadataAssetRepository repository) {
        this.repository = repository;
    }

    @Override
    public MetaSearchResult search(SearchQuery query) {
        Optional<String> tenant = TenantContextHolder.getTenantId();
        if (tenant.isEmpty()) {
            // 缺租户头：不泄漏任何租户数据，返回空集（契约 200，不引入未声明 4xx）。
            log.debug("meta search (pg) without tenant context -> empty result");
            return AssetCatalogSearch.search(List.of(), query);
        }

        List<AssetSummary> assets =
                repository.findByTenantIdOrderByCreatedAtDesc(tenant.get()).stream()
                        .map(PostgresMetadataAdapter::toSummary)
                        .toList();
        log.debug("meta search (pg) tenant={} candidates={} q={} type={}",
                tenant.get(), assets.size(), query.q(), query.type());
        return AssetCatalogSearch.search(assets, query);
    }

    /** 落库实体 → 契约检索摘要（id 以 UUID 串呈现，对齐 {@code AssetSummary.id}）。 */
    private static AssetSummary toSummary(MetadataAssetEntity entity) {
        return new AssetSummary(
                entity.getId().toString(),
                entity.getName(),
                entity.getType(),
                entity.getOwner(),
                entity.getTags());
    }
}
