"use client";

import { api, errorMessage } from "./api";
import { useSession } from "./session";
import { useTracker } from "./tracker-react";
import { useResource } from "./useResource";

/**
 * 从 Java 拉取当前用户的历史任务并交给跟踪器（未结束的会被继续跟踪）。
 * 刷新页面后正是通过它恢复记录。离开页面时中止请求。
 */
export function useTaskHistory(templateId: string | null) {
  const tracker = useTracker();
  const { session } = useSession();
  const key = session.status === "authenticated" ? `history:${session.user.id}:${templateId ?? "*"}` : null;
  const res = useResource(key, async (signal) => {
    const tasks = await api.tasks(templateId, signal);
    if (!signal.aborted) tracker.upsert(tasks);
    return tasks.length;
  });
  return {
    loading: res.loading,
    loaded: res.latest !== undefined,
    error: res.error ? errorMessage(res.error) : null,
    reload: res.reload,
  };
}
