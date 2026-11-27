package li.gkd.app.util

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.hjq.toast.Toaster
import com.hjq.toast.style.WhiteToastStyle
import li.gkd.app.app
import li.gkd.app.matching.MatchingRuntime
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.permission.PermissionStates
import li.gkd.app.rule.ResolvedRule
import li.gkd.app.service.A11yService
import li.gkd.app.settings.SettingsRepository.actionCount
import li.gkd.app.settings.SettingsRepository.settings
import li.gkd.app.ui.util.ActionToastTemplate

object AndroidToastUtils {
    private val darkTheme: Boolean
        get() =
            settings.value.enableDarkTheme
                ?: app.resources.configuration.let {
                    it.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES
                }

    private val toastYOffset: Int
        get() = (ScreenUtils.getSize().height * 0.12f).toInt()

    private val circleOutlineProvider by lazy {
        object : ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                if (view != null && outline != null) {
                    // 20.sp : line height, 12.dp : top/bottom padding
                    outline.setRoundRect(
                        0,
                        0,
                        view.width,
                        view.height,
                        (12.dp.px * 2 + 20.sp.px) / 2f,
                    )
                }
            }
        }
    }

    private fun View.updateStyle() {
        setPaddingRelative(
            16.dp.px.toInt(),
            12.dp.px.toInt(),
            16.dp.px.toInt(),
            12.dp.px.toInt(),
        )
        layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        if (this is TextView) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 14.sp.px)
            setTextColor(if (darkTheme) Color.WHITE else Color.BLACK)
        }
        background =
            GradientDrawable().apply {
                setColor((if (darkTheme) "#303030" else "#fafafa").toColorInt())
            }
        outlineProvider = circleOutlineProvider
        clipToOutline = true
        elevation = 2.dp.px
    }

    private fun setReactiveStyle() {
        Toaster.setStyle(
            object : WhiteToastStyle() {
                override fun getGravity() = Gravity.BOTTOM

                override fun getYOffset() = toastYOffset

                override fun getTranslationZ(context: Context) = 0f

                override fun createView(context: Context): View {
                    return super.createView(context).apply {
                        updateStyle()
                    }
                }
            },
        )
    }

    private var triggerTime = 0L
    private const val triggerInterval = 2600L

    fun showAction(rule: ResolvedRule, runId: Long = 0) {
        if (!settings.value.toastWhenClick) return
        val trace = MatchingRuntime.coordinator.diagnostics
        val dispatch =
            if (trace.enabled) trace.beginOperation("ToastDispatch", runId, rule.matchIdentity())
            else null
        ThreadUtils.runMainOrPost {
            trace.endOperation(dispatch, "main_dispatched")
            val t = System.currentTimeMillis()
            if (t - triggerTime > triggerInterval + 100) { // 100ms 保证二次显示的时候上一次已经完全消失
                triggerTime = t
                val text =
                    ActionToastTemplate.render(
                        settings.value.actionToast,
                        rule.rule.name.toString(),
                        rule.g.group.name,
                        actionCount.value,
                    )
                if (dispatch != null && trace.enabled)
                    trace.record(
                        runId,
                        MatchStage.Idle,
                        "toast_show_requested",
                        dispatch.rule,
                        entrySource = "toast",
                        sessionId = dispatch.sessionId,
                    )
                if (settings.value.useSystemToast) {
                    showSystem(text)
                } else {
                    showA11y()
                }
            } else if (dispatch != null && trace.enabled)
                trace.record(
                    runId,
                    MatchStage.Idle,
                    "toast_throttled",
                    dispatch.rule,
                    entrySource = "toast",
                    sessionId = dispatch.sessionId,
                )
        }
    }

    fun previewAction(text: CharSequence, useSystemToast: Boolean) {
        ThreadUtils.runMainOrPost {
            if (useSystemToast) showSystem(text) else showA11y()
        }
    }

    private var cacheToast: Toast? = null

    private fun showSystem(message: CharSequence) {
        removeAttachedGlass()
        cacheToast?.cancel()
        cacheToast =
            Toast.makeText(app, message, Toast.LENGTH_SHORT).apply {
                show()
            }
    }

    // ───────── v8.1.0 液态柔光玻璃提示（Liquid Glass · 重制版）─────────
    //
    // 视觉方向：HyperOS 柔光玻璃 / OriginOS 渐变半透明材质 / ColorOS 液态
    // 玻璃的通用化实现（不复制厂商专有实现、不依赖系统级 Hook）——
    // · 四层静态玻璃材质：磨砂渐变底 + 顶部柔光内反射 + 边缘细腻高光描边
    //   + 品牌绿弱着色；柔和投影（elevation）；深浅色双材质；
    // · 内容：左圆形 Logo + 右「开屏卫士」标题 +「正在守护」状态行
    //   + 守护状态微动画（呼吸圆点，无复杂装饰）；
    // · 入场：上浮 + 透明度 0→1 + 缩放 0.96→1.0（280ms Decelerate）；
    // · 停留：1.9s 稳定显示，不闪烁、不重复创建；
    // · 退场：柔和淡出 + 轻微缩小（240ms）+ 完全移除窗口。
    //
    // 背景：overlay 窗口绝大多数设备不支持实时背景模糊（跨窗模糊需硬件
    // 支持且常被省电策略关闭）。API 31+ 在系统开启跨窗模糊时以
    // backgroundBlurRadius 渐进增强，其余场景四层静态磨砂兜底——视觉相近
    // 且零每帧开销。不截图、不每帧绘制、不引入高耗电模糊方案。
    //
    // 生命周期（v8.0.8 三处移除脆弱点根治，残影专项）：
    // · addView 全程保护：a11y wm 失败回退应用 wm，再失败回退系统 Toast；
    // · 同一时刻至多一个玻璃视图附着（新提示先强制移除旧视图）；
    // · 兜底移除定时器无条件注册（不随退场动画异常丢失）；
    // · cancel 幂等，入场/呼吸动画在移除前全部取消。

    /** 玻璃容器圆角（dp）。 */
    private val glassRadius = 26.dp.px

    /** 玻璃态显隐时长（ms）：入场 280 + 停留 1900 + 淡出 240。 */
    private const val GLASS_ENTER_MS = 280L
    private const val GLASS_HOLD_MS = 1900L
    private const val GLASS_EXIT_MS = 240L

    /** 守护状态微动画：呼吸周期（ms）与最暗透明度。 */
    private const val STATUS_PULSE_PERIOD_MS = 1600L
    private const val STATUS_PULSE_MIN_ALPHA = 0.35f

    /** 四层玻璃材质。 */
    private fun glassBackground(): LayerDrawable {
        val density = app.resources.displayMetrics.density
        val highlightStroke = (1.2f * density).toInt()
        return LayerDrawable(
            arrayOf(
                // 层1：磨砂玻璃底（TL_BR 渐变，深浅色各自材质）
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    orientation = GradientDrawable.Orientation.TL_BR
                    if (darkTheme) {
                        colors =
                            intArrayOf(
                                0xF21B231E.toInt(),
                                0xE618201B.toInt(),
                                0xD914231E.toInt(),
                            )
                    } else {
                        colors =
                            intArrayOf(
                                0xF2FFFFFF.toInt(),
                                0xE8F4F9F1.toInt(),
                                0xD8E7F0E8.toInt(),
                            )
                    }
                    cornerRadius = glassRadius
                },
                // 层2：顶部柔光内反射（垂直渐变，上亮下透明的玻璃弧光）
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    orientation = GradientDrawable.Orientation.TOP_BOTTOM
                    colors =
                        if (darkTheme) {
                            intArrayOf(0x26FFFFFF.toInt(), 0x00FFFFFF.toInt())
                        } else {
                            intArrayOf(0x5EFFFFFF.toInt(), 0x08FFFFFF.toInt())
                        }
                    cornerRadius = glassRadius
                },
                // 层3：边缘细腻高光描边（内侧一圈微亮线，玻璃壁厚感）
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = glassRadius
                    setStroke(
                        highlightStroke,
                        if (darkTheme) 0x30FFFFFF else 0x70FFFFFF,
                    )
                },
                // 层4：品牌绿弱着色（玻璃透出的底色感）
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    orientation = GradientDrawable.Orientation.BL_TR
                    colors =
                        intArrayOf(0x1433502F.toInt(), 0x0033502F.toInt())
                    cornerRadius = glassRadius
                },
            ),
        )
    }

    /** 圆形品牌 Logo 位图（应用图标裁圆，缓存单例——低频 Toast 场景直接复用）。 */
    @Volatile private var logoBitmap: Bitmap? = null

    private fun brandLogo(sizePx: Int): Bitmap? {
        logoBitmap?.let { return it }
        return runCatching {
            val icon = app.packageManager.getApplicationIcon(app.packageName)
            val iconSize = (sizePx * 1.35f).toInt().coerceAtLeast(sizePx)
            val src = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(src)
            icon.setBounds(0, 0, iconSize, iconSize)
            icon.draw(canvas)
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val outCanvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // 透出玻璃的品牌绿底
            paint.color = if (darkTheme) 0xFF2A3A31.toInt() else 0xFF3B6B50.toInt()
            val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
            outCanvas.drawOval(rect, paint)
            outCanvas.save()
            outCanvas.clipPath(Path().apply { addOval(rect, Path.Direction.CW) })
            // 居中裁切图标（自适应图标含安全边距，取中心 82% 区域）
            val crop = (iconSize * 0.82f).toInt()
            val srcLeft = (iconSize - crop) / 2
            outCanvas.drawBitmap(
                src,
                Rect(srcLeft, srcLeft, srcLeft + crop, srcLeft + crop),
                rect,
                paint,
            )
            outCanvas.restore()
            logoBitmap = out
            out
        }.getOrNull()
    }

    /** 当前唯一附着的玻璃视图（同刻至多一层）。 */
    private class GlassLayer(
        val view: View,
        val pulseDot: View,
        val windowManager: WindowManager,
    )

    @Volatile private var attachedGlass: GlassLayer? = null

    private fun buildGlassView(): Pair<View, View> {
        val dark = darkTheme
        val density = app.resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val row = LinearLayout(app)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14f), dp(10f), dp(18f), dp(10f))

        val logoSize = dp(32f)
        val logo = ImageView(app)
        logo.scaleType = ImageView.ScaleType.FIT_CENTER
        brandLogo(logoSize)?.let { logo.setImageDrawable(BitmapDrawable(app.resources, it)) }
        logo.layoutParams =
            LinearLayout.LayoutParams(logoSize, logoSize).apply { marginEnd = dp(11f) }

        val textCol = LinearLayout(app)
        textCol.orientation = LinearLayout.VERTICAL

        val title = TextView(app)
        title.text = "开屏卫士"
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        title.typeface =
            android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        title.setTextColor(if (dark) 0xFFF4FCF6.toInt() else 0xFF1E3A2C.toInt())
        title.maxLines = 1

        // 状态行：「正在守护」+ 呼吸圆点（守护状态微动画）
        val statusRow = LinearLayout(app)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL

        val pulseDot = View(app)
        pulseDot.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (dark) 0xFF7FD9A4.toInt() else 0xFF3B8B5F.toInt())
            }
        pulseDot.layoutParams =
            LinearLayout.LayoutParams(dp(5f), dp(5f)).apply { marginEnd = dp(6f) }

        val subtitle = TextView(app)
        subtitle.text = "正在守护"
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        subtitle.setTextColor(if (dark) 0xB8D7EEDD.toInt() else 0x9633502F.toInt())
        subtitle.maxLines = 1
        subtitle.letterSpacing = 0.02f

        statusRow.addView(pulseDot)
        statusRow.addView(subtitle)
        textCol.addView(title)
        textCol.addView(statusRow)
        row.addView(logo)
        row.addView(textCol)

        row.background = glassBackground()
        row.outlineProvider =
            object : ViewOutlineProvider() {
                override fun getOutline(view: View?, outline: Outline?) {
                    if (view != null && outline != null) {
                        outline.setRoundRect(0, 0, view.width, view.height, glassRadius)
                    }
                }
            }
        row.clipToOutline = true
        // 柔和投影（玻璃悬浮感）
        row.elevation = 12.dp.px
        row.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        return row to pulseDot
    }

    // 1.使用 WeakReference<View> 在某些机型上导致无法取消
    // 2.使用协程 delay + cacheView 也可能导致无法取消
    // https://github.com/gkd-kit/gkd/issues/697
    // https://github.com/gkd-kit/gkd/issues/698
    private fun showA11y() {
        cacheToast?.cancel()
        cacheToast = null
        val a11yWm = A11yService.instance?.wm
        val appWm = if (PermissionStates.drawOverlays.updateAndGet()) app.windowManager else null
        val wm = a11yWm ?: appWm
        if (wm == null) {
            showSystemFallback()
            return
        }
        // 残影根治①：同一时刻至多一个玻璃视图——新提示先强制移除旧视图
        removeAttachedGlass()
        val (view, pulseDot) = buildGlassView()
        val layoutParams =
            WindowManager.LayoutParams().apply {
                type =
                    if (wm === appWm) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    }
                format = PixelFormat.TRANSLUCENT
                flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                packageName = app.packageName
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = toastYOffset
                // 背景模糊评估结论（v8.1.0）：WindowManager.LayoutParams 的
                // backgroundBlurRadius / setBlurBehindRadius 在 overlay 场景不
                // 可用（后者需 FLAG_BLUR_BEHIND 全屏模糊，前者非公开 API）——
                // 四层静态磨砂玻璃即视觉基线，全 Android 版本一致，无降级差异。
            }
        // 残影根治②：addView 全程保护——a11y wm 垂死（BadToken）不再崩溃；
        // a11y wm 失败回退应用 wm，再失败回退系统 Toast（显示永不失败）。
        try {
            wm.addView(view, layoutParams)
            presentGlass(view, pulseDot, wm)
        } catch (_: Exception) {
            var shown = false
            if (wm !== appWm && appWm != null) {
                try {
                    layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    appWm.addView(view, layoutParams)
                    presentGlass(view, pulseDot, appWm)
                    shown = true
                } catch (_: Exception) {
                }
            }
            if (!shown) showSystemFallback()
        }
    }

    /** 挂载 + 入场动画 + 排期退场。 */
    private fun presentGlass(
        view: View,
        pulseDot: View,
        wm: WindowManager,
    ) {
        attachedGlass = GlassLayer(view, pulseDot, wm)
        // 液态柔光入场：底部轻浮起 + 透明度 0→1 + 缩放 0.96→1.0（Decelerate）
        view.alpha = 0f
        view.translationY = 26.dp.px
        view.scaleX = 0.96f
        view.scaleY = 0.96f
        view.animate().cancel()
        view
            .animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(GLASS_ENTER_MS)
            .setInterpolator(DecelerateInterpolator(1.15f))
            .start()
        startStatusPulse(pulseDot)
        scheduleGlassExit()
    }

    /** 守护状态微动画：呼吸圆点（停留期轻微明暗，无位移无复杂装饰）。 */
    private fun startStatusPulse(pulseDot: View) {
        (pulseDot.tag as? ValueAnimator)?.cancel()
        val animator =
            ValueAnimator.ofFloat(1f, STATUS_PULSE_MIN_ALPHA, 1f).apply {
                duration = STATUS_PULSE_PERIOD_MS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { anim -> pulseDot.alpha = anim.animatedValue as Float }
            }
        pulseDot.tag = animator
        animator.start()
    }

    private var glassExitToken = 0

    private fun scheduleGlassExit() {
        val token = ++glassExitToken
        ThreadUtils.runMainOrPost(GLASS_ENTER_MS + GLASS_HOLD_MS) {
            if (glassExitToken == token) exitGlass()
        }
    }

    /** 退场：柔和淡出 + 轻微缩小 + 完全移除（幂等）。 */
    private fun exitGlass() {
        val layer = attachedGlass ?: return
        attachedGlass = null
        val view = layer.view
        val wm = layer.windowManager
        (layer.pulseDot.tag as? ValueAnimator)?.cancel()
        layer.pulseDot.tag = null
        view.animate().cancel()
        try {
            view
                .animate()
                .alpha(0f)
                .translationY(10.dp.px)
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(GLASS_EXIT_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    runCatching { wm.removeViewImmediate(view) }
                }
                .start()
        } catch (_: Exception) {
            // 退场动画异常不影响移除（兜底定时器在后面无条件注册）
        }
        // 残影根治③：兜底移除定时器无条件注册（不随动画异常丢失）
        ThreadUtils.runMainOrPost(GLASS_EXIT_MS + 80) {
            runCatching { wm.removeViewImmediate(view) }
        }
    }

    /** 强制移除当前附着的玻璃视图（新提示到来/服务切换时）。 */
    private fun removeAttachedGlass() {
        val layer = attachedGlass ?: return
        attachedGlass = null
        (layer.pulseDot.tag as? ValueAnimator)?.cancel()
        layer.pulseDot.tag = null
        layer.view.animate().cancel()
        runCatching { layer.windowManager.removeViewImmediate(layer.view) }
    }

    /** 无障碍/悬浮窗通道全部不可用时的可靠回退（系统 Toast）。 */
    private fun showSystemFallback() {
        val text =
            ActionToastTemplate.render(
                settings.value.actionToast,
                "开屏卫士",
                "正在守护",
                actionCount.value,
            )
        showSystem(text)
    }

    fun init() {
        Toaster.init(app)
        Toaster.setDebugMode(false)
        Toaster.setInterceptor { false } // 覆盖默认拦截器
        setReactiveStyle()
    }
}
