package com.zai.adshield.test;

import com.zai.adshield.core.ReleaseNotes;

/**
 * v7.0.0 版本描述规范测试（ReleaseNotes 纯函数 · JVM 断言锁定）。
 * 用户实锤（检查更新只弹版本号、看不到新版本功能；描述要言简意赅、
 * 带序号、不超过 5 个标题）：
 *  · 「1. 2. 3.」序号行解析（本仓库 v7.0.0 起规范发布体——发布脚本
 *    publish-v700 的 body 即按此格式生成，发布体与解析器同源锁定）；
 *  · ≤5 条上限 + 去重；
 *  · 历史格式四代兼容（①圈号 / # 标题 / - 列表 / * 列表——v6.1-v6.5
 *    时代发布体依然可解析）；
 *  · 噪点过滤（表格行/代码围栏/引用行/纯链接行/分隔线/太短行）；
 *  · markdown 清洗（链接→锚文本、强调/代码符号剥离、44 字截断）。
 */
public final class ReleaseNotesTest {

    public static void main(String[] args) {
        /* ══ 一、v7.0.0 规范发布体（序号行优先——与 publish-v700 同源）══ */
        TestHost.suite("v7.0.0 规范发布体（序号行）");
        String v7Body = "开屏卫士 v7.0.0：回退到 6.0 已验证基座重构——6.0 之后的版本"
                + "全部从 GitHub 清除，本版在稳定基座上重新落地全部新特性。\n\n"
                + "### 新版本功能\n\n"
                + "1. 订阅市场导入根治：八通道镜像自动切换（失败进度可见），全失败时离线快照兜底——导入永不失败\n"
                + "2. 检查更新当场显示新版本 ≤5 条带序号功能描述；顶部更新横幅移除，只有手动检查更新可下载\n"
                + "3. 引擎日志留存 400 条 + 设备画像（分辨率/密度），每天 8 小时一次自动提交 3 次\n"
                + "4. 多点误点三重防线（同目标静默·新鲜坐标·结果静默）按设备类型与分辨率自适应\n"
                + "5. 9 品牌+子品牌 11 组 286 机型测试矩阵五维全绿（开屏/速度/bug/功耗/精准度）\n"
                + "6. 免打扰持久模式取消（零电脑）；界面初始化保险丝——绝不出现「永久加载」白屏态\n"
                + "\n> 升级方式：覆盖安装即用（versionCode 70000 > 60500）。\n"
                + "| 校验 | 值 |\n| --- | --- |\n| MD5 | 1234 |\n";
        String[] h = ReleaseNotes.headlines(v7Body);
        TestHost.checkEq(5, h.length, "v7 发布体解析出 ≤5 条（第 6 条被截断在 5 上限）");
        TestHost.check(h[0].contains("八通道镜像"), "第 1 条 = 订阅导入根治（序号 1）");
        TestHost.check(h[1].contains("检查更新"), "第 2 条 = 检查更新功能描述（序号 2）");
        TestHost.check(h[4].contains("286 机型") || h[4].contains("机型"), "第 5 条 = 测试矩阵（序号 5）");
        for (String s : h) {
            TestHost.check(s.length() <= ReleaseNotes.MAX_LEN, "每条 ≤" + ReleaseNotes.MAX_LEN + " 字（言简意赅）");
            TestHost.check(!s.startsWith("|") && !s.startsWith(">") && !s.contains("```"), "表格/引用/代码行绝不入标题");
        }

        /* ══ 二、上限与去重 ══ */
        TestHost.suite("上限与去重");
        String many = "";
        for (int i = 1; i <= 9; i++) many += i + ". 功能第" + i + "条\n";
        TestHost.checkEq(5, ReleaseNotes.headlines(many).length, "9 条序号行 → 截断在 5");
        String dup = "1. 同一条功能\n1. 同一条功能\n1. 同一条功能";
        TestHost.checkEq(1, ReleaseNotes.headlines(dup).length, "重复标题去重（3 条同文 → 1）");

        /* ══ 三、历史格式四代兼容 ══ */
        TestHost.suite("历史格式兼容（v6.1-v6.5 发布体依然可解析）");
        String legacy = "开屏卫士 v6.1.0：更快、更稳、更省心。\n\n"
                + "## 新版亮点\n\n"
                + "- 订阅拉取五镜像自愈（Connection reset 根治）\n"
                + "- 误触三重防线（同指纹去抖·新鲜坐标·结果静默）\n"
                + "- 日志一日三次（8h 均匀）\n"
                + "* 免打扰持久模式取消\n"
                + "①日志留存 300 条\n";
        String[] lh = ReleaseNotes.headlines(legacy);
        TestHost.checkEq(5, lh.length, "历史体解析出 5 条（-/* /① 列表行兼容；## 章节名不入列）");
        TestHost.check(lh[0].contains("五镜像"), "历史体首条 = - 列表行");
        TestHost.check(lh[4].contains("300 条"), "历史体第 5 条 = ① 圈号行");
        // H1 版本名行也不是功能标题（版本名由调用方另行展示——绝50个名额）
        String h1only = "# 开屏卫士 v6.1.0\n副段落不是列表。";
        TestHost.checkEq(0, ReleaseNotes.headlines(h1only).length, "纯 H1 → 0 条（章节名/版本名不入功能列表）");

        /* ══ 四、噪点过滤 ══ */
        TestHost.suite("噪点过滤");
        String junk = "正文首段不是列表。\n\n"
                + "```代码块第一行\n```\n"
                + "> 引用行不是标题\n"
                + "| 表格 | 行 |\n"
                + "---\n"
                + "\n"
                + "- \n";
        TestHost.checkEq(0, ReleaseNotes.headlines(junk).length, "纯噪点 → 0 条（代码/引用/表格/分隔/空全滤）");
        TestHost.checkEq(0, ReleaseNotes.headlines(null).length, "null → 0 条");
        TestHost.checkEq(0, ReleaseNotes.headlines("").length, "空串 → 0 条");

        /* ══ 五、markdown 清洗 ══ */
        TestHost.suite("markdown 清洗");
        String md = "1. [下载链接](https://example.com/a) 与 **强调** 和 `代码` 的标题行";
        String[] mh = ReleaseNotes.headlines(md);
        TestHost.checkEq(1, mh.length, "含 markdown 的序号行解析成功");
        TestHost.check(mh[0].contains("下载链接") && !mh[0].contains("https"), "链接 → 锚文本（URL 剥离）");
        TestHost.check(!mh[0].contains("**") && !mh[0].contains("`"), "强调/代码符号剥离");
        String longTitle = "1. " + new String(new char[60]).replace('\0', '长');
        String[] lth = ReleaseNotes.headlines(longTitle);
        TestHost.checkEq(1, lth.length, "超长标题不丢条");
        TestHost.check(lth[0].length() == ReleaseNotes.MAX_LEN && lth[0].endsWith("…"), "超长截断 +" + "…");

        /* ══ 六、常量契约 ══ */
        TestHost.suite("常量契约（用户指令：不要超过 5 个标题）");
        TestHost.checkEq(5, ReleaseNotes.MAX_HEADLINES, "MAX_HEADLINES = 5");
        TestHost.checkEq(44, ReleaseNotes.MAX_LEN, "MAX_LEN = 44（言简意赅）");

        TestHost.done();
    }
}
