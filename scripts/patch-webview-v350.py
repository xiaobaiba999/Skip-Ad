#!/usr/bin/env python3
"""APK WebView v3.5.0 改造：绿色 Logo + 保活区 + 引擎日志上传 + 版本信息"""
import io, sys

H = "/home/z/my-project/android-app/assets/www/index.html"
S = "/home/z/my-project/android-app/assets/www/style.css"
J = "/home/z/my-project/android-app/assets/www/app.js"

h = io.open(H, encoding="utf-8").read()
s = io.open(S, encoding="utf-8").read()
j = io.open(J, encoding="utf-8").read()

def rep(f, old, new, tag, count=1):
    n = f.count(old)
    if n != count:
        print(f"FAIL [{tag}] 出现 {n} 次（期望 {count}）"); sys.exit(1)
    print(f"OK   [{tag}]")
    return f.replace(old, new)

# ═══ index.html ═══

# 1. 绿色 Logo（SVG 盾牌+闪电，替换 ⚡ emoji）
h = rep(h,
    '''      <div class="logo">⚡</div>''',
    '''      <div class="logo"><svg viewBox="0 0 48 48" width="24" height="24" fill="none" xmlns="http://www.w3.org/2000/svg"><defs><linearGradient id="lgx" x1="6" y1="3" x2="42" y2="45" gradientUnits="userSpaceOnUse"><stop stop-color="#34d399"/><stop offset="1" stop-color="#059669"/></linearGradient></defs><path d="M24 3 L41 9 C41 26 35 39 24 45 C13 39 7 26 7 9 Z" fill="url(#lgx)"/><path d="M27.5 11 L17 26 h5.5 L20 37 L31 21 h-5.5 Z" fill="#fff"/></svg></div>''',
    "index 绿色Logo")

# 2. 首页服务卡：保活区（服务状态卡内，紧跟开启按钮之后）
h = rep(h,
    '''        <button class="btn btn-primary btn-block" id="btn-open-a11y">开启无障碍服务</button>
        <div class="hint">设置 → 无障碍 → 已下载的应用 → 开屏卫士</div>''',
    '''        <button class="btn btn-primary btn-block" id="btn-open-a11y">开启无障碍服务</button>
        <div class="hint">设置 → 无障碍 → 已下载的应用 → 开屏卫士</div>
        <div class="keep-box" id="keep-box">
          <div class="keep-row">
            <div class="sw-text" style="flex:1;min-width:0">
              <div class="sw-title">服务保活 <span class="tag tag-amber" id="keep-tag" style="margin-left:4px">--</span></div>
              <div class="sw-desc" id="keep-desc">开启服务后自动开启</div>
            </div>
          </div>
          <button class="btn btn-ghost btn-sm btn-block" id="btn-battery" style="margin-top:8px">🔋 加入电池优化白名单（防厂商省电杀服务）</button>
          <div class="hint">Android 会在覆盖安装新版本/重启后关闭无障碍服务（系统安全设计，任何应用无法绕过）。开启保活后：前台常驻通知降低被杀概率；万一断连立即发通知提醒，点按两步恢复，学习数据全部保留。</div>
        </div>''',
    "index 保活区")

# 3. 学习中心：引擎日志卡加「上传日志到 GitHub」按钮 + 结果区
h = rep(h,
    '''        <div class="card-head"><span>引擎日志</span><button class="btn btn-ghost btn-sm" id="btn-refresh-real">刷新</button></div>
        <div class="log-box" id="real-log"></div>''',
    '''        <div class="card-head"><span>引擎日志</span>
          <div style="display:flex;gap:6px;align-items:center">
            <button class="btn btn-ghost btn-sm" id="btn-refresh-real">刷新</button>
            <button class="btn btn-primary btn-sm" id="btn-upload-log">⬆ 上传日志到 GitHub</button>
          </div>
        </div>
        <div class="muted small" style="margin-top:6px">把最近 60 条引擎日志 + 学习规则 + 设备信息一键上传到仓库 Issues（内置加密通道），供下一版迭代参考修复</div>
        <div class="log-box" id="real-log"></div>
        <div id="upload-log-result"></div>
        <div id="upload-log-history"></div>''',
    "index 上传日志按钮")

# ═══ style.css ═══

