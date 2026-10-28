import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Header } from "@/components/Header";
import { SessionProvider } from "@/lib/session";
import { UserScopedTracker } from "@/lib/tracker-react";
import "./globals.css";

export const metadata: Metadata = {
  title: "小影工坊 · 素材制作",
  description: "浏览模板、上传图片、后台制作并查看历史结果",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <SessionProvider>
          <UserScopedTracker>
            <Header />
            <main className="main">{children}</main>
          </UserScopedTracker>
        </SessionProvider>
      </body>
    </html>
  );
}
