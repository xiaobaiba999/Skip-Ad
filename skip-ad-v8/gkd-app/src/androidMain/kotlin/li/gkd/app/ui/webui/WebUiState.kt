package li.gkd.app.ui.webui

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.data.subscription.SubscriptionState
import li.gkd.db.Db
import li.gkd.db.RuleGroupType
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.settings.SubscriptionUpdateInterval
import li.gkd.app.service.A11yService
import li.gkd.app.state.EngineRuntimeCounters
import li.gkd.app.subscription.EmbeddedSubscriptions
import li.gkd.app.subscription.RawSubscriptionParser

/**
 * WebView UI 数据聚合层（v8.0.5）
 * ─────────────────────────────────────────────
 * 把 GKD 功能内核（订阅/规则统计/ActionLog/设置）聚合为
 * 7.15.14 H5（assets/www/app.js）消费的 JSON 结构——字段名与
 * 语义与 7.15.14 Java 侧（AdSkipService.snapshotState /
 * SubscriptionManager.stateJson / Bridge.deviceInfo 等）完全对齐：
 *
 * · state 快照：stats{auto,popup,feed,back,feedback,manual,byApp} + log[]
 *   —— 数据源：RuleTriggerStats（累计触发，按订阅组名分类）+ ActionLog（滚动日志）
 * · subState：内置（APK 内嵌快照统计）+ 远程（当前库内订阅统计）双源，
 *   与 7.15.14「内置源 APK 快照 / 市场源日更」语义同构
 * · stats 分类：组名含「开屏/splash」→ auto；「弹窗/更新/评价/青少年…」→ popup；
 *   「信息流/广告卡片」→ feed；其余 → auto（默认秒跳）
 */
object WebUiState {

    init {
        // 8.0.6 修复：应用列表就绪瞬间立即失效白名单/state 缓存——旧版 30s 白名单缓存
        // 会把「snapshot 未就绪时构建的包名标签」钉住，白名单页先包名后名称的时序缺陷
        appScope.launch {
            li.gkd.app.app.AppInfoRepository.snapshots.collect {
                if (wlMemo != null || stateMemo != null) {
                    wlMemo = null
                    stateMemo = null
                }
            }
        }
    }

    // ───────── 短缓存（高频轮询防抖：首页每 5s 拉一次 state/subState）─────────
    private var stateMemo: Pair<Long, String>? = null
    private var subMemo: Pair<Long, String>? = null
    private var energyMemo: Pair<Long, String>? = null

    const val STATE_TTL = 2000L
    const val SUB_TTL = 4000L

    /** JSON 字符串转义（与 7.15.14 jsonStr 同规则） */
    internal fun jsonStr(s: String?): String {
        if (s == null) return "\"\""
        val b = StringBuilder(s.length + 8)
        b.append('"')
        for (c in s) {
            when {
                c == '"' -> b.append("\\\"")
                c == '\\' -> b.append("\\\\")
                c == '\n' -> b.append("\\n")
                c == '\r' -> b.append("\\r")
                c == '\t' -> b.append("\\t")
                c < ' ' -> b.append(String.format("\\u%04x", c.code))
                else -> b.append(c)
            }
        }
        b.append('"')
        return b.toString()
    }

    /** 7.15.14 shortPkg 语义：名称解析失败时的标签回退（末段；通用词尾取两段） */
    internal fun labelFallback(pkg: String): String {
        if (!pkg.contains('.')) return pkg
        val parts = pkg.split('.')
        val last = parts.last()
        return if (last in GENERIC_TAILS && parts.size >= 3) {
            parts[parts.size - 2] + "." + last
        } else {
            last
        }
    }

    private val GENERIC_TAILS =
        setOf("android", "app", "main", "ui", "mobile", "phone", "client", "core", "sdk", "online", "lite")

    // ══════════ state 快照（kvGet("state") 的真实数据源）══════════

    suspend fun snapshotState(force: Boolean = false): String {
        stateMemo?.let { (at, v) ->
            if (!force && System.currentTimeMillis() - at < STATE_TTL) return v
        }
        val json = withContext(Dispatchers.IO) { buildStateJson() }
        stateMemo = System.currentTimeMillis() to json
        return json
    }

