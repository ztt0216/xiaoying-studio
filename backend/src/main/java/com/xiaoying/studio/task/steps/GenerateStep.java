package com.xiaoying.studio.task.steps;

import com.xiaoying.studio.asset.FileStorage;
import com.xiaoying.studio.task.PipelineStep;
import com.xiaoying.studio.task.StepFailedException;
import com.xiaoying.studio.task.TaskContext;
import com.xiaoying.studio.task.TaskInputRow;
import com.xiaoying.studio.task.TaskStage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * 阶段二：模拟生成。不调用 AI，只把用户素材按模板排成一张拼贴图写到 work/ 目录，
 * 让结果确实来自本次上传的素材。只做缩放与绘制矩形，不依赖字体，适合无头容器。
 */
@Component
public class GenerateStep implements PipelineStep {

    static final int CELL = 480;
    static final int GAP = 24;

    private final FileStorage storage;

    public GenerateStep(FileStorage storage) {
        this.storage = storage;
    }

    @Override
    public TaskStage stage() {
        return TaskStage.GENERATE;
    }

    @Override
    public void execute(TaskContext ctx) throws StepFailedException {
        int n = ctx.inputs().size();
        BufferedImage canvas = new BufferedImage(GAP + n * (CELL + GAP), CELL + GAP * 2 + 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(themeColor(ctx.template().id()));
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            for (TaskInputRow input : ctx.inputs()) {
                BufferedImage img = read(storage.resolve(input.storagePath()), input.slotIndex());
                int x = GAP + input.slotIndex() * (CELL + GAP);
                g.setColor(Color.WHITE);
                g.fillRect(x - 6, GAP - 6, CELL + 12, CELL + 12);
                drawCover(g, img, x, GAP);
            }
            // 底部装饰条，作为“生成”的视觉标记
            g.setColor(new Color(255, 255, 255, 200));
            g.fillRoundRect(GAP, CELL + GAP + 16, canvas.getWidth() - GAP * 2, 16, 16, 16);
        } finally {
            g.dispose();
        }
        Path out = storage.resolve("work/task-" + ctx.taskId() + ".png");
        try {
            ImageIO.write(canvas, "png", out.toFile());
        } catch (IOException e) {
            throw new StepFailedException("生成结果写入失败");
        }
        ctx.setGeneratedFile(out);
    }

    private static BufferedImage read(Path file, int slot) throws StepFailedException {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) {
                throw new StepFailedException("第 " + (slot + 1) + " 张素材无法解码");
            }
            return img;
        } catch (IOException e) {
            throw new StepFailedException("第 " + (slot + 1) + " 张素材无法解码");
        }
    }

    /** 等比缩放并居中裁切到 CELL×CELL。 */
    private static void drawCover(Graphics2D g, BufferedImage img, int x, int y) {
        double scale = Math.max((double) CELL / img.getWidth(), (double) CELL / img.getHeight());
        int w = (int) Math.round(img.getWidth() * scale);
        int h = (int) Math.round(img.getHeight() * scale);
        Graphics2D clip = (Graphics2D) g.create(x, y, CELL, CELL);
        try {
            clip.drawImage(img, (CELL - w) / 2, (CELL - h) / 2, w, h, null);
        } finally {
            clip.dispose();
        }
    }

    private static Color themeColor(String templateId) {
        float hue = (templateId.hashCode() & 0xFFFF) / 65535f;
        return Color.getHSBColor(hue, 0.45f, 0.55f);
    }
}
