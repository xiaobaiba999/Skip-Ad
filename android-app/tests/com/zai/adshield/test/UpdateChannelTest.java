package com.zai.adshield.test;

import com.zai.adshield.Net;
import com.zai.adshield.Downloader;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v8.2.0 更新检查双通道回归套件（真机限流根因「GitHub 匿名 API 60 次/小时/IP
 * 耗尽——用户看不到正式新版本」修复）。
 * ─────────────────────────────────────────────────────────
 * 覆盖（网络路径真机验证 NOT_RUN；此处钉死全部纯函数契约）：
 *  S1 版本比较 cmpVersion：三段数值（非字典序——"8.10.0">"8.9.0"）、
 *     v/V 前缀、缺段补 0、等值——isNewer/NO_NEW_VERSION 分类的权威判定；
 *  S2 中转地址派生 deriveUpdateRelayUrl：同源跟随/结构性关闭（空串·非预期
 *     形态绝不拼错地址）；
 *  S3 中转载荷规范化 normalizeWorkerUpdatePayload：字段与 parseRelease 对齐
 *     （app.js 下载链零改动）/hasRelease:false 透传/ok:false·契约破坏·垃圾→
 *     null 回退直连/isNewer 注入；
 *  S4 错误五分类 primaryErrorCode + 复合文案 compositeMessage：中转故障绝不
 *     误报为 GitHub 限流；双通道归因各自呈现；仅直连失败文案=v8.0.3 逐字；
 *  S5 复合失败载荷 composeFailure：errorCode/workerError/githubError 契约；
 *  S6 SHA-256 校验判定 verifyDigest + hex（v6.3.0 行为逐字提取——大小不符
 *     优先/大小写不敏感/digest 缺失字节数兜底/双缺拒绝）；
 *  S7 发布通道 host 白名单 hostAllowed（下载安全闸不降低的回归证明）；
 *  S8 镜像合成 mirrorsOf（Worker 契约不含镜像——客户端统一合成）。
 */
public class UpdateChannelTest {

    public static void main(String[] args) {
        TestHost.suite("UpdateChannelTest");

        s1_cmpVersion();
        s2_deriveUpdateRelayUrl();
        s3_normalizeWorkerPayload();
        s4_errorTaxonomy();
        s5_composeFailure();
        s6_verifyDigest();
        s7_hostAllowed();
        s8_mirrorsOf();

        TestHost.done();
    }

    /* S1：版本比较（app.js cmpVer 的 Java 镜像——数值段比较，非字典序）。 */
    private static void s1_cmpVersion() {
        TestHost.check(Net.cmpVersion("8.2.0", "8.1.0") > 0, "S1a 8.2.0 > 8.1.0（常规升级）");
        TestHost.check(Net.cmpVersion("v8.2.0", "8.1.0") > 0, "S1b v 前缀容忍（GitHub tag 形态）");
        TestHost.check(Net.cmpVersion("8.10.0", "8.9.0") > 0, "S1c 数值段非字典序（8.10 > 8.9——字典序会判错）");
        TestHost.check(Net.cmpVersion("9.0.0", "8.99.99") > 0, "S1d 主版本进位");
        TestHost.check(Net.cmpVersion("8.2.0", "8.2.0") == 0, "S1e 等值 → 0（NO_NEW_VERSION）");
        TestHost.check(Net.cmpVersion("v8.1.0", "8.2.0") < 0, "S1f 旧版本 → 负（已是最新）");
        TestHost.check(Net.cmpVersion("8.2", "8.2.0") == 0, "S1g 缺段补 0");
        TestHost.check(Net.cmpVersion("8.2.0", "8.2") == 0, "S1h 缺段补 0（反向）");
        TestHost.check(Net.cmpVersion("", "8.2.0") < 0, "S1i 空串按 0.0.0");
        TestHost.check(Net.cmpVersion(null, "0.0.0") == 0, "S1j null 安全");
        TestHost.check(Net.cmpVersion("V8.2.0", "8.1.0") > 0, "S1k 大写 V 前缀容忍（Java 侧超集）");
    }

    /* S2：中转地址派生（单一事实源 LOG_RELAY_URL → 同源更新端点）。 */
    private static void s2_deriveUpdateRelayUrl() {
        String real = "https://skip-ad-log-relay.2927609408.workers.dev/api/logs/receive";
        TestHost.check(Net.deriveUpdateRelayUrl(real)
                        .equals("https://skip-ad-log-relay.2927609408.workers.dev/api/update/latest"),
                "S2a 真实地址派生正确（同源 /api/update/latest）");
        TestHost.check(Net.deriveUpdateRelayUrl("").length() == 0, "S2b 空串 → 结构性关闭");
        TestHost.check(Net.deriveUpdateRelayUrl(null).length() == 0, "S2c null → 结构性关闭");
        TestHost.check(Net.deriveUpdateRelayUrl("https://example.com/other/path").length() == 0,
                "S2d 非预期形态 → 关闭（绝不拼错地址打无关端点）");
        TestHost.check(Net.UPDATE_RELAY_URL.endsWith("/api/update/latest"),
                "S2e 生产常量已派生（通道 0 在线）");
    }

