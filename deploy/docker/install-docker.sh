#!/usr/bin/env bash
# =============================================================================
# 在 CentOS 7 / Rocky Linux / AlmaLinux 上安装 Docker CE + Compose 插件
# 用法：sudo bash install-docker.sh
# 可选环境变量：
#   DOCKER_CE_REPO   自定义 docker-ce 仓库地址
#   DISABLE_SELINUX  1=把 SELinux 设为 permissive（默认 1，仅开发虚拟机建议）
#   MIRROR_LIST      registry-mirrors，逗号分隔的 JSON 片段（默认用下面内置的两个）
# =============================================================================
set -euo pipefail

LOG_TAG="docker-install"
log()  { printf '\033[1;32m[%s]\033[0m %s\n' "$LOG_TAG" "$*"; }
warn() { printf '\033[1;33m[%s] 警告：\033[0m %s\n' "$LOG_TAG" "$*"; }
die()  { printf '\033[1;31m[%s] 失败：\033[0m %s\n' "$LOG_TAG" "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "请用 root 执行：sudo bash $0"

# ---------------------------------------------------------------- 系统识别 ----
[ -r /etc/os-release ] || die "读不到 /etc/os-release，不支持的系统"
# shellcheck disable=SC1091
. /etc/os-release
OS_ID="${ID:-unknown}"
OS_MAJOR="${VERSION_ID%%.*}"
log "系统：${PRETTY_NAME:-$OS_ID $OS_MAJOR}，架构：$(uname -m)"

case "$OS_ID" in
  centos|rhel|rocky|almalinux|ol) ;;
  *) die "本脚本只覆盖 CentOS/RHEL 系（检测到 $OS_ID）。Ubuntu/Debian 请改用 get.docker.com 脚本。" ;;
esac

DOCKER_CE_REPO="${DOCKER_CE_REPO:-https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo}"
DISABLE_SELINUX="${DISABLE_SELINUX:-1}"

PKG_MGR=yum
command -v dnf >/dev/null 2>&1 && PKG_MGR=dnf
log "包管理器：$PKG_MGR"

# ------------------------------------------------------- CentOS 7 已 EOL ----
# 官方 mirror.centos.org 已下线，不切 vault 源会直接 yum 报 404。
if [ "$OS_ID" = "centos" ] && [ "$OS_MAJOR" = "7" ]; then
  # 官方 mirror.centos.org / mirrorlist.centos.org 已下线，vault.centos.org 在国内常被 403。
  # 按顺序探测可用归档镜像，第一个能取到 repomd.xml 的胜出。
  if [ -z "${CENTOS_VAULT_BASE:-}" ]; then
    for m in http://mirrors.aliyun.com/centos-vault \
             http://mirrors.tuna.tsinghua.edu.cn/centos-vault \
             http://vault.centos.org; do
      if curl -fsS -o /dev/null --max-time 8 "$m/7.9.2009/os/x86_64/repodata/repomd.xml" 2>/dev/null; then
        CENTOS_VAULT_BASE="$m"; break
      fi
    done
  fi
  [ -n "${CENTOS_VAULT_BASE:-}" ] || die "找不到可用的 CentOS 7 归档源（aliyun/tuna/vault 都不通）"
  log "CentOS 7 归档源：$CENTOS_VAULT_BASE"

  if ! ls /etc/yum.repos.d/CentOS-Base.repo >/dev/null 2>&1; then
    log "未找到 CentOS-Base.repo（可能是离线安装），直接写一份基础源"
  else
    BACKUP_DIR="/etc/yum.repos.d/backup-$(date +%Y%m%d%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    mv /etc/yum.repos.d/CentOS-*.repo "$BACKUP_DIR"/ 2>/dev/null || true
    log "旧源已备份到 $BACKUP_DIR"
  fi

  # 直接重写源文件：老源里用 $releasever 拼 mirror.centos.org，已整体失效；
  # 归档源路径固定含 7.9.2009，写死比 sed 替换可靠。
  cat > /etc/yum.repos.d/CentOS-Base.repo <<REPO
[base]
name=CentOS-7.9.2009 - Base
baseurl=$CENTOS_VAULT_BASE/7.9.2009/os/\$basearch/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-CentOS-7

[updates]
name=CentOS-7.9.2009 - Updates
baseurl=$CENTOS_VAULT_BASE/7.9.2009/updates/\$basearch/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-CentOS-7

[extras]
name=CentOS-7.9.2009 - Extras
baseurl=$CENTOS_VAULT_BASE/7.9.2009/extras/\$basearch/
gpgcheck=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-CentOS-7
REPO

  rpm --import /etc/pki/rpm-gpg/RPM-GPG-KEY-CentOS-7 2>/dev/null || true
  $PKG_MGR clean all >/dev/null 2>&1 || true
fi

# ------------------------------------------------------------- SELinux ----
# 容器挂载宿主机目录时，Enforcing 模式会让 MySQL 读不到 init 脚本，排查成本高。
if command -v getenforce >/dev/null 2>&1 && [ "$(getenforce)" = "Enforcing" ]; then
  if [ "$DISABLE_SELINUX" = "1" ]; then
    warn "SELinux 为 Enforcing，按开发虚拟机惯例改为 permissive（DISABLE_SELINUX=0 可关闭此行为）"
    setenforce 0 || true
    sed -i 's/^SELINUX=enforcing/SELINUX=permissive/' /etc/selinux/config 2>/dev/null || true
  else
    warn "SELinux 为 Enforcing 且未自动关闭，若 MySQL 挂载目录报权限错误请手动处理"
  fi
fi

