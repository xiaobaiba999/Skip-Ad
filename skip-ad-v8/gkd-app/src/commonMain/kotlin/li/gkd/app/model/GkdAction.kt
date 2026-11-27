package li.gkd.app.model

import kotlinx.serialization.Serializable
import li.gkd.app.subscription.PositionError
import li.gkd.app.subscription.RawSubscription

@Serializable
data class GkdAction(
    val selector: String,
    val fastQuery: Boolean = false,
    val action: String? = null,
    override val position: RawSubscription.Position? = null,
    override val swipeArg: RawSubscription.SwipeArg? = null,
) : RawSubscription.LocationProps

@Serializable
data class ActionResult(
    val action: String,
    val result: Boolean,
    val shell: Boolean = false,
    val position: Pair<Float, Float>? = null,
    val coordinateError: PositionError? = null,
    /** v8.0.8：动作执行耗时（毫秒，perform 前后实测），随详细引擎日志记录 */
    val durationMs: Long? = null,
    /**
     * v8.1.0：失败原因细分（13 类失败原因的类 7/8/10 细分）。
     * · node_invisible——目标节点在匹配与执行之间变为不可见（宁可失败不误点）
     * · node_action_rejected——节点 performAction 返回 false（节点不可点击/已失效）
     * · gesture_unavailable——手势派发不可用（无特权通道且无障碍手势回退缺失）
     * · swipe_arg_missing——swipe 动作缺少 swipeArg 定义
     * 坐标计算失败仍由 coordinateError 承载（outcomeReason=coordinate_calculation_failed）。
     */
    val failureKind: String? = null,
) {
    val outcomeReason: String
        get() =
            when {
                result -> "action_succeeded"
                failureKind != null -> failureKind
                coordinateError != null -> "coordinate_calculation_failed"
                else -> "action_failed"
            }
}
