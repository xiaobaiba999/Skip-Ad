import java.util.Locale
import kotlin.math.abs

/**
 * Skip-Ad 引擎热路径基准测试 —— Kotlin 实现（惯用风格）
 * 算法/数据/时序与 Java 参考版逐位一致（xorshift31 PRNG 同一棵树）。
 */

class Node(
    var text: String? = null,
    var vid: String? = null,
    var cls: String = "",
    var l: Int = 0, var t: Int = 0, var r: Int = 0, var b: Int = 0,
    var clickable: Boolean = false,
    var parent: Node? = null,
    val children: MutableList<Node> = mutableListOf()
)

data class Sel(
    val raw: String,
    val conds: MutableList<Triple<String, String, String>> = mutableListOf(),
    var rel: String = "",
    var relN: Int = 1,
    val posList: MutableList<Int> = mutableListOf()
)

object Bench {

    val APP_TEXT_RULES = arrayOf(
        arrayOf("com.sina.weibo", "跳过广告", "跳过"),
        arrayOf("com.ximalaya.ting.android", "跳过广告", "跳过"),
        arrayOf("tv.danmaku.bili", "跳过广告", "跳过"),
        arrayOf("com.qiyi.video", "跳过广告", "跳过"),
        arrayOf("com.youku.phone", "跳过广告", "跳过"),
        arrayOf("com.tencent.qqlive", "跳过广告", "跳过"),
        arrayOf("com.hunantv.imgo", "跳过广告", "跳过"),
        arrayOf("com.gotokeep.keep", "跳过"),
        arrayOf("com.xingin.xhs", "跳过"),
        arrayOf("com.zhihu.android", "跳过广告"),
        arrayOf("com.sankuai.meituan", "跳过广告"),
        arrayOf("com.dianping.v1", "跳过广告"),
        arrayOf("com.jingdong.app.mall", "跳过"),
        arrayOf("com.taobao.taobao", "跳过"),
        arrayOf("com.xunmeng.pinduoduo", "跳过"),
        arrayOf("com.dragon.read", "跳过广告"),
        arrayOf("com.baidu.searchbox", "跳过"),
        arrayOf("com.netease.cloudmusic", "跳过"),
        arrayOf("com.tencent.qqmusic", "跳过广告"),
        arrayOf("com.kugou.android", "跳过"),
        arrayOf("com.UCMobile", "跳过"),
        arrayOf("com.tencent.mtt", "跳过广告"),
        arrayOf("com.douban.frodo", "跳过广告"),
        arrayOf("cn.wps.moffice_eng", "跳过广告"),
        arrayOf("ctrip.android.view", "跳过"),
        arrayOf("com.taobao.trip", "跳过"),
        arrayOf("com.autonavi.minimap", "跳过"),
        arrayOf("com.sdu.didi.psnger", "跳过")
    )

    val TEXT_EXACT = arrayOf(
        "跳过", "跳过广告", "跳过此广告", "跳过这个广告", "关闭广告", "关闭此广告",
        "点击跳过", "上滑跳过", "跳过提示", "立即跳过",
        "跳过 ad", "跳过ad",
        "skip", "skip ad", "skipad", "skip this ad",
        "跳過", "跳過廣告", "跳過此廣告"
    )

    val CONTAINS_SHORT = arrayOf("跳过", "skip", "跳過")
    val DANGEROUS = arrayOf("领", "红包", "下载", "查看详情", "立即", "参与", "抽奖", "领取")
    val VID_SKIP_KEYS = arrayOf("skip", "tt_splash_skip_btn", "iv_skip", "count_down", "splash")

    val P_COUNTDOWN_TAIL_STR = """^(跳过|跳过广告|跳過廣告)\s*[0-9]+(s|秒)?$"""
    val P_COUNTDOWN_HEAD_STR = """^[0-9]+\s*(s|秒)?(后|後)可?(跳过|跳过广告|跳過廣告)$"""
    /* 预编译形式（optimized 模式 = v5.6.0 优化） */
    var patTail: Regex? = null
    var patHead: Regex? = null

