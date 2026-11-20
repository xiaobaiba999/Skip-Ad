/*
 * Skip-Ad 引擎热路径基准测试 —— C 参考实现（v7.8.0 新增）
 * 算法与 Java/Kotlin/JS/Python 版本完全一致（xorshift31 PRNG 保证同一棵树、
 * 同一套规则、同一套命中——五语言校验和逐位等价）。
 *
 * 三阶段测量：
 *   BOOT    —— 进程冷启动 + 引擎初始化（规则编译完成后打印 READY 即退出）
 *   COMPILE —— GKD 风格选择器编译吞吐（订阅更新路径）
 *   MATCH   —— 无障碍事件到达后的整树匹配（每事件热路径）
 *
 * 用法： ./bench boot | ./bench bench
 *
 * 「用 C 语言写会不会更好」的实测回答（协议同源、数据可查）：
 *   - C 在纳秒级匹配上确实最快（编译型 + 零 GC + 零边界检查）；
 *   - 但引擎瓶颈从来不在匹配层（匹配 ~μs 级 vs 事件到达 80ms 节流——
 *     5 个数量级余量），而在 Accessibility API 事件面与覆盖面（词表/画像/
 *     调度）——C 的 AccessibilityNodeInfo 只能经 JNI 回 Java 拿（C 本体
 *     摸不到无障碍树），改 C = 重写全引擎换不到任何命中率收益。
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <stdint.h>
#include <time.h>
#include <regex.h>

/* ── 引擎真实规则常量（取自 android-app/src/com/zai/adshield/core/RuleEngine.java）── */
static const char *APP_TEXT_RULES[][3] = {
    { "com.sina.weibo", "跳过广告", "跳过" },
    { "com.ximalaya.ting.android", "跳过广告", "跳过" },
    { "tv.danmaku.bili", "跳过广告", "跳过" },
    { "com.qiyi.video", "跳过广告", "跳过" },
    { "com.youku.phone", "跳过广告", "跳过" },
    { "com.tencent.qqlive", "跳过广告", "跳过" },
    { "com.hunantv.imgo", "跳过广告", "跳过" },
    { "com.gotokeep.keep", "跳过", NULL },
    { "com.xingin.xhs", "跳过", NULL },
    { "com.zhihu.android", "跳过广告", NULL },
    { "com.sankuai.meituan", "跳过广告", "跳过" },
    { "com.dianping.v1", "跳过广告", NULL },
    { "com.jingdong.app.mall", "跳过", NULL },
    { "com.taobao.taobao", "跳过", NULL },
    { "com.xunmeng.pinduoduo", "跳过", NULL },
    { "com.dragon.read", "跳过广告", NULL },
    { "com.baidu.searchbox", "跳过", NULL },
    { "com.netease.cloudmusic", "跳过", NULL },
    { "com.tencent.qqmusic", "跳过广告", NULL },
    { "com.kugou.android", "跳过", NULL },
    { "com.UCMobile", "跳过", NULL },
    { "com.tencent.mtt", "跳过广告", NULL },
    { "com.douban.frodo", "跳过广告", NULL },
    { "cn.wps.moffice_eng", "跳过广告", NULL },
    { "ctrip.android.view", "跳过", NULL },
    { "com.taobao.trip", "跳过", NULL },
    { "com.autonavi.minimap", "跳过", NULL },
    { "com.sdu.didi.psnger", "跳过", NULL },
};
#define APP_RULES_N 28

static const char *TEXT_EXACT[] = {
    "跳过", "跳过广告", "跳过此广告", "跳过这个广告", "关闭广告", "关闭此广告",
    "点击跳过", "上滑跳过", "跳过提示", "立即跳过",
    "跳过 ad", "跳过ad",
    "skip", "skip ad", "skipad", "skip this ad",
    "跳過", "跳過廣告", "跳過此廣告",
};
#define TEXT_EXACT_N 18

static const char *CONTAINS_SHORT[] = { "跳过", "skip", "跳過" };
#define CONTAINS_N 3
static const char *DANGEROUS[] = { "领", "红包", "下载", "查看详情", "立即", "参与", "抽奖", "领取" };
#define DANGEROUS_N 8
static const char *VID_SKIP_KEYS[] = { "skip", "tt_splash_skip_btn", "iv_skip", "count_down", "splash" };
#define VID_KEYS_N 5

