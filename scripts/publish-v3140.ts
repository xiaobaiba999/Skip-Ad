/** v3.14.0 发布：创建 Tag+Release（含 APK 附件），回复并关闭 issue#21 */
import { getGithubToken } from "../src/lib/credentials";
import { readFileSync } from "node:fs";

const TOKEN = getGithubToken();
const REPO = "xiaobaiba999/Skip-Ad";
const API = `https://api.github.com/repos/${REPO}`;
const UPLOADS = `https://uploads.github.com/repos/${REPO}`;
const SHA = "7f3ddbfdc07e9e3bb21f092f804d694545428a8d";
const APK_PATH = "/home/z/skip-ad-repo/android-app/dist/adshield-v3.14.0.apk";
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};

const notes = readFileSync("/home/z/skip-ad-repo/scripts/release-tmp/v3140-notes.md", "utf8");

async function main() {
  // 1. Tag v3.14.0
  const tag = await fetch(`${API}/git/refs/tags/v3.14.0`, { headers: H });
  if (tag.status === 404) {
    const t = await fetch(`${API}/git/tags`, {
      method: "POST",
      headers: { ...H, "Content-Type": "application/json" },
      body: JSON.stringify({
        tag: "v3.14.0",
        message: "v3.14.0 跳过后反馈弹窗自动收起+秒跳提速+ADB 自愈移除（issue#21）",
        object: SHA,
        type: "commit",
      }),
    });
    console.log(t.ok ? "✓ Tag v3.14.0 已创建" : `✗ tag ${t.status}: ${(await t.json()).message}`);
  } else {
    console.log("✓ Tag v3.14.0 已存在");
  }

  // 2. Release（latest）
  const rel = await fetch(`${API}/releases`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({
      tag_name: "v3.14.0",
      name: "Skip-Ad v3.14.0 · 跳过后反馈弹窗自动收起 · 秒跳再提速 · ADB 自愈移除",
      body: notes,
      draft: false,
      prerelease: false,
      make_latest: "true",
    }),
  });
  const rd = (await rel.json()) as { id?: number; html_url?: string; message?: string };
  console.log(rel.ok ? `✓ Release 已发布：${rd.html_url}` : `✗ release ${rel.status}: ${rd.message}`);
  if (!rel.ok || !rd.id) process.exit(1);

  // 3. 上传 APK 附件
  const apk = readFileSync(APK_PATH);
  const up = await fetch(`${UPLOADS}/releases/${rd.id}/assets?name=adshield-v3.14.0.apk`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/vnd.android.package-archive" },
    body: new Uint8Array(apk),
  });
  console.log(up.ok ? `✓ APK 已上传（${(apk.length / 1024).toFixed(1)}KB）` : `✗ upload ${up.status}: ${(await up.json()).message}`);

  // 4. 回复 issue#21（日志已分析并全部闭环）
  const reply = [
    "## 日志分析结论（v3.13.0 · 已全部随 v3.14.0 修复）",
    "",
    "感谢上传！这份日志信息量很大，三个问题全部定位：",
    "",
    "### 1. 喜马拉雅（日志中的 `(android)` = com.ximalaya.ting.android）复验风暴 ×2~4 次",
    "- **根因**：跳过开屏后弹出的「不喜欢/反馈投诉」底部面板（你反馈 10 次有 8 次的那个）把广告盖住了——引擎看不到广告关闭，以为没跳成功，连续换候选重试",
    "- **v3.14.0 修复**：反馈面板在场 = 广告已关的铁证 → 立即停用重试 + **自动代点「取消」收起面板**（只点「取消」，绝不替你提交任何反馈选项）。三重安全门：仅 12s 内自动跳过过的应用生效、需强特征（「我为什么会看到广告/反馈投诉」）+ 双特征确认、信息流长按菜单绝不误碰",
    "",
    "### 2. 「手势被系统中断」后重试太慢（最慢 4s+）",
    "- **根因**：系统手势限流取消真实触点后，要等下一轮扫描（1.2~4s）才重试",
    "- **v3.14.0 修复**：中断即退冷却 + **350ms 快速补扫重试**；跳过键晚渲染补扫节流 80→50ms——露头就秒更快",
    "",
    "### 3. 日志噪音（meituan 内容突发 60 秒 3 条同语刷屏）",
    "- **v3.14.0**：同包 30s 去重",
    "",
    "### 顺带说明",
    "- **ADB 自愈授权已整体移除**（要电脑执行太麻烦）：开启服务一步直达系统开关；开启一次持续守护（前台常驻通知+心跳自检+断连提醒），退出 App/清理后台重进无需重新开启",
    "- **关于页**最近三版描述已精简为一句话；首页删除 ADB 卡等冗余字段",
    "- 倒计时形态广告（bili「跳过 5」等）：0ms 首击在你这份日志里实测有效（1s 内关闭），保持首击直点策略；确需门控的（首击未关）仍自动等到激活再出手",
    "",
    "JVM 211 用例全绿（新增反馈弹窗识别 12 例）。**v3.14.0 已发布**：App 内「关于 → 检查更新」可直接升级，学习数据全保留。",
    "",
    "— 开屏卫士 v3.14.0",
  ].join("\n");
  const cm = await fetch(`${API}/issues/21/comments`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ body: reply }),
  });
  console.log(cm.ok ? "✓ issue#21 已回复" : `✗ comment ${cm.status}: ${(await cm.json()).message}`);

  // 5. 关闭 issue#21
  const cl = await fetch(`${API}/issues/21`, {
    method: "PATCH",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ state: "closed" }),
  });
  console.log(cl.ok ? "✓ issue#21 已关闭" : `✗ close ${cl.status}: ${(await cl.json()).message}`);
}

await main();