    val GKD_SELECTORS = arrayOf(
        """[text*="跳过"]""", """[text*="跳过广告"]""", """[vid*="skip"]""",
        """[vid="tt_splash_skip_btn"]""", """[text="跳过广告 3"]""",
        """[text*="跳过" && vid*="count_down"]""",
        """[vid*="skip" >n2 [text*="广告"]""",
        """[text*="跳過廣告" || text*="skip"]""",
        """ImageView <n [vid*="splash"]""",
        """[text*="关闭广告" && clickable=true]""",
        """[vid*="iv_skip"] >n2 TextView""",
        """[text*="跳过"] -n [text*="广告"]""",
        """[text*="skip" && clickable=true] >n2 *""",
        """[vid*="close" && (text*="广告" || vid*="ad")]""",
        """[text*="跳过"][9]""", """[text*="跳过"][9,10]""",
        """* >n [vid*="splash_ad"]""",
        """[text*="跳过广告" >n2 [text*="查看详情"]""",
        """[vid*="count_down"] <n3 ImageView""",
        """[text*="上滑"] || [vid*="slide_skip"]""",
        """[text*="跳过" && visibleToUser=true] >n *""",
        """[vid*="ad_close" && text=null]""",
        """[text*="skip this ad"] >n2 [clickable=true]""",
        """[text*="跳過"][3,8]"""
    )

    val TEXT_POOL = arrayOf(
        "跳过广告 5", "跳过广告 3", "跳过", "立即领取红包", "限时秒杀·低至1折",
        "精选推荐", "新人专享礼包", "点击查看详情", "上滑解锁更多内容", "领取优惠券",
        "skip ad 5", "Skip", "广告", "立即下载App", "今日热点推荐", "签到领金币",
        null, null, null, null
    )
    val VID_POOL = arrayOf(
        "tt_splash_skip_btn", "iv_skip", "count_down_text", "splash_ad_view",
        "close_btn", "ad_container", "banner_view", null, null, null
    )
    val CLS_POOL = arrayOf(
        "android.widget.TextView", "android.widget.ImageView", "android.widget.Button",
        "android.view.ViewGroup", "android.webkit.WebView"
    )

    /* ── xorshift31（与 Java/JS/Python 逐位一致）── */
    var rngState = 0
    fun rnd(): Int {
        var x = rngState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rngState = x
        return x and 0x7FFFFFFF
    }
    fun rndRange(mod: Int): Int = rnd() % mod

    fun buildTree(seed: Int): Node {
        rngState = seed
        val root = Node(cls = "android.view.ViewGroup", l = 0, t = 0, r = 1080, b = 2400)
        repeat(6) {
            val grp = Node(
                cls = CLS_POOL[rndRange(CLS_POOL.size)],
                l = rndRange(1000), t = rndRange(2200)
            )
            grp.r = grp.l + 40 + rndRange(900); grp.b = grp.t + 40 + rndRange(160)
            grp.clickable = rndRange(2) == 0
            link(root, grp)
            val n = 5 + rndRange(5)
            repeat(n) {
                val c = Node()
                var text = TEXT_POOL[rndRange(TEXT_POOL.size)]
                if (text != null) {
                    if (rndRange(2) == 0 && text.length >= 4) {
                        text = text + " " + (1 + rndRange(9))
                    }
                    c.text = text
                }
                val vid = VID_POOL[rndRange(VID_POOL.size)]
                if (vid != null) c.vid = vid + "_" + rndRange(200)
                c.cls = CLS_POOL[rndRange(CLS_POOL.size)]
                c.l = grp.l + rndRange(maxOf(1, grp.r - grp.l - 80))
                c.t = grp.t + rndRange(maxOf(1, grp.b - grp.t - 30))
                val w = 30 + rndRange(700)
                val h = 20 + rndRange(80)
                c.r = c.l + w; c.b = c.t + h
                c.clickable = rndRange(4) == 0
                link(grp, c)
            }
        }
        return root
    }
    fun link(p: Node, c: Node) { c.parent = p; p.children.add(c) }

    var pkg: String = "com.sankuai.meituan"
    var appRuleIndex: Map<String, Array<String>> = emptyMap()
    var exactSet: Set<String> = emptySet()

    var hits = 0L
    var sink = 0L

