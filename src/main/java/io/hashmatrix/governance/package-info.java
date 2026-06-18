/**
 * 数据治理分系统 —— 分层骨架（依赖方向自上而下，内层不依赖外层）：
 *
 * <ul>
 *   <li>{@code api} —— 入站适配（REST 控制器）：透传租户、统一返回（starter-web），不含业务规则。</li>
 *   <li>{@code app} —— 应用服务：编排用例、事务边界、审计（starter-audit），依赖 domain 端口。</li>
 *   <li>{@code domain} —— 领域模型与出站端口（port）：纯业务，无框架依赖。</li>
 *   <li>{@code infra} —— 出站适配：实现 domain 端口，对接 PostgreSQL(JSONB)/Elasticsearch、租户 schema 路由。</li>
 * </ul>
 *
 * <p>本基座只打通基础设施与多租户路由；自研元模型引擎业务（元类继承/关系基数/作用域/采集衔接/供数，
 * AD-16）在 governance#1 之上落地。
 */
package io.hashmatrix.governance;
