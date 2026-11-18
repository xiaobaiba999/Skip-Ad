/** 回复并关闭 issue#39/40/41（v4.3.0 引擎日志：门控开闸慢 4.8s + H5 晚到按钮 + 恢复防护跳系统页 + 功耗） */
import { getGithubToken } from "../src/lib/credentials";

const TOKEN = getGithubToken();
const API = `https://api.github.com/repos/xiaobaiba999/Skip-Ad`;
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const body = `## 日志已分析（v4.3.0 · 问题均已随 **v4.4.0** 修复）

感谢连续上传三份日志！关键线索全部定位到了根因，已随 [v4.4.0](https://github.com/xiaobaiga999/Skip-Ad/releases/tag/v4.4.0) 逐条修复，本轮主题：**比人眼快，露头就秒**。

1. **喜马拉雅/keep 跳过比人手动还慢（实锤：\`门控说 4086ms 后出手，实际 5.5s 后才点到\`）**——根因不是门控算错，而是门控【开闸之后】引擎只能干等下一固定档扫描或内容事件才出手，扫描链空档最长 5s+。人看到倒计时走完 200~300ms 就点下去了，引擎还在等档。修复：设门控的瞬间同步排一针【开闸 +40ms】精确补扫（倒计时数字每秒跳动会自动重排针，零漏扫），**开闸后 40ms 内出手，快于人眼反应**；点击冷却同步 900→600ms；另加点击后 450ms/900ms 两针快速复验——「点了没反应」场景人手 300~500ms 就补第二刀，引擎此前要等 1.2s+，现在永远快一步；

2. **有时候触发不了跳过广告**——日志实锤（www 应用 \`开屏未命中\` 后无下文）：H5/WebView 开屏广告的按钮 1~2s 才渲染，而「内容页早退」在 400ms 就把扫描链杀了，按钮真正出现时补扫全让路 = 永远点不到。三重修复：杀链延迟 400ms→2000ms；广告窗/学习窗/Dialog 窗【绝不早退】（窗口本身就是广告，按钮只是还没渲染）；杀链后的内容事件以 900ms 节流降频放行（滚动风暴照样挡住，晚到的跳过键能救回）；

3. **恢复防护跳到系统设置强制看 10 秒**——两层根因：① 服务「僵尸态」：系统解绑后实例还在，App 误报「守护中」，用户发现不对劲时只能去系统设置重开（MIUI 强制看 10 秒警告）。现在 onServiceConnected/onUnbind/onDestroy 维护真实绑定标记，僵尸态一眼识破；② 拉活等待太短（只等 1.5s，MIUI/HyperOS 重绑慢的 ROM 被误判「需要去系统设置」）。现在深度拉活 6 秒（每 500ms 踢一次系统促使重绑）+ **打开 App 即自动拉活**（连「一键恢复」都不用点）+ 拉活成功暂停自动解除。**绝不再自动跳系统设置**——10 秒强制警告只剩「开关真被系统关掉」时才需要；

4. **功耗还是有点高（实锤：\`事件 4602/开机 · 手势估算 1638µWh\`）**——三刀：① 息屏状态改 SCREEN_ON/OFF 广播缓存（此前每个事件一次 PowerManager binder IPC，4602 事件 = 4602 次 IPC，现在零 IPC）；② 非前台应用的内容变化事件（事件洪流主体：后台动画/刷新/灵岛）此前要过完整过滤器链才丢弃，现在一步丢弃（纯内存字符串比较）；③ 顺带修了一个「触发不了」帮凶：桌面壁纸动画的内容事件曾把前台包名污染成桌面，真前台应用的事件被误丢弃——现在只有窗口切换才更新前台追踪。

升级方式：[v4.4.0 APK 直接下载](https://github.com/xiaobaiga999/Skip-Ad/releases/download/v4.4.0/adshield-v4.4.0.apk)（同签名覆盖安装）。欢迎再传日志验证提速效果——倒计时门控的开闸针在日志里会显示为开闸瞬间出手。`;

async function reply(n: number) {
  const r = await fetch(`${API}/issues/${n}/comments`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ body }),
  });
  console.log("comment #" + n + ":", r.status);
  const c = await fetch(`${API}/issues/${n}`, {
    method: "PATCH",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ state: "closed" }),
  });
  console.log("close #" + n + ":", c.status);
}

async function main() {
  for (const n of [39, 40, 41]) await reply(n);
}

main();
