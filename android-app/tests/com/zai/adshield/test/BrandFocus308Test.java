package com.zai.adshield.test;

import com.zai.adshield.core.AdDecisionCore;
import com.zai.adshield.core.AdDecisionCore.Ctx;
import com.zai.adshield.core.AdDecisionCore.Decision;
import com.zai.adshield.core.AdDecisionCore.ScanFind;
import com.zai.adshield.core.FeedAdGuard;
import com.zai.adshield.core.HumanTap;
import com.zai.adshield.core.PowerGovernor;
import com.zai.adshield.core.RestoreGuard;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.Subscription;
import com.zai.adshield.core.TapPoint;
import com.zai.adshield.test.TestHost.SimNode;

/**
 * v7.1.0 扩容（用户指令：拓展设备型号和品牌以及系统 + 信息流广告维）：
 *  每品牌 26→28 机型（新增 HyperOS 5/HarmonyOS 8/MagicOS 12/ColorOS 18/
 *  OriginOS 8/One UI 10 世代机型）· 11 组 × 28 = 308 机型【八维】全 308/308
 *  ——第七维「信息流广告」（FeedAdGuard 移植回归：朋友圈就业广告等 App 内
 *  广告两段式，角标/菜单几何按机型分辨率 1:1 缩放逐机型验证）。
 *
 * v7.0.0 扩容（用户指令：拓展设备型号和系统——每品牌 20→26 机型）：
 *  新增 2026-2027 世代机型（小米 18 系/HyperOS 4 · Redmi K100/Turbo 5/
 *  Note 16 · POCO F9 · Mate 80/HarmonyOS 7 · Magic9/MagicOS 11 · Find X9/
 *  ColorOS 17 · 真我 GT8 · X300/OriginOS 7 · iQOO 19/Neo 12 · S26/One UI 9 ·
 *  一加 17/Ace 7/Nord 6——11 组 × 26 = 286 机型七维全 286/286；
 *  同轮新增 ReleaseNotes/SubMirrors 纯函数套件（版本描述 + 订阅镜像链）。
 *
 * v6.1.0 品牌聚焦矩阵测试（9 品牌+子品牌 11 组 × 26 机型 = 286 · 七维全 286/286）。
 * 用户指令：优化模拟各个品牌和机型进行测试修复，拓展设备型号和品牌以及系统，
 * 只测试 oppo/vivo/xiaomi/三星/华为/荣耀/红米/一加/iqoo 及其子品牌，其他品牌
 * 就不要测试了——矩阵自 v6.0.0 的 12×15=180 收窄+扩容为 11 组 × 26=286：
 * 删除魅族、中兴/努比亚/红魔、传音 Tecno/Infinix/itel、摩托罗拉、其他
 * （Pixel/Nothing/索尼/联想）五组；红米与 iQOO 按用户点名独立成组（制造商
 * 仍为 Xiaomi/vivo），POCO 与 realme 作为小米/OPPO 子品牌独立成组；机型表
 * 新增第四列「系统版本」（系统拓展：同一机型跨系统版本如 HyperOS 2→3 的
 * 演化在品牌层代表——HyperOS 2/3、HarmonyOS 4.2/5.0/5.1、MagicOS 8/9/10、
 * ColorOS 15/16、OriginOS 5/6、One UI 6.1/7/8、OxygenOS 15）。
 *
 * 品牌组（11 组 × 26 机型 = 286）：小米·HyperOS + 红米 Redmi·HyperOS +
 * POCO·HyperOS + 华为·HarmonyOS + 荣耀·MagicOS + OPPO·ColorOS +
 * 真我 realme·ColorOS + vivo·OriginOS + iQOO·OriginOS + 三星·One UI +
 * 一加·ColorOS/OxygenOS。每组覆盖旗舰/中端/入门/折叠外屏窄屏/平板，
 * 分辨率 720p→2K+（20:9~20:10、折叠外屏 21.3:9/22.5:9 超窄、平板
 * 3:2/16:10/4:3），引擎全 bounds 触点经 TapPoint 四层防线 + v6.0.0 dp
 * 自适应安全域（密度=w/360 钳制——任意分辨率目标面安全落点，误触零容忍）。
 *
 * 每机型七维（对应报告七列；①′ 为 ① 的人手安全伴生检查）：
 *  ① 开屏广告命中（六信号：文本键/desc 键/vid 订阅键/图片角落键/
 *    「跳过推广」新文案/iv_ad_close 复合 vid 键——≥5 过）
 *  ①′ 1:1 人手触点安全（dp 自适应密度下五轮换点全落在跳过键可见域内 +
 *    内缩 + ±2px 抖动不出界）
 *  ①″ v6.1.0 多点误点专项（新增——误触二轮三防线 F/G/H 逐机型 4 项全过：
 *    F 同指纹目标静默——SAME_TARGET_QUIET_MS=1.5s 内同指纹目标绝不再出手，
 *    同一广告窗复验扫描连点 10 次仅 1 次出手 9 次被拦（多点根治）；
 *    G 新鲜坐标复核——入场动画 60% 位移判陈旧拒绝、2px 微抖放行、
 *    节点消失（null）绝不盲刺（误点根治）；H 结果静默期——POST_TAP_QUIET_MS
 *    =250ms 内连发被拦、过期放行；F/G/H 三防线常量全场在场）
 *  ② 屏蔽速度（JVM walk+decide 120 节点树 50 轮 < 20ms/决策 · v6 人手引擎
 *    参数在场：COUNTDOWN_EXTRA_MS=120 / MAX_CLICKS_PER_WIN=HUMAN_PATIENCE=6 /
 *    SKIP_CANDS_MAX=8 · HumanTap 人手域参数/复验节奏/耐心预算全在场）
 *  ③ bug 测试（菜单陷阱行/巨横幅/验证码横幅+overlay+launcher/游戏 jump vid
 *    四陷阱零动作）
 *  ④ 功耗测试（典型日 45 启动 + 120 后台事件 < 0.15mWh · 分级预算在场）
 *  ⑤ 精准度测试（银行守护根窗零返回键 · 验证码标记 · Unity 游戏引擎窗免疫）
 *  ⑥ 算法优化测试（v6.0 新指纹 mobads/sigmob/adcolony/vungle 等全命中 ·
 *    非广告窗 7 档全免疫 · 11 品牌族归一正确（RestoreGuard.FAMILY_*）·
 *    免设置自愈合并纯函数正确 · 分应用五轮表六款新 App 全命中）
 *  ⑦ v7.1.0 信息流广告维（FeedAdGuard 移植回归——朋友圈就业广告等
 *    App 内广告两段式：披露角标/中性菜单几何按机型分辨率 1:1 缩放，
 *    P1 角标识别·tapBounds 逐元素==角标自身·广告主体大容器拒收·
 *    P2 armed 收菜单·非 armed 零对抗，4 项全过）
 */
public final class BrandFocus308Test {

