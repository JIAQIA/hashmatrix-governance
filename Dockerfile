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
# 非 root 运行
RUN addgroup -S app && adduser -S app -G app
# 复制可执行 fat-jar（classifier=exec → *-exec.jar；瘦 jar 不含依赖，勿用）。
COPY --from=build /workspace/target/hashmatrix-governance-*-exec.jar app.jar
USER app
# 应用 HTTP 8082 / 管理(actuator) 9082（M1 §3 端口基线，运行期可经 SERVER_PORT/MANAGEMENT_SERVER_PORT 覆盖）。
EXPOSE 8082 9082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
