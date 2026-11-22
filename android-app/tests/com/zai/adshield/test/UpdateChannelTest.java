package com.zai.adshield.test;

import com.zai.adshield.Net;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 更新检查通道测试（v7.9.6plus 单通道版——替换 v8.2.0 双通道版）。
 * ─────────────────────────────────────────────
 * 覆盖（用户指令·更新功能测试 1/2/3/8/9 项的纯函数面）：
 *  S1 versionCodeOfNotes：Release 正文 versionCode 元数据行解析
 *     （正常/带尾注/无元数据/非数字/多行取首个/空/null 安全）。
 *  S2 isNewerRelease versionCode 主判（核心回归——"7.9.6plus"(82300) vs
 *     "8.2.2"(82200)：versionName 分段比较必判旧（7<8），versionCode 主判
 *     正确判新）；等于不提示重复；小于绝不提示降级；元数据缺失回退
 *     versionName 三段比较。
 *  S3 parseRelease：完整 Release JSON（正文含 versionCode 行）→
 *     remoteVersionCode + isNewer 权威判定；asset digest/size 透传。
 *  S4 mirrorsOf：ghproxy 类代理移除后零备用源（唯一官方源 GitHub）。
 *  S5 cmpVersion 三段数值比较保持（"8.10.0" > "8.9.0"）。
 *  S6 rateLimited：403/429 限流，404/3xx 不是。
 *  S7 错误四分类码在场（WORKER_UNAVAILABLE 已随双通道移除）。
 */
public final class UpdateChannelTest {

    public static void main(String[] args) {
        TestHost.suite("UpdateChannelTest · v7.9.6plus 单通道 + versionCode 主判");
        s1_versionCodeOfNotes();
        s2_isNewerRelease();
        s3_parseRelease();
        s4_mirrorsRemoved();
        s5_cmpVersion();
        s6_rateLimited();
        s7_errorCodes();
        TestHost.done();
    }

    /* ═══ S1 versionCodeOfNotes ═══ */
    private static void s1_versionCodeOfNotes() {
        TestHost.check(Net.versionCodeOfNotes("versionCode: 82300") == 82300, "S1a 标准行");
        TestHost.check(Net.versionCodeOfNotes("versionCode: 82300（versionCode 主判依据）") == 82300,
                "S1b 行尾注释容忍");
        TestHost.check(Net.versionCodeOfNotes("versionCode: 82300 \n正文") == 82300, "S1c 行尾空格");
        TestHost.check(Net.versionCodeOfNotes("更新说明\nversionCode: 79700\n更多") == 79700,
                "S1d 多行取首个 versionCode 行");
        TestHost.check(Net.versionCodeOfNotes("没有任何元数据的正文") == 0, "S1e 无元数据 → 0（回退名比较）");
        TestHost.check(Net.versionCodeOfNotes("versionCode: abc") == 0, "S1f 非数字 → 0");
        TestHost.check(Net.versionCodeOfNotes("versionCode: ") == 0, "S1g 空值 → 0");
        TestHost.check(Net.versionCodeOfNotes("versionCode: 99999999999") == 0, "S1h 超 int → 0（防溢出）");
        TestHost.check(Net.versionCodeOfNotes("versionCode: 0") == 0, "S1i 零值 → 0（无效）");
        TestHost.check(Net.versionCodeOfNotes("") == 0, "S1j 空串安全");
        TestHost.check(Net.versionCodeOfNotes(null) == 0, "S1k null 安全");
        TestHost.check(Net.versionCodeOfNotes("xversionCode: 82300") == 0, "S1l 前缀不匹配（非行首）");
    }

