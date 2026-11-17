#!/bin/sh
# GIT_ASKPASS 凭据助手：用户名返回占位，密码输出令牌。
# 令牌来源优先级：GITHUB_TOKEN 环境变量 → scripts/.github-token 文件。
# （.github-token 已在 .gitignore，绝不入库；历史版本曾调用
#   /home/z/my-project/scripts/print-token.ts，现已内联化，无外部依赖。）
case "$1" in
  *sername*) echo "x-access-token" ;;
  *)
    if [ -n "$GITHUB_TOKEN" ]; then
      echo "$GITHUB_TOKEN"
    elif [ -f "$(dirname "$0")/.github-token" ]; then
      cat "$(dirname "$0")/.github-token"
    else
      echo ""
    fi
    ;;
esac
