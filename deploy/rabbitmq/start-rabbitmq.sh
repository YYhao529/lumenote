#!/usr/bin/env bash
# =============================================================================
# 在 Docker 里启动 RabbitMQ（management 插件），并幂等创建项目要的 vhost / 业务账号
# 用法：cd deploy/rabbitmq && bash start-rabbitmq.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

log()  { printf '\033[1;32m[rabbitmq]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[rabbitmq] 注意：\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[rabbitmq] 失败：\033[0m %s\n' "$*" >&2; exit 1; }

[ -f .env ] || { cp .env.example .env; log ".env 不存在，已从 .env.example 生成"; }
set -a; . ./.env; set +a

IMAGE="rabbitmq:${RABBITMQ_VERSION:-3.13-management-alpine}"
PORT="${RABBITMQ_PORT:-5672}"
MGMT_PORT="${RABBITMQ_MGMT_PORT:-15672}"
DATA_DIR="${RABBITMQ_DATA_DIR:-/opt/lumenote/rabbitmq/data}"
APP_USER="${RABBITMQ_APP_USER:-deepsea}"
APP_PASS="${RABBITMQ_APP_PASS:?请在 .env 设置 RABBITMQ_APP_PASS}"
VHOST="${RABBITMQ_VHOST:-/lumenote}"

command -v docker >/dev/null 2>&1 || die "未安装 docker，先跑 deploy/docker/install-docker.sh"
docker info >/dev/null 2>&1 || die "Docker 服务没运行：systemctl start docker"

log "拉取 $IMAGE"
docker pull "$IMAGE" >/dev/null || die "镜像拉取失败：docker pull $IMAGE 看具体报错"

# 数据目录属主给容器里的 rabbitmq 用户
RMQ_UID=$(docker run --rm --entrypoint id "$IMAGE" -u rabbitmq 2>/dev/null || echo 999)
RMQ_GID=$(docker run --rm --entrypoint id "$IMAGE" -g rabbitmq 2>/dev/null || echo 999)
mkdir -p "$DATA_DIR"
chown -R "${RMQ_UID}:${RMQ_GID}" "$DATA_DIR"
chmod 750 "$DATA_DIR"
log "数据目录：$DATA_DIR -> 容器内 /var/lib/rabbitmq（owner ${RMQ_UID}:${RMQ_GID}）"

if systemctl is-active --quiet firewalld 2>/dev/null; then
  log "放行 ${PORT}/tcp 和 ${MGMT_PORT}/tcp（firewalld）"
  firewall-cmd --permanent --add-port="${PORT}/tcp" >/dev/null 2>&1 || true
  firewall-cmd --permanent --add-port="${MGMT_PORT}/tcp" >/dev/null 2>&1 || true
  firewall-cmd --reload >/dev/null 2>&1 || true
fi

if docker compose version >/dev/null 2>&1; then
  log "使用 docker compose 启动"
  docker compose up -d
else
  log "没有 compose 插件，使用 docker run 启动"
  docker rm -f rabbitmq >/dev/null 2>&1 || true
  docker run -d --name rabbitmq --hostname rabbitmq --restart unless-stopped \
    -p "${PORT}:5672" -p "${MGMT_PORT}:15672" \
    -e RABBITMQ_DEFAULT_USER="${RABBITMQ_DEFAULT_USER:-admin}" \
    -e RABBITMQ_DEFAULT_PASS="${RABBITMQ_DEFAULT_PASS:?}" \
    -e TZ=Asia/Shanghai \
    -v "$DATA_DIR:/var/lib/rabbitmq" \
    -v "$PWD/conf/rabbitmq.conf:/etc/rabbitmq/conf.d/99-lumenote.conf:ro" \
    --ulimit nofile=65535:65535 \
    "$IMAGE" >/dev/null
fi

log "等待 RabbitMQ 就绪"
# 注意：rabbitmq-diagnostics ping 只验证 Erlang 节点可达，此时 rabbit 应用可能还没起来，
# 后续 rabbitmqctl add_vhost 会报 "requires the 'rabbit' app to be running"。
# check_running 才是等 rabbit 应用真正启动。
for i in $(seq 1 90); do
  if docker exec rabbitmq rabbitmq-diagnostics -q check_running >/dev/null 2>&1; then break; fi
  if [ "$i" -eq 90 ]; then
    docker logs --tail 60 rabbitmq >&2 || true
    die "RabbitMQ 180 秒内 rabbit 应用仍未启动"
  fi
  sleep 2
done

# ---- vhost / 业务账号（幂等，不依赖 definitions 文件，所以密码不用预先生成 hash）----
log "确保 vhost ${VHOST}"
if ! docker exec rabbitmq rabbitmqctl -q list_vhosts name 2>/dev/null | grep -qx "$VHOST"; then
  docker exec rabbitmq rabbitmqctl add_vhost "$VHOST" >/dev/null
  log "已创建 vhost $VHOST"
else
  log "vhost 已存在"
fi

log "确保业务账号 ${APP_USER}"
# 注意：3.13 的 `list_users` 不接受列名参数（传了会 "too many arguments"），
# 直接取第一列（name<TAB>tags）
if ! docker exec rabbitmq rabbitmqctl -q list_users 2>/dev/null | cut -f1 | grep -qx "$APP_USER"; then
  docker exec rabbitmq rabbitmqctl add_user "$APP_USER" "$APP_PASS" >/dev/null
  log "已创建用户 $APP_USER"
else
  docker exec rabbitmq rabbitmqctl change_password "$APP_USER" "$APP_PASS" >/dev/null
  log "用户已存在，密码已同步为 .env 里的值"
fi
docker exec rabbitmq rabbitmqctl set_permissions -p "$VHOST" "$APP_USER" ".*" ".*" ".*" >/dev/null
docker exec rabbitmq rabbitmqctl set_user_tags "$APP_USER" administrator >/dev/null
log "权限已配置：${APP_USER} 在 ${VHOST} 上可读写配置"

VERSION=$(docker exec rabbitmq rabbitmqctl -q status 2>/dev/null | grep -E "^RabbitMQ version" | awk '{print $NF}')
HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
log "RabbitMQ ${VERSION} 已就绪，AMQP ${HOST_IP:-<虚拟机IP>}:${PORT}，管理后台 http://${HOST_IP:-<虚拟机IP>}:${MGMT_PORT}"
log "项目账号：${APP_USER} / 见 .env　vhost：${VHOST}"
