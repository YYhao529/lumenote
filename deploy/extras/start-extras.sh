#!/usr/bin/env bash
# =============================================================================
# 一键拉起剩余依赖：MinIO / Elasticsearch / Cassandra / Canal / xxl-job
# 用法：cd deploy/extras && bash start-extras.sh
# 前置：mysql8 容器已在运行（deploy/mysql8）
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

log()  { printf '\033[1;32m[extras]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[extras] 注意：\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[extras] 失败：\033[0m %s\n' "$*" >&2; exit 1; }

if [ -f ../mysql8/.env ]; then set -a; . ../mysql8/.env; set +a; fi
if [ ! -f .env ]; then cp .env.example .env; log ".env 不存在，已从 .env.example 生成"; fi
set -a; . ./.env; set +a

MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
CANAL_DB_USER="${CANAL_DB_USER:-canal}"
CANAL_DB_PASSWORD="${CANAL_DB_PASSWORD:-canal}"
XXL_JOB_DB="${XXL_JOB_DB:-xxl_job}"
MINIO_PORT="${MINIO_PORT:-9000}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
ES_PORT="${ES_PORT:-9200}"
CASSANDRA_PORT="${CASSANDRA_PORT:-9042}"
XXL_JOB_PORT="${XXL_JOB_PORT:-7777}"
KIBANA_PORT="${KIBANA_PORT:-5601}"
ZK_PORT="${ZK_PORT:-2181}"
MINIO_BUCKET="${MINIO_BUCKET:-deepsea-lumenote}"
CASSANDRA_KEYSPACE="${CASSANDRA_KEYSPACE:-lumenote}"
export MYSQL_PASSWORD MYSQL_PORT MYSQL_USER

: "${MYSQL_PASSWORD:?找不到 MySQL 密码：检查 ../mysql8/.env}"

command -v docker >/dev/null 2>&1 || die "未安装 docker"
docker info >/dev/null 2>&1 || die "Docker 服务没运行：systemctl start docker"
docker ps --format '{{.Names}}' | grep -qx mysql8 || die "mysql8 容器不在运行，先 bash ../mysql8/start-mysql.sh"

# ---------------------------------------------------------------- 1. 拉镜像 ----
log "拉取镜像（首次 3~8 分钟）"
IMAGES=(
  "${MINIO_IMAGE:-minio/minio:latest}"
  "${ES_IMAGE:-elasticsearch:7.3.0}"
  "cassandra:${CASSANDRA_VERSION:-4.1}"
  "${ZK_IMAGE:-zookeeper:3.5.6}"
)
for img in "${IMAGES[@]}"; do
  printf '  %-62s ' "$img"
  if docker image inspect "$img" >/dev/null 2>&1; then echo "已存在"; else
    docker pull "$img" >/dev/null && echo "完成" || die "拉取失败：$img"
  fi
done

# ------------------------------------------------- 2. 内核参数（ES 必需）----
# ES 的 bootstrap check 要求 vm.max_map_count >= 262144，否则容器直接退出
CUR=$(sysctl -n vm.max_map_count 2>/dev/null || echo 0)
if [ "${CUR:-0}" -lt 262144 ]; then
  log "调整 vm.max_map_count：$CUR -> 262144（写 /etc/sysctl.d/99-elasticsearch.conf 持久化）"
  sysctl -w vm.max_map_count=262144 >/dev/null
  echo "vm.max_map_count=262144" > /etc/sysctl.d/99-elasticsearch.conf
else
  log "vm.max_map_count 已是 $CUR，跳过"
fi

# ------------------------------------------------------------- 3. 数据目录 ----
log "准备数据目录并设置属主"
mkdir -p "${MINIO_DATA_DIR:-/opt/lumenote/minio/data}" \
         "${ES_DATA_DIR:-/opt/lumenote/es/data}" \
         "${CASSANDRA_DATA_DIR:-/opt/lumenote/cassandra/data}" \
         "${ZK_DATA_DIR:-/opt/lumenote/zookeeper/data}" \
         "${ZK_DATALOG_DIR:-/opt/lumenote/zookeeper/datalog}"
# ES 镜像固定用 uid 1000，Cassandra 用 uid 999
chown -R 1000:1000 "${ES_DATA_DIR:-/opt/lumenote/es/data}"
chown -R 999:999 "${CASSANDRA_DATA_DIR:-/opt/lumenote/cassandra/data}"
chmod 750 "${ES_DATA_DIR:-/opt/lumenote/es/data}" "${CASSANDRA_DATA_DIR:-/opt/lumenote/cassandra/data}"

