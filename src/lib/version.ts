/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.6：动态图开屏（Keep/CSDN 型：跳过键位置随机·无倒计时·纯图片）
 * 泛位置识别根治 + 首页白名单精简 + 代码冗余清理。 */
export const APP_VERSION = "7.15.6";
export const VERSION_CODE = 85600; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.6→85600）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.6",
    code: 85600,
    date: "2026-11-24",
    tagline: "动态图开屏泛位置识别根治（Keep/CSDN）+ 首页白名单精简 + 冗余清理",
    notes: [
      "泛位置图片跳过键（Keep/CSDN 动态图开屏实锤：跳过按钮每次在不同地方、无倒计时、纯图片）：旧几何带只认顶 14%/底 18%+左右缘，居中随机位零收集。新增泛位置候选（任意位置小可点无字图键），仅强广告形态（热区/角标/SDK窗/学习窗）下出手，居中位无「树极简」兑底、尺寸门比角落键更紧（面积 ≤6%）+ vid 诱导 token 否决——不乱点红线不破",
      "CLEAN 档案盲区根治（「识别不到且日志没记录」头号根因）：(pkg|cls) 连续 3 次零候选判省电后只剩文本探针，纯图片键探不到 = 不扫不点不记。新增图形探针（开屏窗内浅 DFS 1.5s 一档）：vid 键/泛位置键/热区/角标任一在场 → 档案复活全量出手",
      "未命中日志三重豁免：热区/角标在场的广告窗不再被「同包 8s 节流」（logo 窗先记一条把真广告窗的吞掉）与「H5 大树 nodeCount>150」双杀——广告窗的 miss 是补规则黄金样本必记录；热区开屏窗 4s 后不再全停扫（500ms 低频补扫到窗口截止——晚到跳过键 5~12s 仍可救回）",
      "首页白名单精简：去掉搜索框与手动添加行，默认只显示 5 款应用（白名单内置顶）+「展开全部 N 款」按需展开（展开后仍可滚动检索）——首页更短更清爽",
      "代码冗余清理（全部经判断与测试验证）：诱导词双表合一（26 词×2 → 单源）；低端机预算降级死存储修复（真正生效）；7 个孤儿桥方法/订阅市场死代码/约 90 行死样式移除；观察窗口径统一（SDK 窗 20s 热区观察）；上传按钮 spinner 动画缺陷修复（adspin 关键帧缺失）",
    ],
  }];

/** 三段式 tag（vX.Y.Z）→ 项目公式 versionCode（v7.10.0 统一版本规则：
 *  Web 面板更新判定只比 versionCode——versionName/tag 字符串比较已随
 *  compareVersion 删除；非三段 tag（历史 plus 系）→ 0（无效——不判新）。
 *  与 scripts/update_manifest.py version_code_of_tag 同语义。 */
export function versionCodeOfTag(tag: string): number {
  const m = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(String(tag || "").trim());
  if (!m) return 0;
  return Number(m[1]) * 10000 + Number(m[2]) * 1000 + Number(m[3]) * 100;
}