/* 字符串形式（linear 模式 = 引擎现状：每调用 regcomp 重编译） */
static const char *P_TAIL_STR = "^(跳过|跳过广告|跳過廣告)[[:space:]]*[0-9]+(s|秒)?$";
static const char *P_HEAD_STR = "^[0-9]+[[:space:]]*(s|秒)?(后|後)可?(跳过|跳过广告|跳過廣告)$";
/* 预编译形式（optimized 模式 = v5.6.0 优化） */
static regex_t patTail, patHead;

/* ── GKD 风格订阅选择器（编译阶段的测试语料——24 条与各语言同源）── */
static const char *GKD_SELECTORS[] = {
    "[text*=\"跳过\"]", "[text*=\"跳过广告\"]", "[vid*=\"skip\"]",
    "[vid=\"tt_splash_skip_btn\"]", "[text=\"跳过广告 3\"]",
    "[text*=\"跳过\" && vid*=\"count_down\"]",
    "[vid*=\"skip\" >n2 [text*=\"广告\"]",
    "[text*=\"跳過廣告\" || text*=\"skip\"]",
    "ImageView <n [vid*=\"splash\"]",
    "[text*=\"关闭广告\" && clickable=true]",
    "[vid*=\"iv_skip\"] >n2 TextView",
    "[text*=\"跳过\"] -n [text*=\"广告\"]",
    "[text*=\"skip\" && clickable=true] >n2 *",
    "[vid*=\"close\" && (text*=\"广告\" || vid*=\"ad\")]",
    "[text*=\"跳过\"][9]", "[text*=\"跳过\"][9,10]",
    "* >n [vid*=\"splash_ad\"]",
    "[text*=\"跳过广告\" >n2 [text*=\"查看详情\"]",
    "[vid*=\"count_down\"] <n3 ImageView",
    "[text*=\"上滑\"] || [vid*=\"slide_skip\"]",
    "[text*=\"跳过\" && visibleToUser=true] >n *",
    "[vid*=\"ad_close\" && text=null]",
    "[text*=\"skip this ad\"] >n2 [clickable=true]",
    "[text*=\"跳過\"][3,8]",
};
#define SEL_N 24

/* ── 节点视图（对齐引擎 NodeView）── */
#define MAX_NODES 128
typedef struct Node {
    char *text;            /* nullable */
    char *vid;            /* nullable */
    const char *cls;
    int l, t, r, b;
    int clickable;
    int parent;            /* -1 = root */
    int firstChild, nextSib;
} Node;
static Node nodes[MAX_NODES];
static int nodeN = 0;

static void link(int p, int c) {
    nodes[c].parent = p;
    if (nodes[p].firstChild < 0) nodes[p].firstChild = c;
    else {
        int k = nodes[p].firstChild;
        while (nodes[k].nextSib >= 0) k = nodes[k].nextSib;
        nodes[k].nextSib = c;
    }
}

/* ── xorshift31 —— 与各语言实现逐位一致（31 位无符号返回值域）── */
static int rngState;
static int rnd(void) {
    uint32_t x = (uint32_t) rngState;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    rngState = (int) x;
    return (int) (x & 0x7FFFFFFFu);
}
static int rndRange(int mod) { return mod > 0 ? rnd() % mod : 0; }

static const char *TEXT_POOL[] = {
    "跳过广告 5", "跳过广告 3", "跳过", "立即领取红包", "限时秒杀·低至1折",
    "精选推荐", "新人专享礼包", "点击查看详情", "上滑解锁更多内容", "领取优惠券",
    "skip ad 5", "Skip", "广告", "立即下载App", "今日热点推荐", "签到领金币",
    NULL, NULL, NULL, NULL,
};
#define TEXT_POOL_N 20
static const char *VID_POOL[] = {
    "tt_splash_skip_btn", "iv_skip", "count_down_text", "splash_ad_view",
    "close_btn", "ad_container", "banner_view", NULL, NULL, NULL,
};
#define VID_POOL_N 10
static const char *CLS_POOL[] = {
    "android.widget.TextView", "android.widget.ImageView", "android.widget.Button",
    "android.view.ViewGroup", "android.webkit.WebView",
};

/* UTF-8 码点数（== Java String.length() for BMP） */

/* UTF-8 码点数（== Java String.length() for BMP） */
static int utf8Len(const char *s) {
    if (!s) return 0;
    int n = 0;
    for (const unsigned char *p = (const unsigned char *) s; *p; p++)
        if ((*p & 0xC0) != 0x80) n++;
    return n;
}

