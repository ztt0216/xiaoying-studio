"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, ApiError, errorMessage, setAuthToken, setUnauthorizedHandler } from "./api";
import type { User } from "./types";

/**
 * 轻量身份：昵称登录 → Java 签发令牌 → 存 localStorage，所有请求带 Authorization 头。
 * 任务、素材、历史都按令牌对应的用户在后端隔离。
 */
type SessionState =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "error"; message: string }
  | { status: "authenticated"; user: User };

interface SessionContextValue {
  session: SessionState;
  login: (nickname: string) => Promise<void>;
  logout: () => Promise<void>;
  reload: () => void;
}

const TOKEN_KEY = "studio.token";
const SessionContext = createContext<SessionContextValue | null>(null);

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function writeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // 隐私模式等无法写入时，仅当前页面有效
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  const clear = useCallback(() => {
    writeToken(null);
    setAuthToken(null);
    setSession({ status: "anonymous" });
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clear);
    return () => setUnauthorizedHandler(null);
  }, [clear]);

  useEffect(() => {
    const token = readToken();
    setAuthToken(token);
    const controller = new AbortController();
    (token ? api.me(controller.signal) : Promise.resolve(null))
      .then((user) => setSession(user ? { status: "authenticated", user } : { status: "anonymous" }))
      .catch((e) => {
        if (controller.signal.aborted) return;
        if (e instanceof ApiError && e.status === 401) clear();
        else setSession({ status: "error", message: errorMessage(e) });
      });
    return () => controller.abort();
  }, [attempt, clear]);

  const login = useCallback(async (nickname: string) => {
    const res = await api.login(nickname);
    writeToken(res.token);
    setAuthToken(res.token);
    setSession({ status: "authenticated", user: res.user });
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      // 令牌失效或网络问题都不影响本地退出
    }
    clear();
  }, [clear]);

  const reload = useCallback(() => {
    setSession({ status: "loading" });
    setAttempt((n) => n + 1);
  }, []);

  const value = useMemo(() => ({ session, login, logout, reload }), [session, login, logout, reload]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error("useSession must be used inside SessionProvider");
  return ctx;
}