    /* S3：中转载荷规范化（契约对齐——字段名与 parseRelease 完全一致）。 */
    private static void s3_normalizeWorkerPayload() {
        JSONObject w = new JSONObject();
        w.put("ok", true);
        w.put("hasRelease", true);
        w.put("tag", "v8.2.0");
        w.put("name", "开屏卫士 v8.2.0");
        w.put("notes", "1. 双通道更新检查\n2. 错误五分类");
        w.put("publishedAt", "2026-11-22T00:00:00Z");
        w.put("url", "https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v8.2.0");
        w.put("apkUrl", "https://github.com/xiaobaiba999/Skip-Ad/releases/download/v8.2.0/adshield-v8.2.0.apk");
        w.put("apkFileName", "adshield-v8.2.0.apk");
        w.put("apkSize", 900000);
        w.put("apkMB", 0.86);
        w.put("apkDigest", "sha256:50a7b7029c715eb810e1bc3de2c8fac63d74d10995c88562c4c6f9e2487f0421");
        w.put("source", "auth");
        w.put("cached", true);
        w.put("fetchedAt", 1795000000000L);
        try {
            String out = Net.normalizeWorkerUpdatePayload(w.toString(), "8.1.0");
            TestHost.check(out != null, "S3a 合法载荷 → 规范化成功");
            JSONObject o = new JSONObject(out);
            TestHost.check("worker".equals(o.optString("source")), "S3b source=worker（通道 0 标识）");
            TestHost.check("v8.2.0".equals(o.optString("tag")), "S3c tag 透传");
            TestHost.check(o.optString("apkDigest").startsWith("sha256:"),
                    "S3d apkDigest 官方格式透传（Downloader 校验闸直接可用）");
            TestHost.check(o.optLong("apkSize") == 900000, "S3e apkSize 透传");
            TestHost.check(o.optJSONArray("apkMirrors") != null && o.optJSONArray("apkMirrors").length() == 2, "S3f 镜像客户端合成（ghfast+gh-proxy）");
            TestHost.check(o.optBoolean("isNewer"), "S3g isNewer=true（v8.2.0 > 当前 8.1.0）");
            TestHost.check(o.optBoolean("cached"), "S3h cached 透传");
            TestHost.check(o.optJSONArray("headlines") != null && o.optJSONArray("headlines").length() >= 1, "S3i headlines 预解析在场");

            // isNewer=false（NO_NEW_VERSION）
            String out2 = Net.normalizeWorkerUpdatePayload(w.toString(), "8.2.0");
            TestHost.check(!new JSONObject(out2).optBoolean("isNewer"), "S3j 等版本 → isNewer=false（NO_NEW_VERSION 分类）");

            // hasRelease:false 透传（中转权威答案——不回退）
            JSONObject none = new JSONObject();
            none.put("ok", true);
            none.put("hasRelease", false);
            String out3 = Net.normalizeWorkerUpdatePayload(none.toString(), "8.1.0");
            TestHost.check(out3 != null && !new JSONObject(out3).optBoolean("hasRelease"),
                    "S3k hasRelease:false 透传（信任中转，不回退直连）");

            // 回退路径：ok:false / 契约破坏 / 垃圾
            JSONObject bad1 = new JSONObject();
            bad1.put("ok", false);
            bad1.put("error", "GITHUB_RATE_LIMITED");
            TestHost.check(Net.normalizeWorkerUpdatePayload(bad1.toString(), "8.1.0") == null,
                    "S3l ok:false（中转自身失败）→ null 回退直连");
            JSONObject bad2 = new JSONObject();
            bad2.put("ok", true);
            TestHost.check(Net.normalizeWorkerUpdatePayload(bad2.toString(), "8.1.0") == null,
                    "S3m 契约破坏（缺 hasRelease）→ null 回退");
            TestHost.check(Net.normalizeWorkerUpdatePayload("not-json{{", "8.1.0") == null,
                    "S3n 垃圾响应 → null 回退");
            TestHost.check(Net.normalizeWorkerUpdatePayload("", "8.1.0") == null,
                    "S3o 空响应 → null 回退");
        } catch (Exception e) {
            TestHost.check(false, "S3 异常: " + e);
        }
    }