# 4. logo 绿色化 + 新样式（tag-amber / keep-box / 上传结果）
s = rep(s,
    '''.logo {
  width: 38px; height: 38px; border-radius: 12px; font-size: 20px;
  display: flex; align-items: center; justify-content: center; color: #fff;
  background: linear-gradient(135deg, #8b5cf6, #d946ef);
  box-shadow: 0 4px 10px rgba(139, 92, 246, .3);
}''',
    '''.logo {
  width: 38px; height: 38px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; color: #fff;
  background: linear-gradient(135deg, #10b981, #059669);
  box-shadow: 0 4px 10px rgba(16, 185, 129, .35);
}
.tag-amber { background: #fffbeb; color: #b45309; }
.keep-box { margin-top: 10px; border-top: 1px dashed #e4e4e7; padding-top: 10px; }
.keep-row { display: flex; align-items: center; gap: 8px; }''',
    "style logo绿+keep")

# ═══ app.js ═══

# 5. 首页刷新：保活状态 + 电池按钮
j = rep(j,
    '''  dot.classList.toggle("on", running);
  card.classList.toggle("ok", running);
  $("#btn-open-a11y").style.display = (HAS_BRIDGE && !running) ? "" : "none";
''',
    '''  dot.classList.toggle("on", running);
  card.classList.toggle("ok", running);
  $("#btn-open-a11y").style.display = (HAS_BRIDGE && !running) ? "" : "none";
  refreshKeep(running);
}

/* v3.5.0 服务保活状态 */
function refreshKeep(running) {
  if (!HAS_BRIDGE) { const kb = $("#keep-box"); if (kb) kb.style.display = "none"; return; }
  let keepOn = false, bat = false;
  try { keepOn = AndroidApp.isKeepOn(); bat = AndroidApp.isBatteryWhitelisted(); } catch (e) {}
  const tag = $("#keep-tag");
  const on = keepOn && running;
  tag.textContent = on ? "守护中" : (keepOn ? "待恢复" : "未开启");
  tag.className = "tag " + (on ? "tag-green" : (keepOn ? "tag-amber" : ""));
  $("#keep-desc").textContent = on
    ? "前台常驻通知已挂载；覆盖安装/重启后若被系统关闭，将立即通知你一键恢复"
    : (keepOn ? "服务未运行——点上方按钮开启无障碍服务，保活随之生效" : "开启服务后自动开启：前台常驻通知 + 断连提醒恢复");
  $("#btn-battery").style.display = bat ? "none" : "";
}
if (HAS_BRIDGE) {
  $("#btn-battery").addEventListener("click", () => {
    try {
      const r = JSON.parse(AndroidApp.requestBatteryWhitelist());
      toast(r.already ? "已在白名单中" : (r.ok ? "已跳转系统白名单页，请允许「开屏卫士」" : "当前系统不支持，可到设置→电池手动处理"));
    } catch (e) { toast("跳转失败"); }
  });
''',
    "app.js 保活状态")

