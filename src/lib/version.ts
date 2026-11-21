/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "8.0.0";
export const VERSION_CODE = 80000; // major*10000 + minor*1000 + patch*100，随版本单调递增
export const GITHUB_REPO = "xiaobaiba999/Skip-Ad";
export const GITHUB_API = `https://api.github.com/repos/${GITHUB_REPO}`;
export const GITHUB_HOME = `https://github.com/${GITHUB_REPO}`;
export const RELEASES_PAGE = `https://github.com/${GITHUB_REPO}/releases`;
export const ISSUES_PAGE = `https://github.com/${GITHUB_REPO}/issues`;

export interface VersionEntry {
  version: string;
  code: number;
  date: string;
  tagline: string;
  notes: string[];
}

/** 「关于」页仅展示当前安装版本（硬约束：数组只保留最新 1 条，
 *  旧版本内容归档在 GitHub Releases 与 git 历史） */
export const VERSION_HISTORY: VersionEntry[] = [
  {
    version: "8.0.0",
    code: 80000,
    date: "2026-11-25",
    tagline: "自动日志上传闭环轮：Android → Cloudflare Worker（skip-ad-log-relay）→ GitHub 私有仓库 Issues——授权默认关 · 客户端零凭据 · 白名单双端过滤 · batchId 幂等重试",
    notes: [
      "自动上传通道上线：Net.LOG_RELAY_URL 填入生产中转地址（skip-ad-log-relay.2927609408.workers.dev——已部署并经用户真实浏览器 /healthz 验证）；服务端凭据（GitHub App 私钥/IP_HMAC 密钥）全部只存 Cloudflare Worker Secret——APK 零凭据红线保持",
      "客户端上传链路（5 个 core 模块）：LogPrivacyFilter（白名单/枚举/危险键整批拒绝/脱敏四正则——与服务端 validate.ts 同规约）+ LogEvents（八类事件工厂：AD_TASK/SAFE_BLOCK/CLICK/VERIFY/STATS/GKD/VIDEO_POLL/CRASH）+ LogUploadPolicy（响应裁决纯函数：ACK/WAIT/RETRY/FAIL 可穷举）+ LogBatchStore（批次持久化：PENDING→UPLOADING→ACKED/FAILED；ACK 前绝不删；400 条缓冲上限关键事件最后丢；48 分批上限）+ LogUploadDispatcher（单后台守护线程；退避 30s→480s×5；RATE_LIMITED 尊重 retryAfter 不耗重试计数；MANUAL_RECONCILIATION_REQUIRED 立即终态绝不换 batchId 重传）",
      "batchId 幂等：每次封批生成 UUID v4；网络超时重试复用同一 batchId——服务端幂等兜底「超时但 Issue 已建」场景（重试必然 ALREADY_ACCEPTED，绝不二建 Issue）；重试不换号结构性保证（上传只读已存批次）",
      "隐私纵深：客户端白名单过滤（字段/枚举/整数界/词族门）+ 服务端权威第二道（同规约）；身份证 18 位差一正则四端同族修复（Net.maskSensitive/www maskText/LogPrivacyFilter/服务端 privacy.ts）；未授权采集门关闭——未同意上传的设备零新增磁盘写入",
      "设置页真数据：上传状态行（待传批次数/24h 已提交/失败检视/最近提交时间）+「立即提交」（同一服务端 24h/3 限额绝不绕过）+「清理待上传」；www 手动本地导出通道并存",
      "上传失败绝不影响广告跳过：事件埋点异步落盘（单线程 executor 非阻塞 submit）+ 上传独立守护线程 + 全部 try/catch 吞尽——无障碍热路径零新增同步 IO；本轮零识别/安全门改动（不新增关键词、不扩大点击区域、不降 SafetyGuard、不动任务状态机）",
      "测试：35 套件 6802 项 JVM 断言全绿（新增 3 套件 149 项：隐私过滤 47/策略裁决 55/批次存储 47——含 org.json JVM shim 使 core 的 org.json 依赖可在无 Android 运行时测试）；全源 ECJ 编译与基线零差异（5 个 R.* 基线错误不变）；secret-scan/app.js 符号闸/version-sync 全绿",
    ],
  },
];

/** 比较两个 semver（支持 v 前缀）：a<b → -1，a>b → 1，相等 → 0 */
export function compareVersion(a: string, b: string): number {
  const pa = a.replace(/^v/, "").split(".").map((n) => parseInt(n, 10) || 0);
  const pb = b.replace(/^v/, "").split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d > 0 ? 1 : -1;
  }
  return 0;
}
