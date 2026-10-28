package com.xiaoying.studio.task;

/** 流水线对持久化的全部依赖。生产实现落 MySQL，测试中用内存实现观察调用顺序。 */
public interface TaskProgressRecorder {

    /** @return false 表示任务已是终态，不应再推进 */
    boolean enterStage(long taskId, TaskStage stage);

    long startStep(long taskId, TaskStage stage);

    void finishStep(long stepId, boolean success, String message);

    void markFailed(long taskId, TaskStage stage, String message);

    void markSucceeded(long taskId, String resultUrl);
}
