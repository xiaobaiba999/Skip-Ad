import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';
const zai = await ZAI.create();
const CHECKS = [
  {
    file: '/tmp/v3-apk-drill2.png',
    ask: '这是 Android APK「演示场」页面向下滚动后的截图。请核对：1)「当前策略学习进度」卡（置信度标签+进度条+日志框）2)「问题反馈」卡：类型下拉（漏拦截/摇一摇仍跳转/误拦截…）、描述输入框、「提交（内置加密通道·零配置）」按钮是否齐全 3)页面里是否还有任何「Token / 令牌输入框」（不应该有）4)有无溢出/重叠/截断。简要回答。',
  },
  {
    file: '/tmp/v3-apk-about2.png',
    ask: '这是 Android APK「关于」页向下滚动后的截图。请核对：1)「最近三个版本」是否恰好三条：v3.0.0、v2.0.0、v1.3.0，且没有 v1.2.0 / v1.1.0 / v1.0.0 或更早版本 2)最底部一行灰字（学习数据仅存本机；联网仅问题反馈与检查更新…）3)有无溢出/重叠/截断。简要回答。',
  },
];
for (const c of CHECKS) {
  const b64 = fs.readFileSync(c.file).toString('base64');
  const res = await zai.chat.completions.createVision({
    messages: [{ role: 'user', content: [
      { type: 'text', text: c.ask },
      { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
    ]}],
  });
  console.log('════ ' + c.file.split('/').pop() + ' ════');
  console.log((res.choices[0]?.message?.content || '（无输出）').slice(0, 800));
  console.log('');
}