    private suspend fun buildStateJson(): String {
        val settings = SettingsRepository.settings.value
        // 1. 组名映射（订阅 id + 组类型 + 组 key → 组名）
        val groupNames = buildGroupNameMap()
        // 2. 累计触发统计（按组名分类 + byApp 聚合）
        val stats = Db.ruleTriggerStatsDao.queryAll()
        var auto = 0L
        var popup = 0L
        var feed = 0L
        val byApp = LinkedHashMap<String, MutableMap<String, Long>>()
        val hitsBySub = HashMap<Long, Long>()
        for (st in stats) {
            val name = groupNames[Triple(st.subsId, st.groupType, st.groupKey)].orEmpty()
            val kind = classifyGroup(name)
            when (kind) {
                "popup" -> popup += st.triggerCount
                "feed" -> feed += st.triggerCount
                else -> auto += st.triggerCount
            }
            val m = byApp.getOrPut(st.appId) { linkedMapOf("auto" to 0L, "popup" to 0L, "feed" to 0L, "manual" to 0L) }
            m[kind] = (m[kind] ?: 0L) + st.triggerCount
            hitsBySub[st.subsId] = (hitsBySub[st.subsId] ?: 0L) + st.triggerCount
        }
        // 3. 滚动日志（最近 16 条 → 7.15.14 log[{t,msg}]；LIMIT 下推免全表扫描后截断）
        val logs = Db.actionLogDao.queryLatest(16L)
        val sb = StringBuilder(512)
        sb.append("{")
        sb.append("\"popupShield\":").append(settings.enableMatch)
        sb.append(",\"learning\":").append(settings.updateSubsInterval != SubscriptionUpdateInterval.Paused)
        sb.append(",\"stats\":{\"auto\":").append(auto)
            .append(",\"popup\":").append(popup)
            .append(",\"feed\":").append(feed)
            .append(",\"back\":0,\"feedback\":0,\"manual\":0,\"byApp\":{")
        val top = byApp.entries.sortedByDescending { (_, m) -> m.values.sum() }.take(80)
        var first = true
        for ((pkg, m) in top) {
            if (!first) sb.append(",")
            first = false
            sb.append(jsonStr(pkg)).append(":{")
                .append("\"auto\":").append(m["auto"] ?: 0L)
                .append(",\"popup\":").append(m["popup"] ?: 0L)
                .append(",\"feed\":").append(m["feed"] ?: 0L)
                .append(",\"manual\":0}")
        }
        sb.append("}},\"log\":[")
        // 日志条目 msg：应用名 + 组名（应用名走 AppInfoRepository 缓存）
        var firstLog = true
        for (log in logs) {
            if (!firstLog) sb.append(",")
            firstLog = false
            val appName = li.gkd.app.app.AppInfoRepository.snapshot?.apps?.get(log.appId)?.name ?: log.appId
            val groupName = groupNames[Triple(log.subsId, log.groupType, log.groupKey)].orEmpty()
            val msg = if (groupName.isNotEmpty()) "「$appName」触发「$groupName」规则" else "「$appName」触发拦截规则"
            sb.append("{\"t\":").append(log.ctime).append(",\"msg\":").append(jsonStr(msg)).append("}")
        }
        sb.append("],\"statsBySub\":{")
        var firstSub = true
        for ((id, n) in hitsBySub) {
            if (!firstSub) sb.append(",")
            firstSub = false
            sb.append("\"$id\":").append(n)
        }
        sb.append("}}")
        return sb.toString()
    }

    /** 订阅组名映射：subsId + groupType(App=2/Global=3) + groupKey → 组名 */
    private fun buildGroupNameMap(): Map<Triple<Long, Int, Int>, String> {
        val map = HashMap<Triple<Long, Int, Int>, String>()
        val subs = SubscriptionState.subsMapFlow.value
        for (sub in subs.values) {
            for (g in sub.globalGroups) {
                map[Triple(sub.id, RuleGroupType.Global, g.key)] = g.name
            }
            for (a in sub.apps) {
                for (g in a.groups) {
                    map[Triple(sub.id, RuleGroupType.App, g.key)] = g.name
                }
            }
        }
        return map
    }

    /** 组名 → 7.15.14 stats 分类键 */
    private fun classifyGroup(name: String): String {
        val n = name.lowercase()
        return when {
            n.contains("开屏") || n.contains("splash") -> "auto"
            n.contains("信息流") || n.contains("广告卡片") || n.contains("沉浸") -> "feed"
            n.contains("弹窗") || n.contains("更新") || n.contains("评价") ||
                n.contains("青少年") || n.contains("权限") || n.contains("通知") ||
                n.contains("会员") || n.contains("dialog") || n.contains("popup") -> "popup"
            else -> "auto"
        }
    }

    // ══════════ subState（7.15.14 SubscriptionManager.stateJson 同构）══════════

    suspend fun subState(force: Boolean = false): String {
        subMemo?.let { (at, v) ->
            if (!force && System.currentTimeMillis() - at < SUB_TTL) return v
        }
        val json = withContext(Dispatchers.IO) { buildSubJson() }
        subMemo = System.currentTimeMillis() to json
        return json
    }

