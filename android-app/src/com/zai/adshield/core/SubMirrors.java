package com.zai.adshield.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 订阅镜像链 + 离线快照映射（v7.0.0 · 纯函数 · JVM 可测）。
 * ─────────────────────────────────────────────
 * 用户实锤（订阅市场点击导入时提示「拉取失败，Connection reset」）：
 * 国内网络对 cdn.jsdelivr.net 等海外源的 TCP 间歇性重置，v6.1 的五通道
 * 链在部分运营商网络下仍可能全军覆没。v7.0.0 两手根治：
 *  · 八通道镜像链：原址 → fastly → gcore → testingcf（jsdelivr 三接入点，
 *    重置概率互相独立）→ ghfast.top 代理 → gh-proxy.com 代理 → raw 直连
 *    （每个通道配合同址瞬时故障重试，最坏 14 次出手任一成功即导入成功）；
 *  · 离线快照兜底：三个市场精选源的最新内容随 APK 内置
 *    （assets/subsnap/*.json5，aapt2 deflate 压缩后合计约 520KB）——
 *    全通道失败时直接导入内置快照（标注「离线快照」，联网后 1 小时
 *    心跳自动重拉最新版）。导入路径从此【永不硬失败】。
 *
 * 本类为纯函数（无 Android 依赖）：镜像链顺序、快照归一化匹配全部
 * JVM 断言锁定——任何回归直接测试红。
 */
public final class SubMirrors {

    private SubMirrors() {
    }

    /* ───────── 市场精选源与内置离线快照的映射 ───────── */

    /** 快照资产名（assets/ 下相对路径） */
    public static final String ASSET_LINARM = "subsnap/linarm.json5";
    public static final String ASSET_GANLIN = "subsnap/ganlin.json5";
    public static final String ASSET_XNX = "subsnap/xnx.json5";

    /**
     * 市场精选源注册表：{规范 jsdelivr URL, 快照资产名}。
     * 第一列是 SubscriptionManager.RECOMMENDED_SUBS 的 URL（同一数据源，
     * 市场渲染与快照匹配共用一套事实）。
     */
    public static final String[][] SNAPSHOT_SOURCES = {
            {"https://cdn.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5", ASSET_LINARM},
            {"https://cdn.jsdelivr.net/gh/ganlinte/GKD-subscription@main/dist/ganlin_gkd.json5", ASSET_GANLIN},
            {"https://cdn.jsdelivr.net/gh/MengNianxiaoyao/gkd-subscription@main/dist/gkd.json5", ASSET_XNX},
    };

    /**
     * URL 的快照资产名（归一化匹配：任意 jsdelivr 接入点变体都命中同一
     * 快照——用户从市场导入的是规范 URL，从历史记录/剪贴板恢复的可能是
     * 镜像变体，二者都应匹配）。未知源返回 null（自定义链接无快照兜底）。
     */
    public static String snapshotAssetFor(String url) {
        String key = jsdelivrKey(url);
        if (key == null) return null;
        for (String[] s : SNAPSHOT_SOURCES) {
            if (key.equals(jsdelivrKey(s[0]))) return s[1];
        }
        return null;
    }

    /**
     * jsdelivr gh URL 的归一化键：owner/repo@branch/path（cdn/fastly/gcore/
     * testingcf 四接入点同键）。非 jsdelivr gh URL 返回 null。
     */
    public static String jsdelivrKey(String url) {
        if (url == null) return null;
        String rest = null;
        if (url.startsWith("https://cdn.jsdelivr.net/gh/")) rest = url.substring("https://cdn.jsdelivr.net/gh/".length());
        else if (url.startsWith("https://fastly.jsdelivr.net/gh/")) rest = url.substring("https://fastly.jsdelivr.net/gh/".length());
        else if (url.startsWith("https://gcore.jsdelivr.net/gh/")) rest = url.substring("https://gcore.jsdelivr.net/gh/".length());
        else if (url.startsWith("https://testingcf.jsdelivr.net/gh/")) rest = url.substring("https://testingcf.jsdelivr.net/gh/".length());
        else if (url.startsWith("https://jsd.jsdelivr.net/gh/")) rest = url.substring("https://jsd.jsdelivr.net/gh/".length());
        if (rest == null) return null;
        int at = rest.indexOf('@');
        int slash = at < 0 ? -1 : rest.indexOf('/', at + 1);
        if (at <= 0 || slash <= at) return null;
        return rest; // owner/repo@branch/path（整个剩余段即归一化键）
    }

    /* ───────── 八通道镜像链 ───────── */

