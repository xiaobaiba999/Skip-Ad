#!/bin/sh
# ─────────────────────────────────────────────────────────────
# 密钥泄露扫描硬门（v7.9.4 安全整改新增）
# ─────────────────────────────────────────────────────────────
# 目的：任何令牌/私钥/签名材料再次进入被 git 跟踪的文件时，
#       在测试与构建两道闸口直接拒绝（退出非零）。
# 用法: ./scripts/secret-scan.sh   （run-tests.sh 与 build.sh 均已接线）
# 检查面：
#   ① 被 git 跟踪的文件中扫常见令牌形态（ghp_/github_pat_/AKIA/私钥头/
#      x-access-token URL 凭据对）；
#   ② src/lib/credentials.ts 结构红线：不得再出现内嵌凭据形态
#      （Base64 长串 / KEY_* 字符数组 / XOR 解码器）；
#   ③ local-gh-token（本机未入库凭据文件）绝不允许被 git 跟踪；
#   ④ android-app/keystore 之外不得新增 keystore/*.jks/*.keystore 材料；
#   ⑤ 发布令牌金库完整性（v7.15.4 新增）：scripts/release-token-vault.mjs
#      必须在场且密文编解码互逆（所有者指令「令牌密文永久固化入库」的
#      防写坏闸；密文纯 Base64 无下划线，结构性不命中 ① 的明文形态）。
# 政策注（v7.15.4）：密文金库与 Net.java builtInToken 同风险等级，由仓库
#   所有者明示背书入库；①②的「明文/混淆形态零容忍」红线不变。
# 退出码：0 = 干净；1 = 发现泄露形态（输出明细）。
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
FAIL=0

echo "==> 密钥泄露扫描（secret-scan）"

# ① 令牌形态扫描（仅被跟踪文件——工作区未入库文件不在扫描面）
#    v8.2.1 精度修正：私钥模式要求完整 PEM 铠甲（-----BEGIN … KEY-----，
#    含首尾破折号）——真实泄露的 PEM 必然带铠甲（零漏报）；而密钥处理
#    代码中的格式名字符串（如 worker/src/update.ts 的 PEM 解析）不带铠甲，
#    不再误报（v8.2.0 轮该误报被掩盖：worker/ 在全量测试跑时未跟踪）。
if git ls-files -z | xargs -0 rg -l --no-messages \
  -e 'ghp_[A-Za-z0-9]{30,}' \
  -e 'github_pat_[A-Za-z0-9_]{60,}' \
  -e 'AKIA[0-9A-Z]{16}' \
  -e '\-\-\-\-\-BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' \
  -e 'x-access-token:[A-Za-z0-9_]{20,}' \
  > /tmp/secret-scan-hits.txt 2>/dev/null; then
  # rg -l 命中时退出码为 0 且列表非空
  if [ -s /tmp/secret-scan-hits.txt ]; then
    echo "  ❌ 令牌形态命中（被跟踪文件）:"
    sed 's/^/     /' /tmp/secret-scan-hits.txt
    FAIL=1
  fi
fi

# ② credentials.ts 结构红线（混淆内嵌凭据的历史形态不得复活）
CRED="src/lib/credentials.ts"
if [ -f "$CRED" ]; then
  if rg -n -e '"[A-Za-z0-9+/]{40,}={0,2}"' -e 'KEY_[A-Z]\s*=\s*\[' -e 'xorWith' "$CRED" >/dev/null 2>&1; then
    echo "  ❌ $CRED 出现内嵌/混淆凭据形态（Base64 长串 / KEY 数组 / XOR 解码器）"
    FAIL=1
  fi
fi

# ③ local-gh-token 绝不入库
if git ls-files --error-unmatch local-gh-token >/dev/null 2>&1; then
  echo "  ❌ local-gh-token 被 git 跟踪（本机凭据文件必须保持未入库）"
  FAIL=1
fi

# ④ keystore 材料面：除既有签名目录外不得新增散落 keystore/jks
KS_HITS=$(git ls-files | rg -v '^android-app/keystore/' | rg '\.(jks|keystore)$' || true)
if [ -n "$KS_HITS" ]; then
  echo "  ❌ keystore 材料散落（应只在 android-app/keystore/）:"
  echo "$KS_HITS" | sed 's/^/     /'
  FAIL=1
fi

# ⑤ 发布令牌金库完整性（v7.15.4：所有者指令「令牌密文永久固化入库」的防写坏闸）
#    密文必须可解码 + 形态合法 + 编解码互逆（零网络——活性探测归
#    check-release-readiness.mjs 的 C 闸，发布时刻强制）。
VAULT="scripts/release-token-vault.mjs"
if [ ! -f "$VAULT" ]; then
  echo "  ❌ 金库文件缺失：$VAULT（发布令牌源——漏写）"
  FAIL=1
elif ! bun "$VAULT" --check >/tmp/vault-check.log 2>&1; then
  echo "  ❌ 金库完整性校验失败（密文损坏/互逆破坏）:"
  sed 's/^/     /' /tmp/vault-check.log
  FAIL=1
fi

if [ "$FAIL" -eq 0 ]; then
  echo "  ✓ 密钥泄露扫描通过（无令牌形态·无内嵌凭据·本机凭据未入库·keystore 面收敛·金库互逆完整）"
else
  echo "  密钥泄露扫描未通过——阻断本轮测试/构建"
fi
exit "$FAIL"
