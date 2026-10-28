import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TaskTracker } from "./taskTracker";
import type { Task, TaskStatus, TaskStage } from "./types";

let clock = 0;

function task(id: number, status: TaskStatus, extra: Partial<Task> = {}): Task {
  clock += 1;
  return {
    id,
    templateId: "demo-postcard",
    templateTitle: "旅行明信片",
    templateCoverUrl: "/demo/postcard-cover.png",
    status,
    stage: null,
    inputUrls: [],
    createdAt: new Date(0).toISOString(),
    updatedAt: new Date(clock * 1000).toISOString(),
    ...extra,
  };
}

/** 可编程的假后端：按任务 id 排队返回结果或抛错 */
function fakeBackend() {
  const queues = new Map<number, Array<Task | Error>>();
  const calls: number[] = [];
  const signals: AbortSignal[] = [];
  const fetchTask = vi.fn(async (id: number, signal: AbortSignal) => {
    calls.push(id);
    signals.push(signal);
    const next = queues.get(id)?.shift();
    if (!next) throw new Error(`no response queued for ${id}`);
    if (next instanceof Error) throw next;
    return next;
  });
  return {
    fetchTask,
    calls,
    signals,
    reply(id: number, ...responses: Array<Task | Error>) {
      queues.set(id, [...(queues.get(id) ?? []), ...responses]);
    },
  };
}

const networkError = () => Object.assign(new Error("网络异常"), { kind: "network", status: 0 });
const stage = (s: TaskStage) => ({ stage: s });

