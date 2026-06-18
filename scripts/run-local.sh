#!/usr/bin/env bash
# 本地一键起栈 + 启动应用，验证 /actuator/health 通过。
# 用法：bash scripts/run-local.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "▶ 启动本地依赖栈（PostgreSQL + Elasticsearch）"
docker compose -f docker-compose.local.yml up -d

echo "▶ 等待依赖就绪（PostgreSQL + Elasticsearch）..."
for i in $(seq 1 30); do
  pg_ok=0; es_ok=0
  docker compose -f docker-compose.local.yml exec -T postgres pg_isready -U governance >/dev/null 2>&1 && pg_ok=1
  curl -sf http://localhost:9200/_cluster/health >/dev/null 2>&1 && es_ok=1
  if [[ "$pg_ok" -eq 1 && "$es_ok" -eq 1 ]]; then
    break
  fi
  sleep 2
done

echo "▶ 构建并启动应用（local profile）"
mvn -B -ntp -DskipTests spring-boot:run -Dspring-boot.run.profiles=local