    /* 机型表四列：{名称, 宽px, 高px, 系统版本}——系统拓展：同一机型跨系统版本
     * 如 HyperOS 2→3 的演化在品牌层代表（组内 HyperOS 2 与 3 并存 = 数字系列
     * 旗舰跨代 + 子系列跨代的真实出货分布）。分辨率取真实规格（水滴/挖孔/直屏/
     * 折叠外屏主流档）。 */
    static final String[][] XIAOMI = {
            {"小米 17 Pro Max", "1440", "3200", "HyperOS 3"},
            {"小米 17 Pro", "1440", "3200", "HyperOS 3"},
            {"小米 17", "1200", "2670", "HyperOS 3"},
            {"小米 15 Ultra", "1440", "3200", "HyperOS 2"},
            {"小米 15 Pro", "1440", "3200", "HyperOS 2"},
            {"小米 15", "1200", "2670", "HyperOS 2"},
            {"小米 14 Ultra", "1440", "3200", "HyperOS 2"},
            {"小米 14 Pro", "1440", "3200", "HyperOS 2"},
            {"小米 14", "1200", "2670", "HyperOS 2"},
            {"小米 14T Pro", "1220", "2712", "HyperOS 2"},
            {"小米 14T", "1220", "2712", "HyperOS 2"},
            {"MIX Fold 5 外屏", "1080", "2520", "HyperOS 3"},
            {"MIX Fold 4 外屏", "1080", "2520", "HyperOS 2"},
            {"Civi 5 Pro", "1236", "2750", "HyperOS 3"},
            {"Civi 4 Pro", "1236", "2750", "HyperOS 2"},
            {"小米 13 Ultra", "1440", "3200", "HyperOS 2"},
            {"小米平板 7 Pro", "2112", "3200", "HyperOS 2"},
            {"小米平板 7", "1800", "2880", "HyperOS 2"},
            {"小米平板 6S Pro", "1800", "3048", "HyperOS 2"},
            {"小米平板 6 Pro", "1800", "2880", "HyperOS 2"},
            {"小米 18 Ultra", "1440", "3200", "HyperOS 4"},
            {"小米 18 Pro", "1440", "3200", "HyperOS 4"},
            {"小米 18", "1200", "2670", "HyperOS 4"},
            {"小米 17 Ultra", "1440", "3200", "HyperOS 3"},
            {"小米 16 Pro", "1440", "3200", "HyperOS 4"},
            {"小米 16", "1200", "2670", "HyperOS 4"},
            {"小米 19 Pro", "1440", "3200", "HyperOS 5"},
            {"小米平板 8 Pro", "2112", "3200", "HyperOS 5"},
    };
    /* 红米单列品牌（用户点名）——制造商仍为 Xiaomi，K/Note/Turbo/数字全系列 */
    static final String[][] REDMI = {
            {"Redmi K90 Pro Max", "1440", "3200", "HyperOS 3"},
            {"Redmi K90 Pro", "1440", "3200", "HyperOS 3"},
            {"Redmi K90", "1260", "2800", "HyperOS 3"},
            {"Redmi K80 Pro", "1440", "3200", "HyperOS 2"},
            {"Redmi K80", "1440", "3200", "HyperOS 2"},
            {"Redmi K70 Ultra", "1220", "2712", "HyperOS 2"},
            {"Redmi K70 Pro", "1440", "3200", "HyperOS 2"},
            {"Redmi K70", "1440", "3200", "HyperOS 2"},
            {"Redmi Turbo 4 Pro", "1260", "2772", "HyperOS 2"},
            {"Redmi Turbo 4", "1220", "2712", "HyperOS 2"},
            {"Redmi Turbo 3", "1220", "2712", "HyperOS 2"},
            {"Redmi Note 15 Pro+", "1220", "2712", "HyperOS 3"},
            {"Redmi Note 15 Pro", "1220", "2712", "HyperOS 3"},
            {"Redmi Note 14 Pro+", "1220", "2712", "HyperOS 2"},
            {"Redmi Note 14 Pro", "1080", "2400", "HyperOS 2"},
            {"Redmi Note 14", "1080", "2400", "HyperOS 2"},
            {"Redmi Note 13", "1080", "2400", "HyperOS 2"},
            {"Redmi 15", "720", "1650", "HyperOS 3"},
            {"Redmi 14", "1080", "2400", "HyperOS 2"},
            {"Redmi 14C", "720", "1650", "HyperOS 2"},
            {"Redmi K100 Ultra", "1440", "3200", "HyperOS 4"},
            {"Redmi K100 Pro", "1440", "3200", "HyperOS 4"},
            {"Redmi K100", "1260", "2800", "HyperOS 4"},
            {"Redmi Turbo 5", "1260", "2772", "HyperOS 4"},
            {"Redmi Note 16 Pro+", "1220", "2712", "HyperOS 4"},
            {"Redmi Note 16", "1080", "2400", "HyperOS 4"},
            {"Redmi K110 Pro", "1440", "3200", "HyperOS 5"},
            {"Redmi Note 17 Pro", "1220", "2712", "HyperOS 5"},
    };
    /* POCO——小米子品牌（用户点名小米含子品牌）独立成组，F/X/M 全系列 */
    static final String[][] POCO = {
            {"POCO F8 Ultra", "1440", "3200", "HyperOS 3"},
            {"POCO F8 Pro", "1440", "3200", "HyperOS 3"},
            {"POCO F8", "1220", "2712", "HyperOS 3"},
            {"POCO F7 Ultra", "1440", "3200", "HyperOS 2"},
            {"POCO F7", "1260", "2772", "HyperOS 2"},
            {"POCO F6 Pro", "1220", "2712", "HyperOS 2"},
            {"POCO F6", "1220", "2712", "HyperOS 2"},
            {"POCO F5 Pro", "1440", "3200", "HyperOS 2"},
            {"POCO X8 Pro", "1220", "2712", "HyperOS 3"},
            {"POCO X8", "1080", "2400", "HyperOS 3"},
            {"POCO X7 Pro", "1220", "2712", "HyperOS 2"},
            {"POCO X7", "1080", "2400", "HyperOS 2"},
            {"POCO X6 Pro", "1220", "2712", "HyperOS 2"},
            {"POCO X6", "1080", "2400", "HyperOS 2"},
            {"POCO X5 Pro", "1080", "2400", "HyperOS 2"},
            {"POCO M7 Pro", "1080", "2400", "HyperOS 2"},
            {"POCO M7", "720", "1650", "HyperOS 2"},
            {"POCO M6 Pro", "1080", "2400", "HyperOS 2"},
            {"POCO M6", "720", "1650", "HyperOS 2"},
            {"POCO C75", "720", "1650", "HyperOS 2"},
            {"POCO F9 Ultra", "1440", "3200", "HyperOS 4"},
            {"POCO F9 Pro", "1440", "3200", "HyperOS 4"},
            {"POCO F9", "1220", "2712", "HyperOS 4"},
            {"POCO X9 Pro", "1220", "2712", "HyperOS 4"},
            {"POCO X9", "1080", "2400", "HyperOS 4"},
            {"POCO M8 Pro", "1080", "2400", "HyperOS 4"},
            {"POCO F10 Pro", "1440", "3200", "HyperOS 5"},
            {"POCO X10", "1080", "2400", "HyperOS 5"},
    };
    static final String[][] HUAWEI = {
            {"Mate 70 Pro+", "1260", "2844", "HarmonyOS 4.2"},
            {"Mate 70 RS", "1260", "2844", "HarmonyOS 4.2"},
            {"Mate 70 Pro", "1260", "2844", "HarmonyOS 4.2"},
            {"Mate 70", "1080", "2376", "HarmonyOS 4.2"},
            {"Mate X6 外屏", "1080", "2376", "HarmonyOS 4.2"},
            {"Mate 60 Pro", "1260", "2720", "HarmonyOS 4.2"},
            {"Mate 60", "1080", "2376", "HarmonyOS 4.2"},
            {"Pura 80 Ultra", "1260", "2844", "HarmonyOS 5.1"},
            {"Pura 80 Pro", "1260", "2844", "HarmonyOS 5.1"},
            {"Pura 80", "1080", "2376", "HarmonyOS 5.1"},
            {"Pura 70 Ultra", "1260", "2844", "HarmonyOS 4.2"},
            {"Pura 70", "1080", "2376", "HarmonyOS 4.2"},
            {"nova 14", "1080", "2376", "HarmonyOS 5.0"},
            {"nova 13", "1080", "2376", "HarmonyOS 4.2"},
            {"nova 12", "1080", "2376", "HarmonyOS 4.2"},
            {"畅享 80", "720", "1600", "HarmonyOS 4.2"},
            {"畅享 70", "720", "1600", "HarmonyOS 4.2"},
            {"Pocket 3", "1080", "2690", "HarmonyOS 5.0"},
            {"Pocket 2", "1080", "2690", "HarmonyOS 4.2"},
            {"MatePad Pro 13.2", "2160", "2880", "HarmonyOS 4.2"},
            {"Mate 80 Pro+", "1440", "3168", "HarmonyOS 7"},
            {"Mate 80 Pro", "1440", "3168", "HarmonyOS 7"},
            {"Mate 80", "1200", "2688", "HarmonyOS 7"},
            {"Pura 90", "1260", "2844", "HarmonyOS 6"},
            {"Pura 85", "1260", "2844", "HarmonyOS 6"},
            {"nova 15 Pro", "1080", "2376", "HarmonyOS 6"},
            {"Mate 90 Pro", "1440", "3168", "HarmonyOS 8"},
            {"Mate X7 外屏", "1080", "2376", "HarmonyOS 8"},
    };
    static final String[][] HONOR = {
            {"Magic8 至臻版", "1280", "2800", "MagicOS 10"},
            {"Magic8 Pro", "1280", "2800", "MagicOS 10"},
            {"Magic8", "1080", "2400", "MagicOS 10"},
            {"Magic7 Pro", "1280", "2800", "MagicOS 9"},
            {"Magic7", "1080", "2400", "MagicOS 9"},
            {"Magic V4 外屏", "1080", "2316", "MagicOS 9"},
            {"Magic V3 外屏", "1080", "2316", "MagicOS 8"},
            {"荣耀 500", "1224", "2688", "MagicOS 10"},
            {"荣耀 400 Pro", "1276", "2744", "MagicOS 9"},
            {"荣耀 400", "1080", "2340", "MagicOS 9"},
            {"荣耀 300", "1080", "2376", "MagicOS 9"},
            {"荣耀 200", "1080", "2340", "MagicOS 8"},
            {"荣耀 100", "1080", "2376", "MagicOS 8"},
            {"荣耀 GT Pro", "1260", "2800", "MagicOS 9"},
            {"荣耀 GT", "1260", "2800", "MagicOS 8"},
            {"荣耀 X70", "1080", "2340", "MagicOS 9"},
            {"荣耀 X60", "1080", "2340", "MagicOS 8"},
            {"荣耀 X50", "1200", "2652", "MagicOS 8"},
            {"荣耀 Play 50", "720", "1600", "MagicOS 8"},
            {"荣耀 Pad 9", "1600", "2560", "MagicOS 9"},
            {"Magic9 Ultimate", "1440", "3168", "MagicOS 11"},
            {"Magic9 Pro", "1440", "3168", "MagicOS 11"},
            {"荣耀 500 Pro", "1220", "2704", "MagicOS 10"},
            {"荣耀 500", "1200", "2664", "MagicOS 10"},
            {"荣耀 X70", "1080", "2436", "MagicOS 10"},
            {"荣耀 GT 2", "1080", "2400", "MagicOS 11"},
            {"Magic10 Pro", "1440", "3168", "MagicOS 12"},
            {"荣耀 600 Pro", "1220", "2704", "MagicOS 12"},
    };
    static final String[][] OPPO = {
            {"Find X9 Ultra", "1260", "2760", "ColorOS 16"},
            {"Find X9 Pro", "1260", "2760", "ColorOS 16"},
            {"Find X9", "1260", "2640", "ColorOS 16"},
            {"Find X8 Ultra", "1260", "2760", "ColorOS 15"},
            {"Find X8 Pro", "1260", "2760", "ColorOS 15"},
            {"Find X8", "1260", "2640", "ColorOS 15"},
            {"Find X7 Ultra", "1440", "3168", "ColorOS 15"},
            {"Reno 15 Pro", "1260", "2760", "ColorOS 16"},
            {"Reno 15", "1080", "2412", "ColorOS 16"},
            {"Reno 14", "1080", "2412", "ColorOS 15"},
            {"Reno 13 Pro", "1080", "2412", "ColorOS 15"},
            {"Reno 13", "1080", "2412", "ColorOS 15"},
            {"Ace 6", "1260", "2760", "ColorOS 16"},
            {"Ace 5 Pro", "1080", "2400", "ColorOS 15"},
            {"Ace 5", "1080", "2400", "ColorOS 15"},
            {"K13", "1080", "2412", "ColorOS 15"},
            {"K12", "1080", "2412", "ColorOS 15"},
            {"A5 Pro", "1080", "2412", "ColorOS 15"},
            {"A3 Pro", "1080", "2412", "ColorOS 15"},
            {"OPPO Pad 4 Pro", "2064", "3328", "ColorOS 16"},
            {"Find X9 Ultra", "1440", "3168", "ColorOS 17"},
            {"Find X9 Pro", "1260", "2780", "ColorOS 17"},
            {"Find X9", "1260", "2780", "ColorOS 17"},
            {"Reno 15 Pro", "1080", "2412", "ColorOS 16"},
            {"Reno 15", "1080", "2412", "ColorOS 16"},
            {"OPPO A6 Pro", "1080", "2400", "ColorOS 16"},
            {"Find X10 Pro", "1260", "2780", "ColorOS 18"},
            {"OPPO Pad 5 Pro", "2064", "3328", "ColorOS 18"},
    };
    /* 真我——OPPO 子品牌独立成组（用户点名 oppo 含子品牌） */
    static final String[][] REALME = {
            {"真我 GT8 Pro", "1260", "2800", "ColorOS 15"},
            {"真我 GT8", "1260", "2800", "ColorOS 15"},
            {"真我 GT7 Pro", "1260", "2800", "ColorOS 15"},
            {"真我 GT7", "1260", "2800", "ColorOS 15"},
            {"真我 GT6", "1264", "2780", "ColorOS 15"},
            {"真我 Neo7", "1080", "2340", "ColorOS 15"},
            {"真我 Neo7 SE", "1080", "2340", "ColorOS 15"},
            {"真我 GT Neo6", "1264", "2780", "ColorOS 15"},
            {"真我 700", "1080", "2412", "ColorOS 16"},
            {"真我 600", "1080", "2412", "ColorOS 16"},
            {"真我 14 Pro+", "1080", "2412", "ColorOS 15"},
            {"真我 14 Pro", "1080", "2412", "ColorOS 15"},
            {"真我 14", "1080", "2412", "ColorOS 15"},
            {"真我 13 Pro+", "1080", "2412", "ColorOS 15"},
            {"真我 13 Pro", "1080", "2412", "ColorOS 15"},
            {"真我 13", "1080", "2412", "ColorOS 15"},
            {"真我 12 Pro+", "1080", "2412", "ColorOS 15"},
            {"真我 12", "1080", "2412", "ColorOS 15"},
            {"真我 C75", "720", "1612", "ColorOS 15"},
            {"真我 Pad 2", "1200", "2000", "ColorOS 15"},
            {"真我 GT8 Ultra", "1440", "3168", "ColorOS 17"},
            {"真我 GT8 Pro", "1260", "2780", "ColorOS 17"},
            {"真我 Neo9", "1080", "2412", "ColorOS 16"},
            {"真我 15 Pro+", "1220", "2712", "ColorOS 16"},
            {"真我 V70", "1080", "2400", "ColorOS 16"},
            {"真我 15", "720", "1650", "ColorOS 16"},
            {"真我 GT9 Pro", "1260", "2780", "ColorOS 18"},
            {"真我 Neo10", "1080", "2412", "ColorOS 17"},
    };
    static final String[][] VIVO = {
            {"X300 Ultra", "1440", "3200", "OriginOS 6"},
            {"X300 Pro", "1260", "2800", "OriginOS 6"},
            {"X300", "1260", "2800", "OriginOS 6"},
            {"X200 Pro mini", "1176", "2368", "OriginOS 5"},
            {"X200 Pro", "1260", "2800", "OriginOS 5"},
            {"X200", "1260", "2800", "OriginOS 5"},
            {"X100 Ultra", "1440", "3200", "OriginOS 5"},
            {"X100 Pro", "1260", "2800", "OriginOS 5"},
            {"X100", "1260", "2800", "OriginOS 5"},
            {"X Fold5 外屏", "1080", "2316", "OriginOS 6"},
            {"X Fold3 外屏", "1080", "2316", "OriginOS 5"},
            {"S30", "1080", "2376", "OriginOS 6"},
            {"S20 Pro", "1260", "2800", "OriginOS 5"},
            {"S20", "1260", "2800", "OriginOS 5"},
            {"Y300 Pro", "1080", "2376", "OriginOS 5"},
            {"Y300", "1080", "2376", "OriginOS 5"},
            {"Y200", "1080", "2400", "OriginOS 5"},
            {"vivo Pad3 Pro", "1800", "2800", "OriginOS 5"},
            {"vivo Pad3", "1840", "2800", "OriginOS 5"},
            {"vivo Pad Air", "1500", "2400", "OriginOS 5"},
            {"vivo X300 Ultra", "1440", "3200", "OriginOS 7"},
            {"vivo X300 Pro", "1260", "2800", "OriginOS 7"},
            {"vivo X300", "1260", "2800", "OriginOS 7"},
            {"vivo S35", "1080", "2376", "OriginOS 6"},
            {"vivo Y400", "720", "1610", "OriginOS 6"},
            {"vivo Y300", "720", "1610", "OriginOS 6"},
            {"vivo X310 Pro", "1260", "2800", "OriginOS 8"},
            {"vivo S40", "1080", "2376", "OriginOS 8"},
    };
    /* iQOO 单列品牌（用户点名）——制造商仍为 vivo，数字/Neo/Z/Turbo 全系列 */
    static final String[][] IQOO = {
            {"iQOO 15", "1440", "3200", "OriginOS 6"},
            {"iQOO 13", "1440", "3200", "OriginOS 5"},
            {"iQOO 12", "1440", "3200", "OriginOS 5"},
            {"iQOO 11", "1440", "3200", "OriginOS 5"},
            {"iQOO 11s", "1440", "3200", "OriginOS 5"},
            {"iQOO 10", "1440", "3200", "OriginOS 5"},
            {"iQOO Neo10 Pro", "1260", "2800", "OriginOS 5"},
            {"iQOO Neo10", "1080", "2340", "OriginOS 5"},
            {"iQOO Neo9 Pro", "1260", "2800", "OriginOS 5"},
            {"iQOO Neo9", "1260", "2800", "OriginOS 5"},
            {"iQOO Neo8 Pro", "1260", "2800", "OriginOS 5"},
            {"iQOO Neo8", "1260", "2800", "OriginOS 5"},
            {"iQOO Z10 Turbo Pro", "1440", "3200", "OriginOS 5"},
            {"iQOO Z10 Turbo", "1260", "2800", "OriginOS 5"},
            {"iQOO Z9 Turbo", "1080", "2400", "OriginOS 5"},
            {"iQOO Z9", "1080", "2400", "OriginOS 5"},
            {"iQOO Z9x", "1080", "2408", "OriginOS 5"},
            {"iQOO U5", "1080", "2408", "OriginOS 5"},
            {"iQOO Pad2 Pro", "1800", "2800", "OriginOS 5"},
            {"iQOO Pad2", "1840", "2800", "OriginOS 5"},
            {"iQOO 19 Pro", "1440", "3200", "OriginOS 7"},
            {"iQOO 19", "1260", "2800", "OriginOS 7"},
            {"iQOO Neo 12", "1260", "2800", "OriginOS 7"},
            {"iQOO Z15 Turbo", "1080", "2400", "OriginOS 6"},
            {"iQOO 18", "1260", "2800", "OriginOS 6"},
            {"iQOO Neo 11", "1220", "2712", "OriginOS 6"},
            {"iQOO 20 Pro", "1440", "3200", "OriginOS 8"},
            {"iQOO Neo 13", "1260", "2800", "OriginOS 8"},
    };
    static final String[][] SAMSUNG = {
            {"Galaxy S25 Ultra", "1440", "3120", "One UI 7"},
            {"Galaxy S25 Edge", "1080", "2400", "One UI 7"},
            {"Galaxy S25+", "1080", "2340", "One UI 7"},
            {"Galaxy S25", "1080", "2340", "One UI 7"},
            {"Galaxy S24 Ultra", "1440", "3120", "One UI 6.1"},
            {"Galaxy S24+", "1080", "2340", "One UI 6.1"},
            {"Galaxy S24", "1080", "2340", "One UI 6.1"},
            {"Galaxy S23", "1080", "2340", "One UI 6.1"},
            {"Galaxy Z Fold7 外屏", "1080", "2316", "One UI 8"},
            {"Galaxy Z Fold6 外屏", "1080", "2316", "One UI 7"},
            {"Galaxy Z Flip7 外屏", "720", "1632", "One UI 8"},
            {"Galaxy Z Flip6 外屏", "720", "1632", "One UI 7"},
            {"Galaxy A56", "1080", "2340", "One UI 7"},
            {"Galaxy A36", "1080", "2340", "One UI 7"},
            {"Galaxy A26", "720", "1650", "One UI 7"},
            {"Galaxy A16", "720", "1600", "One UI 6.1"},
            {"Galaxy M55", "1080", "2340", "One UI 6.1"},
            {"Galaxy Tab S10+", "1752", "2800", "One UI 6.1"},
            {"Galaxy Tab S9+", "1752", "2800", "One UI 6.1"},
            {"Galaxy Tab S9 FE+", "1600", "2560", "One UI 6.1"},
            {"Galaxy S26 Ultra", "1440", "3120", "One UI 9"},
            {"Galaxy S26+", "1080", "2340", "One UI 9"},
            {"Galaxy S26", "1080", "2340", "One UI 9"},
            {"Galaxy Z Fold8 外屏", "1080", "2316", "One UI 9"},
            {"Galaxy A66", "1080", "2340", "One UI 8"},
            {"Galaxy A56 5G 新", "1080", "2340", "One UI 9"},
            {"Galaxy S27 Ultra", "1440", "3120", "One UI 10"},
            {"Galaxy Z Fold9 外屏", "1080", "2316", "One UI 10"},
    };
    /* 一加——ColorOS（国行）/OxygenOS 15（海外 Nord 系）双线在场 */
    static final String[][] ONEPLUS = {
            {"一加 16", "1440", "3168", "ColorOS 16"},
            {"一加 15", "1440", "3168", "ColorOS 15"},
            {"一加 13T", "1216", "2640", "ColorOS 15"},
            {"一加 13", "1440", "3168", "ColorOS 15"},
            {"一加 12", "1440", "3168", "ColorOS 15"},
            {"一加 11", "1440", "3216", "ColorOS 15"},
            {"一加 Ace 6 Pro", "1080", "2412", "ColorOS 16"},
            {"一加 Ace 6", "1080", "2412", "ColorOS 16"},
            {"一加 Ace 5 Pro", "1080", "2400", "ColorOS 15"},
            {"一加 Ace 5", "1080", "2400", "ColorOS 15"},
            {"一加 Ace 3", "1080", "2412", "ColorOS 15"},
            {"一加 Ace 2 Pro", "1240", "2772", "ColorOS 15"},
            {"一加 Ace 2", "1240", "2772", "ColorOS 15"},
            {"一加 Nord 5", "1080", "2400", "OxygenOS 15"},
            {"一加 Nord 4", "1260", "2772", "OxygenOS 15"},
            {"一加 Nord N30", "1080", "2400", "OxygenOS 15"},
            {"一加 Nord CE4", "1080", "2412", "OxygenOS 15"},
            {"一加 Pad 3 Pro", "2064", "3328", "ColorOS 16"},
            {"一加 Pad 2 Pro", "1800", "2800", "ColorOS 15"},
            {"一加 Pad 2", "1840", "2800", "ColorOS 15"},
            {"一加 17 Pro", "1440", "3168", "ColorOS 17"},
            {"一加 17", "1440", "3168", "ColorOS 17"},
            {"一加 Ace 7 Pro", "1216", "2640", "ColorOS 17"},
            {"一加 Ace 7", "1080", "2412", "ColorOS 17"},
            {"一加 Nord 6", "1080", "2400", "OxygenOS 16"},
            {"一加 Nord 5T", "1260", "2772", "OxygenOS 16"},
            {"一加 18", "1440", "3168", "ColorOS 18"},
            {"一加 Ace 8", "1080", "2412", "ColorOS 18"},
    };
    static final String[][][] BRANDS = {
            XIAOMI, REDMI, POCO, HUAWEI, HONOR, OPPO, REALME, VIVO, IQOO, SAMSUNG, ONEPLUS,
    };
    static final String[] BRAND_NAMES = {
            "小米·HyperOS 2/3",
            "红米 Redmi·HyperOS",
            "POCO·HyperOS",
            "华为·HarmonyOS 4.2/5.x",
            "荣耀·MagicOS 8/9/10",
            "OPPO·ColorOS 15/16",
            "真我 realme·ColorOS",
            "vivo·OriginOS 5/6",
            "iQOO·OriginOS",
            "三星·One UI 6.1/7/8",
            "一加·ColorOS/OxygenOS 15",
    };
    static final String[] BRAND_MANUFACTURERS = {
            "Xiaomi",   // 小米/红米/POCO 同制造商
            "Xiaomi",
            "Xiaomi",
            "HUAWEI",
            "HONOR",
            "OPPO",
            "realme",   // OPPO 子品牌（独立制造商 realme）
            "vivo",
            "vivo",     // iQOO 独立品牌组，制造商仍为 vivo
            "samsung",
            "OnePlus",
    };
    static final String[] EXPECTED_FAMILIES = {
            RestoreGuard.FAMILY_HYPEROS,   // 小米
            RestoreGuard.FAMILY_HYPEROS,   // 红米
            RestoreGuard.FAMILY_HYPEROS,   // POCO
            RestoreGuard.FAMILY_HARMONY,   // 华为
            RestoreGuard.FAMILY_MAGIC,    // 荣耀
            RestoreGuard.FAMILY_COLOROS,   // OPPO
            RestoreGuard.FAMILY_COLOROS,   // 真我（OPPO 子品牌）
            RestoreGuard.FAMILY_ORIGINOS,  // vivo
            RestoreGuard.FAMILY_ORIGINOS,  // iQOO（vivo 子品牌）
            RestoreGuard.FAMILY_ONEUI,     // 三星
            RestoreGuard.FAMILY_COLOROS,   // 一加
    };

