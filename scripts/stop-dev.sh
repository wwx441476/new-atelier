#!/usr/bin/env bash
# 停止 new-atelier 开发环境（释放 8090 / 5173 端口）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/dev-common.sh"

graceful_kill_port "$BACKEND_PORT"
graceful_kill_port "$FRONTEND_PORT"
echo "完成"
