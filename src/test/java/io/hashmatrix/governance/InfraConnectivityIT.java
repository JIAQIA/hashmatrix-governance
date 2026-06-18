package io.hashmatrix.governance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hashmatrix.test.fixtures.MockTenants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成切片：用 Testcontainers 起真实 PostgreSQL + Elasticsearch，验证工程基座的基础设施接线、
 * 多租户 schema 路由与 actuator 健康——即 governance#2 验收「mvn verify 起 PG+ES → 自检绿」。
 *
 * <p>走 failsafe（{@code *IT}），{@code mvn package} 不触发；{@code mvn verify} 需本地/CI 有 Docker。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InfraConnectivityIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("governance")
                    .withUsername("governance")
                    .withPassword("governance");

    @Container
    static final ElasticsearchContainer ELASTICSEARCH =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void infraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void probeReportsBothInfraReachableUnderTenantSchema() throws Exception {
        mvc.perform(get("/api/governance/probe").header("X-Tenant-Id", MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.tenantSchema").value("gov_acme"))
                .andExpect(jsonPath("$.data.postgres").value(true))
                .andExpect(jsonPath("$.data.elasticsearch").value(true));
    }

    @Test
    void routesEachTenantToItsOwnSchemaAcrossPooledConnections() throws Exception {
        // 连续探测两个租户：验证 SET LOCAL 按租户正确路由、且池连接复用不串读（守护 W1 修复）
        mvc.perform(get("/api/governance/probe").header("X-Tenant-Id", MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantSchema").value("gov_acme"))
                .andExpect(jsonPath("$.data.postgres").value(true));
        mvc.perform(get("/api/governance/probe").header("X-Tenant-Id", MockTenants.TENANT_DEMO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantSchema").value("gov_tenant_demo"))
                .andExpect(jsonPath("$.data.postgres").value(true));
    }

    @Test
    void actuatorHealthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
