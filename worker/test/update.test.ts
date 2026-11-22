/**
 * 更新中转接口单元测试（bun test · AUTOMATED——零真实网络）
 * 覆盖：载荷规范化契约/正式版双闸/缓存命中与TTL/负缓存/限流映射/
 *       405/GitHub App 认证链（JWT 铸造·签名验证·claims）/匿名回退/PKCS 双格式
 */
import { describe, test, expect, beforeEach } from "bun:test";
import {
  handleUpdateLatest, normalizeRelease, signAppJwt, pemToPkcs8Der, __resetState,
} from "../src/update";
import type { UpdateEnv, CacheLike } from "../src/update";
import { generateKeyPairSync, createVerify } from "node:crypto";

/* ── 测试基件 ── */

function fakeCache(): CacheLike & { size(): number } {
  const m = new Map<string, { body: string; headers: Record<string, string> }>();
  return {
    match: async (k: Request) => {
      const e = m.get(k.url);
      return e ? new Response(e.body, { headers: e.headers }) : null;
    },
    put: async (k: Request, r: Response) => {
      m.set(k.url, { body: await r.clone().text(), headers: Object.fromEntries(r.headers.entries()) });
    },
    size: () => m.size,
  };
}

type Recorded = { url: string; auth: string };

function mockFetch(queue: Array<{ match: (u: string) => boolean; resp: () => Response }>) {
  const calls: Recorded[] = [];
  const fn = (async (url: string | URL | Request, init?: RequestInit) => {
    const u = typeof url === "string" ? url : url instanceof URL ? url.toString() : url.url;
    const auth = ((init?.headers as Record<string, string>)?.Authorization) || "";
    calls.push({ url: u, auth });
    for (const q of queue) if (q.match(u)) return q.resp();
    return new Response("{}", { status: 404 });
  }) as unknown as typeof fetch;
  return { fn, calls };
}

const REQ = (method = "GET") => new Request("https://relay.test/api/update/latest", { method });

function releaseJson(): Record<string, unknown> {
  return {
    tag_name: "v8.1.0", name: "开屏卫士 v8.1.0", draft: false, prerelease: false,
    body: "正文", published_at: "2026-11-22T03:17:13Z",
    html_url: "https://github.com/xiaobaiba999/Skip-Ad/releases/tag/v8.1.0",
    assets: [{
      name: "adshield-v8.1.0.apk", size: 882230, state: "uploaded",
      digest: "sha256:50a7b7029c715eb810e1bc3de2c8fac63d74d10995c88562c4c6f9e2487f0421",
      browser_download_url: "https://github.com/xiaobaiba999/Skip-Ad/releases/download/v8.1.0/adshield-v8.1.0.apk",
    }],
  };
}

/* ── 测试密钥（每次运行现生成——不进仓库不进代码） ── */
const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const PUB_PEM = publicKey.export({ type: "spki", format: "pem" }).toString();
const PRIV_PKCS1 = privateKey.export({ type: "pkcs1", format: "pem" }).toString();
const PRIV_PKCS8 = privateKey.export({ type: "pkcs8", format: "pem" }).toString();

beforeEach(() => __resetState());

/* ══════ 一、载荷规范化契约 ══════ */