describe("TaskTracker", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("tracks several tasks independently and stops each one when it finishes", async () => {
    const be = fakeBackend();
    const tracker = new TaskTracker({ fetchTask: be.fetchTask, intervalMs: 1000 });
    tracker.upsert([task(1, "PENDING"), task(2, "PENDING")]);

    be.reply(1, task(1, "PROCESSING", stage("GENERATE")));
    be.reply(2, task(2, "FAILED", { failedStage: "ASSET_CHECK", errorMessage: "素材数量不符" }));
    await vi.advanceTimersByTimeAsync(1000);

    const snap = tracker.getSnapshot();
    expect(snap.get(1)!.task.status).toBe("PROCESSING");
    expect(snap.get(1)!.task.stage).toBe("GENERATE");
    expect(snap.get(2)!.task.status).toBe("FAILED");
    expect(snap.get(2)!.task.errorMessage).toBe("素材数量不符");
    expect(snap.get(1)!.task.errorMessage).toBeUndefined();
    expect(tracker.activeIds()).toEqual([1]);

    be.reply(1, task(1, "SUCCEEDED", { resultUrl: "/files/results/task-1.png" }));
    await vi.advanceTimersByTimeAsync(1000);
    expect(tracker.getSnapshot().get(1)!.task.resultUrl).toBe("/files/results/task-1.png");
    expect(tracker.activeIds()).toEqual([]);

    // 没有未结束任务后不再发起任何请求
    const callsBefore = be.calls.length;
    await vi.advanceTimersByTimeAsync(10_000);
    expect(be.calls.length).toBe(callsBefore);
  });

  it("does not poll tasks that are already finished", async () => {
    const be = fakeBackend();
    const tracker = new TaskTracker({ fetchTask: be.fetchTask, intervalMs: 1000 });
    tracker.upsert([task(1, "SUCCEEDED"), task(2, "FAILED")]);
    await vi.advanceTimersByTimeAsync(5000);
    expect(be.fetchTask).not.toHaveBeenCalled();
  });

  it("treats a query error as a request problem, not a task failure", async () => {
    const be = fakeBackend();
    const tracker = new TaskTracker({ fetchTask: be.fetchTask, intervalMs: 1000 });
    tracker.upsert([task(1, "PROCESSING", stage("GENERATE")), task(2, "PROCESSING", stage("ASSET_CHECK"))]);

    be.reply(1, networkError());
    be.reply(2, task(2, "PROCESSING", stage("GENERATE")));
    await vi.advanceTimersByTimeAsync(1000);

    const one = tracker.getSnapshot().get(1)!;
    expect(one.task.status).toBe("PROCESSING"); // 保留上次结果
    expect(one.task.stage).toBe("GENERATE");
    expect(one.queryError).toBeTruthy();
    expect(one.paused).toBe(false);
    // 另一个任务不受影响
    expect(tracker.getSnapshot().get(2)!.queryError).toBeNull();
    expect(tracker.getSnapshot().get(2)!.task.stage).toBe("GENERATE");

    // 恢复后错误清除，继续跟踪（第一次重试在退避后发生）
    be.reply(1, task(1, "SUCCEEDED", { resultUrl: "/r.png" }));
    await vi.advanceTimersByTimeAsync(1000);
    expect(tracker.getSnapshot().get(1)!.queryError).toBeNull();
    expect(tracker.getSnapshot().get(1)!.task.status).toBe("SUCCEEDED");
  });

  it("pauses after repeated query errors and resumes on manual retry", async () => {
    const be = fakeBackend();
    const tracker = new TaskTracker({ fetchTask: be.fetchTask, intervalMs: 100, maxConsecutiveErrors: 3 });
    tracker.upsert([task(1, "PROCESSING")]);
    be.reply(1, networkError(), networkError(), networkError());
    await vi.advanceTimersByTimeAsync(100 + 100 + 200);

    const entry = tracker.getSnapshot().get(1)!;
    expect(entry.paused).toBe(true);
    expect(entry.task.status).toBe("PROCESSING");
    expect(tracker.activeIds()).toEqual([]);
    await vi.advanceTimersByTimeAsync(10_000);
    expect(be.calls.length).toBe(3);

    be.reply(1, task(1, "SUCCEEDED"));
    tracker.retry(1);
    await vi.advanceTimersByTimeAsync(0);
    expect(tracker.getSnapshot().get(1)!.task.status).toBe("SUCCEEDED");
    expect(tracker.getSnapshot().get(1)!.paused).toBe(false);
  });

  it("stops tracking a task the server no longer exposes (404)", async () => {
    const be = fakeBackend();
    const tracker = new TaskTracker({ fetchTask: be.fetchTask, intervalMs: 100 });
    tracker.upsert([task(1, "PENDING")]);
    be.reply(1, Object.assign(new Error("任务不存在"), { status: 404 }));
    await vi.advanceTimersByTimeAsync(100);
    expect(tracker.getSnapshot().get(1)!.paused).toBe(true);
    expect(tracker.getSnapshot().get(1)!.task.status).toBe("PENDING");
    expect(tracker.activeIds()).toEqual([]);
  });

  it("never moves a finished task back to processing", async () => {
    const be = fakeBackend();
    const tracker = new TaskTracker({ fetchTask: be.fetchTask, intervalMs: 100 });
    const done = task(1, "SUCCEEDED", { resultUrl: "/r.png" });
    tracker.upsert([done]);
    tracker.upsert([{ ...task(1, "PROCESSING"), updatedAt: new Date(9e12).toISOString() }]);
    expect(tracker.getSnapshot().get(1)!.task.status).toBe("SUCCEEDED");
    expect(tracker.activeIds()).toEqual([]);
  });

  it("drops late responses after stop (e.g. user switched)", async () => {
    const be = fakeBackend();
    let resolveLate: (t: Task) => void = () => {};
    be.fetchTask.mockImplementationOnce(
      (_id: number, signal: AbortSignal) =>
        new Promise<Task>((resolve) => {
          be.signals.push(signal);
          resolveLate = resolve;
        }),
    );
    const tracker = new TaskTracker({ fetchTask: be.fetchTask, intervalMs: 100 });
    const listener = vi.fn();
    tracker.subscribe(listener);
    tracker.upsert([task(1, "PROCESSING")]);
    listener.mockClear();
    await vi.advanceTimersByTimeAsync(100); // 请求在途

    tracker.stop();
    expect(be.signals[0].aborted).toBe(true);
    resolveLate(task(1, "SUCCEEDED"));
    await vi.advanceTimersByTimeAsync(1000);

    expect(listener).not.toHaveBeenCalled();
    expect(tracker.getSnapshot().get(1)!.task.status).toBe("PROCESSING");
    expect(be.fetchTask).toHaveBeenCalledTimes(1);
  });

  it("resumes unfinished tasks on start after stop", async () => {
    const be = fakeBackend();
    const tracker = new TaskTracker({ fetchTask: be.fetchTask, intervalMs: 100 });
    tracker.upsert([task(1, "PROCESSING"), task(2, "SUCCEEDED")]);
    tracker.stop();
    tracker.upsert([task(3, "PENDING")]);
    await vi.advanceTimersByTimeAsync(1000);
    expect(be.fetchTask).not.toHaveBeenCalled();

    be.reply(1, task(1, "SUCCEEDED"));
    be.reply(3, task(3, "PROCESSING"));
    tracker.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(be.calls.sort()).toEqual([1, 3]);
    expect(tracker.activeIds()).toEqual([3]);
  });
});
