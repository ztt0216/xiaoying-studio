package com.xiaoying.studio.task;

import java.time.Instant;
import java.util.List;

/** 任务对外视图。steps 只在单个任务查询中返回。 */
public record TaskView(Long id, String templateId, String templateTitle, String templateCoverUrl,
                       String status, String stage, String failedStage, String errorMessage, String resultUrl,
                       List<String> inputUrls, List<StepView> steps,
                       Instant createdAt, Instant updatedAt, Instant startedAt, Instant finishedAt) {

    public record StepView(String stage, String status, String message, Instant startedAt, Instant finishedAt) {
    }

    static TaskView of(TaskRow t, List<String> inputUrls, List<TaskStepRow> steps) {
        List<StepView> stepViews = steps == null ? null : steps.stream()
                .map(s -> new StepView(s.stage(), s.status(), s.message(), s.startedAt(), s.finishedAt()))
                .toList();
        return new TaskView(t.id(), t.templateId(), t.templateTitle(), t.templateCoverUrl(), t.status(), t.stage(),
                t.failedStage(), t.errorMessage(), t.resultUrl(), inputUrls, stepViews,
                t.createdAt(), t.updatedAt(), t.startedAt(), t.finishedAt());
    }
}
