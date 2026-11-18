/**
 * Skip-Ad 引擎热路径基准测试 —— JavaScript 实现（Node/V8 = Android WebView JS 引擎的性能代理）
 * 算法/数据/时序与 Java 参考版逐位一致（xorshift31 PRNG 同一棵树）。
 *
 * 用法： node Bench.js boot | node Bench.js bench
 */

'use strict';

const APP_TEXT_RULES = [
    ["com.sina.weibo", "跳过广告", "跳过"],
    ["com.ximalaya.ting.android", "跳过广告", "跳过"],
    ["tv.danmaku.bili", "跳过广告", "跳过"],
    ["com.qiyi.video", "跳过广告", "跳过"],
    ["com.youku.phone", "跳过广告", "跳过"],
    ["com.tencent.qqlive", "跳过广告", "跳过"],
    ["com.hunantv.imgo", "跳过广告", "跳过"],
    ["com.gotokeep.keep", "跳过"],
    ["com.xingin.xhs", "跳过"],
    ["com.zhihu.android", "跳过广告"],
    ["com.sankuai.meituan", "跳过广告"],
    ["com.dianping.v1", "跳过广告"],
    ["com.jingdong.app.mall", "跳过"],
    ["com.taobao.taobao", "跳过"],
    ["com.xunmeng.pinduoduo", "跳过"],
    ["com.dragon.read", "跳过广告"],
    ["com.baidu.searchbox", "跳过"],
    ["com.netease.cloudmusic", "跳过"],
    ["com.tencent.qqmusic", "跳过广告"],
    ["com.kugou.android", "跳过"],
    ["com.UCMobile", "跳过"],
    ["com.tencent.mtt", "跳过广告"],
    ["com.douban.frodo", "跳过广告"],
    ["cn.wps.moffice_eng", "跳过广告"],
    ["ctrip.android.view", "跳过"],
    ["com.taobao.trip", "跳过"],
    ["com.autonavi.minimap", "跳过"],
    ["com.sdu.didi.psnger", "跳过"],
];

const TEXT_EXACT = [
    "跳过", "跳过广告", "跳过此广告", "跳过这个广告", "关闭广告", "关闭此广告",
    "点击跳过", "上滑跳过", "跳过提示", "立即跳过",
    "跳过 ad", "跳过ad",
    "skip", "skip ad", "skipad", "skip this ad",
    "跳過", "跳過廣告", "跳過此廣告",
];

const CONTAINS_SHORT = ["跳过", "skip", "跳過"];
const DANGEROUS = ["领", "红包", "下载", "查看详情", "立即", "参与", "抽奖", "领取"];
const VID_SKIP_KEYS = ["skip", "tt_splash_skip_btn", "iv_skip", "count_down", "splash"];

const P_COUNTDOWN_TAIL_STR = "^(跳过|跳过广告|跳過廣告)\\s*[0-9]+(s|秒)?$";
const P_COUNTDOWN_HEAD_STR = "^[0-9]+\\s*(s|秒)?(后|後)可?(跳过|跳过广告|跳過廣告)$";
/* 预编译形式（optimized 模式 = v5.6.0 优化） */
let patTail = null, patHead = null;

const GKD_SELECTORS = [
    '[text*="跳过"]', '[text*="跳过广告"]', '[vid*="skip"]',
    '[vid="tt_splash_skip_btn"]', '[text="跳过广告 3"]',
    '[text*="跳过" && vid*="count_down"]',
    '[vid*="skip" >n2 [text*="广告"]',
    '[text*="跳過廣告" || text*="skip"]',
    'ImageView <n [vid*="splash"]',
    '[text*="关闭广告" && clickable=true]',
    '[vid*="iv_skip"] >n2 TextView',
    '[text*="跳过"] -n [text*="广告"]',
    '[text*="skip" && clickable=true] >n2 *',
    '[vid*="close" && (text*="广告" || vid*="ad")]',
    '[text*="跳过"][9]', '[text*="跳过"][9,10]',
    '* >n [vid*="splash_ad"]',
    '[text*="跳过广告" >n2 [text*="查看详情"]',
    '[vid*="count_down"] <n3 ImageView',
    '[text*="上滑"] || [vid*="slide_skip"]',
    '[text*="跳过" && visibleToUser=true] >n *',
    '[vid*="ad_close" && text=null]',
    '[text*="skip this ad"] >n2 [clickable=true]',
    '[text*="跳過"][3,8]',
];

