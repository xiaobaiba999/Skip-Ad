/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.6plus";
export const VERSION_CODE = 82300; // major*10000 + minor*1000 + patch*100，随版本单调递增（>82200 v8.2.2——覆盖安装升级）
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
    version: "7.9.6plus",
    code: 82300,
    date: "2026-11-23",
    tagline: "以 v7.9.6 为稳定基线的精简重构版：启动时新版本检测（versionCode 主判 + 全屏更新页）+ 日志上传业务保持",
    notes: [
      "启动时优先检查更新：进入 App 自动获取 GitHub 最新正式 Release，远端 versionCode 高于本地时优先显示全屏更新提示页（versionCode 主判——不再只比 versionName，Draft/Pre-release 不作正式更新推送，网络异常静默进入 App 绝不阻塞启动）",
      "全屏更新页 + 下载状态全展示：应用名/图标/当前版本/最新版本/更新内容/安装包大小；正在连接/下载进度/当前使用的下载源/下载中断自动重试（系统断点续传）/正在切换备用源/正在校验 APK/校验成功准备安装/失败可重新下载——下载完成前绝不把不完整文件交给安装器",
      "安全校验不降级：无论哪个下载源，安装前统一核对文件大小 + SHA-256（与 Release 公布值比对）；任何一项不符拒绝安装并删除文件。ghfast.top/gh-proxy.com 等 ghproxy 类第三方代理已移除——唯一官方源 GitHub Release（HTTPS），无经所有者确认的国内备用源前不伪造多源",
      "日志上传业务保持（v8.2.2 简化模型）：正常采集（重启保留·关闭自动上传不影响采集）· 自动上传 24 小时最多成功 3 次（只记服务端确认·失败不耗额度）· 随时手动上传 · 成功创建 GitHub Issue 并显示真实编号与经核实的链接",
      "v8.x 清理：删除更新检查双通道（Worker 中转——沙箱不可验证）/worker 源码目录/v8.x 架构文档/v8.2.2 构建产物；GitHub 无任何 v8.x Release（此前已删）。测试 39 套件 7047 断言全绿（含 versionCode 主判真值表）；真机 E2E NOT_RUN（沙箱无设备）",
    ],
  },];

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
