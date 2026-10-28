"use client";

import { useEffect, useRef, useState } from "react";
import { api, errorMessage, isAbortError, uploadAsset } from "@/lib/api";
import { formatBytes, mimeLabel } from "@/lib/format";
import { useTracker } from "@/lib/tracker-react";
import type { Asset, Template } from "@/lib/types";

type Slot =
  | { status: "empty" }
  | { status: "uploading"; file: File; preview: string; progress: number }
  | { status: "ready"; file: File; preview: string; asset: Asset }
  | { status: "error"; file: File; preview: string | null; error: string; retryable: boolean };

function validateLocally(file: File, template: Template): string | null {
  if (!template.acceptedMimeTypes.includes(file.type)) return `格式不支持，仅支持 ${mimeLabel(template.acceptedMimeTypes)}`;
  if (file.size > template.maxFileSizeBytes) return `文件 ${formatBytes(file.size)}，超过 ${formatBytes(template.maxFileSizeBytes)} 限制`;
  if (file.size === 0) return "文件为空";
  return null;
}

export function UploadPanel({ template }: { template: Template }) {
  const tracker = useTracker();
  const count = template.requiredImageCount;
  const [slots, setSlots] = useState<Slot[]>(() => Array.from({ length: count }, () => ({ status: "empty" })));
  const controllers = useRef<(AbortController | null)[]>([]);
  const inputs = useRef<(HTMLInputElement | null)[]>([]);
  const previews = useRef(new Set<string>());
  const submittingRef = useRef(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // 卸载时中止上传、释放本地预览地址
  useEffect(() => {
    const ctrls = controllers.current;
    const urls = previews.current;
    return () => {
      ctrls.forEach((c) => c?.abort());
      urls.forEach((u) => URL.revokeObjectURL(u));
    };
  }, []);

  function setSlot(i: number, slot: Slot) {
    setSlots((prev) => {
      const old = prev[i];
      const oldPreview = old.status === "empty" ? null : old.preview;
      const newPreview = slot.status === "empty" ? null : slot.preview;
      if (oldPreview && oldPreview !== newPreview) {
        URL.revokeObjectURL(oldPreview);
        previews.current.delete(oldPreview);
      }
      return prev.map((s, j) => (j === i ? slot : s));
    });
  }

  async function startUpload(i: number, file: File) {
    controllers.current[i]?.abort();
    controllers.current[i] = null;
    setNotice(null);
    const localError = validateLocally(file, template);
    if (localError) {
      setSlot(i, { status: "error", file, preview: null, error: localError, retryable: false });
      return;
    }
    const preview = URL.createObjectURL(file);
    previews.current.add(preview);
    const controller = new AbortController();
    controllers.current[i] = controller;
    setSlot(i, { status: "uploading", file, preview, progress: 0 });
    try {
      const asset = await uploadAsset(template.id, file, {
        signal: controller.signal,
        onProgress: (progress) => {
          if (controllers.current[i] === controller) setSlot(i, { status: "uploading", file, preview, progress });
        },
      });
      if (controllers.current[i] !== controller) return;
      setSlot(i, { status: "ready", file, preview, asset });
    } catch (e) {
      if (isAbortError(e) || controllers.current[i] !== controller) return;
      setSlot(i, { status: "error", file, preview, error: errorMessage(e), retryable: true });
    } finally {
      if (controllers.current[i] === controller) controllers.current[i] = null;
    }
  }

  function remove(i: number) {
    controllers.current[i]?.abort();
    controllers.current[i] = null;
    setSlot(i, { status: "empty" });
  }

  const readyIds = slots.flatMap((s) => (s.status === "ready" ? [s.asset.id] : []));
  const uploadingCount = slots.filter((s) => s.status === "uploading").length;
  const missing = count - readyIds.length;
  const canSubmit = missing === 0 && !submitting;

  async function submit() {
    if (!canSubmit || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    setSubmitError(null);
    setNotice(null);
    try {
      const task = await api.createTask(template.id, readyIds);
      tracker.upsert([task]);
      setNotice(`第 ${task.id} 号任务已开始制作。离开这个页面也会继续处理。`);
    } catch (e) {
      setSubmitError(errorMessage(e));
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  let buttonLabel = "开始制作";
  if (submitting) buttonLabel = "提交中…";
  else if (uploadingCount > 0) buttonLabel = "素材上传中…";
  else if (missing > 0) buttonLabel = `还差 ${missing} 张照片`;

  return (
    <section aria-labelledby="upload-title" className="upload-panel">
      <div className="section-head">
        <h2 id="upload-title">放入照片</h2>
        <ul className="spec" aria-label="素材要求">
          <li>{count} 张</li>
          <li>{mimeLabel(template.acceptedMimeTypes)}</li>
          <li>每张不超过 {formatBytes(template.maxFileSizeBytes)}</li>
        </ul>
      </div>
      <div className={`slot-grid slots-${count}`}>
        {slots.map((slot, i) => {
          const n = i + 1;
          return (
            <div key={i} className={`slot slot-${slot.status}`}>
              <input
                ref={(el) => {
                  inputs.current[i] = el;
                }}
                type="file"
                accept={template.acceptedMimeTypes.join(",")}
                className="sr-only"
                tabIndex={-1}
                aria-hidden
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  e.target.value = "";
                  if (file) void startUpload(i, file);
                }}
              />
              {slot.status === "empty" ? (
                <button className="slot-pick" onClick={() => inputs.current[i]?.click()} aria-label={`选择第 ${n} 张图片`}>
                  <span className="slot-no" aria-hidden>
                    {n}
                  </span>
                  <span>选择照片</span>
                </button>
              ) : (
                <>
                  {slot.preview ? <img src={slot.preview} alt={`第 ${n} 张素材预览`} /> : <div className="slot-placeholder" />}
                  <div className="slot-status" role="status">
                    {slot.status === "uploading" && (
                      <span>
                        <span className="spinner" aria-hidden /> 上传中 {slot.progress}%
                      </span>
                    )}
                    {slot.status === "ready" && <span className="ok-text">已上传</span>}
                    {slot.status === "error" && <span className="error-text">{slot.error}</span>}
                  </div>
                  <div className="slot-actions">
                    {slot.status === "error" && slot.retryable && (
                      <button className="btn btn-small" onClick={() => void startUpload(i, slot.file)} aria-label={`重新上传第 ${n} 张图片`}>
                        重试
                      </button>
                    )}
                    {slot.status === "uploading" ? (
                      <button className="btn btn-small" onClick={() => remove(i)} aria-label={`取消上传第 ${n} 张图片`}>
                        取消
                      </button>
                    ) : (
                      <>
                        <button className="btn btn-small" onClick={() => inputs.current[i]?.click()} aria-label={`替换第 ${n} 张图片`}>
                          替换
                        </button>
                        <button className="btn btn-small" onClick={() => remove(i)} aria-label={`移除第 ${n} 张图片`}>
                          移除
                        </button>
                      </>
                    )}
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>
      <button className="btn btn-primary btn-block" disabled={!canSubmit} onClick={() => void submit()} aria-busy={submitting}>
        {buttonLabel}
      </button>
      {submitError && (
        <p className="error-text" role="alert">
          提交失败：{submitError}
        </p>
      )}
      {notice && (
        <p className="ok-text" role="status">
          {notice}
        </p>
      )}
    </section>
  );
}