const TEXT_POOL = [
    "跳过广告 5", "跳过广告 3", "跳过", "立即领取红包", "限时秒杀·低至1折",
    "精选推荐", "新人专享礼包", "点击查看详情", "上滑解锁更多内容", "领取优惠券",
    "skip ad 5", "Skip", "广告", "立即下载App", "今日热点推荐", "签到领金币",
    null, null, null, null,
];
const VID_POOL = [
    "tt_splash_skip_btn", "iv_skip", "count_down_text", "splash_ad_view",
    "close_btn", "ad_container", "banner_view", null, null, null,
];
const CLS_POOL = [
    "android.widget.TextView", "android.widget.ImageView", "android.widget.Button",
    "android.view.ViewGroup", "android.webkit.WebView",
];

/* ── xorshift31（与 Java/Kotlin/Python 逐位一致）── */
let rngState = 0;
function rnd() {
    let x = rngState;
    x ^= (x << 13); x |= 0;
    x ^= (x >>> 17);
    x ^= (x << 5); x |= 0;
    rngState = x;
    return (x & 0x7FFFFFFF) >>> 0;
}
function rndRange(mod) { return rnd() % mod; }

function buildTree(seed) {
    rngState = seed | 0;
    const root = { cls: "android.view.ViewGroup", l: 0, t: 0, r: 1080, b: 2400, parent: null, children: [] };
    for (let g = 0; g < 6; g++) {
        const grp = {
            cls: CLS_POOL[rndRange(CLS_POOL.length)],
            l: rndRange(1000), t: rndRange(2200), clickable: false, parent: null, children: [],
        };
        grp.r = grp.l + 40 + rndRange(900);
        grp.b = grp.t + 40 + rndRange(160);
        grp.clickable = rndRange(2) === 0;
        link(root, grp);
        const n = 5 + rndRange(5);
        for (let i = 0; i < n; i++) {
            const c = { parent: null, children: [] };
            let text = TEXT_POOL[rndRange(TEXT_POOL.length)];
            if (text !== null && text !== undefined) {
                if (rndRange(2) === 0 && text.length >= 4) {
                    text = text + " " + (1 + rndRange(9));
                }
                c.text = text;
            }
            const vid = VID_POOL[rndRange(VID_POOL.length)];
            if (vid !== null && vid !== undefined) c.vid = vid + "_" + rndRange(200);
            c.cls = CLS_POOL[rndRange(CLS_POOL.length)];
            c.l = grp.l + rndRange(Math.max(1, grp.r - grp.l - 80));
            c.t = grp.t + rndRange(Math.max(1, grp.b - grp.t - 30));
            const w = 30 + rndRange(700);
            const h = 20 + rndRange(80);
            c.r = c.l + w; c.b = c.t + h;
            c.clickable = rndRange(4) === 0;
            link(grp, c);
        }
    }
    return root;
}
function link(p, c) { c.parent = p; p.children.push(c); }

let pkg = "com.sankuai.meituan";
let appRuleIndex = null;
let exactSet = null;

let hits = 0;
let sink = 0;

function matchTree(root, optimized) {
    const stack = [root];
    while (stack.length > 0) {
        const n = stack.pop();
        if (n.text !== undefined) {
            const low = n.text.trim().toLowerCase();
            const hit = matchText(n, low, optimized);
            if (hit !== null) return hit;
        }
        if (n.vid !== undefined) {
            const v = n.vid.toLowerCase();
            for (let i = 0; i < VID_SKIP_KEYS.length; i++) {
                if (v.includes(VID_SKIP_KEYS[i])) { if (shapeGate(n, true)) return "vid:" + n.vid; }
            }
        }
        for (let i = n.children.length - 1; i >= 0; i--) stack.push(n.children[i]);
    }
    return null;
}

