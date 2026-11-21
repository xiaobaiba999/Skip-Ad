package org.json;

import java.util.ArrayList;
import java.util.List;

/**
 * JSONArray JVM 测试 shim（与 android.jar org.json 二进制签名兼容——见 JSONObject 头注）。
 * 签名逐一对齐 javap 实测：put(Object/int/long/boolean/double) 返回 JSONArray；
 * remove(int) 返回 Object；opt(int) 返回 Object；长度/序列化同 org.json 语义。
 */
public class JSONArray {

    final List<Object> values = new ArrayList<Object>();

    public JSONArray() {
    }

    public JSONArray(String json) throws JSONException {
        JSONObject.JsonParser.parseInto(json.trim(), null, this);
    }

    public JSONArray put(Object value) {
        values.add(value == null ? JSONObject.NULL : value);
        return this;
    }

    public JSONArray put(int value) {
        values.add(Integer.valueOf(value));
        return this;
    }

    public JSONArray put(long value) {
        values.add(Long.valueOf(value));
        return this;
    }

    public JSONArray put(boolean value) {
        values.add(Boolean.valueOf(value));
        return this;
    }

    public JSONArray put(double value) {
        values.add(Double.valueOf(value));
        return this;
    }

    public int length() {
        return values.size();
    }

    public Object opt(int index) {
        return index < 0 || index >= values.size() ? null : values.get(index);
    }

    public JSONObject optJSONObject(int index) {
        Object v = opt(index);
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    public boolean optBoolean(int index) {
        return optBoolean(index, false);
    }

    public boolean optBoolean(int index, boolean fallback) {
        Object v = opt(index);
        if (v instanceof Boolean) return (Boolean) v;
        return fallback;
    }

    public int optInt(int index) {
        return optInt(index, 0);
    }

    public int optInt(int index, int fallback) {
        Object v = opt(index);
        if (v instanceof Number) return ((Number) v).intValue();
        return fallback;
    }

    public long optLong(int index) {
        return optLong(index, 0L);
    }

    public long optLong(int index, long fallback) {
        Object v = opt(index);
        if (v instanceof Number) return ((Number) v).longValue();
        return fallback;
    }

    public String optString(int index) {
        return optString(index, "");
    }

    public String optString(int index, String fallback) {
        Object v = opt(index);
        if (v == null || v == JSONObject.NULL) return fallback;
        return v instanceof String ? (String) v : v.toString();
    }

    /** remove(int) → Object（org.json 同签名；返回被移除项或 null）。 */
    public Object remove(int index) {
        if (index < 0 || index >= values.size()) return null;
        return values.remove(index);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) b.append(',');
            JSONObject.JsonParser.value(b, values.get(i));
        }
        b.append(']');
        return b.toString();
    }
}
