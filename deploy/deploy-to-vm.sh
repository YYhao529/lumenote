#!/usr/bin/env bash
# =============================================================================
# 把 deploy/ 传到虚拟机，装 Docker + 起 MySQL8 / Redis / RabbitMQ / Nacos / extras
# （extras = MinIO + Elasticsearch + Cassandra + Canal + xxl-job），并自检。
#
# 用法（Git Bash 或 PowerShell 里的 bash）：
#   bash deploy-to-vm.sh root@192.168.100.128
#   bash deploy-to-vm.sh root@192.168.100.128 --port 2222 --key ~/.ssh/id_ed25519
#   bash deploy-to-vm.sh root@192.168.100.128 --skip-docker          # 已有 docker
#   bash deploy-to-vm.sh root@192.168.100.128 --askpass              # 无 tty，用 DSH_SSH_PW 登录
#   bash deploy-to-vm.sh root@192.168.100.128 --skip-extras --skip-nacos   # 只补基础三件套
#
# 前提：--askpass 之外需要密钥免密登录（脚本会在失败时打印做法）。
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"

TARGET=""
PORT=22
KEY=""
SKIP_DOCKER=0
SKIP_REDIS=0
SKIP_RABBITMQ=0
SKIP_NACOS=0
SKIP_EXTRAS=0
ROOT_PW=""
APP_PW=""
ASKPASS=0
REMOTE_DIR=/opt/lumenote/deploy

log()  { printf '\033[1;36m[deploy]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[deploy] 失败：\033[0m %s\n' "$*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --port)          PORT="$2"; shift 2 ;;
    --key)           KEY="$2"; shift 2 ;;
    --skip-docker)   SKIP_DOCKER=1; shift ;;
    --skip-redis)    SKIP_REDIS=1; shift ;;
    --skip-rabbitmq) SKIP_RABBITMQ=1; shift ;;
    --skip-nacos)    SKIP_NACOS=1; shift ;;
    --skip-extras)   SKIP_EXTRAS=1; shift ;;
    --askpass)       ASKPASS=1; shift ;;
    --root-password) ROOT_PW="$2"; shift 2 ;;
    --app-password)  APP_PW="$2"; shift 2 ;;
    -h|--help)       sed -n '2,12p' "$0"; exit 0 ;;
    -*)              die "未知参数 $1" ;;
    *)               TARGET="$1"; shift ;;
  esac
done

[ -n "$TARGET" ] || die "缺少目标，例如：bash deploy-to-vm.sh root@192.168.100.128"

SSH_OPTS=(-o ConnectTimeout=10 -o StrictHostKeyChecking=accept-new -p "$PORT")
if [ "$ASKPASS" = "1" ]; then
  [ -n "${DSH_SSH_PW:-}" ] || die "--askpass 需要环境变量 DSH_SSH_PW"
  SSH_OPTS+=(-o PreferredAuthentications=password -o PubkeyAuthentication=no -o NumberOfPasswordPrompts=1)
else
  SSH_OPTS+=(-o BatchMode=yes)
fi
[ -n "$KEY" ] && SSH_OPTS+=(-i "$KEY")
REMOTE="${TARGET#*@}"

sshx() { ssh "${SSH_OPTS[@]}" "$TARGET" "$@"; }

log "1/10 检查 SSH 连通性：$TARGET"
if ! sshx 'echo connected' >/dev/null 2>&1; then
  cat >&2 <<EOF
无法免密登录 $TARGET。先在虚拟机外执行一次（会提示输入虚拟机密码）：

  ssh-keygen -t ed25519 -N "" -f ~/.ssh/id_ed25519
  ssh-copy-id -i ~/.ssh/id_ed25519.pub $TARGET

Windows 没有 ssh-copy-id 时用 PowerShell：
  type \$env:USERPROFILE\\.ssh\\id_ed25519.pub | ssh $TARGET "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys"
EOF
  exit 1
fi

log "    远端：$(sshx '. /etc/os-release 2>/dev/null && echo "$PRETTY_NAME" || echo unknown') / $(sshx 'uname -m')"

SUDO=""
if [ "$(sshx 'id -u')" != "0" ]; then
  sshx 'sudo -n true' >/dev/null 2>&1 || die "当前用户非 root 且 sudo 需要密码"
  SUDO="sudo"
fi

log "2/10 上传文件到 $REMOTE_DIR"
sshx "mkdir -p $REMOTE_DIR"
tar czf - docker mysql8 redis rabbitmq nacos extras | ssh "${SSH_OPTS[@]}" "$TARGET" "tar xzf - -C $REMOTE_DIR"
sshx "chmod +x $REMOTE_DIR/docker/install-docker.sh $REMOTE_DIR/mysql8/*.sh $REMOTE_DIR/redis/*.sh $REMOTE_DIR/rabbitmq/*.sh $REMOTE_DIR/nacos/*.sh $REMOTE_DIR/extras/*.sh"

if [ "$SKIP_DOCKER" = "1" ]; then
  log "3/10 跳过 Docker 安装（--skip-docker）"
