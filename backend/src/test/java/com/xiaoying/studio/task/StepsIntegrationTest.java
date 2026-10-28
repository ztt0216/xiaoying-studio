package com.xiaoying.studio.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoying.studio.asset.FileStorage;
import com.xiaoying.studio.simulation.SimulationConfig;
import com.xiaoying.studio.task.steps.AssetCheckStep;
import com.xiaoying.studio.task.steps.FinalizeStep;
import com.xiaoying.studio.task.steps.GenerateStep;
import com.xiaoying.studio.template.TemplateRow;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 用真实的三个阶段实现 + 临时目录跑完整流水线。 */
class StepsIntegrationTest {

    @TempDir
    Path dir;

    private TaskPipeline pipeline(FileStorage storage, InMemoryRecorder recorder) {
        return new TaskPipeline(List.of(new AssetCheckStep(storage), new GenerateStep(storage),
                new FinalizeStep(storage)), recorder, ms -> { });
    }

    private TaskInputRow png(FileStorage storage, int slot) throws Exception {
        String rel = "uploads/s" + slot + ".png";
        ImageIO.write(new BufferedImage(64, 40, BufferedImage.TYPE_INT_RGB), "png", storage.resolve(rel).toFile());
        return new TaskInputRow(7L, slot, (long) slot, FileStorage.urlOf(rel), "image/png",
                Files.size(storage.resolve(rel)), rel);
    }

    @Test
    void producesPreviewableResult() throws Exception {
        FileStorage storage = new FileStorage(dir);
        TemplateRow template = new TemplateRow("t", "t", "c", "/c", "d", 2, "image/png", 1_000_000L);
        InMemoryRecorder recorder = new InMemoryRecorder();

        pipeline(storage, recorder).run(new TaskContext(7L, template, List.of(png(storage, 0), png(storage, 1))),
                SimulationConfig.NONE);

        assertThat(recorder.status).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(recorder.resultUrl).isEqualTo("/files/results/task-7.png");
        BufferedImage out = ImageIO.read(storage.resolve("results/task-7.png").toFile());
        assertThat(out.getWidth()).isGreaterThan(out.getHeight());
        assertThat(Files.exists(storage.resolve("work/task-7.png"))).isFalse();
    }

    @Test
    void missingFileFailsAtAssetCheck() throws Exception {
        FileStorage storage = new FileStorage(dir);
        TemplateRow template = new TemplateRow("t", "t", "c", "/c", "d", 1, "image/png", 1_000_000L);
        TaskInputRow input = png(storage, 0);
        Files.delete(storage.resolve(input.storagePath()));
        InMemoryRecorder recorder = new InMemoryRecorder();

        pipeline(storage, recorder).run(new TaskContext(7L, template, List.of(input)), SimulationConfig.NONE);

        assertThat(recorder.status).isEqualTo(TaskStatus.FAILED);
        assertThat(recorder.failedStage).isEqualTo(TaskStage.ASSET_CHECK);
        assertThat(recorder.errorMessage).contains("文件已丢失");
        assertThat(recorder.events).doesNotContain("enter:GENERATE");
    }
}
