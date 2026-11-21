package org.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSONObject JVM 测试 shim（与 android.jar org.json 二进制签名兼容）。
 * ─────────────────────────────────────────────
 * 目的：run-tests.sh 的 JVM 运行时无 android.jar——org.json 在其内为
 * Stub（运行即抛 RuntimeException("Stub!")）。core 生产类（LogPrivacyFilter/
 * LogEvents/LogUploadPolicy/LogBatchStore）编译期指向 android.jar 的
 * org.json 签名；本 shim 以【完全相同的方法签名】在 JVM 运行时承接这些
 * 调用（签名不一致 = NoSuchMethodError，故逐一对齐 javap 实测签名）。
 * 生产 APK 绝不包含本文件（build.sh 只编译 src/；tests/ 仅进 run-tests.sh）。
 * 语义对齐：compact toString / NULL 哨兵 / opt 族缺省回退 / 数字字符串互转。
 */
public class JSONObject {

    /** 哨兵：JSON null（opt 返回它而非 Java null——与 org.json 同语义）。 */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private final Map<String, Object> map = new LinkedHashMap<String, Object>();

    public JSONObject() {
    }

    public JSONObject(String json) throws JSONException {
        JsonParser.parseInto(json.trim(), this, null);
    }

    /* ── put 族（返回 this——链式兼容）── */

    public JSONObject put(String name, int value) {
        map.put(name, Integer.valueOf(value));
        return this;
    }

    public JSONObject put(String name, long value) {
        map.put(name, Long.valueOf(value));
        return this;
    }

    public JSONObject put(String name, boolean value) {
        map.put(name, Boolean.valueOf(value));
        return this;
    }

    public JSONObject put(String name, double value) {
        map.put(name, Double.valueOf(value));
        return this;
    }

    public JSONObject put(String name, Object value) {
        map.put(name, value == null ? NULL : value);
        return this;
    }

    /* ── opt 族（缺失回退 / 类型互转——org.json 同语义）── */

    public Object opt(String name) {
        return name == null ? null : map.get(name);
    }

    public boolean optBoolean(String name) {
        return optBoolean(name, false);
    }

    public boolean optBoolean(String name, boolean fallback) {
        Object v = opt(name);
        if (v == null || v == NULL) return fallback;
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString();
        return "true".equalsIgnoreCase(s);
    }

    public int optInt(String name) {
        return optInt(name, 0);
    }

    public int optInt(String name, int fallback) {
        Object v = opt(name);
        Long n = asLong(v);
        return n == null ? fallback : n.intValue();
    }

    public long optLong(String name) {
        return optLong(name, 0L);
    }

    public long optLong(String name, long fallback) {
        Object v = opt(name);
        Long n = asLong(v);
        return n == null ? fallback : n.longValue();
    }

    public String optString(String name) {
        return optString(name, "");
    }

    public String optString(String name, String fallback) {
        Object v = opt(name);
        if (v == null || v == NULL) return fallback;
        return v instanceof String ? (String) v : v.toString();
    }

