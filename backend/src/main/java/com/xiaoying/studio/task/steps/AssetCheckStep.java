package com.xiaoying.studio.task.steps;

import com.xiaoying.studio.asset.FileStorage;
import com.xiaoying.studio.asset.ImageSniffer;
import com.xiaoying.studio.task.PipelineStep;
import com.xiaoying.studio.task.StepFailedException;
import com.xiaoying.studio.task.TaskContext;
import com.xiaoying.studio.task.TaskInputRow;
import com.xiaoying.studio.task.TaskStage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** 阶段一：再次核对素材（文件仍存在、数量、类型、大小），防止提交后文件被清理或篡改。 */
@Component
public class AssetCheckStep implements PipelineStep {

    private final FileStorage storage;

    public AssetCheckStep(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public TaskStage stage() {
        return TaskStage.ASSET_CHECK;
    }

    @Override
    public void execute(TaskContext ctx) throws StepFailedException {
        int required = ctx.template().requiredImageCount();
        if (ctx.inputs().size() != required) {
            throw new StepFailedException("素材数量不符：需要 " + required + " 张，实际 " + ctx.inputs().size() + " 张");
        }
        for (TaskInputRow input : ctx.inputs()) {
            String slot = "第 " + (input.slotIndex() + 1) + " 张素材";
            Path file = storage.resolve(input.storagePath());
            if (!Files.isRegularFile(file)) {
                throw new StepFailedException(slot + "文件已丢失");
            }
            try (InputStream in = Files.newInputStream(file)) {
                String mime = ImageSniffer.detect(in.readNBytes(16)).orElse(null);
                if (mime == null || !ctx.template().acceptedMimeTypeList().contains(mime)) {
                    throw new StepFailedException(slot + "类型不受该模板支持");
                }
                if (Files.size(file) > ctx.template().maxFileSizeBytes()) {
                    throw new StepFailedException(slot + "超过大小限制");
                }
            } catch (IOException e) {
                throw new StepFailedException(slot + "无法读取");
            }
        }
    }
}
