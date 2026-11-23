/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.10.0";
export const VERSION_CODE = 80000; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.10.0→80000）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.10.0",
    code: 80000,
    date: "2026-11-25",
    tagline: "上传次数限制彻底移除 + 更新检查异步化 + 开屏提醒解耦 + 统一版本规则",
    notes: [
      "日志上传次数限制彻底移除：自动上传不再有 24h/3 成功额度（仅受心跳间隔 8h 与「有新日志才传」约束）；手动上传零次数/额度/冷却门槛，随时可传、短时连传多批全部放行（同 batchId 幂等保护保持，绝不二建 Issue）",
      "更新检查异步化：新增 updateCheckAsync 异步桥——JS 主线程零网络等待（旧同步桥整体移除，弱网下页面冻结最长 40s 的根因根治）；JS 单飞 + 过期序号丢弃 + 90 秒回调兜底三重防线，手动检查绝不永久卡「检查中…」",
      "开屏提醒与更新检查彻底解耦：反馈提醒独立 3 秒定时评估——网络再慢（五源链预算 40s）也最多 3s 看到提醒；24h 节流/5min 防重建/更新优先语义保持",
      "统一版本判定规则：全项目统一 remoteVersionCode > currentVersionCode——versionName 仅展示、绝不作为更新判断依据；Release 正文 versionCode 元数据行回退与 82300/plus 旧兼容链彻底清理（清单侧/JS 侧/生成器侧三面同步）",
    ],
  },];

/** 三段式 tag（vX.Y.Z）→ 项目公式 versionCode（v7.10.0 统一版本规则：
 *  Web 面板更新判定只比 versionCode——versionName/tag 字符串比较已随
 *  compareVersion 删除；非三段 tag（历史 plus 系）→ 0（无效——不判新）。
 *  与 scripts/update_manifest.py version_code_of_tag 同语义。 */
export function versionCodeOfTag(tag: string): number {
  const m = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(String(tag || "").trim());
  if (!m) return 0;
  return Number(m[1]) * 10000 + Number(m[2]) * 1000 + Number(m[3]) * 100;
}