function matchText(n, low, optimized) {
    if (optimized) {
        const row = appRuleIndex[pkg];
        if (row !== undefined) {
            for (let i = 1; i < row.length; i++) {
                if (low === row[i]) return "app:" + row[i];
            }
        }
    } else {
        for (let ri = 0; ri < APP_TEXT_RULES.length; ri++) {
            const row = APP_TEXT_RULES[ri];
            if (pkg.startsWith(row[0])) {
                for (let i = 1; i < row.length; i++) {
                    if (low === row[i]) return "app:" + row[i];
                }
            }
        }
    }
    if (optimized) {
        if (exactSet.has(low)) { if (shapeGate(n, false)) return "exact:" + low; }
    } else {
        for (let i = 0; i < TEXT_EXACT.length; i++) {
            if (low === TEXT_EXACT[i]) { if (shapeGate(n, false)) return "exact:" + low; }
        }
    }
    if (optimized) {
        if (patTail.test(low)) { if (shapeGate(n, false)) return "countdown:" + low; }
        if (patHead.test(low)) { if (shapeGate(n, false)) return "countdown:" + low; }
    } else {
        if (new RegExp(P_COUNTDOWN_TAIL_STR).test(low)) { if (shapeGate(n, false)) return "countdown:" + low; }
        if (new RegExp(P_COUNTDOWN_HEAD_STR).test(low)) { if (shapeGate(n, false)) return "countdown:" + low; }
    }
    if (low.length <= 8) {
        let cont = false;
        for (let i = 0; i < CONTAINS_SHORT.length; i++) if (low.includes(CONTAINS_SHORT[i])) { cont = true; break; }
        if (cont) {
            for (let i = 0; i < DANGEROUS.length; i++) if (low.includes(DANGEROUS[i])) return null;
            if (shapeGate(n, false)) return "contains:" + low;
        }
    }
    return null;
}

function shapeGate(n, relax) {
    const w = n.r - n.l;
    const lim = relax ? 0.70 : 0.55;
    if (w > lim * 1080) return false;
    if (n.parent === null) return true;
    const sib = n.parent.children;
    if (sib.length >= 4) {
        let same = 0;
        const nw = n.r - n.l;
        for (let i = 0; i < sib.length; i++) {
            const s = sib[i];
            if (Math.abs((s.r - s.l) - nw) <= 2 && Math.abs(s.l - n.l) <= 2) same++;
        }
        if (same >= 4) return false;
    }
    return true;
}

function buildOptIndex() {
    appRuleIndex = new Map();
    for (let i = 0; i < APP_TEXT_RULES.length; i++) appRuleIndex.set(APP_TEXT_RULES[i][0], APP_TEXT_RULES[i]);
    exactSet = new Set(TEXT_EXACT);
    patTail = new RegExp(P_COUNTDOWN_TAIL_STR);
    patHead = new RegExp(P_COUNTDOWN_HEAD_STR);
}

/* ── 选择器编译（同 Java 版语义）── */
function compileOne(src) {
    const sel = { raw: src, conds: [], rel: "", relN: 1, posList: [] };
    let i = 0;
    while (i < src.length) {
        const ch = src[i];
        if (ch === '[') {
            const close = src.indexOf(']', i);
            if (close < 0) break;
            const inner = src.substring(i + 1, close);
            const parts = splitLogical(inner);
            for (const part of parts) {
                sel.conds.push(splitCond(part.trim()));
            }
            i = close + 1;
        } else if (ch === '>' || ch === '<' || ch === '-') {
            let j = i + 1;
            while (j < src.length && src[j] >= '0' && src[j] <= '9') j++;
            sel.rel = ch;
            sel.relN = j > i + 1 ? parseInt(src.substring(i + 1, j), 10) : 1;
            i = j;
        } else if (ch === ' ') {
            i++;
        } else {
            let j = i;
            while (j < src.length && src[j] !== ' ' && src[j] !== '['
                && src[j] !== '>' && src[j] !== '<' && src[j] !== '-') j++;
            if (j > i) sel.posList.push(0);
            i = j;
        }
    }
    return sel;
}

