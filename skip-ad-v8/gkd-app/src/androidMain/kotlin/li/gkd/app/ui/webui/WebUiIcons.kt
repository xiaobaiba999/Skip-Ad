package li.gkd.app.ui.webui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream
import li.gkd.app.data.appinfo.PackageAppCatalog
import li.gkd.app.model.AppInfo
import li.gkd.app.util.AndroidTarget

/** 白名单应用图标 → data URL（48px WEBP·base64）。
 *  绘制在调用线程（WebView JavaBridge 线程，每批 ≤30 个），结果由桥层 LRU 缓存；
 *  PackageAppCatalog.icon 的 Drawable 缓存在应用列表刷新时已预取。
 *  minSdk=26：API 30+ 用 WEBP_LOSSY，以下用 deprecated WEBP（同为带 alpha 有损 webp），
 *  相比 PNG(100) 体积小一个量级，白名单批量加载更快。 */
internal object WebUiIcons {
    private const val SIZE = 48

    private val iconFormat
        get() =
            if (AndroidTarget.R) Bitmap.CompressFormat.WEBP_LOSSY
            else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP

    fun iconDataUrl(info: AppInfo): String {
        val drawable: Drawable = PackageAppCatalog.icon(info) ?: return ""
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, SIZE, SIZE)
            drawable.draw(canvas)
            val bos = ByteArrayOutputStream(4096)
            bmp.compress(iconFormat, 90, bos)
            val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            return "data:image/webp;base64,$b64"
        } finally {
            bmp.recycle()
        }
    }
}