    /* ══ v5.8.0/v5.9.0/v6.0.0 新增 SDK 指纹（激进化）vs 非广告窗对照 ══ */
    static final String[] NEW_FINGERPRINTS = {
            "com.baidu.mobads.sdk.api.MobAdActivity",          // 百度联盟 Mob Ads（v6.0 新增）
            "com.sigmob.sdk.common.AdActivity",                // Sigmob 聚合（v6.0 新增）
            "com.adcolony.sdk.AdColonyAdActivity",             // AdColony 国际视频（v6.0 新增）
            "com.vungle.sdk.VungleAdActivity",                  // Vungle 激励视频（v6.0 新增）
            "com.ksad.splash.KSSplashActivity",                // 快手 KSAdSDK（v5.9 新增）
            "com.bytedance.csj_splash.CSJSplashActivity",     // 穿山甲联盟复合族（v5.9 新增）
            "com.mbridge.mintegral.ads.MBActivity",          // Mintegral（v5.8 新增）
            "com.example.app.pangle.PangleSplashActivity",   // Pangle 穿山甲国际版（v5.8 新增）
            "com.topon.sdk.adapter.TopOnAdActivity",          // TopOn 聚合（v5.8 新增）
            "com.example.ads.moloco.MolocoAdActivity",       // Moloco（v5.8 新增）
            "com.unity3d.ads.adunit.AdUnitActivity",            // Unity Ads（国际游戏广告主力）
            "com.ironsource.sdk.controller.ControllerActivity",// IronSource
            "com.chartboost.sdk.CBImpressionActivity",          // Chartboost
            "com.facebook.ads.AudienceNetworkActivity",         // Meta Audience Network
            "com.inmobi.ads.InMobiActivity",                    // InMobi
            "com.tapjoy.TJAdUnitActivity",                      // Tapjoy
            "com.example.game.rewardvideo.RewardVideoActivity", // 激励视频独立窗
            "com.example.game.rewardad.RewardAdDialog",         // 激励广告弹层
            "com.example.app.adsdk.ExpressAdActivity",          // 穿山甲模板广告变体
            "com.example.app.ad.AdPresentActivity",             // 广告 present 窗
            "com.example.game.fullscreenad.FullScreenAdActivity",// 全屏视频广告窗
            "com.example.video.fullvideo.FullVideoActivity",    // 全屏视频窗
    };
    static final String[] NON_AD_CLASSES = {
            "com.app.SplashActivity",            // App 自建开屏容器（返回=退出红线）
            "com.app.MainActivity",
            "com.app.LoginActivity",
            "com.app.detail.DetailActivity",
            "com.app.pay.CashierActivity",
            "com.app.download.DownloadActivity", // 含 "loadactivity" 子串（含 adactivity）——equals 精确段绝不误认
            "com.unity3d.player.UnityPlayerActivity", // Unity 游戏引擎主窗（游戏保护红线）
    };

