/** v5.1.0 · 回复并关闭 issue#46/47（v5.0.0 引擎日志：非主流 App 开屏未命中 + 噪声扫描刷屏） */
import { readFileSync } from "fs";

const TOKEN = readFileSync(new URL("./.github-token", import.meta.url), "utf-8").trim();
const API = `https://api.github.com/repos/xiaobaiba999/Skip-Ad`;
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const body = `## 日志已分析（v5.0.0 · 问题均已随 **v5.1.0** 修复）

感谢上传两份日志！非主流 App 的开屏形态逐帧定位到了根因，已随 [v5.1.0](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v5.1.0) 逐条修复，本轮主题：**非主流 App 开屏攻坚 + 该快就快、该退就退**。

1. **美团开屏广告窗（\`DspActivity\` / \`DynamicDialog$c\` 整窗未命中）**——根因：这两类窗口不在 SDK 广告窗指纹里，整窗"无身份"：close 族图片关闭键不敢点、返回键兜底不敢按，只能干看广告走完。修复：窗口指纹扩展（\`dsp\` / \`dynamicdialog\` / \`adsactivity\` 整段）+ 美团系专项规则（DspActivity 开屏窗与 DynamicDialog 弹窗内，close 族 vid 在窗口限定下放行；弹窗返回键兜底须再过广告形态树——可滚动的运营弹窗绝不误按）。新增 \`DynamicDialog$c\` 内部类窗口名按基类名匹配（混淆类名带 \\$ 后缀也能认）。

2. **校园/工具类 App 反复「开屏未命中」刷屏（school.app 9 秒 7 条）**——日志实锤：校徽启动页（皖江工学院标题 + fl_scan 扫码键 + 通知列表）本来就没有广告可跳，但引擎每个新窗口都重开一整条 16 档扫描链。修复：**零候选退避**——同窗连续 3 次零候选且过 2.6s（加载占位微树 2 次/1.2s）即收手；关键是角落形态控件（如扫码键）必须过了广告形态确认门才算「有候选」，校徽页绝不误点；晚到的真跳过键由内容事件救回通道保住，照点不误。顺带：≤5 节点的占位窗（\`树 4 节点 · FrameLayout\`）不再记诊断日志（纯噪声）。

3. **广告关掉后同一秒又扫 79 节点首页（waimaiii）**——复验通过后首页内容还在落定，引擎又整树扫了一遍还记了条未命中。修复：**跳过后静默期**（复验通过 2.2s 内不记未命中）；第二帧广告是独立窗口事件，不受影响。

4. **非主流 App 没有专属规则**——内置规则库 **54 → 102 款**：银行 9（工/农/中/建/招/交/邮储/平安口袋/云闪付）+ 出行 7（12306/智行/航旅纵横/飞常准/哈啰/T3/曹操）+ 阅读 6（微信读书/起点/七猫/番茄畅听/快看/掌阅）+ 新闻 4 + 工具 7（含日志实锤的百度贴吧）+ 生活 7 + 学习办公 4 + 音乐社交 6（飞书/剪映/美图秀秀等）。规则统一「跳过文本 + desc + vid」三件套，包名精确匹配（写错的包名 = 永不生效的死规则，零误点面）。

5. **安全性同步提升**——① 诱导词黑名单扩容（抢购/秒杀/礼包/优惠/补贴/立减/低价/充值）：「跳过领礼包」类诱饵标签绝不代点；② **支付/资金页零扫描**：树内出现「支付密码 / 付款码 / 收银台 / 刷脸支付」即整窗不扫不点不学（与验证码横幅同一防线）——商店首页里的 \`通知\`/\`全部频道\`/\`搜索\` 等控件本次也专门加了防误触测试。

**质量**：314 项 JVM 断言全绿（新增「非主流 App 模拟」套件 130 项：4 档机型 × 六类日志实锤形态逐帧复刻——美团 Dsp 热区+CTA+close 键 / DynamicDialog 双变体 / 校徽页 135 节点 / 商店首页 145 节点 / www AdsActivity / 外卖渐进加载 4→16→79 节点）。

升级方式：[v5.1.0 APK 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.1.0/adshield-v5.1.0.apk)（同签名覆盖安装）。欢迎再传日志——特别是之前跳不过的那些非主流 App，新指纹命中时会打 \`🎯 SDK 广告窗识别\` 日志。`;

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
  for (const n of [46, 47]) await reply(n);
}

main();
