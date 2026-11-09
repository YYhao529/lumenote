#!/usr/bin/env bash
# =============================================================================
# 在 Docker 里启动 Redis（AOF 持久化，数据绑定挂载到宿主机目录）
# 用法：cd deploy/redis && bash start-redis.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

log()  { printf '\033[1;32m[redis]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[redis] 注意：\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[redis] 失败：\033[0m %s\n' "$*" >&2; exit 1; }

[ -f .env ] || { cp .env.example .env; log ".env 不存在，已从 .env.example 生成"; }
set -a; . ./.env; set +a

IMAGE="redis:${REDIS_VERSION:-7.4-alpine}"
PORT="${REDIS_PORT:-6379}"
DATA_DIR="${REDIS_DATA_DIR:-/opt/lumenote/redis/data}"

command -v docker >/dev/null 2>&1 || die "未安装 docker，先跑 deploy/docker/install-docker.sh"
docker info >/dev/null 2>&1 || die "Docker 服务没运行：systemctl start docker"

log "拉取 $IMAGE"
docker pull "$IMAGE" >/dev/null || die "镜像拉取失败：docker pull $IMAGE 看具体报错"

# 数据目录属主给容器里的 redis 用户（不同基础镜像 uid 可能不同，直接问镜像）
REDIS_UID=$(docker run --rm --entrypoint id "$IMAGE" -u redis 2>/dev/null || echo 999)
REDIS_GID=$(docker run --rm --entrypoint id "$IMAGE" -g redis 2>/dev/null || echo 999)
mkdir -p "$DATA_DIR"
chown -R "${REDIS_UID}:${REDIS_GID}" "$DATA_DIR"
chmod 750 "$DATA_DIR"
log "数据目录：$DATA_DIR -> 容器内 /data（owner ${REDIS_UID}:${REDIS_GID}）"

if systemctl is-active --quiet firewalld 2>/dev/null; then
  log "放行 ${PORT}/tcp（firewalld）"
  firewall-cmd --permanent --add-port="${PORT}/tcp" >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
fi

if docker compose version >/dev/null 2>&1; then
  log "使用 docker compose 启动"
  docker compose up -d
else
  log "没有 compose 插件，使用 docker run 启动"
  docker rm -f redis >/dev/null 2>&1 || true
  docker run -d --name redis --restart unless-stopped \
    -p "${PORT}:6379" \
    -v "$DATA_DIR:/data" \
    -v "$PWD/conf/redis.conf:/usr/local/etc/redis/redis.conf:ro" \
    "$IMAGE" redis-server /usr/local/etc/redis/redis.conf >/dev/null
fi

log "等待 Redis 就绪"
for i in $(seq 1 30); do
  PONG=$(docker exec redis redis-cli ping 2>/dev/null || true)
  [ "$PONG" = "PONG" ] && break
  if [ "$i" -eq 30 ]; then
    docker logs --tail 40 redis >&2 || true
    die "Redis 60 秒未响应 PING"
  fi
  sleep 2
done

VERSION=$(docker exec redis redis-cli INFO server 2>/dev/null | grep -E "^redis_version" | tr -d '\r' | cut -d: -f2)
HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
log "Redis ${VERSION} 已就绪，监听 0.0.0.0:${PORT}（未设密码，仅开发环境）"
log "连接串：spring.data.redis.host=${HOST_IP:-<虚拟机IP>} port=${PORT} password=空"
log "持久化：AOF 每秒钟刷盘，数据在 ${DATA_DIR}"
