/** 回复并关闭 issue#30（v4.0.0 真机日志：微信拍摄强退 / 灵动岛乱点 / 订阅 404 / Logo） */
import { getGithubToken } from "../src/lib/credentials";

const TOKEN = getGithubToken();
const API = `https://api.github.com/repos/xiaobaiba999/Skip-Ad`;
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const reply = [
  "## 日志已分析（v4.0.0 · 四个问题均已随 **v4.1.0** 修复）",
  "",
  "感谢详细反馈！日志中 `检测到内容突发（mm）→ 疑似覆盖层弹窗 → 已点击跳过（mm）：开屏×关闭键` 这条链正是「微信拍摄被强退」的实锤根因，逐条说明：",
  "",
  "1. **微信点「拍摄」被强退**：相机预览界面 = SurfaceView 简单树 + 顶部「×」关闭键，与开屏广告窗特征几乎雷同，引擎把微信相机的关闭键当成了「开屏×关闭键」代点 → 直接退出相机。v4.1.0 加了**相机/拍摄窗硬黑名单**（camera/capture/shoot 类窗口绝不扫描、绝不点击、绝不学习）；",
  "2. **灵动岛点开乱跳乱点**：灵岛展开的窗口事件被当成「新应用进入前台」——重置开屏计时、启动开屏扫描链，灵岛小窗控件被当「角落跳过键」；灵岛动画的高频内容变化还触发了日志里那条「内容突发→覆盖层弹窗」。v4.1.0 加了**三重前台门**（前台包门 / 焦点窗口与小窗尺寸门 / 突发检测只认前台应用），灵岛与悬浮窗绝不再被扫描；",
  "3. **App 内手动关闭**：首页守护中状态新增「**暂停防护**」按钮——暂停/恢复都在 App 内一键完成，服务保持存活（后台恢复能力不受影响），**不再需要去系统设置看 10 秒警告**；暂停期间绝不触发任何跳过；",
  "4. **GKD 订阅导入 404**：npmmirror 上 GKD 官方订阅包的文件路径已从 `/files/gkd.json5` 迁移到 `/files/dist/gkd.json5`，旧官方链接整体 404。v4.1.0 拉取失败会**自动切换镜像回退链**，「官方订阅」按钮也已更新为可用链接；",
  "5. **Logo**：全新白色小机器人侠 + 绿披风形象已上线（启动器图标 / 应用内吉祥物 / 网站全套同步更换）。",
  "",
  "**升级方式**：App 内「我的 → 检查更新」页内直装，或 [Releases v4.1.0](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v4.1.0)（同签名覆盖安装，学习数据与订阅全保留）。",
  "",
  "— 开屏卫士 v4.1.0",
].join("\n");

const cm = await fetch(`${API}/issues/30/comments`, {
  method: "POST",
  headers: { ...H, "Content-Type": "application/json" },
  body: JSON.stringify({ body: reply }),
});
const cl = await fetch(`${API}/issues/30`, {
  method: "PATCH",
  headers: { ...H, "Content-Type": "application/json" },
  body: JSON.stringify({ state: "closed" }),
});
console.log(`#30: comment ${cm.status}, close ${cl.status}`);
