import type { Asset, SimulationConfig, Task, Template, TemplateList, User } from "./types";

/**
 * 请求异常：kind=network 表示没拿到响应（断网、后端不可用、代理失败），
 * kind=http 表示后端返回了错误状态码。两者都只是“请求失败”，与任务本身的 FAILED 状态无关。
 */
export class ApiError extends Error {
  constructor(
    public readonly kind: "network" | "http",
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function isAbortError(e: unknown): boolean {
  return e instanceof DOMException && e.name === "AbortError";
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (e instanceof Error) return e.message;
  return "未知错误";
}

let authToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

async function parseError(res: Response): Promise<ApiError> {
  let code = "HTTP_" + res.status;
  let message = `请求失败（${res.status}）`;
  try {
    const body = await res.json();
    if (body?.code) code = body.code;
    if (body?.message) message = body.message;
  } catch {
    // 代理错误页等非 JSON 响应
    if (res.status >= 500) message = "服务暂不可用，请稍后重试";
  }
  if (res.status === 401) onUnauthorized?.();
  return new ApiError("http", res.status, code, message);
}

async function request<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (authToken) headers.set("Authorization", `Bearer ${authToken}`);
  let body = init.body;
  if (init.json !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(init.json);
  }
  let res: Response;
  try {
    res = await fetch(path, { ...init, headers, body, cache: "no-store" });
  } catch (e) {
    if (isAbortError(e)) throw e;
    throw new ApiError("network", 0, "NETWORK_ERROR", "网络异常，无法连接服务器");
  }
  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  login: (nickname: string) => request<{ token: string; user: User }>("/api/session", { method: "POST", json: { nickname } }),
  logout: () => request<void>("/api/session", { method: "DELETE" }),
  me: (signal?: AbortSignal) => request<User>("/api/me", { signal }),

  templates: (category: string | null, signal?: AbortSignal) =>
    request<TemplateList>(`/api/templates${category ? `?category=${encodeURIComponent(category)}` : ""}`, { signal }),
  template: (id: string, signal?: AbortSignal) => request<Template>(`/api/templates/${encodeURIComponent(id)}`, { signal }),

  createTask: (templateId: string, assetIds: number[]) =>
    request<Task>("/api/tasks", { method: "POST", json: { templateId, assetIds } }),
  activeTasks: (signal?: AbortSignal) => request<Task[]>("/api/tasks?active=true&limit=100", { signal }),
  task: (id: number, signal?: AbortSignal) => request<Task>(`/api/tasks/${id}`, { signal }),
  tasks: (templateId: string | null, signal?: AbortSignal) =>
    request<Task[]>(`/api/tasks${templateId ? `?templateId=${encodeURIComponent(templateId)}` : ""}`, { signal }),

  simulation: (signal?: AbortSignal) => request<SimulationConfig>("/api/dev/simulation", { signal }),
  updateSimulation: (patch: { stepDelayMs?: number; failStage?: string }) =>
    request<SimulationConfig>("/api/dev/simulation", { method: "PUT", json: patch }),
};

/** 用 XHR 上传以获得进度；signal 中止时 reject AbortError。 */
export function uploadAsset(
  templateId: string,
  file: File,
  opts: { signal?: AbortSignal; onProgress?: (percent: number) => void } = {},
): Promise<Asset> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/assets");
    if (authToken) xhr.setRequestHeader("Authorization", `Bearer ${authToken}`);
    xhr.upload.onprogress = (ev) => {
      if (ev.lengthComputable) opts.onProgress?.(Math.round((ev.loaded / ev.total) * 100));
    };
    xhr.onload = () => {
      let body: { code?: string; message?: string } | null = null;
      try {
        body = JSON.parse(xhr.responseText);
      } catch {
        body = null;
      }
      if (xhr.status >= 200 && xhr.status < 300 && body) {
        resolve(body as unknown as Asset);
        return;
      }
      if (xhr.status === 401) onUnauthorized?.();
      const fallback = xhr.status === 413 ? "文件过大" : `上传失败（${xhr.status}）`;
      reject(new ApiError("http", xhr.status, body?.code ?? `HTTP_${xhr.status}`, body?.message ?? fallback));
    };
    xhr.onerror = () => reject(new ApiError("network", 0, "NETWORK_ERROR", "网络异常，上传失败"));
    xhr.onabort = () => reject(new DOMException("Upload aborted", "AbortError"));
    opts.signal?.addEventListener("abort", () => xhr.abort(), { once: true });
    const form = new FormData();
    form.append("templateId", templateId);
    form.append("file", file);
    xhr.send(form);
  });
}
