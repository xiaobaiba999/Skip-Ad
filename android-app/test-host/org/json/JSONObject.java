package org.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务级 JVM 测试桩：JSONObject（保序、round-trip 序列化/解析严格互逆——
 * 服务状态持久化链路 loadState→saveState→prefs→loadState 依赖此性质）。
 */
public class JSONObject {
    final Map<String, Object> map = new LinkedHashMap<String, Object>();

    public JSONObject() { }

    public JSONObject(String json) throws JSONException {
        Object v = new JSONParser(json).parse();
        if (!(v instanceof JSONObject)) throw new JSONException("Not an object");
        JSONObject o = (JSONObject) v;
        map.putAll(o.map);
    }

    public int length() { return map.size(); }

    public JSONObject put(String k, boolean v) { map.put(k, Boolean.valueOf(v)); return this; }

    public JSONObject put(String k, double v) throws JSONException { map.put(k, Double.valueOf(v)); return this; }

    public JSONObject put(String k, int v) { map.put(k, Integer.valueOf(v)); return this; }

    public JSONObject put(String k, long v) { map.put(k, Long.valueOf(v)); return this; }

    public JSONObject put(String k, Object v) { map.put(k, v); return this; }

    public Object opt(String k) { return map.get(k); }

    public boolean has(String k) { return map.containsKey(k); }

    public Object remove(String k) { return map.remove(k); }

    public boolean isNull(String k) { return map.get(k) == null; }

    public String optString(String k) { return optString(k, ""); }

    public String optString(String k, String def) {
        Object v = map.get(k);
        return v == null ? def : v.toString();
    }

    public String getString(String k) throws JSONException {
        Object v = map.get(k);
        if (v == null) throw new JSONException("No value for " + k);
        return v.toString();
    }

    public int optInt(String k) { return optInt(k, 0); }

    public int optInt(String k, int def) {
        Object v = map.get(k);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public int getInt(String k) throws JSONException {
        Object v = map.get(k);
        if (!(v instanceof Number)) throw new JSONException("No int for " + k);
        return ((Number) v).intValue();
    }

    public long optLong(String k) { return optLong(k, 0L); }

    public long optLong(String k, long def) {
        Object v = map.get(k);
        return v instanceof Number ? ((Number) v).longValue() : def;
    }

    public long getLong(String k) throws JSONException {
        Object v = map.get(k);
        if (!(v instanceof Number)) throw new JSONException("No long for " + k);
        return ((Number) v).longValue();
    }

    public boolean optBoolean(String k) { return optBoolean(k, false); }

    public boolean optBoolean(String k, boolean def) {
        Object v = map.get(k);
        return v instanceof Boolean ? ((Boolean) v).booleanValue() : def;
    }

    public boolean getBoolean(String k) throws JSONException {
        Object v = map.get(k);
        if (!(v instanceof Boolean)) throw new JSONException("No boolean for " + k);
        return ((Boolean) v).booleanValue();
    }

    public double optDouble(String k) { return optDouble(k, Double.NaN); }

    public double optDouble(String k, double def) {
        Object v = map.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public JSONObject optJSONObject(String k) {
        Object v = map.get(k);
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    public JSONArray optJSONArray(String k) {
        Object v = map.get(k);
        return v instanceof JSONArray ? (JSONArray) v : null;
    }

    public Iterator<String> keys() { return map.keySet().iterator(); }

    public JSONArray names() {
        if (map.isEmpty()) return null;
        JSONArray a = new JSONArray();
        for (String k : map.keySet()) a.put(k);
        return a;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(valueToString(e.getKey()));
            sb.append(':');
            sb.append(valueToString(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    static String valueToString(Object v) {
        if (v == null) return "null";
        if (v instanceof String) {
            String s = (String) v;
            StringBuilder sb = new StringBuilder(s.length() + 8);
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            sb.append('"');
            return sb.toString();
        }
        if (v instanceof JSONObject) return v.toString();
        if (v instanceof JSONArray) return v.toString();
        return v.toString(); // Number/Boolean
    }
}

/** 极简 JSON 解析器（对象/数组/字符串/数字/布尔/null——桩内自足） */
class JSONParser {
    private final String s;
    private int i;

    JSONParser(String s) { this.s = s == null ? "" : s; }

    Object parse() throws JSONException {
        skipWs();
        Object v = parseValue();
        return v;
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object parseValue() throws JSONException {
        skipWs();
        if (i >= s.length()) throw new JSONException("Empty");
        char c = s.charAt(i);
        if (c == '{') return parseObj();
        if (c == '[') return parseArr();
        if (c == '"') return parseStr();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return parseNum();
    }

    private JSONObject parseObj() throws JSONException {
        JSONObject o = new JSONObject();
        i++; // {
        skipWs();
        if (i < s.length() && s.charAt(i) == '}') { i++; return o; }
        while (true) {
            skipWs();
            if (i >= s.length() || s.charAt(i) != '"') throw new JSONException("Key expected at " + i);
            String k = parseStr();
            skipWs();
            if (i >= s.length() || s.charAt(i) != ':') throw new JSONException("Colon expected at " + i);
            i++;
            Object v = parseValue();
            o.map.put(k, v);
            skipWs();
            if (i >= s.length()) throw new JSONException("Unterminated object");
            char d = s.charAt(i);
            i++;
            if (d == '}') return o;
            if (d != ',') throw new JSONException("Comma expected at " + (i - 1));
        }
    }

    private JSONArray parseArr() throws JSONException {
        JSONArray a = new JSONArray();
        i++; // [
        skipWs();
        if (i < s.length() && s.charAt(i) == ']') { i++; return a; }
        while (true) {
            Object v = parseValue();
            JSONArray.backing(a).add(v);
            skipWs();
            if (i >= s.length()) throw new JSONException("Unterminated array");
            char d = s.charAt(i);
            i++;
            if (d == ']') return a;
            if (d != ',') throw new JSONException("Comma expected in array at " + (i - 1));
        }
    }

    private String parseStr() throws JSONException {
        i++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') { i++; return sb.toString(); }
            if (c == '\\') {
                i++;
                if (i >= s.length()) break;
                char e = s.charAt(i);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 < s.length()) {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                        break;
                    default: sb.append(e);
                }
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        throw new JSONException("Unterminated string");
    }

    private Object parseNum() throws JSONException {
        int start = i;
        while (i < s.length()) {
            char c = s.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
            else break;
        }
        if (start == i) throw new JSONException("Invalid number at " + start);
        String t = s.substring(start, i);
        try {
            if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
                long l = Long.parseLong(t);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return Integer.valueOf((int) l);
                return Long.valueOf(l);
            }
            return Double.valueOf(Double.parseDouble(t));
        } catch (NumberFormatException e) {
            throw new JSONException("Bad number: " + t);
        }
    }
}