static int buildTree(int seed) {
    memset(nodes, 0, sizeof(nodes));
    nodeN = 0;
    rngState = seed;
    int root = nodeN++;
    nodes[root].cls = "android.view.ViewGroup";
    nodes[root].parent = -1; nodes[root].firstChild = -1; nodes[root].nextSib = -1;
    nodes[root].l = 0; nodes[root].t = 0; nodes[root].r = 1080; nodes[root].b = 2400;
    int groups = 6;
    for (int g = 0; g < groups; g++) {
        int grp = nodeN++;
        nodes[grp].firstChild = -1; nodes[grp].nextSib = -1;
        nodes[grp].cls = CLS_POOL[rndRange(5)];
        nodes[grp].l = rndRange(1000); nodes[grp].t = rndRange(2200);
        nodes[grp].r = nodes[grp].l + 40 + rndRange(900);
        nodes[grp].b = nodes[grp].t + 40 + rndRange(160);
        nodes[grp].clickable = (rndRange(2) == 0);
        link(root, grp);
        int n = 5 + rndRange(5);
        for (int i = 0; i < n; i++) {
            int c = nodeN++;
            nodes[c].firstChild = -1; nodes[c].nextSib = -1;
            const char *text = TEXT_POOL[rndRange(TEXT_POOL_N)];
            if (text != NULL) {
                if (rndRange(2) == 0 && utf8Len(text) >= 4) {
                    char buf[64];
                    snprintf(buf, sizeof buf, "%s %d", text, 1 + rndRange(9));
                    nodes[c].text = strdup(buf);
                } else nodes[c].text = strdup(text);
            }
            const char *vid = VID_POOL[rndRange(VID_POOL_N)];
            if (vid != NULL) {
                char buf[64];
                snprintf(buf, sizeof buf, "%s_%d", vid, rndRange(200));
                nodes[c].vid = strdup(buf);
            }
            nodes[c].cls = CLS_POOL[rndRange(5)];
            nodes[c].l = nodes[grp].l + rndRange(nodes[grp].r - nodes[grp].l - 80 > 1 ? nodes[grp].r - nodes[grp].l - 80 : 1);
            nodes[c].t = nodes[grp].t + rndRange(nodes[grp].b - nodes[grp].t - 30 > 1 ? nodes[grp].b - nodes[grp].t - 30 : 1);
            int w = 30 + rndRange(700), h = 20 + rndRange(80);
            nodes[c].r = nodes[c].l + w; nodes[c].b = nodes[c].t + h;
            nodes[c].clickable = (rndRange(4) == 0);
            link(grp, c);
        }
    }
    return root;
}


