package com.zai.adshield.core;

/**
 * v7.15.3 更新源 jsdelivr 多镜像域序（纯函数，JVM 可测，零 Android 依赖）。
 * ─────────────────────────────────────────────────────────────────
 * 真机实锤（v7.15.1 用户截图「多源均不可达：raw@main 不可达 · jsdelivr@main
 * 不可达 · GitHub API 返回 401」三面全崩死因之二）：cdn.jsdelivr.net 在
 * 大陆移动网络存在间歇性连接层不可达（DNS 污染/TCP 重置，与 v6.1.0 订阅
 * 拉取 Connection reset 同源病灶）——旧版检查更新清单源对 jsdelivr 只写死
 * cdn.jsdelivr.net 单域，该域抖动时仅剩 GitHub API 一条路（而 API 兜底又被
 * 失效内置令牌打成 401——见 Net.java v7.15.3 令牌轮换与匿名优先）。
 * 本轮结构性修复：
 *  · jsdelivr 四域梯度（国内可达性经验序）：gcore → cdn → fastly →
 *    testingcf——任一域活着清单就能到达；
 *  · 域偏好 sticky：任一域成功后由调用方（UpdateManifest）进程内记住，
 *    后续检查与 @tag 探测直接复用偏好域——正常路径零额外请求、抖动域
 *    永不再先试；
 *  · @tag 探测域收敛 ≤2（偏好域 + gcore 兜底）——4 tag × 3s 快失败
 *    预算不随域数膨胀。
 * 约束：与 core 包一致——不使用匿名类/内部类/lambda（d8 8.2 规约）。
 */
public final class UpdateHosts {

    private UpdateHosts() {
    }

    /** jsdelivr 四域基础序（国内可达性梯度：gcore 最优 → cdn → fastly → testingcf） */
    public static final String[] JSDELIVR_HOSTS = {
            "gcore.jsdelivr.net", "cdn.jsdelivr.net", "fastly.jsdelivr.net", "testingcf.jsdelivr.net"};

    /**
     * @main 清单探测域序：偏好域置顶 + 基础序跟随（去重）。
     * 无偏好（首次/未成功过）= 基础序。偏好域不在基础序内（外部注入的
     * 非法值）= 基础序——不持有镜像契约的域不探测（攻击面收敛）。
     */
    public static String[] orderedMainHosts(String preferred) {
        return mergePreferred(preferred, JSDELIVR_HOSTS);
    }

    /**
     * @tag 探测域序：偏好域 + gcore（去重后 ≤2 域）。
     * tag 不可变键本就零源站缓存、全域展开会让 4 tag × 3s 快失败
     * 预算翻倍——偏好域（@main 阶段 sticky 下来的，可为四域中任一）置顶
     * + gcore 兜底足够（它也是基础序首位）。偏好域非法（不在四域基础序
     * 内——外部注入值）→ gcore 单域（攻击面收敛，不持镜像契约不探测）。
     */
    public static String[] orderedTagHosts(String preferred) {
        if (preferred == null || preferred.length() == 0) {
            return new String[]{JSDELIVR_HOSTS[0]};
        }
        boolean known = false;
        for (int i = 0; i < JSDELIVR_HOSTS.length; i++) {
            if (JSDELIVR_HOSTS[i].equals(preferred)) {
                known = true;
                break;
            }
        }
        if (!known) return new String[]{JSDELIVR_HOSTS[0]};
        if (preferred.equals(JSDELIVR_HOSTS[0])) return new String[]{JSDELIVR_HOSTS[0]};
        return new String[]{preferred, JSDELIVR_HOSTS[0]};
    }

    /** 偏好域置顶合并（preferred 在 base 内 → 置顶；不在 → base 原样） */
    private static String[] mergePreferred(String preferred, String[] base) {
        if (preferred == null || preferred.length() == 0) return base;
        boolean contains = false;
        for (int i = 0; i < base.length; i++) {
            if (base[i].equals(preferred)) {
                contains = true;
                break;
            }
        }
        if (!contains) return base;
        String[] out = new String[base.length];
        out[0] = preferred;
        int w = 1;
        for (int i = 0; i < base.length; i++) {
            if (!base[i].equals(preferred)) {
                out[w] = base[i];
                w++;
            }
        }
        return out;
    }

    /* ═══ v7.15.5 下载通道白名单 + CDN 直链合成（用户实锤：v7.15.1/v7.15.3
     * 检测到新版本后点下载报「下载链接不在允许的发布通道内」）═══
     * 死因：latest.json 的 cdnApkUrl（gcore.jsdelivr.net）是渠道链首位，
     * 而 Downloader.hostAllowed 白名单只认 github/githubusercontent/
     * ghfast/gh-proxy——jsdelivr 四域全被拒，且 JS 侧渠道启动被拒不换
     * 通道（死局）。修复三件套：
     *  ① 本纯函数（isJsdelivrHost）：白名单收编 jsdelivr 四域——它们是
     *     本仓库官方 CDN 发布通道（@main dist APK 直出，与 Release 资产
     *     同字节同源），安装前 SHA-256 全量闸（v6.3.0）保持——通道扩张
     *     不扩投毒面；JVM 可测（UpdateHostsTest）。
     *  ② 清单侧撤 cdnApkUrl 字段（update_manifest.py + update/latest.json）
     *     ——v7.15.0~v7.15.4 场端旧版看不到 gcore 渠道，首渠道回到
     *     ghfast.top（白名单内），不升级也能恢复正常下载。
     *  ③ 新版客户端（本版起）在 UpdateManifest.pageJson 无该字段时用
     *     cdnApkUrl() 合成 gcore 直链——CDN 优先的可达性保持。 */

    /** host 是否 jsdelivr 官方四域之一（精确等值；尾点 FQDN/大小写混淆收敛）。 */
    public static boolean isJsdelivrHost(String host) {
        if (host == null) return false;
        String h = host.trim().toLowerCase(java.util.Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1); // 尾点 FQDN 混淆
        for (int i = 0; i < JSDELIVR_HOSTS.length; i++) {
            if (JSDELIVR_HOSTS[i].equals(h)) return true;
        }
        return false;
    }

    /** 仓库 dist APK 的 gcore CDN 直链（清单 cdnApkUrl 撤除后的客户端合成；
     *  fileName 非 .apk / 空仓库名 → 空串由调用方跳过）。 */
    public static String cdnApkUrl(String repo, String apkFileName) {
        if (repo == null || repo.length() == 0) return "";
        if (apkFileName == null || apkFileName.length() == 0) return "";
        String fn = apkFileName.trim();
        if (!fn.endsWith(".apk") || fn.contains("/") || fn.contains("\\")) return ""; // 路径穿越收敛
        return "https://gcore.jsdelivr.net/gh/" + repo + "@main/android-app/dist/" + fn;
    }
}
