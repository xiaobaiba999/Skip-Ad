/** 版本与 GitHub 仓库信息（Web 端与发布节奏共用）。
 * v7.15.11：锁屏/切换app开屏根治轮（#105/#107/#108/#109 四台设备日志）——
 * 横幅让路重排 + 解锁晚到键两针 + 前台回归探针（回切整窗漏拦重建）+
 * 多点治理（边界放行日志去重 + 派发失败连击退避）+ 插屏窗级退避。
 * v7.15.10 全部能力保留（三图豁免带/权限弹窗零扫描）。 */
export const APP_VERSION = "7.15.11";
export const VERSION_CODE = 86100; // v7.1.0 起标准公式 major*10000 + minor*1000 + patch*100（7.15.11→86100）；v7.10.0 统一版本规则：更新判定只看 versionCode，versionName 仅展示
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
    version: "7.15.11",
    code: 86100,
    date: "2026-11-25",
    tagline: "锁屏/切换app开屏根治（横幅重排+回归探针）+ 多点治理 + 插屏退避（v7.15.10 全部能力保留）",
    notes: [
      "锁屏/解锁开屏根治一环（#109 实锤：解锁重入后验证码/短信横幅把触点与返回键全挡，横幅 3~8s 自动消散后无人再扫 = 广告白看到底）：①横幅让路重排——出手被 heads-up 横幅挡下时排 3 针重查（+2.5/+5/+9s），横幅已散且前台未换窗口未切即重排扫描链立即补扫（日志「🔔 横幅已消散」用户可核验；每包 6s 一轮防针堆积；绝不点掉横幅红线保持）；②解锁晚到键两针——解锁重入链追加 +2.5s/+5.5s 补扫（解锁瞬间广告窗常是 5 节点空壳，跳过键 4~6s 才渲染，既有链最晚 3.4s 够不着），每次解锁至多两扫，功耗红线不破",
      "切换app开屏整窗漏拦根治（HyperOS/Android17 回切不发 WINDOW_STATE_CHANGED 实锤——v7.12.0 前台竞速晋升在 v7.15.0 版本线重制时遗失，本轮以阳性证据探针重建并泛化）：①竞速登记——窗口事件因活动窗口归属未切换被丢弃时登记候选（3s 窗口）；②回归探针——内容事件前置过滤处以「活动窗口根节点包名==该包」阳性证据复核（查询失败绝不误晋升），到手即按「进入应用」同语义完整重置 + 立即补扫（日志「🔄 切换app回归」）；③节流闸——竞速 1s/兜底 5s 包级 + 1.5s 全局（最坏 ~1.67 次 binder/秒 = 红线的 1/30；窗口类语义还原——源 View 类绝不写入 winClass，聊天/小程序/密码窗类名门照常）",
      "多点治理（#108 azukireader/#109 ting/#105 aweme 三台设备实锤「同一秒 5~13 条放行日志」）：①图片键边界放行日志去重——同包同理由 6s 一条（旧版每轮扫描每轮 execute 都打一条，用户读日志=「点了十几次」；实际出手以 ⚡/🔁 为准。双向验证：旧代码 22 条 → 新代码 1 条）；②派发失败连击退避——手势被覆盖层/横幅拒发后旧版无冷却记账，以扫描节奏 ~2.6 次/秒对同一候选无限重试 7 秒（每轮烧 getWindows 覆盖层判定），现连续 ≥3 次失败后 1.5s 人手耐心节流（首两次快重试保留——动画中 staleBounds 下一轮即恢复），成功/横幅消散/新窗即清零",
      "功耗与性能（#106 OPPO/#105 Redmi 实锤）：①插屏窗级退避——静态窗内容事件不停（动画/进度条）时旧版 2.5s 一轮无限重扫（fenglink66p 一小时 24+ 轮 walk300+双词表直查），现同窗连续未命中递进退避 2.5s→10s→30s（扫描量降 12~24 倍；窗切换/出手成功即清零——新插层照常秒级响应）；②Toast 类窗零开屏扫描（aweme ToastDialog 被当开屏窗整树扫描+未命中刷屏——Toast 2~3.5s 自灭且从无跳过键）；③securitypermission 零扫描（#106 OPPO 权限详情页族被白扫，与 permissioncontroller 同口径）",
      "测试与验证：ServiceHostTest 新增 S10（横幅挡点→消散重排救回，真实时序注入）S11（无窗口事件回切→探针晋升+立即补扫跳过）S12（边界放行日志 22→1 去重）S13（插屏退避梯子 2.5s→10s 递进+窗切换清零）四场景，双向验证全过（旧代码红/新代码绿，29 项服务级全绿——S11d 聊天主界面回切零扫描负例）；核心 4530 断言零改动全绿；主功能 byte-for-byte（决策核心/规则引擎/订阅零改动——全部为恢复链与节流层）",
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
