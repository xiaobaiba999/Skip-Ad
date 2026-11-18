/** 回复并关闭 issue#38（v4.2.0 引擎日志：订阅零命中 + CSDN 漏跳 + 喜马拉雅连击/反馈循环） */
import { getGithubToken } from "../src/lib/credentials";

const TOKEN = getGithubToken();
const API = `https://api.github.com/repos/xiaobaiba999/Skip-Ad`;
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const body = `## 日志已分析（v4.2.0 · 问题均已随 **v4.3.0** 修复）

感谢上传日志！这份 v4.2.0 日志信息量很大，三个关键线索（\`订阅：内置 21 款/21 条（命中 0）· 远程 327 款/828 条（命中 0）\`、\`csdnplus 检测到内容突发后再无下文\`、\`android（即喜马拉雅 com.ximalaya.ting.android）复验连击 + 反馈面板\`）全部定位到了根因，已随 [v4.3.0](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v4.3.0) 逐条修复：

1. **许多第三方 App 无法跳过（如 CSDN）**——日志实锤：\`订阅 828 条规则命中 0\`。根因是 GKD 默认订阅的【主力开屏规则全部住在 globalGroups】（对所有应用生效的全局组，比如「开屏广告」组 \`[text*="跳过"][text.length<10]\`），而此前的解析器只读 apps 数组——**主力规则被整体丢弃**。同时补齐了一批语法盲区（旧解析「成功」但永不匹配）：数字关系符（\`@View <3 FrameLayout <2 FrameLayout < [vid="fl_third_ad"]\`——CSDN 开屏规则原样命中）、childCount（默认订阅 372 处）、text.length、visibleToUser、括号布尔表达式、组级 enable/matchTime/actionCd。CSDN 也已加入内置规则库。**日志没记录**的问题同步根治：现在扫描未命中会记一条诊断日志（应用名 + 窗口类名 + 树规模 + 可点控件样本），上传日志就能定位缺哪种规则；

2. **跳过按钮每次出现在不同地方**——两处修复：① 复验【真正换候选】：日志里「换第 2 个候选」此前是假的——每次重扫都返回同一个首个命中，同一坐标连点 6 次（喜马拉雅实锤）。现在一次扫描收集全部候选按置信度轮换出手，订阅 vid 候选（如喜马拉雅 main_ad_skip）在文本候选用尽后自动接管；② 角落候选扩展到底部（右下/左下）——部分应用跳过键随机出现在右下角；

3. **喜马拉雅跳过慢 +「为什么关闭广告」循环弹出**——① 「为什么关闭广告」等 8 种变体话术加入反馈面板强特征：识别后立即停用重试 + 代点中性收起键（取消/暂不反馈/下次再说——绝不替你提交任何反馈选项），循环弹出根治；② 假倒计时识别：真倒计时每秒跳数字，烧在图里的装饰数字永不变化——首击未关且数字 1.6s 未变 = 不再傻等 5 秒，立即换候选按普通节奏重试（跳过速度显著提升）；

4. **功耗还是有点高**——找到并修复了一个功耗黑洞：内容变化的「前 4s 高频补扫」此前节流比较的是**窗口起点**而非**上次补扫**——信息流/聊天列表滚动时每个内容变化事件都排一次整树扫描（10~30 次/秒）。改为真节流 120ms；另加「内容页早退」（可滚动/有输入框且零候选 = 正常内容页 → 本窗剩余 10+ 个固定扫描档全部取消）、扫描链 16→13 档、节点预算分层、小窗探测缓存、亮屏心跳改窗口式闹钟（系统批量调度）。

**另新增：应用白名单**（首页新卡片）——卫士处理过的应用会列出来（带应用名），开关直选；白名单内应用零扫描零点击零学习，卫士完全让路。

升级方式：[v4.3.0 APK 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v4.3.0/adshield-v4.3.0.apk)（同签名覆盖安装）。如有问题欢迎再传日志——未命中诊断日志会告诉我们具体缺哪条规则。`;

async function main() {
  const n = 38;
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

main();
