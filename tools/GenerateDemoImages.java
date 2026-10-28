import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * 生成模板封面与演示上传素材（纯几何图形，无版权问题）。
 * 用法：java tools/GenerateDemoImages.java
 */
public class GenerateDemoImages {

    public static void main(String[] args) throws Exception {
        File covers = new File("backend/src/main/resources/static/demo");
        File samples = new File("samples");
        covers.mkdirs();
        samples.mkdirs();
        String[] names = {"postcard", "polaroid", "triptych", "pet", "petduo", "xmas", "newyear", "wedding"};
        for (int i = 0; i < names.length; i++) {
            ImageIO.write(draw(600, 800, i * 0.125f, i), "png", new File(covers, names[i] + "-cover.png"));
        }
        for (int i = 1; i <= 4; i++) {
            BufferedImage img = draw(800, 600, 0.1f + i * 0.2f, 100 + i);
            ImageIO.write(img, "png", new File(samples, "sample-" + i + ".png"));
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(img, 0, 0, null);
            ImageIO.write(rgb, "jpg", new File(samples, "sample-" + i + ".jpg"));
        }
        // 超过 2MB 的 PNG（噪点不可压缩），用于演示“萌宠肖像”模板的大小限制
        BufferedImage noise = new BufferedImage(1400, 1000, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(1);
        for (int y = 0; y < noise.getHeight(); y++) {
            for (int x = 0; x < noise.getWidth(); x++) {
                noise.setRGB(x, y, r.nextInt(0xFFFFFF));
            }
        }
        ImageIO.write(noise, "png", new File(samples, "too-large-4mb.png"));
        java.nio.file.Files.writeString(new File(samples, "not-an-image.png").toPath(), "this is plain text");
    }

    private static BufferedImage draw(int w, int h, float hue, long seed) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue, 0.55f, 0.95f), w, h,
                Color.getHSBColor((hue + 0.15f) % 1f, 0.75f, 0.45f)));
        g.fillRect(0, 0, w, h);
        Random r = new Random(seed);
        for (int k = 0; k < 9; k++) {
            g.setColor(new Color(255, 255, 255, 40 + r.nextInt(90)));
            int d = 60 + r.nextInt(Math.min(w, h) / 2);
            if (k % 2 == 0) {
                g.fillOval(r.nextInt(w) - d / 2, r.nextInt(h) - d / 2, d, d);
            } else {
                g.fillRoundRect(r.nextInt(w) - d / 2, r.nextInt(h) - d / 2, d, d / 2 + 20, 30, 30);
            }
        }
        g.dispose();
        return img;
    }
}
