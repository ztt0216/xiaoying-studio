"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { api, errorMessage } from "@/lib/api";
import type { TemplateList } from "@/lib/types";
import { useResource } from "@/lib/useResource";
import { EmptyState, ErrorState } from "./StateViews";

export function TemplateBrowser() {
  const params = useSearchParams();
  const router = useRouter();
  const category = params.get("category");
  const res = useResource<TemplateList>(`templates:${category ?? ""}`, (signal) => api.templates(category, signal));
  // 切换分类时保留上一份数据（半透明显示），避免整页闪烁
  const data = res.data ?? res.latest;
  const categories = data?.categories ?? [];
  const loading = res.loading;
  const error = res.error ? errorMessage(res.error) : null;

  function select(c: string | null) {
    router.push(c ? `/?category=${encodeURIComponent(c)}` : "/", { scroll: false });
  }

  const tabs: (string | null)[] = [null, ...categories];

  return (
    <div className="page">
      <h1 className="page-title">选一个模板，放入你的照片</h1>
      <div className="tabs" role="tablist" aria-label="模板分类">
        {tabs.map((c) => (
          <button
            key={c ?? "all"}
            role="tab"
            aria-selected={category === c}
            className={category === c ? "tab active" : "tab"}
            onClick={() => select(c)}
          >
            {c ?? "全部"}
          </button>
        ))}
      </div>

      {error ? (
        <ErrorState message={`模板加载失败：${error}`} onRetry={res.reload} />
      ) : loading && !data ? (
        <div className="print-grid" aria-busy>
          {Array.from({ length: 8 }, (_, i) => (
            <div key={i} className="print skeleton" />
          ))}
        </div>
      ) : data && data.items.length === 0 ? (
        <EmptyState>该分类下暂无模板</EmptyState>
      ) : (
        <div className={loading ? "print-grid loading" : "print-grid"} aria-busy={loading}>
          {data?.items.map((t) => (
            <Link key={t.id} href={`/templates/${t.id}`} className="print">
              <img src={t.coverUrl} alt="" loading="lazy" />
              <div className="print-caption">
                <span className="print-title">{t.title}</span>
                <span className="print-count">{t.requiredImageCount} 张照片</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
