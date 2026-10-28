"use client";

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";

interface Settled<T> {
  key: string;
  data?: T;
  error?: unknown;
}

/**
 * 按 key 加载数据：key 变化或 reload 时发起新请求并中止旧请求，旧请求的结果不会覆盖新 key。
 * loading 由“当前 key 是否已有结果”推导，不在 effect 中同步 setState。
 * key 为 null 时不请求（例如未登录）。
 */
export function useResource<T>(key: string | null, load: (signal: AbortSignal) => Promise<T>) {
  const [settled, setSettled] = useState<Settled<T> | null>(null);
  const [nonce, setNonce] = useState(0);
  const loadRef = useRef(load);
  useLayoutEffect(() => {
    loadRef.current = load;
  });

  const requestKey = key === null ? null : `${key}#${nonce}`;

  useEffect(() => {
    if (requestKey === null) return;
    const controller = new AbortController();
    loadRef.current(controller.signal).then(
      (data) => !controller.signal.aborted && setSettled({ key: requestKey, data }),
      (error) => !controller.signal.aborted && setSettled({ key: requestKey, error }),
    );
    return () => controller.abort();
  }, [requestKey]);

  const current = settled?.key === requestKey ? settled : null;
  const reload = useCallback(() => setNonce((n) => n + 1), []);

  return {
    loading: requestKey !== null && !current,
    data: current?.data,
    /** 最近一次成功的数据（可能属于上一个 key），用于切换时保留旧内容避免闪烁 */
    latest: settled?.data,
    error: current?.error,
    loaded: current !== null && current.error === undefined,
    reload,
  };
}
