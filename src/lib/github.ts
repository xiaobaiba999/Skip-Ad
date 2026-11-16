/**
 * GitHub 服务端操作库（SERVER-ONLY）
 * ─────────────────────────────────────────────────────────────
 * · 令牌来自 credentials.ts 的内置混淆存储，客户端零接触；
 * · 出口仅三类：Issues（问题反馈）、Contents（学习规则夜间同步）、
 *   Releases（版本发布）。
 */
import { getGithubToken } from "./credentials";
import { GITHUB_API, GITHUB_REPO } from "./version";

const API_ROOT = "https://api.github.com";

interface GhResult<T = unknown> {
  ok: boolean;
  status: number;
  data: T;
  message?: string;
}

async function gh<T = unknown>(
  path: string,
  init: RequestInit & { body?: string } = {},
): Promise<GhResult<T>> {
  const token = getGithubToken();
  if (!token) {
    return { ok: false, status: 401, data: null as T, message: "内置令牌不可用" };
  }
  try {
    const res = await fetch(path.startsWith("http") ? path : `${API_ROOT}${path}`, {
      ...init,
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "skip-ad-instant-engine",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
      },
      cache: "no-store",
    });
    const data = (await res.json().catch(() => ({}))) as T & { message?: string };
    return { ok: res.ok, status: res.status, data, message: data?.message };
  } catch (e) {
    return { ok: false, status: 0, data: null as T, message: e instanceof Error ? e.message : "网络异常" };
  }
}

/** 校验内置令牌（GET /user） */
export async function verifyToken(): Promise<{ ok: boolean; login?: string; message?: string }> {
  const r = await gh<{ login?: string }>("/user");
  return r.ok ? { ok: true, login: r.data.login } : { ok: false, message: r.message ?? `HTTP ${r.status}` };
}

/** 仓库不存在则创建（公开仓库，供 Releases 下载） */
export async function ensureRepo(): Promise<{ ok: boolean; created: boolean; message?: string }> {
  const check = await gh(`/repos/${GITHUB_REPO}`);
  if (check.ok) return { ok: true, created: false };
  if (check.status !== 404) return { ok: false, created: false, message: check.message ?? `HTTP ${check.status}` };
  const create = await gh("/user/repos", {
    method: "POST",
    body: JSON.stringify({
      name: GITHUB_REPO.split("/")[1],
      description: "Skip-Ad 秒跳引擎 · 开屏广告秒级跳过 + 摇一摇拦截 + 守护模式（Web App / PWA）",
      private: false,
      auto_init: true,
    }),
  });
  if (create.ok) return { ok: true, created: true };
  // 并发创建撞车（422 已存在）视为成功
  if (create.status === 422) return { ok: true, created: false };
  return { ok: false, created: false, message: create.message ?? `HTTP ${create.status}` };
}

/** 创建 Issue（问题反馈通道） */
export async function createIssue(input: {
  title: string;
  body: string;
  labels?: string[];
}): Promise<{ ok: boolean; url?: string; number?: number; message?: string }> {
  const payload = JSON.stringify({
    title: input.title,
    body: input.body,
    ...(input.labels?.length ? { labels: input.labels } : {}),
  });
  let r = await gh<{ html_url?: string; number?: number }>(`/repos/${GITHUB_REPO}/issues`, {
    method: "POST",
    body: payload,
  });
  // 仓库无对应标签权限（422）时降级为无标签创建
  if (r.status === 422 && input.labels?.length) {
    r = await gh<{ html_url?: string; number?: number }>(`/repos/${GITHUB_REPO}/issues`, {
      method: "POST",
      body: JSON.stringify({ title: input.title, body: input.body }),
    });
  }
  if (!r.ok) return { ok: false, message: r.message ?? `HTTP ${r.status}` };
  return { ok: true, url: r.data.html_url, number: r.data.number };
}

/** 创建或更新仓库文件（学习规则夜间同步通道） */
export async function putFile(input: {
  path: string;
  content: string;
  message: string;
  branch?: string;
}): Promise<{ ok: boolean; sha?: string; url?: string; message?: string }> {
  const branch = input.branch ?? "main";
  const encoded = encodeURIComponent(input.path);
  const cur = await gh<{ sha?: string }>(
    `/repos/${GITHUB_REPO}/contents/${encoded}?ref=${branch}`,
  );
  const sha = cur.ok ? cur.data.sha : undefined;

  const put = await gh<{ content?: { sha?: string; html_url?: string } }>(
    `/repos/${GITHUB_REPO}/contents/${encoded}`,
    {
      method: "PUT",
      body: JSON.stringify({
        message: input.message,
        content: Buffer.from(input.content, "utf-8").toString("base64"),
        branch,
        ...(sha ? { sha } : {}),
      }),
    },
  );
  if (!put.ok) return { ok: false, message: put.message ?? `HTTP ${put.status}` };
  return { ok: true, sha: put.data.content?.sha, url: put.data.content?.html_url };
}

/** 创建 Release（版本发布通道） */
export async function createRelease(input: {
  tag: string;
  name: string;
  body: string;
}): Promise<{ ok: boolean; url?: string; id?: number; message?: string }> {
  const r = await gh<{ html_url?: string; id?: number }>(`/repos/${GITHUB_REPO}/releases`, {
    method: "POST",
    body: JSON.stringify({
      tag_name: input.tag,
      name: input.name,
      body: input.body,
      draft: false,
      prerelease: false,
    }),
  });
  if (!r.ok) return { ok: false, message: r.message ?? `HTTP ${r.status}` };
  return { ok: true, url: r.data.html_url, id: r.data.id };
}

/** 上传 Release 附件（APK），返回 browser_download_url（应用内更新直下用） */
export async function uploadReleaseAsset(input: {
  releaseId: number;
  name: string;
  bytes: Buffer;
}): Promise<{ ok: boolean; url?: string; message?: string }> {
  const res = await fetch(`https://uploads.github.com/repos/${GITHUB_REPO}/releases/${input.releaseId}/assets?name=${encodeURIComponent(input.name)}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${getGithubToken()}`,
      Accept: "application/vnd.github+json",
      "Content-Type": "application/octet-stream",
      "Content-Length": String(input.bytes.length),
      "User-Agent": "skip-ad-instant-engine",
    },
    body: new Uint8Array(input.bytes),
  });
  const data = (await res.json().catch(() => ({}))) as { browser_download_url?: string; message?: string };
  if (!res.ok) return { ok: false, message: data?.message ?? `HTTP ${res.status}` };
  return { ok: true, url: data.browser_download_url };
}
