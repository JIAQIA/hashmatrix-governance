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

## 工程基座（本地构建与运行）

> 本仓已具备**可独立开发/编译/调试/运行**的 Spring Boot 工程基座（governance#2）。
> 本基座只打通基础设施与多租户路由，**不实现引擎业务**（自研元模型引擎在 #1）。

**分层骨架**（依赖自上而下，内层不依赖外层）：`api`（REST/统一返回）→ `app`（用例编排/审计）→ `domain`（领域模型+出站端口）→ `infra`（PG/ES 适配+租户 schema 路由）。

**公共能力复用**（经 Maven 坐标引用 `libs-java`，非 submodule 路径）：
`starter-tenant`（`X-Tenant-Id` → `TenantContext`）、`starter-web`（统一返回/异常）、`starter-audit`（结构化审计·自动加盖租户）、`starter-observability`（actuator + `/actuator/prometheus`）、`starter-test`（JUnit5/AssertJ/Mockito/Testcontainers + 脱敏 fixtures）。

```bash
# 1) 纯打包（跳过所有测试，无需 Docker）—— 对应 DoD 验收
mvn -q -DskipTests package

# 2a) 构建 + 单测（surefire，无需 Docker）
mvn -B package

# 2b) + 集成切片（failsafe，Testcontainers 起 PG+ES，需 Docker）
mvn -B verify

# 3) 本地起栈 + 运行，验证健康检查
docker compose -f docker-compose.local.yml up -d
bash scripts/run-local.sh          # 或 mvn spring-boot:run -Dspring-boot.run.profiles=local
curl -s localhost:8080/actuator/health
curl -s localhost:8080/api/governance/probe -H 'X-Tenant-Id: tenant-demo'
```

### 冷克隆构建：配置 GitHub Packages

经 Maven 坐标解析 `io.hashmatrix` 公共依赖需能访问 GitHub Packages。**注意**：pom 内 `<repositories>` 无法解析 `<parent>` 自身——Maven 必须先从制品仓拉到 parent 才会读 pom，故 `<parent>` 的解析只能靠 `~/.m2/settings.xml` 里的 **repository（profile）**，仅配 server 凭据不够。空 `.m2` 新机请加入下列 `settings.xml`（与 CI 等价）：

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>   <!-- 需 packages:read -->
    </server>
  </servers>
  <profiles>
    <profile>
      <id>github</id>
      <repositories>
        <repository>
          <id>github</id>
          <url>https://maven.pkg.github.com/HashMatrixData/hashmatrix</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>github</id>
          <url>https://maven.pkg.github.com/HashMatrixData/hashmatrix</url>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles><activeProfile>github</activeProfile></activeProfiles>
</settings>
```

> 或本地先 `mvn install` libs-java（制品进本地 `.m2` 即可离线解析）。多租户隔离：每租户路由到 `gov_<tenant>` schema（行级兜底过滤）。**连接参数/凭据均 env 可覆盖、不入库**（红线合规）。

## 说明

本仓库作为 `hashmatrix` 主仓的 git submodule，挂载于 `services/governance`。架构背景见主仓 `docs/architecture/`。

## License

Apache-2.0
