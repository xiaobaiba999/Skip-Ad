package com.zai.adshield.test;

import com.zai.adshield.core.LogEvents;
import com.zai.adshield.core.LogPrivacyFilter;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v8.0.0 上传链路 · 隐私过滤器套件（JVM 纯函数——org.json shim 承接）。
 *
 * 覆盖面（与服务端 validate.ts 白名单一字不差的双端契约）：
 *  · 白名单/枚举/整数界/字符串长与正则（字段级）；
 *  · 危险键整批拒绝（PRIVACY_REJECTED——显式失败优于静默剥离）；
 *  · 候选文本词族门（close/skip 词族 ≤16 字符——词族外剥离计数）；
 *  · 免费文本脱敏四正则（手机号/身份证 18 位差一修复/邮箱/IPv4）；
 *  · 脱敏-截断顺序（跨界 PII 前缀不因截断漏出——四轮审计修复回归）；
 *  · MAX_EVENTS=48 上限与 dropped 计数；
 *  · LogEvents 工厂自洽（八类事件过自家过滤器零剥离——schema 契约）。
 */
public final class LogPrivacyFilterTest {

    public static void main(String[] args) {
        TestHost.suite("过滤 S1 · 白名单字段与未知键剥离");
        s1_whitelist();
        TestHost.suite("过滤 S2 · 危险键整批拒绝");
        s2_dangerKey();
        TestHost.suite("过滤 S3 · 枚举与整数界");
        s3_enumAndInt();
        TestHost.suite("过滤 S4 · 候选文本词族门");
        s4_candidateText();
        TestHost.suite("过滤 S5 · 脱敏四正则（身份证 18 位差一修复）");
        s5_mask();
        TestHost.suite("过滤 S6 · 脱敏-截断顺序（safetyReason 先脱敏后截断）");
        s6_maskOrder();
        TestHost.suite("过滤 S7 · MAX_EVENTS 上限");
        s7_maxEvents();
        TestHost.suite("过滤 S8 · LogEvents 工厂自洽（零剥离契约）");
        s8_factorySchema();
        TestHost.suite("过滤 S9 · SCAN_MISS 失败码纯函数（v7.9.8）");
        s9_scanMissCode();
        TestHost.done();
    }

    /* ── S1 白名单 ── */
    static void s1_whitelist() {
        JSONObject e = new JSONObject();
        e.put("type", "AD_TASK");
        e.put("at", 1700000000000L);
        e.put("pkg", "com.foo.bar");
        e.put("activity", "com.foo.bar.MainActivity");
        e.put("taskState", "SUCCESS");
        e.put("outcome", "VERIFIED_SUCCESS");
        e.put("retryCount", 2);
        e.put("scanCount", 3);
        e.put("generation", 7);
        e.put("unknownSafeKey", "x"); // 安全未知键 → 剥离计数
        LogPrivacyFilter.Result r = LogPrivacyFilter.sanitizeEvents(arr(e));
        TestHost.check(!r.privacyRejected, "S1a 白名单事件不整批拒绝");
        TestHost.check(r.events.length() == 1, "S1b 事件保留");
        JSONObject out = r.events.optJSONObject(0);
        TestHost.checkEq("AD_TASK", out.optString("type"), "S1c type 保留");
        TestHost.checkEq("com.foo.bar", out.optString("pkg"), "S1d pkg 保留");
        TestHost.checkEq("1700000000000", out.optString("at"), "S1e at（数字以字符串入——服务端再转数）");
        TestHost.checkEq("2", out.optString("retryCount"), "S1f retryCount");
        TestHost.check(!out.has("unknownSafeKey"), "S1g 未知键剥离");
        TestHost.check(r.stripped == 1, "S1h 剥离计数=1");

        // 无 type 事件 → 整事件丢弃（dropped）
        JSONObject noType = new JSONObject();
        noType.put("pkg", "com.foo");
        r = LogPrivacyFilter.sanitizeEvents(arr(e, noType));
        TestHost.check(r.events.length() == 1 && r.droppedEvents == 1, "S1i 无 type 事件丢弃计数");
    }

