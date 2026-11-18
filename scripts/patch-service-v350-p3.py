#!/usr/bin/env python3
"""AdSkipService v3.5.0 · 第三阶段：位置学习 + 前台保活 + 核心安全门 + 测试 V6"""
import io, sys

P = "/home/z/my-project/android-app/src/com/zai/adshield/AdSkipService.java"
C = "/home/z/my-project/android-app/src/com/zai/adshield/core/AdDecisionCore.java"
T = "/home/z/my-project/scripts/device-tests/DecisionCoreTest.java"

src = io.open(P, encoding="utf-8").read()
core = io.open(C, encoding="utf-8").read()
test = io.open(T, encoding="utf-8").read()

def rep(s, old, new, tag, count=1):
    n = s.count(old)
    if n != count:
        print(f"FAIL [{tag}] 出现 {n} 次（期望 {count}）"); sys.exit(1)
    print(f"OK   [{tag}]")
    return s.replace(old, new)

# ── 1. 核心安全门：学习位置只在「广告形态窗」内直点（信息流等正常界面绝不点） ──
core = rep(core,
    "            // L1.5 学习位置点击（v3.5.0）：手动点过的位置指纹 → 次启同窗 500ms 直点\n            if (ctx.tapHintX >= 0 && ctx.tapHintY >= 0 && ctx.clickAttempts == 0\n                    && ctx.elapsedMs >= LEARNED_POS_MS) {",
    "            // L1.5 学习位置点击（v3.5.0）：手动点过的位置指纹 → 次启同窗 500ms 直点\n            //     安全门：仅在广告形态窗（简单树/SDK 窗/学习窗/有角落候选）内出手，\n            //     信息流等正常界面（可滚动/节点多/有输入框）绝不点\n            if (ctx.tapHintX >= 0 && ctx.tapHintY >= 0 && ctx.clickAttempts == 0\n                    && ctx.elapsedMs >= LEARNED_POS_MS\n                    && (isAdLike(f) || ctx.sdkAdWin || ctx.learnedCls || !f.corners.isEmpty())) {",
    "核心 L1.5 安全门")

# ── 2. 服务：learnFromManualClick 增加零文本位置学习分支 ──
src = rep(src,
    """        String text = "";
        String viewId = "";
        try {
            StringBuilder sb = new StringBuilder();
            if (event.getText() != null) {
                for (CharSequence c : event.getText()) {
                    if (c != null) sb.append(c);
                }
            }
            text = sb.toString().trim();
            AccessibilityNodeInfo src = event.getSource();
            if (src != null) {
                if (text.length() == 0) text = cs(src.getContentDescription());
                viewId = cs(src.getViewIdResourceName());
            }
        } catch (Exception ignored) {
        }
        if (!AdDecisionCore.matchesSkip(text) && !AdDecisionCore.isCloseText(text)) return;""",
    """        String text = "";
        String viewId = "";
        AccessibilityNodeInfo srcNode = null;
        try {
            StringBuilder sb = new StringBuilder();
            if (event.getText() != null) {
                for (CharSequence c : event.getText()) {
                    if (c != null) sb.append(c);
                }
            }
            text = sb.toString().trim();
            AccessibilityNodeInfo src = event.getSource();
            if (src != null) {
                srcNode = src;
                if (text.length() == 0) text = cs(src.getContentDescription());
                viewId = cs(src.getViewIdResourceName());
            }
        } catch (Exception ignored) {
        }
        if (!AdDecisionCore.matchesSkip(text) && !AdDecisionCore.isCloseText(text)) {
            // v3.5.0 位置学习：图片跳过键零文本也要学（真机痛点：「手动记录两次
            // 还是不能跳过」——v3.4.0 只认文本，图片键永远学不到）
            learnPositionFromManualClick(pkg, srcNode, now);
            return;
        }""",
    "手动点击位置学习入口")

