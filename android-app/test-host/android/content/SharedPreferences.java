package android.content;

/** 服务级 JVM 测试桩：内存版 SharedPreferences */
public class SharedPreferences {
    public interface Editor {
        Editor putString(String k, String v);
        Editor putBoolean(String k, boolean v);
        Editor putInt(String k, int v);
        Editor putLong(String k, long v);
        Editor remove(String k);
        Editor clear();
        boolean commit();
        void apply();
    }

    private final java.util.Map<String, Object> data =
            new java.util.concurrent.ConcurrentHashMap<String, Object>();

    public String getString(String k, String def) {
        Object v = data.get(k);
        return v instanceof String ? (String) v : def;
    }

    public boolean getBoolean(String k, boolean def) {
        Object v = data.get(k);
        return v instanceof Boolean ? ((Boolean) v).booleanValue() : def;
    }

    public int getInt(String k, int def) {
        Object v = data.get(k);
        return v instanceof Integer ? ((Integer) v).intValue() : def;
    }

    public long getLong(String k, long def) {
        Object v = data.get(k);
        return v instanceof Long ? ((Long) v).longValue() : def;
    }

    public boolean contains(String k) { return data.containsKey(k); }

    public Editor edit() {
        return new Editor() {
            public Editor putString(String k, String v) { data.put(k, v); return this; }
            public Editor putBoolean(String k, boolean v) { data.put(k, Boolean.valueOf(v)); return this; }
            public Editor putInt(String k, int v) { data.put(k, Integer.valueOf(v)); return this; }
            public Editor putLong(String k, long v) { data.put(k, Long.valueOf(v)); return this; }
            public Editor remove(String k) { data.remove(k); return this; }
            public Editor clear() { data.clear(); return this; }
            public boolean commit() { return true; }
            public void apply() { }
        };
    }
}
