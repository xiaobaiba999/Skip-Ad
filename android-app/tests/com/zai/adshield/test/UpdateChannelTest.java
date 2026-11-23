package com.zai.adshield.test;

import com.zai.adshield.Net;

/**
 * 更新检查版本判定纯函数测试（v7.10.0 统一版本规则版）。
 * ─────────────────────────────────────────────
 * v7.9.6plus～v7.9.7：本套件曾覆盖 GitHub 直连通道（latestRelease/
 * parseRelease/mirrorsOf/rateLimited/错误四分类）——v7.9.8 更新检查架构
 * 改造整体移除（匿名 60 次/小时/IP 共享配额限流根除；替代实现见
 * UpdateManifestTest/UpdateRepositoryTest/UpdateAuditTest）。
 *
 * v7.10.0 统一版本规则专项重做本套件：
 *  S1 isNewerRelease 统一规则矩阵（唯一权威语义：remoteVersionCode >
 *     currentVersionCode；versionName 仅展示，绝不参与更新判断）：
 *     六场景矩阵 + plus 历史回归锚点 + 边界值。
 *  S2 已移除符号反射验证（versionName 回退链四函数 + GitHub 直连通道
 *     六符号死灰复燃防线——与 UpdateAuditTest 源码扫描互补）。
 */
public final class UpdateChannelTest {

    public static void main(String[] args) {
        TestHost.suite("UpdateChannelTest · v7.10.0 统一版本规则——版本判定纯函数");
        s1_isNewerRelease();
        s2_removedSymbols();
        TestHost.done();
    }

    /* ═══ S1 isNewerRelease 统一规则矩阵（v7.10.0）═══
     * 全项目唯一权威语义：remoteVersionCode > currentVersionCode。
     * versionName 不再是本函数入参（仅展示——结构上不可能参与判断）。 */
    private static void s1_isNewerRelease() {
        // 六场景矩阵（用户指令矩阵逐条钉死）
        TestHost.check(Net.isNewerRelease(79900, 80000) == false,
                "S1a 矩阵① 79900→80000 前旧后新：80000 用户看到 79900 清单 → 不提示");
        TestHost.check(Net.isNewerRelease(80000, 80000) == false,
                "S1b 矩阵② 80000→80000 同码：不提示重复更新");
        TestHost.check(Net.isNewerRelease(80100, 80000),
                "S1c 矩阵③ 80100→80000 远端更高：正常判新");
        TestHost.check(Net.isNewerRelease(79900, 80000) == false,
                "S1d 矩阵④ 79900→80000 远端更低：绝不提示降级");
        TestHost.check(Net.isNewerRelease(0, 80000) == false,
                "S1e 矩阵⑤ 远端 versionCode 无效（0/缺失）→ 不提示（绝不回退名比较）");
        TestHost.check(Net.isNewerRelease(80000, 0) == false,
                "S1f 矩阵⑥ 本地 versionCode 无效（0/缺失）→ 不提示（绝不回退名比较）");

        // 升级路径回归锚点（历史序列）
        TestHost.check(Net.isNewerRelease(79800, 79700),
                "S1g 清单 79800 vs 本地 79700 → 判新（v7.9.7→v7.9.8 升级路径）");
        TestHost.check(Net.isNewerRelease(79700, 79800) == false,
                "S1h 清单 79700 vs 本地 79800 → 不提示（陈旧清单防降级）");
        TestHost.check(Net.isNewerRelease(79700, 79700) == false,
                "S1i 同版本不提示");

        // plus 命名历史回归（82300 误判史——统一规则从结构上根治）
        TestHost.check(Net.isNewerRelease(82300, 82200),
                "S1j 历史回归：82300 vs 82200 → 判新（plus 命名不构成任何特殊路径）");
        TestHost.check(Net.isNewerRelease(82200, 82300) == false,
                "S1k 历史回归：82200 vs 82300 → 不提示（versionName 视角 8.2.2「高于」7.9.6plus 也不放行）");
        TestHost.check(Net.isNewerRelease(82300, 82300) == false,
                "S1l 已装 82300 同码不提示");

        // 边界值（负数/溢出面）
        TestHost.check(Net.isNewerRelease(-1, 80000) == false,
                "S1m 负 versionCode（远端）→ 不提示（无效输入）");
        TestHost.check(Net.isNewerRelease(80000, -1) == false,
                "S1n 负 versionCode（本地）→ 不提示（无效输入）");
        TestHost.check(Net.isNewerRelease(Integer.MAX_VALUE, 80000),
                "S1o int 上界远端 → 正常判新（无溢出面）");
        TestHost.check(Net.isNewerRelease(Integer.MAX_VALUE, Integer.MAX_VALUE) == false,
                "S1p 双上界同码 → 不提示");
    }