    private suspend fun buildSubJson(): String {
        // 内置源 = APK 内嵌快照（assets/subscriptions/{667,86,233}.json5 统计，永久缓存）
        val builtin = builtinStats()
        // 远程源 = 当前库内订阅（3 个内嵌种子，初始化来自 assets、之后网络日更）
        val subs = SubscriptionState.subsMapFlow.value
        var remoteApps = 0
        var remoteRules = 0
        var remoteMaxVersion = 0
        var remoteOk = false
        val names = ArrayList<String>(4)
        for (id in EmbeddedSubscriptions.ids) {
            val sub = subs[id] ?: continue
            remoteOk = true
            remoteApps += sub.apps.size
            remoteMaxVersion = maxOf(remoteMaxVersion, sub.version)
            sub.globalGroups.forEach { remoteRules += it.rules.size }
            sub.apps.forEach { a -> a.groups.forEach { remoteRules += it.rules.size } }
            names.add(sub.name)
        }
        val remoteName = when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.joinToString(" / ") + " 等社区订阅"
        }
        // 命中统计（RuleTriggerStats by subsId）
        val hits = HashMap<Long, Long>()
        for (st in Db.ruleTriggerStatsDao.queryAll()) {
            hits[st.subsId] = (hits[st.subsId] ?: 0L) + st.triggerCount
        }
        var remoteHits = 0L
        var builtinHits = 0L
        for ((id, n) in hits) {
            if (EmbeddedSubscriptions.ids.contains(id)) remoteHits += n
        }
        builtinHits = remoteHits // 同一套订阅（内置=初始快照、远程=当前版本），命中一致
        // 未更新过任何订阅 → 「当前离线快照兜底」标记（7.15.14 snapshot 语义）
        val snapPending = remoteOk && remoteMaxVersion <= builtin.maxVersion

