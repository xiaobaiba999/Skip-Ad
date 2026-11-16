#!/usr/bin/env python3
"""AdSkipService v3.5.0 改造 · 第二阶段：执行链复验 + 位置学习 + 前台保活"""
import io, sys

P = "/home/z/my-project/android-app/src/com/zai/adshield/AdSkipService.java"
src = io.open(P, encoding="utf-8").read()

def rep(old, new, tag, count=1):
    global src
    n = src.count(old)
    if n != count:
        print(f"FAIL [{tag}] 出现 {n} 次（期望 {count}）"); sys.exit(1)
    src = src.replace(old, new)
    print(f"OK   [{tag}]")

# 1. execute()：CLICK_BOUNDS 路径——复验不熔断 + 第 N 次强制手势 + 逐次日志
rep("""        if (d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) || d.action.equals(AdDecisionCore.A_CLOSE)) {
            if (!clickCoolOK) return false;
            if (clickNode(d)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                if (d.action.equals(AdDecisionCore.A_CLOSE)) {
                    popupBlocks++;
                    lastPopupAt.put(pkg, now);
                    appendLog("🪟 弹窗广告已关闭（" + shortPkg(pkg) + "）："
                            + (f.adBadge ? "「" + f.badgeLabel + "」角标" : "Dialog+营销话术")
                            + " + 关闭键「" + f.closeLabel + "」");
                    bumpStat(pkg, "popup");
                } else {
                    appendLog("⚡ 已点击跳过（" + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                            + "（" + (lastGesture ? "手势点击" : "无障碍代点") + "）");
                    bumpStat(pkg, "auto");
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                }
                fuseChain(pkg);
                return true;
            }
            return false;
        }""",
    """        if (d.action.equals(AdDecisionCore.A_CLICK_BOUNDS) || d.action.equals(AdDecisionCore.A_CLOSE)) {
            if (!clickCoolOK) return false;
            int attempts = triesOfClick(pkg);
            // v3.5.0 复验重试：第 1 次 performAction→手势降级；第 ≥2 次一律手势真实触点
            if (clickNode(d, attempts >= 1)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                clickTries.put(pkg, attempts + 1);
                if (d.action.equals(AdDecisionCore.A_CLOSE)) {
                    popupBlocks++;
                    lastPopupAt.put(pkg, now);
                    appendLog("🪟 弹窗广告已关闭（" + shortPkg(pkg) + "）："
                            + (f.adBadge ? "「" + f.badgeLabel + "」角标" : "Dialog+营销话术")
                            + " + 关闭键「" + f.closeLabel + "」");
                    bumpStat(pkg, "popup");
                } else {
                    if (attempts == 0) {
                        appendLog("⚡ 已点击跳过（" + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                                + "（" + (lastGesture ? "手势点击" : "无障碍代点") + "）→ 1.2s 后复验，未关将换候选重试");
                    } else {
                        appendLog("🔁 复验未关闭 → 第 " + (attempts + 1) + " 次出手（" + shortPkg(pkg) + "）："
                                + reasonZh(d.reason) + "（手势真实触点）");
                    }
                    bumpStat(pkg, "auto");
                    adFeatureUntil.put(pkg, now + CHAIN_GUARD_MS);
                }
                // v3.5.0 关键：点击后【不熔断】扫描链——由后续扫描复验；
                // 广告关掉会触发新窗口事件（计数清零）；仍在则换候选/升级兜底
                return true;
            }
            return false;
        }""", "execute 点击复验")