# ── 3. 服务：新增位置学习方法 + triesOfClick + 保活通知（插在「统计与日志」前） ──
NEW_METHODS = """    /* ───────── v3.5.0 位置学习：图片跳过键（零文本）教一次就会 ───────── */

    /**
     * 手动点击小角落/顶部控件（零文本）→ 记录归一化坐标 + 窗口类名指纹。
     * 次启同窗 500ms 手势直点该位置（仅广告形态窗内出手，见决策核心 L1.5 安全门）。
     */
    private void learnPositionFromManualClick(String pkg, AccessibilityNodeInfo src, long now) {
        if (src == null || screenWidth <= 0 || screenHeight <= 0) return;
        if (now - winAt(pkg) > 10000) return; // 仅窗口切换后 10s 内（开屏期）
        try {
            Rect r = new Rect();
            src.getBoundsInScreen(r);
            if (r.isEmpty()) return;
            int[] b = new int[]{r.left, r.top, r.right, r.bottom};
            double area = AdDecisionCore.areaOf(b, screenWidth, screenHeight);
            if (area > AdDecisionCore.POS_LEARN_AREA_MAX) return; // 大热区=CTA，不学
            int cy = (b[1] + b[3]) / 2;
            boolean corner = AdDecisionCore.cornerLike(b, screenWidth, screenHeight);
            boolean topArea = cy <= screenHeight * 0.25;
            if (!corner && !topArea) return; // 只学角落/顶部小控件（跳过键形态）
            final double x = ((b[0] + b[2]) / 2.0) / screenWidth;
            final double y = ((b[1] + b[3]) / 2.0) / screenHeight;
            final String cls = winClassOf(pkg);
            synchronized (lock) {
                try {
                    JSONObject st = loadState();
                    JSONObject rules = st.optJSONObject("rules");
                    if (rules == null) {
                        rules = new JSONObject();
                        st.put("rules", rules);
                    }
                    JSONArray list = rules.optJSONArray(pkg);
                    if (list == null) {
                        list = new JSONArray();
                        rules.put(pkg, list);
                    }
                    boolean merged = false;
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject rr = list.optJSONObject(i);
                        if (rr == null || !rr.optBoolean("pos", false)) continue;
                        double dx = Math.abs(rr.optDouble("x", -1) - x);
                        double dy = Math.abs(rr.optDouble("y", -1) - y);
                        boolean sameCls = cls.length() == 0 || cls.equals(rr.optString("cls", ""));
                        if (dx <= 0.02 && dy <= 0.02 && sameCls) {
                            rr.put("manual", rr.optInt("manual", 0) + 1);
                            rr.put("ts", now);
                            rr.put("cls", cls);
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) {
                        if (list.length() >= MAX_RULES_PER_APP) return;
                        JSONObject rr = new JSONObject();
                        rr.put("pos", 1);
                        rr.put("x", Math.round(x * 1000) / 1000.0);
                        rr.put("y", Math.round(y * 1000) / 1000.0);
                        rr.put("cls", cls);
                        rr.put("text", "");
                        rr.put("viewId", "");
                        rr.put("manual", 1);
                        rr.put("auto", 0);
                        rr.put("ts", now);
                        list.put(rr);
                    }
                    bumpManualStat(st, pkg);
                    saveState(st);
                } catch (Exception ignored) {
                }
            }
            appendLog("📍 位置已学习（" + shortPkg(pkg) + "）：图片跳过键零文本，已记住位置 ("
                    + Math.round(x * 100) + "%," + Math.round(y * 100) + "%) → 下次启动同窗 500ms 直点");
        } catch (Exception ignored) {
        }
    }

    /** 学习位置指纹（当前窗口类名匹配、manual>0、最新优先）；无则 {-1,-1} */
    private int[] learnedPos(String pkg) {
        try {
            String cls = winClassOf(pkg);
            if (cls.length() == 0) return new int[]{-1, -1};
            JSONObject rules = loadState().optJSONObject("rules");
            if (rules == null) return new int[]{-1, -1};
            JSONArray list = rules.optJSONArray(pkg);
            if (list == null) return new int[]{-1, -1};
            JSONObject best = null;
            long bestTs = -1;
            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null || !r.optBoolean("pos", false)) continue;
                if (r.optInt("manual", 0) < 1) continue;
                if (!cls.equals(r.optString("cls", ""))) continue;
                long ts = r.optLong("ts", 0);
                if (ts > bestTs) {
                    bestTs = ts;
                    best = r;
                }
            }
            if (best == null) return new int[]{-1, -1};
            int x = (int) Math.round(best.optDouble("x", -1) * screenWidth);
            int y = (int) Math.round(best.optDouble("y", -1) * screenHeight);
            if (x <= 0 || y <= 0 || x >= screenWidth || y >= screenHeight) return new int[]{-1, -1};
            return new int[]{x, y};
        } catch (Exception ignored) {
            return new int[]{-1, -1};
        }
    }

    /** 学习位置命中沉淀 auto 次数 */
    private void bumpPosRule(String pkg) {
        try {
            String cls = winClassOf(pkg);
            synchronized (lock) {
                JSONObject st = loadState();
                JSONObject rules = st.optJSONObject("rules");
                if (rules == null) return;
                JSONArray list = rules.optJSONArray(pkg);
                if (list == null) return;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject r = list.optJSONObject(i);
                    if (r == null || !r.optBoolean("pos", false)) continue;
                    if (cls.equals(r.optString("cls", ""))) {
                        r.put("auto", r.optInt("auto", 0) + 1);
                        saveState(st);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private int triesOfClick(String pkg) {
        Integer n = clickTries.get(pkg);
        return n == null ? 0 : n.intValue();
    }

    /* ───────── v3.5.0 保活：前台常驻通知（系统不杀）+ 断连提醒撤销 ───────── */

    private void startKeepAliveForeground() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("keep", "秒跳引擎守护中",
                        NotificationManager.IMPORTANCE_MIN);
                ch.setDescription("服务保持开启，直到你手动关闭");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(this, "keep");
            } else {
                b = new Notification.Builder(this);
            }
            Intent it = new Intent(this, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentTitle("秒跳引擎守护中")
                    .setContentText("保持开启直到你手动关闭 · 点击进入开屏卫士")
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .setColor(0xFF10B981);
            Notification notif = b.build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_KEEP, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_KEEP, notif);
            }
        } catch (Throwable ignored) {
        }
    }

    private void cancelAlertNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(NOTIF_ALERT);
        } catch (Exception ignored) {
        }
    }

    /* ───────── 统计与日志 ───────── */"""

