package com.zai.adshield.test;

import com.zai.adshield.UpdateManifest;

import org.json.JSONObject;

/**
 * 静态更新清单（update/latest.json）解析与校验测试（v7.9.8 更新检查架构改造）。
 * ─────────────────────────────────────────────
 * 覆盖（用户指令·清单解析测试全项）：
 *  S1  正常清单（v7.9.7 真实字段形态）→ 全字段映射 + isNewer 权威判定。
 *  S2  缺字段分类：缺 versionName（tag 兜底）/ 缺 versionCode（正文元数据行
 *      兜底 → 名比较兜底）/ 缺 APK URL（ok 但无下载）/ 缺 SHA256（弱校验语义）/
 *      缺 packageName（INVALID）。
 *  S3  错误形态：错误 JSON / 空 JSON / 空串 / null / 数组 JSON。
 *  S4  包名不匹配 → UPDATE_PACKAGE_MISMATCH（拒绝——防错仓/换包）。
 *  S5  版本异常：负数/字符串数字容忍/超大数；versionName 与 versionCode
 *      不一致（versionCode 主判赢）。
 *  S6  旧 schemaVersion（0/缺失）与未来 schemaVersion（>1）与未知新字段。
 *  S7  Draft/Pre-release 清单标记 → 不作正式更新（hasRelease=false 非错误）。
 *  S8  SHA256 归一化（sha256: 前缀/大小写/裸 hex/非法形态）。
 *  S9  APK 地址安全闸：非 HTTPS / 非发布通道域 → INVALID（拒绝）。
 *  S10 minSupportedVersionCode / mandatoryUpdate 透传。
 *  S11 版本比较专项（7.9.7→7.9.8 / 7.9.8→7.9.7 / 同版 / 降级）。
 */
public final class UpdateManifestTest {

    private static final String PKG = "com.zai.adshield";
    private static final String REAL_SHA = "2367a2fbb4d7b49c596e7f930383468b7fc310abbd185a2a462d2e9ddf4cd4c7";
    private static final String REAL_APK_URL =
            "https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.7/adshield-v7.9.7.apk";

    public static void main(String[] args) {
        TestHost.suite("UpdateManifestTest · 静态清单解析与校验（v7.9.8）");
        s1_normal();
        s2_missingFields();
        s3_malformed();
        s4_packageMismatch();
        s5_versionOddities();
        s6_schemaCompat();
        s7_draftPrerelease();
        s8_shaNormalization();
        s9_apkUrlGate();
        s10_passthrough();
        s11_versionCompare();
        TestHost.done();
    }

    /* 正常清单构造器（真实 v7.9.7 字段形态——可按需覆写单项） */
    private static String manifest(String versionName, int versionCode, String tag,
                                   String apkUrl, String sha, String pkg) {
        return "{\"schemaVersion\":1,\"versionName\":\"" + versionName + "\",\"versionCode\":" + versionCode
                + ",\"releaseTag\":\"" + tag + "\",\"releaseName\":\"v" + versionName + "\""
                + ",\"releaseNotes\":\"versionCode: " + versionCode + "\\n1. 功能一\\n2. 功能二\""
                + ",\"publishedAt\":\"2026-11-22T07:55:06Z\""
                + ",\"apkUrl\":\"" + apkUrl + "\",\"apkFileName\":\"adshield-v" + versionName + ".apk\""
                + ",\"apkSha256\":\"" + sha + "\",\"apkSize\":886326"
                + ",\"packageName\":\"" + pkg + "\",\"minSupportedVersionCode\":0"
                + ",\"mandatoryUpdate\":false,\"manifestUpdatedAt\":\"2026-11-22T07:55:06Z\"}";
    }

    private static JSONObject parseOk(String json, int curVc) throws Exception {
        return new JSONObject(UpdateManifest.parse(json, PKG, curVc));
    }

    private static JSONObject parseFail(String json) throws Exception {
        return new JSONObject(UpdateManifest.parse(json, PKG, 79800));
    }

