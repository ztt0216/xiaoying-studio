"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useSession } from "@/lib/session";
import { useTrackedTasks } from "@/lib/tracker-react";
import { isTerminal } from "@/lib/types";
import { AccountDialog } from "./AccountDialog";

export function Header() {
  const pathname = usePathname();
  const { session } = useSession();
  const [open, setOpen] = useState(false);
  const tasks = useTrackedTasks();
  const active = tasks.filter((e) => !isTerminal(e.task.status)).length;

  const nav = [
    { href: "/", label: "模板" },
    { href: "/history", label: "我的作品" },
  ];

  return (
    <header className="header">
      <Link href="/" className="brand">
        小影工坊
      </Link>
      <nav className="nav" aria-label="主导航">
        {nav.map((n) => {
          const current = n.href === "/" ? pathname === "/" || pathname.startsWith("/templates") : pathname.startsWith(n.href);
          return (
            <Link key={n.href} href={n.href} className={current ? "nav-link active" : "nav-link"} aria-current={current ? "page" : undefined}>
              {n.label}
            </Link>
          );
        })}
      </nav>
      <div className="header-right">
        {active > 0 && (
          <Link href="/history" className="safelight" aria-live="polite">
            <span className="safelight-dot" aria-hidden />
            {active} 个任务处理中
          </Link>
        )}
        <button className="account-btn" onClick={() => setOpen(true)} aria-haspopup="dialog">
          {session.status === "authenticated" ? session.user.nickname : session.status === "loading" ? "…" : "登录"}
        </button>
      </div>
      <AccountDialog open={open} onClose={() => setOpen(false)} />
    </header>
  );
}
