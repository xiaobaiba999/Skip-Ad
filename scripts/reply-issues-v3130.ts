/** v3.13.0 issue 回复：#16~#20（v3.12.0 引擎日志上传，全部已由 v3.13.0 处置）*/
import { getGithubToken } from "../src/lib/credentials";

const TOKEN = getGithubToken();
const REPO = "xiaobaiba999/Skip-Ad";
const API = `https://api.github.com/repos/${REPO}`;
const H = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const RELEASE = "https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v3.13.0";

function core(intro: string, findings: string[]) {
  return [
    intro,
    "",
    "## 日志分析结论 + v3.13.0 处置（已发布）",
    "",
    ...findings,
    "",
    "### 通用修复（同样来自这批日志）",
    "",
    "- **验证码横幅四重防护**（本轮用户直接反馈的顶级事故：接收验证码时顶部横幅被点掉）——短信/来电类覆盖层包事件直接丢弃；验证码文案窗口绝不扫描；触点被横幅覆盖改走无障碍代点；横幅在场绝不派返回键",
    "- **息屏休眠 + 夜间心跳降频**——息屏不扫描/不点击/不学习（来电/闹钟窗零误点）；心跳改 30~45 分钟非精确闹钟，**不再每 5 分钟把设备从 Doze 唤醒（此前一晚唤醒 96 次，夜间耗电元凶）**",
    "- **误触观察口径修正**——「快于人类反应」启发式移除（熟练用户 500ms 手点是正常秒跳不是误触），仅保留全屏热区观察",
    "- **学习中心「分应用统计」卡整体移除**（用户反馈看不懂还乱），只留引擎日志与能耗面板",
    "",
    "### 验证",
    "",
    "- JVM 139 用例全绿（新增防护 31 例）；agent-browser + VLM 三页视觉核查通过",
    "- 升级：应用内「关于 → 检查更新」直装，或 " + RELEASE + " 下载 APK 覆盖安装（学习数据全保留）",
    "",
    "感谢这份高质量日志，直接推动了本轮全部修复。已随 v3.13.0 发布处置完毕，先关闭了；如升级后仍有问题，欢迎新开 issue 附引擎日志。",
  ].join("\n");
}

