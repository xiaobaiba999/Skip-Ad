/** 列出仓库全部 issue（令牌经 askpass 环境变量注入，不打印） */
const TOKEN = process.env.GH_TOKEN ?? "";
async function main() {
  const h = { Authorization: `Bearer ${TOKEN}`, "User-Agent": "skip-ad-debug" };
  const li = await fetch("https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues?state=all&per_page=50&sort=created&direction=desc", { headers: h });
  const issues = await li.json();
  for (const i of issues) {
    if ("pull_request" in i) continue;
    console.log(`#${i.number} [${i.state}] ${i.title.slice(0,70)} | c=${i.comments} | ${i.created_at}`);
  }
  const rl = await fetch("https://api.github.com/repos/xiaobaiba999/Skip-Ad/releases?per_page=5", { headers: h });
  const rels = await rl.json();
  console.log("--- releases ---");
  for (const r of rels) console.log(`${r.tag_name} | assets=${r.assets?.length} | dl=${r.assets?.[0]?.download_count}`);
}
main();
