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
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 阶段三：把中间产物移动到 results/，记录结果地址（未签名路径，对外返回时再签名）。 */
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
        // 随机文件名，不能从任务 ID 推出
        String relative = "results/" + UUID.randomUUID() + ".png";
        try {
            Files.move(generated, storage.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StepFailedException("结果归档失败");
        }
        ctx.setResultUrl(FileStorage.urlOf(relative));
    }
}
