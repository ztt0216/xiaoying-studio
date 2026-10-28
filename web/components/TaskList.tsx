"use client";

import { useEffect, useRef, useState } from "react";
import type { TrackedTask } from "@/lib/taskTracker";
import { useTracker } from "@/lib/tracker-react";
import type { Task } from "@/lib/types";
import { TaskCard } from "./TaskCard";

export function TaskList({ entries, showTemplate }: { entries: TrackedTask[]; showTemplate?: boolean }) {
  const tracker = useTracker();
  const [preview, setPreview] = useState<Task | null>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const d = dialogRef.current;
    if (!d) return;
    if (preview && !d.open) d.showModal();
    if (!preview && d.open) d.close();
  }, [preview]);

  return (
    <>
      <div className="task-grid">
        {entries.map((e) => (
          <TaskCard
            key={e.task.id}
            entry={e}
            showTemplate={showTemplate}
            onRetryQuery={(id) => tracker.retry(id)}
            onPreview={setPreview}
          />
        ))}
      </div>
      <dialog ref={dialogRef} className="dialog dialog-wide" aria-label="结果预览" onClose={() => setPreview(null)}>
        <div className="dialog-head">
          <h2>{preview ? `${preview.templateTitle} · 任务 #${preview.id}` : ""}</h2>
          <button className="icon-btn" aria-label="关闭预览" onClick={() => setPreview(null)}>
            ✕
          </button>
        </div>
        {preview?.resultUrl && <img className="preview-img" src={preview.resultUrl} alt={`任务 #${preview.id} 结果大图`} />}
      </dialog>
    </>
  );
}
