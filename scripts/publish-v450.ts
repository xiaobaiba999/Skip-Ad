/** 发布 v4.5.0 Release（乱点根治 · 系统开关彻底解耦 · 原生探针提速 · 安全加固） */
import { getGithubToken } from "../src/lib/credentials";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const H = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const TAG = "v4.5.0";
const APK = "/home/z/skip-ad-repo/android-app/dist/adshield-v4.5.0.apk";
const APK_NAME = "adshield-v4.5.0.apk";
const md5 = createHash("md5").update(readFileSync(APK)).digest("hex");

const notes = `# 开屏卫士 v4.5.0 · 乱点根治 · 系统开关彻底解耦 · 原生探针提速 · 安全加固

依据 [issue#42](https://github.com/xiaobaiba999/Skip-Ad/issues/42) / [#43](https://github.com/xiaobaiba999/Skip-Ad/issues/43) / [#44](https://github.com/xiaobaiba999/Skip-Ad/issues/44) v4.4.0 日志实锤（\`android(49/0/10)\` 战绩歧义 + \`BaseCustomDialogFragment\` 抽屉菜单里「跳过」列表项被连点）+ 用户五项要求，参考李跳跳形状白名单方法论逐条根因修复。**本轮主题：绝不乱点，开关解耦，更快更安全。**

## 1. 乱点根治（三重防线 · issue#42-44 实锤「进入 App 仍会乱点」）

**根因实锤**：用户点开右侧抽屉菜单（播放历史/录音/扫一扫/切换模式），4 秒让路期一过，引擎把菜单里的「跳过」字样列表项当跳过键连点 2 次——纯文案等值匹配无法区分「菜单项」与「真跳过键」。

- **① 文本命中形状门**：真跳过键是孤立紧凑控件——标签宽度 ≤55% 屏（自身可点的长条按钮放宽到 70%，部分广告底部「点击跳过」长条）；全宽行 = 列表项/横幅，绝不点
- **② 竖列菜单检测（isMenuListRow）**：候选所在行与 ≥3 个兄弟行【同列同宽竖排】= 抽屉/菜单列表，一票否决（行由统一模板渲染必然同宽；⚠ 比较对象是「祖先行 vs 兄弟行」而非「文字 vs 行」——文字总比行窄）。**rule 引擎与 walk→decide 兜底两条路径全覆盖**，SDK 广告窗/学习窗豁免
- **③ 你点开的弹窗/抽屉 30s 零动作**：用户 2.5s 内真实点击后弹出的 Dialog = 用户主动打开（菜单/分享面板/设置弹窗），30s 引擎绝不动作（此前只有 4s 让路，期满就点 = 乱点时序实锤）
- **横排按钮组不误伤**（同宽但同一行 ≠ 竖列）· **3 行短列表不误伤**（<4 行阈值，宁漏勿错杀）· **右下角跳过键/倒计时/vid/英文 Skip 全部不回退**

## 2. 系统开关与 App 开关彻底解耦（用户核心诉求）

> 「实现在系统外有 app 控制，系统一直打开状态，由开屏卫士控制，卫士关闭就不再触发广告跳转，打开就是打开了，也不用跳转到系统里重新打开权限。」

- **系统无障碍开关零触碰**：全代码库零 \`disableSelf\`（本轮白盒断言 W1），服务被解绑后 \`onUnbind\` 保留实例等系统重绑——10 秒强制确认只存在于「用户自己去系统设置关掉再开」这一条路，App 的任何开关都不走那条路
- **通知栏一键暂停/恢复**（新增）：常驻通知栏新增开关按钮，点「暂停防护」立即全停（零扫描零点击零学习，服务保活不受影响），再点「恢复防护」立即守护——**全程无系统交互**。接收器 exported=false + 无 intent-filter，第三方无法伪造
- **App 内开关即开即用**：暂停/恢复均不碰系统设置；首页文案明确引导「关闭防护请用此开关/通知栏按钮（别去系统设置关，重开要看 10 秒警告）」
- **打开 App 自动拉活**：onResume 检测服务掉线（系统设置仍登记）→ 后台深挖 6s 每半秒踢一次 AMS 重绑——「打开就是打开了」

## 3. 弹窗屏蔽开关「自动弹回」根治（issue 直报：开启之后又被 App 自动关闭）

**根因**：状态竞态——UI 开关走 \`kvSet("state")\` 只写 SharedPreferences，而服务持有 stateInMemory 旧副本，任何战绩/日志更新触发的 15s 延迟落盘都会用旧内存态**整体覆盖** UI 写入 → 开关几秒到几分钟内「自动」弹回。
**修复**：开关类设置（弹窗屏蔽/习惯学习）一律走 \`setStateBool\` 专用通道——服务活着直接改服务内存本体（唯一数据源，落盘自然带新值），UI 不再直接写 state 开关字段。

## 4. 提速（参考李跳跳核心路径）

- **原生文本探针**：\`findAccessibilityNodeInfosByText\` 系统原生子树搜索、**无节点预算限制**——H5/WebView 深树（500+ 节点）里 600 预算的 DFS 常在按钮前耗尽预算（「有时候触发不了」残余元凶），DFS 未命中时原生探针「跳过/跳過」双探兜底，复用 matchText 全套规则+形状门（零新乱点面）
- **内联首扫**：0ms 档从「postDelayed 排队等下一轮消息循环」改为窗口事件现场同步出手（省一跳 handler 约 5~16ms）

## 5. 其他修复

- **日志「android」歧义**：\`me.ele.android\`（饿了么）等以 .android 结尾的第三方包此前在日志/战绩榜全部显示为「android」（\`android(49/0/10)\` 实为第三方应用战绩，非系统包乱点）——末段为通用词时显示两段（Java+JS 双端）
- **反馈面板收起后杀链**：跳过后弹出的「不喜欢/反馈」面板在场 = 广告已关铁证 → 本窗固定扫描档全部取消（此前面板收起后 12s 开屏窗仍活着，扫描链继续在后续界面找「跳过」文案 = 广告关了又点第二下）
- **安全加固**：WebView URL 白名单（仅放行 asset 目录，http(s) 转系统浏览器零桥暴露，file 外/intent/javascript 全拦截）· WebSettings 六项显式关闭（跨文件/跨域/自动开窗/地理定位/表单保存/远程调试）· 下载源 https+域名白名单+文件名净化 · 订阅源强制 https（明文 http 可被投毒）· 反馈通道频控 15s+长度上限 · kvSet 256KB 上限

## 验证（279 项断言全绿）

| 套件 | 断言 | 覆盖 |
|---|---|---|
| 单元 V450Test | 27 | issue#43/44 抽屉原场景复现（三路零动作）· 形状门矩阵 · 主路径回归 · 探针路径 |
| 回归 RuleEngine/DecisionCore | 211 | v3.10.0 以来全部规则与决策用例 |
| 白盒（APK 结构） | 17 | 零 disableSelf · 接收器 exported=false · WebView 硬化 · 字节级文案验证 |
| 黑盒（模拟桥） | 14 | setStateBool 竞态 · shortPkgName 歧义 · 产物内容 |
| 整体（签名/资产） | 10 | v1+v2 签名 + 指纹一致（覆盖升级）· 263KB · VC 40500 |

- APK MD5: \`${md5}\`
- 覆盖安装（同签名，versionCode 40500）
`;

async function main() {
  // 1. 创建 release
  const cr = await fetch(`https://api.github.com/repos/xiaobaiba999/Skip-Ad/releases`, {
    method: "POST",
    headers: H,
    body: JSON.stringify({
      tag_name: TAG,
      name: `开屏卫士 v${TAG.slice(1)} · 乱点根治 · 系统开关彻底解耦 · 原生探针提速`,
      body: notes,
      draft: false,
      prerelease: false,
    }),
  });
  const rel = await cr.json();
  if (!cr.ok) {
    console.log("创建失败:", cr.status, JSON.stringify(rel).slice(0, 300));
    process.exit(1);
  }
  console.log("Release 创建成功:", rel.html_url);

  // 2. 上传 APK
  const apk = readFileSync(APK);
  const up = await fetch(rel.upload_url.replace("{?name,label}", `?name=${APK_NAME}`), {
    method: "POST",
    headers: { ...H, "Content-Type": "application/vnd.android.package-archive" },
    body: apk,
  });
  if (!up.ok) {
    console.log("APK 上传失败:", up.status, (await up.text()).slice(0, 200));
    process.exit(1);
  }
  console.log("APK 上传成功:", APK_NAME, apk.length, "bytes");
}

main();