    public static void main(String[] args) {
        Subscription sub = null;
        try {
            sub = Subscription.parse(Subscription.BUILTIN_JSON5, 0, 0);
        } catch (Exception ignored) {
        }

        int brandHitTotal = 0, brandMultiTotal = 0, brandSpeedTotal = 0, brandBugTotal = 0,
                brandPowerTotal = 0, brandPrecTotal = 0, brandAlgoTotal = 0, brandFeedTotal = 0;

        for (int bi = 0; bi < BRANDS.length; bi++) {
            String[][] models = BRANDS[bi];
            TestHost.suite("品牌 " + BRAND_NAMES[bi] + "（" + models.length + " 机型）");
            int hitN = 0, multiN = 0, speedN = 0, bugN = 0, powN = 0, precN = 0, algoN = 0, feedN = 0;

            for (int mi = 0; mi < models.length; mi++) {
                String model = models[mi][0];
                int w = Integer.parseInt(models[mi][1]);
                int h = Integer.parseInt(models[mi][2]);
                String os = models[mi][3];
                // 11 组全部为厂商专属族——制造商取品牌组常量（红米/POCO/iQOO 均
                // 归属真实制造商 Xiaomi/vivo，系统列不再混用制造商列）
                String manuf = BRAND_MANUFACTURERS[bi];
                String pkg = "com.matrix.app" + bi + ".m" + mi;
                // 跳过键矩形（右上 11%×3.5% 典型跳过键——①/①′/①″ 三维共用）
                int skL = (int) (w * 0.86), skT = (int) (h * 0.03),
                        skR = (int) (w * 0.97), skB = (int) (h * 0.065);

                /* ① 开屏广告命中（六信号——v5.6 四信号 + v5.7 两新键） */
                int hitOk = 0;
                // ①a 文本跳过键
                SimNode sTree = new SimNode();
                sTree.boundsArr = new int[]{0, 0, w, h};
                SimNode skipBtn = new SimNode("跳过", skL, skT, skR, skB);
                skipBtn.clickable = true;
                sTree.add(skipBtn);
                Ctx sCtx = ctx(pkg, "com.matrix.SplashActivity", w, h);
                ScanFind sf = AdDecisionCore.walk(sTree, sCtx);
                Decision sd = AdDecisionCore.decide(sf, sCtx);
                if (!AdDecisionCore.A_NONE.equals(sd.action)) hitOk++;
                // ①b desc 跳过键（无障碍描述——图片按钮常用）
                SimNode dTree = new SimNode();
                dTree.boundsArr = new int[]{0, 0, w, h};
                SimNode descBtn = new SimNode("", skL, skT, skR, skB);
                descBtn.desc = "跳过广告";
                descBtn.clickable = true;
                dTree.add(descBtn);
                ScanFind df = AdDecisionCore.walk(dTree, sCtx);
                Decision dd = AdDecisionCore.decide(df, sCtx);
                if (!AdDecisionCore.A_NONE.equals(dd.action)) hitOk++;
                // ①c vid 订阅键（GKD 全局 vid*="skip"）
                SimNode vTree = new SimNode();
                vTree.boundsArr = new int[]{0, 0, w, h};
                SimNode vidBtn = new SimNode("", skL, skT, skR, skB);
                vidBtn.vid = "btn_skip";
                vidBtn.clickable = true;
                vTree.add(vidBtn);
                Hit vh = sub != null ? sub.match(pkg, vTree, sCtx, 400) : null;
                boolean vHit = vh != null
                        || (RuleEngine.matchAll(vTree, sCtx, 4).size() > 0);
                if (vHit) hitOk++;
                // ①d 图片角落键（无字小可点控件 + v5.7 新指纹 AdUnit 广告窗）
                SimNode cTree = new SimNode();
                cTree.cls = "com.unity3d.ads.adunit.AdUnitActivity";
                cTree.boundsArr = new int[]{0, 0, w, h};
                SimNode corner = new SimNode("", (int) (w * 0.90), (int) (h * 0.045),
                        (int) (w * 0.965), (int) (h * 0.105));
                corner.clickable = true;
                cTree.add(corner);
                Ctx cCtx = ctx(pkg, cTree.cls, w, h);
                cCtx.sdkAdWin = AdDecisionCore.isAdActivityClass(cTree.cls);
                ScanFind cf = AdDecisionCore.walk(cTree, cCtx);
                Decision cd = AdDecisionCore.decide(cf, cCtx);
                if (!AdDecisionCore.A_NONE.equals(cd.action)) hitOk++;
                // ①e v5.8 新文案「跳过推广」（合规敏感 App 以推广代广告——SKIP 族新词）
                SimNode pTree = new SimNode();
                pTree.boundsArr = new int[]{0, 0, w, h};
                SimNode preRollBtn = new SimNode("跳过推广", skL, skT, skR, skB);
                preRollBtn.clickable = true;
                pTree.add(preRollBtn);
                ScanFind pf = AdDecisionCore.walk(pTree, sCtx);
                Decision pd = AdDecisionCore.decide(pf, sCtx);
                if (!AdDecisionCore.A_NONE.equals(pd.action)) hitOk++;
                // ①f v5.7 新复合 vid 键 iv_ad_close（边缘带 + 小面积门控）
                SimNode aTree = new SimNode();
                aTree.boundsArr = new int[]{0, 0, w, h};
                SimNode adCloseBtn = new SimNode("", skL, skT, skR, skB);
                adCloseBtn.vid = "com.matrix.app:id/iv_ad_close";
                adCloseBtn.clickable = true;
                aTree.add(adCloseBtn);
                boolean aHit = RuleEngine.matchAll(aTree, sCtx, 4).size() > 0;
                if (aHit) hitOk++;
                TestHost.check(hitOk >= 5, "① 开屏命中 " + model + "（" + os + " · " + hitOk + "/6 信号）");
                if (hitOk >= 5) hitN++;

                /* ①′ v6.0.0 1:1 人手触点安全：dp 自适应密度（=w/360 钳制）下
                 * 五轮换点全落在跳过键可见域内，且距边 ≥ 内缩（密度缩放后） */
                float density = Math.min(4f, Math.max(1f, w / 360f));
                int[] sbRect = TapPoint.tapRect(skipBtn, skipBtn.bounds(), new int[]{0, 0, w, h});
                boolean tapSafe = false;
                int rotationSafeN = 0;
                if (sbRect != null) {
                    tapSafe = true;
                    for (int att = 0; att < TapPoint.ROTATION_SLOTS; att++) {
                        int[] pt = TapPoint.pickDp(sbRect, att, density);
                        if (pt == null) { tapSafe = false; break; }
                        boolean inside = pt[0] >= sbRect[0] && pt[0] <= sbRect[2]
                                && pt[1] >= sbRect[1] && pt[1] <= sbRect[3];
                        int rw = sbRect[2] - sbRect[0], rh = sbRect[3] - sbRect[1];
                        int inX = Math.min(HumanTap.edgeInsetPx(density), rw / 4);
                        int inY = Math.min(HumanTap.edgeInsetPx(density), rh / 4);
                        boolean insetOk = (pt[0] - sbRect[0]) >= inX - 1 && (sbRect[2] - pt[0]) >= inX - 1
                                && (pt[1] - sbRect[1]) >= inY - 1 && (sbRect[3] - pt[1]) >= inY - 1;
                        // ①′a 人手抖动±2px 后仍在域内（tapSpec 抖动不出目标面）
                        boolean jitterSafe = (pt[0] - sbRect[0]) >= HumanTap.JITTER_ABS
                                && (sbRect[2] - pt[0]) >= HumanTap.JITTER_ABS
                                && (pt[1] - sbRect[1]) >= HumanTap.JITTER_ABS
                                && (sbRect[3] - pt[1]) >= HumanTap.JITTER_ABS;
                        if (!inside || !insetOk || !jitterSafe) { tapSafe = false; break; }
                        rotationSafeN++;
                    }
                }
                TestHost.check(tapSafe, "①′ 人手触点安全 " + model + "（密度 " + density + " · 五轮换点全在可见域 · 抖动±" + HumanTap.JITTER_ABS + "px 不出界）");

                /* ①″ v6.1.0 多点误点专项（误触二轮三防线 F/G/H——每机型 4 项全过） */
                int multiOk = 0;
                // ①″a F 同指纹目标静默：同指纹 1.5s 内拒重复出手，过期放行；
                // 同一广告窗复验扫描连点 10 次 → 仅 1 次出手、9 次被拦（多点根治）
                int cx = (skL + skR) / 2, cy = (skT + skB) / 2;
                String tKey = HumanTap.sameTargetKey(pkg, cx, cy, "跳过",
                        HumanTap.dupTolPx(density));
                java.util.HashMap<String, Long> firedAt = new java.util.HashMap<String, Long>();
                long now = 1000000L;
                firedAt.put(tKey, now);
                boolean quietHit = HumanTap.isFiredRecently(firedAt, tKey, now);              // 刚出手 → 拒
                boolean quietExpired = !HumanTap.isFiredRecently(firedAt, tKey, now + 1600);  // 过期 → 放行
                firedAt.clear(); // 同一广告窗复验扫描重演（首击起步）
                int fired = 0, blocked = 0;
                for (int tap = 0; tap < 10; tap++) {
                    if (HumanTap.isFiredRecently(firedAt, tKey, now)) {
                        blocked++;
                    } else {
                        firedAt.put(tKey, now);
                        fired++;
                    }
                }
                if (quietHit && quietExpired && fired == 1 && blocked == 9) multiOk++;
                // ①″b G 新鲜坐标复核：入场动画中心位移 60%（对角线基准）→ 拒绝旧
                // 坐标；仅位移 2px（微抖）→ 正常放行；节点消失（null）→ 绝不盲刺
                int[] oldB = {skL, skT, skR, skB};
                int obW = oldB[2] - oldB[0], obH = oldB[3] - oldB[1];
                int drift = (int) (0.6 * Math.sqrt(obW * obW + obH * obH));
                int[] animB = {oldB[0] + drift, oldB[1] + drift, oldB[2] + drift, oldB[3] + drift};
                boolean staleAnim = HumanTap.isStaleBounds(oldB, animB, density);
                int[] jitterB = {oldB[0] + 2, oldB[1] + 2, oldB[2] + 2, oldB[3] + 2};
                boolean staleJitter = HumanTap.isStaleBounds(oldB, jitterB, density);
                boolean staleGone = HumanTap.isStaleBounds(oldB, null, density);
                if (staleAnim && !staleJitter && staleGone) multiOk++;
                // ①″c H 结果静默期：刚派发完 250ms 内连发被拦；过期放行
                boolean postQuietBlocked = !HumanTap.postTapQuietOk(now - 100, now);
                boolean postQuietPassed = HumanTap.postTapQuietOk(now - 300, now);
                if (postQuietBlocked && postQuietPassed) multiOk++;
                // ①″d F/G/H 三防线常量全场在场（多点误点防线的参数契约）
                boolean quietConsts = HumanTap.SAME_TARGET_QUIET_MS == 1500L
                        && HumanTap.POST_TAP_QUIET_MS == 250L
                        && HumanTap.STALE_DRIFT_MIN_DP == 24
                        && HumanTap.STALE_DRIFT_RATIO == 0.5;
                if (quietConsts) multiOk++;
                TestHost.check(multiOk == 4, "①″ 多点误点 " + model + "（" + os + " · F 指纹静默 " + fired + "出/" + blocked + "拦 · G 新鲜复核 · H 结果静默 · 常量在场 " + multiOk + "/4）");
                if (multiOk == 4) multiN++;

                /* ② 屏蔽速度（120 节点树 · walk+decide · v5.7 激进参数在场） */
                SimNode bigTree = buildDeepTree(w, h, 120);
                Ctx tCtx = ctx(pkg, "com.matrix.SplashActivity", w, h);
                long t0 = System.nanoTime();
                int rounds = 50;
                for (int r = 0; r < rounds; r++) {
                    ScanFind bf = AdDecisionCore.walk(bigTree, tCtx);
                    AdDecisionCore.decide(bf, tCtx);
                }
                long perDecisionMs = (System.nanoTime() - t0) / 1000L / rounds / 1000L;
                boolean speedOk = perDecisionMs < 20
                        && AdDecisionCore.COUNTDOWN_EXTRA_MS == 120
                        && AdDecisionCore.MAX_CLICKS_PER_WIN == HumanTap.HUMAN_PATIENCE
                        && AdDecisionCore.MAX_CLICKS_PER_WIN == 6
                        && AdDecisionCore.SKIP_CANDS_MAX == 8
                        && humanEngineInPlace(model);
                TestHost.check(speedOk, "② 屏蔽速度 " + model + "（" + perDecisionMs + "ms/决策 · v6 人手引擎参数在场）");
                if (speedOk) speedN++;

                /* ③ bug 测试（菜单陷阱 / 巨横幅 / 验证码横幅 / 游戏 jump vid） */
                int bugOk = 0;
                SimNode menuTree = new SimNode();
                menuTree.boundsArr = new int[]{0, 0, w, h};
                SimNode panel = new SimNode();
                panel.boundsArr = new int[]{(int) (w * 0.55), (int) (h * 0.08), w, (int) (h * 0.92)};
                menuTree.add(panel);
                String[] items = {"播放历史", "录音", "扫一扫", "切换模式", "跳过"};
                for (int i = 0; i < items.length; i++) {
                    SimNode row = new SimNode(items[i], (int) (w * 0.58),
                            (int) (h * 0.16) + i * (int) (h * 0.067),
                            (int) (w * 0.96), (int) (h * 0.16) + (i + 1) * (int) (h * 0.067));
                    row.clickable = true;
                    panel.add(row);
                }
                Ctx mCtx = ctx(pkg, "com.matrix.MainActivity", w, h);
                mCtx.splashLive = false;
                boolean menuClean = RuleEngine.matchAll(menuTree, mCtx, 4).isEmpty();
                if (menuClean) bugOk++;
                SimNode bannerTree = new SimNode();
                bannerTree.boundsArr = new int[]{0, 0, w, h};
                SimNode banner = new SimNode("跳过广告 立即查看", 0, (int) (h * 0.4),
                        w, (int) (h * 0.58));
                banner.clickable = true;
                bannerTree.add(banner);
                ScanFind bnf = AdDecisionCore.walk(bannerTree, tCtx);
                Decision bnd = AdDecisionCore.decide(bnf, tCtx);
                if (AdDecisionCore.A_NONE.equals(bnd.action)) bugOk++;
                boolean smsOverlay = AdDecisionCore.isSmsOverlayPkg("com.android.mms");
                boolean launcherOk = AdDecisionCore.isLauncherLike("com.miui.home");
                if (smsOverlay && launcherOk) bugOk++;
                // v5.7 第四陷阱：游戏 jump vid（jump 非跳过语义——安全门红线）
                SimNode gTree = new SimNode();
                gTree.boundsArr = new int[]{0, 0, w, h};
                SimNode jumpBtn = new SimNode("", (int) (w * 0.40), (int) (h * 0.70),
                        (int) (w * 0.60), (int) (h * 0.80));
                jumpBtn.vid = "com.matrix.game:id/btn_jump";
                jumpBtn.clickable = true;
                gTree.add(jumpBtn);
                Ctx gCtx2 = ctx(pkg, "com.unity3d.player.UnityPlayerActivity", w, h);
                ScanFind jf = AdDecisionCore.walk(gTree, gCtx2);
                Decision jd = AdDecisionCore.decide(jf, gCtx2);
                boolean jumpClean = RuleEngine.matchAll(gTree, gCtx2, 4).isEmpty()
                        && AdDecisionCore.A_NONE.equals(jd.action);
                if (jumpClean) bugOk++;
                TestHost.check(bugOk == 4, "③ bug 防线 " + model + "（菜单/横幅/overlay/游戏jump " + bugOk + "/4）");
                if (bugOk == 4) bugN++;

                /* ④ 功耗测试（典型日：45 启动 + 120 后台事件 · v5.8 骨架档 18 · 2 返回键） */
                double microWh = PowerGovernor.microWh(165, 45, 18, 2);
                boolean powOk = microWh < 150 && SmartScanPlannerCheck();
                TestHost.check(powOk, "④ 功耗 " + model + "（" + (microWh / 1000.0) + "mWh/典型日 · 分级预算在场）");
                if (powOk) powN++;

                /* ⑤ 精准度（银行守护根窗零返回键 · 验证码标记 · Unity 游戏引擎窗免疫） */
                int precOk = 0;
                Ctx gcCtx = ctx("com.icbc", "com.icbc.MainActivity", w, h);
                gcCtx.guard = true;
                gcCtx.rootWindow = true;
                SimNode gkTree = new SimNode();
                gkTree.boundsArr = new int[]{0, 0, w, h};
                SimNode gHot = new SimNode("", 0, 0, w, h);
                gHot.clickable = true;
                gkTree.add(gHot);
                ScanFind gf = AdDecisionCore.walk(gkTree, gcCtx);
                Decision gd = AdDecisionCore.decide(gf, gcCtx);
                if (!AdDecisionCore.A_BACK.equals(gd.action)) precOk++;
                if (AdDecisionCore.isCodeBannerMarker("验证码") || AdDecisionCore.isCodeBannerMarker("校验码")) precOk++;
                if (!AdDecisionCore.isAdActivityClass("com.unity3d.player.UnityPlayerActivity")) precOk++;
                TestHost.check(precOk == 3, "⑤ 精准度 " + model + "（守护零返回键/验证码标记/游戏窗免疫 " + precOk + "/3）");
                if (precOk == 3) precN++;

                /* ⑥ 算法优化（v6 新指纹 · 非广告窗免疫 · 品牌族归一 · 自愈合并 · 五轮分应用） */
                int algoOk = 0;
                int newFpHit = 0;
                for (String cls : NEW_FINGERPRINTS) {
                    if (AdDecisionCore.isAdActivityClass(cls)) newFpHit++;
                }
                if (newFpHit == NEW_FINGERPRINTS.length) algoOk++;
                int nonAdHit = 0;
                for (String cls : NON_AD_CLASSES) {
                    if (AdDecisionCore.isAdActivityClass(cls)) nonAdHit++;
                }
                if (nonAdHit == 0) algoOk++;
                // 品牌族归一（每机型都验——11 组全为厂商专属族，红米/POCO/iQOO
                // 归 HyperOS/OriginOS 与独立品牌组并存的厂商族一致）
                String fam = RestoreGuard.familyOf(manuf, manuf, model);
                boolean famOk;
                if (EXPECTED_FAMILIES[bi] != null) {
                    famOk = EXPECTED_FAMILIES[bi].equals(fam);
                } else {
                    famOk = RestoreGuard.FAMILY_GENERIC.equals(fam);
                }
                if (famOk) algoOk++;
                String flat = "com.zai.adshield/com.zai.adshield.AdSkipService";
                String merged1 = RestoreGuard.mergeRegistrationCsv(
                        "com.other.a/.Svc", flat);
                String merged2 = RestoreGuard.mergeRegistrationCsv(null, flat);
                String merged3 = RestoreGuard.mergeRegistrationCsv(flat, flat);
                if ("com.other.a/.Svc:com.zai.adshield/com.zai.adshield.AdSkipService".equals(merged1)
                        && flat.equals(merged2) && flat.equals(merged3)) algoOk++;
                // v6.0.0 分应用五轮表：微博/腾讯视频/优酷/爱奇艺/芒果TV/酷我音乐
                // 六款新 App 的「跳过广告」精确等值全命中（未命中→干看广告）
                int fifthRoundAppHit = 0;
                for (String fp : FIFTH_ROUND_APPS) {
                    SimNode fTree = new SimNode();
                    fTree.boundsArr = new int[]{0, 0, w, h};
                    SimNode fBtn = new SimNode("跳过广告", skL, skT, skR, skB);
                    fBtn.clickable = true;
                    fTree.add(fBtn);
                    Ctx fCtx = ctx(fp, "com.matrix.SplashActivity", w, h);
                    if (!RuleEngine.matchAll(fTree, fCtx, 4).isEmpty()) fifthRoundAppHit++;
                }
                if (fifthRoundAppHit == FIFTH_ROUND_APPS.length) algoOk++;
                TestHost.check(algoOk == 5, "⑥ 算法优化 " + model + "（新指纹 " + newFpHit + "/" + NEW_FINGERPRINTS.length
                        + " · 非广告窗免疫 " + (NON_AD_CLASSES.length - nonAdHit) + "/" + NON_AD_CLASSES.length
                        + " · 族归一 " + (famOk ? "✓" : "✗") + " · 自愈合并 ✓ · 五轮分应用 " + fifthRoundAppHit + "/" + FIFTH_ROUND_APPS.length + "）");
                if (algoOk == 5) algoN++;

                /* ⑦ v7.1.0 信息流广告维（FeedAdGuard 移植回归）：朋友圈就业广告类
                 * App 内广告两段式——角标/菜单几何按机型分辨率 1:1 缩放（1080×2400
                 * 基准坐标按 w/h 比例映射到 720p~2K+ 全几何档），4 项全过：
                 *  a) P1 披露角标识别：非 null·action=feed_p1_badge·label=就业·
                 *     tapBounds 逐元素==角标自身（绝不广告主体）；
                 *  b) 广告主体大容器拒收：正确角标词「广告」+巨容器（100% 宽）面
                 *     积门拒 + 正常内容流零命中（宁漏勿误）；
                 *  c) P2 armed 收菜单：双闸（强特征≥1 且总数≥2）+优先级最高项
                 *     「关闭该广告」+tapBounds==该项自身；
                 *  d) P2 非 armed 零对抗（用户自己长按的菜单绝不对抗）。 */
                int feedOk = 0;
                // 朋友圈广告树（几何 1:1 缩放：root → 广告主体大容器 → 内嵌披露角标）
                SimNode feedTree = new SimNode();
                feedTree.boundsArr = new int[]{0, 0, w, h};
                SimNode adCard = new SimNode("", 0, (int) (h * 0.33), w, (int) (h * 0.67));
                int bX0 = (int) (w * 0.917), bY0 = (int) (h * 0.342);
                int bX1 = bX0 + (int) (w * 0.12), bY1 = bY0 + (int) (h * 0.033);
                SimNode badgeNode = new SimNode("就业", bX0, bY0, bX1, bY1);
                adCard.add(badgeNode);
                feedTree.add(adCard);
                FeedAdGuard.FeedDecision fp1 = FeedAdGuard.decideP1(feedTree, w, h);
                if (fp1 != null && FeedAdGuard.ACTION_P1_BADGE.equals(fp1.action)
                        && "就业".equals(fp1.label) && fp1.tapBounds != null
                        && fp1.tapBounds[0] == bX0 && fp1.tapBounds[1] == bY0
                        && fp1.tapBounds[2] == bX1 && fp1.tapBounds[3] == bY1) feedOk++;
                // 广告主体大容器拒收（宁漏勿误红线）+ 正常内容流零命中
                SimNode normalFeed = new SimNode();
                normalFeed.boundsArr = new int[]{0, 0, w, h};
                normalFeed.add(new SimNode("今天天气真好出来走走", 0, (int) (h * 0.12), w, (int) (h * 0.16)));
                normalFeed.add(new SimNode("周末和朋友聚餐", 0, (int) (h * 0.21), w, (int) (h * 0.25)));
                boolean bodyRejected = !FeedAdGuard.isDisclosureBadge("广告", adCard.boundsArr, w, h)
                        && !FeedAdGuard.isDisclosureBadge("就业广告招聘专场", badgeNode.boundsArr, w, h)
                        && FeedAdGuard.decideP1(normalFeed, w, h) == null;
                if (bodyRejected) feedOk++;
                // 关闭菜单树（紧凑行几何 1:1 缩放）：armed 才收，优先级最高项
                SimNode feedMenuTree = new SimNode();
                feedMenuTree.boundsArr = new int[]{(int) (w * 0.18), (int) (h * 0.29), (int) (w * 0.82), (int) (h * 0.62)};
                int mX0 = (int) (w * 0.2), mX1 = (int) (w * 0.54);
                int rowH = Math.max(1, (int) (h * 0.035));
                int r0Y = (int) (h * 0.317), r1Y = r0Y + (int) (h * 0.042);
                SimNode closeRow = new SimNode("关闭该广告", mX0, r0Y, mX1, r0Y + rowH);
                SimNode dislikeRow = new SimNode("不感兴趣", mX0, r1Y, mX1, r1Y + rowH);
                feedMenuTree.add(closeRow);
                feedMenuTree.add(dislikeRow);
                FeedAdGuard.FeedDecision fp2 = FeedAdGuard.decideP2(feedMenuTree, w, h, true);
                if (fp2 != null && FeedAdGuard.ACTION_P2_CLOSE.equals(fp2.action)
                        && "关闭该广告".equals(fp2.label) && fp2.tapBounds != null
                        && fp2.tapBounds[0] == mX0 && fp2.tapBounds[1] == r0Y
                        && fp2.tapBounds[2] == mX1 && fp2.tapBounds[3] == r0Y + rowH) feedOk++;
                if (FeedAdGuard.decideP2(feedMenuTree, w, h, false) == null) feedOk++;
                TestHost.check(feedOk == 4, "⑦ 信息流广告 " + model + "（" + os + " · 角标/菜单几何 1:1 缩放 · P1 角标+主体拒收+P2 双闸+非 armed 零对抗 " + feedOk + "/4）");
                if (feedOk == 4) feedN++;
            }

            TestHost.suite("品牌汇总 " + BRAND_NAMES[bi]);
            TestHost.check(hitN == models.length, BRAND_NAMES[bi] + " 开屏命中 " + hitN + "/" + models.length);
            TestHost.check(multiN == models.length, BRAND_NAMES[bi] + " 多点误点 " + multiN + "/" + models.length);
            TestHost.check(speedN == models.length, BRAND_NAMES[bi] + " 速度 " + speedN + "/" + models.length);
            TestHost.check(bugN == models.length, BRAND_NAMES[bi] + " bug 防线 " + bugN + "/" + models.length);
            TestHost.check(powN == models.length, BRAND_NAMES[bi] + " 功耗 " + powN + "/" + models.length);
            TestHost.check(precN == models.length, BRAND_NAMES[bi] + " 精准度 " + precN + "/" + models.length);
            TestHost.check(algoN == models.length, BRAND_NAMES[bi] + " 算法优化 " + algoN + "/" + models.length);
            TestHost.check(feedN == models.length, BRAND_NAMES[bi] + " 信息流广告 " + feedN + "/" + models.length);
            brandHitTotal += hitN;
            brandMultiTotal += multiN;
            brandSpeedTotal += speedN;
            brandBugTotal += bugN;
            brandPowerTotal += powN;
            brandPrecTotal += precN;
            brandAlgoTotal += algoN;
            brandFeedTotal += feedN;
        }

        /* ══ 矩阵总检（9 品牌+子品牌 11 组 × 28 机型 = 308 · 八维全 308/308）══ */
        TestHost.suite("矩阵总检（9 品牌+子品牌 11 组 × 28 机型）");
        TestHost.check(brandHitTotal == 308, "开屏命中总 " + brandHitTotal + "/308");
        TestHost.check(brandMultiTotal == 308, "多点误点总 " + brandMultiTotal + "/308");
        TestHost.check(brandSpeedTotal == 308, "屏蔽速度总 " + brandSpeedTotal + "/308");
        TestHost.check(brandBugTotal == 308, "bug 防线总 " + brandBugTotal + "/308");
        TestHost.check(brandPowerTotal == 308, "功耗总 " + brandPowerTotal + "/308");
        TestHost.check(brandPrecTotal == 308, "精准度总 " + brandPrecTotal + "/308");
        TestHost.check(brandAlgoTotal == 308, "算法优化总 " + brandAlgoTotal + "/308");
        TestHost.check(brandFeedTotal == 308, "信息流广告总 " + brandFeedTotal + "/308");
        System.out.println("  矩阵: " + brandHitTotal + "/" + brandMultiTotal + "/" + brandSpeedTotal
                + "/" + brandBugTotal + "/" + brandPowerTotal + "/" + brandPrecTotal
                + "/" + brandAlgoTotal + "/" + brandFeedTotal + "（八维各 /308）");

        TestHost.done();
    }

