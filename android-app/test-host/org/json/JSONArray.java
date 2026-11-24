package org.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务级 JVM 测试桩：JSONArray（保序、可空、round-trip 序列化/解析正确——
 * 服务状态经 prefs 字符串持久化，解析与序列化必须严格互逆）。
 */
public class JSONArray {
    private final List<Object> items = new ArrayList<Object>();

    public JSONArray() { }

    public JSONArray(String json) throws JSONException {
        Object v = new JSONParser(json).parse();
        if (!(v instanceof JSONArray)) throw new JSONException("Not an array");
        JSONArray a = (JSONArray) v;
        for (int i = 0; i < a.items.size(); i++) items.add(a.items.get(i));
    }

    public int length() { return items.size(); }

    public Object get(int i) { return items.get(i); }

    public JSONObject getJSONObject(int i) throws JSONException {
        Object v = items.get(i);
        if (!(v instanceof JSONObject)) throw new JSONException("Not an object at " + i);
        return (JSONObject) v;
    }

    public String getString(int i) throws JSONException {
        Object v = items.get(i);
        return v == null ? null : v.toString();
    }

    public boolean getBoolean(int i) throws JSONException {
        Object v = items.get(i);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        throw new JSONException("Not a boolean at " + i);
    }

    public int getInt(int i) throws JSONException {
        Object v = items.get(i);
        if (v instanceof Number) return ((Number) v).intValue();
        throw new JSONException("Not an int at " + i);
    }

    public long getLong(int i) throws JSONException {
        Object v = items.get(i);
        if (v instanceof Number) return ((Number) v).longValue();
        throw new JSONException("Not a long at " + i);
    }

    public String optString(int i) { return optString(i, ""); }

    public String optString(int i, String def) {
        Object v = i >= 0 && i < items.size() ? items.get(i) : null;
        return v == null ? def : v.toString();
    }

    public int optInt(int i) { return optInt(i, 0); }

    public int optInt(int i, int def) {
        Object v = i >= 0 && i < items.size() ? items.get(i) : null;
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public long optLong(int i) { return optLong(i, 0L); }

    public long optLong(int i, long def) {
        Object v = i >= 0 && i < items.size() ? items.get(i) : null;
        return v instanceof Number ? ((Number) v).longValue() : def;
    }

    public boolean optBoolean(int i) { return optBoolean(i, false); }

    public boolean optBoolean(int i, boolean def) {
        Object v = i >= 0 && i < items.size() ? items.get(i) : null;
        return v instanceof Boolean ? ((Boolean) v).booleanValue() : def;
    }

    public double optDouble(int i) { return optDouble(i, Double.NaN); }

    public double optDouble(int i, double def) {
        Object v = i >= 0 && i < items.size() ? items.get(i) : null;
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }

    public JSONObject optJSONObject(int i) {
        Object v = i >= 0 && i < items.size() ? items.get(i) : null;
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    public JSONArray optJSONArray(int i) {
        Object v = i >= 0 && i < items.size() ? items.get(i) : null;
        return v instanceof JSONArray ? (JSONArray) v : null;
    }

    public boolean isNull(int i) { return i >= 0 && i < items.size() && items.get(i) == null; }

    public JSONArray put(boolean v) { items.add(Boolean.valueOf(v)); return this; }
    public JSONArray put(double v) throws JSONException { items.add(Double.valueOf(v)); return this; }
    public JSONArray put(int v) { items.add(Integer.valueOf(v)); return this; }
    public JSONArray put(long v) { items.add(Long.valueOf(v)); return this; }
    public JSONArray put(Object v) { items.add(v); return this; }

    public Object remove(int i) {
        if (i < 0 || i >= items.size()) return null;
        return items.remove(i);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(JSONObject.valueToString(items.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    static List<Object> backing(JSONArray a) { return a.items; }
}
