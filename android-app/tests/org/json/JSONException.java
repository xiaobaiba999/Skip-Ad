package org.json;

/**
 * JSONException（JVM 测试 shim——与 android.jar org.json 同包同签名）。
 * 生产 APK 绝不包含本文件（build.sh 只编译 src/；tests/ 仅进 run-tests.sh）。
 */
public class JSONException extends RuntimeException {
    public JSONException(String s) {
        super(s);
    }
}
