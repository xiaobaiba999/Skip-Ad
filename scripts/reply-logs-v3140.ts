import { getGithubToken } from "../src/lib/credentials";

const TOKEN = getGithubToken();
const API = `https://api.github.com/repos/xiaobaiba999/Skip-Ad`;
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const reply = [
  "## 日志已分析（v3.13.0 · 所有问题均已随 **v3.14.0** 修复）",
  "",
  "感谢持续上传！本份日志与 #21 同源（v3.13.0），其中反复出现的三类问题正是 v3.14.0 的修复目标：",
  "",
  "1. **喜马拉雅复验风暴**（日志 `(android)` = com.ximalaya.ting.android，第 2/3/4 次换候选重试）：跳过后弹出的「不喜欢/反馈」面板被当成广告未关——v3.14.0 面板出现即确认已关并**自动点「取消」收起**，绝不替你提交反馈；",
  "2. **内容突发日志刷屏**（az/weibo/idlefish/qtl 60 秒多条同语）：v3.14.0 同包 30s 去重；",
  "3. **手势中断后重试慢**：v3.14.0 中断即退冷却 + 350ms 快速补扫。",
  "",
  "另注意到新学习规则（QQ 位置 / 云马校讯跳过文案）已正常沉淀，引擎侧无需调整。",
  "",
  "**请在 App 内「关于 → 检查更新」升级到 v3.14.0**（同签名覆盖安装，学习数据全保留），升级后继续观察；若还有问题随时再传日志。",
  "",
  "— 开屏卫士 v3.14.0",
].join("\n");

for (const n of [22, 23]) {
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
