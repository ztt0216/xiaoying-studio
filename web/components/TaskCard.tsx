"use client";

import Link from "next/link";
import { useState } from "react";
import { formatTime } from "@/lib/format";
import type { TrackedTask } from "@/lib/taskTracker";
import { STAGES, stageLabel, type Task } from "@/lib/types";

function statusText(task: Task): string {
  switch (task.status) {
    case "PENDING":
      return "排队中";
    case "PROCESSING":
      return `${stageLabel(task.stage)}中`;
    case "SUCCEEDED":
      return "已完成";
    case "FAILED":
      return "未完成";
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
            <span className="stage-no" aria-hidden>
              {i + 1}
            </span>
            <span>{s.label}</span>
            <span className="sr-only">：{stateLabel}</span>
          </li>
        );
      })}
    </ol>
  );
}

/**
 * 任务以“底片”呈现：排队是未曝光的暗帧，处理中是安全灯下的负片，
 * 成功显影为正片，失败是起雾的废片。画面本身就是状态。
 */
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
  const image = task.resultUrl ?? task.inputUrls[0] ?? task.templateCoverUrl;
  const developed = task.status === "SUCCEEDED" && task.resultUrl;
  // 只有在本页看着它从处理中变成功的任务才播放一次“显影”过渡；载入时已完成的不播放
  const [initialStatus] = useState(task.status);
  const justDeveloped = developed && initialStatus !== "SUCCEEDED";

  return (
    <article className={`frame frame-${task.status.toLowerCase()}`} aria-label={`任务 ${task.id}`}>
      <div className="frame-edge" aria-hidden>
        <span>{String(task.id).padStart(2, "0")}</span>
        <span>{formatTime(task.createdAt)}</span>
      </div>
      <div className="frame-image">
        {developed ? (
          <button className="media-btn" onClick={() => onPreview(task)} aria-label={`查看任务 ${task.id} 的成品大图`}>
            <img src={task.resultUrl!} alt={`任务 ${task.id} 成品`} className={justDeveloped ? "develop" : undefined} />
          </button>
        ) : (
          <>
            <img src={image} alt="" />
            <div className="frame-caption" aria-live="polite">
              {task.status === "FAILED" ? task.errorMessage ?? "制作未完成" : statusText(task)}
            </div>
          </>
        )}
      </div>
      <div className="frame-body">
        <div className="frame-head">
          {showTemplate ? (
            <Link href={`/templates/${task.templateId}`} className="frame-title">
              {task.templateTitle}
            </Link>
          ) : (
            <span className="frame-title">第 {task.id} 号任务</span>
          )}
          <span className={`status status-${task.status.toLowerCase()}`}>{statusText(task)}</span>
        </div>
        <StageTrack task={task} />
        {task.status === "FAILED" && (
          <p className="error-text">
            在“{stageLabel(task.failedStage)}”停止：{task.errorMessage}
          </p>
        )}
        {task.finishedAt && (
          <dl className="frame-meta">
            <div>
              <dt>结束于</dt>
              <dd>{formatTime(task.finishedAt)}</dd>
            </div>
          </dl>
        )}
        {queryError && (
          <div className="query-error" role="status">
            <span>
              {queryError}
              {paused ? "，已停止自动刷新。" : "，稍后自动重试。"}
            </span>
            <button className="btn-text" onClick={() => onRetryQuery(task.id)}>
              重新查询
            </button>
          </div>
        )}
      </div>
    </article>
  );
}