    /* ═══ S1 正常清单 ═══ */
    private static void s1_normal() {
        try {
            JSONObject o = parseOk(manifest("7.9.7", 79700, "v7.9.7", REAL_APK_URL, REAL_SHA, PKG), 79800);
            TestHost.check(o.optBoolean("ok", false), "S1a ok");
            TestHost.check(o.optBoolean("hasRelease", false), "S1b hasRelease");
            TestHost.check("manifest".equals(o.optString("source", "")), "S1c source=manifest");
            TestHost.check("v7.9.7".equals(o.optString("tag", "")), "S1d tag");
            TestHost.check("7.9.7".equals(o.optString("versionName", "")), "S1e versionName");
            TestHost.check(o.optInt("versionCode", 0) == 79700, "S1f versionCode");
            TestHost.check(o.optInt("remoteVersionCode", -1) == 79700, "S1g remoteVersionCode（旧 UI 兼容字段）");
            TestHost.check(REAL_APK_URL.equals(o.optString("apkUrl", "")), "S1h apkUrl");
            TestHost.check("adshield-v7.9.7.apk".equals(o.optString("apkFileName", "")), "S1i apkFileName");
            TestHost.check(o.optLong("apkSize", 0) == 886326L, "S1j apkSize");
            double apkMB = Double.parseDouble(o.optString("apkMB", "0"));
            TestHost.check(Math.abs(apkMB - 0.85) < 0.01, "S1k apkMB 派生");
            TestHost.check(("sha256:" + REAL_SHA).equals(o.optString("apkDigest", "")), "S1l apkDigest 归一 sha256:hex");
            TestHost.check(o.optJSONArray("apkMirrors") != null && o.optJSONArray("apkMirrors").length() == 0,
                    "S1m apkMirrors 空数组（唯一官方源语义）");
            TestHost.check(o.optJSONArray("headlines") != null && o.optJSONArray("headlines").length() == 2,
                    "S1n headlines 预解析（≤5 条带序号功能描述）");
            TestHost.check(o.has("isNewer") && !o.optBoolean("isNewer", true), "S1o 本地 7.9.8 > 清单 7.9.7 → isNewer=false");
            TestHost.check("https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v7.9.7".equals(o.optString("url", "")),
                    "S1p url（Release 页构造）");
        } catch (Exception e) {
            TestHost.check(false, "S1 异常: " + e);
        }
    }

