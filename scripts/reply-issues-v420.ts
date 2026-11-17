/** 回复并关闭 issue#31-37（v4.1.0 真机日志：微信左右乱划 / 点开页面被自动关 / 订阅零命中） */
import { getGithubToken } from "../src/lib/credentials";

const TOKEN = getGithubToken();
const API = `https://api.github.com/repos/xiaobaiba999/Skip-Ad`;
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const reply = [
  "## 日志已分析（v4.1.0 · 问题均已随 **v4.2.0** 修复）",
  "",
  "感谢上传日志！这批 v4.1.0 日志暴露了两个关键根因，逐条说明：",
  "",
  "1. **微信内左右乱划**（日志实锤：`GKD 订阅规则命中（mm）` 在微信内反复刷屏）：远程订阅（含「已停止维护」的默认订阅）的微信规则**没有校验 activityIds**——GKD 社区规则本应限定只在朋友圈/小程序等特定界面生效，此前的引擎把 Activity 限定字段整体忽略了，规则在微信主界面（LauncherUI）也能命中，900ms 冷却连点 = 乱划元凶。v4.2.0 三层根治：**activityIds 强校验**（非目标界面的规则直接出局）+ **skip 形态门**（无 Activity 限定的规则只允许点跳过/关闭形态控件，底部 Tab 绝不代点）+ **微信主界面门**（LauncherUI 期间引擎零扫描零点击）；",
  "2. **点开的页面被自动关闭、手动重开又被关**（日志实锤：`广告窗直断 → 返回键复验未关 → 继续点击梯子`）：你点击弹窗广告内容打开落地页后，引擎把新窗口当成「广告未关」继续对抗。v4.2.0 **用户操作让路**：检测到你的真实点击后 4s 内一律不出手；你点击后打开的新页面标记为「你主动打开」，**30s 内引擎零动作**——你点开的页面绝不替你关；",
  "3. **订阅导入后没命中**：订阅规则此前只接驳开屏路径，弹窗场景（更新弹窗/青少年模式等 GKD 社区规则的主战场）从不咨询订阅。v4.2.0 **弹窗路径同步接驳**，并按 GKD 语义加同窗限次（actionMaximum）与出手间隔（actionInterval）限流；优先级调整为**内置优先、订阅接力**——订阅页现在同时展示内置/远程命中数；",
  "4. **日志与功耗**：引擎日志现在**每 12 小时自动提交**（手动上传不受影响），内容更具体（16 条带时间戳 + Top5 应用战绩 + 订阅命中 + 能耗画像）；订阅规则改单遍树扫描、内容变化补扫收紧，重规则应用（微信等）CPU 与功耗明显下降。",
  "",
  "另修复：GKD 规则名尾号数字被误读成倒计时（日志 `倒计时门控（mm）：跳过键 1s 后才激活` 的白等）。",
  "",
  "**升级方式**：App 内「我的 → 检查更新」页内直装，或 [Releases v4.2.0](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v4.2.0)（同签名覆盖安装，学习数据与订阅全保留）。",
  "",
  "— 开屏卫士 v4.2.0",
].join("\n");

for (const n of [31, 32, 33, 34, 35, 36, 37]) {
  const cm = await fetch(`${API}/issues/${n}/comments`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ body: reply }),
  });
  const cl = await fetch(`${API}/issues/${n}`, {
    method: "PATCH",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ state: "closed" }),
  });
  console.log(`#${n}: comment ${cm.status}, close ${cl.status}`);
}
