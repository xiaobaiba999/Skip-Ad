import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';
const zai = await ZAI.create();

async function check(path, prompt) {
  const b64 = fs.readFileSync(path).toString('base64');
  const res = await zai.chat.completions.createVision({
    messages: [{
      role: 'user',
      content: [
        { type: 'text', text: prompt },
        { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
      ],
    }],
  });
  console.log(`--- ${path} ---`);
  console.log(res.choices[0]?.message?.content?.slice(0, 600));
}

await check('/tmp/check-final-desktop.png',
  '检查这张网页截图（Skip-Ad 开屏广告演示系统）：1)左侧手机内是否显示开屏广告且布局正常（跳过按钮、品牌区无重叠）2)手机下方控制卡（防跳转护盾三模式/模拟摇晃/频率）是否排列整齐 3)右侧「广告投放后台」是否含智能学习报告且无溢出 4)有无任何元素重叠、错位或文字溢出。简要回答。');

await check('/tmp/check-final-intel.png',
  '检查这张页面截图（策略情报站+页脚）：1)「策略情报站 2026-11 市场扫描」卡片是否正常渲染（含来源列表与合规声明）2)页脚是否含 GitHub 链接 3)有无元素重叠或溢出。简要回答。');
