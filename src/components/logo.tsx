/**
 * 开屏卫士品牌标识（v4.1.0 全新形象）
 * 白色小机器人侠 + 绿披风跳跃，翡翠渐变底——与 APK 启动器图标 /
 * 应用内吉祥物 / PWA 图标同源（icon-192.png）。
 */
export function Logo({ size = 36, className }: { size?: number; className?: string }) {
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src="/icon-192.png"
      width={size}
      height={size}
      alt="开屏卫士"
      role="img"
      className={className}
      style={{ borderRadius: "24%", display: "block" }}
      draggable={false}
    />
  );
}
