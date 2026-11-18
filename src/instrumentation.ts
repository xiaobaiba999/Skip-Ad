/**
 * ─────────────────────────────────────────────────────────────
 *  服务端 instrumentation（SERVER-ONLY · Next.js 16 原生钩子）
 * ─────────────────────────────────────────────────────────────
 *  补齐 UI 承诺的「每晚 02:00（Asia/Shanghai）自动同步学习数据到 GitHub」：
 *  · register() 在服务进程启动时执行一次（dev 热重载 / 多 worker 用全局旗标防重）；
 *  · 启动 30s 后先补查一次（服务重启也能追上当日错过的夜间同步）；
 *  · 之后每 10 分钟轻量检查 shouldRunNightly()——东八区当日 02:00 后
 *    尚无成功 nightly 记录才真正出手（maybeRunNightlySync 内部幂等）；
 *  · 定时器 unref()：绝不阻塞进程正常退出；任何异常只记日志、绝不让进程崩溃。
 */
export async function register(): Promise<void> {
  // 只在 Node.js 运行时注册（Edge/构建阶段跳过）
  if (process.env.NEXT_RUNTIME !== "nodejs") return;
  // 生产构建阶段（next build）不启动调度器
  if (process.env.NEXT_PHASE === "phase-production-build") return;

  const g = globalThis as typeof globalThis & { __skipAdNightlyScheduler?: boolean };
  if (g.__skipAdNightlyScheduler) return; // dev 热重载防重复注册
  g.__skipAdNightlyScheduler = true;

  const CHECK_INTERVAL_MS = 10 * 60 * 1000; // 每 10 分钟检查一次
  const BOOT_DELAY_MS = 30 * 1000; // 启动 30s 后先补查一次

  let running = false;
  async function tick(trigger: string): Promise<void> {
    if (running) return;
    running = true;
    try {
      // 动态导入：把 Prisma / GitHub 依赖推迟到真正需要时（register 阶段零负担）
      const { maybeRunNightlySync } = await import("./lib/sync");
      const fired = await maybeRunNightlySync();
      if (fired) {
        console.log(`[instrumentation] 夜间同步已由 ${trigger} 触发完成`);
      }
    } catch (e) {
      // 同步失败：shouldRunNightly 明日仍会判定未同步 → 自动重试，绝不抛出
      console.error(`[instrumentation] 夜间同步检查异常（${trigger}）`, e);
    } finally {
      running = false;
    }
  }

  const boot = setTimeout(() => void tick("启动补查"), BOOT_DELAY_MS);
  boot.unref?.();
  const loop = setInterval(() => void tick("定时巡检"), CHECK_INTERVAL_MS);
  loop.unref?.();

  console.log(
    `[instrumentation] 夜间同步调度器已就绪：每日 02:00（Asia/Shanghai）· 巡检间隔 ${CHECK_INTERVAL_MS / 60000} 分钟`,
  );
}
