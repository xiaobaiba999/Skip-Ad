package com.zai.adshield.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * v8.0.0 日志隐私过滤器（客户端第一道 · 纯 Java · 零 Android 依赖 · JVM 可测）。
 * ─────────────────────────────────────────────
 * 与中转服务（skip-ad-log-relay）src/validate.ts 同源同规约——双端白名单
 * 必须同步演进（privacyFilterVersion 同号递增；服务端是权威第二道）。
 *
 * 规约（与 DecisionEvidence 隐私红线同族）：
 *  · 事件只允许白名单字段（枚举/整数界/字符串长与正则）；
 *  · 候选文本仅 close/skip 词族 ≤16 字符（词族外剥离计数，绝不默认上传）；
 *  · 危险键（截图/无障碍树/剪贴板/联系人/定位/账号密码支付等形态）→ 整批拒绝
 *    （显式失败优于静默剥离——过滤器版本失配必须让开发者看见）；
 *  · 免费文本脱敏（手机号/身份证/邮箱/IPv4）与 Net.maskSensitive 同族正则。
 *
 * 线程安全：全静态纯函数，无状态。
 */
public final class LogPrivacyFilter {

    public static final int VERSION = 1;
    public static final int MAX_EVENTS = 48;

    /* ── 枚举表（与服务端 validate.ts ENUMS 一字不差）── */
    private static final String[] EV = {"AD_TASK","SCAN_MISS","CLICK","VERIFY","FEEDBACK_PANEL","GKD","VIDEO_POLL","SAFE_BLOCK","SERVICE","CRASH","STATS"};
    private static final String[] UI = {"SPLASH","INTERSTITIAL","FEED","BANNER","REWARDED","VIDEO","FEEDBACK_SHEET","WEBVIEW","OTHER"};
    private static final String[] TS = {"READY","CLICKING","VERIFYING","RETRY_WAIT","SUCCESS","MISS","FAILED","UNKNOWN"};
    private static final String[] CS = {"TEXT","DESC","VID","IMAGE_CORNER","GKD_REMOTE","GKD_BUILTIN","GEOMETRY","OTHER"};
    private static final String[] CT = {"TEXT","DESC","IMAGE","VID","GEOMETRY","OTHER"};
    private static final String[] SG = {"ALLOW","BLOCK","REJECT","UNKNOWN"};
    private static final String[] CR = {"DISPATCHED","PERFORM_ACTION","REJECTED","FAILED"};
    private static final String[] VR = {"CLOSED","NOT_CLOSED","UNCERTAIN"};
    private static final String[] OC = {"VERIFIED_SUCCESS","MISS","FALSE_CLICK","DUPLICATE_CLICK","SAFE_BLOCK","NO_AD_OBSERVED","CRASH","UNKNOWN"};

    private static final Pattern PKG_RE = Pattern.compile("^[A-Za-z0-9_.]+$");
    private static final Pattern ACT_RE = Pattern.compile("^[A-Za-z0-9_.$]+$");
    private static final Pattern ERR_RE = Pattern.compile("^[A-Za-z0-9_.$]+$");
    private static final Pattern KEY_RE = Pattern.compile("^[a-z][a-zA-Z0-9]{0,31}$");
    private static final Pattern CANDIDATE_RE = Pattern.compile(
            "^(?:[\\u00d7\\u2715xX]\\s*)?(?:跳过广告|关闭广告|不再显示|不感兴趣|跳过|关闭|skip\\s*ad|skip|close|dismiss|not\\s+interested)\\s*[。.!！]?$",
            Pattern.CASE_INSENSITIVE);

    /** 危险键形态（命中 → privacyRejected=true 整批拒传——与 DANGEROUS_KEY_RE 同族词面）。 */
    private static final Pattern DANGER_RE = Pattern.compile(
            "(screen|shot|tree|clip|contact|location|gps|geo|notification|sms|passw|pwd|token|secret|cookie|session|account|phone|tel|imei|serial|androidid|adid|ocr|chat|mic|camera|audio|keystroke|history|bookma|userinput|widget|boundsfull|fulltext|payload|attachment|image|photo|bitmap)", Pattern.CASE_INSENSITIVE);

