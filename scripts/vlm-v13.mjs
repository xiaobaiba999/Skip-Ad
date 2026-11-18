import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const imgs = [
  ['/tmp/and-home.png', 'Android离线页-首页(问题反馈卡片)：应含反馈类型下拉、描述框、附带画像复选框、提交按钮、高级Token折叠区、反馈历史，样式应整齐无错位'],
  ['/tmp/and-about.png', 'Android离线页-关于页(检查更新)：应显示最新版本v1.3.0·61KB、当前版本v1.3.0、已是最新版本提示、发布说明摘要若干行，无乱码无重叠'],
  ['/tmp/web-feedback.png', 'Web投放后台(问题反馈+版本与更新卡片)：应显示问题类型/描述/提交按钮、版本与更新面板显示当前v1.3.0最新v1.3.0已是最新，布局整齐'],
];

async function main() {
  const zai = await ZAI.create();
  for (const [p, desc] of imgs) {
    const b64 = fs.readFileSync(p).toString('base64');
    const res = await zai.chat.completions.createVision({
      messages: [
        { role: 'user', content: [
          { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
          { type: 'text', text: `请检查这张界面截图：${desc}。回答：1)所有要素是否齐全 2)有无布局错乱/文字重叠/乱码/溢出 3)总体是否合格。简洁回答，50字内。` },
        ] },
      ],
    });
    console.log('===', p.split('/').pop(), '===');
    console.log(res.choices[0]?.message?.content ?? '无响应');
  }
}
main().catch(e => { console.error('ERR', e.message); process.exit(1); });
