/* VLM 视觉核查 · 第二轮：v3.3.0 修复后（Web 首页/喜马拉雅链路/情报站/关于 + APK 三页） */
import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const zai = await ZAI.create();

const CHECKS = [
  {
    file: '/tmp/shot-home.png',
    name: 'Web 首页·演练场',
    expect: '顶栏「Skip-Ad·秒跳引擎 v3.3.0」+ 副文案「SDK 广告窗指纹 · 极速链 0ms 首扫 · 热区直断 · 链式回弹 · 弹窗 v2 · 十四轮自测 100%」、左侧手机模拟器、右侧「七层纵深」引擎卡。',
  },
  {
    file: '/tmp/shot-xmy-ad.png',
    name: '喜马拉雅广告阶段（首拦·无指纹）',
    expect: '手机内京东企业采购广告 + 覆盖层「SDK 广告窗识别中 · className 指纹比对（零文本可判）」或「SDK 窗指纹命中 · 复核无真跳过键 → 升级直断」、底部「翻转手机，跳转详情页或第三方应用」提示条、左上角「首拦学习指纹」角标。',
  },
  {
    file: '/tmp/shot-xmy-flash.png',
    name: '喜马拉雅拦截成功层（首拦）',
    expect: '深色成功层大标题「SDK 广告窗已直断 · 零文本广告照样拦」+ 耗时 ~6xx ms + 置信度；这是 SDK 指纹直断路径（非 P0 预拦截的「广告未及呈现」）。',
  },
  {
    file: '/tmp/shot-about.png',
    name: 'Web 关于页·检查更新（v3.3.0 已发布）',
    expect: '检查更新卡片：最新发布 v3.3.0 · 61.1 KB / 当前版本 v3.3.0，应显示「已是最新版本」与「重新下载当前版本 APK（页内直下）」按钮（不应有报错）。',
  },
  {
    file: '/tmp/apk-home.png',
    name: 'APK 首页（离线页·手机视口）',
    expect: '顶栏「开屏卫士」+版本徽章；服务状态卡（浏览器预览模式 + 六格统计）；防护能力卡：四行开关（开屏秒跳含 SDK 广告窗识别描述 / 应用内弹窗广告屏蔽 v2 / 诱导跳转回弹（名单扩充） / 智能学习）；问题反馈卡（下拉含「误弹回：正常浏览被送回」选项）。不得出现任何「演练场/演示场」tab（只有 首页/情报站/学习中心/关于 四 tab）。',
  },
  {
    file: '/tmp/apk-intel.png',
    name: 'APK 情报站（离线页）',
    expect: '「千款语料自测 · 十四轮 · 诚实重构」+ 100% 徽章 + 14 行轮次（77.55%→100%）+ 四格小数据 + 策略卡片列表（首卡应为「SDK 广告窗 · 零文本图片广告（喜马拉雅根因）」）。',
  },
  {
    file: '/tmp/apk-about.png',
    name: 'APK 关于页（离线页）',
    expect: '版本信息卡（浏览器预览 v3.3.0）+ 检查更新卡（页内直下 + 备用通道说明）+ 最近三个版本（v3.3.0/v3.2.0/v3.1.0，v3.3.0 含 SDK 广告窗识别等要点）。',
  },
];

let fail = 0;
for (const c of CHECKS) {
  if (!fs.existsSync(c.file)) { console.log(`[SKIP] ${c.name}（文件不存在）`); continue; }
  const b64 = fs.readFileSync(c.file).toString('base64');
  const res = await zai.chat.completions.createVision({
    messages: [{ role: 'user', content: [
      { type: 'text', text: `你在核查「开屏卫士/Skip-Ad」界面截图（${c.name}）。\n期望：${c.expect}\n请逐项核对期望元素是否在、有无乱码/占位符/undefined/明显错位/旧版残留。两三句话回答，最后单独一行：PASS 或 FAIL(原因)。` },
      { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
    ]}],
  });
  const out = res.choices[0]?.message?.content?.trim() ?? '(空)';
  console.log(`\n═══ ${c.name} ═══\n${out}`);
  if (/^FAIL/i.test(out.split('\n').pop() ?? '')) fail++;
}
console.log(`\n────── VLM 第二轮：${CHECKS.length - fail}/${CHECKS.length} PASS ──────`);
process.exit(fail > 0 ? 1 : 0);
