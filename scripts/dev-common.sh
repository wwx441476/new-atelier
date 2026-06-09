#!/usr/bin/env bash
# 开发脚本公共函数（由 start-dev.sh / stop-dev.sh source）

BACKEND_PORT="${BACKEND_PORT:-8090}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

# 递归终止进程树
kill_process_tree() {
  local pid=$1
  local signal=${2:-TERM}
  [ -z "$pid" ] && return 0
  kill -0 "$pid" 2>/dev/null || return 0

  local child
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do
    kill_process_tree "$child" "$signal"
  done
  kill "-${signal}" "$pid" 2>/dev/null || true
}

# 等待端口释放（最多 wait_secs 秒）
wait_port_free() {
  local port=$1
  local wait_secs=${2:-15}
  local i
  for i in $(seq 1 "$wait_secs"); do
    if ! lsof -ti "tcp:${port}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# 优雅停止占用端口的进程：SIGTERM -> 等待 -> SIGKILL
graceful_kill_port() {
  local port=$1
  local pids
  pids=$(lsof -ti "tcp:${port}" 2>/dev/null || true)
  if [ -z "$pids" ]; then
    echo "端口 ${port} 无运行中的进程"
    return 0
  fi

  echo "停止端口 ${port} 上的进程 ..."
  local pid
  for pid in $pids; do
    kill_process_tree "$pid" TERM
  done

  if wait_port_free "$port" 15; then
    return 0
  fi

  echo "强制停止端口 ${port} 上的进程 ..."
  pids=$(lsof -ti "tcp:${port}" 2>/dev/null || true)
  for pid in $pids; do
    kill_process_tree "$pid" KILL
  done
  wait_port_free "$port" 3 || true
}
