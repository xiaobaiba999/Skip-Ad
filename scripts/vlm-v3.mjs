import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';

const zai = await ZAI.create();

const CHECKS = [
  {
    file: '/tmp/v3-web-intel.png',
    ask: '这是 Skip-Ad「情报站」页面（Web 端）。请逐项核对：1)顶部统计卡（累计拦截/平均耗时/学习规则/守护拦截）是否显示 2)「千款语料自测·自查自改×3」区块是否存在，三轮演进卡（63.76%→91.58%→91.58%）与三列小数据（引擎反应/端到端p95/日均能耗）是否完整 3)「能耗：事件驱动·零轮询」与「侧载应用一视同仁」两枚卡片文字是否完整 4)底部深色「诚实边界」条是否显示 5)有无文字溢出、重叠、截断或空白异常块。简要回答，指出问题位置。',
  },
  {
    file: '/tmp/v3-web-about.png',
    ask: '这是「关于」页面（Web 端）。请核对：1)「检查更新」卡是否显示（最新发布/当前版本或“已是最新版本”）2)「最近三个版本」是否恰好三条：v3.0.0、v2.0.0、v1.3.0（不应出现 v1.2.0 或更早）3)品牌卡与页脚是否正常 4)有无文字溢出/重叠/截断。简要回答。',
  },
  {
    file: '/tmp/v3-apk-drill.png',
    ask: '这是 Android APK 内置页面「演示场」（手机宽 412px）。请核对：1)顶部品牌栏（开屏卫士 + 版本徽章）2)服务状态卡与三格统计（自动秒跳/学习样本/学习规则）3)「开屏演练·对抗演习」卡：引擎开关与三个策略 chips（标准开屏/延迟跳过/摇一摇热区）4)手机模拟器（状态栏+信息流+底部导航）5)「问题反馈」卡（类型下拉+描述+提交按钮）6)页面底部四 tab 导航（演示场/情报站/学习中心/关于）7)有无文字溢出、按钮截断、排版重叠。简要回答。',
  },
  {
    file: '/tmp/v3-apk-intel.png',
    ask: '这是 APK「情报站」页面（412px 宽）。请核对：1)「千款语料自测·自查自改×3」卡：右上拦截率标签、三行轮次演进（1/2/3 与百分比）、四枚小卡（测试应用1010款/引擎反应249ms/日均能耗0.28mWh/侧载覆盖234/257）2)「诚实边界」灰字说明 3)策略列表卡若干（延迟跳过键/摇一摇/热区/Deeplink/侧载/边界）每张是否有「反制」绿条 4)有无溢出/重叠/截断。简要回答。',
  },
  {
    file: '/tmp/v3-apk-learn.png',
    ask: '这是 APK「学习中心」页面。请核对：1)「学习规则」卡与开关 2)「分应用统计」卡 3)「引擎日志」卡 4)「能耗面板」卡：右上绿色估算标签、三格统计（处理事件/控件树扫描/代点次数）与底部灰字说明（事件驱动·零后台轮询）5)有无溢出/重叠。简要回答。',
  },
  {
    file: '/tmp/v3-apk-about.png',
    ask: '这是 APK「关于」页面。请核对：1)「版本信息」四行 kv（当前版本/内部版本号/包名/网络）2)「检查更新」卡与说明文字 3)「最近三个版本」应为恰好三条：v3.0.0、v2.0.0、v1.3.0，不应有 v1.2.0/v1.1.0/v1.0.0 4)底部一行灰字隐私说明 5)有无溢出/重叠。简要回答。',
  },
];

for (const c of CHECKS) {
  try {
    const b64 = fs.readFileSync(c.file).toString('base64');
    const res = await zai.chat.completions.createVision({
      messages: [{ role: 'user', content: [
        { type: 'text', text: c.ask },
        { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
      ]}],
    });
    const out = res.choices[0]?.message?.content || '（无输出）';
    console.log('════ ' + c.file.split('/').pop() + ' ════');
    console.log(out.slice(0, 900));
    console.log('');
  } catch (e) {
    console.log('════ ' + c.file.split('/').pop() + ' ════\n检查失败：' + (e?.message || e) + '\n');
  }
}