/* Java String.hashCode()（UTF-16 单元 == BMP 码点；31 进位 int32 回绕） */
static int32_t javaHash(const char *s) {
    uint32_t h = 0;
    for (const unsigned char *p = (const unsigned char *) s; *p; ) {
        uint32_t cp;
        if (*p < 0x80) { cp = *p; p += 1; }
        else if ((*p & 0xE0) == 0xC0) { cp = ((*p & 0x1F) << 6) | (p[1] & 0x3F); p += 2; }
        else if ((*p & 0xF0) == 0xE0) { cp = ((*p & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F); p += 3; }
        else { cp = ((*p & 0x07) << 18) | ((p[1] & 0x3F) << 12) | ((p[2] & 0x3F) << 6) | (p[3] & 0x3F); p += 4; }
        h = 31 * h + cp;
    }
    return (int32_t) h;
}

static const char *pkg;
static int useOptimized;

/* 命中串构造（"app:xxx" 等——与 Java 完全同构） */
static char hitBuf[96];
static const char *mkHit(const char *prefix, const char *s) {
    snprintf(hitBuf, sizeof hitBuf, "%s:%s", prefix, s);
    return hitBuf;
}

static int shapeGate(int ni, int relax) {
    int w = nodes[ni].r - nodes[ni].l;
    double lim = relax ? 0.70 : 0.55;
    if (w > lim * 1080) return 0;
    if (nodes[ni].parent < 0) return 1;
    int p = nodes[ni].parent;
    int sibN = 0, idx[MAX_NODES];
    for (int k = nodes[p].firstChild; k >= 0; k = nodes[k].nextSib) idx[sibN++] = k;
    if (sibN >= 4) {
        int same = 0, nw = nodes[ni].r - nodes[ni].l;
        for (int i = 0; i < sibN; i++) {
            int s = idx[i];
            if (abs((nodes[s].r - nodes[s].l) - nw) <= 2 && abs(nodes[s].l - nodes[ni].l) <= 2) same++;
        }
        if (same >= 4) return 0;
    }
    return 1;
}

static const char *matchText(int ni, const char *low, int optimized) {
    /* 第0层：分应用规则 */
    for (int ri = 0; ri < APP_RULES_N; ri++) {
        if (strncmp(pkg, APP_TEXT_RULES[ri][0], strlen(APP_TEXT_RULES[ri][0])) == 0) {
            for (int i = 1; i < 3; i++) {
                const char *kw = APP_TEXT_RULES[ri][i];
                if (kw && strcmp(low, kw) == 0) return mkHit("app", kw);
            }
            break; /* 与 Java optimized 行为等价：命中行即断（startswith 语义下仅一行可命中） */
        }
    }
    /* 第1层：全局精确文案 */
    for (int i = 0; i < TEXT_EXACT_N; i++)
        if (strcmp(low, TEXT_EXACT[i]) == 0) { if (shapeGate(ni, 0)) return mkHit("exact", low); }
    /* 第2层：倒计时形态（linear = 每调用重编译；optimized = 预编译 regcomp 一次） */
    if (optimized) {
        if (regexec(&patTail, low, 0, NULL, 0) == 0) { if (shapeGate(ni, 0)) return mkHit("countdown", low); }
        if (regexec(&patHead, low, 0, NULL, 0) == 0) { if (shapeGate(ni, 0)) return mkHit("countdown", low); }
    } else {
        regex_t re;
        if (regcomp(&re, P_TAIL_STR, REG_EXTENDED) == 0) {
            int m = regexec(&re, low, 0, NULL, 0);
            regfree(&re);
            if (m == 0) { if (shapeGate(ni, 0)) return mkHit("countdown", low); }
        }
        if (regcomp(&re, P_HEAD_STR, REG_EXTENDED) == 0) {
            int m = regexec(&re, low, 0, NULL, 0);
            regfree(&re);
            if (m == 0) { if (shapeGate(ni, 0)) return mkHit("countdown", low); }
        }
    }
    /* 第3层：短包含 + 危险词黑名单 */
    if (utf8Len(low) <= 8) {
        int cont = 0;
        for (int i = 0; i < CONTAINS_N && !cont; i++)
            if (strstr(low, CONTAINS_SHORT[i])) cont = 1;
        if (cont) {
            for (int i = 0; i < DANGEROUS_N; i++)
                if (strstr(low, DANGEROUS[i])) return NULL;
            if (shapeGate(ni, 0)) return mkHit("contains", low);
        }
    }
    return NULL;
}

static long hits;
static long sink;

static const char *matchTree(int root, int optimized) {
    int stack[MAX_NODES], sp = 0;
    stack[sp++] = root;
    while (sp > 0) {
        int ni = stack[--sp];
        if (nodes[ni].text != NULL) {
            /* trim + toLowerCase(Locale.ROOT)：池内文本无前后空白，仅 ASCII 大小写受影响 */
            char low[96];
            int j = 0;
            for (const unsigned char *p = (const unsigned char *) nodes[ni].text; *p && j < 90; p++)
                low[j++] = (*p >= 'A' && *p <= 'Z') ? (char) (*p + 32) : (char) *p;
            low[j] = 0;
            const char *hit = matchText(ni, low, optimized);
            if (hit != NULL) return hit;
        }
        if (nodes[ni].vid != NULL) {
            char v[96];
            int j = 0;
            for (const unsigned char *p = (const unsigned char *) nodes[ni].vid; *p && j < 90; p++)
                v[j++] = (*p >= 'A' && *p <= 'Z') ? (char) (*p + 32) : (char) *p;
            v[j] = 0;
            for (int i = 0; i < VID_KEYS_N; i++)
                if (strstr(v, VID_SKIP_KEYS[i])) { if (shapeGate(ni, 1)) return mkHit("vid", v); }
        }
        /* Java 原序：children 逆序入栈 → 先弹 child[0]（深度优先先序） */
        {
            int kids[MAX_NODES], kn = 0;
            for (int k = nodes[ni].firstChild; k >= 0; k = nodes[k].nextSib) kids[kn++] = k;
            for (int k = kn - 1; k >= 0; k--) stack[sp++] = kids[k];
        }
    }
    return NULL;
}

/* ── 订阅选择器编译（GkdSelector.compile 对齐：分词 + 括号布尔 + 关系符）── */
typedef struct Sel {
    const char *raw;
    const char *conds[24][3]; /* {attr, op, value} */
    int condN;
    char rel; int relN;
    int posList[24], posN;
} Sel;

/* splitLogical：depth 跟踪 + && / || 分段（括号内不分段）——与 Java 同构 */
static int splitLogical(const char *s, char parts[][128], int maxParts) {
    int n = (int) strlen(s), pi = 0, cj = 0, depth = 0;
    char cur[128];
    for (int k = 0; k <= n; k++) {
        char c = k < n ? s[k] : 0;
        if (k == n || (depth == 0 && c == '&' && k + 1 < n && s[k + 1] == '&')
                  || (depth == 0 && c == '|' && k + 1 < n && s[k + 1] == '|')) {
            cur[cj] = 0;
            if (pi < maxParts) { snprintf(parts[pi], 128, "%s", cur); pi++; }
            cj = 0;
            if (k < n) k++; /* 吞掉第二个 & / | */
            continue;
        }
        if (c == '(') depth++;
        if (c == ')') depth--;
        if (cj < 120) cur[cj++] = c;
    }
    return pi;
}

/* splitCond：attr = op value —— 与 Java 同构（exists / == / *= / !=） */
static void splitCond(const char *s, char *attr, char *op, char *value) {
    const char *eq = strchr(s, '=');
    if (!eq) {
        snprintf(attr, 64, "%s", s);
        strcpy(op, "exists"); value[0] = 0;
        return;
    }
    int al = (int) (eq - s);
    if (al > 60) al = 60;
    memcpy(attr, s, (size_t) al); attr[al] = 0;
    if (eq[0] == '=') strcpy(op, "==");
    else if (eq[0] == '*') strcpy(op, "*=");
    else if (eq[0] == '!') strcpy(op, "!=");
    else strcpy(op, "==");
    const char *q1 = strchr(s, '"');
    const char *q2 = q1 ? strrchr(s, '"') : NULL;
    if (q1 && q2 > q1) {
        int vl = (int) (q2 - q1 - 1);
        if (vl > 60) vl = 60;
        memcpy(value, q1 + 1, (size_t) vl); value[vl] = 0;
    } else value[0] = 0;
}

static void compileOne(const char *src, Sel *sel) {
    memset(sel, 0, sizeof *sel);
    sel->raw = src;
    sel->rel = 0; sel->relN = 1;
    int i = 0, n = (int) strlen(src);
    while (i < n) {
        char ch = src[i];
        if (ch == '[') {
            const char *close = strchr(src + i, ']');
            if (!close) break;
            char inner[128];
            int len = (int) (close - (src + i + 1));
            if (len > 124) len = 124;
            memcpy(inner, src + i + 1, (size_t) len);
            inner[len] = 0;
            char parts[12][128];
            int pn = splitLogical(inner, parts, 12);
            for (int pp = 0; pp < pn; pp++) {
                char attr[64], op[8], value[64];
                splitCond(parts[pp], attr, op, value);
                if (sel->condN < 24) {
                    sel->conds[sel->condN][0] = strdup(attr);
                    sel->conds[sel->condN][1] = strdup(op);
                    sel->conds[sel->condN][2] = strdup(value);
                    sel->condN++;
                }
            }
            i = (int) (close - src) + 1;
        } else if (ch == '>' || ch == '<' || ch == '-') {
            int j = i + 1;
            while (j < n && isdigit((unsigned char) src[j])) j++;
            sel->rel = ch;
            sel->relN = j > i + 1 ? atoi(src + i + 1) : 1;
            i = j;
        } else if (ch == ' ') {
            i++;
        } else {
            int j = i;
            while (j < n && src[j] != ' ' && src[j] != '[' && src[j] != '>' && src[j] != '<' && src[j] != '-') j++;
            if (j > i && sel->posN < 24) sel->posList[sel->posN++] = 0;
            i = j;
        }
    }
}

static long nowNs(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long) ts.tv_sec * 1000000000L + ts.tv_nsec;
}

