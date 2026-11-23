package com.zai.adshield.test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 更新检查网络请求审计测试（v7.9.8 更新检查架构改造的自动防复发闸）。
 * ─────────────────────────────────────────────
 * 用户指令：「Android 客户端更新检查代码不得再直接调用
 * api.github.com/repos/.../releases/latest。不能只靠代码审查口头保证。
 * 同时检查整个更新流程中是否还存在其他直接调用 GitHub REST API 的位置。
 * 日志上传接口是独立功能，本轮不得误删或破坏。」
 *
 * 实现方式（非口头保证——源文件级自动扫描）：
 *  S1 Net.java 无 api.github.com 可执行引用（历史说明注释除外——剥注释扫描）。
 *  S2 MainActivity.java 无 netGetLatestRelease / Net.latestRelease 桥残留。
 *  S3 UpdateRepository 双清单源为静态文件地址（raw.githubusercontent.com
 *     主源 + github.io 备用源），无任何 GitHub REST API 地址。
 *  S4 app.js 无 api.github.com（整个页面资产——含浏览器预览路径）。
 *  S5 全 Android 源码面（src + assets/www）api.github.com 可执行引用
 *     仅允许出现在 core/LogUploader.java（日志上传 Issue 核实——独立功能，
 *     本轮明确保留，不得误删）。
 *  S6 更新链路文件无 /releases/latest 调用形态。
 *
 * 扫描器：剥块/行注释（尊重字符串字面量）后做子串断言——注释里的历史
 * 根因说明不会误报，字符串字面量里的真实 URL 不会漏报。
 */
public final class UpdateAuditTest {

    private static final String[] SEARCH_ROOTS = {"src", "../src", "android-app/src",
            "assets/www", "../assets/www", "android-app/assets/www"};

    public static void main(String[] args) {
        TestHost.suite("UpdateAuditTest · 更新检查网络请求审计（零 GitHub REST API）");
        File appRoot = locateAppRoot();
        TestHost.check(appRoot != null, "A0 定位 android-app 源码根（运行目录自适应）");
        if (appRoot == null) {
            TestHost.done();
            return;
        }
        s1_netJava(appRoot);
        s2_mainActivity(appRoot);
        s3_updateRepository(appRoot);
        s4_appJs(appRoot);
        s5_wholeSource(appRoot);
        s6_noReleasesLatest(appRoot);
        TestHost.done();
    }

    /* ── 运行目录自适应（run-tests.sh 从 android-app/ 或仓库根均可）── */
    private static File locateAppRoot() {
        File f = new File("src/com/zai/adshield/Net.java");
        if (f.isFile()) return new File(".").getAbsoluteFile().getParentFile();
        f = new File("android-app/src/com/zai/adshield/Net.java");
        if (f.isFile()) return new File("android-app").getAbsoluteFile();
        // CI / 异常工作目录：沿父目录向上找（最多 6 层）
        File cur = new File(".").getAbsoluteFile();
        for (int i = 0; i < 6 && cur != null; i++) {
            f = new File(cur, "src/com/zai/adshield/Net.java");
            if (f.isFile()) return cur;
            f = new File(cur, "android-app/src/com/zai/adshield/Net.java");
            if (f.isFile()) return new File(cur, "android-app");
            cur = cur.getParentFile();
        }
        return null;
    }

