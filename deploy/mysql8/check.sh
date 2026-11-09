#!/usr/bin/env bash
# 部署后自检：容器状态、端口、版本、业务账号登录、字符集。
#   bash check.sh            # 在 deploy/mysql8 目录执行
set -uo pipefail
cd "$(dirname "$0")"

ok=0; bad=0
pass() { printf '  \033[1;32m✓\033[0m %s\n' "$*"; ok=$((ok+1)); }
fail() { printf '  \033[1;31m✗\033[0m %s\n' "$*"; bad=$((bad+1)); }

[ -f .env ] || { echo "缺少 .env"; exit 1; }
set -a; . ./.env; set +a

ROOT_PW="${MYSQL_ROOT_PASSWORD:-}"
APP_USER="${MYSQL_APP_USER:-lumenote}"
APP_PW="${MYSQL_APP_PASSWORD:-}"
DB="${MYSQL_DATABASE:-lumenote}"
PORT="${MYSQL_PORT:-3306}"

echo "== 容器 =="
if docker ps --format '{{.Names}}|{{.Status}}' | grep -q '^mysql8|'; then
  pass "mysql8 运行中：$(docker ps --filter name=mysql8 --format '{{.Status}}')"
else
  fail "mysql8 未运行（docker ps -a | grep mysql8）"
fi

echo "== 端口 =="
if command -v ss >/dev/null 2>&1; then
  ss -lnt "( sport = :${PORT} )" 2>/dev/null | grep -q LISTEN \
    && pass "宿主机 ${PORT} 已监听" || fail "宿主机 ${PORT} 未监听"
fi

echo "== 版本与参数 =="
if VER=$(docker exec mysql8 mysql -uroot -p"$ROOT_PW" -N -B -e "SELECT VERSION()" 2>/dev/null); then
  pass "MySQL $VER"
  docker exec mysql8 mysql -uroot -p"$ROOT_PW" -N -B -e \
    "SELECT CONCAT('charset=', @@character_set_server, ' collation=', @@collation_server, ' tz=', @@time_zone)" 2>/dev/null \
    | sed 's/^/    /'
else
  fail "root 无法登录（密码与 .env 一致？docker logs mysql8 看启动日志）"
fi

echo "== 业务账号 =="
if docker exec mysql8 mysql -h127.0.0.1 -u"$APP_USER" -p"$APP_PW" -N -B -e "SELECT 1" >/dev/null 2>&1; then
  pass "${APP_USER}@127.0.0.1 登录成功"
else
  fail "${APP_USER} 登录失败（bash ensure-app-user.sh 补建）"
fi

echo "== 数据库 =="
if docker exec mysql8 mysql -uroot -p"$ROOT_PW" -N -B -e "SHOW DATABASES LIKE '${DB}'" 2>/dev/null | grep -qx "$DB"; then
  pass "库 ${DB} 存在"
else
  fail "库 ${DB} 不存在"
fi

echo "== 数据落盘（宿主机目录）=="
DATA_DIR="${MYSQL_DATA_DIR:-/opt/lumenote/mysql8/data}"
if [ -d "$DATA_DIR" ]; then
  pass "目录 $DATA_DIR（$(du -sh "$DATA_DIR" 2>/dev/null | cut -f1)，owner $(stat -c '%U:%G' "$DATA_DIR" 2>/dev/null)）"
  if docker inspect mysql8 --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Source}}{{end}}{{end}}' 2>/dev/null | grep -qx "$DATA_DIR"; then
    pass "挂载点已指向该目录（不是容器内部匿名卷）"
  else
    fail "容器 /var/lib/mysql 未指向 $DATA_DIR，检查 compose/.env 后重建容器"
  fi
  [ -f "$DATA_DIR/ibdata1" ] && pass "检测到 ibdata1，数据确实在宿主机上" || fail "未找到 ibdata1（首次启动可能尚未完成）"
else
  fail "数据目录 $DATA_DIR 不存在"
fi

echo "== 结果：${ok} 项通过，${bad} 项异常 =="
[ "$bad" -eq 0 ]
