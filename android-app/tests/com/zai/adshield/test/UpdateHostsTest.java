package com.zai.adshield.test;

import com.zai.adshield.core.UpdateHosts;

/**
 * 更新源 jsdelivr 多镜像域序单元测试（v7.15.3 · 真机实锤修复轮）。
 * ─────────────────────────────────────────────────────────────
 * 背景：v7.15.1 用户截图实锤「多源均不可达：raw@main 不可达 · jsdelivr@main
 * 不可达 · GitHub API 返回 401」三面全崩——cdn.jsdelivr.net 单域写死是
 * 清单源的结构性弱点。本轮四域梯度 + 域偏好 sticky + @tag 域收敛。
 * 断言规划（checkEq 总数 24 + S5 新增 22 项）：
 *  S1 基础序与梯度（6）/ S2 @main 偏好置顶（8）
 *  S3 @tag 域收敛 ≤2（6）/ S4 边界与不变式（4）
 *  S5 v7.15.5：下载白名单四域收编 + CDN 直链合成（23）。
 * 与 core 包同规约（纯 Java 零 Android 依赖，ECJ -1.8 编译 JVM 运行）。
 */
public final class UpdateHostsTest {

    public static void main(String[] args) {
        /* ════ S1 基础序与梯度（6 项）════ */
        TestHost.suite("UpdateHosts · S1 基础序（gcore 优先四域梯度）");
        TestHost.checkEq(4, UpdateHosts.JSDELIVR_HOSTS.length, "四域梯度");
        TestHost.checkEq("gcore.jsdelivr.net", UpdateHosts.JSDELIVR_HOSTS[0], "gcore 首位（国内可达性最优）");
        TestHost.checkEq("cdn.jsdelivr.net", UpdateHosts.JSDELIVR_HOSTS[1], "cdn 次位");
        TestHost.checkEq("fastly.jsdelivr.net", UpdateHosts.JSDELIVR_HOSTS[2], "fastly 第三");
        TestHost.checkEq("testingcf.jsdelivr.net", UpdateHosts.JSDELIVR_HOSTS[3], "testingcf 第四");
        TestHost.checkEq(4, UpdateHosts.orderedMainHosts(null).length, "无偏好 @main = 基础序四域");

        /* ════ S2 @main 偏好置顶（8 项）════ */
        TestHost.suite("UpdateHosts · S2 @main 偏好域置顶（sticky 语义）");
        String[] m1 = UpdateHosts.orderedMainHosts("fastly.jsdelivr.net");
        TestHost.checkEq(4, m1.length, "偏好 fastly：长度守恒（去重不膨胀）");
        TestHost.checkEq("fastly.jsdelivr.net", m1[0], "偏好 fastly 置顶");
        TestHost.checkEq("gcore.jsdelivr.net", m1[1], "其余保持梯度序（gcore 跟随）");
        TestHost.checkEq("cdn.jsdelivr.net", m1[2], "其余保持梯度序（cdn 第三）");
        TestHost.checkEq("testingcf.jsdelivr.net", m1[3], "其余保持梯度序（testingcf 第四）");
        String[] m2 = UpdateHosts.orderedMainHosts("gcore.jsdelivr.net");
        TestHost.checkEq("gcore.jsdelivr.net", m2[0], "偏好 gcore：置顶不重复");
        TestHost.checkEq(4, m2.length, "偏好 gcore：长度守恒（自身去重）");
        TestHost.checkEq("cdn.jsdelivr.net", m2[1], "偏好 gcore：cdn 跟随");

        /* ════ S3 @tag 域收敛 ≤2（6 项）════ */
        TestHost.suite("UpdateHosts · S3 @tag 域收敛（偏好 + gcore ≤2 域）");
        String[] t0 = UpdateHosts.orderedTagHosts(null);
        TestHost.checkEq(1, t0.length, "无偏好 @tag：gcore 单域");
        TestHost.checkEq("gcore.jsdelivr.net", t0[0], "无偏好 @tag：gcore");
        String[] t1 = UpdateHosts.orderedTagHosts("cdn.jsdelivr.net");
        TestHost.checkEq(2, t1.length, "偏好 cdn @tag：双域（偏好 + gcore）");
        TestHost.checkEq("cdn.jsdelivr.net", t1[0], "偏好 cdn 置顶");
        TestHost.checkEq("gcore.jsdelivr.net", t1[1], "gcore 兜底跟随");
        String[] t2 = UpdateHosts.orderedTagHosts("gcore.jsdelivr.net");
        TestHost.checkEq(1, t2.length, "偏好 gcore @tag：去重后单域（不重复探测）");

        /* ════ S4 边界与不变式（4 项）════ */
        TestHost.suite("UpdateHosts · S4 边界（空偏好/非法域/不变式）");
        TestHost.checkEq(4, UpdateHosts.orderedMainHosts("").length, "空串偏好 = 基础序");
        TestHost.checkEq(4, UpdateHosts.orderedMainHosts("evil.example.com").length,
                "非法域偏好（不在基础序）→ 基础序原样（不持契约不探测）");
        String[] t3 = UpdateHosts.orderedTagHosts("evil.example.com");
        TestHost.checkEq(1, t3.length, "非法域偏好 @tag → gcore 单域（攻击面收敛）");
        TestHost.checkEq("gcore.jsdelivr.net", t3[0], "非法域偏好 @tag → gcore");

        /* ════ S5 v7.15.5 下载通道白名单 + CDN 直链合成（22 项）════
         * 死因（用户实锤：v7.15.1/v7.15.3 点下载报「下载链接不在允许的
         * 发布通道内」）：渠道链首位的 gcore CDN 直链被旧白名单拒且不换
         * 渠道。本轮白名单收编 jsdelivr 四域 + 清单撤 cdnApkUrl 字段 +
         * 客户端本地合成（cdnApkUrl——路径穿越收敛）。 */
        TestHost.suite("UpdateHosts · S5 下载白名单四域收编（v7.15.5 死局根治）");
        TestHost.checkEq(true, UpdateHosts.isJsdelivrHost("gcore.jsdelivr.net"), "gcore 直出域在白名单");
        TestHost.checkEq(true, UpdateHosts.isJsdelivrHost("cdn.jsdelivr.net"), "cdn 域在白名单");
        TestHost.checkEq(true, UpdateHosts.isJsdelivrHost("fastly.jsdelivr.net"), "fastly 域在白名单");
        TestHost.checkEq(true, UpdateHosts.isJsdelivrHost("testingcf.jsdelivr.net"), "testingcf 域在白名单");
        TestHost.checkEq(false, UpdateHosts.isJsdelivrHost("evil.jsdelivr.net"), "非四域的 jsdelivr 子域拒绝（攻击面收敛——只认官方四接入点）");
        TestHost.checkEq(false, UpdateHosts.isJsdelivrHost("jsdelivr.net"), "裸域拒绝（无此接入点）");
        TestHost.checkEq(true, UpdateHosts.isJsdelivrHost("gcore.jsdelivr.net."),
                "尾点 FQDN 归一化命中（与 Downloader.hostAllowed 同语义——DNS 同一主机）");
        TestHost.checkEq(true, UpdateHosts.isJsdelivrHost("GCORE.JSDELIVR.NET"), "大小写混淆收敛（转小写后命中）");
        TestHost.checkEq(true, UpdateHosts.isJsdelivrHost("  gcore.jsdelivr.net  "), "首尾空白收敛");
        TestHost.checkEq(false, UpdateHosts.isJsdelivrHost("gcore.jsdelivr.net.evil.com"), "子串注入构造拒绝");
        TestHost.checkEq(false, UpdateHosts.isJsdelivrHost(""), "空串拒绝");
        TestHost.checkEq(false, UpdateHosts.isJsdelivrHost(null), "null 拒绝");
        TestHost.checkEq(false, UpdateHosts.isJsdelivrHost("github.com"), "github 域不在本函数辖（Downloader 侧自管）");

        TestHost.suite("UpdateHosts · S5b CDN 直链合成（清单撤字段后的本地合成）");
        TestHost.checkEq("https://gcore.jsdelivr.net/gh/xiaobaiba999/Skip-Ad@main/android-app/dist/adshield-v7.15.5.apk",
                UpdateHosts.cdnApkUrl("xiaobaiba999/Skip-Ad", "adshield-v7.15.5.apk"),
                "标准合成：repo + apkFileName → gcore dist 直链");
        TestHost.checkEq("https://gcore.jsdelivr.net/gh/a/b@main/android-app/dist/c.apk",
                UpdateHosts.cdnApkUrl("a/b", "c.apk"), "任意仓库合成");
        TestHost.checkEq("", UpdateHosts.cdnApkUrl("a/b", "c.txt"), "非 .apk 文件名拒绝");
        TestHost.checkEq("", UpdateHosts.cdnApkUrl("a/b", "../evil.apk"), "路径穿越（…/）拒绝");
        TestHost.checkEq("", UpdateHosts.cdnApkUrl("a/b", "sub/c.apk"), "子路径拒绝（只收纯文件名）");
        TestHost.checkEq("", UpdateHosts.cdnApkUrl("a/b", "c\\d.apk"), "反斜杠路径拒绝");
        TestHost.checkEq("", UpdateHosts.cdnApkUrl("", "c.apk"), "空仓库名拒绝");
        TestHost.checkEq("", UpdateHosts.cdnApkUrl("a/b", ""), "空文件名拒绝");
        TestHost.checkEq("", UpdateHosts.cdnApkUrl(null, "c.apk"), "null 仓库名拒绝");
        TestHost.checkEq("", UpdateHosts.cdnApkUrl("a/b", null), "null 文件名拒绝");

        TestHost.done();
    }
}
