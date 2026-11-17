/** 批量拉取 issue 正文存到 scripts/issue-N.json */
const TOKEN = process.env.GH_TOKEN ?? "";
async function main() {
  const nums = process.argv.slice(2).map(Number);
  const h = { Authorization: `Bearer ${TOKEN}`, "User-Agent": "skip-ad-debug" };
  const fs = await import("fs");
  for (const n of nums) {
    const r = await fetch(`https://api.github.com/repos/xiaobaiba999/Skip-Ad/issues/${n}`, { headers: h });
    const d = await r.json();
    if (!r.ok) { console.log(`#${n} FAIL ${r.status}`); continue; }
    fs.writeFileSync(`scripts/issue-${n}.json`, JSON.stringify({ number: d.number, title: d.title, state: d.state, body: d.body }, null, 2));
    console.log(`#${n} saved | ${d.title.slice(0, 60)}`);
  }
}
main();
