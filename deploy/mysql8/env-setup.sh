#!/usr/bin/env bash
# 生成/更新 mysql8/.env（幂等）。可传参覆盖密码：
#   bash env-setup.sh 'root密码' '业务账号密码'
set -euo pipefail
cd "$(dirname "$0")"

ROOT_PW="${1:-}"
APP_PW="${2:-}"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "已从 .env.example 生成 .env"
fi

if [ -n "$ROOT_PW" ]; then
  sed -i "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=${ROOT_PW}|" .env
fi
if [ -n "$APP_PW" ]; then
  sed -i "s|^MYSQL_APP_PASSWORD=.*|MYSQL_APP_PASSWORD=${APP_PW}|" .env
fi

chmod 600 .env
echo ".env 就绪（权限 600）：$(grep -E '^MYSQL_(ROOT_HOST|PORT|DATABASE)=' .env | tr '\n' ' ')"
