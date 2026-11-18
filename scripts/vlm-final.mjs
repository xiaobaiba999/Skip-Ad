import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';
const zai = await ZAI.create();
const b64 = fs.readFileSync('/tmp/check-final-desktop.png').toString('base64');
const res = await zai.chat.completions.createVision({
  messages: [{
    role: 'user',
    content: [
      { type: 'text', text: '检查这张网页截图：1)右侧"广告投放后台"面板是否为视口内固定的卡片（sticky布局），内部是否有滚动条 2)左侧手机模拟器显示是否正常 3)有无元素重叠或溢出。简要回答。' },
      { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
    ],
  }],
});
console.log(res.choices[0]?.message?.content?.slice(0, 500));
