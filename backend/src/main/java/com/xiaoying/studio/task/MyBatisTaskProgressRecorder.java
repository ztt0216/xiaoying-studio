package com.xiaoying.studio.task;

import org.springframework.stereotype.Component;

@Component
public class MyBatisTaskProgressRecorder implements TaskProgressRecorder {

    private final TaskMapper taskMapper;

    public MyBatisTaskProgressRecorder(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public boolean enterStage(long taskId, TaskStage stage) {
        return taskMapper.markStage(taskId, stage.name()) == 1;
    }

    @Override
    public long startStep(long taskId, TaskStage stage) {
        NewTaskStep step = new NewTaskStep();
        step.taskId = taskId;
        step.stage = stage.name();
        taskMapper.insertStep(step);
        return step.id;
    }

    @Override
    public void finishStep(long stepId, boolean success, String message) {
        taskMapper.finishStep(stepId, success ? "SUCCEEDED" : "FAILED", message);
    }

    @Override
    public void markFailed(long taskId, TaskStage stage, String message) {
        taskMapper.markFailed(taskId, stage == null ? null : stage.name(), message);
    }

    @Override
    public void markSucceeded(long taskId, String resultUrl) {
        taskMapper.markSucceeded(taskId, resultUrl);
    }
}
