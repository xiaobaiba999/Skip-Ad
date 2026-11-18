import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';
const zai = await ZAI.create();
const b64 = fs.readFileSync('/tmp/v3-apk-drill3.png').toString('base64');
const res = await zai.chat.completions.createVision({
  messages: [{ role: 'user', content: [
    { type: 'text', text: '这是 Android APK「演示场」页面滚动到最底部后的截图。请核对：1)「问题反馈」卡：类型下拉、描述输入框、「提交（内置加密通道·零配置）」按钮是否齐全 2)有无任何 Token/令牌输入框（不应有）3)反馈历史区 4)有无溢出/重叠/截断。简要回答。' },
    { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
  ]}],
});
console.log((res.choices[0]?.message?.content || '').slice(0, 600));