# ---------------------------------------------------------- 安装依赖包 ----
if [ "$PKG_MGR" = "dnf" ]; then
  DEPS="dnf-plugins-core device-mapper-persistent-data lvm2 ca-certificates curl"
else
  DEPS="yum-utils device-mapper-persistent-data lvm2 ca-certificates curl"
fi
log "安装基础依赖：$DEPS"
# shellcheck disable=SC2086
$PKG_MGR install -y $DEPS >/dev/null

# 已装过 docker.io / podman-docker 的机器会和 docker-ce 冲突，先清掉
if rpm -q docker-ce >/dev/null 2>&1; then
  log "检测到已安装 docker-ce，跳过仓库配置"
else
  for old in docker docker-client docker-client-latest docker-common docker-latest \
             docker-latest-logrotate docker-logrotate docker-engine podman-docker; do
    rpm -q "$old" >/dev/null 2>&1 && $PKG_MGR remove -y "$old" >/dev/null && log "移除冲突包 $old"
  done

  log "配置 docker-ce 仓库（阿里云镜像）"
  ADDED=0
  if [ "$PKG_MGR" = "dnf" ]; then
    if dnf config-manager --add-repo "$DOCKER_CE_REPO" >/dev/null 2>&1; then ADDED=1; fi
  else
    if yum-config-manager --add-repo "$DOCKER_CE_REPO" >/dev/null 2>&1; then ADDED=1; fi
  fi
  if [ "$ADDED" = "0" ]; then
    curl -fsSL --max-time 30 "$DOCKER_CE_REPO" -o /etc/yum.repos.d/docker-ce.repo \
      || die "下载 docker-ce.repo 失败，网络能到 mirrors.aliyun.com 吗？"
  fi
  sed -i 's|download.docker.com|mirrors.aliyun.com/docker-ce|g' /etc/yum.repos.d/docker-ce.repo || true
  $PKG_MGR makecache -y >/dev/null 2>&1 || true

  log "安装 docker-ce / cli / containerd"
  $PKG_MGR install -y docker-ce docker-ce-cli containerd.io

  # 插件包在部分 el7 仓库里缺失，装不上不影响跑容器（compose 有 docker run 兜底）
  log "安装 buildx / compose 插件（失败不致命）"
  $PKG_MGR install -y docker-buildx-plugin docker-compose-plugin \
    || warn "compose/buildx 插件不可用，启动 MySQL 时会退回 docker run"
fi

# ------------------------------------------------------------ daemon.json ----
# 国内直连 Docker Hub 常超时，先配好加速镜像；镜像站时效性会变，可自行替换。
mkdir -p /etc/docker
if [ ! -f /etc/docker/daemon.json ]; then
  log "写入 /etc/docker/daemon.json"
  cat > /etc/docker/daemon.json <<'JSON'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "100m", "max-file": "3" },
  "live-restore": true,
  "exec-opts": ["native.cgroupdriver=systemd"]
}
JSON
else
  log "/etc/docker/daemon.json 已存在，保持不动"
fi

# --------------------------------------------------------------- 启动服务 ----
if systemctl is-enabled --quiet firewalld 2>/dev/null && systemctl is-active --quiet firewalld; then
  log "firewalld 正在运行，放行 MySQL 3306（docker0 交给 Docker 自己的 docker 域管理）"
  firewall-cmd --permanent --add-port=3306/tcp >/dev/null || true
  # 曾把 docker0 塞进 trusted 域的机器会和 Docker 的 firewalld 集成冲突：
  # dockerd 报 ZONE_CONFLICT: 'docker0' already bound to a zone 直接起不来。
  STALE_ZONES=$(firewall-cmd --permanent --get-zones 2>/dev/null || echo "")
  for z in $STALE_ZONES; do
    if firewall-cmd --permanent --zone="$z" --list-interfaces 2>/dev/null | tr ' ' '\n' | grep -qx docker0; then
      warn "docker0 被绑定在 $z 域，移除（否则 dockerd 启动会 ZONE_CONFLICT）"
      firewall-cmd --permanent --zone="$z" --remove-interface=docker0 >/dev/null 2>&1 || true
      firewall-cmd --zone="$z" --remove-interface=docker0 >/dev/null 2>&1 || true
    fi
  done
  firewall-cmd --reload >/dev/null || true
fi

log "启动 Docker 并设置开机自启"
systemctl daemon-reload
systemctl reset-failed docker 2>/dev/null || true
systemctl enable --now docker >/dev/null

sleep 2
docker info >/dev/null 2>&1 || die "Docker 启动了但 docker info 失败，检查 journalctl -u docker"

# ------------------------------------------------------------- compose 兜底 ----
if docker compose version >/dev/null 2>&1; then
  log "Compose 插件可用：$(docker compose version --short 2>/dev/null || echo ok)"
else
  warn "docker compose 插件不可用，尝试安装 v2 standalone 二进制"
  ck=$(curl -fsSL --max-time 20 https://get.daocloud.io/docker/compose/releases/latest/download/docker-compose-Linux-x86_64 2>/dev/null || true)
  if [ -n "$ck" ]; then
    mkdir -p /usr/local/lib/docker/cli-plugins
    printf '%s' "$ck" > /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    log "Compose 已安装到 /usr/local/lib/docker/cli-plugins"
  else
    warn "Compose 下载失败，后续启动 MySQL 会退回 docker run 方式"
  fi
fi

log "完成：Docker $(docker version --format '{{.Server.Version}}' 2>/dev/null || echo '?')"
log "提示：如需非 root 使用 docker，执行 usermod -aG docker <用户名> 后重新登录"
