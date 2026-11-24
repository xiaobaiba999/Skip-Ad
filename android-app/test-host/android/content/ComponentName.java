package android.content;

/** 服务级 JVM 测试桩 */
public class ComponentName {
    private final String pkg, cls;
    public ComponentName(String pkg, String cls) { this.pkg = pkg; this.cls = cls; }
    public ComponentName(Context ctx, Class<?> clz) { this.pkg = ctx.getPackageName(); this.cls = clz.getName(); }
    public String getPackageName() { return pkg; }
    public String getClassName() { return cls; }
    public String flattenToString() { return pkg + "/" + cls; }
    public String flattenToShortString() { return pkg + "/" + cls.substring(cls.lastIndexOf('.') + 1); }
}
