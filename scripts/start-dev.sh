#!/usr/bin/env bash
# 一键编译并启动 new-atelier 前后端开发环境
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT/logs"
BACKEND_PID=""
FRONTEND_PID=""
TAIL_PID=""
SHUTDOWN=0
SERVICES_STARTED=0

# shellcheck disable=SC1091
source "$SCRIPT_DIR/dev-common.sh"

load_env_file() {
  local env_file=$1
  echo "加载环境变量: $env_file"
  set +u
  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a
  set -u
}

if [ -f "$ROOT/.env" ]; then
  load_env_file "$ROOT/.env"
elif [ -f "$SCRIPT_DIR/.env" ]; then
  load_env_file "$SCRIPT_DIR/.env"
fi

BACKEND_PORT="${BACKEND_PORT:-8090}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

stop_services() {
  if [ -n "${FRONTEND_PID:-}" ]; then
    kill_process_tree "$FRONTEND_PID" TERM
  fi
  if [ -n "${BACKEND_PID:-}" ]; then
    kill_process_tree "$BACKEND_PID" TERM
  fi

  local i
  for i in $(seq 1 10); do
    if { [ -z "${BACKEND_PID:-}" ] || ! kill -0 "$BACKEND_PID" 2>/dev/null; } \
      && { [ -z "${FRONTEND_PID:-}" ] || ! kill -0 "$FRONTEND_PID" 2>/dev/null; }; then
      break
    fi
    sleep 1
  done

  if [ -n "${FRONTEND_PID:-}" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    kill_process_tree "$FRONTEND_PID" KILL
  fi
  if [ -n "${BACKEND_PID:-}" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill_process_tree "$BACKEND_PID" KILL
  fi

  graceful_kill_port "$BACKEND_PORT"
  graceful_kill_port "$FRONTEND_PORT"
}

cleanup() {
  if [ "$SHUTDOWN" -eq 1 ]; then
    return 0
  fi
  SHUTDOWN=1
  trap - INT TERM

  if [ -n "${TAIL_PID:-}" ] && kill -0 "$TAIL_PID" 2>/dev/null; then
    kill "$TAIL_PID" 2>/dev/null || true
    wait "$TAIL_PID" 2>/dev/null || true
  fi

  if [ "$SERVICES_STARTED" -eq 1 ]; then
    echo ""
    echo "正在停止服务 ..."
    stop_services
    echo "服务已停止"
  fi
}

wait_for_backend() {
  local url="http://127.0.0.1:${BACKEND_PORT}/api/v2/datasources"
  echo -n "等待后端就绪"
  for _ in $(seq 1 90); do
    if curl -sf --noproxy '*' "$url" >/dev/null 2>&1; then
      echo " OK"
      return 0
    fi
    if [ -n "${BACKEND_PID:-}" ] && ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      echo ""
      echo "后端启动失败，最近日志："
      tail -n 40 "$LOG_DIR/backend.log" 2>/dev/null || true
      exit 1
    fi
    echo -n "."
    sleep 2
  done
  echo ""
  echo "后端启动超时，请查看 $LOG_DIR/backend.log"
  exit 1
}

trap cleanup INT TERM

mkdir -p "$LOG_DIR"
graceful_kill_port "$BACKEND_PORT"
graceful_kill_port "$FRONTEND_PORT"

echo "==> [1/4] 编译后端 (mvn clean install -DskipTests)"
cd "$ROOT"
mvn clean install -DskipTests

echo ""
echo "==> [2/4] 安装并编译前端"
cd "$ROOT/atelier-web"
npm install
npm run build

echo ""
echo "==> [3/4] 启动后端 (端口 ${BACKEND_PORT})"
cd "$ROOT/atelier-app"
mvn spring-boot:run >"$LOG_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
echo "后端 PID: ${BACKEND_PID}，日志: $LOG_DIR/backend.log"

wait_for_backend

echo ""
echo "==> [4/4] 启动前端 (端口 ${FRONTEND_PORT})"
cd "$ROOT/atelier-web"
npm run dev >"$LOG_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "前端 PID: ${FRONTEND_PID}，日志: $LOG_DIR/frontend.log"

SERVICES_STARTED=1

sleep 2
echo ""
echo "=========================================="
echo "  new-atelier 开发环境已启动"
echo "  前端: http://localhost:${FRONTEND_PORT}"
echo "  后端: http://localhost:${BACKEND_PORT}"
echo "  API:  http://localhost:${BACKEND_PORT}/api/v2"
echo "=========================================="
echo "按 Ctrl+C 停止前后端，或另开终端执行 scripts/stop-dev.sh"
echo ""

tail -f "$LOG_DIR/frontend.log" "$LOG_DIR/backend.log" &
TAIL_PID=$!

while [ "$SHUTDOWN" -eq 0 ]; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null && ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [ "$SHUTDOWN" -eq 0 ]; then
  SHUTDOWN=1
  trap - INT TERM
  if [ -n "${TAIL_PID:-}" ] && kill -0 "$TAIL_PID" 2>/dev/null; then
    kill "$TAIL_PID" 2>/dev/null || true
    wait "$TAIL_PID" 2>/dev/null || true
  fi
  echo ""
  echo "服务已停止（外部 stop-dev.sh 或进程退出）"
  echo "提示: 日志中的 Maven BUILD FAILURE / exit code 143 为正常停止，可忽略"
fi

exit 0
