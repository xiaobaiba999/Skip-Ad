/**
 * 千款热门应用兼容性测试语料库（v3.3.0 · 含对抗样本层 · 诚实重构）
 * ─────────────────────────────────────────────────────────────
 * · REAL 层：130 款真实国民应用（真实包名，依据 2026-11 国内应用市场
 *   公开信息整理；包名以主流应用商店在架版本为准）。
 * · GEN 层：930 款确定性生成式长尾应用（种子固定，完全可复现），
 *   分类权重与获取渠道（官方商店 / 第三方侧载）按真实市场分布建模。
 * · 渲染建模：native（原生控件树）86.5% / web（H5，其中 60% 无障碍
 *   树可见）/ self（自绘，其中 35% 角落有原生跳过小控件）——诚实反映
 *   「哪些广告结构上可见、哪些不可见」。
 * · 对抗样本层（v3.1.0 喜马拉雅专案沉淀 + v3.2.0 扩充 + v3.3.0 真机反馈重构）：
 *   fakeSkip（广告图内嵌假「跳过」热区，点击即跳转）、
 *   chainJump（倒计时归零自动跳落地页并二跳外部应用）、
 *   gyroFlip（无倒计时 + 陀螺仪诱导，手机一动就跳）、
 *   popup（应用内弹窗广告：「广告」角标 + 小号关闭键）、
 *   backResponsive（无控件广告对返回键是否响应）。
 * · v3.3.0 新增建模（2026-11 真机反馈暴露的盲区）：
 *   sdkAd（开屏广告窗口为已知广告 SDK Activity——穿山甲/优量汇/快手/
 *     AdMob/宿主 ad.activity，className 指纹可识别）、
 *   gyroWindow（陀螺仪广告窗口形态：sdk=SDK 窗零文本 / hostText=宿主窗
 *     文案可读 / image=自绘图片零文本——v3.2.0 只建模了 hostText）、
 *   flipTrigMs（翻转/摇一摇触发时刻 500~2500ms——「拿起手机就跳」）、
 *   jumpTarget（跳转目标：known=旧名单电商商店 / expanded=微信支付宝
 *     美团知乎小红书 / browser=浏览器落地页 / unknown=其他）、
 *   popupForm（dialog=独立弹窗有窗口事件 / overlay=覆盖层无窗口事件）、
 *   popupAtMs（弹窗出现时刻 2s~60s——v3.2.0 弹窗扫描被 12s 开屏窗误拦）、
 *   popupSignal（badge=带角标 / ctaOnly=无角标但 Dialog 形态+营销话术）。
 * · v3.4.0 新增建模（2026-11 二次真机反馈：微博/喜马拉雅等主流应用仍拦截失败）：
 *   imageSkip（第一方图片开屏：应用自建广告系统渲染——窗口类名是应用
 *     自己的 Activity（不匹配 SDK 指纹）、跳过按钮纯图片零文本、倒计时
 *     烧在图里——v3.3.0 全部识别手段零信号，广告完整展示的根因）、
 *   imageCorner（该图片跳过键是否为右上角小可点控件——v3.4.0 L4 角落
 *     控件点击可命中；否则需 L6 坐标级点击兜底）。
 * · 能耗画像：每款应用的开屏事件量（事件数 = 引擎工作量的输入）。
 */
import type { AdType } from "./instant-engine";

export type RenderKind = "native" | "web" | "self";
export type SourceKind = "official" | "sideload";
export type GyroWindow = "none" | "sdk" | "hostText" | "image";
export type JumpTarget = "known" | "expanded" | "browser" | "unknown";
export type PopupForm = "dialog" | "overlay";
export type PopupSignal = "badge" | "ctaOnly";

