"use client";

import type { ReactNode } from "react";
import { useSession } from "@/lib/session";
import { LoginForm } from "./LoginForm";

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button className="btn btn-ghost" onClick={onRetry}>
          重试
        </button>
      )}
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="state">{children}</div>;
}

export function Loading({ label = "加载中…" }: { label?: string }) {
  return (
    <div className="state" role="status">
      <span className="spinner" aria-hidden /> {label}
    </div>
  );
}

/** 需要登录的区域：会话加载中/异常/未登录时给出对应界面 */
export function RequireLogin({ children, hint, showForm = true }: { children: ReactNode; hint: string; showForm?: boolean }) {
  const { session, reload } = useSession();
  if (session.status === "loading") return <Loading label="正在确认登录状态…" />;
  if (session.status === "error") return <ErrorState message={`无法确认登录状态：${session.message}`} onRetry={reload} />;
  if (session.status === "anonymous")
    return (
      <div className="state">
        <p>{hint}</p>
        {showForm && <LoginForm />}
      </div>
    );
  return <>{children}</>;
}
