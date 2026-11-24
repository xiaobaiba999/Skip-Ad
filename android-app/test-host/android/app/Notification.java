package android.app;

import android.content.Context;

/** 服务级 JVM 测试桩 */
public class Notification {
    public static final int PRIORITY_MIN = -2;
    public static final int PRIORITY_DEFAULT = 0;

    public static class Builder {
        public Builder(Context ctx) { }
        public Builder(Context ctx, String channelId) { }
        public Builder setContentTitle(CharSequence t) { return this; }
        public Builder setContentText(CharSequence t) { return this; }
        public Builder setSmallIcon(int icon) { return this; }
        public Builder setContentIntent(PendingIntent pi) { return this; }
        public Builder setOngoing(boolean b) { return this; }
        public Builder setShowWhen(boolean b) { return this; }
        public Builder setColor(int c) { return this; }
        public Builder setPriority(int p) { return this; }
        public Builder setCategory(String c) { return this; }
        public Builder setAutoCancel(boolean b) { return this; }
        public Builder addAction(Action a) { return this; }
        public Builder setOnlyAlertOnce(boolean b) { return this; }
        public Notification build() { return new Notification(); }
    }

    public static class Action {
        public static class Builder {
            public Builder(Integer icon, CharSequence title, PendingIntent pi) { }
            public Builder(int icon, CharSequence title, PendingIntent pi) { }
            public Action build() { return new Action(); }
        }
    }
}