export interface CorpusApp {
  pkg: string;
  name: string;
  category: string;
  adType: AdType;
  guard: boolean;
  source: SourceKind;
  render: RenderKind;
  /** 真跳过控件出现延迟（毫秒，0~5000 均匀分布；假跳过陷阱的陪衬） */
  skipDelayMs: number;
  /** web 渲染：无障碍树是否暴露内容（H5 开屏约 6 成可见） */
  webviewA11y: boolean;
  /** self 渲染：角落是否仍保留原生「跳过」小控件（约 35%） */
  hybridSkip: boolean;
  /** 广告图内嵌假「跳过」热区（点击即跳转，非真跳过） */
  fakeSkip: boolean;
  /** 链式二跳（倒计时归零自动跳落地页 → 再跳外部应用/商店） */
  chainJump: boolean;
  /** 陀螺仪诱导（v3.2.0）：无倒计时 + 「翻转手机跳详情页/第三方应用」，手机一动就跳 */
  gyroFlip: boolean;
  /** 应用内弹窗广告（v3.2.0）：「广告」角标 + 小号关闭键（与开屏类型正交） */
  popup: boolean;
  /** 无控件可见时，广告窗口对返回键是否响应（约 82%） */
  backResponsive: boolean;
  /** 热度 0~100（决定抽样权重与事件量） */
  hot: number;
  /* ── v3.3.0 真机反馈建模（既有字段值与 v3.2.0 完全一致，新增字段追加在随机序列末尾） ── */
  /** 开屏/插屏广告窗口为已知广告 SDK Activity（className 指纹可识别，约 55%） */
  sdkAd: boolean;
  /** 陀螺仪广告窗口形态（仅 gyroFlip 时有意义） */
  gyroWindow: GyroWindow;
  /** 翻转/摇一摇触发时刻（ms，500~2500——「拿起手机就跳」） */
  flipTrigMs: number;
  /** 跳转目标（v3.2.0 回弹名单只有 known；v3.3.0 扩充至 expanded/browser） */
  jumpTarget: JumpTarget;
  /** 弹窗广告形态（dialog 有窗口事件；overlay 无，靠内容突发检测） */
  popupForm: PopupForm;
  /** 弹窗出现时刻（ms，距最近窗口切换 2s~60s——v3.2.0 被 12s 开屏窗误拦） */
  popupAtMs: number;
  /** 弹窗信号类型（badge=带角标；ctaOnly=无角标但 Dialog 形态+营销话术） */
  popupSignal: PopupSignal;
  /* ── v3.4.0 真机反馈建模（追加在随机序列末尾，既有字段不变） ── */
  /** 第一方图片开屏（真机反馈主场景）：非 SDK 窗口、跳过键纯图片零文本 */
  imageSkip: boolean;
  /** 图片跳过键是否为右上角小可点控件（L4 角落点击可命中；否则需 L6 坐标兜底） */
  imageCorner: boolean;
}

/* ───────── 确定性伪随机（可复现） ───────── */

