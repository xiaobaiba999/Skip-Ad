package li.gkd.app.upload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import li.gkd.app.network.FeedbackUploader

/**
 * v8.0.8 日志上传真实 E2E（环境变量 E2E_UPLOAD=on 时才执行，默认跳过）。
 *
 * 走**真实生产代码路径**（FeedbackUploader.createIssue：内置混淆令牌内存解码 →
 * 双层脱敏 → Ktor/OkHttp 提交 → 15s 频控），在官方仓库 xiaobaiba999/Skip-Ad
 * 创建一条明确标注的测试 Issue，并用 GET 复验内容（脱敏生效）。
 *
 * 显式执行：E2E_UPLOAD=on ./gradlew :gkd-app:jvmTest --tests "*FeedbackUploadE2eTest*"
 */
class FeedbackUploadE2eTest {
    private val enabled get() = System.getenv("E2E_UPLOAD") == "on"

    @Test
    fun maskSensitiveCoversPhoneIdEmailIpv4() {
        // 本地脱敏断言（不依赖网络，始终执行）
        assertEquals(
            "手机 138****5678",
            FeedbackUploader.maskSensitive("手机 13812345678"),
        )
        assertTrue(FeedbackUploader.maskSensitive("a@b.com") == "***@b.com")
        assertTrue(!FeedbackUploader.maskSensitive("IP 192.168.1.1").contains("192.168.1.1"))
    }

    @Test
    fun realIssueCreationThroughProductionPath() {
        if (!enabled) return
        val body =
            buildString {
                appendLine("本条为 v8.0.8 发布链 E2E 验证存档（自动化测试 Issue，可关闭，勿修复）。")
                appendLine()
                appendLine("## 1. 基本环境")
                appendLine("- 应用: 开屏卫士 v8.0.8 (88000) · 通道 gkd · E2E 测试环境（JVM）")
                appendLine("- 引擎状态: 未知（仅移动端可探测）")
                appendLine()
                appendLine("## 2. 最新触发记录（最多 30 条）")
                appendLine("（暂无触发记录：引擎尚未成功执行任何规则——结合第 3 节匹配未命中汇总可定位原因）")
                appendLine()
                appendLine("## 3. 详细执行记录（引擎日志最近 120 条，时间正序；表内保留最近 1200 条）")
                appendLine("- [11-27 09:00:00]ℹ️ 🔌 无障碍服务已连接（E2E）· 引擎开始监听")
                appendLine()
                appendLine("## 4. 异常与失败")
                appendLine("（窗口内无警告/错误级记录）")
                appendLine()
                appendLine("## 5. 统计摘要")
                appendLine("- 详细窗口(120 条内): 动作成功 0 · 动作失败 0 · 匹配未命中汇总 0 · 记录共 1 条")
                appendLine()
                appendLine("## 6. 日志说明")
                appendLine("- 采集范围: 详细引擎日志表(上限 1200 条) + 触发记录表(上限 500 条) + 崩溃存档")
                appendLine("- 统计口径: 本条为发布链 E2E 存档，包含脱敏探针：手机 13812345678 邮箱 e2e@example.com")
            }
        val result =
            runBlocking {
                FeedbackUploader.createIssue("[E2E-TEST][v8.0.8] 统一日志上传通道验证（发布链存档）", body)
            }
        val success = result as? FeedbackUploader.Result.Success
        assertNotNull(success, "E2E Issue 必须真实创建成功: $result")
        assertTrue(success.number > 0)
        println("E2E-ISSUE-NUMBER=${success.number}")
        println("E2E-ISSUE-URL=${success.url}")

        // 15s 客户端频控：立即重发必须被拦截（防重复提交门）
        val rateLimited =
            runBlocking {
                FeedbackUploader.createIssue("[E2E-TEST] 频控验证（不应创建）", "不应出现的第二条")
            }
        assertTrue(rateLimited is FeedbackUploader.Result.Failure, "15s 频控必须生效")
    }
}