# --------------------------------------------------- 4. MySQL 侧准备工作 ----
# Canal 已改为在宿主机（Windows）运行，但它要读这台 MySQL 的 binlog，
# 所以复制账号还是要在这里建好（宿主机 canal 的 instance.properties 直接用这组账号）。
log "创建 Canal 订阅账号 ${CANAL_DB_USER}（给宿主机上的 canal 用：REPLICATION SLAVE / CLIENT + SELECT）"
docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" <<SQL 2>/dev/null
CREATE USER IF NOT EXISTS '${CANAL_DB_USER}'@'%' IDENTIFIED WITH mysql_native_password BY '${CANAL_DB_PASSWORD}';
ALTER USER '${CANAL_DB_USER}'@'%' IDENTIFIED WITH mysql_native_password BY '${CANAL_DB_PASSWORD}';
GRANT SELECT, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO '${CANAL_DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL
log "Canal 账号就绪"

log "准备 xxl-job 库 ${XXL_JOB_DB}"
docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS \`${XXL_JOB_DB}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" 2>/dev/null
XXL_TABLES=$(docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -B \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${XXL_JOB_DB}';" 2>/dev/null)
if [ "${XXL_TABLES:-0}" -eq 0 ]; then
  if [ -s ./xxl-job-schema.sql ]; then
    log "导入 xxl-job 表结构（./xxl-job-schema.sql）"
    docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$XXL_JOB_DB" < ./xxl-job-schema.sql 2>/dev/null
    log "导入完成：$(docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${XXL_JOB_DB}';" 2>/dev/null) 张表"
  else
    warn "库 ${XXL_JOB_DB} 是空的，且没有 ./xxl-job-schema.sql —— xxl-job 会启动失败。"
    warn "把 xxl-job 的 tables_xxl_job.sql 放到 $PWD/xxl-job-schema.sql 后重跑。"
  fi
else
  log "库中已有 $XXL_TABLES 张表，跳过导入"
fi

# --------------------------------------------------------------- 5. 防火墙 ----
if systemctl is-active --quiet firewalld 2>/dev/null; then
  log "放行 ${MINIO_PORT}/${MINIO_CONSOLE_PORT}/${ES_PORT}/${CASSANDRA_PORT}/${ZK_PORT}/${XXL_JOB_PORT}/${KIBANA_PORT}"
  for p in "$MINIO_PORT" "$MINIO_CONSOLE_PORT" "$ES_PORT" "$CASSANDRA_PORT" "$ZK_PORT" "$XXL_JOB_PORT" "$KIBANA_PORT"; do
    firewall-cmd --permanent --add-port="${p}/tcp" >/dev/null 2>&1 || true
  done
  firewall-cmd --reload >/dev/null 2>&1 || true
fi

# ----------------------------------------------------------------- 6. 启动 ----
if docker compose version >/dev/null 2>&1; then
  log "docker compose up -d"
  docker compose up -d
else
  die "没有 docker compose 插件，请先装 docker-compose-plugin"
fi

# ------------------------------------------------------------- 7. 等待就绪 ----
wait_http() { # $1=name $2=url $3=timeout秒
  local name=$1 url=$2 t=${3:-180} i=0
  while [ $i -lt "$t" ]; do
    if curl -sf -o /dev/null --max-time 3 "$url" 2>/dev/null; then log "$name 就绪"; return 0; fi
    i=$((i+3)); sleep 3
  done
  docker logs --tail 40 "$name" >&2 || true
  warn "$name ${t}s 内未就绪"
  return 1
}

wait_http minio "http://127.0.0.1:${MINIO_PORT}/minio/health/live" 90 || true
wait_http elasticsearch "http://127.0.0.1:${ES_PORT}/_cluster/health" 180 || true

log "等待 Cassandra 起来（首次初始化约 60~120 秒）"
for i in $(seq 1 60); do
  if docker exec cassandra cqlsh -e "DESCRIBE KEYSPACES" >/dev/null 2>&1; then log "cassandra 就绪"; break; fi
  [ "$i" -eq 60 ] && { docker logs --tail 40 cassandra >&2 || true; warn "Cassandra 未就绪"; }
  sleep 3
done

log "等待 ZooKeeper 起来（ruok -> imok）"
ZK_OK=0
for i in $(seq 1 30); do
  RESP=$( { exec 3<>/dev/tcp/127.0.0.1/${ZK_PORT}; printf 'ruok' >&3; timeout 2 cat <&3; exec 3<&-; } 2>/dev/null )
  if [ "$RESP" = "imok" ]; then log "zookeeper 就绪（Mode: $( { exec 3<>/dev/tcp/127.0.0.1/${ZK_PORT}; printf 'srvr' >&3; timeout 2 cat <&3; exec 3<&-; } 2>/dev/null | grep -i '^Mode' | head -1)）"; ZK_OK=1; break; fi
  [ "$i" -eq 30 ] && { docker logs --tail 30 zookeeper >&2 || true; warn "ZooKeeper 未就绪"; }
  sleep 2
done

log "等待 xxl-job 起来"
wait_http xxl-job "http://127.0.0.1:${XXL_JOB_PORT}/xxl-job-admin/" 180 || true

# Kibana 放在 profile 里：镜像拉不到时不阻塞整套部署
if docker image inspect "${KIBANA_IMAGE:-kibana:7.3.0}" >/dev/null 2>&1; then
  log "启动 Kibana（ES 可视化 / 监控）"
  docker compose --profile kibana up -d kibana
  wait_http kibana "http://127.0.0.1:${KIBANA_PORT}/api/status" 300 || true
else
  warn "Kibana 镜像不在本地，跳过。需要时：docker pull ${KIBANA_IMAGE:-kibana:7.3.0} && docker compose --profile kibana up -d kibana"
fi

# ----------------------------------------------------- 8. 建桶 / 建 keyspace ----
# MinIO 不会自动建桶，代码里 putObject 之前也没有 makeBucket，所以桶必须预先存在。
# 有 mc 镜像就用它建；没有就提示用控制台手工建（MinIO 的桶在数据目录里就是一个子目录）。
if docker exec minio ls /data 2>/dev/null | grep -qx "$MINIO_BUCKET"; then
  log "MinIO 桶 ${MINIO_BUCKET} 已存在"
elif docker image inspect minio/mc:latest >/dev/null 2>&1; then
  log "用 minio/mc 创建桶 ${MINIO_BUCKET}"
  docker run --rm --add-host "host.docker.internal:host-gateway" --entrypoint sh minio/mc:latest -c \
    "mc alias set local http://host.docker.internal:${MINIO_PORT} '${MINIO_ROOT_USER:-deepsea}' '${MINIO_ROOT_PASSWORD}' >/dev/null && mc mb -p local/${MINIO_BUCKET}" \
    && log "桶已创建" || warn "建桶失败"
else
  warn "桶 ${MINIO_BUCKET} 不存在，且没有 minio/mc 镜像。"
  warn "用控制台 http://<虚拟机IP>:${MINIO_CONSOLE_PORT} 登录后新建 bucket，名称为 ${MINIO_BUCKET}"
fi

log "确保 Cassandra keyspace ${CASSANDRA_KEYSPACE}"
docker exec cassandra cqlsh -e \
  "CREATE KEYSPACE IF NOT EXISTS ${CASSANDRA_KEYSPACE} WITH replication = {'class':'SimpleStrategy','replication_factor':1};" >/dev/null 2>&1 \
  && log "keyspace 就绪" || warn "keyspace 创建失败，稍后可用 cqlsh 手动建"

HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
cat <<EOF

$(printf '\033[1;32mextras 部署完成\033[0m')（${HOST_IP:-<虚拟机IP>}）

  MinIO       http://${HOST_IP}:${MINIO_PORT}   控制台 http://${HOST_IP}:${MINIO_CONSOLE_PORT}  账号 ${MINIO_ROOT_USER:-deepsea} / 见 .env  桶 ${MINIO_BUCKET}
  ES          http://${HOST_IP}:${ES_PORT}     版本 7.x（xpack 已关）
  Cassandra   ${HOST_IP}:${CASSANDRA_PORT}     keyspace ${CASSANDRA_KEYSPACE}
  ZooKeeper   ${HOST_IP}:${ZK_PORT}            单节点，Leaf 雪花 workerId 分配用
  xxl-job     http://${HOST_IP}:${XXL_JOB_PORT}/xxl-job-admin  默认 admin/123456  accessToken 见 .env（需 --profile xxl-job）
  Kibana      http://${HOST_IP}:${KIBANA_PORT}             ES 可视化 / Stack Monitoring（需 --profile kibana，版本与 ES 一致）
EOF
