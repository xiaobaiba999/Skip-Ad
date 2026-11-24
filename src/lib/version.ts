/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.9：开屏命中死角根治轮（#102 真机实锤三死链）——CLEAN 档案窗
 * 晚到键救回（晚档轻扫梯子）+ 微树门分账（探针/扫描枚举账饿死链根治）
 * + 零候选退避微树豁免（1.2s 快退砍救命档）+ 诊断日志升级。v7.15.8
 * 全部能力保留（binder 风暴收敛/5 秒观闸/日志更详细）。 */
export const APP_VERSION = "7.15.9";
export const VERSION_CODE = 85900; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.9→85900）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.9",
    code: 85900,
    date: "2026-11-25",
    tagline: "开屏命中死角根治：CLEAN 窗晚到键救回 + 微树门分账 + 零候选退避微树豁免（v7.15.8 全部能力保留）",
    notes: [
      "CLEAN 档案窗晚到跳过键救回（#102 真机实锤：netdisk「跳过 4」被迫手动点——手动样本 9→14）：SurfaceView 视频开屏零内容事件（树静默）时时间梯子是唯一希望，而省电档案窗（连续 3 次零候选）此前无订阅=纯探针（0ms 一次）、有订阅={0,700} 两档——晚到跳过键（1~3s 出现）无人再扫。现在两类 CLEAN 窗统一「0ms 多窗探针哨兵 + 晚档轻扫梯子」（无订阅 {700,1600,3200} · 有订阅 {0,700,1600,3200}），微树/小树整树扫描 ~1ms/档，典型日功耗模型红线不变",
      "微树门分账（#102 真机实锤：v7.15.8 整份日志零🪟——多窗补扫从未触发）：探针与扫描微树门此前共用一本 400ms 枚举账，探针前置每 ~400ms 消耗预算（CLEAN 窗内容事件 → 探针 collectRoots(allWins)），扫描链梯子档的微树门检查时预算永远是「刚被探针用掉」→ 补扫死代码——weibo 6 节点 / keep 8 节点空壳都没触发。现在两路径各自 400ms 独立节流（microScanEnumOk/multiWinEnumOk 分账），最坏同窗双枚举仍比 v7.15.7 风暴低一个量级",
      "零候选退避微树豁免（#102 真机实锤：晚到键救命档被 1.2s 快退砍光）：≤48 节点（SHELL_TREE_NODES，与微树门同源）零候选空壳窗此前 2 次零候选 + 1.2s 即杀链——1500ms 后的救命档全取消，静默窗晚到按钮（2~3s 出现）永远等不到扫描。现在微树空壳绝不退避：扫描成本 ~0.1ms/档（18 档全跑也不及一次常规树扫描），占位窗本就秒级消失（窗口一切换链自动作废），为省这点电砍救命档得不偿失",
      "诊断日志升级：未命中日志微树空壳标记（单根 6~48 节点且五类候选全空 → 「微树空壳（跳过键或在覆盖层/晚到）」——铁签名直接可见，不再需要反推树规模）；微树门无窗可补时打点「🪟 微树补扫：无同包覆盖层可补（活动窗 N 节点空壳）」（每窗一条）——「门被饿死」与「无覆盖层」从此日志可分",
      "回归基建：服务级 JVM 套件新增 S7 场景（CLEAN 窗探针饿死复刻——探针账被占用时开新窗，覆盖层跳过键必须被晚档梯子的分账微树门救回）；已验证 v7.15.8 代码上 S7 红（gestureCount=0 零🪟）· v7.15.9 绿；全套 4517 断言通过",
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