    fun matchTree(root: Node, optimized: Boolean): String? {
        val stack = ArrayList<Node>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeAt(stack.size - 1)
            val text = n.text
            if (text != null) {
                val low = text.trim().lowercase(Locale.ROOT)
                val hit = matchText(n, low, optimized)
                if (hit != null) return hit
            }
            val vid = n.vid
            if (vid != null) {
                val v = vid.lowercase(Locale.ROOT)
                for (k in VID_SKIP_KEYS) {
                    if (v.contains(k) && shapeGate(n, true)) return "vid:$vid"
                }
            }
            for (i in n.children.size - 1 downTo 0) stack.add(n.children[i])
        }
        return null
    }

    fun matchText(n: Node, low: String, optimized: Boolean): String? {
        if (optimized) {
            val row = appRuleIndex[pkg]
            if (row != null) {
                for (i in 1 until row.size) {
                    if (low == row[i]) return "app:" + row[i]
                }
            }
        } else {
            for (row in APP_TEXT_RULES) {
                if (pkg.startsWith(row[0])) {
                    for (i in 1 until row.size) {
                        if (low == row[i]) return "app:" + row[i]
                    }
                }
            }
        }
        if (optimized) {
            if (low in exactSet && shapeGate(n, false)) return "exact:$low"
        } else {
            for (k in TEXT_EXACT) {
                if (low == k && shapeGate(n, false)) return "exact:$low"
            }
        }
        if (optimized) {
            if (patTail!!.matches(low) && shapeGate(n, false)) return "countdown:$low"
            if (patHead!!.matches(low) && shapeGate(n, false)) return "countdown:$low"
        } else {
            if (Regex(P_COUNTDOWN_TAIL_STR).matches(low) && shapeGate(n, false)) return "countdown:$low"
            if (Regex(P_COUNTDOWN_HEAD_STR).matches(low) && shapeGate(n, false)) return "countdown:$low"
        }
        if (low.length <= 8) {
            val cont = CONTAINS_SHORT.any { low.contains(it) }
            if (cont) {
                if (DANGEROUS.any { low.contains(it) }) return null
                if (shapeGate(n, false)) return "contains:$low"
            }
        }
        return null
    }

    fun shapeGate(n: Node, relax: Boolean): Boolean {
        val w = n.r - n.l
        val lim = if (relax) 0.70 else 0.55
        if (w > lim * 1080) return false
        val parent = n.parent ?: return true
        val sib = parent.children
        if (sib.size >= 4) {
            var same = 0
            val nw = n.r - n.l
            for (s in sib) {
                if (abs((s.r - s.l) - nw) <= 2 && abs(s.l - n.l) <= 2) same++
            }
            if (same >= 4) return false
        }
        return true
    }

    fun buildOptIndex() {
        appRuleIndex = APP_TEXT_RULES.associate { it[0] to it }
        exactSet = TEXT_EXACT.toHashSet()
        patTail = Regex(P_COUNTDOWN_TAIL_STR)
        patHead = Regex(P_COUNTDOWN_HEAD_STR)
    }

    /* ── 选择器编译（同 Java 版语义）── */
    fun compileSelectors(sources: Array<String>, rounds: Int): MutableList<Sel> {
        val out = mutableListOf<Sel>()
        repeat(rounds) {
            out.clear()
            for (s in sources) out.add(compileOne(s))
        }
        return out
    }

    fun compileOne(src: String): Sel {
        val sel = Sel(raw = src)
        var i = 0
        while (i < src.length) {
            when (val ch = src[i]) {
                '[' -> {
                    val close = src.indexOf(']', i)
                    if (close < 0) break
                    val inner = src.substring(i + 1, close)
                    for (part in splitLogical(inner)) {
                        sel.conds.add(splitCond(part.trim()))
                    }
                    i = close + 1
                }
                '>', '<', '-' -> {
                    var j = i + 1
                    while (j < src.length && src[j].isDigit()) j++
                    sel.rel = ch.toString()
                    sel.relN = if (j > i + 1) src.substring(i + 1, j).toInt() else 1
                    i = j
                }
                ' ' -> i++
                else -> {
                    var j = i
                    while (j < src.length && src[j] != ' ' && src[j] != '['
                        && src[j] != '>' && src[j] != '<' && src[j] != '-') j++
                    if (j > i) sel.posList.add(0)
                    i = j
                }
            }
        }
        return sel
    }

    fun splitLogical(s: String): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var depth = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '(') depth++
            if (c == ')') depth--
            if (depth == 0 && c == '&' && i + 1 < s.length && s[i + 1] == '&') {
                parts.add(cur.toString()); cur.setLength(0); i++; i++; continue
            }
            if (depth == 0 && c == '|' && i + 1 < s.length && s[i + 1] == '|') {
                parts.add(cur.toString()); cur.setLength(0); i++; i++; continue
            }
            cur.append(c); i++
        }
        parts.add(cur.toString())
        return parts
    }

    fun splitCond(s: String): Triple<String, String, String> {
        val eq = s.indexOf('=')
        if (eq < 0) return Triple(s, "exists", "")
        val attr = s.substring(0, eq).trim()
        val rest = s.substring(eq)
        val op = when {
            rest.startsWith("=") -> "=="
            rest.startsWith("*") -> "*="
            rest.startsWith("!") -> "!="
            else -> "=="
        }
        val q1 = s.indexOf('"'); val q2 = s.lastIndexOf('"')
        val value = if (q1 >= 0 && q2 > q1) s.substring(q1 + 1, q2) else ""
        return Triple(attr, op, value)
    }

    fun rssKb(): Long {
        return try {
            java.io.File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("VmRSS:") }
                    ?.replace(Regex("[^0-9]"), "")?.toLong() ?: -1L
            }
        } catch (e: Exception) { -1L }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = if (args.isNotEmpty()) args[0] else "bench"

        val t0 = System.nanoTime()
        compileSelectors(GKD_SELECTORS, 1)   // 预热 1 轮
        compileSelectors(GKD_SELECTORS, 5)  // 预热 5 轮
        val m0c = System.nanoTime()
        compileSelectors(GKD_SELECTORS, 50)  // 测量 50 轮
        val m1c = System.nanoTime()
        val sels = compileSelectors(GKD_SELECTORS, 1)
        val t1 = System.nanoTime()
        var selSink = 0L
        for (s in sels) selSink += s.raw.length + s.conds.size + s.relN + s.posList.size
        val compileUs = (m1c - m0c) / 50 / 1000

        if (mode == "boot") {
            println("READY")
            return
        }

        val tree = buildTree(20261118)
        val COLD = 1000; val WARMUP = 2000; val ITERS = 20000

        var cold = 0L
        val c0 = System.nanoTime()
        repeat(COLD) {
            val h = matchTree(tree, false)
            if (h != null) sink += h.length
        }
        val c1 = System.nanoTime()
        cold = (c1 - c0) / COLD

        repeat(WARMUP) {
            val h = matchTree(tree, false)
            if (h != null) sink += h.length
        }
        hits = 0
        val s0 = System.nanoTime()
        repeat(ITERS) {
            val h = matchTree(tree, false)
            if (h != null) { hits++; sink += h.hashCode() + h.length }
        }
        val s1 = System.nanoTime()

        buildOptIndex()
        repeat(WARMUP) {
            val h = matchTree(tree, true)
            if (h != null) sink += h.length
        }
        hits = 0
        val o0 = System.nanoTime()
        repeat(ITERS) {
            val h = matchTree(tree, true)
            if (h != null) { hits++; sink += h.hashCode() + h.length }
        }
        val o1 = System.nanoTime()

        println("{\"lang\":\"kotlin\",\"compile_us\":$compileUs"
            + ",\"sel_sink\":$selSink"
            + ",\"match_linear_first1000_ns\":$cold"
            + ",\"match_linear_ns\":${"%.1f".format(java.util.Locale.ROOT, (s1 - s0).toDouble() / ITERS)}"
            + ",\"match_optimized_ns\":${"%.1f".format(java.util.Locale.ROOT, (o1 - o0).toDouble() / ITERS)}"
            + ",\"hits\":$hits,\"sink\":${sink % 1000003}"
            + ",\"rss_kb\":${rssKb()}}")
    }
}
