import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const PAGES = [
  ['/tmp/shot-arena.png', '演练场（手机模拟器+秒跳引擎面板）'],
  ['/tmp/shot-intel.png', '情报站（统计卡+市场套路反制+规则库表格+防线对比）'],
  ['/tmp/shot-learning.png', '学习中心（画像+学习规则+夜间同步深色面板+事件流）'],
  ['/tmp/shot-about.png', '关于页（品牌+最近三个版本）'],
];

async function main() {
  const zai = await ZAI.create();
  for (const [file, desc] of PAGES) {
    const b64 = fs.readFileSync(file).toString('base64');
    const res = await zai.chat.completions.createVision({
      messages: [{
        role: 'user',
        content: [
          {
            type: 'text',
            text: `这是「Skip-Ad 秒跳引擎」网页的${desc}截图。请严格检查：1)布局错乱/元素重叠/文字溢出/空白异常区块 2)表格列是否对齐、数据是否为空 3)配色与对比度是否清晰可读 4)整体是否专业。若一切正常回答「通过」并用一句话概括；如有问题，指出具体位置与现象。`,
          },
          { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
        ],
      }],
    });
    console.log(`\n=== ${desc} ===`);
    console.log(res.choices[0]?.message?.content?.slice(0, 500));
  }
}

main().catch((e) => { console.error('VLM check failed:', e.message); process.exit(1); });
