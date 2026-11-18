/* VLM 视觉核查：v3.5.0（Web 关键页 + 图标） */
import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const zai = await ZAI.create();

const CHECKS = [
  {
    file: '/tmp/render/web-home.png',
    name: 'Web 首页·演练场（绿色 Logo）',
    expect: '顶栏左侧是【绿色渐变盾牌+闪电】Logo（翡翠绿，不再是紫色），标题「Skip-Ad·秒跳引擎 v3.5.0」绿色版本徽章；引擎卡「九层纵深」徽章；文案含「点击复验换候选重试」「学习位置点击」；无乱码/undefined/旧文案（八层纵深）。',
  },
  {
    file: '/tmp/render/xm-first-ad.png',
    name: '演练场·喜马拉雅首启广告',
    expect: '手机模拟器中开屏广告展示，右上/底部有「翻转手机，跳转详情页或第三方应用」提示条，广告上有假跳过按钮与「陷阱已忽略」或引擎识别状态覆盖层；右侧流水线面板正常渲染，无错位。',
  },
  {
    file: '/tmp/render/web-about.png',
    name: 'Web 关于页（v3.5.0）',
    expect: '最近三个版本 v3.5.0/v3.4.0/v3.3.0，v3.5.0 置顶且要点含「点击复验梯子」「位置学习」「服务保活」「引擎日志上传」「全新绿色 Logo」；页面无 v3.2.0 残留、无 undefined。',
  },
  {
    file: '/tmp/render/web-intel.png',
    name: 'Web 情报站',
    expect: '正常渲染千款自测数据与轮次表；无报错、无 undefined、无空白大块。',
  },
  {
    file: '/tmp/render/logo-check.png',
    name: '全新绿色图标（左：标准 / 右：maskable）',
    expect: '两个应用图标都是【绿色渐变圆角方块 + 白色盾牌 + 深绿闪电】，左右风格一致；无紫色残留、无变形、无文字。',
  },
];

let fail = 0;
for (const c of CHECKS) {
  const b64 = fs.readFileSync(c.file).toString('base64');
  try {
    const res = await zai.chat.completions.createVision({
      messages: [
        {
          role: 'user',
          content: [
            { type: 'text', text: `你是 UI 验收员。检查截图是否满足以下预期，逐项回答满足/不满足并给一句理由，最后给出总结论 PASS 或 FAIL：\n${c.expect}` },
            { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
          ],
        },
      ],
      thinking: { type: 'disabled' },
    });
    const out = res.choices[0]?.message?.content ?? '';
    const isFail = /FAIL/i.test(out.split('\n').reverse().find((l) => /PASS|FAIL/i.test(l)) ?? '');
    if (isFail) fail++;
    console.log(`\n[${isFail ? '✗' : '✓'}] ${c.name}\n${out}`);
  } catch (e) {
    fail++;
    console.log(`\n[✗] ${c.name} VLM 调用失败：${e.message}`);
  }
}
console.log(`\n═══ VLM 结果：${CHECKS.length - fail} PASS / ${fail} FAIL ═══`);
process.exit(fail > 0 ? 1 : 0);