    /* ── S2 危险键 ── */
    static void s2_dangerKey() {
        JSONObject bad = new JSONObject();
        bad.put("type", "AD_TASK");
        bad.put("screenshotUrl", "http://x"); // 危险形态键（screen…）
        LogPrivacyFilter.Result r = LogPrivacyFilter.sanitizeEvents(arr(bad));
        TestHost.check(r.privacyRejected, "S2a 危险键整批拒绝");
        TestHost.checkEq("screenshotUrl", r.dangerKey, "S2b dangerKey 回报");
        TestHost.check(r.events.length() == 0, "S2c 拒绝时事件清空");
        // 第二危险词形：imei/phone/token
        JSONObject bad2 = new JSONObject();
        bad2.put("type", "CLICK");
        bad2.put("userToken", "t");
        r = LogPrivacyFilter.sanitizeEvents(arr(bad2));
        TestHost.check(r.privacyRejected && "userToken".equals(r.dangerKey), "S2d token 词形拒绝");
    }

    /* ── S3 枚举与整数界 ── */
    static void s3_enumAndInt() {
        JSONObject e = new JSONObject();
        e.put("type", "AD_TASK");
        e.put("ui", "INTERSTITIAL");
        e.put("ui2", "NOT_A_UI"); // 非法枚举值 → 剥离
        e.put("retryCount", 999); // 超界 → 剥离
        e.put("at", 123L);        // 超界（< 1e12）→ 剥离
        LogPrivacyFilter.Result r = LogPrivacyFilter.sanitizeEvents(arr(e));
        JSONObject out = r.events.optJSONObject(0);
        TestHost.checkEq("INTERSTITIAL", out.optString("ui"), "S3a 合法枚举保留");
        TestHost.check(!out.has("ui2"), "S3b 非法枚举剥离");
        TestHost.check(!out.has("retryCount"), "S3c 超界整数剥离");
        TestHost.check(!out.has("at"), "S3d 超界 at 剥离");
        // pkg 正则（空格/特殊字符不合法）
        JSONObject e2 = new JSONObject();
        e2.put("type", "CRASH");
        e2.put("pkg", "com foo!"); // 空格+叹号 → PKG_RE 不匹配 → 剥离
        r = LogPrivacyFilter.sanitizeEvents(arr(e2));
        TestHost.check(!r.events.optJSONObject(0).has("pkg"), "S3e 非法 pkg 形态剥离（空格/特殊字符）");
    }

    /* ── S4 词族门 ── */
    static void s4_candidateText() {
        TestHost.check(LogPrivacyFilter.isAllowedCandidateText("跳过广告"), "S4a 中文词族成员");
        TestHost.check(LogPrivacyFilter.isAllowedCandidateText("Skip Ad"), "S4b 英文词族成员");
        TestHost.check(LogPrivacyFilter.isAllowedCandidateText("× 关闭"), "S4c × 前缀词族");
        TestHost.check(!LogPrivacyFilter.isAllowedCandidateText("王者荣耀启动"), "S4d 词族外拒绝");
        TestHost.check(!LogPrivacyFilter.isAllowedCandidateText("跳过广告顺便看看详情页"), "S4e 超长拒绝");
        // 事件内 text 字段：词族外 → 剥离计数
        JSONObject e = new JSONObject();
        e.put("type", "CLICK");
        e.put("text", "用户昵称什么的");
        LogPrivacyFilter.Result r = LogPrivacyFilter.sanitizeEvents(arr(e));
        TestHost.check(!r.events.optJSONObject(0).has("text"), "S4f 词族外 text 剥离");
        TestHost.check(r.stripped == 1, "S4g text 剥离计数");
    }

