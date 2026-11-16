import ZAI from "z-ai-web-dev-sdk";
import { readFileSync } from "node:fs";
const zai = await ZAI.create();
const b64 = readFileSync("/tmp/render/shot-xmy-chain3.png").toString("base64");
const res = await zai.chat.completions.createVision({
  messages: [{ role: "user", content: [
    { type: "image_url", image_url: { url: `data:image/png;base64,${b64}` } },
    { type: "text", text: `检查「Skip-Ad」手机模拟器截图（链式二跳识别阶段）。期望：广告展示中；右下角「跳过 5」假按钮带红色描边+划线+陷阱已忽略徽标；底部提示条为红色，文字含「链式二跳识别」「直接阻断」；文字清晰无乱码。两句话内回答，最后一行：PASS 或 FAIL(原因)。` },
  ]}],
});
console.log(res.choices[0]?.message?.content?.trim());
