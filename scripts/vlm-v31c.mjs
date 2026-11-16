import ZAI from "z-ai-web-dev-sdk";
import { readFileSync } from "node:fs";
const SHOTS = [
  { file: "/tmp/render/shot-xmy-ignored.png", name: "假跳过已忽略阶段", expect: "广告展示中；右下角橙色「跳过 5」按钮带红色描边+划线+「陷阱已忽略」红字徽标；底部引擎提示条显示「已忽略假跳过陷阱」" },
  { file: "/tmp/render/shot-xmy-chain2.png", name: "链式识别阶段", expect: "广告展示中；假跳过按钮仍带陷阱已忽略标识；底部提示条变为红色「链式二跳识别→升级直接阻断」" },
];
const zai = await ZAI.create();
let allPass = true;
for (const s of SHOTS) {
  const b64 = readFileSync(s.file).toString("base64");
  try {
    const res = await zai.chat.completions.createVision({
      messages: [{ role: "user", content: [
        { type: "image_url", image_url: { url: `data:image/png;base64,${b64}` } },
        { type: "text", text: `检查「Skip-Ad」手机模拟器截图（${s.name}）。期望：${s.expect}\n检查元素是否齐全、文字是否清晰可读无乱码。两句话内回答，最后一行：PASS 或 FAIL(原因)。` },
      ]}],
    });
    const out = res.choices[0]?.message?.content ?? "";
    console.log(`\n──── ${s.name} ────\n${out.trim()}`);
    if (!/PASS/.test(out)) allPass = false;
  } catch (e) {
    console.log(`\n──── ${s.name} ────\n✗ 调用失败：${e instanceof Error ? e.message : e}`);
    allPass = false;
  }
}
console.log(`\n═══ 总结：${allPass ? "全部 PASS ✅" : "存在 FAIL ❌"} ═══`);
