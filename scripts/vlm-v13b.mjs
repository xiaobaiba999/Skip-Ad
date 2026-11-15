import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const imgs = [
  ['/tmp/and-top.png', 'Android关于页滚动到最顶部：版本信息卡片(当前版本/版本号内部/包名/体积/网络权限)应完整可见且不被顶栏遮挡'],
  ['/tmp/and-bottom.png', 'Android关于页滚动到最底部：最后的内容(真机测试清单)应完整可见，底部导航栏(首页/演练场/情报站/学习中心/关于)不应遮挡任何正文文字'],
];

async function main() {
  const zai = await ZAI.create();
  for (const [p, desc] of imgs) {
    const b64 = fs.readFileSync(p).toString('base64');
    const res = await zai.chat.completions.createVision({
      messages: [
        { role: 'user', content: [
          { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
          { type: 'text', text: `检查截图：${desc}。请明确回答：1)首/尾内容是否完整可见 2)是否存在元素真正叠在一起渲染(而非滚动截断) 3)结论。60字内。` },
        ] },
      ],
    });
    console.log('===', p.split('/').pop(), '===');
    console.log(res.choices[0]?.message?.content ?? '无响应');
  }
}
main().catch(e => { console.error('ERR', e.message); process.exit(1); });
