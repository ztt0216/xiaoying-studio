"use client";

import { createContext, useContext, useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import { api } from "./api";
import { useSession } from "./session";
import { TaskTracker, type TrackedTask, type TrackerSnapshot } from "./taskTracker";

const TrackerContext = createContext<TaskTracker | null>(null);
const EMPTY: TrackerSnapshot = new Map();

/**
 * 每个用户一个跟踪器实例。切换/退出用户时 key 变化，旧实例被 stop：
 * 在途请求被中止，迟到的响应被丢弃，不会把上一个用户的任务写进当前界面。
 */
function TrackerProvider({ children, userId }: { children: ReactNode; userId: number | null }) {
  const [tracker] = useState(() => new TaskTracker({ fetchTask: (id, signal) => api.task(id, signal) }));
  useEffect(() => {
    tracker.start();
    return () => tracker.stop();
  }, [tracker]);

  // 登录确认后（含刷新页面）立即拉取该用户所有未结束任务，不依赖当前停留在哪个页面。
  // 失败时按退避重试；任务本身的状态由各页面和轮询继续维护。
  useEffect(() => {
    if (userId === null) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    const load = (attempt: number) => {
      api.activeTasks(controller.signal).then(
        (tasks) => tracker.upsert(tasks),
        () => {
          if (!controller.signal.aborted) timer = setTimeout(() => load(attempt + 1), Math.min(2000 * 2 ** attempt, 30000));
        },
      );
    };
    load(0);
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [tracker, userId]);
  return <TrackerContext.Provider value={tracker}>{children}</TrackerContext.Provider>;
}

export function UserScopedTracker({ children }: { children: ReactNode }) {
  const { session } = useSession();
  const userId = session.status === "authenticated" ? session.user.id : null;
  return (
    <TrackerProvider key={userId ?? "anonymous"} userId={userId}>
      {children}
    </TrackerProvider>
  );
}

export function useTracker(): TaskTracker {
  const tracker = useContext(TrackerContext);
  if (!tracker) throw new Error("useTracker must be used inside UserScopedTracker");
  return tracker;
}

export function useTrackerSnapshot(): TrackerSnapshot {
  const tracker = useTracker();
  return useSyncExternalStore(tracker.subscribe, tracker.getSnapshot, () => EMPTY);
}

/** 按创建时间倒序返回（可选按模板过滤）的已跟踪任务 */
export function useTrackedTasks(templateId?: string): TrackedTask[] {
  const snapshot = useTrackerSnapshot();
  return useMemo(
    () =>
      [...snapshot.values()]
        .filter((e) => !templateId || e.task.templateId === templateId)
        .sort((a, b) => b.task.id - a.task.id),
    [snapshot, templateId],
  );
}
