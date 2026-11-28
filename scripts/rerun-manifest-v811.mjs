import { getGhToken } from "/home/z/skip-ad-repo/scripts/gh-token.mjs";
const token = getGhToken();
const REPO = "xiaobaiba999/Skip-Ad";
const headers = { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json", "User-Agent": "skip-ad-instant-engine" };
// 重新运行失败的 manifest 工作流（release 事件重放 = rerun failed）
const r = await fetch(`https://api.github.com/repos/${REPO}/actions/runs/46113624483/rerun-failed-jobs`, {
  method: "POST",
  headers,
});
console.log(r.status === 201 ? "RERUN OK" : "RERUN FAIL " + r.status + " " + await r.text());