    public JSONObject optJSONObject(String name) {
        Object v = opt(name);
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    public JSONArray optJSONArray(String name) {
        Object v = opt(name);
        return v instanceof JSONArray ? (JSONArray) v : null;
    }

    public boolean has(String name) {
        return name != null && map.containsKey(name);
    }

    public Object remove(String name) {
        return name == null ? null : map.remove(name);
    }

    public int length() {
        return map.size();
    }

    @SuppressWarnings("unchecked")
    public java.util.Iterator<String> keys() {
        return map.isEmpty() ? Collections.<String>emptyIterator()
                : new ArrayList<String>(map.keySet()).iterator();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) b.append(',');
            first = false;
            JsonParser.quote(b, e.getKey());
            b.append(':');
            JsonParser.value(b, e.getValue());
        }
        b.append('}');
        return b.toString();
    }

    /* ── 内部 ── */

    private static Long asLong(Object v) {
        if (v == null || v == NULL) return null;
        if (v instanceof Number) return Long.valueOf(((Number) v).longValue());
        String s = v.toString().trim();
        try {
            return Long.valueOf(Long.parseLong(s));
        } catch (Throwable t) {
            try {
                Double d = Double.valueOf(Double.parseDouble(s));
                return Long.valueOf(d.longValue());
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /** 解析器（包内共用）。 */
    static final class JsonParser {
        static void quote(StringBuilder b, String s) {
            b.append('"');
            for (int i = 0; s != null && i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': b.append("\\\""); break;
                    case '\\': b.append("\\\\"); break;
                    case '\n': b.append("\\n"); break;
                    case '\r': b.append("\\r"); break;
                    case '\t': b.append("\\t"); break;
                    case '\b': b.append("\\b"); break;
                    case '\f': b.append("\\f"); break;
                    default:
                        if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                        else b.append(c);
                }
            }
            b.append('"');
        }

        static void value(StringBuilder b, Object v) {
            if (v == null || v == NULL) b.append("null");
            else if (v instanceof String) quote(b, (String) v);
            else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) b.append(v.toString());
            else if (v instanceof Double) b.append(((Double) v).toString());
            else b.append(v.toString());
        }

        /** 解析入口：targetObj/targetArr 二选一（另一传 null）。 */
        static Object parseInto(String s, JSONObject targetObj, JSONArray targetArr) throws JSONException {
            P p = new P(s);
            Object out = p.parseValue(targetObj, targetArr);
            p.skipWs();
            if (p.pos < p.len) throw new JSONException("end of input expected at " + p.pos);
            return out;
        }

        private static final class P {
            final String s;
            final int len;
            int pos = 0;

            P(String s) {
                this.s = s;
                this.len = s == null ? 0 : s.length();
            }

            void skipWs() {
                while (pos < len) {
                    char c = s.charAt(pos);
                    if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++;
                    else break;
                }
            }

            char peek() throws JSONException {
                if (pos >= len) throw new JSONException("unexpected end at " + pos);
                return s.charAt(pos);
            }

            void expect(char c) throws JSONException {
                if (pos >= len || s.charAt(pos) != c) {
                    throw new JSONException("expected '" + c + "' at " + pos);
                }
                pos++;
            }

            Object parseValue(JSONObject targetObj, JSONArray targetArr) throws JSONException {
                skipWs();
                if (pos >= len) throw new JSONException("empty input");
                char c = s.charAt(pos);
                if (c == '{') {
                    JSONObject o = targetObj != null ? targetObj : new JSONObject();
                    parseObject(o);
                    return o;
                }
                if (c == '[') {
                    JSONArray a = targetArr != null ? targetArr : new JSONArray();
                    parseArray(a);
                    return a;
                }
                if (c == '"') return parseString(); // 字符串值（键与值同一解析器）
                return parseScalar();
            }

            void parseObject(JSONObject o) throws JSONException {
                expect('{');
                skipWs();
                if (pos < len && s.charAt(pos) == '}') {
                    pos++;
                    return;
                }
                while (true) {
                    skipWs();
                    if (pos >= len || s.charAt(pos) != '"') {
                        throw new JSONException("expected string key at " + pos);
                    }
                    String key = parseString();
                    skipWs();
                    expect(':');
                    Object v = parseValue(null, null);
                    o.map.put(key, v);
                    skipWs();
                    char c = peek();
                    if (c == ',') {
                        pos++;
                    } else if (c == '}') {
                        pos++;
                        return;
                    } else {
                        throw new JSONException("expected ',' or '}' at " + pos);
                    }
                }
            }

            void parseArray(JSONArray a) throws JSONException {
                expect('[');
                skipWs();
                if (pos < len && s.charAt(pos) == ']') {
                    pos++;
                    return;
                }
                while (true) {
                    Object v = parseValue(null, null);
                    a.values.add(v);
                    skipWs();
                    char c = peek();
                    if (c == ',') {
                        pos++;
                    } else if (c == ']') {
                        pos++;
                        return;
                    } else {
                        throw new JSONException("expected ',' or ']' at " + pos);
                    }
                }
            }

            String parseString() throws JSONException {
                expect('"');
                StringBuilder b = new StringBuilder();
                while (pos < len) {
                    char c = s.charAt(pos++);
                    if (c == '"') return b.toString();
                    if (c == '\\') {
                        if (pos >= len) break;
                        char e = s.charAt(pos++);
                        switch (e) {
                            case '"': b.append('"'); break;
                            case '\\': b.append('\\'); break;
                            case '/': b.append('/'); break;
                            case 'n': b.append('\n'); break;
                            case 'r': b.append('\r'); break;
                            case 't': b.append('\t'); break;
                            case 'b': b.append('\b'); break;
                            case 'f': b.append('\f'); break;
                            case 'u':
                                if (pos + 4 > len) throw new JSONException("bad \\u escape at " + pos);
                                String hex = s.substring(pos, pos + 4);
                                pos += 4;
                                try {
                                    b.append((char) Integer.parseInt(hex, 16));
                                } catch (Throwable t) {
                                    throw new JSONException("bad \\u escape: " + hex);
                                }
                                break;
                            default:
                                throw new JSONException("bad escape '\\" + e + "' at " + pos);
                        }
                    } else {
                        b.append(c);
                    }
                }
                throw new JSONException("unterminated string at " + pos);
            }

            Object parseScalar() throws JSONException {
                if (s.startsWith("true", pos)) {
                    pos += 4;
                    return Boolean.TRUE;
                }
                if (s.startsWith("false", pos)) {
                    pos += 5;
                    return Boolean.FALSE;
                }
                if (s.startsWith("null", pos)) {
                    pos += 4;
                    return JSONObject.NULL;
                }
                int start = pos;
                while (pos < len) {
                    char c = s.charAt(pos);
                    if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') pos++;
                    else break;
                }
                if (pos == start) throw new JSONException("unexpected char at " + start);
                String num = s.substring(start, pos);
                if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0) {
                    try {
                        return Long.valueOf(Long.parseLong(num));
                    } catch (Throwable t) {
                        return Double.valueOf(Double.parseDouble(num));
                    }
                }
                return Double.valueOf(Double.parseDouble(num));
            }
        }
    }

}