    /* ── S5 脱敏四正则 ── */
    static void s5_mask() {
        TestHost.checkEq("138****5678", LogPrivacyFilter.maskFreeText("13812345678"), "S5a 手机号前3后4");
        TestHost.checkEq("电话138****5678联系", LogPrivacyFilter.maskFreeText("电话13812345678联系"), "S5b 嵌入手机号原位脱敏");
        // 身份证 18 位（差一修复核心回归：18 位真证件号必须被脱敏）
        TestHost.check(!LogPrivacyFilter.maskFreeText("11010119900307857X").contains("19900307"),
                "S5c 身份证中段不泄漏");
        TestHost.check(LogPrivacyFilter.maskFreeText("11010119900307857X").startsWith("110"),
                "S5d 身份证保留前 3");
        TestHost.check(LogPrivacyFilter.maskFreeText("11010119900307857X").endsWith("57X"),
                "S5e 身份证保留后 2+校验位");
        TestHost.check(LogPrivacyFilter.maskFreeText("11010119900307857X").contains("************"),
                "S5f 身份证中段 12 星");
        TestHost.checkEq("***@example.com", LogPrivacyFilter.maskFreeText("user@example.com"), "S5g 邮箱本地部打码");
        TestHost.checkEq("192.168.*.*", LogPrivacyFilter.maskFreeText("192.168.1.100"), "S5h IPv4 前两段保留");
        // 边界：19 位数字串不匹配（非 18 位证件形态）
        TestHost.checkEq("1234567890123456789", LogPrivacyFilter.maskFreeText("1234567890123456789"),
                "S5i 19 位串不误伤（边界锚）");
    }