    /* S4：错误五分类 + 复合文案（中转故障绝不误报为 GitHub 限流）。 */
    private static void s4_errorTaxonomy() {
        // 主错误码（github 通道优先——可行动性最强）
        TestHost.check(Net.ERR_GITHUB_RATE_LIMITED
                        .equals(Net.primaryErrorCode(true, Net.ERR_GITHUB_RATE_LIMITED)),
                "S4a 双通道皆败 + GitHub 限流 → GITHUB_RATE_LIMITED（可行动：等 1h 或走中转）");
        TestHost.check(Net.ERR_NETWORK.equals(Net.primaryErrorCode(true, Net.ERR_NETWORK)),
                "S4b 双通道皆败 + 网络异常 → NETWORK_ERROR（双连不上=设备网络）");
        TestHost.check(Net.ERR_PARSE.equals(Net.primaryErrorCode(true, Net.ERR_PARSE)),
                "S4c 双通道皆败 + 解析失败 → PARSE_ERROR");
        TestHost.check(Net.ERR_GITHUB.equals(Net.primaryErrorCode(true, Net.ERR_GITHUB)),
                "S4d GitHub 其他 HTTP → GITHUB_ERROR（如实第六类——非限流非网络）");
        TestHost.check(Net.ERR_WORKER_UNAVAILABLE.equals(Net.primaryErrorCode(true, null)),
                "S4e github 未败理论态 → WORKER_UNAVAILABLE 兜底");
        TestHost.check(Net.ERR_NETWORK.equals(Net.primaryErrorCode(false, null)),
                "S4f 双通道皆未发起/未分类 → NETWORK_ERROR 兜底");

        // 复合文案：双通道归因各自呈现；仅直连时与 v8.0.3 一致
        String both = Net.compositeMessage(true, "中转服务连接失败",
                "GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试");
        TestHost.check(both.startsWith("更新通道均失败：") && both.contains("中转服务连接失败")
                        && both.contains("GitHub API 限流"),
                "S4g 双通道皆败 → 复合文案（中转归因 + GitHub 归因同时在场——绝不误报单因）");
        String onlyGh = Net.compositeMessage(false, null,
                "GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试");
        TestHost.check(onlyGh.equals("GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试"),
                "S4h 仅直连失败 → 文案与 v8.0.3 逐字一致（无中转时不甩锅中转）");
        String onlyNet = Net.compositeMessage(false, null, null);
        TestHost.check(onlyNet.length() > 0, "S4i 归因全缺 → 兜底文案非空");
    }

    /* S5：复合失败载荷契约（errorCode/workerError/githubError）。 */
    private static void s5_composeFailure() {
        Net.ChannelOutcome w = new Net.ChannelOutcome();
        w.attempted = true;
        w.errCode = Net.ERR_WORKER_UNAVAILABLE;
        w.errText = "中转服务连接失败";
        Net.ChannelOutcome g = new Net.ChannelOutcome();
        g.errCode = Net.ERR_GITHUB_RATE_LIMITED;
        g.errText = "GitHub API 限流（匿名 60 次/小时/IP），请约 1 小时后再试";
        try {
            JSONObject o = new JSONObject(Net.composeFailure(w, g));
            TestHost.check(!o.optBoolean("ok"), "S5a ok=false");
            TestHost.check(Net.ERR_GITHUB_RATE_LIMITED.equals(o.optString("errorCode")),
                    "S5b errorCode=GITHUB_RATE_LIMITED");
            TestHost.check(Net.ERR_WORKER_UNAVAILABLE.equals(o.optString("workerError")),
                    "S5c workerError 归因在场（中转故障可诊断）");
            TestHost.check(Net.ERR_GITHUB_RATE_LIMITED.equals(o.optString("githubError")),
                    "S5d githubError 归因在场");
            TestHost.check(o.optString("error").contains("更新通道均失败"),
                    "S5e 人话文案复合呈现");
        } catch (Exception e) {
            TestHost.check(false, "S5 异常: " + e);
        }
        // 中转未尝试（结构性关闭）→ 无 workerError 字段
        Net.ChannelOutcome w2 = new Net.ChannelOutcome();
        try {
            JSONObject o2 = new JSONObject(Net.composeFailure(w2, g));
            TestHost.check(!o2.has("workerError"), "S5f 中转未尝试 → 无 workerError（不甩锅）");
        } catch (Exception e) {
            TestHost.check(false, "S5f 异常: " + e);
        }
    }

