"use client";

import { useId, useState, type FormEvent } from "react";
import { errorMessage } from "@/lib/api";
import { useSession } from "@/lib/session";

export function LoginForm({ submitLabel = "登录", onDone }: { submitLabel?: string; onDone?: () => void }) {
  const { login } = useSession();
  const inputId = useId();
  const [nickname, setNickname] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(name: string) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await login(name.trim());
      setNickname("");
      onDone?.();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (nickname.trim()) void submit(nickname);
  }

  return (
    <form className="login-form" onSubmit={onSubmit}>
      <label htmlFor={inputId} className="field-label">
        昵称
      </label>
      <div className="row">
        <input
          id={inputId}
          className="input"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder="输入昵称，不存在则自动创建"
          minLength={2}
          maxLength={20}
          autoComplete="username"
        />
        <button className="btn btn-primary" type="submit" disabled={pending || nickname.trim().length < 2}>
          {pending ? "登录中…" : submitLabel}
        </button>
      </div>
      <div className="row hint">
        演示账号：
        {["alice", "bob"].map((n) => (
          <button key={n} type="button" className="btn btn-link" disabled={pending} onClick={() => void submit(n)}>
            {n}
          </button>
        ))}
      </div>
      {error && (
        <p className="error-text" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}
