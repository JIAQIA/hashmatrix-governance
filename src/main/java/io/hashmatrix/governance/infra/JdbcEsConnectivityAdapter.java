package io.hashmatrix.governance.infra;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.hashmatrix.governance.domain.InfraStatus;
import io.hashmatrix.governance.domain.TenantSchema;
import io.hashmatrix.governance.domain.port.InfraConnectivityPort;
import io.hashmatrix.starter.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link InfraConnectivityPort} 的 PostgreSQL(JSONB) + Elasticsearch 适配实现。
 *
 * <p>多租户路由：按当前 {@link TenantContextHolder} 派生 {@link TenantSchema}，在**单个事务内**
 * 用 {@code SET LOCAL search_path} 切到租户 schema 后再查询——
 * <ul>
 *   <li>{@code LOCAL} 使 search_path 仅在本事务生效，提交/回滚即自动复位，
 *       池化连接归还复用时**不会继承上一租户的 search_path**（杜绝跨租户串读）；</li>
 *   <li>同一事务保证 {@code SET} 与后续查询落在同一物理连接上。</li>
 * </ul>
 * 这是引擎 #1 落地业务查询时应复用的租户路由范式。schema 名已在 {@link TenantSchema} 归一化（无注入）。
 * 本基座仅验证连通与 JSONB 能力，不建业务表（typedef/实例表由引擎 #1 落地）。
 */
@Component
public class JdbcEsConnectivityAdapter implements InfraConnectivityPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcEsConnectivityAdapter.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ElasticsearchClient elasticsearchClient;

    public JdbcEsConnectivityAdapter(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ElasticsearchClient elasticsearchClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public InfraStatus probe() {
        TenantSchema schema =
                TenantSchema.forTenant(TenantContextHolder.getTenantId().orElse("public"));
        return new InfraStatus(schema.name(), postgresReachable(schema), elasticsearchReachable());
    }

    private boolean postgresReachable(TenantSchema schema) {
        try {
            Boolean ok =
                    transactionTemplate.execute(
                            status -> {
                                // SET LOCAL：仅本事务生效，事务结束自动复位，池连接复用不泄漏
                                jdbcTemplate.execute(
                                        "SET LOCAL search_path TO \"" + schema.name() + "\", public");
                                // 探针：验证连通 + JSONB 能力（不存在的 schema 不报错，留待引擎建表）
                                jdbcTemplate.queryForObject("SELECT '{}'::jsonb", String.class);
                                return true;
                            });
            return Boolean.TRUE.equals(ok);
        } catch (RuntimeException ex) {
            log.warn("PostgreSQL 连通探测失败: {}", ex.getMessage());
            return false;
        }
    }

    private boolean elasticsearchReachable() {
        try {
            return elasticsearchClient.ping().value();
        } catch (Exception ex) {
            log.warn("Elasticsearch 连通探测失败: {}", ex.getMessage());
            return false;
        }
    }
}
