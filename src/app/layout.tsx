import type { Metadata, Viewport } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/toaster";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Skip-Ad · 开屏广告与跳转广告演示系统",
  description:
    "移动端开屏/插屏广告 2026 全策略演示 + 智能防跳转引擎：多信号精准区分广告诱导跳转与用户主动操作，含智能学习、PWA 安装与投放管理后台。",
  keywords: ["开屏广告", "跳转广告", "防跳转", "摇一摇", "广告策略", "Skip-Ad", "GKD", "智能学习"],
  manifest: "/manifest.webmanifest",
  icons: {
    icon: "https://z-cdn.chatglm.cn/z-ai/static/logo.svg",
    apple: "/icon-192.png",
  },
  appleWebApp: { capable: true, statusBarStyle: "default", title: "Skip-Ad" },
};

export const viewport: Viewport = {
  themeColor: "#7c3aed",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased bg-background text-foreground`}
      >
        {children}
        <Toaster />
      </body>
    </html>
  );
}
