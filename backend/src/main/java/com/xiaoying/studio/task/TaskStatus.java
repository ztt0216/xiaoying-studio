package com.xiaoying.studio.task;

public enum TaskStatus {
    /** 已受理、等待工作线程 */
    PENDING,
    /** 某个阶段正在执行 */
    PROCESSING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
