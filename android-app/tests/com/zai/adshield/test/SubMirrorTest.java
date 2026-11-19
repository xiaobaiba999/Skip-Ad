package com.zai.adshield.test;

import com.zai.adshield.core.SubMirrors;

import java.util.List;

/**
 * v7.0.0 订阅镜像链 + 离线快照映射测试（SubMirrors 纯函数 · JVM 断言锁定）。
 * 用户实锤（订阅市场点击导入时提示「拉取失败，Connection reset」——
 * v6.1 五通道链在部分运营商网络下仍全军覆没）：
 *  · 八通道镜像链：原址 → fastly → gcore → testingcf → ghfast 代理 →
 *    gh-proxy 代理 → raw 直连（顺序锁定——顺序即容灾优先级）；
 *  · 任意 jsdelivr 接入点作输入 → 同一条链（归一化）；
 *  · raw.githubusercontent 输入 → jsdelivr 兜底追加（v4.1.0 链保持）；
 *  · @gkd-kit 官方 → npmmirror 镜像（v4.1.0 链保持）；
 *  · 离线快照归一匹配：三个市场精选源的任意接入点变体 → 同一资产；
 *  · 自定义源：仅原址 + 无快照（绝不让用户以为有兜底）。
 */
public final class SubMirrorTest {

