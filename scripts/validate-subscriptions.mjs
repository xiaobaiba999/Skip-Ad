#!/usr/bin/env bun
/**
 * v8.0.10 发布链订阅资产校验（零依赖，bun/node 均可运行）：
 *   bun scripts/validate-subscriptions.mjs
 *
 * 背景：内置 667.json5（Lin-arm v644）曾被截断（954KB / 完整约 1.4MB，JSON5 非法）
 * 静默打包进 APK，导致首装种子不入库且 WebUI 统计归零。本脚本在发布链最前端
 * 用字节级下限 + 尾部完整性快速拦截（深层的 JSON5 解析与逐组编译由
 * :gkd-app:jvmTest 的 BundledSubscriptionAssetsTest 兜底）：
 *   · 字节下限：667 ≥ 1MB（截断版 954KB 会被拦下），86/233 ≥ 100KB
 *   · 尾部完整性：最后一个非空白字节必须是 '}'（截断文件在字符串中途结束）
 *   · UTF-8 无 BOM：首字节不得为 EF BB BF（保证与上游 dist 字节一致）
 * 任一项失败即 exit 1。
 */
import { readFileSync, statSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const subscriptionsDir = join(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  'skip-ad-v8',
  'gkd-android',
  'src',
  'main',
  'assets',
  'subscriptions',
)

const SUBSCRIPTIONS = [
  { id: 86, minBytes: 100_000 },
  { id: 233, minBytes: 100_000 },
  { id: 667, minBytes: 1_000_000 },
]

const rows = []
let failed = false

for (const { id, minBytes } of SUBSCRIPTIONS) {
  const file = join(subscriptionsDir, `${id}.json5`)
  const problems = []
  let bytes = 0
  try {
    bytes = statSync(file).size
  } catch {
    rows.push({ id, file, bytes, minBytes, problems: ['文件不存在'] })
    failed = true
    continue
  }
  if (bytes <= minBytes) {
    problems.push(`字节数 ${bytes} 未超过下限 ${minBytes}（疑似截断）`)
  }
  const buffer = readFileSync(file)
  const hasBom = buffer.length >= 3 && buffer[0] === 0xef && buffer[1] === 0xbb && buffer[2] === 0xbf
  if (hasBom) {
    problems.push('存在 UTF-8 BOM（应与上游 dist 字节一致，无 BOM）')
  }
  let lastNonWhitespace = -1
  for (let i = buffer.length - 1; i >= 0; i--) {
    const byte = buffer[i]
    if (byte !== 0x20 && byte !== 0x09 && byte !== 0x0a && byte !== 0x0d) {
      lastNonWhitespace = byte
      break
    }
  }
  if (lastNonWhitespace !== 0x7d /* '}' */) {
    problems.push(`尾部非空白字节为 0x${lastNonWhitespace.toString(16)}（应为 '}'，疑似中途截断）`)
  }
  if (problems.length > 0) failed = true
  rows.push({ id, file, bytes, minBytes, problems })
}

const format = (n) => n.toLocaleString('en-US')
console.log(`内置订阅资产字节校验（目录 ${subscriptionsDir}）`)
console.log(
  ['id'.padEnd(6), 'bytes'.padStart(14), 'floor'.padStart(14), 'status'.padEnd(8), 'problems'].join(
    ' ',
  ),
)
for (const row of rows) {
  const status = row.problems.length === 0 ? 'PASS' : 'FAIL'
  console.log(
    [
      String(row.id).padEnd(6),
      format(row.bytes).padStart(14),
      format(row.minBytes).padStart(14),
      status.padEnd(8),
      row.problems.join('; '),
    ].join(' '),
  )
}

if (failed) {
  console.error('\n订阅资产校验失败：存在截断/损坏风险，禁止发布。')
  process.exit(1)
}
console.log('\n全部通过：三套内置订阅字节完整。')