        val sb = StringBuilder(320)
        sb.append("{")
        sb.append("\"builtinApps\":").append(builtin.apps)
        sb.append(",\"builtinRules\":").append(builtin.rules)
        sb.append(",\"remoteOk\":").append(remoteOk)
        if (remoteOk) {
            sb.append(",\"remoteName\":").append(jsonStr(remoteName))
            sb.append(",\"remoteVersion\":").append(remoteMaxVersion)
            sb.append(",\"remoteApps\":").append(remoteApps)
            sb.append(",\"remoteRules\":").append(remoteRules)
        }
        sb.append(",\"remoteEnabled\":true")
        sb.append(",\"updatedAt\":").append(System.currentTimeMillis())
        sb.append(",\"remoteHits\":").append(remoteHits)
        sb.append(",\"builtinHits\":").append(builtinHits)
        sb.append(",\"prunedApps\":0")
        sb.append(",\"error\":\"\"")
        if (snapPending) sb.append(",\"snapshot\":true")
        sb.append("}")
        return sb.toString()
    }

    // ══════════ 内嵌订阅 assets 统计（永久缓存）══════════

    private data class BuiltinStats(val apps: Int, val rules: Int, val maxVersion: Int)

    private var builtinMemo: BuiltinStats? = null

    private fun builtinStats(): BuiltinStats {
        builtinMemo?.let { return it }
        var apps = 0
        var rules = 0
        var maxVersion = 0
        try {
            for (seed in EmbeddedSubscriptions.seeds) {
                val text = app.assets.open("subscriptions/${seed.id}.json5")
                    .bufferedReader().use { it.readText() }
                val sub = RawSubscriptionParser.parse(text)
                apps += sub.apps.size
                maxVersion = maxOf(maxVersion, sub.version)
                sub.globalGroups.forEach { rules += it.rules.size }
                sub.apps.forEach { a -> a.groups.forEach { rules += it.rules.size } }
            }
        } catch (_: Throwable) {
        }
        val v = BuiltinStats(apps, rules, maxVersion)
        builtinMemo = v
        return v
    }

    // ══════════ 设备信息（7.15.14 Bridge.deviceInfo 同构）══════════

    fun deviceInfo(): String {
        val brand = Build.BRAND ?: ""
        val model = Build.MODEL ?: ""
        val osName = "Android ${Build.VERSION.RELEASE ?: ""}"
        val sdk = Build.VERSION.SDK_INT
        return "{\"brand\":${jsonStr(brand)},\"model\":${jsonStr(model)}," +
            "\"os\":${jsonStr(osName)},\"sdk\":$sdk," +
            "\"version\":\"v${META.versionName}\",\"versionCode\":${META.versionCode}}"
    }

    // ══════════ 能耗画像（v8.0.7：真实计数 + 旧版系数估算）══════════

    /**
     * events/scans：真实计数（进程本次启动以来，EngineRuntimeCounters）；
     * microWh：旧版 7.15.14 PowerGovernor 系数估算（事件×0.22 + 扫描×0.72 µWh，非实测）；
     * probes：新引擎无探针机制，返回 JSON null（不伪造 0）；sinceBoot=false（口径为进程启动非开机）。
     */
    suspend fun energyStats(): String {
        energyMemo?.let { (at, v) ->
            if (System.currentTimeMillis() - at < STATE_TTL) return v
        }
        val json = withContext(Dispatchers.IO) {
            val events = EngineRuntimeCounters.eventCount()
            val scans = EngineRuntimeCounters.scanCount()
            // 能耗估算系数沿用旧版 7.15.14 PowerGovernor（事件×0.22 + 扫描×0.72 µWh，标注估算非实测）
            val microWh = events * 22 / 100 + scans * 72 / 100
            val running = A11yService.isRunning.value
            "{\"ok\":true,\"events\":$events,\"scans\":$scans,\"microWh\":$microWh,\"running\":$running,\"sinceBoot\":false,\"probes\":null}"
        }
        energyMemo = System.currentTimeMillis() to json
        return json
    }

    // ══════════ 白名单聚合（known 上限 500，与 7.15.14 语义一致）══════════

    private var wlMemo: Pair<Long, Pair<Set<String>, String>>? = null
    const val WL_TTL = 30_000L
    @Volatile private var lastSnapshotReady = false

    suspend fun whitelistPayload(force: Boolean = false): Pair<Set<String>, String> {
        wlMemo?.let { (at, v) ->
            if (!force && System.currentTimeMillis() - at < WL_TTL) return v
        }
        val payload = withContext(Dispatchers.IO) {
            val blocked = SettingsRepository.blockMatchAppList.value
            val known = ArrayList<Pair<String, String>>(256) // pkg to label
            // 已安装可见应用 ∪ 白名单 ∪ 战绩 byApp
            val snapshot = li.gkd.app.app.AppInfoRepository.snapshot
            val byApp = runCatching {
                Db.ruleTriggerStatsDao.queryAll().map { it.appId }.toSet()
            }.getOrDefault(emptySet())
            val seen = HashSet<String>(512)
            if (snapshot != null) {
                for (info in snapshot.apps.values) {
                    if (info.isSystem && !blocked.contains(info.id)) continue
                    if (seen.add(info.id)) {
                        // 名称等于包名（loadLabel 失败回退）→ 末段回退展示
                        val label =
                            if (info.name == info.id) labelFallback(info.id) else info.name
                        known.add(info.id to label)
                    }
                }
            }
            for (pkg in byApp) {
                if (seen.add(pkg)) known.add(pkg to labelFallback(pkg))
            }
            for (pkg in blocked) {
                if (seen.add(pkg)) known.add(pkg to labelFallback(pkg))
            }
            known.sortWith(compareByDescending<Pair<String, String>> { blocked.contains(it.first) }
                .thenBy { it.second })
            val capped = known.take(500)
            val sb = StringBuilder(8192)
            sb.append("{\"snapshotReady\":").append(snapshot != null).append(",\"list\":[")
            var first = true
            for (pkg in blocked) {
                if (!first) sb.append(",")
                first = false
                sb.append(jsonStr(pkg))
            }
            sb.append("],\"known\":[")
            var kf = true
            for ((pkg, label) in capped) {
                if (!kf) sb.append(",")
                kf = false
                sb.append("{\"pkg\":").append(jsonStr(pkg))
                    .append(",\"label\":").append(jsonStr(label)).append("}")
            }
            sb.append("]}")
            lastSnapshotReady = snapshot != null
            blocked to sb.toString()
        }
        // snapshot 未就绪：不落 30s 缓存（前端 1.5s 重试可立即拿到就绪后的完整名单）
        if (lastSnapshotReady) wlMemo = System.currentTimeMillis() to payload else wlMemo = null
        return payload
    }

    fun invalidateWhitelist() {
        wlMemo = null
    }

    // ══════════ 崩溃堆栈（上传日志附带，读后即清——7.15.14 语义）══════════

    fun crashDump(): String {
        return try {
            val pending = li.gkd.app.crash.FileCrashStorage.takePending()
            val current = li.gkd.app.crash.FileCrashStorage.load()
            (pending.asSequence() + current.asSequence())
                .mapNotNull { it.stackTrace }
                .take(2)
                .joinToString("\n\n——\n\n")
                .let { it.take(8000) }
        } catch (_: Throwable) {
            ""
        }
    }
}