    /* ═══ S2 缺字段分类 ═══ */
    private static void s2_missingFields() {
        try {
            // 缺 versionName → releaseTag 兜底（v 前缀剥离）
            String j1 = "{\"schemaVersion\":1,\"versionCode\":79800,\"releaseTag\":\"v7.9.8\","
                    + "\"apkUrl\":\"" + REAL_APK_URL + "\",\"apkSha256\":\"" + REAL_SHA + "\","
                    + "\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}";
            JSONObject o1 = parseOk(j1, 79700);
            TestHost.check(o1.optBoolean("ok", false) && "7.9.8".equals(o1.optString("versionName", "")),
                    "S2a 缺 versionName → tag 兜底");
            TestHost.check(o1.optBoolean("isNewer", false), "S2b tag 兜底后判新正常");

            // 缺 versionCode → 不判新（v7.10.0 统一规则：绝不从正文元数据行回退定码）
            String j2 = "{\"schemaVersion\":1,\"versionName\":\"7.9.8\",\"releaseTag\":\"v7.9.8\","
                    + "\"releaseNotes\":\"versionCode: 79800\\n1. 功能\",\"apkUrl\":\"" + REAL_APK_URL + "\","
                    + "\"apkSha256\":\"" + REAL_SHA + "\",\"packageName\":\"" + PKG + "\"}";
            JSONObject o2 = parseOk(j2, 79700);
            TestHost.check(o2.optBoolean("ok", false) && o2.optInt("versionCode", -1) == 0,
                    "S2c 缺 versionCode → 0（正文元数据行回退已删——versionCode: 行被无视）");
            TestHost.check(o2.optBoolean("isNewer", true) == false,
                    "S2c2 缺 versionCode → isNewer=false（统一规则：无码不判新）");

            // 缺 versionCode 且正文无元数据 → 不判新（versionName 仅展示，绝不名比较兜底）
            String j3 = "{\"versionName\":\"8.0.0\",\"releaseTag\":\"v8.0.0\","
                    + "\"releaseNotes\":\"无元数据正文\",\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\"}";
            JSONObject o3 = parseOk(j3, 79700);
            TestHost.check(o3.optBoolean("ok", false), "S2d 缺 versionCode 无元数据 → 仍可解析（展示数据保全）");
            TestHost.check(o3.optInt("versionCode", -1) == 0, "S2e versionCode 保持 0");
            TestHost.check(o3.optBoolean("isNewer", true) == false,
                    "S2f 名比较回退已删：versionName 8.0.0 也不判新（versionName 仅展示）");

            // 版本信息全缺 → UPDATE_VERSION_INVALID
            String j4 = "{\"apkUrl\":\"" + REAL_APK_URL + "\",\"packageName\":\"" + PKG + "\"}";
            JSONObject o4 = parseFail(j4);
            TestHost.check(!o4.optBoolean("ok", true)
                    && UpdateManifest.ERR_VERSION.equals(o4.optString("errorCode", "")),
                    "S2g 版本信息全缺 → UPDATE_VERSION_INVALID");

            // 缺 APK URL → ok 但 apkUrl 空（UI「该版本没有 APK 附件」路径）
            JSONObject o5 = parseOk(manifest("7.9.8", 79800, "v7.9.8", "", REAL_SHA, PKG), 79700);
            TestHost.check(o5.optBoolean("ok", false) && "".equals(o5.optString("apkUrl", "x")),
                    "S2h 缺 apkUrl → ok + 空地址（不致命）");

            // 缺 SHA256 → ok + apkDigest 空（Downloader 字节数弱校验兜底——语义保持）
            JSONObject o6 = parseOk(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, "", PKG), 79700);
            TestHost.check(o6.optBoolean("ok", false) && "".equals(o6.optString("apkDigest", "x")),
                    "S2i 缺 SHA256 → ok + 空 digest（弱校验兜底语义保持）");

            // 缺 packageName → UPDATE_INVALID_MANIFEST
            String j7 = "{\"schemaVersion\":1,\"versionName\":\"7.9.8\",\"versionCode\":79800,"
                    + "\"releaseTag\":\"v7.9.8\",\"apkUrl\":\"\",\"releaseNotes\":\"x\"}";
            JSONObject o7 = parseFail(j7);
            TestHost.check(!o7.optBoolean("ok", true)
                    && UpdateManifest.ERR_INVALID.equals(o7.optString("errorCode", "")),
                    "S2j 缺 packageName → UPDATE_INVALID_MANIFEST");
        } catch (Exception e) {
            TestHost.check(false, "S2 异常: " + e);
        }
    }

    /* ═══ S3 错误形态 ═══ */
    private static void s3_malformed() {
        try {
            JSONObject o1 = parseFail("这不是 JSON");
            TestHost.check(!o1.optBoolean("ok", true)
                    && UpdateManifest.ERR_INVALID.equals(o1.optString("errorCode", "")), "S3a 非 JSON → INVALID");
            JSONObject o2 = parseFail("");
            TestHost.check(!o2.optBoolean("ok", true)
                    && UpdateManifest.ERR_INVALID.equals(o2.optString("errorCode", "")), "S3b 空串 → INVALID");
            JSONObject o3 = parseFail("   ");
            TestHost.check(!o3.optBoolean("ok", true), "S3c 空白 → INVALID");
            JSONObject o4 = parseFail(null);
            TestHost.check(!o4.optBoolean("ok", true), "S3d null → INVALID");
            JSONObject o5 = parseFail("[1,2,3]");
            TestHost.check(!o5.optBoolean("ok", true)
                    && UpdateManifest.ERR_INVALID.equals(o5.optString("errorCode", "")), "S3e 数组 JSON → INVALID");
            JSONObject o6 = parseFail("{\"a\":1}"); // 对象但无任何有效字段
            TestHost.check(!o6.optBoolean("ok", true), "S3f 无有效字段对象 → 拒绝");
        } catch (Exception e) {
            TestHost.check(false, "S3 异常: " + e);
        }
    }

    /* ═══ S4 包名不匹配 ═══ */
    private static void s4_packageMismatch() {
        try {
            JSONObject o = parseFail(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, REAL_SHA, "com.evil.clone"));
            TestHost.check(!o.optBoolean("ok", true)
                    && UpdateManifest.ERR_PACKAGE.equals(o.optString("errorCode", "")),
                    "S4a 包名不匹配 → UPDATE_PACKAGE_MISMATCH（拒绝）");
            TestHost.check(o.optString("error", "").contains("com.evil.clone"), "S4b 错误文本含实际包名");
        } catch (Exception e) {
            TestHost.check(false, "S4 异常: " + e);
        }
    }

    /* ═══ S5 版本号异常 ═══ */
    private static void s5_versionOddities() {
        try {
            // 负数 versionCode → 0 → 不判新（统一规则：无效输入不回退）
            JSONObject o1 = parseFail("{\"versionName\":\"7.9.8\",\"versionCode\":-5,\"releaseTag\":\"v7.9.8\","
                    + "\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}");
            TestHost.check(o1.optBoolean("ok", false) && o1.optInt("versionCode", -1) == 0,
                    "S5a 负 versionCode → 0（不判新——回退链已删）");
            TestHost.check(o1.optBoolean("isNewer", true) == false,
                    "S5a2 负 versionCode → isNewer=false");
            // 字符串数字容忍（org.json optInt 数字字符串可转）
            JSONObject o2 = parseFail("{\"versionName\":\"7.9.8\",\"versionCode\":\"79800\",\"releaseTag\":\"v7.9.8\","
                    + "\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}");
            TestHost.check(o2.optBoolean("ok", false) && o2.optInt("versionCode", 0) == 79800,
                    "S5b 字符串数字 versionCode 容忍");
            // 非数字 versionCode → 0 → 不判新
            JSONObject o3 = parseFail("{\"versionName\":\"7.9.8\",\"versionCode\":\"abc\",\"releaseTag\":\"v7.9.8\","
                    + "\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}");
            TestHost.check(o3.optBoolean("ok", false) && o3.optInt("versionCode", -1) == 0,
                    "S5c 非数字 versionCode → 0（不判新——回退链已删）");
            // 超大 versionCode（字符串形态超 int）→ 0 → 不判新
            JSONObject o4 = parseFail("{\"versionName\":\"7.9.8\",\"versionCode\":\"99999999999\",\"releaseTag\":\"v7.9.8\","
                    + "\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}");
            TestHost.check(o4.optBoolean("ok", false) && o4.optInt("versionCode", -1) == 0,
                    "S5d 超大 versionCode → 0 不崩（不判新）");
        } catch (Exception e) {
            TestHost.check(false, "S5 异常: " + e);
        }
    }

    /* ═══ S6 schema 兼容 ═══ */
    private static void s6_schemaCompat() {
        try {
            // schemaVersion 缺失（旧清单）→ 宽容解析
            String j1 = "{\"versionName\":\"7.9.8\",\"versionCode\":79800,\"releaseTag\":\"v7.9.8\","
                    + "\"apkUrl\":\"" + REAL_APK_URL + "\",\"apkSha256\":\"" + REAL_SHA + "\","
                    + "\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}";
            JSONObject o1 = parseOk(j1, 79700);
            TestHost.check(o1.optBoolean("ok", false), "S6a schemaVersion 缺失 → 宽容");
            // schemaVersion 0 → 宽容
            String j2 = "{\"schemaVersion\":0,\"versionName\":\"7.9.8\",\"versionCode\":79800,"
                    + "\"releaseTag\":\"v7.9.8\",\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}";
            JSONObject o2 = parseOk(j2, 79700);
            TestHost.check(o2.optBoolean("ok", false), "S6b schemaVersion 0 → 宽容");
            // 未来 schemaVersion 2 + 未知新字段 → 仍解析已知字段（向前兼容）
            String j3 = "{\"schemaVersion\":2,\"versionName\":\"7.9.8\",\"versionCode\":79800,"
                    + "\"releaseTag\":\"v7.9.8\",\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\","
                    + "\"releaseNotes\":\"x\",\"futureField\":{\"nested\":true},\"anotherNew\":123}";
            JSONObject o3 = parseOk(j3, 79700);
            TestHost.check(o3.optBoolean("ok", false) && o3.optInt("schemaVersion", 0) == 2,
                    "S6c 未来 schema + 未知字段 → 已知字段照常解析");
        } catch (Exception e) {
            TestHost.check(false, "S6 异常: " + e);
        }
    }

    /* ═══ S7 Draft/Pre-release 清单标记 ═══ */
    private static void s7_draftPrerelease() {
        try {
            JSONObject o1 = parseFail("{\"draft\":true,\"versionName\":\"7.9.8\",\"versionCode\":79800,"
                    + "\"releaseTag\":\"v7.9.8\",\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}");
            TestHost.check(o1.optBoolean("ok", false) && !o1.optBoolean("hasRelease", true),
                    "S7a draft 标记 → 不作正式更新（hasRelease=false 非错误）");
            JSONObject o2 = parseFail("{\"prerelease\":true,\"versionName\":\"7.9.8\",\"versionCode\":79800,"
                    + "\"releaseTag\":\"v7.9.8\",\"apkUrl\":\"\",\"packageName\":\"" + PKG + "\",\"releaseNotes\":\"x\"}");
            TestHost.check(o2.optBoolean("ok", false) && !o2.optBoolean("hasRelease", true),
                    "S7b prerelease 标记 → 不作正式更新");
        } catch (Exception e) {
            TestHost.check(false, "S7 异常: " + e);
        }
    }

    /* ═══ S8 SHA256 归一化 ═══ */
    private static void s8_shaNormalization() {
        try {
            // 大写裸 hex → 归一小写 + sha256: 前缀
            JSONObject o1 = parseOk(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, REAL_SHA.toUpperCase(), PKG), 79700);
            TestHost.check(("sha256:" + REAL_SHA).equals(o1.optString("apkDigest", "")), "S8a 大写裸 hex → 归一");
            // sha256: 前缀 + 大写 → 归一
            JSONObject o2 = parseOk(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, "sha256:" + REAL_SHA.toUpperCase(), PKG), 79700);
            TestHost.check(("sha256:" + REAL_SHA).equals(o2.optString("apkDigest", "")), "S8b sha256: 前缀容忍");
            // 长度错误 → INVALID
            JSONObject o3 = parseFail(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, "abc123", PKG));
            TestHost.check(!o3.optBoolean("ok", true)
                    && UpdateManifest.ERR_INVALID.equals(o3.optString("errorCode", "")), "S8c 短 hex → INVALID");
            // 非 hex 字符 → INVALID
            String bad = REAL_SHA.substring(0, 63) + "g";
            JSONObject o4 = parseFail(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, bad, PKG));
            TestHost.check(!o4.optBoolean("ok", true)
                    && UpdateManifest.ERR_INVALID.equals(o4.optString("errorCode", "")), "S8d 非 hex → INVALID");
            // 65 位 → INVALID
            String long64 = REAL_SHA + "a";
            JSONObject o5 = parseFail(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, long64, PKG));
            TestHost.check(!o5.optBoolean("ok", true), "S8e 65 位 → INVALID");
        } catch (Exception e) {
            TestHost.check(false, "S8 异常: " + e);
        }
    }

    /* ═══ S9 APK 地址安全闸 ═══ */
    private static void s9_apkUrlGate() {
        try {
            // http 明文 → 拒绝
            JSONObject o1 = parseFail(manifest("7.9.8", 79800, "v7.9.8",
                    "http://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.8/a.apk", REAL_SHA, PKG));
            TestHost.check(!o1.optBoolean("ok", true)
                    && UpdateManifest.ERR_INVALID.equals(o1.optString("errorCode", "")), "S9a http 明文 → INVALID");
            // 域名伪装（github.com.evil.com）→ 拒绝
            JSONObject o2 = parseFail(manifest("7.9.8", 79800, "v7.9.8",
                    "https://github.com.evil.com/x.apk", REAL_SHA, PKG));
            TestHost.check(!o2.optBoolean("ok", true), "S9b 域名伪装 → INVALID（Downloader.hostAllowed 同闸）");
            // 未知域 → 拒绝
            JSONObject o3 = parseFail(manifest("7.9.8", 79800, "v7.9.8",
                    "https://evil.example.com/x.apk", REAL_SHA, PKG));
            TestHost.check(!o3.optBoolean("ok", true), "S9c 未知域 → INVALID");
            // 官方 GitHub Release 下载域 → 通过
            JSONObject o4 = parseOk(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, REAL_SHA, PKG), 79700);
            TestHost.check(o4.optBoolean("ok", false), "S9d github.com Release 域 → 通过");
            // GitHub 资产 CDN 域（release-assets.githubusercontent.com）→ 通过
            JSONObject o5 = parseOk(manifest("7.9.8", 79800, "v7.9.8",
                    "https://release-assets.githubusercontent.com/x/y.apk", REAL_SHA, PKG), 79700);
            TestHost.check(o5.optBoolean("ok", false), "S9e release-assets CDN 域 → 通过");
        } catch (Exception e) {
            TestHost.check(false, "S9 异常: " + e);
        }
    }

    /* ═══ S10 透传字段 ═══ */
    private static void s10_passthrough() {
        try {
            String j = "{\"schemaVersion\":1,\"versionName\":\"7.9.8\",\"versionCode\":79800,"
                    + "\"releaseTag\":\"v7.9.8\",\"apkUrl\":\"" + REAL_APK_URL + "\",\"apkSha256\":\"" + REAL_SHA + "\","
                    + "\"packageName\":\"" + PKG + "\",\"minSupportedVersionCode\":79800,"
                    + "\"mandatoryUpdate\":true,\"releaseNotes\":\"x\",\"publishedAt\":\"2026-11-23T00:00:00Z\","
                    + "\"manifestUpdatedAt\":\"2026-11-23T01:00:00Z\"}";
            JSONObject o = parseOk(j, 79700);
            TestHost.check(o.optInt("minSupportedVersionCode", 0) == 79800, "S10a minSupportedVersionCode 透传");
            TestHost.check(o.optBoolean("mandatoryUpdate", false), "S10b mandatoryUpdate 透传");
            TestHost.check(o.optBoolean("belowMinSupported", false), "S10c 本地低于最低支持线 → belowMinSupported 标记");
            JSONObject o2 = parseOk(j, 79800);
            TestHost.check(!o2.optBoolean("belowMinSupported", false), "S10d 高于最低支持线 → 无标记");
            TestHost.check("2026-11-23T01:00:00Z".equals(o.optString("manifestUpdatedAt", "")),
                    "S10e manifestUpdatedAt 透传（数据时间戳）");
        } catch (Exception e) {
            TestHost.check(false, "S10 异常: " + e);
        }
    }

    /* ═══ S11 版本比较专项 ═══ */
    private static void s11_versionCompare() {
        try {
            // 7.9.7 → 7.9.8 升级
            JSONObject up = parseOk(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, REAL_SHA, PKG), 79700);
            TestHost.check(up.optBoolean("isNewer", false), "S11a 7.9.7 → 7.9.8 判新");
            // 7.9.8 → 7.9.7 不降级
            JSONObject down = parseOk(manifest("7.9.7", 79700, "v7.9.7", REAL_APK_URL, REAL_SHA, PKG), 79800);
            TestHost.check(!down.optBoolean("isNewer", true), "S11b 7.9.8 → 7.9.7 不提示（绝不降级）");
            // 版本相同
            JSONObject same = parseOk(manifest("7.9.8", 79800, "v7.9.8", REAL_APK_URL, REAL_SHA, PKG), 79800);
            TestHost.check(!same.optBoolean("isNewer", true), "S11c 同版本不提示");
            // versionName 与 versionCode 不一致：versionCode 主判赢
            JSONObject mixed1 = parseOk(manifest("7.9.7", 79900, "v7.9.7", REAL_APK_URL, REAL_SHA, PKG), 79800);
            TestHost.check(mixed1.optBoolean("isNewer", false),
                    "S11d versionCode 79900>79800 判新（versionName 7.9.7 异常不否决——主判权威）");
            JSONObject mixed2 = parseOk(manifest("8.0.0", 79700, "v8.0.0", REAL_APK_URL, REAL_SHA, PKG), 79800);
            TestHost.check(!mixed2.optBoolean("isNewer", true),
                    "S11e versionName 8.0.0 但 versionCode 79700<79800 → 不提示（陈旧清单防降级）");
            // versionCode 高于当前但 versionName 异常形态（plus 系——统一规则：
            // versionName 形态对判定零影响，唯一输入是 versionCode）
            JSONObject plus = parseOk(manifest("7.9.6plus", 82400, "v7.9.6plus", REAL_APK_URL, REAL_SHA, PKG), 79800);
            TestHost.check(plus.optBoolean("isNewer", false), "S11f plus 命名 + versionCode 主判正常（无任何特殊路径）");
        } catch (Exception e) {
            TestHost.check(false, "S11 异常: " + e);
        }
    }
}
