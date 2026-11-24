package com.zai.adshield.test;

import com.zai.adshield.core.ComplianceGuard;

/**
 * 图片跳过键专用边界终闸单元测试（v7.15.7 · 用户实锤续修：CSDN 动态图
 * 开屏「角落图片跳过键：语义断言（非跳过/关闭方向）→ 放弃」反复出现——
 * 纯图键无文本无 vid 语义，通用终闸语义断言永远凑不齐证据 = 收得到候选
 * 却出不了手；新增 imageKeyBoundaryCheck 场景+严格形态双重门替代）。
 * 与 RandGraphicSkipTest 同规约（纯 Java 零 Android 依赖，ECJ -1.8 编译 JVM 运行）。
 * ─────────────────────────────────────────────────────────────
 * 断言规划（check/checkEq 总数 18）：
 *  S1 形态门真值表（10）/ S2 场景门（4）/ S3 与通用闸对照（4）。
 * 标准屏 1080×2400；CSDN 动态图开屏典型形态：跳过键为无字小图片键
 * （如用户手动学习到的 (9%,80%) 位 ~97×72px 档），位置每次随机。
 */
public final class ImageKeyBoundaryTest {

    public static void main(String[] args) {
        final int W = 1080, H = 2400;

        /* ════ S1 形态门真值表（8 项）════ */
        TestHost.suite("ImageKeyBoundary · S1 形态门（≤6% 屏·宽≤30%·高≤12%）");
        // CSDN 实测档：~97×72（(9%,80%) 学习位）≈0.27% 屏 → 放行
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{93, 1913, 190, 1985}, W, H, "net.csdn.www", "SplashActivity")
                        == ComplianceGuard.BND_OK,
                "97×72 小图键（CSDN 实测档）→ 放行");
        // Keep 居中随机位 140×120 ≈0.65% 屏 → 放行
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{470, 1050, 610, 1170}, W, H, "com.gotokeep.keep", "SplashActivity")
                        == ComplianceGuard.BND_OK,
                "居中 140×120 小图键（Keep 随机位）→ 放行");
        // 角落档 120×90（右上）→ 放行（场景同、位置不限——位置门在决策层）
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{920, 90, 1040, 180}, W, H, "com.x.x", "SplashActivity")
                        == ComplianceGuard.BND_OK,
                "右上角落 120×90 图键 → 放行");
        // 宽超限：400px > 30%×1080=324 → 形态拒（CTA 横幅不是跳过键）
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{340, 1000, 740, 1120}, W, H, "com.x.x", "SplashActivity")
                        == ComplianceGuard.BND_FAIL_FORM,
                "宽 400px 超限 → 形态拒（横幅 CTA 防线）");
        // 高超限：500px > 12%×2400=288 → 形态拒
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{490, 900, 590, 1400}, W, H, "com.x.x", "SplashActivity")
                        == ComplianceGuard.BND_FAIL_FORM,
                "高 500px 超限 → 形态拒");
        // 面积纵深门：宽屏（2322×1080，wideForm → ref=1080²）上 696×129
        // 宽高双合规但面积 7.7% > 6% → 形态拒（竖屏上 30%×12%=3.6% < 6%
        // 面积门永不首发——它是宽屏/贴边组合的第四道纵深）
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{100, 400, 796, 529}, 2322, 1080, "com.x.x", "SplashActivity")
                        == ComplianceGuard.BND_FAIL_FORM,
                "宽屏 696×129 宽高合规但面积 7.7% 超限 → 形态拒（面积纵深门）");
        // 无效 bounds（null / 长度不足 / 零宽）→ 形态拒
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        null, W, H, "com.x.x", "SplashActivity")
                        == ComplianceGuard.BND_FAIL_FORM,
                "bounds=null → 形态拒");
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{100, 200}, W, H, "com.x.x", "SplashActivity")
                        == ComplianceGuard.BND_FAIL_FORM,
                "bounds 长度 2 → 形态拒");
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{100, 100, 100, 180}, W, H, "com.x.x", "SplashActivity")
                        == ComplianceGuard.BND_FAIL_FORM,
                "零宽键 → 形态拒");
        // 非法屏参 → 形态拒（防呆）
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        new int[]{93, 1913, 190, 1985}, 0, 0, "com.x.x", "SplashActivity")
                        == ComplianceGuard.BND_FAIL_FORM,
                "屏参 0×0 → 形态拒");

        /* ════ S2 场景门（4 项）════ */
        TestHost.suite("ImageKeyBoundary · S2 场景门（零扫描场景全拒）");
        int[] okKey = new int[]{93, 1913, 190, 1985};
        // 相机包 → 场景拒（issue#30 红线：相机界面绝不出手）
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        okKey, W, H, "com.android.camera", "com.android.camera.CameraActivity")
                        == ComplianceGuard.BND_FAIL_SCENARIO,
                "相机包 → 场景拒");
        // 短信覆盖层包 → 场景拒
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        okKey, W, H, "com.android.mms", "android.widget.FrameLayout")
                        == ComplianceGuard.BND_FAIL_SCENARIO,
                "短信覆盖层包 → 场景拒");
        // 相机窗口类 → 场景拒
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        okKey, W, H, "com.x.x", "com.miui.camera.Camera")
                        == ComplianceGuard.BND_FAIL_SCENARIO,
                "相机窗口类 → 场景拒");
        // 守护名单包（银行/支付）→ 场景拒
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        okKey, W, H, "com.eg.android.AlipayGphone", "SplashActivity")
                        == ComplianceGuard.BND_FAIL_SCENARIO,
                "支付宝（守护名单）→ 场景拒");

        /* ════ S3 与通用闸对照（4 项）════ */
        TestHost.suite("ImageKeyBoundary · S3 对照（语义换源边界——只救纯图键）");
        // 同一目标：通用闸对零文本零 vid 键 BND_FAIL_SEMANTIC（CSDN 死因本体）
        TestHost.check(ComplianceGuard.boundaryCheck(
                        "", "", okKey, W, H, "net.csdn.www", "SplashActivity", false)
                        == ComplianceGuard.BND_FAIL_SEMANTIC,
                "通用闸：零文本零 vid 纯图键 → 语义拒（CSDN 死因本体）");
        // 同一目标：图片键闸 BND_OK（语义换源后放行）
        TestHost.check(ComplianceGuard.imageKeyBoundaryCheck(
                        okKey, W, H, "net.csdn.www", "SplashActivity")
                        == ComplianceGuard.BND_OK,
                "图片键闸：同一目标 → 放行（语义换源）");
        // 通用闸对带 skip vid 的键本来就放行（v3.8.0 微博路径零回归）
        TestHost.check(ComplianceGuard.boundaryCheck(
                        "", "com.weibo/iv_skip", okKey, W, H, "com.sina.weibo", "SplashActivity", false)
                        == ComplianceGuard.BND_OK,
                "通用闸：vid 含 skip → 原路径放行（零回归）");
        // 通用闸对「立即下载」文案诱饵照旧语义拒（图片键闸只救纯图键——
        // 服务侧接线限定 reason=corner_image_skip/rand_graphic_skip 且
        // bl/bv 双空，此断言钉死「有文案但非跳过语义的真诱饵零放行」）
        TestHost.check(ComplianceGuard.boundaryCheck(
                        "立即下载", "", new int[]{400, 1000, 680, 1120}, W, H, "com.x.x", "SplashActivity", false)
                        == ComplianceGuard.BND_FAIL_SEMANTIC,
                "通用闸：「立即下载」诱饵文案 → 语义拒（红线不破）");

        TestHost.done();
    }
}
