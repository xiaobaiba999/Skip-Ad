package li.gkd.app.ui.webui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Locale

/**
 * 保活体检厂商直达表（v8.0.5）
 * ─────────────────────────────────────────────
 * 自 7.15.14 core/RestoreGuard.java 原样移植的品牌族归一 + 深链候选注册表。
 * UI 侧（WebView 首页「保活体检」卡片）按 id 直达厂商自启动/省电设置页。
 *
 * · 品牌族归一：manufacturer/brand/model 任一命中即归族（大小写不敏感）
 * · 深链候选全部是硬编码系统包，杜绝任意组件注入
 * · 候选逐个 resolveActivity 回退，最终回退应用详情页（永远可用）
 */
object KeepAliveTargets {
    // ───── 品牌族常量（值即页面侧分发键，与 7.15.14 一致）─────
    private const val FAMILY_HYPEROS = "hyperos" // Xiaomi/Redmi/POCO
    private const val FAMILY_HARMONY = "harmony" // 华为
    private const val FAMILY_MAGIC = "magic" // 荣耀
    private const val FAMILY_COLOROS = "coloros" // OPPO/realme/一加
    private const val FAMILY_ORIGINOS = "originos" // vivo/iQOO
    private const val FAMILY_ONEUI = "oneui" // 三星
    private const val FAMILY_FLYME = "flyme" // 魅族
    private const val FAMILY_GENERIC = "generic" // ZTE/联想/其他

    /** 一条保活体检项（与 7.15.14 KeepAliveItem 同构） */
    data class KeepAliveItem(
        val id: String,
        val label: String,
        val desc: String,
        /** 深链候选 {包名, Activity}，按优先级排序逐个回退 */
        val candidates: Array<Pair<String, String>>,
        /** "miui_pkg" = 需附 package_name extra；null = 无 */
        val extra: String?,
    )

    fun familyLabel(family: String): String = when (family) {
        FAMILY_HYPEROS -> "MIUI/HyperOS（小米系）"
        FAMILY_HARMONY -> "HarmonyOS（华为）"
        FAMILY_MAGIC -> "MagicOS（荣耀）"
        FAMILY_COLOROS -> "ColorOS（OPPO/一加/realme）"
        FAMILY_ORIGINOS -> "OriginOS/Funtouch（vivo/iQOO）"
        FAMILY_ONEUI -> "OneUI（三星）"
        FAMILY_FLYME -> "Flyme（魅族）"
        else -> "通用 Android"
    }

    fun familyOf(manufacturer: String?, brand: String?, model: String?): String {
        val m = (manufacturer.orEmpty() + " " + brand.orEmpty() + " " + model.orEmpty())
            .lowercase(Locale.ROOT)
        // 荣耀先于华为判断（2021 后独立，manufacturer 是 HONOR）
        if (m.contains("honor") || m.contains("荣耀")) return FAMILY_MAGIC
        if (m.contains("huawei") || m.contains("华为")) return FAMILY_HARMONY
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
            m.contains("小米") || m.contains("红米")
        ) return FAMILY_HYPEROS
        if (m.contains("oneplus") || m.contains("realme") || m.contains("oppo") ||
            m.contains("一加")
        ) return FAMILY_COLOROS
        if (m.contains("iqoo") || m.contains("vivo")) return FAMILY_ORIGINOS
        if (m.contains("samsung") || m.contains("三星")) return FAMILY_ONEUI
        if (m.contains("meizu") || m.contains("魅族")) return FAMILY_FLYME
        return FAMILY_GENERIC
    }

    fun currentFamily(): String = familyOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL)

    /** 品牌族 → 体检项列表（generic 返回空——只显示三项自动检测） */
    fun itemsFor(family: String): List<KeepAliveItem> = when (family) {
        FAMILY_HYPEROS -> listOf(
            KeepAliveItem(
                "hyperos_autostart", "自启动（防清理后台吊销）",
                "安全中心 → 应用管理 → 权限 → 自启动：允许开屏卫士",
                arrayOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                null,
            ),
            KeepAliveItem(
                "hyperos_battery", "省电策略 → 无限制",
                "设置 → 省电与电池 → 应用智能省电：开屏卫士选「无限制」",
                arrayOf("com.miui.powerkeeper" to "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                "miui_pkg",
            ),
        )
        FAMILY_HARMONY -> listOf(
            KeepAliveItem(
                "harmony_autostart", "应用启动管理 → 手动管理",
                "设置 → 应用和服务 → 应用启动管理：开屏卫士改手动，三项全开",
                arrayOf(
                    "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
                ),
                null,
            ),
        )
        FAMILY_MAGIC -> listOf(
            KeepAliveItem(
                "magic_autostart", "应用启动管理 → 手动管理",
                "设置 → 应用启动管理：开屏卫士改手动管理，三项全开",
                arrayOf(
                    "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                    "com.hihonor.systemmanager" to "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity",
                ),
                null,
            ),
        )
        FAMILY_COLOROS -> listOf(
            KeepAliveItem(
                "coloros_autostart", "自启动 + 允许完全后台行为",
                "设置 → 应用管理 → 开屏卫士：允许自启动、允许完全后台行为",
                arrayOf(
                    "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
                    "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
                ),
                null,
            ),
        )
        FAMILY_ORIGINOS -> listOf(
            KeepAliveItem(
                "originos_autostart", "自启动 + 后台高耗电允许",
                "设置 → 电池 → 后台高耗电：开屏卫士允许；i管家 → 应用启动允许",
                arrayOf(
                    "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartHighPowerManager",
                    "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    "com.iqoo.secure" to "com.iqoo.secure.safeguard.PurviewTabActivity",
                ),
                null,
            ),
        )
        FAMILY_ONEUI -> listOf(
            KeepAliveItem(
                "oneui_battery", "电池 → 不受限 + 从不休眠应用",
                "设置 → 电池 → 后台使用限制：开屏卫士加入「从不休眠的应用」",
                arrayOf("com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity"),
                null,
            ),
        )
        FAMILY_FLYME -> listOf(
            KeepAliveItem(
                "flyme_autostart", "自启动 + 后台管理允许",
                "设置 → 应用管理 → 开屏卫士：允许自启动、后台运行不限制",
                arrayOf("com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity"),
                null,
            ),
        )
        else -> emptyList()
    }

    /** 直达体检项对应厂商设置页（逐候选 resolveActivity 回退；最终回退应用详情页） */
    fun openTarget(context: Context, id: String): Boolean {
        val items = itemsFor(currentFamily())
        val target = items.find { it.id == id } ?: run {
            openAppDetails(context)
            return true
        }
        for (cand in target.candidates) {
            try {
                val intent = Intent()
                intent.component = ComponentName(cand.first, cand.second)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (target.extra == "miui_pkg") {
                    intent.putExtra("package_name", context.packageName)
                }
                if (context.packageManager.resolveActivity(intent, 0) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Throwable) {
            }
        }
        openAppDetails(context)
        return true
    }

    private fun openAppDetails(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName),
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }
}
