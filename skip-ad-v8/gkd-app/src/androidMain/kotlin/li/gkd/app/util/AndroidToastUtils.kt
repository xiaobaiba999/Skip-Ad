package li.gkd.app.util

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
                    showA11y(text)
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
            if (useSystemToast) showSystem(text) else showA11y(text)
        }
    }

    private var cancelCustomToast: (() -> Unit)? = null
    private var cacheToast: Toast? = null

    private fun showSystem(message: CharSequence) {
        cancelCustomToast?.invoke()
        cacheToast?.cancel()
        cacheToast =
            Toast.makeText(app, message, Toast.LENGTH_SHORT).apply {
                show()
            }
    }

    // ───────── v8.0.8 液态柔光玻璃提示（Liquid Glass）─────────

    /** 玻璃容器圆角（dp）。 */
    private val glassRadius = 26.dp.px

    /** 玻璃态显隐时长（ms）：入场 260 + 停留 1900 + 淡出 220。 */
    private const val GLASS_ENTER_MS = 260L
    private const val GLASS_HOLD_MS = 1900L
    private const val GLASS_EXIT_MS = 220L

    /** 品牌底色（与卫士主色系一致：苔绿）。 */
    private const val BRAND_TINT = 0x1A33502F

    /**
     * 液态柔光玻璃背景：多层渐变模拟玻璃质感（纯 GradientDrawable 实现，全 Android
     * 版本通用——不依赖模糊 API，overlay 窗口无法使用背景模糊，渐变+描边+柔影即基线）。
     */
    private fun glassBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            if (darkTheme) {
                // 深色玻璃：近黑绿底 双向弱渐变 + 内侧高光描边
                orientation = GradientDrawable.Orientation.TL_BR
                colors =
                    intArrayOf(
                        0xE61C231E.toInt(),
                        0xCC17201B.toInt(),
                    )
                setStroke(
                    (1.2f * app.resources.displayMetrics.density).toInt(),
                    0x2EFFFFFF,
                )
            } else {
                // 浅色玻璃：白底 双向弱渐变 + 内侧高光描边（品牌绿弱着色）
                orientation = GradientDrawable.Orientation.TL_BR
                colors =
                    intArrayOf(
                        0xF2FFFFFF.toInt(),
                        0xD9EDF5EE.toInt(),
                    )
                setStroke(
                    (1.2f * app.resources.displayMetrics.density).toInt(),
                    0x66FFFFFF,
                )
            }
            cornerRadius = glassRadius
            // 轻微品牌着色（玻璃透出底下的品牌色感）
            setTint(BRAND_TINT)
            setTintBlendMode(android.graphics.BlendMode.SRC_OVER)
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

    private fun buildGlassView(message: CharSequence): View {
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
        title.text = message
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        title.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        title.setTextColor(if (dark) 0xFFF2FBF4.toInt() else 0xFF1E3A2C.toInt())
        title.ellipsize = TextUtils.TruncateAt.END
        title.maxLines = 1

        val subtitle = TextView(app)
        subtitle.text = "正在守护"
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        subtitle.setTextColor(if (dark) 0x99D7EEDD.toInt() else 0x8C33502F.toInt())
        subtitle.ellipsize = TextUtils.TruncateAt.END
        subtitle.maxLines = 1

        textCol.addView(title)
        textCol.addView(subtitle)
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
        // 柔影（玻璃悬浮感）
        row.elevation = 10.dp.px
        row.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        return row
    }

    // 1.使用 WeakReference<View> 在某些机型上导致无法取消
    // 2.使用协程 delay + cacheView 也可能导致无法取消
    // https://github.com/gkd-kit/gkd/issues/697
    // https://github.com/gkd-kit/gkd/issues/698
    private fun showA11y(message: CharSequence) {
        cancelCustomToast?.invoke()
        cacheToast?.cancel()
        cacheToast = null
        val wm =
            A11yService.instance?.wm
                ?: if (PermissionStates.drawOverlays.updateAndGet()) app.windowManager else null
        if (wm == null) {
            showSystem(message)
            return
        }
        val view = buildGlassView(message)
        val layoutParams =
            WindowManager.LayoutParams().apply {
                type =
                    if (wm == app.windowManager) {
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
                // v8.0.8：入场/退场动画由视图自绘（位移+透明度），不再用系统 Toast 动画
            }
        wm.addView(view, layoutParams)
        // 液态柔光入场：底部轻柔浮起（位移 + 透明度，Decelerate）
        view.alpha = 0f
        view.translationY = 28.dp.px
        view
            .animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(GLASS_ENTER_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        val cancel: () -> Unit = {
            cancelCustomToast = null
            try {
                // 淡出后移除窗口（动画结束回调里兜底 removeViewImmediate）
                view
                    .animate()
                    .alpha(0f)
                    .translationY(18.dp.px)
                    .setDuration(GLASS_EXIT_MS)
                    .withEndAction {
                        try {
                            wm.removeViewImmediate(view)
                        } catch (_: Exception) {}
                    }
                    .start()
                // 兜底：极端情况下 endAction 不执行（视图已 detach），超时强制移除
                ThreadUtils.runMainOrPost(GLASS_EXIT_MS + 80) {
                    try {
                        wm.removeViewImmediate(view)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        cancelCustomToast = cancel
        ThreadUtils.runMainOrPost(GLASS_ENTER_MS + GLASS_HOLD_MS) {
            if (cancelCustomToast === cancel) {
                cancel()
            }
        }
    }

    fun init() {
        Toaster.init(app)
        Toaster.setDebugMode(false)
        Toaster.setInterceptor { false } // 覆盖默认拦截器
        setReactiveStyle()
    }
}
