/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用） */
export const APP_VERSION = "7.9.0";
export const VERSION_CODE = 79000; // major*10000 + minor*100 + patch，随版本单调递增
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
    version: "7.9.0",
    code: 79000,
    date: "2026-11-20",
    tagline: "源码审计轮：三处接线盲区根治 + 图标关闭钮路径 + 视频画像竞速根治",
    notes: [
      "三处接线盲区根治（源码审计实锤——纯函数单测全绿、服务侧零接线的装配缺陷）：①闸⓪第三路「SDK 插屏窗指纹」从未被接通（wctx.sdkAdWin 恒 false——无语境词无 WebView 的原生 SDK 插屏三路语境全空永不出手）→ isAdActivityClass(窗类) 接通；②SDK 窗零候选 streak 冻结在 0（豁免窗跳过计数块——videoLike 的 sdkAdWin 路径永不触发，SDK 视频开屏观察窗不延长轮询针不排）→ streak 无条件计数（豁免只挡杀链不挡计数）；③外来包 Dialog 插屏卫士从未被拉起（非前台 Dialog 分支只走弹窗识别路径——广告 SDK 独立包 Dialog 插窗整窗漏拦）→ 同分支接线 + winClass 登记补齐",
      "图标关闭钮路径（用户实锤「关闭按钮是图片/只有图标没有文字」）：无文字角落小可点控件在强广告语境窗（SDK 插屏窗指纹 / WebView+广告角标——语境门槛比文字路径更严，仅语境词在场的普通窗角落图标绝不点）内收为图标钮候选——顶带优先 + 面积 ≤2% 屏图标级 + 中部 tabBar 高发区全拒 + label=× 走既有终闸零改动（execute 既有防线全量复用）",
      "视频画像竞速根治（树慢速构建窗）：WebView/H5 视频容器树慢速构建（内容事件持续到 ~2s）时零候选退避杀链（streak≥3+窗龄≥2.6s）早于静默≥1.6s 的全画像成立——arm-hint（streak≥3+窗龄≥2.2s+静默≥0.8s）窗免杀链，链连续活到全画像接管（旧版链死等固定档自愈最长 2s 空档；静默持续 <0.8s 照杀——功耗红线）",
      "视频轮询针探针词 5→10（+关闭/close/×/✕/✖——图内纯图标钮与拉丁关闭词旧探针探不到；裸 X/x 不收防 contains 噪声面）+ 每 3 针一次全量复扫（12~20s 段纯图片钮零文字零探针命中——walk 角落候选是唯一路径；20s 窗内 ≤5 次——功耗契约）",
      "23 套件 6114 项 JVM 断言全绿（+51：VideoSplashTest S9 画像竞速仿真 + S10 探针词契约、InterstitialGuardTest S9 图标钮真值表、InterstitialRoutingTest S10 接线契约）；性能契约全保持（零新增扫描/零新增档位/事件订阅零变化）· APK 836KB · VC 79000 覆盖安装即升级",
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
