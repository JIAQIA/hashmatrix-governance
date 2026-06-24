package io.hashmatrix.governance.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.hashmatrix.governance.domain.metadata.AssetSummary;
import io.hashmatrix.governance.domain.metadata.AssetType;
import io.hashmatrix.governance.domain.metadata.AssetUpsertRequest;
import io.hashmatrix.governance.domain.metadata.MetaSearchResult;
import io.hashmatrix.governance.domain.metadata.SearchQuery;
import io.hashmatrix.governance.infra.persistence.MetadataAssetEntity;
import io.hashmatrix.governance.infra.persistence.MetadataAssetRepository;
import io.hashmatrix.starter.tenant.TenantContext;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import io.hashmatrix.starter.web.BusinessException;
import io.hashmatrix.test.fixtures.MockTenants;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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

    @Test
    void registersAssetUnderCurrentTenantWithServerGeneratedId() {
        when(repository.save(any(MetadataAssetEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);

        AssetSummary summary =
                TenantContextHolder.callWith(
                        TenantContext.of(MockTenants.ACME),
                        () ->
                                adapter.register(
                                        new AssetUpsertRequest(
                                                "orders", AssetType.TABLE, "data-team", "orders",
                                                List.of("finance"), Map.of("k", "v"))));

        assertThat(summary.id()).isNotBlank(); // 服务端生成
        assertThat(summary.name()).isEqualTo("orders");
        verify(repository).existsByTenantIdAndCode(MockTenants.ACME, "orders");
        verify(repository).save(any(MetadataAssetEntity.class));
    }

    @Test
    void rejectsDuplicateCodeOnRegisterWith409() {
        when(repository.existsByTenantIdAndCode(MockTenants.ACME, "orders")).thenReturn(true);
        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);

        assertThatThrownBy(
                        () ->
                                TenantContextHolder.callWith(
                                        TenantContext.of(MockTenants.ACME),
                                        () ->
                                                adapter.register(
                                                        new AssetUpsertRequest(
                                                                "orders", AssetType.TABLE, null, "orders",
                                                                List.of(), Map.of()))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).save(any());
    }

    @Test
    void failsClosedOnRegisterWhenTenantMissing() {
        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);

        // 缺租户头：fail-closed 拒绝为 400（客户端错误，非 500），绝不落库（D9/D2）。
        assertThatThrownBy(
                        () ->
                                adapter.register(
                                        new AssetUpsertRequest(
                                                "orders", AssetType.TABLE, null, null, List.of(), Map.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test
    void updatesExistingAssetWithinTenant() {
        MetadataAssetEntity existing =
                MetadataAssetEntity.register(
                        MockTenants.ACME, "orders", AssetType.TABLE, "data-team",
                        List.of("finance"), Map.of());
        UUID id = existing.getId();
        when(repository.findByIdAndTenantId(id, MockTenants.ACME)).thenReturn(Optional.of(existing));
        when(repository.save(any(MetadataAssetEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);

        AssetSummary summary =
                TenantContextHolder.callWith(
                        TenantContext.of(MockTenants.ACME),
                        () ->
                                adapter.update(
                                        id.toString(),
                                        new AssetUpsertRequest(
                                                "orders_v2", AssetType.VIEW, "bi-team", null,
                                                List.of("report"), Map.of())));

        assertThat(summary.name()).isEqualTo("orders_v2");
        assertThat(summary.type()).isEqualTo(AssetType.VIEW);
    }

    @Test
    void update404WhenIdNotInTenant() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        when(repository.findByIdAndTenantId(id, MockTenants.TENANT_DEMO)).thenReturn(Optional.empty());
        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);

        assertThatThrownBy(
                        () ->
                                TenantContextHolder.callWith(
                                        TenantContext.of(MockTenants.TENANT_DEMO),
                                        () ->
                                                adapter.update(
                                                        id.toString(),
                                                        new AssetUpsertRequest(
                                                                "x", AssetType.TABLE, null, null,
                                                                List.of(), Map.of()))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update404OnMalformedId() {
        PostgresMetadataAdapter adapter = new PostgresMetadataAdapter(repository);

        assertThatThrownBy(
                        () ->
                                TenantContextHolder.callWith(
                                        TenantContext.of(MockTenants.ACME),
                                        () ->
                                                adapter.update(
                                                        "not-a-uuid",
                                                        new AssetUpsertRequest(
                                                                "x", AssetType.TABLE, null, null,
                                                                List.of(), Map.of()))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
