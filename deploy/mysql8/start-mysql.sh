#!/usr/bin/env bash
# =============================================================================
# 启动 MySQL 8 容器（有 compose 插件用 compose，没有则用 docker run）
# 数据目录绑定挂载到宿主机：默认 /opt/lumenote/mysql8/data（.env 里 MYSQL_DATA_DIR 可改）
# 等健康后创建业务账号并打印连接信息。可重复执行，不会丢数据。
# 用法：cd deploy/mysql8 && bash start-mysql.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

log()  { printf '\033[1;32m[mysql8]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[mysql8] 失败：\033[0m %s\n' "$*" >&2; exit 1; }

[ -f .env ] || { log ".env 不存在，从 .env.example 复制（记得改密码）"; cp .env.example .env; }
set -a; . ./.env; set +a

: "${MYSQL_ROOT_PASSWORD:?请先在 .env 设置 MYSQL_ROOT_PASSWORD}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-lumenote}"
MYSQL_ROOT_HOST="${MYSQL_ROOT_HOST:-%}"
DATA_DIR="${MYSQL_DATA_DIR:-/opt/lumenote/mysql8/data}"

command -v docker >/dev/null 2>&1 || die "未安装 docker，先跑 deploy/docker/install-docker.sh"
docker info >/dev/null 2>&1 || die "Docker 服务没运行：systemctl start docker"

# 端口被别的进程占用时尽早报错，而不是等容器反复重启
if command -v ss >/dev/null 2>&1 && ss -lnt "( sport = :${MYSQL_PORT} )" 2>/dev/null | grep -q LISTEN; then
  if ! docker ps --format '{{.Names}} {{.Ports}}' | grep -q '^mysql8 .*:'"${MYSQL_PORT}"'->'; then
    die "宿主机 ${MYSQL_PORT} 端口已被占用（ss -lntp | grep ${MYSQL_PORT} 查看），改 .env 里的 MYSQL_PORT 或释放端口"
  fi
fi

log "拉取 mysql:8.0（首次约 200MB，国内网络慢可稍等）"
docker pull mysql:8.0 >/dev/null || die "镜像拉取失败：检查 /etc/docker/daemon.json 的 registry-mirrors，或 docker pull mysql:8.0 看具体报错"

# 先让 mysqld 自己校验 my.cnf：参数写错（如 max_errors）会让容器反复重启，日志难读
log "校验 my.cnf 参数"
VALIDATION=$(docker run --rm --user mysql -v "$PWD/conf/my.cnf:/etc/mysql/conf.d/my.cnf:ro" \
  --entrypoint mysqld mysql:8.0 --validate-config 2>&1 || true)
if printf '%s' "$VALIDATION" | grep -qiE "unknown variable|invalid option|Aborting"; then
  printf '%s\n' "$VALIDATION" >&2
  die "my.cnf 里有 MySQL 不认的参数（见上），改完再启动"
fi

# ---- 准备数据目录：绑定挂载到宿主机，权限必须给容器里的 mysql 用户 ----
MYSQL_UID=$(docker run --rm --entrypoint id mysql:8.0 -u mysql 2>/dev/null || echo 999)
MYSQL_GID=$(docker run --rm --entrypoint id mysql:8.0 -g mysql 2>/dev/null || echo 999)

if [ -d "$DATA_DIR" ] && [ -n "$(ls -A "$DATA_DIR" 2>/dev/null)" ]; then
  log "复用已有数据目录 $DATA_DIR（$(du -sh "$DATA_DIR" 2>/dev/null | cut -f1)）"
else
  log "初始化数据目录 $DATA_DIR（owner ${MYSQL_UID}:${MYSQL_GID}）"
  mkdir -p "$DATA_DIR"
fi
chown -R "${MYSQL_UID}:${MYSQL_GID}" "$DATA_DIR"
chmod 750 "$DATA_DIR"
log "数据目录：$DATA_DIR -> 容器内 /var/lib/mysql"

if docker compose version >/dev/null 2>&1; then
  log "使用 docker compose 启动"
  docker compose up -d
else
  log "没有 compose 插件，使用 docker run 启动"
  docker rm -f mysql8 >/dev/null 2>&1 || true
  docker run -d --name mysql8 --restart unless-stopped \
    -p "${MYSQL_PORT}:3306" \
    -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
    -e MYSQL_ROOT_HOST="$MYSQL_ROOT_HOST" \
    -e MYSQL_DATABASE="$MYSQL_DATABASE" \
    -e TZ=Asia/Shanghai \
    -v "$DATA_DIR:/var/lib/mysql" \
    -v "$PWD/conf/my.cnf:/etc/mysql/conf.d/my.cnf:ro" \
    -v "$PWD/init:/docker-entrypoint-initdb.d:ro" \
    --ulimit nofile=65535:65535 \
    mysql:8.0 --default-authentication-plugin=mysql_native_password >/dev/null
fi

log "等待 MySQL 初始化完成（首次初始化数据目录需要 30~60 秒）"
for i in $(seq 1 60); do
  if docker exec mysql8 mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent >/dev/null 2>&1; then
    break
  fi
  if [ "$i" -eq 60 ]; then
    docker logs --tail 60 mysql8 >&2 || true
    die "MySQL 60 次探测仍未就绪，日志见上"
  fi
  sleep 2
done

bash ./ensure-app-user.sh

VERSION=$(docker exec mysql8 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "SELECT VERSION()" 2>/dev/null)
HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')

log "MySQL ${VERSION} 已就绪，监听 0.0.0.0:${MYSQL_PORT}"
log "数据落在宿主机：$DATA_DIR（删容器不丢，删目录才丢）"
log "JDBC: jdbc:mysql://${HOST_IP:-<虚拟机IP>}:${MYSQL_PORT}/${MYSQL_DATABASE}?useUnicode=true&characterEncoding=utf-8&autoReconnect=true&useSSL=false&serverTimezone=Asia/Shanghai"
log "业务账号：${MYSQL_APP_USER:-lumenote} / 见 .env"
log "常用命令：docker logs -f mysql8    docker exec -it mysql8 mysql -uroot -p"
