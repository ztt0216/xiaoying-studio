import { isTerminal, type Task } from "./types";

/**
 * 任务跟踪器：前端异步状态的唯一来源，与 React 解耦以便单测。
 *
 * - 每个未结束任务各自一个轮询循环（独立的定时器、AbortController、错误计数），互不串扰；
 * - 任务进入 SUCCEEDED/FAILED 后停止对它的轮询，没有未结束任务时不发任何请求；
 * - 查询失败只记录 queryError，保留上次拿到的任务数据，绝不把任务改成 FAILED；
 *   连续失败达到上限后暂停自动查询，由用户点击“重新查询”恢复；
 * - 终态不会被旧数据覆盖回处理中；
 * - stop() 中止所有在途请求，之后到达的响应全部丢弃（用于退出/切换用户、组件卸载）；
 *   start() 恢复对未结束任务的跟踪（兼容 React StrictMode 的卸载-重挂）。
 */

export interface TrackedTask {
  task: Task;
  /** 最近一次状态查询失败的原因；与任务本身是否失败无关 */
  queryError: string | null;
  /** 连续查询失败过多，自动查询已暂停 */
  paused: boolean;
}

export type TrackerSnapshot = ReadonlyMap<number, TrackedTask>;

export interface TaskTrackerOptions {
  fetchTask: (id: number, signal: AbortSignal) => Promise<Task>;
  intervalMs?: number;
  maxBackoffMs?: number;
  maxConsecutiveErrors?: number;
}

interface PollState {
  timer: ReturnType<typeof setTimeout> | null;
  controller: AbortController | null;
  errors: number;
}

const NOT_FOUND_MESSAGE = "任务不存在或无权访问";
const QUERY_FAILED_MESSAGE = "状态查询失败，已保留上次结果";

export class TaskTracker {
  private snapshot: TrackerSnapshot = new Map();
  private readonly listeners = new Set<() => void>();
  private readonly polls = new Map<number, PollState>();
  private stopped = false;
  private readonly fetchTask: TaskTrackerOptions["fetchTask"];
  private readonly intervalMs: number;
  private readonly maxBackoffMs: number;
  private readonly maxErrors: number;

  constructor(opts: TaskTrackerOptions) {
    this.fetchTask = opts.fetchTask;
    this.intervalMs = opts.intervalMs ?? 1500;
    this.maxBackoffMs = opts.maxBackoffMs ?? 15000;
    this.maxErrors = opts.maxConsecutiveErrors ?? 5;
  }

  // ---- 订阅（配合 useSyncExternalStore） ----
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  getSnapshot = (): TrackerSnapshot => this.snapshot;

  /** 当前正在自动跟踪的任务 id */
  activeIds(): number[] {
    return [...this.polls.keys()];
  }

  /** 合并来自提交接口或列表接口的任务，并为未结束的任务启动跟踪（stop 期间只合并数据）。 */
  upsert(tasks: Task[]): void {
    const next = new Map(this.snapshot);
    let changed = false;
    for (const task of tasks) {
      const prev = next.get(task.id);
      if (prev && !shouldReplace(prev.task, task)) continue;
      next.set(task.id, { task, queryError: null, paused: false });
      changed = true;
    }
    if (changed) this.commit(next);
    for (const task of tasks) {
      const current = this.snapshot.get(task.id);
      if (current && !isTerminal(current.task.status) && !current.paused) this.startPolling(task.id, this.intervalMs);
    }
  }

  /** 用户手动恢复查询：立即查询一次，并重置错误计数。 */
  retry(id: number): void {
    const entry = this.snapshot.get(id);
    if (this.stopped || !entry || isTerminal(entry.task.status)) return;
    this.stopPolling(id);
    this.patch(id, { paused: false });
    this.startPolling(id, 0);
  }

  start(): void {
    if (!this.stopped) return;
    this.stopped = false;
    for (const entry of this.snapshot.values()) {
      if (!isTerminal(entry.task.status) && !entry.paused) this.startPolling(entry.task.id, 0);
    }
  }

  stop(): void {
    this.stopped = true;
    for (const id of [...this.polls.keys()]) this.stopPolling(id);
  }

  // ---- 内部实现 ----
  private startPolling(id: number, delay: number): void {
    if (this.stopped || this.polls.has(id)) return;
    const state: PollState = { timer: null, controller: null, errors: 0 };
    this.polls.set(id, state);
    this.schedule(id, state, delay);
  }

  private stopPolling(id: number): void {
    const state = this.polls.get(id);
    if (!state) return;
    if (state.timer) clearTimeout(state.timer);
    state.controller?.abort();
    this.polls.delete(id);
  }

  private schedule(id: number, state: PollState, delay: number): void {
    state.timer = setTimeout(() => {
      state.timer = null;
      void this.poll(id, state);
    }, delay);
  }

  private async poll(id: number, state: PollState): Promise<void> {
    const controller = new AbortController();
    state.controller = controller;
    try {
      const task = await this.fetchTask(id, controller.signal);
      if (!this.isCurrent(id, state, controller)) return;
      state.errors = 0;
      this.applyPolled(task);
      if (isTerminal(this.snapshot.get(id)!.task.status)) this.stopPolling(id);
      else this.schedule(id, state, this.intervalMs);
    } catch (e) {
      if (!this.isCurrent(id, state, controller)) return;
      if (statusOf(e) === 404) {
        this.patch(id, { queryError: NOT_FOUND_MESSAGE, paused: true });
        this.stopPolling(id);
        return;
      }
      state.errors += 1;
      if (state.errors >= this.maxErrors) {
        this.patch(id, { queryError: QUERY_FAILED_MESSAGE, paused: true });
        this.stopPolling(id);
        return;
      }
      this.patch(id, { queryError: QUERY_FAILED_MESSAGE });
      const backoff = Math.min(this.intervalMs * 2 ** (state.errors - 1), this.maxBackoffMs);
      this.schedule(id, state, backoff);
    } finally {
      if (state.controller === controller) state.controller = null;
    }
  }

  /** 响应到达时确认：未被 stop、未被中止、该任务的轮询没有被替换。 */
  private isCurrent(id: number, state: PollState, controller: AbortController): boolean {
    return !this.stopped && !controller.signal.aborted && this.polls.get(id) === state;
  }

  private applyPolled(task: Task): void {
    const prev = this.snapshot.get(task.id);
    const accepted = !prev || shouldReplace(prev.task, task) ? task : prev.task;
    const next = new Map(this.snapshot);
    next.set(task.id, { task: accepted, queryError: null, paused: false });
    this.commit(next);
  }

  private patch(id: number, fields: Partial<Omit<TrackedTask, "task">>): void {
    const prev = this.snapshot.get(id);
    if (!prev) return;
    const next = new Map(this.snapshot);
    next.set(id, { ...prev, ...fields });
    this.commit(next);
  }

  private commit(next: Map<number, TrackedTask>): void {
    this.snapshot = next;
    for (const l of this.listeners) l();
  }
}

/** 终态不被非终态覆盖；同为非终态时以 updatedAt 新者为准。 */
export function shouldReplace(current: Task, incoming: Task): boolean {
  if (isTerminal(current.status) && !isTerminal(incoming.status)) return false;
  if (isTerminal(current.status) && isTerminal(incoming.status)) return true;
  return Date.parse(incoming.updatedAt) >= Date.parse(current.updatedAt) || isTerminal(incoming.status);
}

function statusOf(e: unknown): number | undefined {
  return typeof e === "object" && e !== null && "status" in e ? (e as { status: number }).status : undefined;
}
