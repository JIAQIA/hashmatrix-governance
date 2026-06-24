package io.hashmatrix.governance.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.hashmatrix.governance.domain.metadata.AssetSummary;
import io.hashmatrix.governance.domain.metadata.AssetType;
import io.hashmatrix.governance.domain.metadata.MetaSearchResult;
import io.hashmatrix.governance.domain.metadata.SearchQuery;
import io.hashmatrix.governance.infra.persistence.MetadataAssetEntity;
import io.hashmatrix.governance.infra.persistence.MetadataAssetRepository;
import io.hashmatrix.starter.tenant.TenantContext;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import io.hashmatrix.test.fixtures.MockTenants;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PostgresMetadataAdapter} 单元测试（mock 仓储，无库）：验证 D9 租户隔离取数（只查当前租户）、
 * 实体→{@link AssetSummary} 映射，以及缺租户头时返回空且不触库。检索/分面/分页语义由
 * {@code AssetCatalogSearch}（经 {@code MockMetadataCatalogAdapterTest}）守护，此处只验适配器职责。
 *
 * <p>真实落库的按租户过滤端到端另由 {@code MetadataAssetPersistenceIT}（Testcontainers）守护。
 */
@ExtendWith(MockitoExtension.class)
class PostgresMetadataAdapterTest {

    @Mock private MetadataAssetRepository repository;

    @Test
    void queriesOnlyCurrentTenantAndMapsToSummary() {
        MetadataAssetEntity orders =
                MetadataAssetEntity.register(
                        MockTenants.ACME, "orders", AssetType.TABLE, "data-team",
                        List.of("finance", "core"), Map.of());
        MetadataAssetEntity customers =
                MetadataAssetEntity.register(
                        MockTenants.ACME, "customers", AssetType.TABLE, "data-team",
                        List.of("crm"), Map.of());
        when(repository.findByTenantIdOrderByCreatedAtDesc(MockTenants.ACME))
                .thenReturn(List.of(orders, customers));

        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);
        MetaSearchResult result =
                TenantContextHolder.callWith(
                        TenantContext.of(MockTenants.ACME),
                        () -> adapter.search(SearchQuery.of(null, null, 1, 20)));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items())
                .extracting(AssetSummary::name)
                .containsExactlyInAnyOrder("orders", "customers");
        assertThat(result.items()).extracting(AssetSummary::id).doesNotContainNull();
        // 仅按当前租户取数（D9）：绝不查其它租户。
        verify(repository).findByTenantIdOrderByCreatedAtDesc(MockTenants.ACME);
        verify(repository, never()).findAll();
    }

    @Test
    void appliesKeywordFilterWithinTenant() {
        when(repository.findByTenantIdOrderByCreatedAtDesc(MockTenants.ACME))
                .thenReturn(
                        List.of(
                                MetadataAssetEntity.register(
                                        MockTenants.ACME, "orders", AssetType.TABLE, "data-team",
                                        List.of("finance"), Map.of()),
                                MetadataAssetEntity.register(
                                        MockTenants.ACME, "customers", AssetType.TABLE, "data-team",
                                        List.of("crm"), Map.of())));

        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);
        MetaSearchResult result =
                TenantContextHolder.callWith(
                        TenantContext.of(MockTenants.ACME),
                        () -> adapter.search(SearchQuery.of("order", null, 1, 20)));

        assertThat(result.items()).extracting(AssetSummary::name).containsExactly("orders");
    }

    @Test
    void returnsEmptyAndDoesNotTouchDbWhenTenantMissing() {
        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);

        // 无租户上下文（未经网关注入）：返回空、绝不查库（D9 不泄漏）。
        MetaSearchResult result = adapter.search(SearchQuery.of(null, null, 1, 20));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        verifyNoInteractions(repository);
    }
}