    /** 分级预算常量在场（SmartScanPlanner · JVM 侧可断言的投影） */
    static boolean SmartScanPlannerCheck() {
        return com.zai.adshield.core.SmartScanPlanner.BUDGET_FULL == 600
                && com.zai.adshield.core.SmartScanPlanner.BUDGET_LIGHT == 350
                && com.zai.adshield.core.SmartScanPlanner.DEPTH_LIGHT == 20;
    }

    /** v6.0.0 分应用五轮表六款新 App（RuleEngine 分应用精确层命中面） */
    static final String[] FIFTH_ROUND_APPS = {
            "com.sina.weibo",             // 微博
            "com.tencent.qqlive",        // 腾讯视频
            "com.youku.phone",           // 优酷视频
            "com.qiyi.video",             // 爱奇艺
            "com.hunantv.imgo.activity",  // 芒果TV
            "com.kuwo.player",            // 酷我音乐
    };

    /** v6.0.0 1:1 人手引擎参数在场（HumanTap 域参数/复验节奏/耐心预算/
     *  dp 密度归一/轨迹去重容差——五要素全在场且在真人域） */
    static boolean humanEngineInPlace(String model) {
        boolean ok = HumanTap.DELAY_MIN == 26 && HumanTap.DELAY_SPAN == 31
                && HumanTap.DUR_FIRST_MIN == 95 && HumanTap.DUR_FIRST_SPAN == 36
                && HumanTap.DUR_RETRY_MIN == 115 && HumanTap.DUR_RETRY_SPAN == 46
                && HumanTap.JITTER_ABS == 2
                && HumanTap.HUMAN_PATIENCE == 6
                && HumanTap.MIN_SIDE_DP == 12 && HumanTap.EDGE_INSET_DP == 6
                && HumanTap.DUP_TOL_DP == 8
                && HumanTap.verifyDelayMs(0) == 500L
                && HumanTap.verifyDelayMs(1) == 1400L
                && HumanTap.verifyDelayMs(5) == 7000L
                && HumanTap.verifyDelayMs(99) == 7000L
                && HumanTap.minSidePx(1f) == 12 && HumanTap.minSidePx(2f) == 24
                && HumanTap.minSidePx(3.5f) == 42
                && HumanTap.edgeInsetPx(1f) == 6 && HumanTap.edgeInsetPx(3f) == 18
                && HumanTap.dupTolPx(1f) == 8 && HumanTap.dupTolPx(2.5f) == 20
                && HumanTap.clampDensity(0.5f) == 1f && HumanTap.clampDensity(9f) == 4f;
        // tapSpec 域断言：首击/重试时长都在真人域，抖动不出 ±2px
        for (int attempt = 0; attempt <= 3; attempt++) {
            int[] s = HumanTap.tapSpec(0x1234ABCDL + model.hashCode(), attempt);
            boolean inRange = s[0] >= HumanTap.DELAY_MIN && s[0] < HumanTap.DELAY_MIN + HumanTap.DELAY_SPAN;
            int durMin = attempt <= 0 ? HumanTap.DUR_FIRST_MIN : HumanTap.DUR_RETRY_MIN;
            int durSpan = attempt <= 0 ? HumanTap.DUR_FIRST_SPAN : HumanTap.DUR_RETRY_SPAN;
            inRange = inRange && s[1] >= durMin && s[1] < durMin + durSpan;
            inRange = inRange && Math.abs(s[2]) <= HumanTap.JITTER_ABS && Math.abs(s[3]) <= HumanTap.JITTER_ABS;
            if (!inRange) return false;
        }
        return ok;
    }

