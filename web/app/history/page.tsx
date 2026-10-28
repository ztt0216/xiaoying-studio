"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { EmptyState, ErrorState, Loading, RequireLogin } from "@/components/StateViews";
import { TaskList } from "@/components/TaskList";
import { useTrackedTasks } from "@/lib/tracker-react";
import { useTaskHistory } from "@/lib/useTaskHistory";
import { isTerminal } from "@/lib/types";

const FILTERS = [
  { key: "all", label: "全部" },
  { key: "active", label: "处理中" },
  { key: "SUCCEEDED", label: "已完成" },
  { key: "FAILED", label: "失败" },
] as const;

export default function HistoryPage() {
  return (
    <div className="page">
      <h1 className="page-title">我的作品</h1>
      <RequireLogin hint="登录后查看你的制作历史。">
        <History />
      </RequireLogin>
    </div>
  );
}

function History() {
  const history = useTaskHistory(null);
  const all = useTrackedTasks();
  const [filter, setFilter] = useState<(typeof FILTERS)[number]["key"]>("all");
  const entries = useMemo(
    () =>
      all.filter((e) =>
        filter === "all" ? true : filter === "active" ? !isTerminal(e.task.status) : e.task.status === filter,
      ),
    [all, filter],
  );

  return (
    <>
      <div className="toolbar">
        <div className="tabs" role="tablist" aria-label="按状态筛选">
          {FILTERS.map((f) => (
            <button key={f.key} role="tab" aria-selected={filter === f.key} className={filter === f.key ? "tab active" : "tab"} onClick={() => setFilter(f.key)}>
              {f.label}
            </button>
          ))}
        </div>
        <button className="btn btn-ghost" onClick={history.reload} disabled={history.loading}>
          {history.loading ? "刷新中…" : "刷新"}
        </button>
      </div>
      {history.error && <ErrorState message={`历史记录加载失败：${history.error}`} onRetry={history.reload} />}
      {entries.length > 0 ? (
        <TaskList entries={entries} showTemplate />
      ) : !history.loaded ? (
        !history.error && <Loading label="加载历史记录…" />
      ) : (
        <EmptyState>
          <p>{filter === "all" ? "还没有制作记录。" : "没有符合条件的任务。"}</p>
          <Link href="/" className="btn btn-ghost">
            去挑选模板
          </Link>
        </EmptyState>
      )}
    </>
  );
}
