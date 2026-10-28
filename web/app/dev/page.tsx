"use client";

import { useEffect, useState, type FormEvent } from "react";
import { ErrorState, Loading } from "@/components/StateViews";
import { api, errorMessage } from "@/lib/api";
import { STAGES, type SimulationConfig } from "@/lib/types";

/** 开发用模拟控制面板（不在导航中出现），对应 Java 的 /api/dev/simulation */
export default function DevPage() {
  const [config, setConfig] = useState<SimulationConfig | null>(null);
  const [delay, setDelay] = useState("3000");
  const [failStage, setFailStage] = useState("NONE");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    api
      .simulation(controller.signal)
      .then((c) => {
        setConfig(c);
        setDelay(String(c.stepDelayMs));
        setFailStage(c.failStage);
      })
      .catch((e) => !controller.signal.aborted && setError(errorMessage(e)));
    return () => controller.abort();
  }, []);

  async function save(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      const c = await api.updateSimulation({ stepDelayMs: Number(delay), failStage });
      setConfig(c);
      setSaved(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page narrow">
      <h1 className="page-title">模拟控制（开发）</h1>
      <p className="muted">修改只影响之后提交的任务；已提交的任务沿用提交时的配置快照。</p>
      {error && <ErrorState message={error} />}
      {!config && !error ? (
        <Loading />
      ) : (
        <form className="card form" onSubmit={save}>
          <label className="field-label" htmlFor="delay">
            每阶段耗时（毫秒）
          </label>
          <input id="delay" className="input" type="number" min={0} max={120000} step={100} value={delay} onChange={(e) => setDelay(e.target.value)} />
          <label className="field-label" htmlFor="fail">
            失败阶段
          </label>
          <select id="fail" className="input" value={failStage} onChange={(e) => setFailStage(e.target.value)}>
            <option value="NONE">不注入失败</option>
            {STAGES.map((s) => (
              <option key={s.key} value={s.key}>
                {s.label}（{s.key}）
              </option>
            ))}
          </select>
          <button className="btn btn-primary" disabled={saving}>
            {saving ? "保存中…" : "保存"}
          </button>
          {saved && (
            <p className="ok-text" role="status">
              已保存：耗时 {config?.stepDelayMs}ms，失败阶段 {config?.failStage}
            </p>
          )}
        </form>
      )}
    </div>
  );
}
