package com.xiaoying.studio.simulation;

import com.xiaoying.studio.task.TaskStage;

/**
 * 模拟参数快照。任务投递时取一次快照，之后修改配置不影响已投递的任务，
 * 方便演示“A 任务失败、B 任务成功”这类多任务场景。
 *
 * @param failStage 为 null 时不注入失败
 */
public record SimulationConfig(long stepDelayMs, TaskStage failStage) {

    public static final SimulationConfig NONE = new SimulationConfig(0, null);
}