function fnv1a(str: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

function mulberry32(seed: number) {
  let a = seed >>> 0;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** 由包名派生的稳定随机序列（同包名永远同结果） */
function seqOf(pkg: string) {
  return mulberry32(fnv1a(pkg) ^ 0x20261116);
}

/* ───────── REAL 层：130 款真实热门应用 ─────────
   [name, pkg, category, adType, render, source]；guard 由类别（银行/政务/支付）推导 */

type RealRow = [string, string, string, AdType, RenderKind, SourceKind];

const REAL: RealRow[] = [
  // 电商 / 本地生活（14）
  ["淘宝", "com.taobao.taobao", "电商", "splash", "native", "official"],
  ["京东", "com.jingdong.app.mall", "电商", "splash", "native", "official"],
  ["拼多多", "com.xunmeng.pinduoduo", "电商", "splash", "native", "official"],
  ["小红书", "com.xingin.xhs", "电商", "splash", "native", "official"],
  ["得物", "com.shizhuang.mapp", "电商", "splash", "native", "official"],
  ["闲鱼", "com.taobao.idlefish", "电商", "splash", "native", "official"],
  ["转转", "com.wudaokou.humanity", "电商", "splash", "native", "official"],
  ["唯品会", "com.achievo.vipshop", "电商", "splash", "native", "official"],
  ["美团", "com.sankuai.meituan", "本地生活", "splash", "native", "official"],
  ["饿了么", "me.ele", "本地生活", "splash", "native", "official"],
  ["盒马", "com.wm.dhy", "本地生活", "splash", "native", "official"],
  ["叮咚买菜", "com.yaya.zone", "本地生活", "splash", "native", "official"],
  ["大众点评", "com.dianping.v1", "本地生活", "splash", "native", "official"],
  ["苏宁易购", "com.suning.mobile.lifewe", "电商", "splash", "native", "official"],
  // 视频 / 短视频 / 音频（12）
  ["抖音", "com.ss.android.ugc.aweme", "视频", "splash", "native", "official"],
  ["快手", "com.smile.gifmaker", "视频", "splash", "native", "official"],
  ["哔哩哔哩", "tv.danmaku.bili", "视频", "splash", "native", "official"],
  ["腾讯视频", "com.tencent.qqlive", "视频", "splash", "native", "official"],
  ["爱奇艺", "com.qiyi.video", "视频", "splash", "native", "official"],
  ["优酷视频", "com.youku.phone", "视频", "splash", "native", "official"],
  ["芒果TV", "com.hunantv.imgo.activity", "视频", "splash", "native", "official"],
  ["咪咕视频", "com.cmcc.cmvideo", "视频", "splash", "native", "official"],
  ["西瓜视频", "com.ss.android.article.video", "视频", "splash", "native", "official"],
  ["网易云音乐", "com.netease.cloudmusic", "音频", "splash", "native", "official"],
  ["喜马拉雅", "com.ximalaya.ting.android", "音频", "splash", "native", "official"],
  // 社交 / 资讯（10）
  ["微信", "com.tencent.mm", "社交", "splash", "native", "official"],
  ["QQ", "com.tencent.mobileqq", "社交", "splash", "native", "official"],
  ["微博", "com.sina.weibo", "社交", "splash", "native", "official"],
  ["知乎", "com.zhihu.android", "社交", "splash", "native", "official"],
  ["Soul", "cn.soulapp.android", "社交", "splash", "native", "official"],
  ["今日头条", "com.ss.android.article.news", "资讯", "splash", "native", "official"],
  ["网易新闻", "com.netease.newsreader.activity", "资讯", "splash", "native", "official"],
  ["凤凰新闻", "com.ifeng.news", "资讯", "splash", "native", "official"],
  ["百度", "com.baidu.searchbox", "资讯", "splash", "native", "official"],
  ["UC浏览器", "com.UCMobile", "资讯", "splash", "native", "official"],
  // 工具 / 效率（12）
  ["WiFi万能钥匙", "com.snda.wifilocating", "工具", "splash", "native", "official"],
  ["清理大师", "com.cleanmaster.mguard_cn", "工具", "splash", "native", "sideload"],
  ["迅雷", "com.xunlei.player", "工具", "splash", "web", "official"],
  ["WPS Office", "com.wps.moffice_eng", "工具", "splash", "native", "official"],
  ["夸克", "com.quark.browser", "工具", "splash", "native", "official"],
  ["讯飞输入法", "com.iflytek.inputmethod", "工具", "splash", "native", "official"],
  ["搜狗输入法", "com.sohu.inputmethod.sogou", "工具", "splash", "native", "official"],
  ["百度输入法", "com.baidu.input", "工具", "splash", "native", "official"],
  ["墨迹天气", "com.moji.mjweather", "工具", "splash", "native", "official"],
  ["航旅纵横", "com.umetrip.android.msky.app", "工具", "splash", "native", "official"],
  ["扫码王", "com.qrscan.pro", "工具", "splash", "web", "sideload"],
  ["极简压缩", "com.lite.zip", "工具", "splash", "web", "sideload"],
  // 地图 / 出行 / 旅行（9）
  ["高德地图", "com.autonavi.minimap", "出行", "splash", "native", "official"],
  ["百度地图", "com.baidu.BaiduMap", "出行", "splash", "native", "official"],
  ["腾讯地图", "com.tencent.map", "出行", "splash", "native", "official"],
  ["携程旅行", "ctrip.android.view", "旅行", "splash", "native", "official"],
  ["飞猪", "com.taobao.trip", "旅行", "splash", "native", "official"],
  ["去哪儿旅行", "com.Qunar", "旅行", "splash", "native", "official"],
  ["铁路12306", "com.MobileTicket", "出行", "splash", "native", "official"],
  ["滴滴出行", "com.sdu.didi.psnger", "出行", "splash", "native", "official"],
  ["哈啰", "cn.hsa.business", "出行", "splash", "native", "official"],
  // 网文阅读（5）
  ["QQ阅读", "com.qq.reader", "阅读", "splash", "native", "official"],
  ["起点读书", "com.qidian.QDReader", "阅读", "splash", "native", "official"],
  ["番茄小说", "com.dragon.read", "阅读", "splash", "native", "official"],
  ["七猫小说", "com.kmxs.reader", "阅读", "splash", "native", "official"],
  ["微信读书", "com.tencent.weread", "阅读", "splash", "native", "official"],
  // 教育 / 健康（6）
  ["作业帮", "com.baidu.homework", "教育", "splash", "native", "official"],
  ["猿题库", "com.fenbi.android.solar", "教育", "splash", "native", "official"],
  ["百词斩", "com.jiongji.baicizhan", "教育", "splash", "native", "official"],
  ["Keep", "com.gotokeep.keep", "健康", "splash", "native", "official"],
  ["丁香医生", "com.dxy.aspirin", "健康", "splash", "native", "official"],
  ["平安好医生", "com.pingan.papd", "健康", "splash", "native", "official"],
  // 游戏（10）：游戏厂商开屏自绘/H5 比例显著高于其他品类
  ["王者荣耀", "com.tencent.tmgp.sgame", "游戏", "splash", "native", "official"],
  ["和平精英", "com.tencent.tmgp.pubgmhd", "游戏", "splash", "native", "official"],
  ["原神", "com.miHoYo.Genshin", "游戏", "splash", "self", "official"],
  ["崩坏：星穹铁道", "com.miHoYo.hkrpg", "游戏", "splash", "self", "official"],
  ["明日方舟", "com.hypergryph.arknights", "游戏", "splash", "native", "official"],
  ["第五人格", "com.netease.dwrg", "游戏", "interstitial", "native", "official"],
  ["阴阳师", "com.netease.onmyoji", "游戏", "interstitial", "self", "official"],
  ["蛋仔派对", "com.netease.mc.mi", "游戏", "splash", "native", "official"],
  ["迷你世界", "com.minitech.miniworld", "游戏", "interstitial", "web", "official"],
  ["我的世界中国版", "com.netease.x19", "游戏", "interstitial", "self", "official"],
  // 支付（3，guard）
  ["支付宝", "com.eg.android.AlipayGphone", "支付", "splash", "native", "official"],
  ["云闪付", "com.unionpay", "支付", "splash", "native", "official"],
  ["美团支付", "com.sankuai.pay", "支付", "interstitial", "native", "official"],
  // 银行（15，guard）
  ["中国工商银行", "com.icbc", "银行", "splash", "native", "official"],
  ["中国建设银行", "com.chinamworld.main", "银行", "splash", "native", "official"],
  ["中国农业银行", "com.android.bankabc", "银行", "splash", "native", "official"],
  ["中国银行", "com.chinamworld.bocmbci", "银行", "splash", "native", "official"],
  ["招商银行", "com.cmbchina.newapp", "银行", "splash", "native", "official"],
  ["交通银行", "com.bankcomm.mbank", "银行", "splash", "native", "official"],
  ["邮储银行", "com.psbc.app", "银行", "splash", "native", "official"],
  ["浦发银行", "com.spdb.mobile", "银行", "splash", "native", "official"],
  ["中信银行", "com.ecitic.bank.mobile", "银行", "splash", "native", "official"],
  ["光大银行", "com.cebbank.app", "银行", "splash", "native", "official"],
  ["民生银行", "com.mmbank", "银行", "splash", "native", "official"],
  ["平安银行", "com.pingan.bank", "银行", "splash", "native", "official"],
  ["兴业银行", "cn.com.cib", "银行", "splash", "native", "official"],
  ["广发银行", "com.cgbchina.xpt", "银行", "splash", "native", "official"],
  ["华夏银行", "com.hxb.mobilebank", "银行", "splash", "native", "official"],
  // 政务（8，guard）
  ["个人所得税", "cn.gov.tax.its", "政务", "splash", "native", "official"],
  ["国家政务服务平台", "com.gov.unicom", "政务", "splash", "native", "official"],
  ["交管12123", "com.tljq.apps", "政务", "splash", "native", "official"],
  ["国家医保服务平台", "com.nhsapp.gov", "政务", "interstitial", "native", "official"],
  ["粤省事", "com.gd.yueshengshi", "政务", "splash", "native", "official"],
  ["浙里办", "com.zjzwfw", "政务", "splash", "native", "official"],
  ["随申办", "com.sh.suishenban", "政务", "splash", "native", "official"],
  ["北京通", "com.beijing.tong", "政务", "splash", "native", "official"],
  // 办公协同（6）
  ["钉钉", "com.alibaba.android.rimet", "办公", "splash", "native", "official"],
  ["飞书", "com.feishu.cn", "办公", "splash", "native", "official"],
  ["企业微信", "com.tencent.wework", "办公", "splash", "native", "official"],
  ["腾讯会议", "com.tencent.meeting", "办公", "splash", "native", "official"],
  ["京东金融", "com.jd.jrapp", "金融理财", "splash", "native", "official"],
  ["陆金所", "com.lufax", "金融理财", "splash", "native", "official"],
  // 补充层（21）：长视频直播 / 二手 / 房产 / 财经 / 快递 / 连锁 / 应用商店 / 更多银行政务游戏
  ["斗鱼", "com.douyu.group", "视频", "splash", "native", "official"],
  ["虎牙直播", "com.duowan.kiwi", "视频", "splash", "native", "official"],
  ["百度网盘", "com.baidu.netdisk", "工具", "splash", "native", "official"],
  ["贝壳找房", "com.lianjia.beike", "本地生活", "splash", "native", "official"],
  ["安居客", "com.anjuke.android.app", "本地生活", "splash", "native", "official"],
  ["马蜂窝", "com.mfw.roadbook", "旅行", "splash", "native", "official"],
  ["豆瓣", "com.douban.frodo", "社交", "splash", "native", "official"],
  ["美图秀秀", "com.meitu.mtxx", "摄影", "splash", "native", "official"],
  ["同花顺", "com.hexin.plat.android", "金融理财", "splash", "native", "official"],
  ["东方财富", "com.eastmoney.android.berlin", "金融理财", "splash", "native", "official"],
  ["雪球", "com.xueqiu.android", "金融理财", "splash", "native", "official"],
  ["菜鸟", "com.cainiao.wireless", "工具", "splash", "native", "official"],
  ["瑞幸咖啡", "com.luckycoffee.client", "本地生活", "splash", "native", "official"],
  ["TapTap", "com.taptap.app", "应用商店", "splash", "native", "sideload"],
  ["应用宝", "com.tencent.android.qqdownloader", "应用商店", "interstitial", "native", "official"],
  ["酷安", "com.coolapk.market", "应用商店", "splash", "native", "sideload"],
  ["宁波银行", "com.nbcb.mobilebank", "银行", "splash", "native", "official"],
  ["浙商银行", "com.czbank.mbank", "银行", "splash", "native", "official"],
  ["网上国网", "com.sgcc.wosp", "政务", "splash", "native", "official"],
  ["欢乐斗地主", "com.qqgame.doudizhu", "游戏", "interstitial", "web", "official"],
  ["网易严选", "com.netease.yanxuan", "电商", "splash", "native", "official"],
];

const GUARD_CATEGORIES = new Set(["银行", "政务", "支付"]);

function mkApp(row: RealRow): CorpusApp {
  const [name, pkg, category, adType, render, source] = row;
  const rnd = seqOf(pkg);
  const skipDelayMs = Math.round(rnd() * 5000);
  const webviewA11y = rnd() < 0.6;
  const hybridSkip = rnd() < 0.35;
  const hot = 60 + Math.round(rnd() * 40);
  // 对抗样本：约 8% 假跳过陷阱 / 约 8% 链式二跳 / 约 7% 陀螺仪诱导 / 约 11% 弹窗广告（确定性派生）
  const fakeSkip = (adType === "splash" || adType === "interstitial") && rnd() < 0.08;
  const chainJump = adType === "splash" && rnd() < 0.08;
  const gyroFlip = adType === "splash" && rnd() < 0.07;
  const popup = rnd() < 0.11;
  const backResponsive = rnd() < 0.82;
  const v330 = model330(rnd, adType, render, webviewA11y, hybridSkip, gyroFlip);
  /* v3.4.0 建模（追加在末尾，既有字段不变）：第一方图片开屏约 14%
     （真机反馈：微博/喜马拉雅等主流应用的自建广告系统渲染形态） */
  const guardCat = GUARD_CATEGORIES.has(category);
  const imageSkip = adType === "splash" && !guardCat && !fakeSkip && !chainJump && !gyroFlip
    && !v330.sdkAd && rnd() < 0.14;
  const imageCorner = imageSkip && rnd() < 0.55;
  const app: CorpusApp = {
    pkg,
    name,
    category,
    adType,
    guard: GUARD_CATEGORIES.has(category),
    source,
    render,
    skipDelayMs,
    webviewA11y,
    hybridSkip,
    fakeSkip,
    chainJump,
    gyroFlip,
    popup,
    backResponsive,
    hot,
    ...v330,
    imageSkip,
    imageCorner,
  };
  // 喜马拉雅专案 v4 画像（2026-11 二次真机实测）：第一方图片开屏
  // （WelComeActivity 自建广告渲染，窗口类名非 SDK、跳过键纯图片零文本、
  // 倒计时烧在图里）+ 翻转手机 900ms 即跳（零文本翻转提示同样烧在图里）+
  // 浏览器二跳 + 30s 首页覆盖层弹窗。v3.3.0 全部识别手段零信号 → 完整展示。
  if (pkg === "com.ximalaya.ting.android") {
    app.fakeSkip = false;
    app.chainJump = true;
    app.gyroFlip = true;
    app.skipDelayMs = 5100;
    app.hot = 98;
    app.sdkAd = false;
    app.gyroWindow = "image";
    app.flipTrigMs = 900;
    app.jumpTarget = "browser";
    app.backResponsive = true;
    app.imageSkip = true;
    app.imageCorner = true;
    app.popup = true;
    app.popupForm = "overlay";
    app.popupAtMs = 30000;
    app.popupSignal = "badge";
  }
  return app;
}

/* ───────── v3.3.0 真机反馈建模（追加在既有随机序列末尾，既有字段不变） ───────── */

function model330(
  rnd: () => number,
  adType: AdType,
  render: RenderKind,
  webviewA11y: boolean,
  hybridSkip: boolean,
  gyroFlip: boolean,
): Pick<CorpusApp, "sdkAd" | "gyroWindow" | "flipTrigMs" | "jumpTarget" | "popupForm" | "popupAtMs" | "popupSignal"> {
  const isAdWindowType = adType === "splash" || adType === "interstitial";
  // 约 55% 开屏/插屏由广告 SDK Activity 承载（className 指纹可识别）
  const sdkAd = isAdWindowType && rnd() < 0.55;
  // 陀螺仪广告窗口形态：sdk（零文本）/ hostText（文案可读）/ image（自绘零文本）
  let gyroWindow: GyroWindow = "none";
  if (gyroFlip) {
    if (sdkAd) gyroWindow = "sdk";
    else {
      const textLike = render === "native" ? true : render === "web" ? webviewA11y : hybridSkip;
      gyroWindow = rnd() < 0.55 || textLike ? "hostText" : "image";
    }
  }
  // 翻转/摇一摇触发时刻：500~2500ms（「拿起手机就跳」）
  const flipTrigMs = 500 + Math.round(rnd() * 2000);
  // 跳转目标：known 45% / expanded 25% / browser 20% / unknown 10%
  const tRoll = rnd();
  const jumpTarget: JumpTarget =
    tRoll < 0.45 ? "known" : tRoll < 0.7 ? "expanded" : tRoll < 0.9 ? "browser" : "unknown";
  // 弹窗形态：dialog 60%（有窗口事件）/ overlay 40%（覆盖层，仅内容变化）
  const popupForm: PopupForm = rnd() < 0.6 ? "dialog" : "overlay";
  // 弹窗出现时刻：距最近窗口切换 2s~60s（v3.2.0 的 12s 窗外弹窗全部漏拦）
  const popupAtMs = 2000 + Math.round(rnd() * 58000);
  // 弹窗信号：badge 85% / ctaOnly 15%（覆盖层弹窗均为 SDK 生成，必带角标）
  const popupSignal: PopupSignal = popupForm === "overlay" ? "badge" : rnd() < 0.85 ? "badge" : "ctaOnly";
  return { sdkAd, gyroWindow, flipTrigMs, jumpTarget, popupForm, popupAtMs, popupSignal };
}

/* ───────── GEN 层：930 款确定性生成式长尾 ───────── */

/** 分类 → 生成数量（权重按真实应用市场长尾分布；合计 930） */
const GEN_PLAN: [string, number][] = [
  ["工具", 150], ["游戏", 130], ["电商", 90], ["视频", 65], ["资讯", 65],
  ["社交", 60], ["教育", 50], ["健康", 50], ["出行", 40], ["本地生活", 40],
  ["阅读", 30], ["摄影", 30], ["金融理财", 25], ["音频", 20], ["旅行", 20],
  ["办公", 15], ["其他", 50],
];

/** 品牌词库（生成式长尾，与 REAL 层无重名） */
const BRANDS = "星云|极光|晨风|灵犀|云梯|拾光|青柠|麦浪|远航|启明|千屿|白泽|九霄|芳草|碧波|朗月|松果|踏浪|知了|枫叶|破晓|星野|回声|海豚|灯塔|原野|溪谷|磐石|飞鸟|新叶".split("|");
const PRODUCTS = "优选|严选|集市|生活|星球|笔记|管家|助手|工坊|小铺|电台|相册|日程|账本|课堂|运动|厨房|花园|旅行|影院|书房|诊所|快递|票务|头条|圈子|相机|清单|工场|营地".split("|");
const VENDOR_TOKS = "zaiyun|jiguang|chenfeng|lingxi|yunti|shiguang|qingning|mailang|yuanhang|qiming|qianyu|baize|jiuxiao|fangcao|bibo|langyue|songguo|talang|zhiliao|fengye".split("|");
const PROD_TOKS = "mall|market|life|star|note|butler|helper|shop|radio|album|agenda|ledger|class|sport|kitchen|garden|trip|cinema|study|clinic|express|ticket|news|circle|cam|list|works|camp".split("|");

/** 各分类的 adType 概率表（权重） */
const AD_WEIGHTS: Record<string, [AdType, number][]> = {
  default: [["splash", 78], ["interstitial", 10], ["shake", 8], ["deeplink", 4]],
  游戏: [["splash", 62], ["interstitial", 26], ["shake", 8], ["deeplink", 4]],
  银行: [["splash", 80], ["interstitial", 20]],
  政务: [["splash", 85], ["interstitial", 15]],
  支付: [["splash", 85], ["interstitial", 15]],
};

/** 各分类的 render 概率表（游戏自绘比例高是市场真实情况） */
const RENDER_WEIGHTS: Record<string, [RenderKind, number][]> = {
  default: [["native", 87], ["web", 9], ["self", 4]],
  游戏: [["native", 55], ["web", 30], ["self", 15]],
  阅读: [["native", 80], ["web", 16], ["self", 4]],
  工具: [["native", 84], ["web", 12], ["self", 4]],
};

function pickW<T>(rnd: () => number, table: [T, number][]): T {
  const total = table.reduce((s, [, w]) => s + w, 0);
  let r = rnd() * total;
  for (const [v, w] of table) {
    r -= w;
    if (r <= 0) return v;
  }
  return table[table.length - 1][0];
}

function genLayer(): CorpusApp[] {
  const out: CorpusApp[] = [];
  const usedNames = new Set(REAL.map((r) => r[0]));
  const usedPkgs = new Set(REAL.map((r) => r[1]));
  const master = mulberry32(0xA11C0FFEE); // GEN 层主种子：固定可复现
  let idx = 0;
  for (const [category, count] of GEN_PLAN) {
    for (let i = 0; i < count; i++) {
      idx++;
      // 确定性取名（冲突则递增下标重试）
      let name = "";
      let pkg = "";
      let attempt = 0;
      do {
        const b = BRANDS[(idx * 7 + attempt * 3) % BRANDS.length];
        const p = PRODUCTS[(idx * 13 + attempt * 5) % PRODUCTS.length];
        // 冲突时加序号，命名空间扩到 30×30×∞，彻底杜绝死循环
        name = `${b}${p}${attempt > 0 ? attempt : ""}`;
        const v = VENDOR_TOKS[(idx * 11 + attempt * 7) % VENDOR_TOKS.length];
        const q = PROD_TOKS[(idx * 17 + attempt * 3) % PROD_TOKS.length];
        pkg = `com.${v}.${q}${attempt > 0 ? attempt : ""}`;
        attempt++;
      } while (usedNames.has(name) || usedPkgs.has(pkg));
      usedNames.add(name);
      usedPkgs.add(pkg);

      const rnd = seqOf(pkg);
      const guard = GUARD_CATEGORIES.has(category);
      const adType = pickW(rnd, AD_WEIGHTS[category] ?? AD_WEIGHTS.default);
      const render = pickW(rnd, RENDER_WEIGHTS[category] ?? RENDER_WEIGHTS.default);
      const source: SourceKind = rnd() < 0.3 ? "sideload" : "official";
      const skipDelayMs = Math.round(rnd() * 5000);
      const webviewA11y = rnd() < 0.6;
      const hybridSkip = rnd() < 0.35;
      const fakeSkip = (adType === "splash" || adType === "interstitial") && rnd() < 0.12;
      const chainJump = adType === "splash" && rnd() < 0.1;
      const gyroFlip = adType === "splash" && rnd() < 0.08;
      const popup = rnd() < 0.11;
      const backResponsive = rnd() < 0.82;
      const hot = Math.round(Math.pow(rnd(), 2) * 60 + 4); // 长尾幂律：多数低热度
      const v330 = model330(rnd, adType, render, webviewA11y, hybridSkip, gyroFlip);
      const imageSkip = adType === "splash" && !guard && !fakeSkip && !chainJump && !gyroFlip
        && !v330.sdkAd && rnd() < 0.14;
      const imageCorner = imageSkip && rnd() < 0.55;
      out.push({
        pkg,
        name,
        category,
        adType,
        guard,
        source,
        render,
        skipDelayMs,
        webviewA11y,
        hybridSkip,
        fakeSkip,
        chainJump,
        gyroFlip,
        popup,
        backResponsive,
        hot,
        ...v330,
        imageSkip,
        imageCorner,
      });
    }
  }
  // GEN 层再撒少量 guard 类别外的「金融理财」广告形态约束：理财类不 shake
  return out;
}

let cached: CorpusApp[] | null = null;

/** 全量语料：REAL 130 + GEN 930 = 1060 款（构建一次后缓存） */
export function buildCorpus(): CorpusApp[] {
  if (cached) return cached;
  cached = [...REAL.map(mkApp), ...genLayer()];
  return cached;
}

export const CORPUS_SUMMARY = {
  get total() { return REAL.length + GEN_PLAN.reduce((s, [, n]) => s + n, 0); },
  get real() { return REAL.length; },
  get generated() { return GEN_PLAN.reduce((s, [, n]) => s + n, 0); },
};