    private LogPrivacyFilter() {
    }

    /** 过滤结果： sanitized 事件 + 剥离计数 + 拒绝标记（拒绝时 sanitized 为空）。 */
    public static final class Result {
        public final JSONArray events;
        public final int stripped;
        public final int droppedEvents;
        public final boolean privacyRejected;
        public final String dangerKey;

        private Result(JSONArray events, int stripped, int droppedEvents, boolean rejected, String dangerKey) {
            this.events = events;
            this.stripped = stripped;
            this.droppedEvents = droppedEvents;
            this.privacyRejected = rejected;
            this.dangerKey = dangerKey;
        }
    }

    /** 事件数组过滤（上传前唯一入口；服务端还会再过滤一次——纵深防御）。 */
    public static Result sanitizeEvents(JSONArray raw) {
        if (raw == null) raw = new JSONArray();
        JSONArray out = new JSONArray();
        int stripped = 0;
        int dropped = 0;
        String danger = null;

        int n = Math.min(raw.length(), MAX_EVENTS);
        if (raw.length() > MAX_EVENTS) dropped += raw.length() - MAX_EVENTS;

        for (int i = 0; i < n; i++) {
            Object o = raw.opt(i);
            if (!(o instanceof JSONObject)) { dropped++; continue; }
            JSONObject e = (JSONObject) o;
            JSONObject clean = new JSONObject();
            boolean hasType = false;
            Iterator<String> it = e.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k == null || !KEY_RE.matcher(k).matches() || !isKnownField(k)) {
                    if (k != null && DANGER_RE.matcher(k).find()) {
                        danger = k;
                        break;
                    }
                    stripped++;
                    continue;
                }
                Object v = e.opt(k);
                if (v == null || v == JSONObject.NULL) continue;
                String norm = normalizeKnown(k, v);
                if (norm == null) { stripped++; continue; }
                if ("text".equals(k) && !isAllowedCandidateText(norm)) { stripped++; continue; }
                if ("type".equals(k)) hasType = true;
                try {
                    clean.put(k, norm);
                } catch (org.json.JSONException je) {
                    stripped++;
                }
            }
            if (danger != null) break;
            if (!hasType) { dropped++; continue; }
            out.put(clean);
        }
        if (danger != null) {
            return new Result(new JSONArray(), 0, 0, true, danger);
        }
        return new Result(out, stripped, dropped, false, null);
    }

    /** 候选文本白名单（与 DecisionEvidence.sanitizeToken 同族语义）。 */
    public static boolean isAllowedCandidateText(String s) {
        if (s == null) return false;
        String t = s.trim();
        return t.length() > 0 && t.length() <= 16 && CANDIDATE_RE.matcher(t).matches();
    }

    /** 免费文本脱敏（与 Net.maskSensitive 同族正则——本类零 Android 依赖故自带）。
     *  四轮审计修复（身份证差一缺陷）：旧正则 (\d{3})\d{12}(\d{3}[\dXx]) 要求 19 位——
     *  中国居民身份证实为 18 位，导致同族实现（含服务端 privacy.ts）从未真正脱敏
     *  18 位证件号；修正为 (\d{3})\d{12}(\d{2}[\dXx]) = 18 位全匹配（前 3 后 2+校验位）。 */
    public static String maskFreeText(String s) {
        if (s == null) return "";
        String out = s;
        out = out.replaceAll("(?<![0-9])(1[3-9]\\d)\\d{4}(\\d{4})(?![0-9])", "$1****$2");
        out = out.replaceAll("(?<![0-9A-Za-z])(\\d{3})\\d{12}(\\d{2}[\\dXx])(?![0-9A-Za-z])", "$1************$2");
        out = out.replaceAll("[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})", "***@$1");
        out = out.replaceAll("(?<![0-9])(\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}(?![0-9])", "$1.*.*");
        return out;
    }

    /* ── 内部：白名单字段规约（与服务端 FIELDS 表一字不差）── */

    private static final Map<String, String[]> ENUM_FIELDS = buildEnums();

    private static Map<String, String[]> buildEnums() {
        Map<String, String[]> m = new HashMap<String, String[]>();
        m.put("type", EV);
        m.put("ui", UI);
        m.put("taskState", TS);
        m.put("candidateSource", CS);
        m.put("candidateType", CT);
        m.put("safetyGuard", SG);
        m.put("clickResult", CR);
        m.put("verifyResult", VR);
        m.put("outcome", OC);
        return m;
    }

    private static boolean isKnownField(String k) {
        if (ENUM_FIELDS.containsKey(k)) return true;
        return STR_MAX.containsKey(k) || INT_RANGE.containsKey(k);
    }

    private static final Map<String, Integer> STR_MAX = new HashMap<String, Integer>();
    private static final Map<String, long[]> INT_RANGE = new HashMap<String, long[]>();

    static {
        STR_MAX.put("pkg", 96); STR_MAX.put("activity", 96); STR_MAX.put("text", 16);
        STR_MAX.put("safetyReason", 32); STR_MAX.put("errClass", 80);
        INT_RANGE.put("at", new long[]{1_000_000_000_000L, 4_102_444_800_000L});
        INT_RANGE.put("retryCount", new long[]{0, 99});
        INT_RANGE.put("scanCount", new long[]{0, 9_999});
        INT_RANGE.put("intervalMs", new long[]{0, 600_000});
        INT_RANGE.put("generation", new long[]{0, 1_000_000_000});
        String[] counters = {
                "gkdConsulted", "gkdMatched", "gkdCandidateAccepted", "gkdClicked",
                "gkdVerifiedSuccess", "gkdClickFail", "gkdFalseClick", "gkdBlocked",
                "taskSuccess", "taskMiss", "taskFailed", "taskUnknown", "videoPollCount",
        };
        for (String k : counters) INT_RANGE.put(k, new long[]{0, 1_000_000_000L});
    }

    /** 已知字段的归一化校验；返回归一值（String 直存 / 数字转 Long.toString），非法返回 null。 */
    private static String normalizeKnown(String k, Object v) {
        String[] en = ENUM_FIELDS.get(k);
        if (en != null) {
            if (!(v instanceof String) || !in(en, (String) v)) return null;
            return (String) v;
        }
        long[] range = INT_RANGE.get(k);
        if (range != null) {
            Long n = asLong(v);
            if (n == null || n < range[0] || n > range[1]) return null;
            return n.toString();
        }
        Integer maxLen = STR_MAX.get(k);
        if (maxLen != null) {
            if (!(v instanceof String) || ((String) v).length() == 0) return null;
            /* 四轮审计（脱敏-截断顺序）：mask 字段先对完整原文脱敏再截断——
             * 截断先行的场合，跨界 PII（恰被 maxLen 边界切断的手机号）会变成
             * 部分数字串、脱敏正则不再命中、前缀以明文漏出（与服务端 validate.ts
             * 同修）。 */
            if ("safetyReason".equals(k)) {
                String masked = maskFreeText((String) v);
                return masked.length() > maxLen ? masked.substring(0, maxLen) : masked;
            }
            String s = ((String) v).length() > maxLen ? ((String) v).substring(0, maxLen) : (String) v;
            if ("pkg".equals(k) && !PKG_RE.matcher(s).matches()) return null;
            if ("activity".equals(k) && !ACT_RE.matcher(s).matches()) return null;
            if ("errClass".equals(k) && !ERR_RE.matcher(s).matches()) return null;
            return s;
        }
        return null;
    }

    private static boolean in(String[] arr, String s) {
        for (String a : arr) if (a.equals(s)) return true;
        return false;
    }

    private static Long asLong(Object v) {
        if (v instanceof Integer) return ((Integer) v).longValue();
        if (v instanceof Long) return (Long) v;
        if (v instanceof java.math.BigDecimal) return ((java.math.BigDecimal) v).longValue();
        return null;
    }
}