# 6. 引擎日志上传（学习中心）
j = rep(j,
    '''$("#btn-refresh-real").addEventListener("click", () => { renderReal(); renderEnergy(); toast("已刷新"); });''',
    '''$("#btn-refresh-real").addEventListener("click", () => { renderReal(); renderEnergy(); toast("已刷新"); });

/* ══════════ v3.5.0 引擎日志一键上传（内置加密通道，供下版迭代参考） ══════════ */
function uploadLogHistory() { return store.get("log_history", []); }
function renderUploadLogHistory() {
  const h = uploadLogHistory();
  if (!h.length) { $("#upload-log-history").innerHTML = ""; return; }
  $("#upload-log-history").innerHTML =
    '<div class="muted small" style="margin-top:12px">我的日志上传（本地记录）</div>' +
    h.slice(-5).reverse().map(x => `
      <div class="rule-item">
        <div class="rule-detail">${esc(x.date)} · 日志 ${x.count} 条</div>
        <div class="muted small">${x.url ? linkBtn(x.url, "Issue #" + x.no) : "未同步"}</div>
      </div>`).join("");
}

$("#btn-upload-log").addEventListener("click", () => {
  const st = getRealState();
  const log = st.log || [];
  const btn = $("#btn-upload-log");
  const box = $("#upload-log-result");
  const rules = st.rules || {};
  let ruleCount = 0, pkgCount = 0;
  Object.keys(rules).forEach(k => { const n = (rules[k] || []).length; ruleCount += n; if (n) pkgCount++; });
  if (log.length === 0) {
    box.innerHTML = '<div class="fb-msg err">暂无日志：先开启服务并使用一会儿，或点「刷新」</div>';
    return;
  }
  let dev = {}; try { dev = JSON.parse(AndroidApp.deviceInfo()); } catch (e) {}
  let running = false; try { running = AndroidApp.isServiceRunning(); } catch (e) {}
  let bat = false; try { bat = AndroidApp.isBatteryWhitelisted(); } catch (e) {}
  const s = st.stats || {};
  const logLines = log.slice(-60).map(l => {
    const d = new Date(l.t || Date.now());
    return "- " + d.toLocaleString("zh-CN", { hour12: false }) + " " + (l.msg || "");
  }).join("\\n");
  const ruleLines = Object.keys(rules).map(pkg =>
    "- " + pkg + "：" + (rules[pkg] || []).map(r =>
      r.pos ? "位置(" + Math.round((r.x || 0) * 100) + "%," + Math.round((r.y || 0) * 100) + "%)" + (r.cls ? "@" + r.cls.split(".").pop() : "")
            : (r.cls ? "窗指纹 " + r.cls : "文本「" + (r.text || "-") + "」"))
      .join(" | ")).join("\\n");
  const title = "[引擎日志][v" + appVer() + "] " + (dev.brand || "") + " " + (dev.model || "设备") + " · " + (dev.os || "") + "（手动上传）";
  const body = [
    "## 环境信息（自动附加）",
    "- 设备：" + (dev.brand || "?") + " " + (dev.model || "?") + " · " + (dev.os || "?") + "（SDK " + (dev.sdk || "?") + "）",
    "- 版本：v" + appVer() + "（code " + appCode() + "）· 服务：" + (running ? "运行中" : "未运行") + " · 电池白名单：" + (bat ? "已加入" : "未加入"),
    "- 战绩：秒跳 " + (s.auto || 0) + " · 直断 " + (s.back || 0) + " · 弹窗 " + (s.popup || 0) + " · 回弹 " + (s.chain || 0) + " · 手动样本 " + (s.manual || 0) + " · 误触观察 " + (s.misclick || 0),
    "- 学习规则：" + ruleCount + " 条（" + pkgCount + " 个应用）",
    "",
    "## 学习规则明细",
    ruleLines || "- （无）",
    "",
    "## 引擎日志（最近 " + Math.min(60, log.length) + " 条）",
    logLines,
    "",
    "> 本 Issue 由「学习中心 → 引擎日志 → 上传日志到 GitHub」手动创建（内置加密通道），供下一版迭代参考。",
    "",
    "— 开屏卫士 v" + appVer(),
  ].join("\\n");

  box.innerHTML = '<div class="fb-msg">正在通过内置加密通道上传（最长约 15s，请勿关闭页面）…</div>';
  btn.disabled = true;
  setTimeout(() => {
    let res = null;
    try { res = JSON.parse(AndroidApp.netPostIssue(title, body)); }
    catch (e) { res = { ok: false, error: "提交异常：" + (e.message || e) }; }
    btn.disabled = false;
    const h = uploadLogHistory();
    h.push({ date: new Date().toLocaleString("zh-CN", { hour12: false }), count: Math.min(60, log.length), url: res && res.ok ? res.url : "", no: (res && res.number) || 0 });
    store.set("log_history", h.slice(-10));
    renderUploadLogHistory();
    if (res && res.ok) {
      box.innerHTML = '<div class="fb-msg ok">日志已上传 ' + linkBtn(res.url, "Issue #" + res.number + " 查看") + "，感谢！它会直接成为下一版迭代的修复依据</div>";
      toast("引擎日志已上传到 GitHub");
    } else {
      box.innerHTML = '<div class="fb-msg err">' + esc((res && res.error) || "未知错误") + "</div>";
    }
  }, 60);
});
renderUploadLogHistory();''',
    "app.js 日志上传")

io.open(H, "w", encoding="utf-8").write(h)
io.open(S, "w", encoding="utf-8").write(s)
io.open(J, "w", encoding="utf-8").write(j)
print("WEBVIEW DONE")
