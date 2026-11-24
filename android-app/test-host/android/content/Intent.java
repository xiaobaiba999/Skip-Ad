package android.content;

/** 服务级 JVM 测试桩 */
public class Intent {
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;
    public static final int FLAG_UPDATE_CURRENT = 0x8000000;
    public static final int FLAG_IMMUTABLE = 0x4000000;

    public static final String ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED";
    public static final String ACTION_SCREEN_ON = "android.intent.action.SCREEN_ON";
    public static final String ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF";
    public static final String ACTION_USER_PRESENT = "android.intent.action.USER_PRESENT";

    public String action;
    public ComponentName component;
    public String pkg;

    public Intent() { }
    public Intent(Context ctx, Class<?> cls) {
        this.component = new ComponentName(ctx.getPackageName(), cls.getName());
    }
    public Intent(String action) { this.action = action; }

    public Intent setAction(String a) { this.action = a; return this; }
    public Intent setPackage(String p) { this.pkg = p; return this; }
    public Intent setComponent(ComponentName c) { this.component = c; return this; }
    public Intent addFlags(int f) { return this; }
    public Intent setFlags(int f) { return this; }
    public String getAction() { return action; }
    public int getIntExtra(String name, int def) { return def; }
    public ComponentName getComponent() { return component; }
}