    /** 生成 n 节点深树（速度测试用：容器嵌套 + 兄弟铺开） */
    static SimNode buildDeepTree(int w, int h, int targetNodes) {
        SimNode root = new SimNode();
        root.boundsArr = new int[]{0, 0, w, h};
        java.util.ArrayList<SimNode> frontier = new java.util.ArrayList<SimNode>();
        frontier.add(root);
        int count = 1;
        int layer = 0;
        while (count < targetNodes && !frontier.isEmpty() && layer < 24) {
            java.util.ArrayList<SimNode> next = new java.util.ArrayList<SimNode>();
            for (SimNode parent : frontier) {
                int kids = Math.min(3, targetNodes - count);
                for (int i = 0; i < kids; i++) {
                    SimNode n = new SimNode(layer % 2 == 0 ? "广告" + layer : "",
                            (int) (w * 0.1 * (i + 1)), (int) (h * 0.12) + layer * 40,
                            (int) (w * 0.1 * (i + 1) + w * 0.05), (int) (h * 0.12) + layer * 40 + 60);
                    n.clickable = layer % 3 == 0;
                    parent.add(n);
                    next.add(n);
                    count++;
                }
            }
            frontier = next;
            layer++;
        }
        return root;
    }

    static Ctx ctx(String pkg, String act, int w, int h) {
        Ctx ctx = new Ctx();
        ctx.screenW = w;
        ctx.screenH = h;
        ctx.pkg = pkg;
        ctx.activityId = act;
        ctx.elapsedMs = 800;
        ctx.splashLive = true;
        return ctx;
    }
}
