/**
 * 开屏卫士 · 更新检查中转接口（v8.2.0 · 独立只读 · 自包含模块）
 * ═══════════════════════════════════════════════════════════════════
 * GET /api/update/latest —— 解决「检查更新过度依赖 GitHub 匿名 API
 * （60 次/小时/IP，国内共享出口极易耗尽）」的真机根因。
 *
 * 架构（Android v8.2.0 双通道的第 0 通道；Worker 失败自动回退 GitHub 直连）：
 *   设备 → 本接口（边缘缓存 5~10 分钟）→ GitHub Releases
 *          （GitHub App 安装令牌认证——独立于匿名配额池；
 *           凭据不足/失败自动回退匿名——公开数据，绝不要求扩权）
 *
 * 安全红线：
 *   · 只读公开数据（Release 元数据）——零用户数据、零写操作；
 *   · 响应【绝不含】任何 Token/私钥/GitHub App 凭据（secret-scan 双侧闸）；
 *   · GitHub App 私钥等只存 Cloudflare Worker Secret（APK 零凭据红线不破）；
 *   · 只返回 draft=false && prerelease=false 的正式版本（防御性双闸——
 *     /releases/latest 端点本身已保证，此处再显式拒绝，绝不透传草稿）。
 *
 * 接线（现有 skip-ad-log-relay Worker 的 fetch handler 加一个分支）：
 *   import { handleUpdateLatest } from "./update";
 *   if (url.pathname === "/api/update/latest") return handleUpdateLatest(request, env);
 *
 * 缓存策略（「绝不过度请求上游」的结构性保证）：
 *   · L1 Cache API（caches.default，跨隔离实例持久）TTL 默认 600s
 *     （clamp 300~900s，即规格的 5~10 分钟）——同边缘节点最坏 6 次上游/小时；
 *   · 响应体自带 fetchedAt——命中后二次校验新鲜度（Cache API 过期语义
 *     之外再加一道代码层闸，测试可注入时钟确定性验证）；
 *   · 失败负缓存 60s（隔离实例内存）——上游限流/异常时的重复请求不再
 *     逐次打上游（「绝不失败疯狂重试」的 Worker 侧镜像）；
 *   · 认证令牌内存缓存至过期前 60s（安装令牌 1h 有效——不逐请求铸造 JWT）。
 *
 * 错误契约（Android 侧五分类的映射源）：
 *   405 BAD_METHOD            | 500 INTERNAL_ERROR
 *   502 GITHUB_RATE_LIMITED   （上游匿名 403/429）
 *   502 GITHUB_ERROR          （上游其他失败/超时——AbortController 8s）
 */

/** Worker 环境绑定（Secrets 由 wrangler secret / Dashboard 配置；全部可选——
 *  缺省时回退匿名+缓存，功能不中断，绝不要求先配齐）。 */
export interface UpdateEnv {
  /** GitHub App ID（数字字符串，如 "5859168"）。 */
  APP_ID?: string;
  /** GitHub App 私钥（PEM；PKCS#1 "BEGIN RSA PRIVATE KEY" 与 PKCS#8 均支持）。 */
  APP_PRIVATE_KEY?: string;
  /** GitHub App Installation ID（如 "184262217"）。 */
  INSTALLATION_ID?: string;
  /** 运维/测试直通令牌——设置时跳过 JWT 铸造直接作为 Bearer（仅用于验证与
   *  本仓 live 测试；生产留空走 App 认证）。 */
  GH_TOKEN?: string;
  /** 目标仓库（默认 "xiaobaiba999/Skip-Ad"——与 version.ts GITHUB_REPO 单源对齐）。 */
  REPO?: string;
  /** 缓存 TTL 秒（默认 600；clamp 300~900）。 */
  CACHE_TTL_S?: string;
}

/** Cache API 最小接口（注入式——bun 测试用 Map 实现，生产传 caches.default）。 */
export interface CacheLike {
  match(key: Request): Promise<Response | null>;
  put(key: Request, resp: Response): Promise<void>;
}

/** 测试/运维注入点（缺省用生产实现）。 */
export interface UpdateDeps {
  cache?: CacheLike;
  now?: () => number;
  fetchFn?: typeof fetch;
}

const UA = "adshield-update-relay/1.0";
const DEFAULT_REPO = "xiaobaiba999/Skip-Ad";
const UPSTREAM_TIMEOUT_MS = 8000;
const NEG_CACHE_MS = 60_000;

