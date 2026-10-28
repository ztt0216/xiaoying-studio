import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * 生成模板封面与演示上传素材（纯几何图形，无版权问题）。
 * 封面画出每个模板的成品版式，照片位数量与模板 requiredImageCount 一致；不绘制文字，不依赖字体。
 * 用法（在仓库根目录）：java tools/GenerateDemoImages.java
 */
public class GenerateDemoImages {

    static final int W = 600;
    static final int H = 800;

    public static void main(String[] args) throws Exception {
        File covers = new File("backend/src/main/resources/static/demo");
        File samples = new File("samples");
        covers.mkdirs();
        samples.mkdirs();

        write(postcard(), covers, "postcard-cover.png");
        write(polaroid(), covers, "polaroid-cover.png");
        write(triptych(), covers, "triptych-cover.png");
        write(petPortrait(), covers, "pet-cover.png");
        write(petDuo(), covers, "petduo-cover.png");
        write(xmasCard(), covers, "xmas-cover.png");
        write(newYearBanner(), covers, "newyear-cover.png");
        write(weddingAlbum(), covers, "wedding-cover.png");

        for (int i = 1; i <= 4; i++) {
            BufferedImage img = samplePhoto(800, 600, 0.1f + i * 0.2f, 100 + i);
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

    // ---------------- 各模板封面 ----------------

    /** 旅行明信片：横版卡片，左侧两张照片拼贴，右侧邮票、邮戳与书写线。 */
    static BufferedImage postcard() {
        BufferedImage img = canvas(new Color(0xC9D6DD), new Color(0xA9BCC6));
        Graphics2D g = gfx(img);
        g.rotate(Math.toRadians(-3), W / 2.0, H / 2.0);
        int cx = 40, cy = 230, cw = 520, ch = 350;
        card(g, cx, cy, cw, ch, new Color(0xFBF8F2), 6);
        drawPhoto(g, cx + 20, cy + 20, 150, ch - 40, 0.55f, 1);
        drawPhoto(g, cx + 178, cy + 20, 120, ch - 40, 0.08f, 2);
        // 邮票：齿边 + 小图
        int sx = cx + cw - 96, sy = cy + 24;
        g.setColor(Color.WHITE);
        g.fillRect(sx, sy, 72, 88);
        g.setColor(new Color(0xFBF8F2));
        for (int i = 0; i <= 8; i++) {
            g.fillOval(sx - 4 + i * 9, sy - 4, 8, 8);
            g.fillOval(sx - 4 + i * 9, sy + 84, 8, 8);
        }
        for (int i = 0; i <= 10; i++) {
            g.fillOval(sx - 4, sy - 4 + i * 9, 8, 8);
            g.fillOval(sx + 68, sy - 4 + i * 9, 8, 8);
        }
        // 邮票画面用纯色图形，避免被看成第三个照片位
        g.setColor(new Color(0xE9B44C));
        g.fillRect(sx + 8, sy + 8, 56, 72);
        g.setColor(new Color(0x3F7D74));
        g.fillPolygon(new int[]{sx + 8, sx + 30, sx + 46, sx + 64, sx + 64, sx + 8},
                new int[]{sy + 70, sy + 40, sy + 56, sy + 34, sy + 80, sy + 80}, 6);
        // 邮戳
        g.setStroke(new BasicStroke(3));
        g.setColor(new Color(60, 70, 90, 150));
        g.drawOval(sx - 58, sy + 30, 70, 70);
        for (int i = 0; i < 3; i++) {
            g.drawLine(sx - 120, sy + 52 + i * 12, sx - 60, sy + 52 + i * 12);
        }
        // 书写线
        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(0xB9B3A8));
        for (int i = 0; i < 4; i++) {
            int y = cy + 170 + i * 38;
            g.drawLine(cx + 320, y, cx + cw - 24, y);
        }
        g.dispose();
        return img;
    }

    /** 拍立得相框：一张白色相纸，底边留宽，略微倾斜。 */
    static BufferedImage polaroid() {
        BufferedImage img = canvas(new Color(0xD8C9B2), new Color(0xB9A486));
        Graphics2D g = gfx(img);
        g.rotate(Math.toRadians(-5), W / 2.0, H / 2.0);
        int pw = 380, ph = 460, px = (W - pw) / 2, py = (H - ph) / 2 - 10;
        card(g, px, py, pw, ph, Color.WHITE, 3);
        drawPhoto(g, px + 24, py + 24, pw - 48, pw - 48, 0.12f, 11);
        // 底边手写签名的示意线
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x6B7A8F));
        int by = py + pw + 30;
        g.drawArc(px + 110, by - 14, 40, 28, 200, 220);
        g.drawLine(px + 150, by, px + 260, by - 4);
        g.dispose();
        return img;
    }

    /** 三联画：三张竖幅照片挂在墙上并排。 */
    static BufferedImage triptych() {
        BufferedImage img = canvas(new Color(0xE6E1D8), new Color(0xD3CCBF));
        Graphics2D g = gfx(img);
        int pw = 150, ph = 400, gap = 24, x0 = (W - (pw * 3 + gap * 2)) / 2, y0 = 190;
        float[] hues = {0.58f, 0.62f, 0.66f};
        for (int i = 0; i < 3; i++) {
            int x = x0 + i * (pw + gap);
            card(g, x - 10, y0 - 10, pw + 20, ph + 20, new Color(0x2B2B2B), 2);
            drawPhoto(g, x, y0, pw, ph, hues[i], 20 + i);
        }
        // 地面线
        g.setColor(new Color(0xC2B9A9));
        g.fillRect(0, 690, W, H - 690);
        g.dispose();
        return img;
    }

    /** 萌宠肖像：画框 + 衬底 + 椭圆照片。 */
    static BufferedImage petPortrait() {
        BufferedImage img = canvas(new Color(0x3E4A5C), new Color(0x27303D));
        Graphics2D g = gfx(img);
        int fx = 95, fy = 120, fw = 410, fh = 540;
        card(g, fx, fy, fw, fh, new Color(0xB8925A), 4);
        g.setColor(new Color(0x9A7744));
        g.setStroke(new BasicStroke(4));
        g.drawRect(fx + 14, fy + 14, fw - 28, fh - 28);
        g.setColor(new Color(0xF2EBDD));
        g.fillRect(fx + 30, fy + 30, fw - 60, fh - 60);
        Shape oval = new Ellipse2D.Double(fx + 70, fy + 70, fw - 140, fh - 140);
        drawPhoto(g, oval, 0.08f, 31);
        g.setColor(new Color(0xB8925A));
        g.setStroke(new BasicStroke(5));
        g.draw(oval);
        g.dispose();
        return img;
    }

    /** 毛孩子合影：一张卡片上的两格方形照片。 */
    static BufferedImage petDuo() {
        BufferedImage img = canvas(new Color(0xCFE3D4), new Color(0xA8CBB2));
        Graphics2D g = gfx(img);
        int cw = 500, ch = 330, cx = (W - cw) / 2, cy = 230;
        card(g, cx, cy, cw, ch, Color.WHITE, 16);
        int s = 220;
        drawPhoto(g, new RoundRectangle2D.Double(cx + 20, cy + 20, s, s, 18, 18), 0.07f, 41);
        drawPhoto(g, new RoundRectangle2D.Double(cx + cw - 20 - s, cy + 20, s, s, 18, 18), 0.13f, 42);
        // 两个爪印作为点缀
        paw(g, cx + cw / 2 - 30, cy + ch - 46);
        paw(g, cx + cw / 2 + 30, cy + ch - 46);
        g.dispose();
        return img;
    }

    /** 圣诞贺卡：深绿底、红边贺卡、一张照片、雪点。 */
    static BufferedImage xmasCard() {
        BufferedImage img = canvas(new Color(0x1F4D3A), new Color(0x123326));
        Graphics2D g = gfx(img);
        Random r = new Random(7);
        g.setColor(new Color(255, 255, 255, 170));
        for (int i = 0; i < 70; i++) {
            int d = 3 + r.nextInt(6);
            g.fillOval(r.nextInt(W), r.nextInt(H), d, d);
        }
        int cw = 380, ch = 520, cx = (W - cw) / 2, cy = 140;
        card(g, cx, cy, cw, ch, new Color(0xFBF7EF), 8);
        g.setColor(new Color(0xB3261E));
        g.setStroke(new BasicStroke(8));
        g.drawRect(cx + 16, cy + 16, cw - 32, ch - 32);
        drawPhoto(g, new RoundRectangle2D.Double(cx + 50, cy + 60, cw - 100, 300, 14, 14), 0.02f, 51);
        // 冬青果
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(0x2E6B4F));
            g.fillOval(cx + cw / 2 - 40 + i * 24, cy + 400, 26, 16);
            g.setColor(new Color(0xB3261E));
            g.fillOval(cx + cw / 2 - 26 + i * 12, cy + 422, 12, 12);
        }
        g.dispose();
        return img;
    }

    /** 新年横幅：红色长幅，两张圆形照片，金色点缀。 */
    static BufferedImage newYearBanner() {
        BufferedImage img = canvas(new Color(0xF1E3C8), new Color(0xE2CCA2));
        Graphics2D g = gfx(img);
        int bw = 540, bh = 300, bx = (W - bw) / 2, by = 250;
        // 挂绳
        g.setColor(new Color(0x8A5A2B));
        g.setStroke(new BasicStroke(4));
        g.drawLine(bx + 60, by, W / 2, 150);
        g.drawLine(bx + bw - 60, by, W / 2, 150);
        card(g, bx, by, bw, bh, new Color(0xC0262D), 4);
        g.setColor(new Color(0xE8B64C));
        g.setStroke(new BasicStroke(5));
        g.drawRect(bx + 14, by + 14, bw - 28, bh - 28);
        int d = 200;
        for (int i = 0; i < 2; i++) {
            int x = bx + 60 + i * (bw - 120 - d);
            g.setColor(new Color(0xE8B64C));
            g.fillOval(x - 8, by + (bh - d) / 2 - 8, d + 16, d + 16);
            drawPhoto(g, new Ellipse2D.Double(x, by + (bh - d) / 2.0, d, d), 0.1f + i * 0.45f, 61 + i);
        }
        // 底部流苏
        for (int i = 0; i < 9; i++) {
            g.setColor(new Color(0xE8B64C));
            g.fillRect(bx + 30 + i * 60, by + bh, 6, 40);
        }
        g.dispose();
        return img;
    }

    /** 婚礼相册：摊开的相册，左页一张大图，右页上下两张。 */
    static BufferedImage weddingAlbum() {
        BufferedImage img = canvas(new Color(0xEADDE0), new Color(0xD5C1C6));
        Graphics2D g = gfx(img);
        int bw = 540, bh = 400, bx = (W - bw) / 2, by = 200;
        card(g, bx - 10, by - 10, bw + 20, bh + 20, new Color(0x7A5A63), 10);
        g.setColor(new Color(0xFCFAF7));
        g.fillRect(bx, by, bw / 2 - 2, bh);
        g.fillRect(bx + bw / 2 + 2, by, bw / 2 - 2, bh);
        // 书脊阴影
        g.setPaint(new GradientPaint(bx + bw / 2f - 30, 0, new Color(0, 0, 0, 0), bx + bw / 2f, 0, new Color(0, 0, 0, 60)));
        g.fillRect(bx + bw / 2 - 30, by, 30, bh);
        g.setPaint(new GradientPaint(bx + bw / 2f, 0, new Color(0, 0, 0, 60), bx + bw / 2f + 30, 0, new Color(0, 0, 0, 0)));
        g.fillRect(bx + bw / 2, by, 30, bh);
        drawPhoto(g, bx + 28, by + 36, bw / 2 - 64, bh - 72, 0.92f, 71);
        drawPhoto(g, bx + bw / 2 + 36, by + 36, bw / 2 - 64, bh / 2 - 50, 0.85f, 72);
        drawPhoto(g, bx + bw / 2 + 36, by + bh / 2 + 14, bw / 2 - 64, bh / 2 - 50, 0.05f, 73);
        g.dispose();
        return img;
    }

    // ---------------- 绘制工具 ----------------

    static BufferedImage canvas(Color top, Color bottom) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        g.setPaint(new GradientPaint(0, 0, top, 0, H, bottom));
        g.fillRect(0, 0, W, H);
        g.dispose();
        return img;
    }

    static Graphics2D gfx(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    /** 带柔和投影的卡片 */
    static void card(Graphics2D g, int x, int y, int w, int h, Color fill, int radius) {
        for (int i = 8; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 6));
            g.fillRoundRect(x - i + 4, y - i + 10, w + i * 2, h + i * 2, radius + i * 2, radius + i * 2);
        }
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, radius, radius);
    }

    static void drawPhoto(Graphics2D g, int x, int y, int w, int h, float hue, long seed) {
        g.drawImage(photo(w, h, hue, seed), x, y, null);
    }

    static void drawPhoto(Graphics2D g, Shape clip, float hue, long seed) {
        var b = clip.getBounds();
        Shape old = g.getClip();
        g.clip(clip);
        g.drawImage(photo(b.width, b.height, hue, seed), b.x, b.y, null);
        g.setClip(old);
    }

    static void paw(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(0xC9A27E));
        g.fillOval(cx - 12, cy - 4, 24, 20);
        g.fillOval(cx - 18, cy - 18, 9, 11);
        g.fillOval(cx - 7, cy - 24, 9, 11);
        g.fillOval(cx + 4, cy - 24, 9, 11);
        g.fillOval(cx + 12, cy - 18, 9, 11);
    }

    /** 抽象“照片”：渐变底 + 半透明几何形 */
    static BufferedImage photo(int w, int h, float hue, long seed) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
        g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue, 0.55f, 0.95f), w, h,
                Color.getHSBColor((hue + 0.15f) % 1f, 0.75f, 0.45f)));
        g.fillRect(0, 0, w, h);
        Random r = new Random(seed);
        for (int k = 0; k < 9; k++) {
            g.setColor(new Color(255, 255, 255, 40 + r.nextInt(90)));
            int d = 20 + r.nextInt(Math.max(1, Math.min(w, h) / 2));
            if (k % 2 == 0) {
                g.fillOval(r.nextInt(w) - d / 2, r.nextInt(h) - d / 2, d, d);
            } else {
                g.fillRoundRect(r.nextInt(w) - d / 2, r.nextInt(h) - d / 2, d, d / 2 + 10, 20, 20);
            }
        }
        g.dispose();
        return img;
    }

    /** 演示上传素材（与已提交的 samples/ 保持一致的旧参数） */
    static BufferedImage samplePhoto(int w, int h, float hue, long seed) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = gfx(img);
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

    static void write(BufferedImage img, File dir, String name) throws Exception {
        ImageIO.write(img, "png", new File(dir, name));
    }
}
