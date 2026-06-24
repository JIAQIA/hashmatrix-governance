package io.hashmatrix.governance.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hashmatrix.governance.infra.persistence.MetadataAssetRepository;
import io.hashmatrix.test.fixtures.MockTenants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 末端集成自检（#30）：经 **HTTP（MockMvc）→ 真实 controller/service/adapter → Testcontainers PG** 跑通
 * 数据目录·资产登记**写读链**，守护跨边界不变量 **D9 租户隔离** 与写路径 fail-closed，并验 M1 readiness 不回归。
 *
 * <p>纯测试、零生产改动：是 #27→#28→#29 的依赖图末端唯一汇聚点。不起 ES 容器（ES 客户端惰性，
 * readiness 为 deps-optional）；不使用类级 {@code @Transactional}（写经 HTTP 真提交，{@link #cleanUp} 清理），
 * 避免预期异常把测试事务标记 rollback-only。租户上下文经 {@code X-Tenant-Id} 头注入（{@code starter-tenant} 解析）。
 *
 * <p>走 failsafe（{@code *IT}），需本地/CI 有 Docker；置于 {@code io.hashmatrix.governance.it}（应用根严格子包）。
 */
@SpringBootTest(properties = "management.server.port=")
@AutoConfigureMockMvc
@Testcontainers
class MetadataWriteChainIsolationIT {

    private static final String TENANT_HEADER = "X-Tenant-Id";

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

    @Autowired private MockMvc mvc;
    @Autowired private MetadataAssetRepository repository;
    @Autowired private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    private static String body(String name, String code) {
        return """
                {"name":"%s","type":"table","owner":"data-team","code":"%s",\
                "tags":["finance"],"attributes":{"sourceSystem":"demo"}}"""
                .formatted(name, code);
    }

    @Test
    void registeredAssetVisibleToOwningTenantOnly() throws Exception {
        // acme 登记
        mvc.perform(
                        post("/api/meta/assets")
                                .header(TENANT_HEADER, MockTenants.ACME)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("orders", "orders")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("orders"));

        // 本租户 search 即可见
        mvc.perform(get("/api/meta/search").header(TENANT_HEADER, MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("orders"));

        // 他租户不可见（D9）
        mvc.perform(get("/api/meta/search").header(TENANT_HEADER, MockTenants.TENANT_DEMO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void cannotEditAnotherTenantsAsset() throws Exception {
        MvcResult created =
                mvc.perform(
                                post("/api/meta/assets")
                                        .header(TENANT_HEADER, MockTenants.ACME)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body("orders", "orders")))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = node.get("id").asText();

        // 以他租户身份按 id 编辑 → 404（不越权改他租户，D9）
        mvc.perform(
                        put("/api/meta/assets/" + id)
                                .header(TENANT_HEADER, MockTenants.TENANT_DEMO)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("hacked", "orders")))
                .andExpect(status().isNotFound());
    }

    @Test
    void writeWithoutTenantHeaderIsRejectedAndNotPersisted() throws Exception {
        // 缺租户头：fail-closed → 400（D9/D2），绝不落库
        mvc.perform(
                        post("/api/meta/assets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("orders", "orders")))
                .andExpect(status().isBadRequest());

        // 任一租户视角均查不到（未持久化）
        mvc.perform(get("/api/meta/search").header(TENANT_HEADER, MockTenants.ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void invalidBodyIsRejectedWith400() throws Exception {
        // name 缺失 → @Valid 触发 400（契约仅声明 400/409，不落 500）
        mvc.perform(
                        post("/api/meta/assets")
                                .header(TENANT_HEADER, MockTenants.ACME)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"type\":\"table\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readinessStaysGreen() throws Exception {
        // M1 不回归：readiness 为 deps-optional，应用自身 UP
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
