#!/usr/bin/env bash
# 在已启动的 mysql8 容器里创建业务账号（幂等，可重复执行）
set -euo pipefail

log() { printf '\033[1;32m[mysql-user]\033[0m %s\n' "$*"; }

[ -f .env ] || { echo "缺少 .env（先 cp .env.example .env）" >&2; exit 1; }
set -a; . ./.env; set +a

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD 未设置}"
APP_USER="${MYSQL_APP_USER:-lumenote}"
APP_PW="${MYSQL_APP_PASSWORD:?MYSQL_APP_PASSWORD 未设置}"
DB="${MYSQL_DATABASE:-lumenote}"

case "$APP_PW" in
  *"'"*) echo "MYSQL_APP_PASSWORD 不能包含单引号" >&2; exit 1 ;;
esac

docker exec -i mysql8 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS '${APP_USER}'@'%' IDENTIFIED WITH mysql_native_password BY '${APP_PW}';
ALTER USER '${APP_USER}'@'%' IDENTIFIED WITH mysql_native_password BY '${APP_PW}';
GRANT ALL PRIVILEGES ON ${DB}.* TO '${APP_USER}'@'%';
FLUSH PRIVILEGES;
SQL

log "业务账号 ${APP_USER}@% 已就绪，可访问库 ${DB}"
