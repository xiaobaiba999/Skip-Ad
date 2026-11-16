/**
 * 演练场场景应用定义
 * 6 个典型应用覆盖全部广告形态与守护场景：
 *  电商·标准开屏 / 游戏·摇一摇 / 视频·Deeplink 诱导 /
 *  银行·政务·支付·守护模式（拦广告但绝不终止服务）
 */
import type { AdType, AppCategory } from "./instant-engine";

export interface ScenarioAd {
  brand: string; // 广告主品牌（对照模式落地页用）
  title: string;
  subtitle: string;
  ctaText: string;
  theme: "ecommerce" | "game" | "vip" | "finance" | "gov" | "pay";
  countdownSec: number; // 对照模式（引擎关闭）时的倒计时
  landingHint: string; // 对照模式被跳转后的落地页提示
}

export interface ScenarioApp {
  key: string;
  name: string;
  category: AppCategory;
  adType: AdType;
  guard: boolean;
  ruleName: string; // 命中的预置规则名
  icon: string; // lucide 图标名（组件内映射）
  gradient: string; // 图标渐变
  screen: "mall" | "game" | "bank" | "gov" | "pay" | "video";
  ad: ScenarioAd;
  note: string; // 演练场卡片的一句话说明
}

export const SCENARIO_APPS: ScenarioApp[] = [
  {
    key: "mall",
    name: "极选商城",
    category: "电商",
    adType: "splash",
    guard: false,
    ruleName: "全局·跳过文本规则",
    icon: "shopping-bag",
    gradient: "from-rose-500 to-orange-400",
    screen: "mall",
    ad: {
      brand: "聚星商城",
      title: "11.11 狂欢盛典",
      subtitle: "全场低至 1 折 · 大额券限量抢",
      ctaText: "立即抢购",
      theme: "ecommerce",
      countdownSec: 5,
      landingHint: "已跳转聚星商城活动页",
    },
    note: "标准开屏：倒计时 + 小小跳过键",
  },
  {
    key: "game",
    name: "星际远征",
    category: "游戏",
    adType: "shake",
    guard: false,
    ruleName: "全局·传感器挂起规则",
    icon: "rocket",
    gradient: "from-violet-600 to-fuchsia-500",
    screen: "game",
    ad: {
      brand: "星际远征公测",
      title: "年度科幻机甲手游",
      subtitle: "摇一摇立即预约 · 开服送十连抽",
      ctaText: "摇一摇参与",
      theme: "game",
      countdownSec: 6,
      landingHint: "摇晃触发「有效点击」，已跳转预约页",
    },
    note: "摇一摇：拿起手机即被跳转",
  },
  {
    key: "bank",
    name: "惠银银行",
    category: "银行",
    adType: "splash",
    guard: true,
    ruleName: "守护名单·温和代点规则",
    icon: "landmark",
    gradient: "from-sky-600 to-blue-500",
    screen: "bank",
    ad: {
      brand: "星云钱包",
      title: "灵活理财新选择",
      subtitle: "100 元起投 · 随存随取",
      ctaText: "了解详情",
      theme: "finance",
      countdownSec: 5,
      landingHint: "已跳转星云钱包推广页",
    },
    note: "守护模式：拦广告，不碰服务",
  },
  {
    key: "gov",
    name: "政务通",
    category: "政务",
    adType: "interstitial",
    guard: true,
    ruleName: "守护名单·温和代点规则",
    icon: "building-2",
    gradient: "from-teal-600 to-emerald-500",
    screen: "gov",
    ad: {
      brand: "惠民生活服务",
      title: "水电燃气一键代缴",
      subtitle: "新用户立减 10 元",
      ctaText: "立即开通",
      theme: "gov",
      countdownSec: 4,
      landingHint: "已跳转第三方推广页",
    },
    note: "守护模式：办事弹窗广告照拦",
  },
  {
    key: "pay",
    name: "惠民支付",
    category: "支付",
    adType: "shake",
    guard: true,
    ruleName: "守护名单·传感器双控规则",
    icon: "wallet",
    gradient: "from-emerald-600 to-teal-500",
    screen: "pay",
    ad: {
      brand: "分期钱包",
      title: "买单分期 0 首期",
      subtitle: "摇一摇领 188 元红包",
      ctaText: "摇一摇领取",
      theme: "pay",
      countdownSec: 6,
      landingHint: "摇晃触发跳转，已进入借贷推广页",
    },
    note: "守护 + 传感器：支付场景双保险",
  },
  {
    key: "video",
    name: "刷刷视频",
    category: "视频",
    adType: "deeplink",
    guard: false,
    ruleName: "全局·Scheme 黑名单规则",
    icon: "play",
    gradient: "from-pink-500 to-rose-500",
    screen: "video",
    ad: {
      brand: "星云 VIP",
      title: "免广告畅看权益",
      subtitle: "限时 5 折 · 首月 6 元",
      ctaText: "0 元开通",
      theme: "vip",
      countdownSec: 5,
      landingHint: "倒计时归零自动唤起外部 App（二跳）",
    },
    note: "Deeplink：自动唤起外部应用",
  },
];

export const findApp = (key: string) => SCENARIO_APPS.find((a) => a.key === key) ?? null;
