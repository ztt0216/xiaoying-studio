"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { EmptyState, ErrorState, Loading, RequireLogin } from "@/components/StateViews";
import { TaskList } from "@/components/TaskList";
import { UploadPanel } from "@/components/UploadPanel";
import { api, ApiError, errorMessage } from "@/lib/api";
import { useTrackedTasks } from "@/lib/tracker-react";
import { useTaskHistory } from "@/lib/useTaskHistory";
import type { Template } from "@/lib/types";
import { useResource } from "@/lib/useResource";

export default function TemplateDetailPage() {
  const { id } = useParams<{ id: string }>();
  const res = useResource<Template>(`template:${id}`, (signal) => api.template(id, signal));

  if (res.loading) return <Loading label="模板加载中…" />;
  if (res.error instanceof ApiError && res.error.status === 404)
    return (
      <EmptyState>
        <p>模板不存在或已下线。</p>
        <Link href="/" className="btn btn-ghost">
          返回模板列表
        </Link>
      </EmptyState>
    );
  if (res.error || !res.data) return <ErrorState message={`模板加载失败：${errorMessage(res.error)}`} onRetry={res.reload} />;

  const t = res.data;
  return (
    <div className="detail">
      <aside className="detail-side">
        <Link href="/" className="back-link">
          ‹ <span>{t.title}</span>
        </Link>
        <img className="detail-cover" src={t.coverUrl} alt={`${t.title} 示例封面`} />
        <p className="muted">{t.description}</p>
        <RequireLogin hint="登录后即可上传素材并提交制作。">
          <UploadPanel key={t.id} template={t} />
        </RequireLogin>
      </aside>
      <section className="detail-history" aria-labelledby="history-title">
        <RequireLogin hint="登录后查看该模板下你的制作记录。" showForm={false}>
          <TemplateHistory templateId={t.id} />
        </RequireLogin>
      </section>
    </div>
  );
}

function TemplateHistory({ templateId }: { templateId: string }) {
  const history = useTaskHistory(templateId);
  const entries = useTrackedTasks(templateId);
  return (
    <>
      <div className="section-head">
        <h2 id="history-title">History</h2>
        <button className="icon-btn" onClick={history.reload} aria-label="刷新历史记录" disabled={history.loading}>
          <span className={history.loading ? "spin" : undefined}>↻</span>
        </button>
      </div>
      {history.error && <ErrorState message={`历史记录加载失败：${history.error}`} onRetry={history.reload} />}
      {entries.length > 0 ? (
        <TaskList entries={entries} />
      ) : history.loading || !history.loaded ? (
        !history.error && <Loading label="加载历史记录…" />
      ) : (
        <EmptyState>还没有用这个模板制作过，上传素材试试吧。</EmptyState>
      )}
    </>
  );
}
