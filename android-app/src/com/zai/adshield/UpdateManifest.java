package com.zai.adshield;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 静态更新清单（update/latest.json）解析与校验（v7.9.8 更新检查架构改造）。
 * ─────────────────────────────────────────────
 * 背景（根因）：v7.9.7 及以前检查更新直连 GitHub Releases REST API
 * （GET /repos/{repo}/releases/latest，匿名 60 次/小时/IP 出口配额）。
 * 国内移动网络共享出口 IP 场景下所有用户消耗同一配额池——更新页即
 * 「GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试」。
 *
 * 新架构：更新元数据只从仓库 main 分支静态清单读取
 * （raw.githubusercontent.com · Fastly CDN · 无 REST API 配额），由
 * GitHub Actions（.github/workflows/update-manifest.yml）在正式 Release
 * 发布时从真实 Release + 真实 APK 资产自动生成（包名/同名同码/SHA256/
 * 大小全量校验）。本类是清单的客户端契约层：
 *
 *  · 校验链（任何失败 = 拒绝使用该清单，绝不降级安装）：
 *    - JSON 可解析且为对象（非 JSON/空 → UPDATE_INVALID_MANIFEST）
 *    - draft/prerelease 标记 → 不作为正式更新（hasRelease=false，非错误）
 *    - packageName 必须与当前应用一致（缺 → INVALID；不同 → PACKAGE_MISMATCH）
 *    - apkUrl（若非空）必须 HTTPS 且在官方发布通道域内（Downloader.hostAllowed）
 *    - apkSha256（若非空）必须是合法 64 位十六进制（形态污染 → INVALID）
 *  · 版本判定（v7.10.0 统一规则——与 Net.isNewerRelease 同一权威语义）：
 *    - isNewer = remoteVersionCode > currentVersionCode；versionCode
 *      缺失/非法（≤0）→ 不判新（绝不回退 Release 正文「versionCode:」
 *      元数据行或 versionName 名比较——旧回退链已随统一规则专项删除）；
 *    - versionName 仅作展示（tag 派生兜底语义保持）
 *  · 输出与旧 Net.parseRelease 字段兼容（tag/name/notes/headlines/apkUrl/
 *    apkFileName/apkMB/apkSize/apkDigest/apkMirrors/remoteVersionCode/isNewer）
 *    ——更新页/下载状态机/全屏更新页零改动消费。
 *
 * 纯函数零 Android 依赖（org.json + Net/Downloader 纯静态路径），JVM 可测。
 * 约束：无 lambda/匿名类/内部类（d8 8.2 兼容）。
 */
public final class UpdateManifest {

    private UpdateManifest() {
    }

    /* ═══ 错误分类码（十类 + TLS——UI 与诊断用；纯字符串常量防 ProGuard 形变）═══
     * 设计原则：网络失败 ≠ 服务端拒绝 ≠ 限流 ≠ 超时 ≠ 清单损坏——
     * 每类如实分类，UI 如实转达（不再一律「稍后再试」）。 */
    public static final String ERR_NETWORK = "UPDATE_NETWORK_ERROR";
    public static final String ERR_DNS = "UPDATE_DNS_ERROR";
    public static final String ERR_TIMEOUT = "UPDATE_TIMEOUT";
    public static final String ERR_TLS = "UPDATE_TLS_ERROR";
    public static final String ERR_HTTP = "UPDATE_HTTP_ERROR";
    public static final String ERR_INVALID = "UPDATE_INVALID_MANIFEST";
    public static final String ERR_PACKAGE = "UPDATE_PACKAGE_MISMATCH";
    public static final String ERR_VERSION = "UPDATE_VERSION_INVALID";
    public static final String ERR_NO_CACHE = "UPDATE_NO_CACHE";
    public static final String ERR_RATE_LIMITED = "UPDATE_RATE_LIMITED";
    public static final String ERR_SERVER = "UPDATE_SERVER_ERROR";

    /** 当前清单协议版本（schemaVersion>1 的未来清单仍尽力解析——向前兼容）。 */
    public static final int SCHEMA_VERSION = 1;

