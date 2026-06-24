package io.hashmatrix.governance.app;

import io.hashmatrix.governance.domain.metadata.AssetSummary;
import io.hashmatrix.governance.domain.metadata.AssetUpsertRequest;
import io.hashmatrix.governance.domain.port.MetadataCatalogPort;
import io.hashmatrix.starter.audit.AuditEvent;
import io.hashmatrix.starter.audit.AuditRecorder;
import org.springframework.stereotype.Service;

/**
 * 元数据写入应用服务：经 {@link MetadataCatalogPort} 在**当前租户**登记/编辑资产，并记一次审计
 * （{@code starter-audit} 自动加盖当前租户）。租户隔离与去重/不存在判定由适配器落地（D9）。
 *
 * <p>与只读的 {@link MetadataQueryService} 对称；{@code api} 层经本服务写入，不耦合具体来源。
 */
@Service
public class MetadataCommandService {

    private final MetadataCatalogPort catalog;
    private final AuditRecorder auditRecorder;

    public MetadataCommandService(MetadataCatalogPort catalog, AuditRecorder auditRecorder) {
        this.catalog = catalog;
        this.auditRecorder = auditRecorder;
    }

    /** 登记一条资产并记审计。 */
    public AssetSummary register(AssetUpsertRequest request) {
        AssetSummary summary = catalog.register(request);
        auditRecorder.record(
                AuditEvent.of(
                        "system", // post-M1：取真实调用方（网关 JWT subject）
                        "META_ASSET_REGISTER",
                        "meta/assets/" + summary.id(),
                        AuditEvent.Outcome.SUCCESS,
                        "name=" + summary.name()));
        return summary;
    }

    /** 按 id 编辑一条资产并记审计。 */
    public AssetSummary update(String id, AssetUpsertRequest request) {
        AssetSummary summary = catalog.update(id, request);
        auditRecorder.record(
                AuditEvent.of(
                        "system",
                        "META_ASSET_UPDATE",
                        "meta/assets/" + id,
                        AuditEvent.Outcome.SUCCESS,
                        "name=" + summary.name()));
        return summary;
    }
}