    public static void main(String[] args) {
        String linarm = "https://cdn.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5";

        /* ══ 一、八通道镜像链（顺序锁定）══ */
        TestHost.suite("八通道镜像链");
        List<String> v = SubMirrors.variants(linarm);
        TestHost.checkEq(7, v.size(), "市场源展开 7 变体（原址+fastly+gcore+testingcf+ghfast+gh-proxy+raw）");
        TestHost.checkEq(linarm, v.get(0), "变体 0 = 原址（用户填写的地址打头）");
        TestHost.check(v.get(1).startsWith("https://fastly.jsdelivr.net/gh/"), "变体 1 = fastly 接入点");
        TestHost.check(v.get(2).startsWith("https://gcore.jsdelivr.net/gh/"), "变体 2 = gcore 接入点");
        TestHost.check(v.get(3).startsWith("https://testingcf.jsdelivr.net/gh/"), "变体 3 = testingcf 接入点");
        TestHost.check(v.get(4).startsWith("https://ghfast.top/"), "变体 4 = ghfast 代理（代理 raw 直连地址）");
        TestHost.check(v.get(5).startsWith("https://gh-proxy.com/"), "变体 5 = gh-proxy 代理");
        TestHost.check(v.get(6).startsWith("https://raw.githubusercontent.com/Lin-arm/GKD_subscription/main/"), "变体 6 = raw 直连");
        // 三接入点变体指向同一内容路径（owner/repo@branch/path 归一）
        for (int i = 0; i <= 6; i++) {
            TestHost.check(v.get(i).endsWith("Lin-arm/GKD_subscription@main/dist/gkd.json5")
                            || v.get(i).endsWith("Lin-arm/GKD_subscription/main/dist/gkd.json5")
                            || v.get(i).contains("Lin-arm/GKD_subscription/main/raw/main/dist/gkd.json5")
                            || v.get(i).contains("Lin-arm/GKD_subscription/raw/main/dist/gkd.json5"),
                    "变体 " + i + " 指向同一订阅文件");
        }

        /* ══ 二、归一化：任意接入点输入 → 同一条链 ══ */
        TestHost.suite("接入点归一化");
        List<String> v2 = SubMirrors.variants("https://fastly.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5");
        TestHost.checkEq(7, v2.size(), "fastly 输入 → 同样 7 变体（canonical cdn 也补入链）");
        TestHost.check(v2.get(0).startsWith("https://fastly.jsdelivr.net/"), "fastly 输入原址打头（记忆地址优先）");
        boolean sameSet = true;
        for (String u : v2) {
            if (!u.contains("Lin-arm/GKD_subscription")) sameSet = false;
        }
        TestHost.check(sameSet, "全部变体同源（owner/repo/branch/path 一致）");
        TestHost.checkEq(SubMirrors.jsdelivrKey(linarm),
                SubMirrors.jsdelivrKey("https://gcore.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5"),
                "cdn/gcore 接入点同归一键");

        /* ══ 三、离线快照归一匹配 ══ */
        TestHost.suite("离线快照归一匹配");
        TestHost.checkEq("subsnap/linarm.json5", SubMirrors.snapshotAssetFor(linarm), "市场源 1（Lin-arm）→ linarm 快照");
        TestHost.checkEq("subsnap/ganlin.json5",
                SubMirrors.snapshotAssetFor("https://cdn.jsdelivr.net/gh/ganlinte/GKD-subscription@main/dist/ganlin_gkd.json5"),
                "市场源 2（ganlinte）→ ganlin 快照");
        TestHost.checkEq("subsnap/xnx.json5",
                SubMirrors.snapshotAssetFor("https://cdn.jsdelivr.net/gh/MengNianxiaoyao/gkd-subscription@main/dist/gkd.json5"),
                "市场源 3（小年侠）→ xnx 快照");
        TestHost.checkEq("subsnap/linarm.json5",
                SubMirrors.snapshotAssetFor("https://fastly.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5"),
                "fastly 接入点变体 → 同一 linarm 快照（归一）");
        TestHost.checkEq("subsnap/linarm.json5",
                SubMirrors.snapshotAssetFor("https://gcore.jsdelivr.net/gh/Lin-arm/GKD_subscription@main/dist/gkd.json5"),
                "gcore 接入点变体 → 同一 linarm 快照（归一）");
        TestHost.checkEq(null, SubMirrors.snapshotAssetFor("https://example.com/custom.json5"),
                "自定义源 → 无快照（null）");
        TestHost.checkEq(null, SubMirrors.snapshotAssetFor("https://raw.githubusercontent.com/Lin-arm/GKD_subscription/main/dist/gkd.json5"),
                "raw 直连形式 → 无快照（市场走 jsdelivr 规范地址）");
        // 快照注册表与市场推荐表一一对应（同一套事实——Recommend 与 SubMirrors 同源）
        int marketSources = SubMirrors.SNAPSHOT_SOURCES.length;
        TestHost.checkEq(3, marketSources, "快照注册表恰好 3 个市场精选源");

        /* ══ 四、历史链路保持 ══ */
        TestHost.suite("历史链路保持（v4.1.0 转换链不回归）");
        List<String> raw = SubMirrors.variants("https://raw.githubusercontent.com/gkd-kit/subscription/main/dist/gkd.json5");
        boolean hasJsdelivr = false;
        for (String u : raw) {
            if (u.startsWith("https://cdn.jsdelivr.net/gh/gkd-kit/subscription@main/dist/gkd.json5")) hasJsdelivr = true;
        }
        TestHost.check(raw.size() >= 2 && hasJsdelivr, "raw 输入 → jsdelivr 兜底追加");
        List<String> gkdOfficial = SubMirrors.variants(linarm);
        TestHost.check(!gkdOfficial.isEmpty(), "链路非空");
        List<String> npm = SubMirrors.variants("https://github.com/gkd-kit/subscription");
        boolean hasNpmmirror = false;
        for (String u : SubMirrors.variants("https://cdn.jsdelivr.net/gh/gkd-kit/subscription@main/dist/gkd.json5")) {
            if (u.contains("registry.npmmirror.com/@gkd-kit/subscription")) hasNpmmirror = true;
        }
        TestHost.check(hasNpmmirror, "gkd-kit 官方源 → npmmirror 镜像在场");
        // 空输入
        TestHost.checkEq(0, SubMirrors.variants("").size(), "空串 → 空链");
        TestHost.checkEq(0, SubMirrors.variants(null).size(), "null → 空链");

        /* ══ 五、hostOf 进度提示 ══ */
        TestHost.suite("hostOf（镜像切换进度提示）");
        TestHost.checkEq("cdn.jsdelivr.net", SubMirrors.hostOf(linarm), "原址主机名");
        TestHost.checkEq("gh-proxy.com", SubMirrors.hostOf("https://gh-proxy.com/https://github.com/a/b"), "代理主机名");
        TestHost.checkEq("", SubMirrors.hostOf(null), "null → 空串");

        TestHost.done();
    }
}
