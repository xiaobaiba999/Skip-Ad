/**
 * 开屏卫士品牌标识（v4.0.0 全新设计 · 跳跳侠）
 * 戴风镜的冲刺小人 + 飞披风 + 速度线：广告露头就被跳走。
 * 与 APK 启动器图标 / 应用内吉祥物同源设计（96 视口几何）。
 */
export function Logo({ size = 36, className }: { size?: number; className?: string }) {
  return (
    <svg
      viewBox="0 0 96 96"
      width={size}
      height={size}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-label="开屏卫士"
      role="img"
    >
      {/* 速度线 */}
      <g stroke="#6EE7B7" strokeWidth="5" strokeLinecap="round">
        <line x1="6" y1="32" x2="22" y2="32" />
        <line x1="3" y1="46" x2="21" y2="46" />
        <line x1="8" y1="60" x2="18" y2="60" />
      </g>
      {/* 披风 */}
      <path d="M47 40 Q26 56 26 78 Q35 76 44 70 L52 66 L48 42 Z" fill="#059669" />
      {/* 腿 */}
      <path d="M58 72 L48 83" stroke="#10B981" strokeWidth="9" strokeLinecap="round" />
      <path d="M66 70 L77 79" stroke="#10B981" strokeWidth="9" strokeLinecap="round" />
      {/* 身体（前倾胶囊） */}
      <rect x="42.5" y="32.5" width="23" height="57" rx="11.5" fill="#10B981" transform="rotate(-8 56 60)" />
      {/* 前臂 */}
      <path d="M65 52 L83 50" stroke="#10B981" strokeWidth="8" strokeLinecap="round" />
      {/* 脑袋 + 风镜 */}
      <circle cx="60" cy="23" r="15.5" fill="#10B981" />
      <rect x="48.5" y="17.2" width="23" height="8.5" rx="4.2" fill="#052E1C" />
      <circle cx="63.5" cy="21.5" r="2" fill="#A7F3D0" />
    </svg>
  );
}
