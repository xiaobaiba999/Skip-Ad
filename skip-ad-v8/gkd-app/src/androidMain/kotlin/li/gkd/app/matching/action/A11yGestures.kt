package li.gkd.app.matching.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.matching.diagnostics.observeActionOperation

/**
 * The system owns a submitted gesture. Cancelling its caller cannot retract it: keep the input lane
 * occupied until the completion/cancellation callback arrives.
 */
suspend fun AccessibilityService.awaitGesture(gesture: GestureDescription): Boolean {
    currentCoroutineContext().ensureActive()
    val result = withContext(NonCancellable) { dispatchAndAwaitGesture(gesture) }
    currentCoroutineContext().ensureActive()
    return result == GestureSegmentResult.Completed
}

private suspend fun AccessibilityService.dispatchAndAwaitGesture(
    gesture: GestureDescription,
): GestureSegmentResult {
    val capabilities = serviceInfo?.capabilities ?: 0
    if (capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES == 0) {
        return GestureSegmentResult.Rejected
    }
    val finished = CompletableDeferred<GestureSegmentResult>()
    val submitted =
        observeActionOperation("AccessibilityService.dispatchGesture") {
            dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        finished.complete(GestureSegmentResult.Completed)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        finished.complete(GestureSegmentResult.Cancelled)
                    }
                },
                null,
            )
        }
    if (!submitted) return GestureSegmentResult.Rejected
    return observeActionOperation("GestureCallbackWait") {
        // v8.0.8：回调丢失兜底——服务中途断开时系统可能既不回调 onCompleted 也不回调
        // onCancelled，finished.await() 将永久挂起并泄漏 worker。真实手势时长
        // 远小于 10s（点击 <100ms / 滑动 <1s），超时按已取消处理并释放调度车道。
        withTimeoutOrNull(GESTURE_CALLBACK_TIMEOUT_MS) { finished.await() }
            ?: GestureSegmentResult.Cancelled
    }
}

/** 手势回调等待上限：远超任何真实手势时长，仅用于回调丢失兜底。 */
private const val GESTURE_CALLBACK_TIMEOUT_MS = 10_000L

suspend fun AccessibilityService.awaitSwipe(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    durationMillis: Long,
): Boolean =
    continuousSwipe<GestureDescription.StrokeDescription>(
        startX,
        startY,
        endX,
        endY,
        durationMillis,
        create = { previous, segment ->
            val path =
                Path().apply {
                    moveTo(segment.start.x.toFloat(), segment.start.y.toFloat())
                    if (segment.start != segment.end)
                        lineTo(segment.end.x.toFloat(), segment.end.y.toFloat())
                }
            previous?.continueStroke(path, 0, segment.durationMillis, segment.continues)
                ?: GestureDescription.StrokeDescription(
                    path,
                    0,
                    segment.durationMillis,
                    segment.continues,
                )
        },
        dispatch = { dispatchAndAwaitGesture(GestureDescription.Builder().addStroke(it).build()) },
    )
