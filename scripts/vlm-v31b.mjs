/* VLM 复查：喜马拉雅首启广告甄别阶段（首次启动的截图） */
import ZAI from "z-ai-web-dev-sdk";
import { readFileSync } from "node:fs";

const SHOTS = [
  { file: "/tmp/render/e2e-xmy-ad.png", name: "喜马拉雅首启·广告甄别中", expect: "手机内广告展示（电商风格），右下角或角落有「跳过 5」假跳过按钮（橙色渐变大按钮），底部引擎提示条（假跳过甄别中/已忽略），左上角可能有「首次拦截将沉淀指纹」提示" },
  { file: "/tmp/render/e2e-xmy-ad2.png", name: "喜马拉雅首启·链式识别阶段", expect: "广告仍在或已进入拦截闪光；若在广告阶段：假跳过按钮应显示划线+「陷阱已忽略」红色标识，底部提示为链式二跳识别（红色）；右侧流水线含「假跳过甄别」「链式二跳识别」「窗口级直接阻断」步骤" },
];

async function main() {
  const zai = await ZAI.create();
  let allPass = true;
  for (const s of SHOTS) {
    const b64 = readFileSync(s.file).toString("base64");
    try {
      const res = await zai.chat.completions.createVision({
        messages: [
          {
            role: "user",
            content: [
              { type: "image_url", image_url: { url: `data:image/png;base64,${b64}` } },
              {
                type: "text",
                text: `你在检查中文 Web 应用「Skip-Ad 秒跳引擎」演练场的手机模拟器截图（${s.name}）。\n期望：${s.expect}\n请检查期望元素是否在、有无乱码/占位符/明显错位。两三句话回答，最后单独一行：PASS 或 FAIL(原因)。`,
              },
            ],
          },
        ],
      });
      const out = res.choices[0]?.message?.content ?? "(无输出)";
      console.log(`\n──── ${s.name} ────\n${out.trim()}`);
      if (!/PASS/.test(out)) allPass = false;
    } catch (e) {
      console.log(`\n──── ${s.name} ────\n✗ VLM 调用失败：${e instanceof Error ? e.message : e}`);
      allPass = false;
    }
  }
  console.log(`\n═══ 复查总结：${allPass ? "全部 PASS ✅" : "存在 FAIL ❌"} ═══`);
}

main();
