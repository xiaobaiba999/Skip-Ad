/**
 * Skip-Ad 品牌标识（v3.5.0 全新设计 · 绿色）
 * 翡翠渐变盾牌 + 深绿闪电：守护（盾）+ 秒跳（闪电）。
 * 与 APK 启动器图标 / 通知图标 / 应用内 Logo 同源设计。
 */
export function Logo({ size = 36, className }: { size?: number; className?: string }) {
  return (
    <svg
      viewBox="0 0 48 48"
      width={size}
      height={size}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-label="Skip-Ad"
      role="img"
    >
      <defs>
        <linearGradient id="logo-shield-grad" x1="7" y1="5" x2="41" y2="43" gradientUnits="userSpaceOnUse">
          <stop stopColor="#34D399" />
          <stop offset="1" stopColor="#059669" />
        </linearGradient>
      </defs>
      <path d="M24 3 L41 9 C41 26 35 39 24 45 C13 39 7 26 7 9 Z" fill="url(#logo-shield-grad)" />
      <path d="M27.5 10.5 L17.5 26 h5.5 L20 37.5 L30.5 20.5 h-5.5 Z" fill="#047857" />
      <path d="M24 3 L41 9 C41 12.5 40.6 15.7 40 18.7 C33 15.2 15 15.2 8 18.7 C7.4 15.7 7 12.5 7 9 Z" fill="#fff" fillOpacity="0.14" />
    </svg>
  );
}
