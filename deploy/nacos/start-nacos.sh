#!/usr/bin/env bash
# =============================================================================
# 在 Docker 里启动 Nacos（standalone + MySQL 8 存储），并建好 lumenote 命名空间。
# 依赖：同一个 VM 上的 mysql8 容器（deploy/mysql8）
# 用法：cd deploy/nacos && bash start-nacos.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

log()  { printf '\033[1;32m[nacos]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[nacos] 注意：\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[nacos] 失败：\033[0m %s\n' "$*" >&2; exit 1; }

# 先继承 MySQL 的账号密码，再让 nacos/.env 覆盖（注意：不能在这里就把密码定下来，
# 因为 nacos/.env 里若留着空的 MYSQL_PASSWORD= 会把它清空）
if [ -f ../mysql8/.env ]; then
  set -a; . ../mysql8/.env; set +a
fi
if [ ! -f .env ]; then
  cp .env.example .env
  log ".env 不存在，已从 .env.example 生成"
fi
set -a; . ./.env; set +a

# 两处都读完再兜底：空值回退到 mysql8 的 MYSQL_ROOT_PASSWORD
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
MYSQL_PORT="${MYSQL_PORT:-3306}"

IMAGE="nacos/nacos-server:${NACOS_VERSION:-v2.4.3}"
HTTP_PORT="${NACOS_HTTP_PORT:-8848}"
NAMESPACE="${NACOS_NAMESPACE:-lumenote}"
NACOS_DB="${NACOS_DB:-nacos_config}"
LOG_DIR="${NACOS_LOG_DIR:-/opt/lumenote/nacos/logs}"
DATA_DIR="${NACOS_DATA_DIR:-/opt/lumenote/nacos/data}"
MYSQL_HOST="${MYSQL_HOST:-host.docker.internal}"
MYSQL_USER="${MYSQL_USER:-root}"
SCHEMA_FILE="$PWD/mysql-schema.sql"

: "${MYSQL_PASSWORD:?找不到 MySQL 密码：填 nacos/.env 的 MYSQL_PASSWORD，或先部署 mysql8}"
export MYSQL_PASSWORD

command -v docker >/dev/null 2>&1 || die "未安装 docker，先跑 deploy/docker/install-docker.sh"
docker info >/dev/null 2>&1 || die "Docker 服务没运行：systemctl start docker"
docker ps --format '{{.Names}}' | grep -qx mysql8 || die "mysql8 容器不在运行，先 bash ../mysql8/start-mysql.sh"

log "拉取 $IMAGE（约 400MB，首次稍慢）"
docker pull "$IMAGE" >/dev/null || die "镜像拉取失败：docker pull $IMAGE 看具体报错（或换 NACOS_VERSION=v2.2.3）"

# ---- 1. 从镜像里取建表 SQL，避免依赖 GitHub 下载 ----
if [ -s "$SCHEMA_FILE" ]; then
  log "复用已有 $SCHEMA_FILE"
else
  log "从镜像里提取 Nacos 建表 SQL"
  FOUND=""
  for p in /home/nacos/conf/mysql-schema.sql /home/nacos/conf/schema.sql; do
    if docker run --rm --entrypoint sh "$IMAGE" -c "test -f $p" 2>/dev/null; then FOUND="$p"; break; fi
  done
  [ -n "$FOUND" ] || die "镜像里找不到 mysql-schema.sql，手工下载放到 $SCHEMA_FILE"
  docker run --rm --entrypoint sh "$IMAGE" -c "cat $FOUND" > "$SCHEMA_FILE"
  log "已提取：$FOUND -> $SCHEMA_FILE（$(wc -l < "$SCHEMA_FILE") 行）"
fi

# ---- 2. 建库 + 首次导入表结构 ----
log "准备 MySQL 库 $NACOS_DB"
docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e \
  "CREATE DATABASE IF NOT EXISTS \`$NACOS_DB\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" \
  2>/dev/null || die "连不上 mysql8（密码是否与 ../mysql8/.env 一致？）"

