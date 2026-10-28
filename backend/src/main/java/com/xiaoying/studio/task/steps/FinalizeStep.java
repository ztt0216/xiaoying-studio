package com.xiaoying.studio.task.steps;

import com.xiaoying.studio.asset.FileStorage;
import com.xiaoying.studio.task.PipelineStep;
import com.xiaoying.studio.task.StepFailedException;
import com.xiaoying.studio.task.TaskContext;
import com.xiaoying.studio.task.TaskStage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

/** 阶段三：把中间产物移动到 results/，生成可预览的结果地址。 */
@Component
public class FinalizeStep implements PipelineStep {

    private final FileStorage storage;

    public FinalizeStep(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public TaskStage stage() {
        return TaskStage.FINALIZE;
    }

    @Override
    public void execute(TaskContext ctx) throws StepFailedException {
        Path generated = ctx.generatedFile();
        if (generated == null || !Files.isRegularFile(generated)) {
            throw new StepFailedException("未找到生成产物");
        }
        String relative = "results/task-" + ctx.taskId() + ".png";
        try {
            Files.move(generated, storage.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StepFailedException("结果归档失败");
        }
        ctx.setResultUrl(FileStorage.urlOf(relative));
    }
}
