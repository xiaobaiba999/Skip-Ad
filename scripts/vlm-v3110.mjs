import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

async function check(file, desc, ask) {
  const zai = await ZAI.create();
  const b64 = fs.readFileSync(file).toString('base64');
  const res = await zai.chat.completions.createVision({
    messages: [{
      role: 'user',
      content: [
        { type: 'text', text: ask },
        { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
      ],
    }],
  });
  console.log(`=== ${desc} ===`);
  console.log(res.choices[0]?.message?.content?.slice(0, 700));
  console.log();
}

await check('/tmp/v3110-home.png', '首页',
  '这是 Android 应用「开屏卫士」v3.11.0 的首页截图（无障碍跳广告工具）。请检查：1)是否有布局错乱/元素重叠/文字溢出 2)统计瓦片与开关是否正常显示 3)整体是否干净专业。简要回答，如有问题指出具体位置。');

await check('/tmp/v3110-learn.png', '学习中心',
  '这是 Android 应用「开屏卫士」v3.11.0 的「学习中心」页截图。要求：该页【不应】出现「学习规则」明细列表（已在 v3.11.0 移除），应只有「分应用统计」「引擎日志」「能耗面板」三张卡片。请检查：1)是否满足上述要求 2)是否有布局错乱/元素重叠/文字溢出 3)是否有「李跳跳」「情报站」字样。简要回答。');
