package com.zai.adshield.test;

import com.zai.adshield.Net;

/**
 * 更新检查版本判定纯函数测试（v7.9.8 静态清单架构版）。
 * ─────────────────────────────────────────────
 * v7.9.6plus～v7.9.7：本套件曾覆盖 GitHub 直连通道（latestRelease/
 * parseRelease/mirrorsOf/rateLimited/错误四分类）——v7.9.8 更新检查架构
 * 改造整体移除（匿名 60 次/小时/IP 共享配额限流根除；替代实现见
 * UpdateManifestTest/UpdateRepositoryTest/UpdateAuditTest）。
 *
 * 原地保留的纯函数（清单与 Release 正文共用同一权威语义）：
 *  S1 versionCodeOfNotes：正文 versionCode 元数据行解析（清单 versionCode
 *     缺失时的回退来源——旧清单兼容链）。
 *  S2 isNewerRelease versionCode 主判（核心回归——"7.9.6plus"(82300) vs
 *     "8.2.2"(82200)：versionName 分段比较必判旧（7<8），versionCode 主判
 *     正确判新）；等于不提示重复；小于绝不提示降级；元数据缺失回退
 *     versionName 三段比较。
 *  S3 cmpVersion/segNum 三段数值比较保持（"8.10.0" > "8.9.0"）。
 *  S4 已移除符号的反射验证（GitHub 直连通道死灰复燃防线——与
 *     UpdateAuditTest 源码扫描互补）。
 */
public final class UpdateChannelTest {

    public static void main(String[] args) {
        TestHost.suite("UpdateChannelTest · v7.9.8 静态清单架构——版本判定纯函数");
        s1_versionCodeOfNotes();
        s2_isNewerRelease();
        s3_cmpVersion();
        s4_removedSymbols();
        TestHost.done();
    }

    /* ═══ S1 versionCodeOfNotes（清单 versionCode 缺失回退链）═══ */
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
        // v7.9.8 静态清单场景：清单 7.9.8(79800) vs 本地 7.9.7(79700)
        TestHost.check(Net.isNewerRelease(79800, "v7.9.8", 79700, "7.9.7"),
                "S2f 清单 7.9.8 vs 本地 7.9.7 → 判新（升级路径）");
        TestHost.check(Net.isNewerRelease(79700, "v7.9.7", 79800, "7.9.8") == false,
                "S2g 清单 7.9.7 vs 本地 7.9.8 → 不提示（绝不降级）");
        TestHost.check(Net.isNewerRelease(79700, "v7.9.7", 79700, "7.9.7") == false,
                "S2h 同版本不提示");
        // 元数据缺失（remoteVc=0）→ 回退 versionName 三段比较
        TestHost.check(Net.isNewerRelease(0, "8.5.0", 82300, "7.9.6plus"),
                "S2i 回退名比较：8.5.0 > 7.9.6plus（[8,5,0]>[7,9,6]）");
        // 【plus 后缀段解析修复回归】segNum("6plus")=6："7.9.6plus"≈[7,9,6]
        TestHost.check(Net.isNewerRelease(0, "7.9.6", 82300, "7.9.6plus") == false,
                "S2j plus 同族不提示：segNum(6plus)=6 → 7.9.6 == 7.9.6plus（修复回归）");
        // 本地 versionCode 取不到（0）→ 回退名比较
        TestHost.check(Net.isNewerRelease(82300, "9.0.0", 0, "8.2.2"),
                "S2k 本地 VC 缺失回退名比较：9.0.0 > 8.2.2");
        TestHost.check(Net.isNewerRelease(82300, "7.0.0", 0, "8.2.2") == false,
                "S2l 本地 VC 缺失回退名比较：7.0.0 < 8.2.2 不提示");
        // versionName 与 versionCode 不一致 → versionCode 主判赢（清单权威语义）
        TestHost.check(Net.isNewerRelease(79900, "7.9.7", 79800, "7.9.8"),
                "S2m versionCode 高于本地但 versionName 反映旧版 → 仍判新（主判权威）");
        TestHost.check(Net.isNewerRelease(79700, "8.0.0", 79800, "7.9.8") == false,
                "S2n versionName 看似更高但 versionCode 更低 → 不提示（主判防降级）");
    }

    /* ═══ S3 cmpVersion/segNum ═══ */
    private static void s3_cmpVersion() {
        TestHost.check(Net.cmpVersion("8.10.0", "8.9.0") > 0, "S3a 数值段非字典序");
        TestHost.check(Net.cmpVersion("v7.9.6", "7.9.6") == 0, "S3b v 前缀容忍");
        TestHost.check(Net.cmpVersion("7.9.6", "7.9.6.1") == 0, "S3c 缺段补 0（三段封顶）");
        TestHost.check(Net.cmpVersion("8.2.2", "7.9.6plus") > 0,
                "S3d 名比较视角 8.2.2 > 7.9.6plus（versionCode 主判存在的理由）");
        TestHost.check(Net.cmpVersion("7.9.6plus", "7.9.6") == 0,
                "S3e segNum 修复回归：7.9.6plus 与 7.9.6 同族（6plus→6）");
        TestHost.check(Net.cmpVersion("7.9.6plus2", "7.9.6") == 0,
                "S3f 任意字母后缀同族（6plus2→6）");
        TestHost.check(Net.segNum("6plus") == 6, "S3g segNum 前缀数字");
        TestHost.check(Net.segNum("") == 0, "S3h segNum 空串");
        TestHost.check(Net.segNum("x") == 0, "S3i segNum 纯字母 → 0");
    }

    /* ═══ S4 已移除符号反射验证（GitHub 直连通道死灰复燃防线）═══ */
    private static void s4_removedSymbols() {
        boolean gone = true;
        try {
            Net.class.getMethod("latestRelease", String.class, int.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S4a Net.latestRelease（GitHub REST 直连）已移除");

        gone = true;
        try {
            Net.class.getMethod("parseRelease", String.class, String.class, int.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S4b Net.parseRelease 已移除（清单解析迁移 UpdateManifest.parse）");

        gone = true;
        try {
            Net.class.getMethod("mirrorsOf", String.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S4c Net.mirrorsOf 已移除");

        gone = true;
        try {
            Net.class.getMethod("rateLimited", int.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S4d Net.rateLimited 已移除（迁移 UpdateManifest.isRateLimitCode）");

        gone = true;
        try {
            Net.class.getField("API");
            gone = false;
        } catch (NoSuchFieldException expected) {
        }
        TestHost.check(gone, "S4e Net.API（api.github.com 基址）已移除");

        gone = true;
        try {
            Net.class.getField("ERR_GITHUB_RATE_LIMITED");
            gone = false;
        } catch (NoSuchFieldException expected) {
        }
        TestHost.check(gone, "S4f Net.ERR_GITHUB_RATE_LIMITED 已移除（「GitHub API 限流」文案随根因消除）");

        gone = true;
        try {
            Net.class.getField("RELEASES_PAGE");
            gone = false;
        } catch (NoSuchFieldException expected) {
        }
        TestHost.check(gone, "S4g Net.RELEASES_PAGE 已移除");

        TestHost.check("xiaobaiba999/Skip-Ad".equals(com.zai.adshield.Net.REPO),
                "S4h Net.REPO 保留（UpdateManifest 构造 Release 页链接用）");
    }
}