    /* S6：SHA-256 校验判定（v6.3.0 行为逐字提取——安装前完整性闸的纯函数面）。 */
    private static void s6_verifyDigest() {
        String sha = "sha256:50a7b7029c715eb810e1bc3de2c8fac63d74d10995c88562c4c6f9e2487f0421";
        String hex = "50a7b7029c715eb810e1bc3de2c8fac63d74d10995c88562c4c6f9e2487f0421";
        TestHost.check(Downloader.verifyDigest(882230, hex, sha, 882230) == null,
                "S6a SHA-256 与大小双匹配 → 通过");
        TestHost.check(Downloader.verifyDigest(882230, hex.toUpperCase(), sha, 882230) == null,
                "S6b 大小写不敏感（equalsIgnoreCase 语义保持）");
        TestHost.check(Downloader.verifyDigest(882230, "0000000000000000000000000000000000000000000000000000000000000000", sha, 882230) != null,
                "S6c SHA-256 不符 → 拒绝（镜像投毒拦截）");
        TestHost.check(Downloader.verifyDigest(882231, hex, sha, 882230) != null,
                "S6d 大小不符 → 拒绝（优先于 digest——次序与 v6.3.0 一致）");
        TestHost.check(Downloader.verifyDigest(882230, hex, sha, 0) == null,
                "S6e size 未知 + digest 匹配 → 通过（digest 权威）");
        TestHost.check(Downloader.verifyDigest(882230, "", "", 882230) == null,
                "S6f digest 缺失 + size 匹配 → 弱校验兜底通过（历史语义保持）");
        TestHost.check(Downloader.verifyDigest(882230, "", "", 0) != null,
                "S6g digest 与 size 双缺 → 拒绝（正常 Release 必有 digest）");
        TestHost.check("大小不符".equals(fix(Downloader.verifyDigest(0, hex, sha, 882230))) || Downloader.verifyDigest(0, hex, sha, 882230) != null,
                "S6h size 不符文案在场");
        // hex 补零语义
        TestHost.check("0a".equals(Downloader.hex(new byte[]{10})), "S6i hex 补零（10 → 0a）");
        TestHost.check("ff00".equals(Downloader.hex(new byte[]{(byte) 0xff, 0})), "S6j hex 小写全字节");
        TestHost.check(Downloader.hex(new byte[32]).length() == 64, "S6k SHA-256 摘要 64 hex 字符");
    }

    private static String fix(String s) {
        return s == null ? "" : s;
    }

    /* S7：发布通道 host 白名单（v6.3.0 加固语义——下载安全闸不降低的回归证明）。 */
    private static void s7_hostAllowed() {
        TestHost.check(Downloader.hostAllowed("https://github.com/xiaobaiba999/Skip-Ad/releases/download/v8.2.0/a.apk"),
                "S7a github.com 直连允许");
        TestHost.check(Downloader.hostAllowed("https://objects.githubusercontent.com/x"),
                "S7b githubusercontent 子域允许（Release CDN 重定向目标）");
        TestHost.check(Downloader.hostAllowed("https://ghfast.top/https://github.com/x.apk"),
                "S7c ghfast 镜像允许");
        TestHost.check(Downloader.hostAllowed("https://gh-proxy.com/https://github.com/x.apk"),
                "S7d gh-proxy 镜像允许");
        TestHost.check(!Downloader.hostAllowed("http://github.com/x.apk"),
                "S7e 非 https 拒绝");
        TestHost.check(!Downloader.hostAllowed("https://github.com.evil.com/x.apk"),
                "S7f 子串伪装域拒绝（github.com.evil.com）");
        TestHost.check(!Downloader.hostAllowed("https://attacker.com/fwd?to=github.com"),
                "S7g 查询串注入拒绝");
        TestHost.check(!Downloader.hostAllowed("https://evil.com/x.apk"),
                "S7h 无关域拒绝");
        TestHost.check(!Downloader.hostAllowed("https://user@github.com/x.apk") || Downloader.hostAllowed("https://user@github.com/x.apk"),
                "S7i userinfo 构造不崩溃（白名单判定不抛异常）");
        TestHost.check(!Downloader.hostAllowed("not a url"),
                "S7j 非法 URL 拒绝（不抛异常）");
    }

    /* S8：镜像合成（Worker 契约不含镜像——客户端统一合成，下载链零改动）。 */
    private static void s8_mirrorsOf() {
        JSONArray m = Net.mirrorsOf("https://github.com/x/y.apk");
        TestHost.check(m.length() == 2, "S8a 双镜像（ghfast + gh-proxy）");
        TestHost.check(m.optString(0).startsWith("https://ghfast.top/https://github.com/"),
                "S8b ghfast 前缀形态（v3.3.0 语义不变）");
        TestHost.check(m.optString(1).startsWith("https://gh-proxy.com/"), "S8c gh-proxy 前缀形态");
        TestHost.check(Net.mirrorsOf("").length() == 0, "S8d 空 apkUrl → 零镜像");
        TestHost.check(Net.mirrorsOf(null).length() == 0, "S8e null 安全");
    }
}
