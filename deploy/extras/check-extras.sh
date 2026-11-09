#!/usr/bin/env bash
# extras 自检：MinIO / Elasticsearch / Cassandra / Canal / xxl-job
set -uo pipefail
cd "$(dirname "$0")"

ok=0; bad=0
pass() { printf '  \033[1;32m✓\033[0m %s\n' "$*"; ok=$((ok+1)); }
fail() { printf '  \033[1;31m✗\033[0m %s\n' "$*"; bad=$((bad+1)); }

if [ -f ../mysql8/.env ]; then set -a; . ../mysql8/.env; set +a; fi
[ -f .env ] || { echo "缺少 .env"; exit 1; }
set -a; . ./.env; set +a
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
MINIO_PORT="${MINIO_PORT:-9000}"
MINIO_BUCKET="${MINIO_BUCKET:-deepsea-lumenote}"
ES_PORT="${ES_PORT:-9200}"
CASSANDRA_PORT="${CASSANDRA_PORT:-9042}"
CASSANDRA_KEYSPACE="${CASSANDRA_KEYSPACE:-lumenote}"
CANAL_PORT="${CANAL_PORT:-11111}"
XXL_JOB_PORT="${XXL_JOB_PORT:-7777}"
XXL_JOB_DB="${XXL_JOB_DB:-xxl_job}"

container_up() { docker ps --format '{{.Names}}' | grep -qx "$1"; }
port_up() { ss -lnt "( sport = :$1 )" 2>/dev/null | grep -q LISTEN; }

echo "== MinIO =="
container_up minio && pass "容器运行中" || fail "minio 未运行"
port_up "$MINIO_PORT" && pass "端口 ${MINIO_PORT} 已监听" || fail "端口 ${MINIO_PORT} 未监听"
curl -sf -o /dev/null --max-time 5 "http://127.0.0.1:${MINIO_PORT}/minio/health/live" \
  && pass "健康接口 /minio/health/live" || fail "健康接口无响应"
# MinIO 的桶在数据目录里就是一个子目录，直接看容器内 /data 最省事（不依赖 mc 镜像）
if docker exec minio ls /data 2>/dev/null | grep -qx "${MINIO_BUCKET}"; then
  pass "桶 ${MINIO_BUCKET} 存在（容器内 /data/${MINIO_BUCKET}）"
else
  fail "桶 ${MINIO_BUCKET} 不存在（控制台 http://<IP>:9001 新建）"
fi

echo "== Elasticsearch =="
container_up elasticsearch && pass "容器运行中" || fail "elasticsearch 未运行"
port_up "$ES_PORT" && pass "端口 ${ES_PORT} 已监听" || fail "端口 ${ES_PORT} 未监听"
ESV=$(curl -s --max-time 5 "http://127.0.0.1:${ES_PORT}" 2>/dev/null | grep -o '"number" *: *"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -n "$ESV" ]; then
  case "$ESV" in 7.*) pass "版本 $ESV（与客户端 7.3.0 同一大版本）" ;; *) fail "版本 $ESV 不是 7.x，high-level client 会不兼容" ;; esac
else
  fail "读不到版本（curl :${ES_PORT}）"
fi
ESJ=$(curl -s --max-time 5 "http://127.0.0.1:${ES_PORT}/_cluster/health" 2>/dev/null | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
[ -n "$ESJ" ] && pass "集群状态 $ESJ" || fail "集群健康接口异常"

echo "== Cassandra =="
container_up cassandra && pass "容器运行中" || fail "cassandra 未运行"
port_up "$CASSANDRA_PORT" && pass "端口 ${CASSANDRA_PORT} 已监听" || fail "端口 ${CASSANDRA_PORT} 未监听"
if docker exec cassandra cqlsh -e "DESCRIBE KEYSPACE ${CASSANDRA_KEYSPACE}" >/dev/null 2>&1; then
  pass "keyspace ${CASSANDRA_KEYSPACE} 存在"
else
  fail "keyspace ${CASSANDRA_KEYSPACE} 不存在"
fi

echo "== Canal（运行在宿主机，不在容器里）=="
CANAL_USER=$(docker exec -i mysql8 mysql -uroot -p"$MYSQL_PASSWORD" -N -B \
  -e "SELECT COUNT(*) FROM mysql.user WHERE user='${CANAL_DB_USER:-canal}'" 2>/dev/null)
[ "${CANAL_USER:-0}" -ge 1 ] \
  && pass "MySQL 复制账号 ${CANAL_DB_USER:-canal} 已就绪（宿主机 canal 用它读 binlog）" \
  || fail "MySQL 复制账号 ${CANAL_DB_USER:-canal} 缺失"

echo "== xxl-job =="
container_up xxl-job && pass "容器运行中" || fail "xxl-job 未运行"
port_up "$XXL_JOB_PORT" && pass "端口 ${XXL_JOB_PORT} 已监听" || fail "端口 ${XXL_JOB_PORT} 未监听"
CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:${XXL_JOB_PORT}/xxl-job-admin/toLogin" 2>/dev/null)
[ "$CODE" = "200" ] && pass "控制台登录页 200" || fail "控制台返回 $CODE（看 docker logs xxl-job）"
XXL_T=$(docker exec -i mysql8 mysql -uroot -p"$MYSQL_PASSWORD" -N -B \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${XXL_JOB_DB}'" 2>/dev/null)
[ "${XXL_T:-0}" -ge 8 ] && pass "库 ${XXL_JOB_DB} 有 ${XXL_T} 张表" || fail "库 ${XXL_JOB_DB} 表数量异常（${XXL_T}）"

echo "== 数据落盘 =="
for d in "${MINIO_DATA_DIR:-/opt/lumenote/minio/data}" "${ES_DATA_DIR:-/opt/lumenote/es/data}" "${CASSANDRA_DATA_DIR:-/opt/lumenote/cassandra/data}"; do
  [ -d "$d" ] && pass "$d（$(du -sh "$d" 2>/dev/null | cut -f1)）" || fail "$d 不存在"
done

echo "== 结果：${ok} 项通过，${bad} 项异常 =="
[ "$bad" -eq 0 ]
