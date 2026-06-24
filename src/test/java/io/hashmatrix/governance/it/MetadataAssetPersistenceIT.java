package io.hashmatrix.governance.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hashmatrix.governance.domain.metadata.AssetSummary;
import io.hashmatrix.governance.domain.metadata.AssetType;
import io.hashmatrix.governance.domain.metadata.AssetUpsertRequest;
import io.hashmatrix.governance.domain.metadata.SearchQuery;
import io.hashmatrix.governance.infra.PostgresMetadataAdapter;
import io.hashmatrix.governance.infra.persistence.MetadataAssetEntity;
import io.hashmatrix.governance.infra.persistence.MetadataAssetRepository;
import io.hashmatrix.starter.tenant.TenantContext;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import io.hashmatrix.starter.web.BusinessException;
import io.hashmatrix.test.fixtures.MockTenants;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成切片：用 Testcontainers 起真实 PostgreSQL，验证 #27/WP1——Flyway 起服务建表成功、
 * {@link MetadataAssetEntity} 经 {@link MetadataAssetRepository} 存取通过、JSONB（tags/attributes）
 * 落库回读保真、且按租户隔离（D9：跨租户读不到他人资产）。
 *
 * <p>不起 ES 容器：本类只验持久层，ES 客户端惰性连接（仅 {@code /actuator/health} 触发 ping），
 * 应用上下文启动不依赖可达 ES（readiness 为 deps-optional）。ES 连通另由 {@link InfraConnectivityIT} 守护。
 *
 * <p>走 failsafe（{@code *IT}），{@code mvn package} 不触发；{@code mvn verify} 需本地/CI 有 Docker。
 * 置于 {@code io.hashmatrix.governance.it}（{@code GovernanceApplication} 严格子包），使裸
 * {@code @SpringBootTest} 能向上搜到 {@code @SpringBootConfiguration}（同 {@link InfraConnectivityIT}）。
 *
 * <p>每方法 {@code @Transactional} 回滚，保静态容器跨用例不串数据。
 */
@SpringBootTest(properties = "management.server.port=")
@Testcontainers
@Transactional
class MetadataAssetPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("governance")
                    .withUsername("governance")
                    .withPassword("governance");

    @DynamicPropertySource
    static void infraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MetadataAssetRepository repository;

    @Autowired
    private PostgresMetadataAdapter adapter;

    @Test
    void persistsAssetWithJsonbAndReadsItBack() {
        MetadataAssetEntity saved =
                repository.save(
                        MetadataAssetEntity.register(
                                MockTenants.ACME,
                                "orders",
                                AssetType.TABLE,
                                "data-team",
                                List.of("finance", "core"),
                                Map.of("sourceSystem", "demo", "rowCountHint", 1000)));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        MetadataAssetEntity reloaded =
                repository.findByIdAndTenantId(saved.getId(), MockTenants.ACME).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("orders");
        assertThat(reloaded.getType()).isEqualTo(AssetType.TABLE);
        assertThat(reloaded.getTags()).containsExactly("finance", "core");
        assertThat(reloaded.getAttributes())
                .containsEntry("sourceSystem", "demo")
                .containsEntry("rowCountHint", 1000);
    }

    @Test
    void isolatesAssetsByTenant() {
        repository.save(
                MetadataAssetEntity.register(
                        MockTenants.ACME, "orders", AssetType.TABLE, "data-team",
                        List.of("finance"), Map.of()));
        repository.save(
                MetadataAssetEntity.register(
                        MockTenants.TENANT_DEMO, "customers", AssetType.TABLE, "data-team",
                        List.of("crm"), Map.of()));

        assertThat(repository.findByTenantIdOrderByCreatedAtDesc(MockTenants.ACME))
                .extracting(MetadataAssetEntity::getName)
                .containsExactly("orders");
        assertThat(repository.findByTenantIdOrderByCreatedAtDesc(MockTenants.TENANT_DEMO))
                .extracting(MetadataAssetEntity::getName)
                .containsExactly("customers");
    }

    @Test
    void doesNotLeakAssetAcrossTenantsById() {
        MetadataAssetEntity acmeAsset =
                repository.save(
                        MetadataAssetEntity.register(
                                MockTenants.ACME, "orders", AssetType.TABLE, "data-team",
                                List.of(), Map.of()));

        // 以他租户身份按 id 取 → 取不到（D9：跨租户越权读被阻断）。
        assertThat(repository.findByIdAndTenantId(acmeAsset.getId(), MockTenants.TENANT_DEMO))
                .isEmpty();
    }

    // ---- WP3 写端点（经 PostgresMetadataAdapter 真写真查，#29）----

    @Test
    void registeredAssetIsImmediatelySearchableAndTenantIsolated() {
        TenantContextHolder.runWith(
                TenantContext.of(MockTenants.ACME),
                () ->
                        adapter.register(
                                new AssetUpsertRequest(
                                        "orders", AssetType.TABLE, "data-team", "orders",
                                        List.of("finance"), Map.of("sourceSystem", "demo"))));

        // 本租户登记即可见
        assertThat(
                        TenantContextHolder.callWith(
                                        TenantContext.of(MockTenants.ACME),
                                        () -> adapter.search(SearchQuery.of(null, null, 1, 20)))
                                .items())
                .extracting(AssetSummary::name)
                .containsExactly("orders");
        // 他租户不可见（D9）
        assertThat(
                        TenantContextHolder.callWith(
                                        TenantContext.of(MockTenants.TENANT_DEMO),
                                        () -> adapter.search(SearchQuery.of(null, null, 1, 20)))
                                .items())
                .isEmpty();
    }

    @Test
    void duplicateCodeWithinTenantIsRejectedWith409() {
        AssetUpsertRequest req =
                new AssetUpsertRequest(
                        "orders", AssetType.TABLE, "data-team", "dup-code", List.of(), Map.of());
        TenantContextHolder.runWith(TenantContext.of(MockTenants.ACME), () -> adapter.register(req));

        assertThatThrownBy(
                        () ->
                                TenantContextHolder.runWith(
                                        TenantContext.of(MockTenants.ACME), () -> adapter.register(req)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
