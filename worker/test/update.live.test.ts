/**
 * 更新中转接口 live 测试（REAL_API · 模块级——真实 GitHub 上游，非部署实例）
 * ═══════════════════════════════════════════════════════════════════
 * 运行门槛：RUN_LIVE=1 且 GH_TOKEN 在场（认证通道——独立于匿名 60 次/小时/IP
 * 配额池；匿名通道另做一次受控真实验证）。
 *   RUN_LIVE=1 GH_TOKEN=… bun test test/update.live.test.ts
 *
 * 证明目标（对应验收 #2/#3/#4 的模块级部分）：
 *   · 认证通道能取到真实最新正式 Release（当前 v8.1.0——含官方 SHA-256）；
 *   · 缓存生效：同缓存两次请求只打一次上游，且第二次 cached:true；
 *   · 匿名通道真实可用（Worker 未配 App 凭据时的回退路径——仅 1 次受控调用）。
 */
import { describe, test, expect, beforeEach } from "bun:test";
import { handleUpdateLatest, __resetState } from "../src/update";
import type { CacheLike } from "../src/update";

const RUN_LIVE = process.env.RUN_LIVE === "1";
const GH_TOKEN = process.env.GH_TOKEN || "";

function fakeCache(): CacheLike {
  const m = new Map<string, { body: string; headers: Record<string, string> }>();
  return {
    match: async (k: Request) => {
      const e = m.get(k.url);
      return e ? new Response(e.body, { headers: e.headers }) : null;
    },
    put: async (k: Request, r: Response) => {
      m.set(k.url, { body: await r.clone().text(), headers: Object.fromEntries(r.headers.entries()) });
    },
  };
}

const REQ = () => new Request("https://relay.test/api/update/latest", { method: "GET" });

beforeEach(() => __resetState());

describe.skipIf(!RUN_LIVE)("live · 认证通道（真实 GitHub API）", () => {
  test("取到最新正式 Release（tag/apk/digest 官方真实值）", async () => {
    let upstreamCalls = 0;
    const fetchFn = (async (...a: Parameters<typeof fetch>) => {
      upstreamCalls++;
      return fetch(...a);
    }) as typeof fetch;
    const r = await handleUpdateLatest(REQ(), { GH_TOKEN }, { cache: fakeCache(), fetchFn });
    expect(r.status).toBe(200);
    const j = await r.json();
    expect(j.ok).toBe(true);
    expect(j.hasRelease).toBe(true);
    expect(j.source).toBe("auth");
    expect(String(j.tag)).toMatch(/^v\d+\.\d+\.\d+$/);
    expect(String(j.tag) >= "v8.1.0").toBe(true);
    expect(String(j.apkFileName)).toMatch(/^adshield-v[\d.]+\.apk$/);
    expect(String(j.apkDigest)).toMatch(/^sha256:[0-9a-f]{64}$/);
    expect(Number(j.apkSize)).toBeGreaterThan(800_000);
    expect(upstreamCalls).toBe(1);
    /* v8.1.0 仍是 latest 时钉死官方指纹（发布新版后自然放宽为格式断言） */
    if (j.tag === "v8.1.0") {
      expect(j.apkDigest).toBe("sha256:50a7b7029c715eb810e1bc3de2c8fac63d74d10995c88562c4c6f9e2487f0421");
      expect(j.apkSize).toBe(882230);
    }
  });

  test("缓存生效：两次请求一次上游，第二次 cached:true 同 fetchedAt", async () => {
    const cache = fakeCache();
    let upstreamCalls = 0;
    const fetchFn = (async (...a: Parameters<typeof fetch>) => {
      upstreamCalls++;
      return fetch(...a);
    }) as typeof fetch;
    const r1 = await handleUpdateLatest(REQ(), { GH_TOKEN }, { cache, fetchFn });
    const j1 = await r1.json();
    const r2 = await handleUpdateLatest(REQ(), { GH_TOKEN }, { cache, fetchFn });
    const j2 = await r2.json();
    expect(upstreamCalls).toBe(1);
    expect(j2.cached).toBe(true);
    expect(j2.fetchedAt).toBe(j1.fetchedAt);
    expect(j2.tag).toBe(j1.tag);
    expect(j2.apkDigest).toBe(j1.apkDigest);
  });
});

describe.skipIf(!RUN_LIVE)("live · 匿名通道（未配 App 凭据的回退——1 次受控调用）", () => {
  test("匿名真实上游可用（Worker 零凭据部署形态的功能兜底）", async () => {
    const r = await handleUpdateLatest(REQ(), {}, { cache: fakeCache() });
    expect(r.status).toBe(200);
    const j = await r.json();
    expect(j.ok).toBe(true);
    expect(j.source).toBe("anonymous");
    if (j.hasRelease) {
      expect(String(j.apkDigest)).toMatch(/^sha256:[0-9a-f]{64}$/);
    }
  });
});
