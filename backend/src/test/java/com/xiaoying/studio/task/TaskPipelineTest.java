package com.xiaoying.studio.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xiaoying.studio.simulation.SimulationConfig;
import com.xiaoying.studio.template.TemplateRow;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskPipelineTest {

    private static final TemplateRow TEMPLATE = new TemplateRow("t1", "测试", "分类", "/c.png", "d", 1,
            "image/png", 1024L);

    private final List<String> executed = new ArrayList<>();
    private final List<Long> sleeps = new ArrayList<>();
    private InMemoryRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new InMemoryRecorder();
    }

    private PipelineStep step(TaskStage stage, String failWith) {
        return new PipelineStep() {
            @Override
            public TaskStage stage() {
                return stage;
            }

            @Override
            public void execute(TaskContext ctx) throws StepFailedException {
                executed.add(stage.name());
                if (failWith != null) {
                    throw new StepFailedException(failWith);
                }
                if (stage == TaskStage.FINALIZE) {
                    ctx.setResultUrl("/files/results/task-1.png");
                }
            }
        };
    }

    private TaskPipeline pipeline(PipelineStep... steps) {
        return new TaskPipeline(List.of(steps), recorder, sleeps::add);
    }

    private TaskContext ctx() {
        return new TaskContext(1L, TEMPLATE, List.of());
    }

    @Test
    void runsStagesInOrderAndRecordsResult() {
        // 故意乱序注册，流水线应按枚举顺序执行
        TaskPipeline p = pipeline(step(TaskStage.FINALIZE, null), step(TaskStage.ASSET_CHECK, null),
                step(TaskStage.GENERATE, null));

        p.run(ctx(), new SimulationConfig(500, null));

        assertThat(executed).containsExactly("ASSET_CHECK", "GENERATE", "FINALIZE");
        assertThat(recorder.events).containsExactly(
                "enter:ASSET_CHECK", "start:ASSET_CHECK", "ok:ASSET_CHECK",
                "enter:GENERATE", "start:GENERATE", "ok:GENERATE",
                "enter:FINALIZE", "start:FINALIZE", "ok:FINALIZE",
                "SUCCEEDED");
        assertThat(recorder.status).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(recorder.resultUrl).isEqualTo("/files/results/task-1.png");
        assertThat(sleeps).containsExactly(500L, 500L, 500L);
    }

    @Test
    void realStepFailureStopsLaterStages() {
        TaskPipeline p = pipeline(step(TaskStage.ASSET_CHECK, null), step(TaskStage.GENERATE, "素材无法解码"),
                step(TaskStage.FINALIZE, null));

        p.run(ctx(), SimulationConfig.NONE);

        assertThat(executed).containsExactly("ASSET_CHECK", "GENERATE");
        assertThat(recorder.status).isEqualTo(TaskStatus.FAILED);
        assertThat(recorder.failedStage).isEqualTo(TaskStage.GENERATE);
        assertThat(recorder.errorMessage).isEqualTo("素材无法解码");
        assertThat(recorder.resultUrl).isNull();
        assertThat(recorder.events).doesNotContain("enter:FINALIZE", "SUCCEEDED");
    }

    @Test
    void simulatedFailureAtEachStageStopsPipeline() {
        for (TaskStage target : TaskStage.values()) {
            executed.clear();
            recorder = new InMemoryRecorder();
            TaskPipeline p = pipeline(step(TaskStage.ASSET_CHECK, null), step(TaskStage.GENERATE, null),
                    step(TaskStage.FINALIZE, null));

            p.run(ctx(), new SimulationConfig(0, target));

            assertThat(recorder.status).isEqualTo(TaskStatus.FAILED);
            assertThat(recorder.failedStage).isEqualTo(target);
            assertThat(recorder.errorMessage).contains("模拟失败").contains(target.label());
            // 被注入失败的阶段本身不执行业务逻辑，之后的阶段也不再进入
            assertThat(executed).hasSize(target.ordinal());
            assertThat(recorder.events).last().isEqualTo("FAILED");
        }
    }

    @Test
    void unexpectedExceptionIsRecordedAsFailure() {
        PipelineStep crashing = new PipelineStep() {
            @Override
            public TaskStage stage() {
                return TaskStage.ASSET_CHECK;
            }

            @Override
            public void execute(TaskContext ctx) {
                throw new IllegalStateException("boom");
            }
        };
        pipeline(crashing, step(TaskStage.GENERATE, null), step(TaskStage.FINALIZE, null))
                .run(ctx(), SimulationConfig.NONE);

        assertThat(recorder.status).isEqualTo(TaskStatus.FAILED);
        assertThat(recorder.failedStage).isEqualTo(TaskStage.ASSET_CHECK);
        assertThat(recorder.errorMessage).doesNotContain("boom");
        assertThat(executed).isEmpty();
    }

    @Test
    void finishedTaskIsNotMovedBackToProcessing() {
        recorder.status = TaskStatus.SUCCEEDED;
        pipeline(step(TaskStage.ASSET_CHECK, null), step(TaskStage.GENERATE, null), step(TaskStage.FINALIZE, null))
                .run(ctx(), SimulationConfig.NONE);

        assertThat(recorder.status).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(executed).isEmpty();
        assertThat(recorder.events).containsExactly("skip:ASSET_CHECK");
    }

    @Test
    void rejectsIncompleteStageConfiguration() {
        assertThatThrownBy(() -> pipeline(step(TaskStage.ASSET_CHECK, null), step(TaskStage.FINALIZE, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