    /* ═══ S2 已移除符号反射验证（死灰复燃防线）═══ */
    private static void s2_removedSymbols() {
        // v7.10.0 统一规则专项删除的 versionName 回退链四函数
        boolean gone = true;
        try {
            Net.class.getMethod("versionCodeOfNotes", String.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2a Net.versionCodeOfNotes（正文元数据行回退）已移除");

        gone = true;
        try {
            Net.class.getMethod("cmpVersion", String.class, String.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2b Net.cmpVersion（versionName 三段比较）已移除");

        gone = true;
        try {
            Net.class.getMethod("versionSegs", String.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2c Net.versionSegs（三段拆分）已移除");

        gone = true;
        try {
            Net.class.getMethod("segNum", String.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2d Net.segNum（plus 后缀段解析——82300/plus 兼容核心）已移除");

        // isNewerRelease 旧四参签名（含 versionName 回退入参）不得回场
        gone = true;
        try {
            Net.class.getMethod("isNewerRelease",
                    int.class, String.class, int.class, String.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2e Net.isNewerRelease 旧四参签名（versionName 回退入参）已移除——仅剩二参统一规则版");

        // v7.9.8 已移除的 GitHub 直连通道符号（防线保持）
        gone = true;
        try {
            Net.class.getMethod("latestRelease", String.class, int.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2f Net.latestRelease（GitHub REST 直连）已移除");

        gone = true;
        try {
            Net.class.getMethod("parseRelease", String.class, String.class, int.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2g Net.parseRelease 已移除（清单解析迁移 UpdateManifest.parse）");

        gone = true;
        try {
            Net.class.getMethod("mirrorsOf", String.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2h Net.mirrorsOf 已移除");

        gone = true;
        try {
            Net.class.getMethod("rateLimited", int.class);
            gone = false;
        } catch (NoSuchMethodException expected) {
        }
        TestHost.check(gone, "S2i Net.rateLimited 已移除（迁移 UpdateManifest.isRateLimitCode）");

        gone = true;
        try {
            Net.class.getField("API");
            gone = false;
        } catch (NoSuchFieldException expected) {
        }
        TestHost.check(gone, "S2j Net.API（api.github.com 基址）已移除");

        gone = true;
        try {
            Net.class.getField("ERR_GITHUB_RATE_LIMITED");
            gone = false;
        } catch (NoSuchFieldException expected) {
        }
        TestHost.check(gone, "S2k Net.ERR_GITHUB_RATE_LIMITED 已移除（「GitHub API 限流」文案随根因消除）");

        gone = true;
        try {
            Net.class.getField("RELEASES_PAGE");
            gone = false;
        } catch (NoSuchFieldException expected) {
        }
        TestHost.check(gone, "S2l Net.RELEASES_PAGE 已移除");

        TestHost.check("xiaobaiba999/Skip-Ad".equals(com.zai.adshield.Net.REPO),
                "S2m Net.REPO 保留（UpdateManifest 构造 Release 页链接用）");
    }
}
