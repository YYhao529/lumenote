#!/usr/bin/env bash
# Nacos 部署后自检：容器、三个端口、健康接口、命名空间、MySQL 表
set -uo pipefail
cd "$(dirname "$0")"

ok=0; bad=0
pass() { printf '  \033[1;32m✓\033[0m %s\n' "$*"; ok=$((ok+1)); }
fail() { printf '  \033[1;31m✗\033[0m %s\n' "$*"; bad=$((bad+1)); }

[ -f .env ] || { echo "缺少 .env"; exit 1; }
if [ -f ../mysql8/.env ]; then set -a; . ../mysql8/.env; set +a; MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"; fi
set -a; . ./.env; set +a

HTTP_PORT="${NACOS_HTTP_PORT:-8848}"
NAMESPACE="${NACOS_NAMESPACE:-lumenote}"
NACOS_DB="${NACOS_DB:-nacos_config}"
MYSQL_USER="${MYSQL_USER:-root}"

echo "== 容器 =="
if docker ps --format '{{.Names}}' | grep -qx nacos; then
  pass "nacos 运行中：$(docker ps -f name=^nacos$ --format '{{.Status}}')"
else
  fail "nacos 未运行（docker ps -a | grep nacos；docker logs nacos）"
fi

echo "== 端口（2.x 客户端必须能连 9848）=="
for p in "$HTTP_PORT" 9848 9849; do
  if ss -lnt "( sport = :$p )" 2>/dev/null | grep -q LISTEN; then pass "宿主机 $p 已监听"; else fail "宿主机 $p 未监听"; fi
done

echo "== 健康接口 =="
CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:${HTTP_PORT}/nacos/v1/console/health/liveness" 2>/dev/null)
[ "$CODE" = "200" ] && pass "liveness 200" || fail "liveness 返回 $CODE"

echo "== 命名空间（bootstrap.yaml 用 namespace: lumenote）=="
if curl -s --max-time 5 "http://127.0.0.1:${HTTP_PORT}/nacos/v1/console/namespaces" 2>/dev/null | grep -q "\"namespace\":\"$NAMESPACE\""; then
  pass "命名空间 $NAMESPACE 存在"
else
  fail "命名空间 $NAMESPACE 不存在（bash start-nacos.sh 会补建）"
fi

echo "== MySQL 存储 =="
CNT=$(docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -B -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$NACOS_DB';" 2>/dev/null)
if [ "${CNT:-0}" -gt 10 ]; then
  pass "库 $NACOS_DB 有 $CNT 张表"
else
  fail "库 $NACOS_DB 表数量异常（$CNT）"
fi

echo "== 结果：${ok} 项通过，${bad} 项异常 =="
[ "$bad" -eq 0 ]
