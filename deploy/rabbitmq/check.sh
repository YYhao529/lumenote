#!/usr/bin/env bash
# RabbitMQ 自检：容器、端口、节点状态、vhost、账号权限、管理 API
set -uo pipefail
cd "$(dirname "$0")"

ok=0; bad=0
pass() { printf '  \033[1;32m✓\033[0m %s\n' "$*"; ok=$((ok+1)); }
fail() { printf '  \033[1;31m✗\033[0m %s\n' "$*"; bad=$((bad+1)); }

[ -f .env ] || { echo "缺少 .env"; exit 1; }
set -a; . ./.env; set +a
PORT="${RABBITMQ_PORT:-5672}"
MGMT_PORT="${RABBITMQ_MGMT_PORT:-15672}"
DATA_DIR="${RABBITMQ_DATA_DIR:-/opt/lumenote/rabbitmq/data}"
APP_USER="${RABBITMQ_APP_USER:-deepsea}"
VHOST="${RABBITMQ_VHOST:-/lumenote}"

echo "== 容器 =="
if docker ps --format '{{.Names}}' | grep -qx rabbitmq; then
  pass "rabbitmq 运行中：$(docker ps -f name=^rabbitmq$ --format '{{.Status}}')"
else
  fail "rabbitmq 未运行（docker ps -a | grep rabbitmq；docker logs rabbitmq）"
fi

echo "== 端口 =="
for p in "$PORT" "$MGMT_PORT"; do
  if ss -lnt "( sport = :$p )" 2>/dev/null | grep -q LISTEN; then pass "宿主机 $p 已监听"; else fail "宿主机 $p 未监听"; fi
done

echo "== 节点 =="
if docker exec rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
  pass "节点存活：$(docker exec rabbitmq rabbitmqctl -q status 2>/dev/null | grep -E '^RabbitMQ version' | awk '{print $NF}')"
else
  fail "节点无响应（rabbitmq-diagnostics -q ping）"
fi

echo "== vhost 与账号 =="
docker exec rabbitmq rabbitmqctl -q list_vhosts name 2>/dev/null | grep -qx "$VHOST" \
  && pass "vhost $VHOST 存在" || fail "vhost $VHOST 缺失"
docker exec rabbitmq rabbitmqctl -q list_users 2>/dev/null | cut -f1 | grep -qx "$APP_USER" \
  && pass "用户 $APP_USER 存在" || fail "用户 $APP_USER 缺失"
PERM=$(docker exec rabbitmq rabbitmqctl -q list_permissions -p "$VHOST" 2>/dev/null | awk -v u="$APP_USER" '$1==u {print "conf=" $2 " write=" $3 " read=" $4}')
[ -n "$PERM" ] && pass "权限：$PERM" || fail "用户 $APP_USER 在 $VHOST 上没有权限"

echo "== 数据落盘（宿主机目录）=="
if [ -d "$DATA_DIR" ]; then
  MOUNT=$(docker inspect rabbitmq --format '{{range .Mounts}}{{if eq .Destination "/var/lib/rabbitmq"}}{{.Source}}{{end}}{{end}}' 2>/dev/null)
  [ "$MOUNT" = "$DATA_DIR" ] && pass "挂载点 -> $DATA_DIR（$(du -sh "$DATA_DIR" 2>/dev/null | cut -f1)）" || fail "挂载点不符（$MOUNT）"
  [ -f "$DATA_DIR/.erlang.cookie" ] && pass "已生成 .erlang.cookie（节点元数据在宿主机上）" || fail "缺少 .erlang.cookie"
else
  fail "数据目录 $DATA_DIR 不存在"
fi

echo "== 管理 API =="
CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
  -u "${APP_USER}:${RABBITMQ_APP_PASS}" "http://127.0.0.1:${MGMT_PORT}/api/overview" 2>/dev/null)
[ "$CODE" = "200" ] && pass "GET /api/overview 200（账号可登录管理后台）" || fail "管理 API 返回 $CODE"

echo "== 结果：${ok} 项通过，${bad} 项异常 =="
[ "$bad" -eq 0 ]
