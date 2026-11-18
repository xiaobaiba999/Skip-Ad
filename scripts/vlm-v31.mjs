/* VLM 视觉检查：v3.1.0 关键界面（演练场/喜马拉雅专案/预拦截/情报站/关于） */
import ZAI from "z-ai-web-dev-sdk";
import { readFileSync } from "node:fs";

const SHOTS = [
  { file: "/tmp/render/shot-arena-home.png", name: "演练场首页", expect: "七大场景应用图标（含喜马拉雅）、引擎状态卡（五层纵深）、三个统计chip、判定流水线、会话日志；不应有引擎开关/对照模式" },
  { file: "/tmp/render/shot-xmy-chain.png", name: "喜马拉雅广告甄别阶段", expect: "手机内广告展示，右下角假跳过按钮（划线+陷阱已忽略标识或倒计时样式），底部引擎甄别提示；右侧流水线含假跳过甄别/链式二跳识别步骤" },
  { file: "/tmp/render/shot-preempt-flash.png", name: "P0 预拦截闪光层", expect: "深色全屏层：预拦截成功·广告未及呈现、耗时ms、0ms 广告可见时间·用户零感知徽标" },
  { file: "/tmp/render/shot-intel.png", name: "情报站（十轮自测）", expect: "十轮演进表格（10行，拦截率列后三行100%）、喜马拉雅专案卡、五层纵深防线列表、市场套路×反制卡片、规则库表格；无占位符/错位" },
  { file: "/tmp/render/shot-about.png", name: "关于页（页内直下）", expect: "品牌卡 v3.1.0、检查更新区（已是最新+重新下载按钮或下载完成提示）、最近三个版本（3.1.0/3.0.0/2.0.0）" },
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
                text: `你在检查一个中文 Web 应用「Skip-Ad 秒跳引擎」的界面截图（${s.name}）。\n期望内容：${s.expect}\n请检查：1) 期望元素是否都在；2) 有无文字溢出/重叠/截断；3) 有无乱码、占位符（XXX/TODO/undefined/NaN）；4) 布局是否明显错位。\n用两三句话回答，最后单独一行给结论：PASS 或 FAIL(原因)。`,
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
  console.log(`\n═══ 视觉检查总结：${allPass ? "全部 PASS ✅" : "存在 FAIL ❌"} ═══`);
}

main();
