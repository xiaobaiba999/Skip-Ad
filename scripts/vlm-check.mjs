import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

async function check(file, desc) {
  const zai = await ZAI.create();
  const b64 = fs.readFileSync(file).toString('base64');
  const res = await zai.chat.completions.createVision({
    messages: [{
      role: 'user',
      content: [
        { type: 'text', text: `这是一个"开屏广告+跳转广告演示系统"网页的${desc}截图。请检查：1)是否有布局错乱/元素重叠/文字溢出 2)手机模拟器是否显示正常 3)整体视觉是否专业。请简要回答，如有问题指出具体位置。` },
        { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
      ],
    }],
  });
  console.log(`=== ${desc} ===`);
  console.log(res.choices[0]?.message?.content?.slice(0, 600));
}

await check('/tmp/check-mobile.png', '移动端(390px)');
await check('/tmp/check-desktop.png', '桌面端(1440px)');