const replies: Record<number, { body: string }[]> = {
  16: [{
    body: core(
      "这份日志信息量很大——三条独立问题全部在 v3.13.0 处置完毕。",
      [
        "1. **安全中心被学出「跳过键」**：`com.miui.securitycenter` 应用详情页被学出位置 `(91%,9%)@ApplicationsDetailsActivity`——下次进详情页 500ms 直点 = 在权限管理页乱点。v3.13.0：OEM 工具包（miui/securitycenter/systemmanager/safecenter/permissionmanager/finddevice 族）位置学习**永久拒收**，且启动时自动清理已误学规则（你这台设备上的 3 条 securitycenter 位置规则升级后会被自动清掉）。",
        "2. **越界位置拒绝刷屏**：`(90%,103%)`、`(10%,103%)`、`(70%,101%)` 30 秒内刷了 6 条拒绝日志——手势导航条区域误触被反复尝试学习。拒绝逻辑本身是对的（越界点了也是死区），v3.13.0 加了同包 30s 去重，不再刷屏。",
        "3. **误触观察 48 条**：几乎全是「窗口切换后 X ms 内点击」——那是引擎和你自己在正常秒跳，不是误触。v3.13.0 移除该口径，仅保留全屏热区观察。",
      ],
    ),
  }],
  17: [{
    body: core(
      "日志已复盘——两条观察 + 一处确认正常，处置如下。",
      [
        "1. **误触观察 9 条**：全部是「窗口切换后 195~696ms 内点击」——熟练用户等在跳过键上、窗口一切换就点，这正是本应用的目标体验，不是误触。v3.13.0 移除「快于人类反应」口径，仅保留全屏热区观察（真信号）。",
        "2. **plugin 包角落键连点 ×3 未复验通过**：角落图片跳过键的点击梯子兜底在工作（换候选重试），属设计内行为；v3.13.0 的验证码横幅防护与 OEM 拒学会进一步减少这类无谓出手场景。",
        "3. **喜马拉雅「跳过 05」、贴吧倒计时门控命中后复验通过**：学习与门控链路工作正常，无需处置。",
      ],
    ),
  }],
  18: [{
    body: core(
      "日志已复盘——最有价值的是凌晨时段的活动记录，直接坐实了待机功耗问题。",
      [
        "1. **凌晨 05:13 / 07:12 / 08:13 仍在扫描与点击**：这是「夜间待机功耗偏高」的直接证据。v3.13.0 息屏休眠门：息屏时不扫描/不点击/不学习（息屏没有可见广告，此时出手只可能误点来电/闹钟等敏感窗）；心跳闹钟从 5 分钟精确（一晚唤醒 96 次）改为息屏 30~45 分钟非精确窗口式——**待机唤醒次数从 96 次/晚降到 ≤16 次/晚**。",
        "2. **securitycenter 位置学习 (91%,9%) 与 xhs 粉丝群审批页位置规则**：安全中心属 OEM 工具包，v3.13.0 永久拒收并启动清理；xhs 的 3 条位置在审批页右上/页内小控件上（疑似该页关闭键），暂按正常学习保留——如升级后在该页出现误点，请再上传日志。",
        "3. **喜马拉雅 SplashAdActivity 返回键被拦截后换点击梯子 2 次命中**：兜底链路按设计工作（SDK 窗先 BACK 温和关闭 → 被拦 → 点击梯子兜底），无需处置。",
      ],
    ),
  }],
  19: [{
    body: core(
      "与 #18 同设备的连续日志，结论一致——夜间活动记录直接推动了 v3.13.0 的功耗治理。",
      [
        "1. **凌晨 05:13/07:12/08:13 的扫描与点击**：v3.13.0 息屏休眠门已根治（息屏不扫描/不点击/不学习 + 手势派发息屏熔断双保险）。",
        "2. **心跳一晚唤醒 96 次**：改息屏 30~45 分钟窗口式非精确闹钟，系统批量调度进 Doze 维护窗，绝不主动唤醒设备。",
        "3. **securitycenter / xhs 位置规则与误触观察口径**：同 #18 处置（OEM 工具包拒收+清理 / 误触口径修正）。",
      ],
    ),
  }],
  20: [{
    body: core(
      "第三份同设备日志，与 #18/#19 互相印证——全部发现在 v3.13.0 处置完毕。",
      [
        "1. **凌晨与早晨的息屏期扫描**：v3.13.0 息屏休眠门根治；夜间心跳降频（96 次/晚 → ≤16 次/晚）。",
        "2. **误触观察 60 条口径失真**：移除「快于人类反应」启发式，仅保留全屏热区观察。",
        "3. **securitycenter 误学位置**：OEM 工具包永久拒收 + 升级启动自动清理（本机 3 条将被清除）。",
      ],
    ),
  }],
};

async function main() {
  for (const [no, items] of Object.entries(replies)) {
    for (const c of items) {
      const r = await fetch(`${API}/issues/${no}/comments`, {
        method: "POST",
        headers: { ...H, "Content-Type": "application/json" },
        body: JSON.stringify({ body: c.body }),
      });
      console.log(r.ok ? `✓ 已回复 issue#${no}` : `✗ reply#${no} ${r.status}: ${(await r.json()).message}`);
    }
    const cl = await fetch(`${API}/issues/${no}`, {
      method: "PATCH",
      headers: { ...H, "Content-Type": "application/json" },
      body: JSON.stringify({ state: "closed", state_reason: "completed" }),
    });
    console.log(cl.ok ? `✓ 已关闭 issue#${no}` : `✗ close#${no} ${cl.status}`);
  }
}

await main();
