-- 资产业务编码 + 登记去重唯一键（M2 资产登记写端点 #29，对齐契约 governance-metadata-v1 v1.1.0）。
-- 写入面 AssetUpsertRequest.code 可选；业务唯一键 (tenant_id, code)：同租户同 code 视为重复 → 409。
-- 加法演进（在 V1 之上 ALTER），既有行 code 为 NULL。

ALTER TABLE metadata_asset ADD COLUMN code VARCHAR(255);

-- 部分唯一索引：仅对非空 code 约束 (tenant_id, code) 唯一；code 省略（NULL）不参与去重，可重复登记。
CREATE UNIQUE INDEX uq_metadata_asset_tenant_code
    ON metadata_asset (tenant_id, code)
    WHERE code IS NOT NULL;
