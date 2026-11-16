#!/bin/sh
# GIT_ASKPASS 凭据助手：用户名返回占位，密码输出服务端解密的内置令牌
case "$1" in
  *sername*) echo "x-access-token" ;;
  *) exec bun /home/z/my-project/scripts/print-token.ts ;;
esac
