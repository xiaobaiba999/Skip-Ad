package li.gkd.app.data

import android.view.accessibility.AccessibilityEvent
import li.gkd.app.model.A11yEventLogItem
import li.gkd.app.util.JsonUtils
import li.gkd.db.A11yEventLog

/**
 * 事件悬浮窗的待持久化日志。
 *
 * [rawText] 只保留原始字符串列表（a11y 事件线程零 JSON 编码成本）；
 * JSON 文本延迟到 EventService 的 flush 协程侧由 [encodeEventLog] 统一拼出，
 * 编码函数与字段完全不变，持久化的 A11yEventLog.text 与旧格式逐字节一致。
 */
class A11yPendingEventLog(
    val item: A11yEventLogItem,
    val rawText: List<String>,
)

fun A11yPendingEventLog.encodeEventLog(): A11yEventLog {
    val log = item.eventLog
    return A11yEventLog(
        id = log.id,
        ctime = log.ctime,
        type = log.type,
        appId = log.appId,
        name = log.name,
        desc = log.desc,
        text = JsonUtils.default.encodeToString(rawText),
    )
}

/**
 * item.eventLog.text 留空字符串：悬浮窗 UI 只读取 id/ctime/type/appId/name/desc
 * 与 parsedText（原始列表），不读 text；带真实 JSON 文本的实体由 flush 侧生成。
 */
fun AccessibilityEvent.toA11yPendingEventLog(id: Int): A11yPendingEventLog {
    val text = text.map { it.toString() }
    return A11yPendingEventLog(
        item =
            A11yEventLogItem(
                eventLog =
                    A11yEventLog(
                        id = id,
                        ctime = System.currentTimeMillis(),
                        type = eventType,
                        appId = packageName.toString(),
                        name = className.toString(),
                        desc = contentDescription?.toString(),
                        text = "",
                    ),
                parsedText = Result.success(text),
            ),
        rawText = text,
    )
}
