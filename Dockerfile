# syntax=docker/dockerfile:1
# 多阶段构建：build 阶段经 Maven 坐标解析公共依赖并打可执行 jar，run 阶段仅装 JRE + jar。
#
# 公共依赖在 GitHub Packages，build 阶段需凭据：用 BuildKit secret 挂载 Maven settings.xml，
# 避免 token 落入镜像层。示例：
#   DOCKER_BUILDKIT=1 docker build --secret id=maven_settings,src=$HOME/.m2/settings.xml -t hashmatrix-governance:local .

# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    --mount=type=cache,target=/root/.m2/repository \
    mvn -B -ntp -DskipTests clean package

# ---- run ----
FROM eclipse-temurin:17-jre-alpine AS run
WORKDIR /app
# 非 root 运行。USER 必须为「数字 uid」：K8s restricted 安全上下文（runAsNonRoot:true 且不带 runAsUser）
# 要在不启动容器的前提下验证 uid≠0，非数字用户名解析不出 uid → kubelet 拒绝创建容器
# （CreateContainerConfigError，见 #18）。故 -u/-g 显式钉死 uid 与 gid，并用数字 USER：
#   · 不依赖 busybox 自动分配（实测会落 uid=100/gid=101，且可能随基础镜像升级漂移）；
#   · 选 10001 远离系统区(0-999)，规避未来基础镜像新增约定 uid 1000 时的占用冲突；
#   · USER 仅写数字 uid（不写 :gid）——运行期 gid 由 app 用户主组(10001)决定，同样确定；
#     且 inspect .Config.User 恰为纯数字，CI 防回归断言无需解析冒号。
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
# 复制可执行 fat-jar（classifier=exec → *-exec.jar；瘦 jar 不含依赖，勿用）。
COPY --from=build /workspace/target/hashmatrix-governance-*-exec.jar app.jar
USER 10001
# 应用 HTTP 8082 / 管理(actuator) 9082（M1 §3 端口基线，运行期可经 SERVER_PORT/MANAGEMENT_SERVER_PORT 覆盖）。
EXPOSE 8082 9082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
