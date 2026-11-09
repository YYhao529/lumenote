#!/usr/bin/env bash
# SSH_ASKPASS 垫片：ssh 在无 tty 环境下要通过它取密码。
# 密码不放这里，从环境变量 DSH_SSH_PW 读，调用时随命令行传入即可。
printf '%s\n' "${DSH_SSH_PW:-}"
