import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-v7" };

/* ── GitHub 面板描述（仓库 description 字段）同步 v7.9.5：真实世界证据层轮
 * 用户指令：完成过后同步 GitHub 和推送 Releases + 优化 GitHub 面板描述。
 * 本轮主题：真实 App → 真实广告 → 候选 → Decision Evidence → SafetyGuard
 * → HumanTap → VERIFY → 真实结果 → 样本沉淀。面板描述按「一句定位 + 本轮
 * 主题 + 证据链承诺 + 常青能力 + 校验口径」五段式重写——信息密度优先，
 * 去除冗长堆叠，真机诚实口径（REAL_DEVICE_TEST=NOT_AVAILABLE）入文案。 */
const desc = "无障碍驱动的安卓开屏广告秒跳引擎 · v7.9.5 真实世界证据层：点击决策证据（最小结构化·隐私红线——无树/无截图/白名单标签闸）+ 点击结果六态闭环（dispatchGesture 成功 ≠ 广告关闭）+ 订阅证据链八格 + WebView 综合证据路径 + 视频开屏自适应轮询 · 30 套件 6601 断言全绿 · L3 真机批次 1 已定义（50 款 12 类配额·REAL_DEVICE_TEST=NOT_AVAILABLE 诚实标记）· 失败样本库建库（FALSE_CLICK > DUPLICATE_CLICK > MISS 优先级）· 免 Root · APK 848KB · 更新链 SHA-256 校验 · 396 机型十维矩阵（使用前请阅 README 免责声明）";
const dr = await fetch(`https://api.github.com/repos/${REPO}`, {
  method: "PATCH",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ description: desc }),
});
console.log("DESC UPDATE", dr.ok ? "OK" : "FAIL " + dr.status);

/* ── Release notes 正文（v7.9.5：真实世界证据层）──
 * 与 App 内 core.ReleaseNotes 解析器同源：前 5 条带序号功能行就地渲染。
 * 规范：1 标题 1 段落 + 5 条带序号功能（每条 ≤44 字）。 */