/* ── 隔离实例级内存状态 ── */
let instToken: string | undefined;
let instTokenExpireAt = 0;
let negCache: { at: number; status: number; body: string } | null = null;

/* ══════════════ 对外入口 ══════════════ */

export async function handleUpdateLatest(
  req: Request,
  env: UpdateEnv,
  deps: UpdateDeps = {},
): Promise<Response> {
  const now = deps.now ?? (() => Date.now());
  const fetchFn = deps.fetchFn ?? ((...a: Parameters<typeof fetch>) => fetch(...a));
  const method = req.method.toUpperCase();

  if (method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }
  if (method !== "GET") {
    return errJson("BAD_METHOD", 405);
  }

  const ttlMs = ttlMsOf(env);
  const cache = deps.cache ?? defaultCache();

  try {
    /* ── 负缓存（失败短窗——限流/异常风暴不逐次打上游）── */
    if (negCache && now() - negCache.at < NEG_CACHE_MS) {
      return new Response(negCache.body, {
        status: negCache.status,
        headers: jsonHeaders(),
      });
    }

    /* ── L1 Cache API 命中（body 内 fetchedAt 二次新鲜度校验）── */
    const keyReq = cacheKeyOf(req);
    const hit = cache ? await cache.match(keyReq).catch(() => null) : null;
    if (hit) {
      const body = await hit.text().catch(() => "");
      const j = safeJson(body);
      if (j && j.ok === true && typeof j.fetchedAt === "number"
        && now() - (j.fetchedAt as number) < ttlMs) {
        j.cached = true;
        return new Response(JSON.stringify(j), { status: 200, headers: jsonHeaders() });
      }
    }

    /* ── 上游获取（App 认证 → 匿名回退）── */
    const out = await fetchUpstream(env, { fetchFn, now });
    if (out.kind === "ok") {
      const payload = { ...out.payload, cached: false, fetchedAt: now() };
      const body = JSON.stringify(payload);
      if (cache) {
        await cache.put(keyReq, new Response(body, {
          headers: {
            "Content-Type": "application/json",
            "Cache-Control": `public, max-age=${Math.floor(ttlMs / 1000)}`,
          },
        })).catch(() => { });
      }
      return new Response(body, { status: 200, headers: jsonHeaders() });
    }

    /* ── 失败：负缓存 + 502 ── */
    const body = JSON.stringify({ ok: false, error: out.error });
    negCache = { at: now(), status: out.status, body };
    return new Response(body, { status: out.status, headers: jsonHeaders() });
  } catch (e) {
    console.warn("[update] internal:", e instanceof Error ? e.message : String(e));
    return errJson("INTERNAL_ERROR", 500);
  }
}

/* ══════════════ 上游获取与认证 ══════════════ */

type UpstreamResult =
  | { kind: "ok"; payload: Record<string, unknown> }
  | { kind: "err"; error: string; status: number };

async function fetchUpstream(
  env: UpdateEnv,
  deps: { fetchFn: typeof fetch; now: () => number },
): Promise<UpstreamResult> {
  const repo = (env.REPO && env.REPO.length > 0) ? env.REPO : DEFAULT_REPO;
  let token = env.GH_TOKEN && env.GH_TOKEN.length > 0
    ? env.GH_TOKEN
    : await mintInstallationToken(env, deps);

  let res = await ghFetch(deps.fetchFn, `https://api.github.com/repos/${repo}/releases/latest`, token);
  /* 认证失败/权限不足（401 令牌失效 · 403 令牌权限或限流）→ 有界单次匿名重试：
   * 公开仓库数据匿名可读——【绝不要求扩权】，回退后功能完整（仅上游配额池不同）。
   * 404 不重试：无正式 Release 对任何认证形态同义。 */
  if (token && (res.status === 401 || res.status === 403)) {
    console.warn(`[update] authed upstream ${res.status} — bounded anonymous retry`);
    res = await ghFetch(deps.fetchFn, `https://api.github.com/repos/${repo}/releases/latest`, null);
    token = null;
  }

  if (res.status === 200) {
    const j = safeJson(await res.text());
    if (!j) return { kind: "err", error: "GITHUB_ERROR", status: 502 };
    const payload = normalizeRelease(j, token ? "auth" : "anonymous");
    return { kind: "ok", payload };
  }
  if (res.status === 404) {
    /* 仓库尚无正式 Release 的正常态（与 Android 直连通道语义一致）*/
    return {
      kind: "ok",
      payload: { ok: true, hasRelease: false, source: token ? "auth" : "anonymous" },
    };
  }
  if (res.status === 403 || res.status === 429) {
    return { kind: "err", error: "GITHUB_RATE_LIMITED", status: 502 };
  }
  return { kind: "err", error: "GITHUB_ERROR", status: 502 };
}

