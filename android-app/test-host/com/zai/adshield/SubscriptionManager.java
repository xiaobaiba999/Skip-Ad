package com.zai.adshield;

import android.content.Context;
import com.zai.adshield.core.AdDecisionCore.NodeView;
import com.zai.adshield.core.RuleEngine;
import com.zai.adshield.core.RuleEngine.Hit;
import com.zai.adshield.core.Subscription;

import java.util.ArrayList;

/**
 * 服务级 JVM 测试桩：订阅管理器（零订阅——订阅命中路径已由
 * SubscriptionBuiltinTest/SubMirrorTest/SubPruneTest 核心套件覆盖；
 * 服务级回归聚焦窗口收集/执行/日志链路）。
 */
public final class SubscriptionManager {
    public static Subscription builtinSub = null;
    public static Subscription remoteSub = null;

    private SubscriptionManager() { }

    public static void ensureLoaded(Context ctx) { }

    public static void maybeAutoImport(Context ctx) { }

    public static boolean coversPkg(String pkg) { return false; }

    public static ArrayList<Hit> matchSources(String pkg, NodeView root, com.zai.adshield.core.AdDecisionCore.Ctx ctx) {
        return new ArrayList<Hit>();
    }

    public static String coverageLine(String pkg) { return "订阅[桩]"; }

    public static String stateJson(Context ctx) { return "{}"; }
}
