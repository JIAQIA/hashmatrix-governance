#!/usr/bin/env bash
# redline-check.sh —— CI/发布前「信息红线」结构化校验（公开开源仓，见 CLAUDE.md）。
#
# 源码/POM/配置/脚本不得含真实主机 IP、内网地址或凭据。两类检查，命中即非零退出：
#   1) 结构性：真实 IPv4 字面量（排除回环/通配占位）。
#   2) denylist：可选本地词表 scripts/.redline-denylist（已 gitignore，仅本地留存真实代号/客户术语）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${1:-$(cd "$SCRIPT_DIR/.." && pwd)}"
DENYLIST="$SCRIPT_DIR/.redline-denylist"

EXCLUDES=(--exclude-dir=target --exclude-dir=.git --exclude-dir=node_modules)
ALLOW_IP_RE='^(0\.0\.0\.0|127\.0\.0\.1|255\.255\.255\.255)$'

fail=0
echo "🔎 redline-check: 扫描 $TARGET"

# ---- 0) denylist 必须未被 git 跟踪（防止真实代号/客户术语误入库） ----
if git -C "$TARGET" ls-files --error-unmatch "scripts/.redline-denylist" >/dev/null 2>&1; then
  echo "  ❌ scripts/.redline-denylist 已被 git 跟踪——该文件承载敏感术语，绝不可入库（应在 .gitignore）。"
  fail=1
fi

ip_hits="$(grep -RInaoE '\b([0-9]{1,3}\.){3}[0-9]{1,3}\b' "${EXCLUDES[@]}" "$TARGET" 2>/dev/null || true)"
if [[ -n "$ip_hits" ]]; then
  while IFS= read -r line; do
    ip="${line##*:}"
    if [[ ! "$ip" =~ $ALLOW_IP_RE ]]; then
      echo "  ❌ 疑似 IP 字面量: $line"
      fail=1
    fi
  done <<< "$ip_hits"
fi

if [[ -f "$DENYLIST" ]]; then
  while IFS= read -r term; do
    [[ -z "$term" || "$term" == \#* ]] && continue
    if grep -RInaF "${EXCLUDES[@]}" -- "$term" "$TARGET" >/dev/null 2>&1; then
      echo "  ❌ 命中 denylist 术语: $term"
      fail=1
    fi
  done < "$DENYLIST"
else
  echo "  [info] 未发现本地 denylist: $DENYLIST ; 仅做结构性 IP 检查。"
fi

if [[ "$fail" -ne 0 ]]; then
  echo "🛑 redline-check 失败：请清除上述敏感信息后再提交。"
  exit 1
fi
echo "✅ redline-check 通过。"