TABLES=$(docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -B -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$NACOS_DB';" 2>/dev/null)
if [ "${TABLES:-0}" -eq 0 ]; then
  log "导入表结构（首次）"
  docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$NACOS_DB" < "$SCHEMA_FILE" 2>/dev/null \
    || die "导入表结构失败，检查 $SCHEMA_FILE 与 MySQL 版本"
  log "导入完成，表数量：$(docker exec -i mysql8 mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$NACOS_DB';" 2>/dev/null)"
else
  log "库中已有 $TABLES 张表，跳过导入"
fi

# ---- 3. 目录 + 防火墙 ----
mkdir -p "$LOG_DIR" "$DATA_DIR"
if systemctl is-active --quiet firewalld 2>/dev/null; then
  log "放行 8848/9848/9849（firewalld）"
  firewall-cmd --permanent --add-port=8848/tcp >/dev/null 2>&1 || true
  firewall-cmd --permanent --add-port=9848/tcp >/dev/null 2>&1 || true
  firewall-cmd --permanent --add-port=9849/tcp >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
fi

# ---- 4. 启动 ----
if docker compose version >/dev/null 2>&1; then
  log "使用 docker compose 启动"
  docker compose up -d
else
  log "没有 compose 插件，使用 docker run 启动"
  docker rm -f nacos >/dev/null 2>&1 || true
  docker run -d --name nacos --restart unless-stopped \
    -p "${HTTP_PORT}:8848" -p 9848:9848 -p 9849:9849 \
    -e MODE=standalone \
    -e SPRING_DATASOURCE_PLATFORM=mysql \
    -e MYSQL_SERVICE_HOST="$MYSQL_HOST" \
    -e MYSQL_SERVICE_PORT="${MYSQL_PORT:-3306}" \
    -e MYSQL_SERVICE_DB_NAME="$NACOS_DB" \
    -e MYSQL_SERVICE_USER="$MYSQL_USER" \
    -e MYSQL_SERVICE_PASSWORD="$MYSQL_PASSWORD" \
    -e MYSQL_SERVICE_DB_PARAM="characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
    -e NACOS_AUTH_ENABLE="${NACOS_AUTH_ENABLE:-false}" \
    -e TZ=Asia/Shanghai -e JVM_XMS=512m -e JVM_XMX=512m \
    --add-host "host.docker.internal:host-gateway" \
    -v "$LOG_DIR:/home/nacos/logs" -v "$DATA_DIR:/home/nacos/data" \
    "$IMAGE" >/dev/null
fi

# ---- 5. 等就绪 ----
log "等待 Nacos 就绪（首次连库建表约 30~60 秒）"
for i in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:${HTTP_PORT}/nacos/v1/console/health/liveness" 2>/dev/null)" = "200" ]; then
    break
  fi
  if [ "$i" -eq 60 ]; then
    docker logs --tail 80 nacos >&2 || true
    die "Nacos 120 秒未就绪，日志见上（常见：连不上 MySQL / 密码错 / 9848 端口占用）"
  fi
  sleep 2
done

# ---- 6. 建 lumenote 命名空间（bootstrap.yaml 里 namespace: lumenote 指的就是这个 ID）----
log "确认命名空间 $NAMESPACE"
EXIST=$(curl -s --max-time 5 "http://127.0.0.1:${HTTP_PORT}/nacos/v1/console/namespaces" 2>/dev/null | grep -c "\"namespace\":\"$NAMESPACE\"" || true)
if [ "${EXIST:-0}" -eq 0 ]; then
  curl -s --max-time 5 -X POST "http://127.0.0.1:${HTTP_PORT}/nacos/v1/console/namespaces" \
    -d "customNamespaceId=$NAMESPACE" -d "namespaceName=$NAMESPACE" \
    -d "namespaceDesc=lumenote 开发环境" >/dev/null || true
  log "已创建命名空间（customNamespaceId=$NAMESPACE）"
else
  log "命名空间已存在，跳过"
fi

HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
log "Nacos 就绪，控制台：http://${HOST_IP:-<虚拟机IP>}:${HTTP_PORT}/nacos  （开发环境未开鉴权，免登录）"
log "命名空间：$NAMESPACE　数据存 MySQL 库：$NACOS_DB　日志：$LOG_DIR"
