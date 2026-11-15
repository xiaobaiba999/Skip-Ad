import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';
const zai = await ZAI.create();
const b64 = fs.readFileSync('/tmp/admin-crop.png').toString('base64');
const res = await zai.chat.completions.createVision({
  messages: [{ role: 'user', content: [
    { type: 'text', text: '这是「广告投放后台」面板的放大截图。请逐项核对并原文引用：1)表格是否有「策略」列且每行有策略徽章 2)「智能学习报告·判断引擎画像」里：精准度数值、自动跳过时机、快速点击判定、主题偏好四个模块的实际文字 3)「最近判定日志」是否有条目 4)有无真实的位置重叠/文字叠印（注意：正常卡片上下排列不算重叠）。简要回答。' },
    { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
  ]}],
});
console.log(res.choices[0]?.message?.content?.slice(0, 700));
