#!/bin/sh
# 一次性推送：token 仅注入内存 URL，不写入 git config（复用 gh-token.mjs 解密）
# tr 去除 bun 输出的尾部换行（曾致 Authentication failed）
TOKEN=$(cd "$(dirname "$0")/.." && bun -e 'import("./scripts/gh-token.mjs").then(m => console.log(m.getGhToken()))' 2>/dev/null | tr -d '\n\r')
exec git push "https://x-access-token:${TOKEN}@github.com/xiaobaiba999/Skip-Ad.git" "$@"
