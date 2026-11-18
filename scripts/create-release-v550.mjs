import { getGhToken } from "./gh-token.mjs";
import { readFileSync } from "fs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
const body = `## 开屏卫士 v5.5.0 · 全维度优化（UI 交互专项 + 三源规则引擎 + 安全合规）

### 模块一 · UI/交互专项（你最关心的三件事）

1. **版本记录极简化**：App「我的」页与 Web 关于页只展示**当前安装版本**（版本号 / 更新要点 / 发布时间三项），不再堆叠历史版本——历史完整归档在 [Releases](https://github.com/xiaobaiba999/Skip-Ad/releases)
2. **日志上传强反馈**：「上传日志」全程阶段化提示（收集 → 脱敏 → 提交）；成功后**弹窗确认 + 顶部绿色横幅 + 系统通知三重反馈**，明确告知「上传成功，感谢反馈」；失败给出具体原因（401 令牌 / 403 限流 / 网络异常）与一键重试
3. **10 秒警告红线（硬性约束）**：启停仅走 App 内主开关与通知栏快捷键，**绝不跳转系统无障碍设置**——「开启服务」按钮先做状态校验，只要服务仍在系统登记中一律走应用内自愈（15s 自适应深挖拉活）；真被系统吊销才直达开关页并提前告知「10 秒安全确认属正常」

### 模块二 · 核心规则引擎

- **三源规则治理**：官方内嵌 > GKD 社区订阅 > 学习规则三级优先级；动作冲突高优先级胜出、等价规则合并计数、选择器规范化去重（100% GKD 兼容）
- **学习规则生命周期**：新生观察（72h）→ 活跃 → 衰退 → 退休四态管理；质量分（命中深度×0.5 + 成功率×0.3 + 新鲜度×0.2）门控预拦截——低分规则绝不预拦截（误触率只降不升）；SDK 特征泛化（A 应用学会的穿山甲骨架，B 应用同 SDK 广告直接复用）
- **新四大广告场景**：半屏插屏（底部半屏 Dialog 关闭键）/ 更新诱导弹窗（「以后再说/暂不更新」否定侧——仅肯定+否定侧文案**双信号同场**才出手）/ 悬浮广告球（小面积+边缘带+关闭语义三重门）/ 视频前贴（preroll vid 键 + 倒计时形态）
- **李跳跳式分级扫描**：广告窗 600 全量 · 新场景 400 · 普通窗 350（普通窗口预算 -30%，配合探针前置文本键零 DFS）

### 模块三+四 · 性能功耗兼容 + 安全合规

- **设备分级 + 电量联动**：低端机低电量自动降级（扫描预算 ×0.6、突发检测关闭）——省电只砍增益路径，事件驱动 0ms 首扫与探针前置**永不降级**
- **折叠屏/平板/分屏自适应**：宽屏形态按短边归一面积（分屏半屏不再低估一半）
- **零扫描场景扩容**：系统设置页 / 输入法 / 锁屏 / 银行密码页**绝不扫描绝不点击绝不学习**（在既有短信/来电/相机/验证码/支付页之上）
- **动作边界自检**：任何出手前三重断言——跳过/关闭语义 + 非零扫描场景 + 面积 ≤35%；任一不过即放弃（宁可漏拦绝不误触）
- **权限使用审计**：App「我的」页新增 7 项权限逐条展示（用途 + 触发时机），零权限声明置顶（定位/通讯录/存储/相机/麦克风全部未申请）

### 模块五+六+七 · Web 端与工程化

- 调试沙箱新增 3 个场景模板（更新诱导弹窗 / 悬浮广告球 / 视频前贴），与真机引擎第 5 层规则同源
- CI/Release/规则巡检三流水线对齐真实工具链（build.sh + run-tests.sh），版本号双端统一门禁（tag ↔ version.ts 强制一致 + versionCode 公式校验 + 防降级守卫）
- **599 项 JVM 断言全绿**（+124：三源合并 77 项 + 合规守卫 47 项），误触率专项场景全部转化为可回归断言

### 升级方式

[adshield-v5.5.0.apk 直接下载](https://github.com/xiaobaiba999/Skip-Ad/releases/download/v5.5.0/adshield-v5.5.0.apk)（versionCode 50500 > 50300，同签名直接覆盖安装；学习指纹/省电档案全部保留）。旧版 App 内「我的 → 检查更新」也会自动发现本版本并页内直下。`;
const r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/json" },
  body: JSON.stringify({ tag_name: "v5.5.0", name: "开屏卫士 v5.5.0 · 全维度优化：UI 交互专项 · 三源规则引擎 · 安全合规 · 599 断言", body }),
});
const rel = await r.json();
if (!r.ok) { console.log("RELEASE FAIL", rel.message, JSON.stringify(rel.errors ?? "")); process.exit(1); }
console.log("RELEASE OK #" + rel.id, rel.html_url);
const apk = readFileSync("/home/z/my-project/android-app/dist/adshield-v5.5.0.apk");
const up = await fetch(`https://uploads.github.com/repos/${REPO}/releases/${rel.id}/assets?name=adshield-v5.5.0.apk`, {
  method: "POST",
  headers: { ...headers, "Content-Type": "application/octet-stream", "Content-Length": String(apk.length) },
  body: new Uint8Array(apk),
});
const asset = await up.json();
console.log(up.ok ? "ASSET OK " + asset.browser_download_url + " (id " + asset.id + ")" : "ASSET FAIL " + (asset.message ?? up.status));
