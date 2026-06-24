package io.hashmatrix.governance.infra;

import io.hashmatrix.governance.domain.metadata.AssetCatalogSearch;
import io.hashmatrix.governance.domain.metadata.AssetSummary;
import io.hashmatrix.governance.domain.metadata.AssetUpsertRequest;
import io.hashmatrix.governance.domain.metadata.MetaSearchResult;
import io.hashmatrix.governance.domain.metadata.SearchQuery;
import io.hashmatrix.governance.domain.port.MetadataCatalogPort;
import io.hashmatrix.governance.infra.persistence.MetadataAssetEntity;
import io.hashmatrix.governance.infra.persistence.MetadataAssetRepository;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import io.hashmatrix.starter.web.BusinessException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public AssetSummary register(AssetUpsertRequest request) {
        String tenant = currentTenantOrReject(); // fail-closed：缺租户头即拒绝（D9/D2）
        if (request.code() != null && repository.existsByTenantIdAndCode(tenant, request.code())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "ASSET_CODE_CONFLICT",
                    "asset code already exists in tenant: " + request.code());
        }
        MetadataAssetEntity saved =
                repository.save(
                        MetadataAssetEntity.register(
                                tenant, request.name(), request.type(), request.owner(),
                                request.code(), request.tags(), request.attributes()));
        log.debug("meta asset registered tenant={} id={}", tenant, saved.getId());
        return toSummary(saved);
    }

    @Override
    @Transactional
    public AssetSummary update(String id, AssetUpsertRequest request) {
        String tenant = currentTenantOrReject(); // fail-closed（D9/D2）
        UUID assetId = parseId(id);
        // 限本租户取数：非本租户 / 不存在 → 404（不越权改他租户、不泄漏存在性）。
        MetadataAssetEntity entity =
                repository
                        .findByIdAndTenantId(assetId, tenant)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND",
                                                "asset not found: " + id));
        if (request.code() != null
                && repository.existsByTenantIdAndCodeAndIdNot(tenant, request.code(), assetId)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "ASSET_CODE_CONFLICT",
                    "asset code already exists in tenant: " + request.code());
        }
        entity.update(
                request.name(), request.type(), request.owner(),
                request.code(), request.tags(), request.attributes());
        log.debug("meta asset updated tenant={} id={}", tenant, assetId);
        return toSummary(repository.save(entity));
    }

    /**
     * 写路径取当前租户，缺失即 fail-closed 拒绝（D9/D2）。返回 **400**（客户端/网关未注入 {@code X-Tenant-Id}，
     * 属调用方错误），而非 {@code requireTenantId()} 的 {@code IllegalStateException}→500——避免把客户端错误
     * 记成服务端错误。生产中租户头由网关强制注入（require_tenant=true），此为应用层兜底。
     */
    private static String currentTenantOrReject() {
        return TenantContextHolder.getTenantId()
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        HttpStatus.BAD_REQUEST, "TENANT_REQUIRED",
                                        "X-Tenant-Id is required for write operations"));
    }

    /** 路径 id 解析：非法 UUID 视为不存在（404），不泄漏内部细节、不抛 500。 */
    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "asset not found: " + id);
        }
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