function splitLogical(s) {
    const parts = [];
    let cur = "";
    let depth = 0;
    let i = 0;
    while (i < s.length) {
        const c = s[i];
        if (c === '(') depth++;
        if (c === ')') depth--;
        if (depth === 0 && c === '&' && i + 1 < s.length && s[i + 1] === '&') {
            parts.push(cur); cur = ""; i += 2; continue;
        }
        if (depth === 0 && c === '|' && i + 1 < s.length && s[i + 1] === '|') {
            parts.push(cur); cur = ""; i += 2; continue;
        }
        cur += c; i++;
    }
    parts.push(cur);
    return parts;
}

function splitCond(s) {
    const eq = s.indexOf('=');
    if (eq < 0) return [s, "exists", ""];
    const attr = s.substring(0, eq).trim();
    const rest = s.substring(eq);
    let op = "==";
    if (rest.startsWith("=")) op = "==";
    else if (rest.startsWith("*")) op = "*=";
    else if (rest.startsWith("!")) op = "!=";
    const q1 = s.indexOf('"');
    const q2 = s.lastIndexOf('"');
    const value = (q1 >= 0 && q2 > q1) ? s.substring(q1 + 1, q2) : "";
    return [attr, op, value];
}

function hashString(s) {
    let h = 0;
    for (let i = 0; i < s.length; i++) {
        h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
    }
    return h;
}

const mode = process.argv[2] || "bench";

const t0 = process.hrtime.bigint();
let sels = [];
for (const s of GKD_SELECTORS) sels.push(compileOne(s)); // 预热 1 轮
for (let r = 0; r < 5; r++) { sels = []; for (const s of GKD_SELECTORS) sels.push(compileOne(s)); } // 预热 5 轮
const m0c = process.hrtime.bigint();
for (let r = 0; r < 50; r++) { sels = []; for (const s of GKD_SELECTORS) sels.push(compileOne(s)); } // 测量 50 轮
const m1c = process.hrtime.bigint();
sels = [];
for (const s of GKD_SELECTORS) sels.push(compileOne(s));
const t1 = process.hrtime.bigint();
let selSink = 0;
for (const s of sels) selSink += s.raw.length + s.conds.length + s.relN + s.posList.length;
const compileUs = Number(m1c - m0c) / 50 / 1000;

if (mode === "boot") {
    console.log("READY");
    process.exit(0);
}

const tree = buildTree(20261118);
const COLD = 1000, WARMUP = 2000, ITERS = 20000;

const c0 = process.hrtime.bigint();
for (let i = 0; i < COLD; i++) {
    const h = matchTree(tree, false);
    if (h !== null) sink += h.length;
}
const c1 = process.hrtime.bigint();
const firstNs = Number(c1 - c0) / COLD;

for (let i = 0; i < WARMUP; i++) {
    const h = matchTree(tree, false);
    if (h !== null) sink += h.length;
}
hits = 0;
const s0 = process.hrtime.bigint();
for (let i = 0; i < ITERS; i++) {
    const h = matchTree(tree, false);
    if (h !== null) { hits++; sink += hashString(h) + h.length; }
}
const s1 = process.hrtime.bigint();

buildOptIndex();
for (let i = 0; i < WARMUP; i++) {
    const h = matchTree(tree, true);
    if (h !== null) sink += h.length;
}
hits = 0;
const o0 = process.hrtime.bigint();
for (let i = 0; i < ITERS; i++) {
    const h = matchTree(tree, true);
    if (h !== null) { hits++; sink += hashString(h) + h.length; }
}
const o1 = process.hrtime.bigint();

const mem = process.memoryUsage();
console.log(JSON.stringify({
    lang: "js",
    compile_us: Math.round(compileUs),
    sel_sink: selSink,
    match_linear_first1000_ns: Math.round(firstNs),
    match_linear_ns: +(Number(s1 - s0) / ITERS).toFixed(1),
    match_optimized_ns: +(Number(o1 - o0) / ITERS).toFixed(1),
    hits: hits,
    sink: sink % 1000003,
    rss_kb: Math.round(mem.rss / 1024),
}));
