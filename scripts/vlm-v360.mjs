import ZAI from 'z-ai-web-dev-sdk';
import fs from 'fs';
const zai = await ZAI.create();

async function ask(file, prompt) {
  const b64 = fs.readFileSync(file).toString('base64');
  const res = await zai.chat.completions.createVision({
    messages: [{ role: 'user', content: [
      { type: 'text', text: prompt },
      { type: 'image_url', image_url: { url: `data:image/png;base64,${b64}` } },
    ]}],
  });
  console.log(`\n===== ${file.split('/').pop()} =====`);
  console.log(res.choices[0]?.message?.content?.slice(0, 900));
}

// 演示场首页：v3.6.0 标识 + 十层纵深描述
await ask('/tmp/v360-shots/01-arena-home.png',
  '这是「开屏卫士」演示场页面截图。请核对并原文引用：1) 页面大标题的版本号；2) 秒跳引擎状态卡里的层级徽章文字（如「十层纵深」）与防线条目里是否出现「根窗口安全门」「入口窗绝不返回键」字样；3) 右上角语料徽章的轮次文字；4) 手机模拟器与八大场景列表是否正常渲染、有无文字叠印或截断。简要回答。');

// 喜马拉雅广告拦截中
await ask('/tmp/v360-shots/02-ximalaya-ad.png',
  '这是演示场中喜马拉雅广告展示期截图。请核对：1) 是否为广告展示界面（广告创意+假跳过陷阱+陀螺仪提示条）；2) 引擎状态覆盖层的文字（如「SDK 广告窗识别中」）；3) 左上角是否有「首拦学习指纹」提示；4) 有无布局异常。简要回答。');

// 拦截后
await ask('/tmp/v360-shots/03-ximalaya-after.png',
  '这是喜马拉雅启动数秒后的手机模拟器截图。请核对：1) 模拟器当前处于什么界面（拦截成功层/预拦截成功层/音频主页）；2) 若是预拦截层，耗时与「0ms 广告可见」徽章是否显示；3) 引擎面板的会话拦截日志是否有「喜马拉雅」条目；4) 有无布局异常。简要回答。');

// 情报站全页
await ask('/tmp/v360-shots/04-intel-top.png',
  '这是「情报站」页面全页截图。请核对并原文引用：1) 自测轮次标题（应为「二十三轮」相关字样）与轮次表最后两行（22/23 轮的名称与拦截率）；2) 专案列表里是否有「进入应用被模拟人操作退出到桌面」和「清理后台后无障碍服务就停了」两个 v3.6.0 专案；3) 纵深防线列表是否为「十层」且含「根窗口安全门」条目；4) 真机反馈闭环段落是否提到毒指纹/根窗口安全门/JVM 70 用例；5) 有无文字叠印或截断。简要回答。');

// 关于页
await ask('/tmp/v360-shots/06-about.png',
  '这是「关于」页面全页截图。请核对并原文引用：1) 版本历史是否恰好三个版本（3.6.0/3.5.0/3.4.0），3.6.0 的 tagline 是否含「根窗口安全门」；2) 3.6.0 的更新说明是否提到毒指纹清理、入口窗返回键、自动拉活；3) 更新通道与应用内直下说明是否在；4) 有无布局异常。简要回答。');