    /* ═══ S2 isNewerRelease versionCode 主判 ═══ */
    private static void s2_isNewerRelease() {
        // 核心回归：v7.9.6plus(82300) vs v8.2.2(82200)——versionName 比较会误判旧
        TestHost.check(Net.isNewerRelease(82300, "7.9.6plus", 82200, "8.2.2"),
                "S2a plus 命名 versionCode 主判判新（versionName 分段必判旧——主判兜底）");
        TestHost.check(Net.isNewerRelease(82200, "8.2.2", 82200, "8.2.2") == false,
                "S2b 相等不提示重复更新");
        TestHost.check(Net.isNewerRelease(82100, "8.2.1", 82200, "8.2.2") == false,
                "S2c 远端更低绝不提示降级");
        TestHost.check(Net.isNewerRelease(82300, "v7.9.6plus", 82300, "7.9.6plus") == false,
                "S2d 已装最新版不提示");
        TestHost.check(Net.isNewerRelease(82400, "8.2.3", 82300, "7.9.6plus"),
                "S2e 远端更高正常判新");
        // 元数据缺失（remoteVc=0）→ 回退 versionName 三段比较
        TestHost.check(Net.isNewerRelease(0, "8.5.0", 82300, "7.9.6plus"),
                "S2f 回退名比较：8.5.0 > 7.9.6plus（[8,5,0]>[7,9,6]）");
        // 【plus 后缀段解析修复回归】segNum("6plus")=6："7.9.6plus"≈[7,9,6]
        // 与 "7.9.6" 同族 → 比较为 0 → 不提示（修复前 "6plus"→0 被当作
        // [7,9,0]，远端 v7.9.6 被误判为新版弹更新页——浏览器实测抓到）
        TestHost.check(Net.isNewerRelease(0, "7.9.6", 82300, "7.9.6plus") == false,
                "S2g plus 同族不提示：segNum(6plus)=6 → 7.9.6 == 7.9.6plus（修复回归）");
        // 本地 versionCode 取不到（0）→ 回退名比较
        TestHost.check(Net.isNewerRelease(82300, "9.0.0", 0, "8.2.2"),
                "S2h 本地 VC 缺失回退名比较：9.0.0 > 8.2.2");
        TestHost.check(Net.isNewerRelease(82300, "7.0.0", 0, "8.2.2") == false,
                "S2i 本地 VC 缺失回退名比较：7.0.0 < 8.2.2 不提示");
    }

    /* ═══ S3 parseRelease ═══ */
    private static void s3_parseRelease() {
        try {
            StringBuilder assets = new StringBuilder();
            assets.append("[{\"name\":\"adshield-v7.9.6plus.apk\",")
                    .append("\"browser_download_url\":\"https://github.com/xiaobaiba999/Skip-Ad/releases/download/v7.9.6plus/adshield-v7.9.6plus.apk\",")
                    .append("\"size\":886326,\"digest\":\"sha256:e058cbd5b6fb32904579ee0c90cc03a5d7f45de76b2556fe44df4e3bf3b1867c\"}]");
            JSONObject rel = new JSONObject();
            rel.put("tag_name", "v7.9.6plus");
            rel.put("name", "v7.9.6plus");
            rel.put("body", "versionCode: 82300\n1. 启动时新版本检测\n2. 全屏更新页\n3. 断点续传");
            rel.put("published_at", "2026-11-23T00:00:00Z");
            rel.put("html_url", "https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v7.9.6plus");
            rel.put("assets", new JSONArray(assets.toString()));

            // 本地 82200（v8.2.2）→ 判新（versionCode 主判）
            String out1 = Net.parseRelease(rel.toString(), "8.2.2", 82200);
            JSONObject o1 = new JSONObject(out1);
            TestHost.check(o1.optBoolean("ok", false), "S3a 载荷 ok");
            TestHost.check("v7.9.6plus".equals(o1.optString("tag", "")), "S3b tag");
            TestHost.check(o1.optInt("remoteVersionCode", 0) == 82300, "S3c 正文元数据解析");
            TestHost.check(o1.optBoolean("isNewer", false), "S3d 本地 82200 → isNewer=true（升级路径）");
            TestHost.check("sha256:e058cbd5b6fb32904579ee0c90cc03a5d7f45de76b2556fe44df4e3bf3b1867c"
                    .equals(o1.optString("apkDigest", "")), "S3e digest 透传（安装闸期望值）");
            TestHost.check(o1.optLong("apkSize", 0) == 886326L, "S3f size 透传");
            TestHost.check(o1.optString("apkUrl", "").endsWith(".apk"), "S3g apkUrl");

            // 本地 82300（同版）→ 不提示
            JSONObject o2 = new JSONObject(Net.parseRelease(rel.toString(), "7.9.6plus", 82300));
            TestHost.check(!o2.optBoolean("isNewer", true), "S3h 同版本不提示重复");

            // 本地 82400（更高）→ 绝不提示降级
            JSONObject o3 = new JSONObject(Net.parseRelease(rel.toString(), "8.3.0", 82400));
            TestHost.check(!o3.optBoolean("isNewer", true), "S3i 本地更高不提示降级");

            // 无元数据正文 → remoteVersionCode=0 + 回退名比较（segNum 修复后
            // "7.9.6plus"→[7,9,6] 与 "7.9.6" 同族 → 不提示）
            JSONObject rel2 = new JSONObject(rel.toString());
            rel2.put("body", "1. 手写正文没有元数据行");
            JSONObject o4 = new JSONObject(Net.parseRelease(rel2.toString(), "7.9.6", 0));
            TestHost.check(o4.optInt("remoteVersionCode", -1) == 0, "S3j 无元数据 → 0");
            TestHost.check(!o4.optBoolean("isNewer", true),
                    "S3k plus 同族不提示：7.9.6plus≈[7,9,6] 与 7.9.6 同族（segNum 修复回归）");
        } catch (Exception e) {
            TestHost.check(false, "S3 异常: " + e);
        }
    }

