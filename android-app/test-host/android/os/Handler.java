package android.os;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 服务级 JVM 测试桩：队列式 Handler——post/postDelayed 全部入队，
 * 由测试泵 drain() 依序执行（忽略延时——扫描链全档立即跑，逻辑全覆盖）。
 * 深度防御：单次 drain 上限 512 条 + 派发中入队继续处理（真实主循环同构）。
 */
public class Handler {
    private final Queue<Runnable> queue = new ArrayDeque<Runnable>();

    public Handler() { }
    public Handler(Looper looper) { }
    public Handler(Looper looper, android.os.Callback cb) { }

    public boolean post(Runnable r) { if (r != null) queue.add(r); return true; }
    public boolean postDelayed(Runnable r, long delayMillis) { return post(r); }
    public boolean postAtTime(Runnable r, long uptimeMillis) { return post(r); }
    public void removeCallbacks(Runnable r) { queue.remove(r); }
    public void removeCallbacksAndMessages(Object token) { queue.clear(); }

    /** 测试泵：执行全部排队的 Runnable（含派发中新入队的），上限 512 条 */
    public void drain() {
        int n = 0;
        Runnable r;
        while ((r = queue.poll()) != null) {
            r.run();
            if (++n >= 512) break;
        }
    }

    public int pendingCount() { return queue.size(); }
}