else
  log "3/10 安装 Docker CE（1~3 分钟）"
  sshx "$SUDO bash $REMOTE_DIR/docker/install-docker.sh" 2>&1 | sed 's/^/    /'
fi

log "4/10 生成 MySQL 配置 .env"
sshx "$SUDO bash $REMOTE_DIR/mysql8/env-setup.sh '$ROOT_PW' '$APP_PW'" 2>&1 | sed 's/^/    /'

log "5/10 启动 MySQL 8"
sshx "$SUDO bash $REMOTE_DIR/mysql8/start-mysql.sh" 2>&1 | sed 's/^/    /'

if [ "$SKIP_REDIS" = "1" ]; then log "6/10 跳过 Redis"; else
  log "6/10 启动 Redis"
  sshx "$SUDO bash $REMOTE_DIR/redis/start-redis.sh" 2>&1 | sed 's/^/    /'
fi

if [ "$SKIP_RABBITMQ" = "1" ]; then log "7/10 跳过 RabbitMQ"; else
  log "7/10 启动 RabbitMQ"
  sshx "$SUDO bash $REMOTE_DIR/rabbitmq/start-rabbitmq.sh" 2>&1 | sed 's/^/    /'
fi

if [ "$SKIP_NACOS" = "1" ]; then log "8/10 跳过 Nacos"; else
  log "8/10 启动 Nacos"
  sshx "$SUDO bash $REMOTE_DIR/nacos/start-nacos.sh" 2>&1 | sed 's/^/    /'
fi

if [ "$SKIP_EXTRAS" = "1" ]; then log "9/10 跳过 extras"; else
  log "9/10 启动 extras（MinIO / ES / Cassandra / Canal / xxl-job，镜像较大）"
  sshx "$SUDO bash $REMOTE_DIR/extras/start-extras.sh" 2>&1 | sed 's/^/    /'
fi

log "10/10 自检"
set +e
sshx "bash $REMOTE_DIR/mysql8/check.sh" 2>&1 | sed 's/^/    /'; CHECK_RC=$?
REDIS_RC=0; RABBIT_RC=0; NACOS_RC=0; EXTRAS_RC=0
[ "$SKIP_REDIS" != "1" ] && { echo; sshx "bash $REMOTE_DIR/redis/check.sh" 2>&1 | sed 's/^/    /'; REDIS_RC=$?; }
[ "$SKIP_RABBITMQ" != "1" ] && { echo; sshx "bash $REMOTE_DIR/rabbitmq/check.sh" 2>&1 | sed 's/^/    /'; RABBIT_RC=$?; }
[ "$SKIP_NACOS" != "1" ] && { echo; sshx "bash $REMOTE_DIR/nacos/check.sh" 2>&1 | sed 's/^/    /'; NACOS_RC=$?; }
[ "$SKIP_EXTRAS" != "1" ] && { echo; sshx "bash $REMOTE_DIR/extras/check-extras.sh" 2>&1 | sed 's/^/    /'; EXTRAS_RC=$?; }
set -e

verdict() { [ "$1" = "0" ] && echo '通过' || echo '有异常'; }
sv() { [ "$2" = "1" ] && echo '跳过' || verdict "$1"; }

cat <<EOF

$(printf '\033[1;32m部署完成\033[0m')：${REMOTE}

  MySQL    3306   root/1234
  Redis    6379   无密码
  RabbitMQ 5672   vhost /lumenote　deepsea/1234　管理台 :15672
  Nacos    8848   namespace lumenote（免登录）
  MinIO    9000   控制台 :9001　deepsea / 见 .env　桶 deepsea-lumenote
  ES       9200   7.17.x（xpack 关闭）
  Cassandra 9042  keyspace lumenote
  Canal    11111  destination example（订阅 lumenote.* binlog）
  xxl-job  7777   /xxl-job-admin　accessToken default_token

  自检：MySQL $(verdict "$CHECK_RC")　Redis $(sv "$REDIS_RC" "$SKIP_REDIS")　RabbitMQ $(sv "$RABBIT_RC" "$SKIP_RABBITMQ")　Nacos $(sv "$NACOS_RC" "$SKIP_NACOS")　extras $(sv "$EXTRAS_RC" "$SKIP_EXTRAS")

  运维
    ssh ${TARGET} 'docker ps'
    ssh ${TARGET} 'docker logs -f elasticsearch'
    ssh ${TARGET} 'bash ${REMOTE_DIR}/extras/check-extras.sh'

  数据目录（都在宿主机，删容器不丢）
    /opt/lumenote/{mysql8,redis,rabbitmq,minio,es,cassandra}/data　/opt/lumenote/nacos/{logs,data}
EOF

[ "$CHECK_RC" -eq 0 ] && [ "$REDIS_RC" -eq 0 ] && [ "$RABBIT_RC" -eq 0 ] && [ "$NACOS_RC" -eq 0 ] && [ "$EXTRAS_RC" -eq 0 ]
