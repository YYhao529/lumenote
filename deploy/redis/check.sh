#!/usr/bin/env bash
# Redis 自检：容器、端口、PING、版本、持久化、数据目录
set -uo pipefail
cd "$(dirname "$0")"

ok=0; bad=0
pass() { printf '  \033[1;32m✓\033[0m %s\n' "$*"; ok=$((ok+1)); }
fail() { printf '  \033[1;31m✗\033[0m %s\n' "$*"; bad=$((bad+1)); }

[ -f .env ] || { echo "缺少 .env"; exit 1; }
set -a; . ./.env; set +a
PORT="${REDIS_PORT:-6379}"
DATA_DIR="${REDIS_DATA_DIR:-/opt/lumenote/redis/data}"

echo "== 容器 =="
if docker ps --format '{{.Names}}' | grep -qx redis; then
  pass "redis 运行中：$(docker ps -f name=^redis$ --format '{{.Status}}')"
else
  fail "redis 未运行（docker ps -a | grep redis；docker logs redis）"
fi

echo "== 端口 =="
if ss -lnt "( sport = :$PORT )" 2>/dev/null | grep -q LISTEN; then
  pass "宿主机 ${PORT} 已监听"
else
  fail "宿主机 ${PORT} 未监听"
fi

echo "== 连通与版本 =="
PONG=$(docker exec redis redis-cli ping 2>/dev/null | tr -d '\r')
[ "$PONG" = "PONG" ] && pass "redis-cli PING -> PONG" || fail "PING 无响应（$PONG）"
VER=$(docker exec redis redis-cli INFO server 2>/dev/null | grep -E "^redis_version" | tr -d '\r' | cut -d: -f2)
[ -n "$VER" ] && pass "Redis $VER" || fail "读不到版本"

echo "== 持久化 =="
AOF=$(docker exec redis redis-cli CONFIG GET appendonly 2>/dev/null | tr -d '\r' | tail -1)
[ "$AOF" = "yes" ] && pass "appendonly = yes（AOF 已开）" || fail "appendonly = ${AOF:-未知}"
SAVE=$(docker exec redis redis-cli CONFIG GET dir 2>/dev/null | tr -d '\r' | tail -1)
[ "$SAVE" = "/data" ] && pass "dir = /data" || fail "dir = ${SAVE:-未知}"

echo "== 数据落盘（宿主机目录）=="
if [ -d "$DATA_DIR" ]; then
  MOUNT=$(docker inspect redis --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Source}}{{end}}{{end}}' 2>/dev/null)
  [ "$MOUNT" = "$DATA_DIR" ] && pass "挂载点 -> $DATA_DIR" || fail "挂载点不符（$MOUNT）"
  # 空实例不会有 AOF 文件，先自己写一条带 TTL 的探针键，既验证读写也把 AOF 逼出来
  docker exec redis redis-cli SET lumenote:__check__ ok EX 60 >/dev/null 2>&1
  VAL=$(docker exec redis redis-cli GET lumenote:__check__ 2>/dev/null | tr -d '\r')
  if [ "$VAL" = "ok" ]; then
    pass "读写正常（SET/GET 探针键，60 秒后自动过期）"
  else
    fail "读写异常（GET 返回 ${VAL:-空}）"
  fi
  # Redis 7 的 AOF 在 appendonlydir/ 下（base.rdb / incr.aof / manifest），
  # 不能用一个 ls 带多个 glob——没匹配到的模式会让 ls 返回非 0
  FOUND=""
  for f in "$DATA_DIR"/appendonlydir/*.aof "$DATA_DIR"/appendonlydir/*.rdb "$DATA_DIR"/*.aof "$DATA_DIR"/dump.rdb; do
    [ -f "$f" ] && FOUND="$(basename "$f")" && break
  done
  if [ -n "$FOUND" ]; then
    pass "已生成持久化文件（$FOUND，目录 $(du -sh "$DATA_DIR" 2>/dev/null | cut -f1)）"
  else
    fail "没看到 AOF/RDB 文件（appendonly 是否真的生效？）"
  fi
else
  fail "数据目录 $DATA_DIR 不存在"
fi

echo "== 结果：${ok} 项通过，${bad} 项异常 =="
[ "$bad" -eq 0 ]