    /**
     * 完整镜像变体链（按顺序逐个尝试，成功即止）：
     *  · jsdelivr gh URL：原址 → fastly → gcore → testingcf →
     *    ghfast.top 代理 → gh-proxy.com 代理 → raw.githubusercontent 直连；
     *  · raw.githubusercontent URL：原址 → jsdelivr CDN 兜底（v4.1.0 链）；
     *  · @gkd-kit/subscription 官方：npmmirror 镜像迁移路径（v4.1.0 链）；
     *  · 其余 URL：仅原址（自定义源无已知镜像规则）。
     * <p>每通道都配合 Net.httpGetRaw 的同址瞬时故障重试（600ms 间隔 1 次）
     * ——最坏 7 通道 × 2 次 = 14 次出手。
     */
    public static List<String> variants(String url) {
        List<String> list = new ArrayList<>();
        if (url == null || url.length() == 0) return list;
        list.add(url);
        try {
            // ① @gkd-kit/subscription 官方包路径迁移（v4.1.0：npmmirror 上
            //    files/gkd.json5 → files/dist/gkd.json5）
            if (url.contains("registry.npmmirror.com/@gkd-kit/subscription")
                    && url.endsWith("/files/gkd.json5")) {
                addUnique(list, url.substring(0, url.length() - "/gkd.json5".length()) + "/dist/gkd.json5");
            }
            // ② @gkd-kit 官方直连不可达 → npmmirror 镜像（阿里系 CDN 国内常稳）
            boolean gkdOfficial = url.contains("gkd-kit/subscription");
            if (gkdOfficial && !url.contains("npmmirror.com")) {
                addUnique(list, "https://registry.npmmirror.com/@gkd-kit/subscription/latest/files/dist/gkd.json5");
            }
            // ③ raw.githubusercontent.com 任意仓库 → jsdelivr CDN 兜底（v4.1.0 链）
            if (url.startsWith("https://raw.githubusercontent.com/")) {
                String rest = url.substring("https://raw.githubusercontent.com/".length());
                int i1 = rest.indexOf('/');
                int i2 = i1 < 0 ? -1 : rest.indexOf('/', i1 + 1);
                int i3 = i2 < 0 ? -1 : rest.indexOf('/', i2 + 1);
                if (i1 > 0 && i2 > i1 && i3 > i2) {
                    String owner = rest.substring(0, i1);
                    String repo = rest.substring(i1 + 1, i2);
                    String branch = rest.substring(i2 + 1, i3);
                    String path = rest.substring(i3 + 1);
                    addUnique(list, "https://cdn.jsdelivr.net/gh/" + owner + "/" + repo + "@" + branch + "/" + path);
                }
            }
            // ④ v7.0.0 八通道主链（Connection reset 根治）：jsdelivr gh URL →
            //    四接入点轮换 + 双代理 + raw 直连。任何接入点变体作原址输入
            //    都展开出同一条链（归一化后同集合——canonical cdn 必在其中，
            //    原址永远打头）。
            String key = jsdelivrKey(url);
            if (key != null) {
                addUnique(list, "https://cdn.jsdelivr.net/gh/" + key);
                addUnique(list, "https://fastly.jsdelivr.net/gh/" + key);
                addUnique(list, "https://gcore.jsdelivr.net/gh/" + key);
                addUnique(list, "https://testingcf.jsdelivr.net/gh/" + key);
                String gh = ghRawOf(key);
                if (gh != null) {
                    addUnique(list, "https://ghfast.top/" + gh);
                    addUnique(list, "https://gh-proxy.com/" + gh);
                    addUnique(list, gh);
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /** jsdelivr 归一化键 → GitHub raw 直连 URL（owner/repo@branch/path 拆解） */
    static String ghRawOf(String key) {
        if (key == null) return null;
        int at = key.indexOf('@');
        int slash = at < 0 ? -1 : key.indexOf('/', at + 1);
        if (at <= 0 || slash <= at) return null;
        String ownerRepo = key.substring(0, at);        // owner/repo
        String branch = key.substring(at + 1, slash);
        String path = key.substring(slash + 1);
        return "https://raw.githubusercontent.com/" + ownerRepo + "/" + branch + "/" + path;
    }

    /** 变体链的主机名（进度提示「正在切换镜像 X/Y · host」用） */
    public static String hostOf(String url) {
        if (url == null) return "";
        try {
            int s = url.indexOf("://");
            if (s < 0) return url.length() > 24 ? url.substring(0, 24) : url;
            int e = url.indexOf('/', s + 3);
            return e < 0 ? url.substring(s + 3) : url.substring(s + 3, e);
        } catch (Throwable t) {
            return "";
        }
    }

    private static void addUnique(List<String> list, String url) {
        if (url == null || url.length() == 0) return;
        for (String s : list) {
            if (s.equals(url)) return;
        }
        list.add(url);
    }
}
