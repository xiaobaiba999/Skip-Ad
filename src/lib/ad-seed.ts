import { db } from "@/lib/db";

/**
 * 演示种子数据：按名称 upsert，保证老库升级后也能拿到 2026 新策略。
 * strategy 对应 2026-11 调研出的最新跳转形态：
 *  - timer   倒计时归零自动跳转（“拿起即跳转”的网页形态）
 *  - shake   摇一摇传感器触发（对应澎湃实测的高灵敏体感广告）
 *  - anyclick 全屏隐藏热区（对应“点击任意位置即跳转”）
 */
const SEED_ADS = [
  {
    name: "双11大促·开屏A",
    type: "splash",
    title: "双11 狂欢盛典",
    subtitle: "全场低至 1 折起 · 领券再减 300",
    ctaText: "立即抢购",
    linkUrl: "promo-1111",
    theme: "ecommerce",
    strategy: "timer",
    weight: 6,
  },
  {
    name: "新游首发·开屏B",
    type: "splash",
    title: "星际远征 手游",
    subtitle: "公测开启 · 登录送限定机甲",
    ctaText: "立即下载",
    linkUrl: "game-launch",
    theme: "game",
    strategy: "shake",
    weight: 4,
  },
  {
    name: "会员特惠·开屏C",
    type: "splash",
    title: "星云 VIP 限时 5 折",
    subtitle: "免广告 · 专属内容 · 每月观影券",
    ctaText: "开通会员",
    linkUrl: "vip-promo",
    theme: "vip",
    strategy: "anyclick",
    weight: 5,
  },
  {
    name: "理财速报·开屏D",
    type: "splash",
    title: "灵活理财 年化 3.5%",
    subtitle: "100 元起投 · 随存随取",
    ctaText: "了解详情",
    linkUrl: "finance",
    theme: "finance",
    strategy: "standard",
    weight: 4,
  },
  {
    name: "会员特惠·插屏A",
    type: "interstitial",
    title: "星云 VIP 限时 5 折",
    subtitle: "免广告 · 专属内容 · 每月观影券",
    ctaText: "开通会员",
    linkUrl: "vip-promo",
    theme: "vip",
    strategy: "standard",
    weight: 5,
  },
  {
    name: "理财开户·插屏B",
    type: "interstitial",
    title: "灵活理财 年化 3.5%",
    subtitle: "100 元起投 · 随存随取",
    ctaText: "了解详情",
    linkUrl: "finance",
    theme: "finance",
    strategy: "standard",
    weight: 5,
  },
];

let seeded = false;

export async function ensureSeed() {
  if (seeded) return;
  for (const s of SEED_ADS) {
    // 按名称 upsert：新库播种、老库补齐缺失种子并对齐演示策略
    const exist = await db.ad.findFirst({ where: { name: s.name } });
    if (!exist) {
      await db.ad.create({ data: { ...s } });
    } else if (exist.strategy !== s.strategy) {
      await db.ad.update({ where: { id: exist.id }, data: { strategy: s.strategy } });
    }
  }
  seeded = true;
}
