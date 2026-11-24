package com.zai.adshield;

/** 服务级 JVM 测试桩：网络层（零外联——反馈/上传路径静默成功） */
public final class Net {
    /** 反馈提交桩：恒成功（网络路径不在服务级回归范围） */
    public static String postIssue(String title, String body) {
        return "ok";
    }
}