    /**
     * 解析并校验静态更新清单。
     *
     * @param raw                清单原始 JSON 文本
     * @param expectedPackage    当前应用包名（ctx.getPackageName()——包名闸）
     * @param currentVersionCode 当前 versionCode（≤0 = 不做 isNewer 判定——
     *                            v7.10.0 统一规则：仅 versionCode 单一判定，
     *                            本方法不再接收 versionName）
     * @return 成功载荷（ok:true，字段见类注释）/ {ok:false, error, errorCode}
     */
    public static String parse(String raw, String expectedPackage, int currentVersionCode) {
        try {
            if (raw == null || raw.trim().length() == 0) {
                return fail(ERR_INVALID, "更新清单为空");
            }
            JSONObject d;
            try {
                d = new JSONObject(raw);
            } catch (Exception e) {
                return fail(ERR_INVALID, "更新清单不是合法 JSON");
            }
            // Draft / Pre-release 标记（清单层防线——正常清单不带；生成端已挡）
            if (d.optBoolean("draft", false) || d.optBoolean("prerelease", false)) {
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("hasRelease", false);
                o.put("source", "manifest");
                o.put("reason", "non-official");
                return o.toString();
            }
            // 包名闸：清单必须来自同一应用（防错仓/换包清单被当作本应用更新）
            String pkg = d.optString("packageName", "");
            if (pkg.length() == 0) {
                return fail(ERR_INVALID, "更新清单缺少 packageName");
            }
            if (expectedPackage != null && expectedPackage.length() > 0
                    && !pkg.equals(expectedPackage)) {
                return fail(ERR_PACKAGE, "更新清单包名不匹配（" + pkg + "），已拒绝");
            }
            // 版本字段（v7.10.0 统一规则：versionCode 缺失/非法 → 0 → 不判新；
            // 绝不回退 Release 正文元数据行/名比较——回退链已删）。
            // 解析用 optLong + int 域校验：org.json optInt 对超 int 数值（如
            // 99999999999）会静默截断回绕成正值（1215752191）→ 误判新版——
            // 必须按「无效 versionCode → 不判新」处理（负数/非数字/超 int 同理）。
            long vcRaw = d.optLong("versionCode", 0L);
            int vc = (vcRaw > 0L && vcRaw <= Integer.MAX_VALUE) ? (int) vcRaw : 0;
            String versionName = d.optString("versionName", "");
            String tag = d.optString("releaseTag", "");
            String name = d.optString("releaseName", "");
            if (versionName.length() == 0 && tag.length() > 0) {
                versionName = tag.startsWith("v") ? tag.substring(1) : tag;
            }
            if (versionName.length() == 0 && vc > 0) {
                versionName = String.valueOf(vc); // 极端降级：仅 versionCode 在场
            }
            if (versionName.length() == 0 && vc <= 0) {
                return fail(ERR_VERSION, "更新清单缺少有效版本信息");
            }
            // APK 下载地址：非空必须 HTTPS + 官方发布通道域（安全闸不降级）
            String apkUrl = d.optString("apkUrl", "");
            if (apkUrl.length() > 0 && !Downloader.hostAllowed(apkUrl)) {
                return fail(ERR_INVALID, "更新清单 APK 地址不在允许的发布通道内，已拒绝");
            }
            // APK SHA256：非空必须 64 位十六进制（sha256: 前缀容忍；大小写不敏感）
            String apkSha256 = normalizeSha(d.optString("apkSha256", ""));
            if (apkSha256 == null) {
                return fail(ERR_INVALID, "更新清单 APK SHA256 格式异常，已拒绝");
            }
            long apkSize = d.optLong("apkSize", 0);
            if (apkSize < 0) apkSize = 0;

            /* ═══ 成功载荷（字段与旧 parseRelease 兼容 + 清单新增字段）═══ */
            JSONObject out = new JSONObject();
            out.put("ok", true);
            out.put("hasRelease", true);
            out.put("source", "manifest");
            out.put("schemaVersion", d.optInt("schemaVersion", 0));
            out.put("tag", tag);
            out.put("name", name.length() > 0 ? name : tag);
            out.put("versionName", versionName);
            out.put("versionCode", vc);
            out.put("remoteVersionCode", vc); // 旧 UI 字段兼容
            String notes = d.optString("releaseNotes", "");
            out.put("notes", notes);
            out.put("headlines", headlinesOf(notes));
            out.put("publishedAt", d.optString("publishedAt", ""));
            out.put("manifestUpdatedAt", d.optString("manifestUpdatedAt", ""));
            out.put("url", tag.length() > 0
                    ? "https://github.com/" + Net.REPO + "/releases/tag/" + tag : "");
            int remoteVcFinal = vc;
            if (currentVersionCode > 0) {
                out.put("isNewer", Net.isNewerRelease(remoteVcFinal, currentVersionCode));
            }
            String apkFileName = d.optString("apkFileName", "");
            out.put("apkUrl", apkUrl);
            out.put("apkFileName", apkFileName.length() > 0 ? apkFileName : "adshield.apk");
            out.put("apkMB", Math.round(apkSize / 1048576.0 * 100) / 100.0);
            out.put("apkSize", apkSize);
            out.put("apkDigest", apkSha256.length() > 0 ? "sha256:" + apkSha256 : "");
            out.put("apkMirrors", new JSONArray()); // v7.9.6plus 语义保持：唯一官方源
            int minSupported = d.optInt("minSupportedVersionCode", 0);
            out.put("minSupportedVersionCode", minSupported);
            if (minSupported > 0 && currentVersionCode > 0 && currentVersionCode < minSupported) {
                out.put("belowMinSupported", true); // 当前版本低于最低支持线（提示用，不阻断）
            }
            out.put("mandatoryUpdate", d.optBoolean("mandatoryUpdate", false));
            return out.toString();
        } catch (Exception e) {
            return fail(ERR_INVALID, "更新清单解析异常");
        }
    }

    /** SHA256 归一化：「sha256:HEX」/「HEX」（大小写不敏感）→ 小写 hex；
     *  空串原样通过（弱校验回退语义保持——Downloader 字节数兑底）；非法 → null。 */
    static String normalizeSha(String s) {
        if (s == null) return "";
        String v = s.trim();
        if (v.length() == 0) return "";
        String low = v.toLowerCase(java.util.Locale.ROOT);
        if (low.startsWith("sha256:")) low = low.substring("sha256:".length());
        if (low.length() != 64) return null;
        for (int i = 0; i < low.length(); i++) {
            char c = low.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return null;
        }
        return low;
    }

    /** 清单正文 → headlines[]（与 parseRelease 同源——ReleaseNotes 四代兼容）。 */
    private static JSONArray headlinesOf(String notes) {
        JSONArray hl = new JSONArray();
        try {
            String[] heads = com.zai.adshield.core.ReleaseNotes.headlines(notes);
            for (String h : heads) hl.put(h);
        } catch (Throwable ignored) {
        }
        return hl;
    }

    /** HTTP 状态码 → 限流类（403/429；静态 CDN 少见但如实分类）。纯函数 JVM 可测。 */
    public static boolean isRateLimitCode(int code) {
        return code == 403 || code == 429;
    }

    /** 失败载荷（{ok:false, error, errorCode}）。 */
    static String fail(String errorCode, String text) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", text);
            o.put("errorCode", errorCode);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"" + text + "\",\"errorCode\":\"" + errorCode + "\"}";
        }
    }
}