src = rep(src, "    /* ───────── 统计与日志 ───────── */", NEW_METHODS, "新增方法块")

# ── 4. 测试 V6：信息流界面即便有学习位置指纹也绝不点 ──
test = rep(test,
    """    /** V5：角落图片自身不可点、父容器可点 → 候选以父为代点目标、以子为点击坐标 */""",
    """    /** V6：信息流/正常界面即便有学习位置指纹也绝不点（L1.5 安全门） */
    static void learnedPos_safetyGate() {
        FakeNode root = FakeNode.root(\"com.sina.weibo.HomeActivity\");
        FakeNode feed = FakeNode.node(\"androidx.recyclerview.widget.RecyclerView\", 0, 200, 1080, 2300).scroll();
        FakeNode avatar = FakeNode.node(\"android.widget.ImageView\", 950, 120, 1040, 210).click();
        feed.add(avatar);
        root.add(feed);
        Ctx ctx = splash(800);
        ctx.tapHintX = 995;
        ctx.tapHintY = 165; // 用户在信息流点过头像的位置
        Decision d = run(root, ctx);
        check(\"V6 信息流界面有位置指纹也零动作（安全门）\",
                d.action.equals(AdDecisionCore.A_NONE), \"实际: \" + d.action + \"/\" + d.reason);
    }

    /** V5：角落图片自身不可点、父容器可点 → 候选以父为代点目标、以子为点击坐标 */""",
    "测试 V6 安全门")

test = rep(test,
    "        verify_retryLadder();\n        splash_closeKey();\n        longText_guard();\n        learnedPos_tap();\n        corner_ancestor();",
    "        verify_retryLadder();\n        splash_closeKey();\n        longText_guard();\n        learnedPos_tap();\n        learnedPos_safetyGate();\n        corner_ancestor();",
    "测试 V6 注册")

io.open(P, "w", encoding="utf-8").write(src)
io.open(C, "w", encoding="utf-8").write(core)
io.open(T, "w", encoding="utf-8").write(test)
print("PHASE-3 DONE")
