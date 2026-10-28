"use client";

import { useEffect, useRef } from "react";
import { formatTime } from "@/lib/format";
import { useSession } from "@/lib/session";
import { LoginForm } from "./LoginForm";

export function AccountDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  const { session, logout } = useSession();

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog ref={ref} className="dialog" aria-labelledby="account-title" onClose={onClose}>
      <div className="dialog-head">
        <h2 id="account-title">账户</h2>
        <button className="icon-btn" aria-label="关闭账户对话框" onClick={onClose}>
          ✕
        </button>
      </div>
      {session.status === "authenticated" ? (
        <>
          <div className="account-card">
            <div className="avatar" aria-hidden>
              {session.user.nickname.slice(0, 1).toUpperCase()}
            </div>
            <div>
              <div className="account-name">{session.user.nickname}</div>
              <div className="muted">
                用户 ID {session.user.id}，{formatTime(session.user.createdAt)} 加入
              </div>
            </div>
          </div>
          <section className="dialog-section">
            <h3>切换用户</h3>
            <LoginForm submitLabel="切换" onDone={onClose} />
          </section>
          <button
            className="btn btn-danger-ghost"
            onClick={() => {
              void logout();
              onClose();
            }}
          >
            退出登录
          </button>
        </>
      ) : (
        <section className="dialog-section">
          <p className="muted">登录后可上传素材、提交制作并查看自己的历史记录。</p>
          <LoginForm onDone={onClose} />
        </section>
      )}
    </dialog>
  );
}