    private static String read(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** 剥注释（块注释与行注释），尊重字符串字面量——返回可执行代码面。 */
    /* v7.11.0 修复：正则字面量感知。旧版扫描器不识别 JS 正则字面量——
     * esc() 的 /[&<>"']/g 内嵌引号会把状态机打入字符串/字符模式并相位漂移，
     * 随 app.js 内容改动（v7.11.0 订阅块移除/帮助页新增）恰好把「注释里的
     * api.github.com 历史记述」泄漏进剥离输出 → A4a/A4d/A5a/A6 误报。
     * 新增 mode 5（正则字面量）：模式 0 下 / 前一有效字符为 ( , = : [ ! & | ? { ; 等
     * 运算符/开括号时按正则进入（跳过字符类 [] 内的 / 与 ]，直至未转义 / 结束），
     * 字符串/字符字面量内容照常保留（URL 审计语义不变——只剔注释）。 */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        int mode = 0; // 0 代码 / 1 行注释 / 2 块注释 / 3 字符串 / 4 字符 / 5 正则
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (mode == 0) {
                if (c == '/' && next == '/') { mode = 1; i += 2; continue; }
                if (c == '/' && next == '*') { mode = 2; i += 2; continue; }
                if (c == '"') { mode = 3; out.append(c); i++; continue; }
                if (c == '\'') { mode = 4; out.append(c); i++; continue; }
                if (c == '/' && regexAllowed(out)) { mode = 5; out.append(c); i++; continue; }
                out.append(c); i++;
            } else if (mode == 1) {
                if (c == '\n') { mode = 0; out.append(c); }
                i++;
            } else if (mode == 2) {
                if (c == '*' && next == '/') { mode = 0; i += 2; continue; }
                i++;
            } else if (mode == 3) {
                out.append(c);
                if (c == '\\' && i + 1 < n) { out.append(src.charAt(i + 1)); i += 2; continue; }
                if (c == '"') mode = 0;
                i++;
            } else if (mode == 4) {
                out.append(c);
                if (c == '\\' && i + 1 < n) { out.append(src.charAt(i + 1)); i += 2; continue; }
                if (c == '\'') mode = 0;
                i++;
            } else { // mode 5：正则字面量——内容保留（URL 审计不剔正则），引号不再扰乱状态机
                out.append(c);
                if (c == '\\' && i + 1 < n) { out.append(src.charAt(i + 1)); i += 2; continue; }
                if (c == '[') { // 字符类：跳到 ] （类内的 / 与 ] 不结束正则）
                    int j = i + 1;
                    while (j < n && src.charAt(j) != ']') {
                        if (src.charAt(j) == '\\' && j + 1 < n) j += 2; else j++;
                    }
                    int end = Math.min(j + 1, n);
                    out.append(src, i + 1, end);
                    i = end;
                    continue;
                }
                if (c == '/') mode = 0;
                i++;
            }
        }
        return out.toString();
    }

    /** 模式 0 下 / 是否为正则起点（前一有效字符为运算符/开括号/起始 → 正则；
     *  标识符/数字/)/]/引号之后 → 除号——JS 经典启发式，对本仓库源码足够）。 */
    static boolean regexAllowed(StringBuilder out) {
        for (int k = out.length() - 1; k >= 0; k--) {
            char p = out.charAt(k);
            if (p == ' ' || p == '\t' || p == '\n' || p == '\r') continue;
            return !(Character.isLetterOrDigit(p) || p == ')' || p == ']' || p == '"' || p == '\'');
        }
        return true; // 文件起始
    }

    /* ═══ S1 Net.java ═══ */
    private static void s1_netJava(File root) {
        String code = stripComments(read(new File(root, "src/com/zai/adshield/Net.java")));
        TestHost.check(!code.contains("api.github.com"),
                "A1a Net.java 可执行代码零 api.github.com（GitHub 直连更新通道已移除）");
        TestHost.check(!code.contains("releases/latest") && !code.contains("/releases?"),
                "A1b Net.java 零 /releases/latest 与 /releases 列表调用形态");
    }

    /* ═══ S2 MainActivity ═══ */
    private static void s2_mainActivity(File root) {
        String code = stripComments(read(new File(root, "src/com/zai/adshield/MainActivity.java")));
        TestHost.check(!code.contains("netGetLatestRelease"),
                "A2a MainActivity 零 netGetLatestRelease 桥残留");
        TestHost.check(!code.contains("Net.latestRelease"),
                "A2b MainActivity 零 Net.latestRelease 调用");
        TestHost.check(code.contains("updateCheck") && code.contains("UpdateRepository.check"),
                "A2c 更新检查统一走 UpdateRepository.check（唯一数据访问层）");
        TestHost.check(!code.contains("api.github.com"),
                "A2d MainActivity 零 api.github.com");
    }

    /* ═══ S3 UpdateRepository 源链（v7.9.9：raw 主源 + jsDelivr 四接入点）═══ */
    private static void s3_updateRepository(File root) {
        String code = stripComments(read(new File(root, "src/com/zai/adshield/UpdateRepository.java")));
        TestHost.check(!code.contains("api.github.com"),
                "A3a UpdateRepository 零 api.github.com（架构红线）");
        TestHost.check(code.contains("https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json"),
                "A3b 主源 = raw.githubusercontent.com main 分支静态清单");
        // v7.9.9：github.io 备用源已移除（Pages 未启用恒 404 + 国内阻断——
        // 小米 15 Pro 真机双源全灭根因）；备用链改 jsDelivr 四接入点
        TestHost.check(!code.contains("xiaobaiba999.github.io"),
                "A3c github.io 死源已移除（Pages 未启用 + 国内不可达）");
        boolean jsdelivrAll = code.contains("https://cdn.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json")
                && code.contains("https://fastly.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json")
                && code.contains("https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json")
                && code.contains("https://testingcf.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/update/latest.json");
        TestHost.check(jsdelivrAll,
                "A3d 备用链 = jsDelivr 四接入点（cdn/fastly/gcore/testingcf——SubMirrors 同族）");
        // 链预算闸在场（五源链不膨胀的时间面证据）
        TestHost.check(code.contains("CHAIN_BUDGET_MS"),
                "A3e 整链时间预算闸在场（长阻塞防线）");
    }

    /* ═══ S4 app.js ═══ */
    private static void s4_appJs(File root) {
        String code = stripComments(read(new File(root, "assets/www/app.js")));
        TestHost.check(!code.contains("api.github.com"),
                "A4a app.js 可执行代码零 api.github.com（含浏览器预览路径）");
        TestHost.check(!code.contains("netGetLatestRelease"),
                "A4b app.js 零 netGetLatestRelease 调用");
        TestHost.check(code.contains("AndroidApp.updateCheck"),
                "A4c app.js 更新检查走 AndroidApp.updateCheck 桥");
        TestHost.check(code.contains("https://raw.githubusercontent.com/xiaobaiba999/Skip-Ad/main/update/latest.json"),
                "A4d 浏览器预览路径 = 静态清单（与 APK 原生同源）");
        TestHost.check(code.contains("updateErrText"),
                "A4e 错误十类人话映射在场（不混为一谈）");
    }

    /* ═══ S5 全源码面 api.github.com 允许清单 ═══ */
    private static void s5_wholeSource(File root) {
        List<File> files = new ArrayList<File>();
        collectJava(new File(root, "src"), files);
        files.add(new File(root, "assets/www/app.js"));
        int violations = 0;
        StringBuilder where = new StringBuilder();
        for (File f : files) {
            String rel = relativeName(root, f);
            // 唯一允许：LogUploader 的 Issue 核实（日志上传独立功能——用户指令保留）
            if (rel.endsWith("core/LogUploader.java")) continue;
            String code = stripComments(read(f));
            if (code.contains("api.github.com")) {
                violations++;
                where.append(rel).append(' ');
            }
        }
        TestHost.check(violations == 0,
                "A5a 全源码面 api.github.com 仅 LogUploader（日志上传 Issue 核实——独立功能保留）；违规: [" + where + "]");
        // LogUploader 的 Issue 核实仍在（未误删）
        String logUploader = stripComments(read(new File(root, "src/com/zai/adshield/core/LogUploader.java")));
        TestHost.check(logUploader.contains("api.github.com") && logUploader.contains("issues/"),
                "A5b LogUploader Issue 核实链路保留（独立功能未破坏）");
    }

    /* ═══ S6 更新链路文件零 /releases/latest 形态 ═══ */
    private static void s6_noReleasesLatest(File root) {
        String[] updateChain = {
                "src/com/zai/adshield/Net.java",
                "src/com/zai/adshield/UpdateRepository.java",
                "src/com/zai/adshield/UpdateManifest.java",
                "src/com/zai/adshield/MainActivity.java",
                "assets/www/app.js",
        };
        int bad = 0;
        StringBuilder where = new StringBuilder();
        for (String p : updateChain) {
            String code = stripComments(read(new File(root, p)));
            if (code.contains("releases/latest")) {
                bad++;
                where.append(p).append(' ');
            }
        }
        TestHost.check(bad == 0, "A6 更新链路五文件零 releases/latest 调用形态；违规: [" + where + "]");
    }

    /* ── 工具 ── */
    private static void collectJava(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) collectJava(f, out);
            else if (f.getName().endsWith(".java")) out.add(f);
        }
    }

    private static String relativeName(File root, File f) {
        String r = root.getAbsolutePath();
        String p = f.getAbsolutePath();
        if (p.startsWith(r)) return p.substring(r.length() + 1).replace('\\', '/');
        return p;
    }

    private static int countOccurrences(String s, String sub) {
        int count = 0, idx = 0;
        while ((idx = s.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
