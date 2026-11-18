/** v3.11.0 修复回复 issue#13（链式跳转误拦截用户主动分享） */
import { getGithubToken } from "../src/lib/credentials";

const TOKEN = getGithubToken();
const REPO = "xiaobaiba999/Skip-Ad";
const ISSUE = 13;

const body = [
  "## 已定位并修复 ✅ v3.11.0",
  "",
  "感谢这份日志——「链式跳转已弹回：securitycenter 的广告试图拉起 mm ×3」这条记录把根因拍实了：**你在安全中心主动点「分享到微信」，引擎却把 `securitycenter → 微信` 当成「广告拉起微信」，按返回键把你踢回去，你重试 3 次全被弹回**。这是误判，抱歉。",
  "",
  "### 根因",
  "",
  "回弹判定只看了「宿主应用是否有广告特征 + 目标是否在广告目标名单」，**完全没识别「这是你自己点的」**。微信恰好在广告目标名单里（广告确实常拉微信小程序），于是主动分享被当成了广告跳转。",
  "",
  "### 修复（参考同类头部应用的安全哲学：回弹绝不与用户意愿对抗，只拦「无人在场时广告自己拉起」的跳转）",
  "",
  "v3.11.0 加了**三重放行安全门**，任何一道命中都不回弹：",
  "",
  "| 放行条件 | 覆盖场景 |",
  "| --- | --- |",
  "| 你 **2.2 秒内**有真实点击（分享/登录按钮，引擎代点不计入） | 直接分享、点按钮跳转 |",
  "| 系统分享选择器 **8 秒内**出现过（Resolver/Chooser 全 ROM 兼容识别） | 在分享面板里挑微信/QQ 等 |",
  "| 同一「宿主→目标」**12 秒内**已弹回过一次又立刻拉起 | 你被弹回后的重试（本次事故里它单独就能拦住后两次误弹） |",
  "",
  "配套：点商品/文章详情跳应用内置页面不再被误弹回；分享面板的「疑似误触」观察噪音归零。",
  "",
  "### 学习规则明细已撤出界面",
  "",
  "你反馈的「学习规则显示太多太乱」也一并处理：学习中心不再罗列规则明细（学习引擎照常静默工作，首页保留规则计数，明细只随「上传日志」进 GitHub 诊断）。",
  "",
  "### 验证",
  "",
  "- 决策核心 JVM 单测 **131 用例全绿**（新增 19 例专门建模本次事故：分享按钮点击门 / 选择器门 / 同对重试门 / 真广告自动拉起仍正常弹回的回归保护）",
  "- 规则引擎 60 用例全绿；浏览器 + VLM 视觉核查通过",
  "",
  "### 升级",
  "",
  "应用内「关于 → 检查更新」直接升级，或下载：[adshield-v3.11.0.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v3.11.0)（同签名覆盖安装，学习数据保留）。",
  "",
  "升级后再分享一次试试——如果还有被弹回的情况，麻烦再上传一次日志，日志里现在会记录「放行原因」，能直接看出是哪道门在起作用。",
].join("\n");

const r = await fetch(`https://api.github.com/repos/${REPO}/issues/${ISSUE}/comments`, {
  method: "POST",
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    Accept: "application/vnd.github+json",
    "Content-Type": "application/json",
    "User-Agent": "skip-ad-instant-engine",
  },
  body: JSON.stringify({ body }),
});
const d = (await r.json()) as { html_url?: string; message?: string };
console.log(r.ok ? `✓ 已回复 issue#${ISSUE}：${d.html_url}` : `✗ ${r.status}: ${d.message}`);
process.exit(r.ok ? 0 : 1);