    /* ═══ S4 mirrorsOf：ghproxy 代理移除 ═══ */
    private static void s4_mirrorsRemoved() {
        JSONArray m1 = Net.mirrorsOf("https://github.com/x/y.apk");
        TestHost.check(m1.length() == 0, "S4a ghproxy 类代理移除 → 零备用源");
        TestHost.check(Net.mirrorsOf("").length() == 0, "S4b 空 apkUrl 安全");
        TestHost.check(Net.mirrorsOf(null).length() == 0, "S4c null 安全");
    }

    /* ═══ S5 cmpVersion 三段数值比较保持 ═══ */
    private static void s5_cmpVersion() {
        TestHost.check(Net.cmpVersion("8.10.0", "8.9.0") > 0, "S5a 数值段非字典序");
        TestHost.check(Net.cmpVersion("v7.9.6", "7.9.6") == 0, "S5b v 前缀容忍");
        TestHost.check(Net.cmpVersion("7.9.6", "7.9.6.1") == 0, "S5c 缺段补 0（三段封顶）");
        TestHost.check(Net.cmpVersion("8.2.2", "7.9.6plus") > 0,
                "S5d 名比较视角 8.2.2 > 7.9.6plus（versionCode 主判存在的理由）");
        TestHost.check(Net.cmpVersion("7.9.6plus", "7.9.6") == 0,
                "S5e segNum 修复回归：7.9.6plus 与 7.9.6 同族（6plus→6）");
        TestHost.check(Net.cmpVersion("7.9.6plus2", "7.9.6") == 0,
                "S5f 任意字母后缀同族（6plus2→6）");
    }

    /* ═══ S6 rateLimited ═══ */
    private static void s6_rateLimited() {
        TestHost.check(Net.rateLimited(403), "S6a 403 匿名配额耗尽 = 限流");
        TestHost.check(Net.rateLimited(429), "S6b 429 次级限流 = 限流");
        TestHost.check(!Net.rateLimited(404), "S6c 404 不算限流（无正式 Release 正常态）");
        TestHost.check(!Net.rateLimited(302), "S6d 3xx 不算限流（劫持另类处理）");
        TestHost.check(!Net.rateLimited(500), "S6e 5xx 不算限流");
    }

    /* ═══ S7 错误四分类 ═══ */
    private static void s7_errorCodes() {
        TestHost.check("GITHUB_RATE_LIMITED".equals(Net.ERR_GITHUB_RATE_LIMITED), "S7a 限流码");
        TestHost.check("NETWORK_ERROR".equals(Net.ERR_NETWORK), "S7b 网络码");
        TestHost.check("PARSE_ERROR".equals(Net.ERR_PARSE), "S7c 解析码");
        TestHost.check("GITHUB_ERROR".equals(Net.ERR_GITHUB), "S7d GitHub 码");
        // WORKER_UNAVAILABLE 已随 v8.2.0 双通道移除——反射验证字段不存在
        boolean workerCodeGone = true;
        try {
            Net.class.getField("ERR_WORKER_UNAVAILABLE");
            workerCodeGone = false;
        } catch (NoSuchFieldException expected) {
        }
        TestHost.check(workerCodeGone, "S7e WORKER_UNAVAILABLE 已移除（单通道回归）");
        boolean updateRelayGone = true;
        try {
            Net.class.getField("UPDATE_RELAY_URL");
            updateRelayGone = false;
        } catch (NoSuchFieldException expected) {
        }
        TestHost.check(updateRelayGone, "S7f UPDATE_RELAY_URL 已移除");
        boolean deriveGone = true;
        try {
            Net.class.getMethod("deriveUpdateRelayUrl", String.class);
            deriveGone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(deriveGone, "S7g deriveUpdateRelayUrl 已移除");
    }
}