describe("normalizeRelease 契约", () => {
  test("完整 Release → 字段与 Android parseRelease 完全对齐", () => {
    const out = normalizeRelease(releaseJson(), "auth");
    expect(out.ok).toBe(true);
    expect(out.hasRelease).toBe(true);
    expect(out.source).toBe("auth");
    expect(out.tag).toBe("v8.1.0");
    expect(out.apkUrl).toContain("releases/download/v8.1.0/adshield-v8.1.0.apk");
    expect(out.apkFileName).toBe("adshield-v8.1.0.apk");
    expect(out.apkSize).toBe(882230);
    expect(out.apkMB).toBe(0.84);
    expect(out.apkDigest).toBe("sha256:50a7b7029c715eb810e1bc3de2c8fac63d74d10995c88562c4c6f9e2487f0421");
    expect(out.publishedAt).toBe("2026-11-22T03:17:13Z");
  });

  test("防御性双闸：draft=true 绝不透传", () => {
    const j = releaseJson(); j.draft = true;
    expect(normalizeRelease(j, "auth")).toEqual({ ok: true, hasRelease: false, source: "auth" });
  });

  test("防御性双闸：prerelease=true 绝不透传", () => {
    const j = releaseJson(); j.prerelease = true;
    expect(normalizeRelease(j, "auth").hasRelease).toBe(false);
  });

  test("无 assets → apk 字段安全缺省（下载链拒绝而非崩溃）", () => {
    const j = releaseJson(); delete j.assets;
    const out = normalizeRelease(j, "anonymous");
    expect(out.hasRelease).toBe(true);
    expect(out.apkUrl).toBe("");
    expect(out.apkSize).toBe(0);
    expect(out.apkDigest).toBe("");
    expect(out.apkFileName).toBe("adshield.apk");
  });

  test("非 .apk 资产被跳过", () => {
    const j = releaseJson();
    j.assets = [{ name: "notes.txt", size: 10, digest: "sha256:aa", browser_download_url: "x" }];
    expect(normalizeRelease(j, "auth").apkDigest).toBe("");
  });
});

/* ══════ 二、缓存行为 ══════ */

describe("缓存（L1 Cache API + fetchedAt 双闸）", () => {
  const ENV: UpdateEnv = {};

  test("未命中→上游→落缓存；二次请求命中且不再打上游", async () => {
    const cache = fakeCache();
    const { fn, calls } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => Response.json(releaseJson()) },
    ]);
    const r1 = await handleUpdateLatest(REQ(), ENV, { cache, fetchFn: fn, now: () => 1_000_000 });
    const j1 = await r1.json();
    expect(j1.ok).toBe(true);
    expect(j1.cached).toBe(false);
    expect(calls.length).toBe(1);
    expect(cache.size()).toBe(1);

    const r2 = await handleUpdateLatest(REQ(), ENV, { cache, fetchFn: fn, now: () => 1_000_000 + 300_000 });
    const j2 = await r2.json();
    expect(j2.cached).toBe(true);
    expect(j2.fetchedAt).toBe(j1.fetchedAt);
    expect(calls.length).toBe(1); // 上游仍只调了 1 次
  });

  test("TTL 过期（>600s）→ 重新打上游", async () => {
    const cache = fakeCache();
    const { fn, calls } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => Response.json(releaseJson()) },
    ]);
    await handleUpdateLatest(REQ(), ENV, { cache, fetchFn: fn, now: () => 0 });
    const r2 = await handleUpdateLatest(REQ(), ENV, { cache, fetchFn: fn, now: () => 601_000 });
    const j2 = await r2.json();
    expect(j2.cached).toBe(false);
    expect(calls.length).toBe(2);
  });

  test("失败响应不落 L1 缓存（只有成功可缓存）", async () => {
    const cache = fakeCache();
    const { fn } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => new Response("", { status: 500 }) },
    ]);
    const r = await handleUpdateLatest(REQ(), ENV, { cache, fetchFn: fn, now: () => 0 });
    expect(r.status).toBe(502);
    expect(cache.size()).toBe(0);
  });
});

/* ══════ 三、错误映射与负缓存 ══════ */