# 2. A_TAP_POINT 路径：同样计数 + 不熔断 + learned_pos 沉淀
rep("""        if (d.action.equals(AdDecisionCore.A_TAP_POINT)) {
            if (!clickCoolOK) return false;
            if (gestureTap(d.x, d.y)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                coordTapUsed.put(pkg, Boolean.TRUE);
                energyGestures++;
                appendLog("🎯 坐标级点击兜底（" + shortPkg(pkg) + "）：开屏窗内零信号简单树"
                        + " → 点击标准跳过位 (" + d.x + "," + d.y + ")，本次启动仅一次");
                bumpStat(pkg, "auto");
                fuseChain(pkg);
                return true;
            }
            return false;
        }""",
    """        if (d.action.equals(AdDecisionCore.A_TAP_POINT)) {
            if (!clickCoolOK) return false;
            boolean isLearned = "learned_pos".equals(d.reason);
            if (isLearned) coordTapUsed.put(pkg, Boolean.TRUE); // 位置指纹属高置信，直接占位
            if (gestureTap(d.x, d.y)) {
                lastClickAt.put(pkg, now);
                lastAutoClickPkg = pkg;
                lastAutoClickAt = now;
                clickTries.put(pkg, triesOfClick(pkg) + 1);
                if (!isLearned) coordTapUsed.put(pkg, Boolean.TRUE);
                energyGestures++;
                if (isLearned) {
                    appendLog("🎯 学习位置点击（" + shortPkg(pkg) + "）：你手动教过的位置指纹 ("
                            + d.x + "," + d.y + ")（手势真实触点）→ 1.2s 后复验");
                    bumpPosRule(pkg);
                } else {
                    appendLog("🎯 坐标级点击兜底（" + shortPkg(pkg) + "）：开屏窗内零信号简单树"
                            + " → 点击标准跳过位 (" + d.x + "," + d.y + ")，本次启动仅一次 → 1.2s 后复验");
                }
                bumpStat(pkg, "auto");
                return true; // 不熔断：交由复验扫描确认
            }
            return false;
        }""", "execute 坐标点击")

# 3. A_BACK 路径：click_verify_fail 日志补充
rep("""            appendLog("🛡 广告窗直断（" + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                    + " · " + ctx.elapsedMs + "ms → 返回键温和关闭（不碰进程与服务）");""",
    """            if ("click_verify_fail".equals(d.reason)) {
                appendLog("🛡 点击复验失败升级（" + shortPkg(pkg) + "）：同窗 " + ctx.clickAttempts
                        + " 次点击仍未关闭 → 返回键兜底温和关闭（不碰进程与服务）");
            } else {
                appendLog("🛡 广告窗直断（" + shortPkg(pkg) + "）：" + reasonZh(d.reason)
                        + " · " + ctx.elapsedMs + "ms → 返回键温和关闭（不碰进程与服务）");
            }""", "execute 直断日志")

# 4. clickNode：forceGesture 参数（复验重试一律真实触点）
rep("""    private boolean lastGesture = false;

    /** 点击控件：performAction 优先，失效降级 dispatchGesture 手势点击（真实触点） */
    private boolean clickNode(Decision d) {
        lastGesture = false;
        if (d.node instanceof NodeAdapter) {
            try {
                if (((NodeAdapter) d.node).n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        if (d.bounds != null) {
            int cx = (d.bounds[0] + d.bounds[2]) / 2;
            int cy = (d.bounds[1] + d.bounds[3]) / 2;
            if (gestureTap(cx, cy)) {
                lastGesture = true;
                energyGestures++;
                return true;
            }
        }
        return false;
    }""",
    """    private boolean lastGesture = false;

    /** 点击控件：performAction 优先，失效降级 dispatchGesture 手势点击（真实触点）；
     *  v3.5.0：forceGesture=true（复验重试）跳过 performAction 直接真实触点——
     *  performAction 返回 true 也可能被应用无声吞掉，真机「点了没反应」的主因 */
    private boolean clickNode(Decision d, boolean forceGesture) {
        lastGesture = false;
        if (!forceGesture && d.node instanceof NodeAdapter) {
            try {
                if (((NodeAdapter) d.node).n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        if (d.bounds != null) {
            int cx = (d.bounds[0] + d.bounds[2]) / 2;
            int cy = (d.bounds[1] + d.bounds[3]) / 2;
            if (gestureTap(cx, cy)) {
                lastGesture = true;
                energyGestures++;
                return true;
            }
        }
        return false;
    }""", "clickNode 强制手势")

# 5. reasonZh：新增原因
rep("""        if (r.equals("corner_image_skip")) return "角落图片跳过键";""",
    """        if (r.equals("corner_image_skip")) return "角落图片跳过键";
        if (r.equals("learned_pos")) return "学习位置（你手动教过）";
        if (r.equals("splash_close")) return "开屏×关闭键";""", "reasonZh 新增")

# 6. learnedClassHit：位置规则不触发快断（先点位置而非直接返回键）
rep("""            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r != null && cls.equals(r.optString("cls", ""))) return true;
            }""",
    """            for (int i = 0; i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null) continue;
                if (r.optBoolean("pos", false)) continue; // v3.5.0：位置规则走「点位置」而非快断
                if (cls.equals(r.optString("cls", ""))) return true;
            }""", "learnedClassHit 跳过位置规则")

io.open(P, "w", encoding="utf-8").write(src)
print("PHASE-2 DONE")
