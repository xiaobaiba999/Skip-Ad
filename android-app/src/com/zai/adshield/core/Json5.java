package com.zai.adshield.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON5 解析器（v4.0.0 订阅融合）
 * ─────────────────────────────────────
 * GKD 订阅使用 JSON5 语法（https://json5.org）：无需键引号、支持
 * 单引号字符串、注释、尾逗号。本解析器输出 java.util 结构
 * （Map / List / String / Double / Boolean / null），零 Android 依赖，
 * 供 Subscription 解析订阅文本。
 *
 * 不追求完整 JSON5：不支持十六进制/Infinity/NaN/多行字符串——
 * 订阅文件用不到；遇到不支持语法抛 ParseException（订阅加载侧
 * 捕获后跳过，绝不影响主引擎）。
 */
public final class Json5 {

    public static final class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ParseException(String msg) {
            super(msg);
        }
    }

    private final String s;
    private int i = 0;

    private Json5(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        if (text == null) throw new ParseException("null json");
        Json5 p = new Json5(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i < p.s.length()) throw new ParseException("trailing content at " + p.i);
        return v;
    }

    /* ── 词法 ── */

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\uFEFF') {
                i++;
            } else if (c == '/' && i + 1 < s.length()) {
                char d = s.charAt(i + 1);
                if (d == '/') { // 行注释
                    while (i < s.length() && s.charAt(i) != '\n') i++;
                } else if (d == '*') { // 块注释
                    int end = s.indexOf("*/", i + 2);
                    if (end < 0) throw new ParseException("unterminated block comment");
                    i = end + 2;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
    }

    private char peek() {
        if (i >= s.length()) throw new ParseException("unexpected end");
        return s.charAt(i);
    }

    /* ── 语法 ── */

    private Object value() {
        ws();
        char c = peek();
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '\'' || c == '"' || c == '`') return string();
        if (c == 't' || c == 'f') return bool();
        if (c == 'n') {
            expect("null");
            return null;
        }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        i++; // {
        ws();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            String key;
            char c = peek();
            if (c == '\'' || c == '"' || c == '`') {
                key = string();
            } else { // 无引号键
                int st = i;
                while (i < s.length()) {
                    char d = s.charAt(i);
                    if (Character.isLetterOrDigit(d) || d == '_' || d == '$' || d == '-') i++;
                    else break;
                }
                if (i == st) throw new ParseException("bad key at " + i);
                key = s.substring(st, i);
            }
            ws();
            if (peek() != ':') throw new ParseException("expect : at " + i);
            i++;
            m.put(key, value());
            ws();
            char t = peek();
            if (t == ',') {
                i++;
                ws();
                if (peek() == '}') { // 尾逗号
                    i++;
                    return m;
                }
                continue;
            }
            if (t == '}') {
                i++;
                return m;
            }
            throw new ParseException("expect , or } at " + i);
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<Object>();
        i++; // [
        ws();
        if (peek() == ']') {
            i++;
            return l;
        }
        while (true) {
            l.add(value());
            ws();
            char t = peek();
            if (t == ',') {
                i++;
                ws();
                if (peek() == ']') { // 尾逗号
                    i++;
                    return l;
                }
                continue;
            }
            if (t == ']') {
                i++;
                return l;
            }
            throw new ParseException("expect , or ] at " + i);
        }
    }

    private String string() {
        char q = s.charAt(i);
        i++;
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                if (i >= s.length()) break;
                char d = s.charAt(i);
                if (d == 'n') b.append('\n');
                else if (d == 'r') b.append('\r');
                else if (d == 't') b.append('\t');
                else if (d == 'b') b.append('\b');
                else if (d == 'f') b.append('\f');
                else if (d == 'u') {
                    if (i + 4 < s.length()) {
                        try {
                            b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            b.append(d);
                        }
                    } else {
                        b.append(d);
                    }
                } else {
                    b.append(d); // \' \" \\ \/ \`
                }
                i++;
            } else if (c == q) {
                i++;
                return b.toString();
            } else if (c == '\n') {
                throw new ParseException("newline in string (JSON5 multi-line unsupported)");
            } else {
                b.append(c);
                i++;
            }
        }
        throw new ParseException("unterminated string");
    }

    private Boolean bool() {
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        throw new ParseException("bad bool at " + i);
    }

    private Double number() {
        int st = i;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        boolean digits = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                digits = true;
                i++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                i++;
            } else {
                break;
            }
        }
        if (!digits) throw new ParseException("bad value at " + st);
        try {
            return Double.parseDouble(s.substring(st, i));
        } catch (NumberFormatException e) {
            throw new ParseException("bad number at " + st);
        }
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw new ParseException("expect " + word + " at " + i);
        i += word.length();
    }

    /* ── 取值工具（订阅解析用） ── */

    public static Map<String, Object> asMap(Object o) {
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        return null;
    }

    public static List<Object> asList(Object o) {
        if (o instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) o;
            return l;
        }
        return null;
    }

    public static String str(Map<String, Object> m, String key, String def) {
        if (m == null) return def;
        Object v = m.get(key);
        return v instanceof String ? (String) v : def;
    }

    public static double num(Map<String, Object> m, String key, double def) {
        if (m == null) return def;
        Object v = m.get(key);
        return v instanceof Double ? ((Double) v).doubleValue() : def;
    }

    public static boolean boolv(Map<String, Object> m, String key, boolean def) {
        if (m == null) return def;
        Object v = m.get(key);
        return v instanceof Boolean ? ((Boolean) v).booleanValue() : def;
    }
}