    /* ── S6 脱敏-截断顺序 ── */
    static void s6_maskOrder() {
        // safetyReason：先脱敏（完整原文）再截 32——跨界手机号前缀不以明文漏出
        String phone = "13812345678";
        String raw = "a" + phone; // 12 字符：截断后正好跨在手机号上
        JSONObject e = new JSONObject();
        e.put("type", "SAFE_BLOCK");
        e.put("safetyReason", raw);
        LogPrivacyFilter.Result r = LogPrivacyFilter.sanitizeEvents(arr(e));
        String out = r.events.optJSONObject(0).optString("safetyReason");
        TestHost.check(!out.contains("13812345678") && !out.contains("138123"),
                "S6a 截断前先脱敏（数字前缀不漏）");
        TestHost.check(out.length() <= 32, "S6b 32 上限");
        // 长文本（>32）跨界场景：后 20 字符含手机号，截 32 不应漏明文
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 25; i++) sb.append('x');
        sb.append("13812345678");
        JSONObject e2 = new JSONObject();
        e2.put("type", "SAFE_BLOCK");
        e2.put("safetyReason", sb.toString());
        r = LogPrivacyFilter.sanitizeEvents(arr(e2));
        String out2 = r.events.optJSONObject(0).optString("safetyReason");
        TestHost.check(!out2.contains("13812345678"), "S6c 跨界手机号先脱敏");
    }

    /* ── S7 MAX_EVENTS ── */
    static void s7_maxEvents() {
        JSONArray many = new JSONArray();
        for (int i = 0; i < 50; i++) {
            JSONObject e = new JSONObject();
            e.put("type", "AD_TASK");
            many.put(e);
        }
        LogPrivacyFilter.Result r = LogPrivacyFilter.sanitizeEvents(many);
        TestHost.check(r.events.length() == 48, "S7a 48 上限截取");
        TestHost.check(r.droppedEvents == 2, "S7b 超限 dropped 计数");
    }

    /* ── S8 工厂自洽 ── */
    static void s8_factorySchema() {
        // LogEvents 八类工厂输出必须零剥离过自家过滤器（schema 契约——字段名/枚举值
        // 与白名单一字不差；任何剥离=工厂产错值）
        JSONArray all = new JSONArray();
        all.put(LogEvents.adTask("com.app.one", "com.app.one.MainActivity", "SUCCESS",
                "VERIFIED_SUCCESS", 3, 5, 9));
        all.put(LogEvents.adTask("com.app.two", "com.app.two.MainActivity", "FAILED",
                "MISS", 0, 0, 0));
        all.put(LogEvents.safeBlock("com.app.three", "inter_close"));
        all.put(LogEvents.click("com.app.four", "com.app.four.MainActivity",
                com.zai.adshield.core.DecisionEvidence.SRC_TEXT, "跳过广告", true));
        all.put(LogEvents.click("com.app.five", "com.app.five.MainActivity",
                com.zai.adshield.core.DecisionEvidence.SRC_IMAGE, "不在词族的文本", false));
        all.put(LogEvents.verify("com.app.six", true));
        all.put(LogEvents.verify("com.app.seven", false));
        all.put(LogEvents.stats(new int[]{1, 2, 3, 4, 5, 6, 7, 8}, 9, 10, 11, "DEV_Test.Brand"));
        all.put(LogEvents.gkdError("java.io.IOException"));
        all.put(LogEvents.videoPoll("com.app.eight", 1500L));
        all.put(LogEvents.crash("java.lang.NullPointerException"));
        /* v7.9.8 SCAN_MISS：白名单事件类型首次接线（零候选窗结构化诊断） */
        all.put(LogEvents.scanMiss("com.app.nine", "com.app.nine.MainActivity", false,
                "NO_RULE_ZERO_CAND", 3, 4200L));
        all.put(LogEvents.scanMiss("com.app.ten", "com.app.ten.AdDialog", true,
                "VIDEO_LIKE_ZERO_CAND", 1, 2600L));
        LogPrivacyFilter.Result r = LogPrivacyFilter.sanitizeEvents(all);
        TestHost.check(!r.privacyRejected, "S8a 工厂输出零危险键");
        TestHost.check(r.events.length() == 13, "S8b 工厂输出零事件丢弃");
        // click 词族外文本：工厂主动不带 → 剥离数为 0（不是靠服务端剥离兜底）
        TestHost.check(r.stripped == 0, "S8c 工厂输出零字段剥离（词族外 text 工厂已丢弃）");
        // 圆整：数字字段以字符串入（服务端互转契约），可回读
        JSONObject ad = r.events.optJSONObject(0);
        TestHost.checkEq("3", ad.optString("retryCount"), "S8d retryCount 数字串互转契约");
        JSONObject st = r.events.optJSONObject(7);
        TestHost.checkEq("7", st.optString("gkdFalseClick"), "S8e GKD 八格契约");
        // click 词族内 text 保留
        JSONObject ck = r.events.optJSONObject(3);
        TestHost.checkEq("跳过广告", ck.optString("text"), "S8f 词族内 text 保留");
        TestHost.checkEq("TEXT", ck.optString("candidateSource"), "S8g candidateSource 枚举映射");
        TestHost.checkEq("DISPATCHED", ck.optString("clickResult"), "S8h clickResult 枚举");
        // SCAN_MISS 白名单契约（v7.9.8——服务端字段面零新增）
        JSONObject sm = r.events.optJSONObject(11);
        TestHost.checkEq("SCAN_MISS", sm.optString("type"), "S8i SCAN_MISS 类型白名单内");
        TestHost.checkEq("SPLASH", sm.optString("ui"), "S8j ui 枚举（开屏）");
        TestHost.checkEq("NO_RULE_ZERO_CAND", sm.optString("errClass"), "S8k 稳定失败码入 errClass");
        TestHost.checkEq("3", sm.optString("scanCount"), "S8l 零候选次数");
        TestHost.checkEq("4200", sm.optString("intervalMs"), "S8m 静默时长");
        JSONObject sm2 = r.events.optJSONObject(12);
        TestHost.checkEq("INTERSTITIAL", sm2.optString("ui"), "S8n ui 枚举（弹窗窗期）");
        // STATS 设备码（v7.9.8）：白名单形态携带——errClass 字段面
        JSONObject stt = r.events.optJSONObject(7);
        TestHost.checkEq("DEV_Test.Brand", stt.optString("errClass"), "S8o 设备码经 STATS 上行");
    }

    /* ── S9 SCAN_MISS 失败码纯函数（v7.9.8）── */
    static void s9_scanMissCode() {
        TestHost.checkEq("VIDEO_LIKE_ZERO_CAND",
                LogEvents.scanMissCode(false, 2500L, false),
                "S9a 零内容事件+窗龄≥2s → 视频容器静默铁证");
        TestHost.checkEq("SILENT_TREE_ZERO_CAND",
                LogEvents.scanMissCode(false, 1200L, true),
                "S9b 零内容事件+窗龄尚短 → 树静默观察中");
        TestHost.checkEq("NO_RULE_ZERO_CAND",
                LogEvents.scanMissCode(true, 800L, false),
                "S9c 有内容事件+无规则 → 补规则/订阅可解");
        TestHost.checkEq("RULE_MISS_ZERO_CAND",
                LogEvents.scanMissCode(true, 800L, true),
                "S9d 有内容事件+有规则 → 规则未命中（需看样本）");
    }

    static JSONArray arr(JSONObject... es) {
        JSONArray a = new JSONArray();
        for (JSONObject e : es) a.put(e);
        return a;
    }
}
