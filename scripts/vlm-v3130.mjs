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
  console.log(res.choices[0]?.message?.content?.slice(0, 600));
  console.log();
}

// 演练场
await check('/tmp/v3130-arena.png', '演练场',
  '这是跳广告应用「开屏卫士」Web 演示的演练场页（v3.13.0）。请检查：1)左侧手机模拟器与右侧引擎面板是否布局正常、无元素重叠/文字溢出 2)会话拦截日志是否显示了拦截记录（如喜马拉雅/极选商城）3)顶部版本号是否为 v3.13.0。简要回答，如有问题指出具体位置。');

// 学习中心
await check('/tmp/v3130-learn.png', '学习中心',
  '这是「开屏卫士」v3.13.0 的学习中心页。要求：该页【不应】出现「常用应用 TOP」分应用统计条形图明细（已按用户反馈移除），应有「应用覆盖」一行极简汇总（如「N 款应用 · 共拦截 N 次」），且保留使用时段柱状图/场景分布/学习规则/夜间同步/学习事件流。请检查：1)是否满足上述要求 2)是否有布局错乱/重叠/溢出 3)是否有「李跳跳」等敏感竞品字样。简要回答。');

// 关于
await check('/tmp/v3130-about.png', '关于页',
  '这是「开屏卫士」v3.13.0 的关于页。要求：版本历史【必须恰好 3 张卡】：v3.13.0（当前版本，含验证码横幅四重防护/息屏休眠/夜间心跳降频）、v3.12.0、v3.11.0，不得出现更多或更少版本卡。请检查：1)版本卡数量与版本号是否正确 2)是否有布局错乱/重叠/溢出 3)当前版本徽章是否在 v3.13.0 上。简要回答。');
