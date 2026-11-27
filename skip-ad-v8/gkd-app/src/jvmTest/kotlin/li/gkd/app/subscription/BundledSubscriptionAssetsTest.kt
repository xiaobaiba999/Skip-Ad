package li.gkd.app.subscription

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 定位 gkd-android 内置订阅 assets（jvmTest 运行目录随调用方不同）。
 * Gradle `:gkd-app:jvmTest` 以模块目录为工作目录，IDE 单测可能是仓库根目录。
 */
internal object BundledSubscriptionAssets {
    val directory: File =
        sequenceOf(
                "gkd-android/src/main/assets/subscriptions",
                "../gkd-android/src/main/assets/subscriptions",
                "../../gkd-android/src/main/assets/subscriptions",
            )
            .map(::File)
            .firstOrNull { File(it, "667.json5").isFile }
            ?: error(
                "bundled subscriptions directory not found " +
                    "(user.dir=${System.getProperty("user.dir")})",
            )

    fun file(id: Long): File = File(directory, "$id.json5")

    fun read(id: Long): String = file(id).readText()
}

/**
 * v8.0.10 构建期校验闸（防再次静默截断）。
 *
 * 背景：内置 667.json5（Lin-arm v644）曾被截断（954KB / 完整约 1.4MB，JSON5 非法）
 * 打包进 APK——首装种子整体不入库、WebUI 内置统计归零，且无任何构建期报错。
 * 本测试在 jvmTest 阶段拦截同类回归：
 * · 字节下限（截断检测：667 以 1MB 为下限，86/233 以 100KB 为下限）
 * · RawSubscriptionParser.parse 成功（JSON5 解析 + 结构映射 + 补丁层全链路）
 * · 规模下限（apps 数；667 另要求版本 ≥ 上游 Lin-arm v646）
 * · 667 全量 RuleGroupCompilation（选择器语法 + 类型双闸，2547 组逐组编译）
 */
class BundledSubscriptionAssetsTest {
    private data class Expectation(val id: Long, val minBytes: Long, val minApps: Int)

    private val expectations =
        listOf(
            Expectation(id = 86L, minBytes = 100_000L, minApps = 70),
            Expectation(id = 233L, minBytes = 100_000L, minApps = 600),
            Expectation(id = 667L, minBytes = 1_000_000L, minApps = 1000),
        )

    @Test
    fun bundledAssetsAreCompleteAndParsable() {
        for (expectation in expectations) {
            val file = BundledSubscriptionAssets.file(expectation.id)
            assertTrue(file.isFile, "bundled subscription exists: ${file.path}")
            val bytes = file.length()
            assertTrue(
                bytes > expectation.minBytes,
                "subscription ${expectation.id}.json5 bytes=$bytes " +
                    "must exceed ${expectation.minBytes} (truncation regression guard)",
            )
            val subscription = RawSubscriptionParser.parse(BundledSubscriptionAssets.read(expectation.id))
            assertEquals(expectation.id, subscription.id, "subscription id mismatch")
            assertTrue(
                subscription.apps.size > expectation.minApps,
                "subscription ${expectation.id} apps=${subscription.apps.size} " +
                    "must exceed ${expectation.minApps}",
            )
        }
    }

    @Test
    fun subscription667TracksUpstreamV646() {
        val subscription = RawSubscriptionParser.parse(BundledSubscriptionAssets.read(667L))
        assertTrue(
            subscription.version >= 646,
            "内置 667 至少为上游 Lin-arm v646（当前 v${subscription.version}）",
        )
        val groupCount = subscription.globalGroups.size + subscription.apps.sumOf { it.groups.size }
        assertTrue(
            groupCount >= 2500,
            "667 组数 $groupCount 应 ≥ 2500（上游 v646 为 2547，截断版约 1690）",
        )
    }

    /** 语法 + 类型双闸全量扫描：667 的每个组（含补丁层改写后的组）必须全部编译通过。 */
    @Test
    fun subscription667AllGroupsCompile() {
        val subscription = RawSubscriptionParser.parse(BundledSubscriptionAssets.read(667L))
        subscription.globalGroups.forEach { group ->
            assertTrue(
                group.valid,
                "667 globalGroup key=${group.key} ${group.name}: ${group.validationError}",
            )
        }
        subscription.apps.forEach { app ->
            app.groups.forEach { group ->
                assertTrue(
                    group.valid,
                    "667 app ${app.id} group key=${group.key} ${group.name}: ${group.validationError}",
                )
            }
        }
    }
}