static long rssKb(void) {
    FILE *f = fopen("/proc/self/status", "r");
    if (!f) return -1;
    char line[256];
    long kb = -1;
    while (fgets(line, sizeof line, f))
        if (strncmp(line, "VmRSS:", 6) == 0) { kb = atol(line + 6); break; }
    fclose(f);
    return kb;
}

int main(int argc, char **argv) {
    const char *mode = argc > 1 ? argv[1] : "bench";
    if (strcmp(mode, "seldbg") == 0) {
        for (int s = 0; s < SEL_N; s++) {
            Sel sel; compileOne(GKD_SELECTORS[s], &sel);
            printf("%d len=%d conds=%d rel=%c relN=%d pos=%d\n", s,
                   (int) strlen(sel.raw), sel.condN, sel.rel ? sel.rel : '-', sel.relN, sel.posN);
        }
        return 0;
    }
    pkg = "com.sankuai.meituan";
    useOptimized = 0;

    Sel sels[SEL_N];

    long t0 = nowNs();
    for (int s = 0; s < SEL_N; s++) compileOne(GKD_SELECTORS[s], &sels[s]);   /* 预热 1 轮 */
    for (int r = 0; r < 5; r++) for (int s = 0; s < SEL_N; s++) compileOne(GKD_SELECTORS[s], &sels[s]); /* 预热 5 轮 */
    long m0c = nowNs();
    for (int r = 0; r < 50; r++) for (int s = 0; s < SEL_N; s++) compileOne(GKD_SELECTORS[s], &sels[s]); /* 测量 50 轮 */
    long m1c = nowNs();
    for (int s = 0; s < SEL_N; s++) compileOne(GKD_SELECTORS[s], &sels[s]);
    long t1 = nowNs();
    long selSink = 0;
    for (int s = 0; s < SEL_N; s++)
        selSink += (long) utf8Len(sels[s].raw) + sels[s].condN + sels[s].relN + sels[s].posN;
    long compileUs = (m1c - m0c) / 50 / 1000;
    (void) t0; (void) t1;

    if (strcmp(mode, "boot") == 0) {
        printf("READY\n");
        return 0;
    }

    regcomp(&patTail, P_TAIL_STR, REG_EXTENDED);
    regcomp(&patHead, P_HEAD_STR, REG_EXTENDED);

    int root = buildTree(20261118);

    const int COLD = 1000, WARMUP = 2000, ITERS = 20000;

    /* 冷段（含编译惩罚）——线性版 */
    long c0 = nowNs();
    for (int i = 0; i < COLD; i++) {
        const char *h = matchTree(root, 0);
        if (h) sink += (long) utf8Len(h);
    }
    long c1 = nowNs();
    long firstLinearNs = (c1 - c0) / COLD;

    /* 预热 → 稳态——线性版 */
    for (int i = 0; i < WARMUP; i++) {
        const char *h = matchTree(root, 0);
        if (h) sink += (long) utf8Len(h);
    }
    hits = 0;
    long s0 = nowNs();
    for (int i = 0; i < ITERS; i++) {
        const char *h = matchTree(root, 0);
        if (h) { hits++; sink += (long) ((int64_t) javaHash(h) + (int64_t) utf8Len(h)); }
    }
    long s1 = nowNs();

    /* v5.6.0 候选优化（预编译正则）对照 */
    for (int i = 0; i < WARMUP; i++) {
        const char *h = matchTree(root, 1);
        if (h) sink += (long) utf8Len(h);
    }
    hits = 0;
    long o0 = nowNs();
    for (int i = 0; i < ITERS; i++) {
        const char *h = matchTree(root, 1);
        if (h) { hits++; sink += (long) ((int64_t) javaHash(h) + (int64_t) utf8Len(h)); }
    }
    long o1 = nowNs();

    printf("{\"lang\":\"c\",\"compile_us\":%ld"
           ",\"sel_sink\":%ld"
           ",\"match_linear_first1000_ns\":%ld"
           ",\"match_linear_ns\":%.1f"
           ",\"match_optimized_ns\":%.1f"
           ",\"hits\":%ld"
           ",\"sink\":%ld"
           ",\"rss_kb\":%ld}\n",
           compileUs, selSink, firstLinearNs,
           (double) (s1 - s0) / ITERS,
           (double) (o1 - o0) / ITERS,
           hits, sink % 1000003, rssKb());
    return 0;
}
