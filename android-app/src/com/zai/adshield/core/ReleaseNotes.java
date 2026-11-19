package com.zai.adshield.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Release notes → 新版本功能标题解析（v7.0.0 · 纯函数 · JVM 可测）。
 * ─────────────────────────────────────────────
 * 用户实锤（检查更新只弹版本号、看不到下一版本的功能描述；新版描述要
 * 言简意赅、带序号、不超过 5 个标题）：
 *  · 检查更新发现新版本时当场显示 ≤5 条【带序号】功能标题；
 *  · 解析优先级：阿拉伯序号行（1. / 1、/ 1) ——本仓库 v7.0.0 起的规范
 *    发布体）优先；无序号行时回落历史列表格式（①圈号 / - * 列表行）；
 *  · # 标题行【绝不当功能标题】——它是章节名/版本名（如「### 新版本
 *    功能」「## 新版亮点」），不是功能本身；版本名由调用方另行展示
 *    （tag/「发现新版本 vX.Y.Z」），标题行混入只会挤占 5 条名额；
 *  · 标题行必须「像标题」：太短（<2 字）、表格行、引用行、代码围栏、
 *    纯链接行、分隔线全部过滤；行内 markdown 噪点（* ` > 链接锚文本）
 *    清洗后截断 44 字（言简意赅）。
 *
 * 纯函数零 Android 依赖：Net.parseRelease 组装进 latestRelease JSON 的
 * headlines[] 字段（桥同步返回——沿用 v6.0/v6.1 已实测的同步桥形态，
 * 绝不引入异步桥），JS 侧优先渲染 headlines[]，为空时回落 JS 本地解析。
 */
public final class ReleaseNotes {

    private ReleaseNotes() {
    }

    /** 新版功能标题上限（用户指令：不要超过 5 个标题） */
    public static final int MAX_HEADLINES = 5;
    /** 单标题截断长度（言简意赅） */
    public static final int MAX_LEN = 44;

    /**
     * 解析 Release notes 正文 → ≤5 条功能标题（保持正文行序）。
     * 空/null → 空数组（调用方回落 JS 解析或显示「无描述」）。
     */
    public static String[] headlines(String notes) {
        List<String> out = new ArrayList<>();
        if (notes == null || notes.length() == 0) return new String[0];
        String[] lines = notes.split("\n");
        // 第一遍：序号行（v7.0.0 规范发布体）——有则优先（章节名/标题行绝不混入）
        for (int i = 0; i < lines.length && out.size() < MAX_HEADLINES; i++) {
            String s = numberedLine(lines[i].trim());
            if (s == null) continue;
            if (!out.contains(s)) out.add(s);
        }
        // 第二遍：历史格式回落（无任何序号行时才启用——①圈号 / - * 列表）
        if (out.isEmpty()) {
            for (int i = 0; i < lines.length && out.size() < MAX_HEADLINES; i++) {
                String s = legacyLine(lines[i].trim());
                if (s == null) continue;
                if (!out.contains(s)) out.add(s);
            }
        }
        return out.toArray(new String[0]);
    }

    /** 序号行 → 标题文本；非序号行返回 null（1~2 位数字前导 + 分隔符） */
    static String numberedLine(String t) {
        if (t == null || t.length() < 3) return null;
        char c0 = t.charAt(0);
        if (c0 < '0' || c0 > '9') return null;
        int p = 1;
        while (p < t.length() && t.charAt(p) >= '0' && t.charAt(p) <= '9') p++;
        if (p > 2 || p >= t.length()) return null; // 1~2 位序号（“2026 年…”四位年份不算）
        char sep = t.charAt(p);
        if (sep == '.' || sep == '、' || sep == ')' || sep == '）' || sep == ':' || sep == '：') {
            return clean(t.substring(Math.min(p + 1, t.length())).trim());
        }
        return null;
    }

    /** 历史列表行 → 标题文本（①圈号 / - / * 列表；# 标题行绝不入列） */
    static String legacyLine(String t) {
        if (t == null || t.length() < 3) return null;
        if (t.startsWith("- ") || t.startsWith("* ")) return clean(t.substring(2).trim());
        char c = t.charAt(0);
        if (c >= 0x2460 && c <= 0x2473) return clean(t.substring(1).trim()); // ①~⑳
        return null;
    }

    /** 清洗 markdown 噪点并截断（言简意赅） */
    static String clean(String s) {
        if (s == null) return null;
        String out = s;
        // 链接 → 锚文本
        out = out.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        // 强调/代码/引用符号
        out = out.replace("*", "").replace("`", "").replace(">", "").trim();
        if (out.length() < 2) return null;
        if (out.length() > MAX_LEN) out = out.substring(0, MAX_LEN - 1) + "…";
        return out;
    }
}
