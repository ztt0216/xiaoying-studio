package com.xiaoying.studio.task;

import com.xiaoying.studio.simulation.SimulationConfig;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 顺序执行 素材检查 → 模拟生成 → 结果整理。
 * 每个阶段：推进任务阶段 → 写入阶段记录(RUNNING) → 模拟耗时/注入失败 → 执行 → 结束阶段记录。
 * 任一阶段失败即停止，保存失败阶段与原因；全部成功后写入结果地址。
 */
@Component
public class TaskPipeline {

    /** 可替换的等待实现，测试中不真正 sleep。 */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final Logger log = LoggerFactory.getLogger(TaskPipeline.class);

    private final List<PipelineStep> steps;
    private final TaskProgressRecorder recorder;
    private final Sleeper sleeper;

    @Autowired
    public TaskPipeline(List<PipelineStep> steps, TaskProgressRecorder recorder) {
        this(steps, recorder, Thread::sleep);
    }

    public TaskPipeline(List<PipelineStep> steps, TaskProgressRecorder recorder, Sleeper sleeper) {
        // 按枚举声明顺序排列，并保证每个阶段恰好一个实现
        this.steps = steps.stream().sorted((a, b) -> a.stage().compareTo(b.stage())).toList();
        List<TaskStage> stages = this.steps.stream().map(PipelineStep::stage).toList();
        if (!stages.equals(List.of(TaskStage.values()))) {
            throw new IllegalStateException("流水线阶段配置不完整: " + stages);
        }
        this.recorder = recorder;
        this.sleeper = sleeper;
    }

    public void run(TaskContext ctx, SimulationConfig sim) {
        long taskId = ctx.taskId();
        for (PipelineStep step : steps) {
            TaskStage stage = step.stage();
            if (!recorder.enterStage(taskId, stage)) {
                log.warn("task {} is already finished, skip stage {}", taskId, stage);
                return;
            }
            long stepId = recorder.startStep(taskId, stage);
            try {
                simulate(stage, sim);
                step.execute(ctx);
                recorder.finishStep(stepId, true, null);
                log.info("task {} stage {} done", taskId, stage);
            } catch (StepFailedException e) {
                fail(taskId, stepId, stage, e.getMessage());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(taskId, stepId, stage, "任务执行被中断");
                return;
            } catch (RuntimeException e) {
                log.error("task {} stage {} crashed", taskId, stage, e);
                fail(taskId, stepId, stage, stage.label() + "阶段发生内部错误");
                return;
            }
        }
        recorder.markSucceeded(taskId, ctx.resultUrl());
    }

    private void simulate(TaskStage stage, SimulationConfig sim) throws InterruptedException, StepFailedException {
        if (sim.stepDelayMs() > 0) {
            sleeper.sleep(sim.stepDelayMs());
        }
        if (stage == sim.failStage()) {
            throw new StepFailedException("模拟失败：" + stage.label() + "未通过（由模拟配置触发）");
        }
    }

    private void fail(long taskId, long stepId, TaskStage stage, String message) {
        recorder.finishStep(stepId, false, message);
        recorder.markFailed(taskId, stage, message);
        log.info("task {} failed at {}: {}", taskId, stage, message);
    }
}
