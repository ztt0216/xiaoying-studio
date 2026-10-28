package com.xiaoying.studio.task;

/** 某个阶段的可预期失败；message 会作为面向用户的失败原因保存。 */
public class StepFailedException extends Exception {
    public StepFailedException(String message) {
        super(message);
    }
}
