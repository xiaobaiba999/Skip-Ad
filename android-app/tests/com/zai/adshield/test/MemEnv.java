package com.zai.adshield.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.zai.adshield.core.LogStore;

/** JVM 测试注入环境（内存 prefs + 临时目录——模拟 Android Context 语义）。
 *  LogStoreTest 与 LogUploaderTest 共用。 */
final class MemEnv implements LogStore.StoreEnv {
    final Map<String, String> prefs = new HashMap<String, String>();
    final File dir;

    MemEnv() {
        try {
            dir = File.createTempFile("logstore", ".d");
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public String prefGet(String key) {
        return prefs.get(key);
    }

    public void prefPut(String key, String value) {
        prefs.put(key, value);
    }

    public File filesDir() {
        return dir;
    }
}
