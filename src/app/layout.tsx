import type { Metadata } from "next";
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
  title: "开屏卫士 Skip-Ad · 开屏广告秒跳引擎",
  description: "开屏广告自动跳过引擎：五层内置规则 + 28 款分应用表，0ms 露头秒跳，绝不乱点。GitHub 开源：xiaobaiba999/Skip-Ad",
  keywords: ["开屏卫士", "Skip-Ad", "开屏广告", "秒跳", "无障碍", "Android", "广告跳过"],
  authors: [{ name: "xiaobaiba999" }],
  icons: {
    icon: "https://z-cdn.chatglm.cn/z-ai/static/logo.svg",
  },
  openGraph: {
    title: "开屏卫士 Skip-Ad",
    description: "开屏广告秒跳引擎 · 内置规则库 · 绝不乱点",
    siteName: "Skip-Ad",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "开屏卫士 Skip-Ad",
    description: "开屏广告秒跳引擎 · 内置规则库 · 绝不乱点",
  },
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
