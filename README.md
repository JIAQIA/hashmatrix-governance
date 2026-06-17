# hashmatrix-governance

> hashmatrix 数据中台子模块 · 所属：应用服务层 · 数据治理分系统
>
> 主仓：[HashMatrixData/hashmatrix](https://github.com/HashMatrixData/hashmatrix)

## 角色与位置（一眼看懂）

- **所属**：应用服务层 · 数据治理分系统（无状态 Spring Boot 应用）。
- **一句话**：平台的"数据账本"——元数据 / 血缘 / 质量 / 数据标准，**旁路采集各环节信息、不挡主链路**。
- **调用流**：data-foundation（湖仓/计算）→ 旁路采集 → **governance（元数据·血缘·质量）** → 供 webui / 数据服务消费。

## 职责与边界

- **做**：元数据管理、数据血缘、数据质量规则、专题-主题-实体三层模型、数据标准。
- **不做（边界）**：不做采集/计算（`data-foundation`）；不做分类分级/安全审批（`security`）；不存业务明细数据（只管元数据）。

## 骨架技术选型（首选 · 待逐仓细化）

| 维度 | 选型 |
|--|--|
| 运行时 | Spring Boot（Java） |
| 元模型引擎 | **自研**（以 Apache Atlas 的 TypeDef 体系为蓝本：元类继承 / 属性约束 / 关系基数 / 分类树 / 草稿发布 / 版本 / 平台公共+租户私有作用域） |
| 元数据采集 / 血缘来源 | 复用 Connector SPI + 数据源统一管理；OpenMetadata / Atlas / DataHub 仅作**采集连接器 / 血缘解析**的可选来源 |
| 存储 / 检索 | PostgreSQL(JSONB 存 typedef/实例) · Elasticsearch(检索) |

> **核心是元模型引擎**（运行期用户可自定义类型系统），不是开箱即用目录——OpenMetadata/DataHub 类型代码态不契合，故自研；选型依据见主仓 `docs/architecture/03-技术选型.md` 与本仓选型 Issue。血缘 / 关系可视化前端在 `webui`（AntV G6）；多租户上下文经主仓 `libs-java` 的 `starter-tenant` 透传。

## 产品形态与多租户（北极星）

**双模交付**：公网 SaaS（我们运营 · 统一**我们品牌** · 租户=企业）／私有化部署（客户环境 · **客户品牌**部署级 · 租户=客户部门）。品牌**部署级**、不按租户运行期换肤。多租户走 **C 分层桥接**：控制平面共享 + 数据平面按租户隔离（Keycloak Organizations 单 realm · schema/db-per-tenant · namespace-per-tenant），由 `control-plane` 编排开通。

**本仓视角**：治理资产按租户隔离（schema/catalog 路由 + 行级兜底），不跨租户串。

> 详见主仓 `docs/00-主仓初始化-spec.md`、`docs/architecture/05-多租户与控制平面.md`。

## 说明

本仓库作为 `hashmatrix` 主仓的 git submodule，挂载于 `services/governance`。架构背景见主仓 `docs/architecture/`。

## License

Apache-2.0
