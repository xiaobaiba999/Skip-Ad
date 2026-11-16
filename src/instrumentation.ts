/**
 * Next.js 服务端启动钩子：播种预置规则 + 启动夜间同步调度器。
 * （Next 16 instrumentation：server 进程启动时调用一次）
 */
export async function register() {
  if (process.env.NEXT_RUNTIME !== "nodejs") return;

  const g = globalThis as unknown as { __skipAdBooted?: boolean };
  if (g.__skipAdBooted) return; // 防 HMR/多 worker 重复启动
  g.__skipAdBooted = true;

  try {
    const { ensureRulesSeed } = await import("./lib/preset-rules");
    await ensureRulesSeed();
    console.log("[boot] 预置规则库就绪");
  } catch (e) {
    console.error("[boot] 规则播种失败", e);
  }

  // 每 5 分钟检查一次：东八区每日 02:00 后且当日未同步 → 自动提交学习数据
  const { maybeRunNightlySync } = await import("./lib/sync");
  const timer = setInterval(() => {
    void maybeRunNightlySync();
  }, 5 * 60 * 1000);
  if (typeof timer.unref === "function") timer.unref();
  console.log("[boot] 夜间同步调度器已启动（每日 02:00 · Asia/Shanghai）");
}
