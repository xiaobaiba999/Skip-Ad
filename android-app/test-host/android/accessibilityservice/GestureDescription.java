package android.accessibilityservice;

import android.graphics.Path;

/** 服务级 JVM 测试桩 */
public class GestureDescription {
    public float tapX, tapY;

    public static class Builder {
        private final GestureDescription d = new GestureDescription();

        public Builder addStroke(Stroke s) {
            d.tapX = s.path.lastX;
            d.tapY = s.path.lastY;
            return this;
        }

        public GestureDescription build() { return d; }
    }

    public static class StrokeDescription extends Stroke {
        public StrokeDescription(android.graphics.Path path, long startTime, long duration) {
            super(path, startTime);
        }
    }

    public static class Stroke {
        final Path path;
        public Stroke(Path path, long startTime) { this.path = path; }
    }
}