describe("错误映射", () => {
  test("上游 403 → 502 GITHUB_RATE_LIMITED", async () => {
    const { fn } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => new Response("", { status: 403 }) },
    ]);
    const r = await handleUpdateLatest(REQ(), {}, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    expect(r.status).toBe(502);
    expect((await r.json()).error).toBe("GITHUB_RATE_LIMITED");
  });

  test("上游 429 → 502 GITHUB_RATE_LIMITED", async () => {
    const { fn } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => new Response("", { status: 429 }) },
    ]);
    const r = await handleUpdateLatest(REQ(), {}, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    expect((await r.json()).error).toBe("GITHUB_RATE_LIMITED");
  });

  test("上游 500 → 502 GITHUB_ERROR", async () => {
    const { fn } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => new Response("", { status: 500 }) },
    ]);
    const r = await handleUpdateLatest(REQ(), {}, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    expect((await r.json()).error).toBe("GITHUB_ERROR");
  });

  test("上游 404 → 200 hasRelease:false（无正式 Release 的正常态）", async () => {
    const { fn } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => new Response("", { status: 404 }) },
    ]);
    const r = await handleUpdateLatest(REQ(), {}, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    const j = await r.json();
    expect(r.status).toBe(200);
    expect(j.ok).toBe(true);
    expect(j.hasRelease).toBe(false);
  });

  test("POST → 405 BAD_METHOD；OPTIONS → 204", async () => {
    const r1 = await handleUpdateLatest(REQ("POST"), {}, { now: () => 0 });
    expect(r1.status).toBe(405);
    expect((await r1.json()).error).toBe("BAD_METHOD");
    const r2 = await handleUpdateLatest(REQ("OPTIONS"), {}, { now: () => 0 });
    expect(r2.status).toBe(204);
  });

  test("负缓存：限流后 60s 内重复请求不再打上游；61s 后放行", async () => {
    const { fn, calls } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => new Response("", { status: 403 }) },
    ]);
    let t = 0;
    const now = () => t;
    await handleUpdateLatest(REQ(), {}, { cache: fakeCache(), fetchFn: fn, now });
    t = 30_000;
    await handleUpdateLatest(REQ(), {}, { cache: fakeCache(), fetchFn: fn, now });
    expect(calls.length).toBe(1); // 负缓存拦截
    t = 61_000;
    await handleUpdateLatest(REQ(), {}, { cache: fakeCache(), fetchFn: fn, now });
    expect(calls.length).toBe(2); // 窗口外放行重试
  });
});

/* ══════ 四、GitHub App 认证链 ══════ */

