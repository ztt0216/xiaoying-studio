package com.xiaoying.studio.task;

import java.util.ArrayList;
import java.util.List;

/** 记录流水线写入顺序，并模拟数据库中“终态不可回退”的条件更新。 */
class InMemoryRecorder implements TaskProgressRecorder {

    final List<String> events = new ArrayList<>();
    TaskStatus status = TaskStatus.PENDING;
    TaskStage stage;
    TaskStage failedStage;
    String errorMessage;
    String resultUrl;
    private long nextStepId = 1;

    @Override
    public boolean enterStage(long taskId, TaskStage s) {
        if (status.isTerminal()) {
            events.add("skip:" + s);
            return false;
        }
        status = TaskStatus.PROCESSING;
        stage = s;
        events.add("enter:" + s);
        return true;
    }

    @Override
    public long startStep(long taskId, TaskStage s) {
        events.add("start:" + s);
        return nextStepId++;
    }

    @Override
    public void finishStep(long stepId, boolean success, String message) {
        events.add((success ? "ok:" : "fail:") + stage);
    }

    @Override
    public void markFailed(long taskId, TaskStage s, String message) {
        if (status.isTerminal()) {
            return;
        }
        status = TaskStatus.FAILED;
        failedStage = s;
        errorMessage = message;
        events.add("FAILED");
    }

    @Override
    public void markSucceeded(long taskId, String url) {
        if (status != TaskStatus.PROCESSING) {
            return;
        }
        status = TaskStatus.SUCCEEDED;
        resultUrl = url;
        events.add("SUCCEEDED");
    }
}