const body = `开屏卫士 v7.9.5：真实世界证据层——用户指令主线「真实 App → 真实广告 → 候选 → Decision Evidence → SafetyGuard → HumanTap → VERIFY → 真实结果 → 样本沉淀 → 失败案例进入下一轮修复」。本轮不以增加 JVM 断言数量为目标，把证据层建进生产链路：新核心模块 core/DecisionEvidence（纯 Java · 零 Android 依赖）——每次准备点击前记录最小结构化决策证据（包/Activity/任务ID/代际/任务状态 · 候选来源 text/contentDescription/resourceId/imageCorner · 上下文四格 adContext/sdkContext/webViewContext/subscriptionContext · 归一化几何 position/size/screenRegion/clickable · SafetyGuard 裁决/finalDecision）。隐私红线结构性强制（不是约定而是模块能力面）：不保存完整 Accessibility Tree（模块无树容器）、不上传原始屏幕截图（零位图引用）、账号/手机号/支付/聊天内容一律白名单闸拒收（候选标签只保留 close/skip 词族成员 ≤12 字符——实测手机号/账号文本/支付文本全部空串拒收）、resourceId ≤32 字符资源名形态校验、环形缓冲有界 12 条、证据链异常全吞绝不影响点击主链。点击结果六态闭环：CLICK_DISPATCHED → VERIFYING → VERIFIED_SUCCESS/RETRY/MISS/FALSE_CLICK/SAFE_BLOCK/UNKNOWN——dispatchGesture 成功绝不等于广告关闭（唯一成功证据是验证链：两轮零候选 ≥350ms/窗口切换/反馈面板收起三路汇合）；FALSE_CLICK 自动化环境恒 0（真机样本回传唯一来源，绝不虚报）；上传日志新增「点击结果」「点击证据」两行直接可读。GKD 订阅证据链八格：consulted/matched/candidateAccepted/clicked/verifiedSuccess/clickFail/falseClick/blocked——matchSources 被咨询即计数（订阅参与度与命中解耦——内置探针命中不饿死订阅的反饥饿证据面）、候选过 SafetyGuard 终闸计数、订阅出手的任务 VERIFIED_SUCCESS 计数（点击→广告真关闭闭环）。WebView 综合证据路径（真广告+ScrollView/Input/WebView 专项——v7.9.4 已知 isAdLikeLoose 过强阻断）：WebView 容器在场且至少一个广告语境证据（角标/SDK 窗指纹/Dialog 形态/热区）同窗才出手——不能简单取消安全门而是综合判断；WebView 单独在场不构成证据（应用内浏览器普通页零出手——误触红线保持）。视频开屏自适应轮询（20s ceiling 保持）：低证据降频（连续 ≥4 针探针零命中 → 1.5s→3s，不再固定高频扫整个观察窗）、探针命中/出手恢复标准节拍、证据消失立即终止。L3 真机批次 1 定义（不按列表前 50——12/12 类配额 + video-splash 全收 6 + 引擎覆盖四档交叉 43/39/11/3 + P0/P1/P2 混合，每款 14 项记录字段全部 PENDING_REAL_DEVICE）+ 失败样本库 compatibility/samples/（修复优先级 FALSE_CLICK > DUPLICATE_CLICK > MISS > 命中率——安全 > 正确关闭 > 命中率 > 性能微优化）。REAL_DEVICE_TEST = NOT_AVAILABLE（本机无真机，绝不虚报执行结果——L1/L2/证据层全绿 ≠ 真机验证）。极简窗口专项批次（8 类交互图标 × 四角在普通 UI 全零出手 + 树极简兜底契约 + FAB 防线 + 时机闸）。30 套件 6601 项 JVM 断言全绿（+114 全部对应新生产面与用户指令批次；既有 6487 项零回退）。versionCode 79500 > 79400——覆盖安装即升级（无障碍授权/学习数据/白名单/已导入订阅全部保留）。

1. 点击决策证据：候选来源/上下文四格/几何/安全裁决——隐私红线结构性强制
2. 点击结果六态闭环：dispatchGesture 成功 ≠ 广告关闭——唯一成功证据是验证链
3. 订阅证据链八格 + WebView 综合证据路径（真广告+ScrollView/Input 出口）
4. 视频开屏自适应轮询低证据降频 + L3 批次 1 定义 + 失败样本库建库
5. 30 套件 6601 断言全绿（+114 全对应新生产面；既有零回退）

> 升级方式：[adshield-v7.9.5.apk](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.5/adshield-v7.9.5.apk) 同签名直接覆盖安装（versionCode 79500——证据层与综合路径即刻生效；无障碍授权/学习数据/白名单/已导入订阅全部保留）。App 内「我的 → 检查更新」也会发现本版并页内直下（安装前自动校验 SHA-256——下方校验表即比对基准）。

> 诚实口径：REAL_DEVICE_TEST = NOT_AVAILABLE（本机无真机）——L3 批次 1 已定义未执行；真机测试/广告样本/成功关闭/漏检/误触/重复点击当前全部 0（没有真数据就没有结论）；失败样本库空库（0 条）。L3 真机数据必须回传日志后录入，绝不虚报。

| 校验 | 值 |
| --- | --- |
| SHA-256 | \`118ab150b47149ab83b19ed53ee3c71c1a208b6740d501177b1472900e6c137c\` |
| MD5 | \`bed29adc43bc1769c44c7c5a588fa86c\` |
| 体积 | 865,846 B（848KB，含三源离线订阅快照 ~520KB） |
| versionCode / versionName | 79500 / 7.9.5 |
| minSdk / targetSdk | 24 / 34 |`;

const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({
    tag_name: "v7.9.5",
    name: "开屏卫士 v7.9.5 · 真实世界证据层：点击决策证据 + 点击结果六态闭环 + WebView 综合证据路径",
    body,
  }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);

const apk = readFileSync("/home/z/skip-ad-repo/android-app/dist/adshield-v7.9.5.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v7.9.5.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/vnd.android.package-archive" },
  body: apk,
});
console.log("ASSET UPLOAD", up.ok ? "OK" : "FAIL " + up.status);

/* digest 核对（App 安装前 SHA-256 校验链——发布侧基准闭环） */
const got = await fetch(`https://api.github.com/repos/${REPO}/releases/${rel.id}`, { headers });
const rel2 = await got.json();
for (const a of rel2.assets ?? []) {
  if (a.name === "adshield-v7.9.5.apk") {
    console.log("ASSET digest", a.digest, "size", a.size, "state", a.state);
    const local = await import("crypto").then(c => c.createHash("sha256").update(apk).digest("hex"));
    console.log("LOCAL sha256", local, "MATCH", a.digest === "sha256:" + local ? "YES" : "NO");
  }
}
