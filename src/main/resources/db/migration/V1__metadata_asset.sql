-- 元数据资产（governance 首张业务表，M2 数据目录·资产登记 #27/WP1）。
-- 多租户隔离档（主仓 Disc #58 / arch 05 D9 落地档位）：M2 走「行级 tenant_id 先行」——
--   写落 tenant_id、读按 X-Tenant-Id 强制过滤；升 schema-per-tenant 后行级过滤仍作兜底保留（不返工，守 R2）。
--   故本表落共享 schema（默认 public），不走 gov_<tenant> 路由；动态租户 schema 建表归 control-plane provisioning，子仓勿擅自 CREATE SCHEMA。
-- 可扩展（R2）：attributes JSONB 承接 #1 元模型引擎落地时的自定义属性，避免引擎落地返工。
-- 🔴 红线：示例/种子一律虚构脱敏（asset-* / acme / 通用业务名），无任何真实信息。

CREATE TABLE metadata_asset (
    id          UUID         PRIMARY KEY,
    -- 租户隔离路由键，对齐 ICD tenant-context-headers 的 X-Tenant-Id（D9：写落、读过滤）。
    tenant_id   VARCHAR(63)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    -- 资产类型（契约 governance-metadata-v1 的 AssetType 小写串：table/view/column/dataset/api）。
    type        VARCHAR(32)  NOT NULL,
    -- 归属（团队/责任人占位，脱敏，如 data-team）。
    owner       VARCHAR(255),
    -- 标签集合（JSONB 数组，如 ["finance","core"]）。
    tags        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- 可扩展属性（JSONB 对象）：预留 #1 元模型引擎的 typedef 驱动自定义属性。
    attributes  JSONB        NOT NULL DEFAULT '{}'::jsonb,

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 按租户过滤是所有读路径的第一约束（D9）：建租户索引支撑 search/list。
CREATE INDEX idx_metadata_asset_tenant ON metadata_asset (tenant_id);
