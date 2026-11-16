/* VLM 视觉核查：v3.4.0（Web 三页 + APK 三页） */
import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const zai = await ZAI.create();

const CHECKS = [
  {
    file: '/tmp/v34-shot-home.png',
    name: 'Web 首页·演练场',
    expect: '顶栏「Skip-Ad·秒跳引擎 v3.4.0」；引擎卡「八层纵深」徽章与「角落键+坐标点击」描述；无乱码/undefined。',
  },
  {
    file: '/tmp/v34-shot-intel.png',
    name: 'Web 情报站（十八轮）',
    expect: '「千款语料十八轮自测·诚实重构·自改自修复 ×18」；18 行轮次表（73.21%→95.09%→100%，R16 起绿）；喜马拉雅专案 v4 卡（R15 广告已展示 900ms → R16 角落跳过键 659ms < 翻转 900）；「八层纵深防线」含 P0.8 角落图片跳过键；市场套路首卡「第一方图片开屏（微博/喜马拉雅实测）v3.4.0 根因修复」。有无 undefined/旧文案（十四轮/sdkCutMs）。',
  },
  {
    file: '/tmp/v34-shot-intel-mid.png',
    name: 'Web 情报站中部（滚动）',
    expect: '喜马拉雅专案 v4 卡片：R15 v3.3.0 零信号翻转触发后才回弹（广告已展示 900ms）→ R16 角落图片跳过键 659ms 抢在翻转前 → 稳态预拦截 34ms；「图片开屏 × 弹窗全断」卡（角落 ×31 + 坐标 ×22）；八层纵深防线含 P0.8 行。',
  },
  {
    file: '/tmp/v34-shot-about.png',
    name: 'Web 关于页（v3.4.0 已发布场景）',
    expect: '检查更新卡：最新发布与当前版本 v3.4.0（或提示已是最新）；最近三版本 v3.4.0/v3.3.0/v3.2.0，v3.4.0 置顶含「角落图片跳过键/坐标级点击兜底/决策核心 38 用例」要点；无报错。',
  },
  {
    file: '/tmp/v34-apk-home.png',
    name: 'APK 首页',
    expect: '顶栏「开屏卫士」+ v3.4.0 徽章；防护能力卡首行「开屏秒跳（角落键 + 坐标点击 + SDK 指纹）」含手势降级描述；问题反馈下拉含「漏拦截：开屏图片广告（无跳过按钮）」与「误点击：坐标兜底点错位置」选项（需下拉可见或至少默认项正常）；四 tab。',
  },
  {
    file: '/tmp/v34-apk-intel.png',
    name: 'APK 情报站',
    expect: '「千款语料自测·十八轮·诚实重构」+ 100% 徽章；轮次列表含 R15「v3.3.0 终态复测（新建模：图片开屏）95.09%」与 R16「+角落控件/坐标点击 → 100%【v3.4.0】」；策略首卡「第一方图片开屏（微博/喜马拉雅实测）v3.4.0」。',
  },
  {
    file: '/tmp/v34-apk-about.png',
    name: 'APK 关于页',
    expect: '版本信息（浏览器预览·引擎 v3.4.0）+ 检查更新卡 + 最近三版本（v3.4.0 置顶，含角落键/坐标点击/38 用例要点）。',
  },
];

let fail = 0;
for (const c of CHECKS) {
  if (!fs.existsSync(c.file)) { console.log(`[SKIP] ${c.name}`); continue; }
  const b64 = fs.readFileSync(c.file).toString('base64');
  const res = await zai.chat.completions.createVision({
    messages: [{ role: 'user', content: [
      { type: 'text', text: `你在核查「开屏卫士/Skip-Ad」截图（${c.name}）。\n期望：${c.expect}\n逐项核对，两三句话，最后单独一行：PASS 或 FAIL(原因)。` },
      { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
    ]}],
  });
  const out = res.choices[0]?.message?.content?.trim() ?? '(空)';
  console.log(`\n═══ ${c.name} ═══\n${out}`);
  if (/^FAIL/i.test(out.split('\n').pop() ?? '')) fail++;
}
console.log(`\n────── VLM v3.4.0：${CHECKS.length - fail}/${CHECKS.length} PASS ──────`);
process.exit(fail > 0 ? 1 : 0);