async function ghFetch(fetchFn: typeof fetch, url: string, token: string | null): Promise<Response> {
  const headers: Record<string, string> = {
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": UA,
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), UPSTREAM_TIMEOUT_MS);
  try {
    return await fetchFn(url, { headers, signal: ac.signal });
  } finally {
    clearTimeout(timer);
  }
}

/** 铸造 GitHub App 安装令牌（JWT(RS256) → access_tokens 端点）。任一环失败
 *  → undefined（调用方回退匿名）——App 凭据是【增强项】不是依赖项。 */
async function mintInstallationToken(
  env: UpdateEnv,
  deps: { fetchFn: typeof fetch; now: () => number },
): Promise<string | undefined> {
  if (!env.APP_ID || !env.APP_PRIVATE_KEY || !env.INSTALLATION_ID) return undefined;
  if (instToken && deps.now() < instTokenExpireAt) return instToken;
  try {
    const jwt = await signAppJwt(env.APP_ID, env.APP_PRIVATE_KEY, deps.now());
    const ac = new AbortController();
    const timer = setTimeout(() => ac.abort(), UPSTREAM_TIMEOUT_MS);
    let res: Response;
    try {
      res = await deps.fetchFn(
        `https://api.github.com/app/installations/${env.INSTALLATION_ID}/access_tokens`,
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${jwt}`,
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": UA,
          },
          signal: ac.signal,
        },
      );
    } finally {
      clearTimeout(timer);
    }
    if (!res.ok) {
      console.warn(`[update] app token mint failed: ${res.status}`);
      return undefined;
    }
    const j = safeJson(await res.text());
    if (!j || typeof j.token !== "string" || (j.token as string).length === 0) return undefined;
    instToken = j.token as string;
    const exp = typeof j.expires_at === "string" ? Date.parse(j.expires_at as string) : NaN;
    instTokenExpireAt = (isNaN(exp) ? deps.now() + 50 * 60_000 : exp) - 60_000;
    return instToken;
  } catch (e) {
    console.warn("[update] app token mint error:", e instanceof Error ? e.message : String(e));
    return undefined;
  }
}

/* ══════════════ 载荷规范化（纯函数——bun 单测钉死契约）══════════════
 * 字段名与 Android Net.parseRelease 输出【完全对齐】——app.js 下载链
 * （apkUrl/apkMirrors 由客户端合成、apkDigest="sha256:hex"、apkSize）零改动。 */
export function normalizeRelease(j: Record<string, unknown>, source: string): Record<string, unknown> {
  /* 防御性正式版双闸：/releases/latest 端点本身已排除 draft/prerelease——
   * 此处显式再拒（上游行为变更/构造响应绝不透传草稿或预发布）。 */
  if (j.draft === true || j.prerelease === true) {
    return { ok: true, hasRelease: false, source };
  }
  const assets = Array.isArray(j.assets) ? (j.assets as Array<Record<string, unknown>>) : [];
  const apk = assets.find((a) => typeof a?.name === "string" && (a.name as string).endsWith(".apk"));
  const size = apk && typeof apk.size === "number" ? (apk.size as number) : 0;
  return {
    ok: true,
    hasRelease: true,
    source,
    tag: str(j.tag_name),
    name: str(j.name),
    notes: str(j.body),
    publishedAt: str(j.published_at),
    url: str(j.html_url),
    apkUrl: apk ? str(apk.browser_download_url) : "",
    apkFileName: apk ? (str(apk.name) || "adshield.apk") : "adshield.apk",
    apkSize: size,
    apkMB: Math.round((size / 1048576) * 100) / 100,
    apkDigest: apk ? str(apk.digest) : "",
  };
}

/* ══════════════ JWT（RS256 · WebCrypto——零依赖；PKCS#1/PKCS#8 双格式）══════════════ */

export async function signAppJwt(appId: string, pem: string, nowMs: number): Promise<string> {
  const keyData = pemToPkcs8Der(pem);
  const key = await crypto.subtle.importKey(
    "pkcs8", keyData,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false, ["sign"],
  );
  const header = b64url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const now = Math.floor(nowMs / 1000);
  const claims = b64url(new TextEncoder().encode(JSON.stringify({
    iat: now - 60,          // 时钟偏移容忍（GitHub 官方建议）
    exp: now + 540,         // GitHub 上限 600s——留 60s 余量
    iss: appId,
  })));
  const data = `${header}.${claims}`;
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(data));
  return `${data}.${b64url(new Uint8Array(sig))}`;
}

/** PEM（PKCS#1 或 PKCS#8）→ PKCS#8 DER（WebCrypto 仅收 PKCS#8；GitHub App
 *  下载的私钥是 PKCS#1——用标准 ASN.1 包装头转换，零依赖）。 */
export function pemToPkcs8Der(pem: string): ArrayBuffer {
  const isPkcs1 = pem.includes("BEGIN RSA PRIVATE KEY");
  const isPkcs8 = pem.includes("BEGIN PRIVATE KEY");
  if (!isPkcs1 && !isPkcs8) throw new Error("unsupported PEM header");
  const b64 = pem
    .replace(/-----(BEGIN|END)[^-]+-----/g, "")
    .replace(/\s+/g, "");
  const der = base64ToBytes(b64);
  if (isPkcs8) {
    return der.buffer.slice(der.byteOffset, der.byteOffset + der.byteLength) as ArrayBuffer;
  }
  /* PKCS#1 → PKCS#8 包装：
   * SEQ { INTEGER 0, SEQ { OID rsaEncryption, NULL }, OCTET STRING { pkcs1 } } */
  const octetHdr = der.length < 128
    ? [0x04, der.length]
    : [0x04, 0x82, (der.length >> 8) & 0xff, der.length & 0xff];
  const algId = [0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00];
  const content = 3 + algId.length + octetHdr.length + der.length;
  const total = 4 + content; // 外层 SEQ 头固定 30 82 hi lo（content < 65536 恒真）
  const out = new Uint8Array(total);
  out[0] = 0x30; out[1] = 0x82;
  out[2] = (content >> 8) & 0xff; out[3] = content & 0xff;
  let p = 4;
  out[p++] = 0x02; out[p++] = 0x01; out[p++] = 0x00; // INTEGER 0
  out.set(algId, p); p += algId.length;              // AlgorithmIdentifier
  out.set(octetHdr, p); p += octetHdr.length;        // OCTET STRING header
  out.set(der, p);
  return out.buffer.slice(out.byteOffset, out.byteOffset + out.byteLength) as ArrayBuffer;
}

/* ══════════════ 工具 ══════════════ */

function ttlMsOf(env: UpdateEnv): number {
  const raw = parseInt(env.CACHE_TTL_S || "", 10);
  const s = isNaN(raw) ? 600 : Math.min(900, Math.max(300, raw));
  return s * 1000;
}

function cacheKeyOf(req: Request): Request {
  const u = new URL(req.url);
  u.search = ""; // 查询串不参与键（无参数端点——剥离任何附加查询）
  return new Request(u.toString(), { method: "GET" });
}

function defaultCache(): CacheLike | undefined {
  try {
    const c = (globalThis as { caches?: { default: Cache } }).caches;
    return c ? c.default : undefined;
  } catch {
    return undefined;
  }
}

function corsHeaders(): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Max-Age": "86400",
  };
}

function jsonHeaders(): Record<string, string> {
  return {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "public, max-age=300", // 边缘/客户端提示层；权威缓存=Cache API 层
    "X-Content-Type-Options": "nosniff",
    ...corsHeaders(),
  };
}

function errJson(code: string, status: number): Response {
  return new Response(JSON.stringify({ ok: false, error: code }), { status, headers: jsonHeaders() });
}

function safeJson(s: string): Record<string, unknown> | null {
  try {
    const v = JSON.parse(s);
    return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function str(v: unknown): string {
  return typeof v === "string" ? v : "";
}

function b64url(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64ToBytes(b64: string): Uint8Array {
  const norm = b64.replace(/-/g, "+").replace(/_/g, "/");
  const pad = norm.length % 4 === 0 ? "" : "=".repeat(4 - (norm.length % 4));
  const bin = atob(norm + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** 测试辅助：清空模块级内存状态（令牌缓存/负缓存）。 */
export function __resetState(): void {
  instToken = undefined;
  instTokenExpireAt = 0;
  negCache = null;
}
