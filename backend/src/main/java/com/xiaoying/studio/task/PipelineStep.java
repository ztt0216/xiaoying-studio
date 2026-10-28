package com.xiaoying.studio.task;

public interface PipelineStep {

    TaskStage stage();

    void execute(TaskContext ctx) throws StepFailedException;
}
