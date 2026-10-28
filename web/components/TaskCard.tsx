"use client";

import Link from "next/link";
import { formatTime } from "@/lib/format";
import type { TrackedTask } from "@/lib/taskTracker";
import { STAGES, stageLabel, type Task } from "@/lib/types";

function statusText(task: Task): string {
  switch (task.status) {
    case "PENDING":
      return "等待处理";
    case "PROCESSING":
      return `${stageLabel(task.stage)}中…`;
    case "SUCCEEDED":
      return "制作完成";
    case "FAILED":
      return "制作失败";
  }
}

/** 三段进度：已完成 / 进行中 / 失败 / 未开始 */
function StageTrack({ task }: { task: Task }) {
  const current = STAGES.findIndex((s) => s.key === (task.failedStage ?? task.stage));
  return (
    <ol className="stage-track" aria-label="处理阶段">
      {STAGES.map((s, i) => {
        let state = "todo";
        if (task.status === "SUCCEEDED" || i < current) state = "done";
        else if (i === current && task.status === "FAILED") state = "failed";
        else if (i === current && task.status === "PROCESSING") state = "active";
        const stateLabel = { done: "已完成", failed: "失败", active: "进行中", todo: "未开始" }[state];
        return (
          <li key={s.key} className={`stage stage-${state}`}>
            <span className="stage-dot" aria-hidden />
            <span>{s.label}</span>
            <span className="sr-only">：{stateLabel}</span>
          </li>
        );
      })}
    </ol>
  );
}

export function TaskCard({
  entry,
  onRetryQuery,
  onPreview,
  showTemplate,
}: {
  entry: TrackedTask;
  onRetryQuery: (id: number) => void;
  onPreview: (task: Task) => void;
  showTemplate?: boolean;
}) {
  const { task, queryError, paused } = entry;
  const thumb = task.resultUrl ?? task.inputUrls[0] ?? task.templateCoverUrl;

  return (
    <article className={`task-card task-${task.status.toLowerCase()}`} aria-label={`任务 #${task.id}`}>
      <div className="task-media">
        {task.status === "SUCCEEDED" && task.resultUrl ? (
          <button className="media-btn" onClick={() => onPreview(task)} aria-label={`预览任务 #${task.id} 的结果`}>
            <img src={task.resultUrl} alt={`任务 #${task.id} 结果`} />
          </button>
        ) : (
          <>
            <img src={thumb} alt="" className="dimmed" />
            <div className="media-overlay" aria-live="polite">
              {task.status === "FAILED" ? (
                <span className="overlay-error">{task.errorMessage ?? "制作失败"}</span>
              ) : (
                <>
                  <span className="spinner" aria-hidden />
                  <span>{statusText(task)}</span>
                </>
              )}
            </div>
          </>
        )}
      </div>
      <div className="task-body">
        <div className="task-title-row">
          {showTemplate ? (
            <Link href={`/templates/${task.templateId}`} className="task-title">
              {task.templateTitle}
            </Link>
          ) : (
            <span className="task-title">任务 #{task.id}</span>
          )}
          <span className={`badge badge-${task.status.toLowerCase()}`}>{statusText(task)}</span>
        </div>
        <StageTrack task={task} />
        {task.status === "FAILED" && (
          <p className="error-text">
            失败阶段：{stageLabel(task.failedStage)} · {task.errorMessage}
          </p>
        )}
        <p className="muted small">
          {showTemplate && <>#{task.id} · </>}创建于 {formatTime(task.createdAt)}
          {task.finishedAt && <> · 完成于 {formatTime(task.finishedAt)}</>}
        </p>
        {queryError && (
          <div className="query-error" role="status">
            <span>
              {queryError}
              {paused ? "，自动查询已暂停" : "，正在重试…"}
            </span>
            <button className="btn btn-link" onClick={() => onRetryQuery(task.id)}>
              重新查询
            </button>
          </div>
        )}
      </div>
    </article>
  );
}