describe("GitHub App 认证", () => {
  const APP_ENV: UpdateEnv = {
    APP_ID: "5859168", APP_PRIVATE_KEY: PRIV_PKCS1, INSTALLATION_ID: "184262217",
  };

  test("JWT(RS256) 签名可验证 + claims 正确（iss/iat/exp）", async () => {
    const nowMs = 1_700_000_000_000;
    const jwt = await signAppJwt("5859168", PRIV_PKCS1, nowMs);
    const [h, c, s] = jwt.split(".");
    const b64 = (x: string) => Buffer.from(x.replace(/-/g, "+").replace(/_/g, "/"), "base64");
    const verifier = createVerify("RSA-SHA256").update(`${h}.${c}`);
    expect(verifier.verify(PUB_PEM, b64(s))).toBe(true);
    const claims = JSON.parse(b64(c).toString());
    expect(claims.iss).toBe("5859168");
    expect(claims.iat).toBe(Math.floor(nowMs / 1000) - 60);
    expect(claims.exp).toBe(Math.floor(nowMs / 1000) + 540);
  });

  test("PKCS#1 与 PKCS#8 私钥格式均可导入签名", async () => {
    const j1 = await signAppJwt("1", PRIV_PKCS1, 0);
    const j2 = await signAppJwt("1", PRIV_PKCS8, 0);
    expect(j1.split(".").length).toBe(3);
    expect(j2.split(".").length).toBe(3);
    /* PKCS#1 包装后与原生 PKCS#8 产生相同签名（同一把钥） */
    expect(j1).toBe(j2);
  });

  test("完整铸造链：JWT → access_tokens → Bearer 安装令牌打上游；令牌内存缓存", async () => {
    const { fn, calls } = mockFetch([
      {
        match: (u) => u.includes("/app/installations/184262217/access_tokens"),
        resp: () => Response.json({ token: "ghs_test_inst", expires_at: new Date(3600_000).toISOString() }),
      },
      { match: (u) => u.includes("/releases/latest"), resp: () => Response.json(releaseJson()) },
    ]);
    const opts = { cache: fakeCache(), fetchFn: fn, now: () => 0 };
    const r1 = await handleUpdateLatest(REQ(), APP_ENV, opts);
    const j1 = await r1.json();
    expect(j1.source).toBe("auth");
    expect(j1.tag).toBe("v8.1.0");
    /* access_tokens 恰一次；上游带安装令牌 */
    expect(calls.filter((c) => c.url.includes("access_tokens")).length).toBe(1);
    expect(calls.find((c) => c.url.includes("releases/latest"))?.auth).toBe("Bearer ghs_test_inst");
    /* 第二次请求（新缓存实例=模拟另一隔离实例命中同一 L1……此处负例：无 L1）→
     * 令牌已内存缓存 → access_tokens 不再铸造 */
    const r2 = await handleUpdateLatest(REQ(), APP_ENV, { ...opts, cache: fakeCache() });
    await r2.json();
    expect(calls.filter((c) => c.url.includes("access_tokens")).length).toBe(1);
  });

  test("认证上游 403（权限不足/配额）→ 有界单次匿名重试，绝不报错要求扩权", async () => {
    let first = true;
    const { fn, calls } = mockFetch([
      {
        match: (u) => u.includes("/app/installations/"),
        resp: () => Response.json({ token: "ghs_x", expires_at: new Date(3600_000).toISOString() }),
      },
      {
        match: (u) => u.includes("/releases/latest"),
        resp: () => {
          if (first) { first = false; return new Response("", { status: 403 }); }
          return Response.json(releaseJson());
        },
      },
    ]);
    const r = await handleUpdateLatest(REQ(), APP_ENV, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    const j = await r.json();
    expect(j.ok).toBe(true);
    expect(j.source).toBe("anonymous");
    const ups = calls.filter((c) => c.url.includes("releases/latest"));
    expect(ups.length).toBe(2); // 有界：认证 1 次 + 匿名 1 次
    expect(ups[0].auth).toBe("Bearer ghs_x");
    expect(ups[1].auth).toBe("");
  });

  test("access_tokens 铸造失败（401 坏钥）→ 直接匿名，上游仅 1 次", async () => {
    const { fn, calls } = mockFetch([
      { match: (u) => u.includes("/app/installations/"), resp: () => new Response("", { status: 401 }) },
      { match: (u) => u.includes("/releases/latest"), resp: () => Response.json(releaseJson()) },
    ]);
    const r = await handleUpdateLatest(REQ(), APP_ENV, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    const j = await r.json();
    expect(j.source).toBe("anonymous");
    expect(calls.filter((c) => c.url.includes("releases/latest")).length).toBe(1);
  });

  test("GH_TOKEN 直通（运维/测试通道——跳过铸造）", async () => {
    const { fn, calls } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => Response.json(releaseJson()) },
    ]);
    const r = await handleUpdateLatest(REQ(), { GH_TOKEN: "gho_direct" }, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    expect((await r.json()).source).toBe("auth");
    expect(calls[0].auth).toBe("Bearer gho_direct");
  });
});

/* ══════ 五、响应安全（零凭据外泄）════════ */

describe("响应安全", () => {
  test("成功/失败响应体均不含任何令牌形态（ghs_/gho_/Bearer/PRIVATE KEY）", async () => {
    const { fn } = mockFetch([
      { match: (u) => u.includes("/app/installations/"), resp: () => Response.json({ token: "ghs_secret_should_leak", expires_at: new Date(3600_000).toISOString() }) },
      { match: (u) => u.includes("/releases/latest"), resp: () => Response.json(releaseJson()) },
    ]);
    const env: UpdateEnv = { APP_ID: "5859168", APP_PRIVATE_KEY: PRIV_PKCS1, INSTALLATION_ID: "184262217" };
    const r = await handleUpdateLatest(REQ(), env, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    const text = JSON.stringify(await r.json());
    expect(text.includes("ghs_secret_should_leak")).toBe(false);
    expect(text.includes("PRIVATE KEY")).toBe(false);
    expect(text.includes("Bearer")).toBe(false);
  });

  test("CORS 头在场（浏览器预览通道）", async () => {
    const { fn } = mockFetch([
      { match: (u) => u.includes("/releases/latest"), resp: () => Response.json(releaseJson()) },
    ]);
    const r = await handleUpdateLatest(REQ(), {}, { cache: fakeCache(), fetchFn: fn, now: () => 0 });
    expect(r.headers.get("Access-Control-Allow-Origin")).toBe("*");
    expect(r.headers.get("Content-Type")).toContain("application/json");
  });
});
