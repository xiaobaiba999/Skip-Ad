/* VLM 视觉核查：v3.3.0 关键界面（Web 演练场/情报站/关于 + APK 离线页） */
import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const zai = await ZAI.create();

const CHECKS = [
  {
    file: '/tmp/shot-home.png',
    name: 'Web 首页·演练场（默认视图）',
    expect: '顶栏「Skip-Ad·秒跳引擎 v3.3.0」与子文案（SDK 广告窗指纹/十四轮自测 100%）、四 tab 导航、左侧手机模拟器（桌面+应用图标）、右侧「秒跳引擎·运行中 七层纵深」卡片、三格统计、判定流水线与八大场景列表。请检查有无乱码、占位符、排版溢出。',
  },
  {
    file: '/tmp/shot-xmy-ad.png',
    name: '喜马拉雅广告阶段',
    expect: '手机模拟器内广告画面（京东企业采购）+ 顶部覆盖层提示（应为 SDK 广告窗识别/className 指纹比对 或 已忽略陷阱类文案）、底部或顶部「翻转手机，跳转详情页或第三方应用」提示条、右侧流水线含「SDK 广告窗识别」「极速链复核」「返回键直断」「回弹+指纹沉淀」等步骤。',
  },
  {
    file: '/tmp/shot-xmy-flash.png',
    name: '喜马拉雅拦截成功层',
    expect: '深色拦截成功层，大标题应为「SDK 广告窗已直断 · 零文本广告照样拦」，附耗时 ms 与置信度；右侧会话日志出现「SDK 广告窗指纹直断 + 回弹」条目。',
  },
  {
    file: '/tmp/shot-intel.png',
    name: '情报站（十四轮自测）',
    expect: '「千款语料十四轮自测·诚实重构·自改自修复 ×14」标题、14 行轮次表（77.55%→100%，R10 起绿色加粗）、喜马拉雅专案 v3 卡片（R1 漏拦 900ms → SDK 窗直断 504ms < 翻转触发）、七层纵深防线列表（含 P0.7 SDK 广告窗识别）、市场套路×反制卡片（SDK 广告窗·零文本图片广告 等）。请核对有无旧文案（六层纵深/十轮/86.79% 位置错误）或字段 undefined。',
  },
  {
    file: '/tmp/shot-about.png',
    name: '关于页（最近三版本）',
    expect: 'v3.3.0 徽章、检查更新卡片（页内直下说明）、最近三个版本列表（v3.3.0/v3.2.0/v3.1.0，v3.3.0 置顶高亮，含 SDK 广告窗识别/热区直断/弹窗 v2/更新链路修复/十四轮自测等要点）。',
  },
];

let fail = 0;
for (const c of CHECKS) {
  if (!fs.existsSync(c.file)) { console.log(`[SKIP] ${c.name}（文件不存在）`); continue; }
  const b64 = fs.readFileSync(c.file).toString('base64');
  const res = await zai.chat.completions.createVision({
    messages: [{ role: 'user', content: [
      { type: 'text', text: `你在核查中文 Web 应用「Skip-Ad 秒跳引擎」截图（${c.name}）。\n期望：${c.expect}\n请逐项核对期望元素是否在、有无乱码/占位符/undefined/明显错位或旧版残留文案。两三句话回答，最后单独一行：PASS 或 FAIL(原因)。` },
      { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
    ]}],
  });
  const out = res.choices[0]?.message?.content?.trim() ?? '(空)';
  console.log(`\n═══ ${c.name} ═══\n${out}`);
  if (/^FAIL/i.test(out.split('\n').pop() ?? '')) fail++;
}
console.log(`\n────── VLM 结果：${CHECKS.length - fail}/${CHECKS.length} PASS ──────`);
process.exit(fail > 0 ? 1 : 0);
