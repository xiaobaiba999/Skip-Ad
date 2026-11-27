package li.gkd.app.ui.webui

import android.view.ViewGroup
import android.webkit.WebView

/**
 * v8.0.9 WebView 销毁协议（进入/退出卫士界面残影根治）
 * ─────────────────────────────────────────
 * 根因：destroy() 在 WebView 仍 attach 到视图树时调用——Chromium 的
 * SurfaceView Surface 注销时序与窗口注销竞态，退出后 Surface 内容
 * 残留 → 「残影/重影/旧悬浮层」观感。
 * 协议（顺序敏感）：先从视图树 detach（removeView）→
 * removeJavascriptInterface → stopLoading → destroy。
 *
 * 抽象为纯接口（Target）使「先 detach 再 destroy」顺序可在
 * androidHostTest 单测校验（见 WebViewReleaseProtocolTest）；
 * WebView 适配重载供 MainActivity（onRelease 主路径 + onDestroy
 * 兜底）与 WebViewPage.android.kt 共用。
 */
object WebViewRelease {
    /** 销毁协议步骤的纯抽象（与 android.webkit 解耦，便于单测记录调用顺序） */
    interface Target {
        fun detachFromParent()

        fun removeJavascriptInterface(name: String)

        fun stopLoading()

        fun destroy()
    }

    /** 顺序敏感：detach 必须先于 destroy（见文件头根因说明） */
    fun run(target: Target, jsInterfaceName: String) {
        target.detachFromParent()
        target.removeJavascriptInterface(jsInterfaceName)
        target.stopLoading()
        target.destroy()
    }

    /** WebView 适配器（MainActivity "AndroidApp" / WebViewPage "gkd" 共用此协议） */
    fun run(view: WebView, jsInterfaceName: String) {
        run(
            object : Target {
                override fun detachFromParent() {
                    (view.parent as? ViewGroup)?.removeView(view)
                }

                override fun removeJavascriptInterface(name: String) {
                    view.removeJavascriptInterface(name)
                }

                override fun stopLoading() {
                    view.stopLoading()
                }

                override fun destroy() {
                    view.destroy()
                }
            },
            jsInterfaceName,
        )
    }
}
