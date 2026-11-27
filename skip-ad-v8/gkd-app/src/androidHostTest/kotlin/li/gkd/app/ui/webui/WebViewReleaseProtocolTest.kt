package li.gkd.app.ui.webui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v8.0.9 WebView 销毁协议单测（进入/退出卫士界面残影根治）。
 *
 * 用记录型 [WebViewRelease.Target] 验证协议的核心顺序保证：
 * 「先 detach 再 destroy」——destroy 时 WebView 若仍 attach 到视图树，
 * Chromium SurfaceView 的 Surface 注销时序与窗口注销竞态，退出后
 * Surface 内容残留 → 残影/重影。MainActivity（AndroidView.onRelease
 * 主路径 + onDestroy 兜底）与 WebViewPage.android.kt 均经由
 * [WebViewRelease.run] 执行，此顺序即生产顺序。
 *
 * 纯逻辑测试（不触 android.webkit 实例），无需 Robolectric。
 */
class WebViewReleaseProtocolTest {
    private class RecordingTarget : WebViewRelease.Target {
        val calls = mutableListOf<String>()

        override fun detachFromParent() {
            calls += "detach"
        }

        override fun removeJavascriptInterface(name: String) {
            calls += "removeJavascriptInterface:$name"
        }

        override fun stopLoading() {
            calls += "stopLoading"
        }

        override fun destroy() {
            calls += "destroy"
        }
    }

    @Test
    fun detachAlwaysHappensBeforeDestroy() {
        val target = RecordingTarget()
        WebViewRelease.run(target, "AndroidApp")
        assertTrue(
            "detach 必须先于 destroy（destroy 时仍 attach 会致 Surface 注销时序错乱、退出残影）",
            target.calls.indexOf("detach") < target.calls.indexOf("destroy"),
        )
    }

    @Test
    fun protocolRunsEveryStepExactlyOnceInOrder() {
        val target = RecordingTarget()
        WebViewRelease.run(target, "gkd")
        assertEquals(
            listOf(
                "detach",
                "removeJavascriptInterface:gkd",
                "stopLoading",
                "destroy",
            ),
            target.calls,
        )
    }

    @Test
    fun destroyIsTheLastStep() {
        val target = RecordingTarget()
        WebViewRelease.run(target, "AndroidApp")
        assertEquals("destroy", target.calls.last())
    }
}
