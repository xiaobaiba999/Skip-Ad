import { getGithubToken } from "../src/lib/credentials";
const TOKEN = getGithubToken();
const API = `https://api.github.com/repos/xiaobaiba999/Skip-Ad`;
const H: Record<string, string> = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: "application/vnd.github+json",
  "User-Agent": "skip-ad-instant-engine",
};
const reply = [
  "## 日志已分析（该版本问题已随 v4.0.0 → v4.2.0 后续版本修复关闭）",
  "",
  "感谢上传日志！该版本的已知问题在此后版本中已全部修复：",
  "",
  "- **v4.1.0**：微信「拍摄」强退（相机窗硬黑名单）· 灵动岛乱点（三重前台门）· 订阅导入 404（镜像自动回退）· 应用内一键暂停/恢复",
  "- **v4.2.0**：用户操作让路（点开的页面绝不自动关）· 微信内左右乱划（订阅 activityIds 强校验 + 主界面零扫描）· 订阅弹窗场景接驳与限流 · 日志 12h 自动提交 · 功耗整治",
  "",
  "**升级方式**：App 内「我的 → 检查更新」页内直装，或 [Releases v4.2.0](https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v4.2.0)（同签名覆盖安装，学习数据与订阅全保留）。",
  "",
  "— 开屏卫士 v4.2.0",
].join("\n");
for (const n of [24, 25, 26, 27, 28, 29]) {
  const cm = await fetch(`${API}/issues/${n}/comments`, {
    method: "POST",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ body: reply }),
  });
  const cl = await fetch(`${API}/issues/${n}`, {
    method: "PATCH",
    headers: { ...H, "Content-Type": "application/json" },
    body: JSON.stringify({ state: "closed", state_reason: "completed" }),
  });
  console.log(`#${n}: comment ${cm.status}, close ${cl.status}`);
}
