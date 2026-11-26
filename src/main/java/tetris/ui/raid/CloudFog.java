package tetris.ui.raid;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;
import tetris.ui.fx.StarfieldBackground;

/**
 * 보드 일부를 가리는 뭉게구름. 불투명한 속이 아래 블록을 확실히 가리고, 그 위에 몽글몽글한 원들이 겉모양을 만든다.
 * 원 모양은 한 번만 그려 두고 위치만 일렁인다. 같은 자리 구름은 늘 같은 모양이다. 연출이 낮으면 멈춘다
 */
final class CloudFog {
    private static final int SPRITE = 96;
    private static final Color CORE = new Color(214, 226, 250);
    private static final Color OUTLINE = new Color(40, 48, 96, 220);

    private BufferedImage shadow;
    private BufferedImage puff;
    private int frame;

    void tick() {
        frame++;
    }

    void paint(Graphics2D g, Rectangle play, int[] area, int cell, boolean colorBlind, float alpha) {
        if (area == null || area.length < 4 || cell <= 0) return;
        int x = play.x + area[1] * cell;
        int y = play.y + area[0] * cell;
        int w = Math.max(cell, area[3] * cell);
        int h = Math.max(cell, area[2] * cell);
        ensure();
        Puff[] puffs = puffs(area, cell);
        boolean drift = !StarfieldBackground.lowEffects();
        Graphics2D layer = (Graphics2D) g.create();
        try {
            // 영역이 아니라 보드에 맞춰 잘라서, 구름 가장자리가 영역 밖으로 살짝 부풀 수 있게 한다
            layer.clipRect(play.x, play.y, play.width, play.height);
            layer.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            layer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (alpha < 0.99f) layer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
            int drop = Math.max(2, cell / 5);
            for (Puff p : puffs) draw(layer, shadow, x + p.dx + wave(p, drift, cell), y + p.dy + drop, p.radius);
            // 속: 원 사이 틈으로 아래가 비치지 않게 안쪽을 불투명하게 채운다. 겉 원들이 덮어서 모서리는 둥글게 보인다
            int inset = Math.max(2, cell / 3);
            layer.setColor(CORE);
            layer.fillRoundRect(x + inset, y + inset, Math.max(1, w - inset * 2), Math.max(1, h - inset * 2), cell, cell);
            for (Puff p : puffs) draw(layer, puff, x + p.dx + wave(p, drift, cell), y + p.dy, p.radius);
            if (colorBlind) {
                layer.setColor(OUTLINE);
                layer.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        1f, new float[] {4f, 3f}, 0f));
                layer.drawRoundRect(x + 1, y + 1, Math.max(1, w - 3), Math.max(1, h - 3), 8, 8);
            }
        } finally {
            layer.dispose();
        }
    }

    private int wave(Puff p, boolean drift, int cell) {
        if (!drift) return 0;
        return (int) Math.round(Math.sin(frame * p.speed + p.phase) * Math.max(1, cell / 8.0));
    }

    private static void draw(Graphics2D g, BufferedImage image, int cx, int cy, int radius) {
        g.drawImage(image, cx - radius, cy - radius, radius * 2, radius * 2, null);
    }

    /** 칸마다 원 하나 + 위쪽 가장자리에 봉긋한 원 몇 개. 구름 자리로 씨앗을 정해 매번 같은 모양 */
    private static Puff[] puffs(int[] area, int cell) {
        int rows = Math.max(1, area[2]);
        int cols = Math.max(1, area[3]);
        Random random = new Random(area[0] * 7919L + area[1] * 104729L + rows * 31L + cols);
        int extra = cols + 1;
        Puff[] puffs = new Puff[rows * cols + extra];
        int i = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int dx = c * cell + cell / 2 + jitter(random, cell);
                int dy = r * cell + cell / 2 + jitter(random, cell);
                puffs[i++] = new Puff(dx, dy, radius(random, cell, 0.95), random);
            }
        }
        for (int k = 0; k < extra; k++) {
            int dx = (int) Math.round((double) k * cols * cell / Math.max(1, extra - 1)) + jitter(random, cell);
            int dy = cell / 3 + jitter(random, cell);
            puffs[i++] = new Puff(dx, dy, radius(random, cell, 0.8), random);
        }
        return puffs;
    }

    private static int jitter(Random random, int cell) {
        return (int) Math.round((random.nextDouble() - 0.5) * cell * 0.35);
    }

    private static int radius(Random random, int cell, double scale) {
        return Math.max(4, (int) Math.round(cell * scale * (0.85 + random.nextDouble() * 0.3)));
    }

    private void ensure() {
        if (puff != null) return;
        shadow = sprite(new float[] {0f, 0.6f, 1f}, new Color[] {
                new Color(150, 165, 222, 220), new Color(128, 146, 208, 150), new Color(120, 138, 204, 0)});
        puff = sprite(new float[] {0f, 0.55f, 0.8f, 1f}, new Color[] {
                new Color(255, 255, 255, 250), new Color(234, 242, 255, 240),
                new Color(218, 230, 252, 160), new Color(210, 224, 250, 0)});
    }

    private static BufferedImage sprite(float[] stops, Color[] colors) {
        BufferedImage image = new BufferedImage(SPRITE, SPRITE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float c = SPRITE / 2f;
            // 빛이 위에서 오는 것처럼 밝은 중심을 살짝 위로
            g.setPaint(new RadialGradientPaint(c, c, c, c, c * 0.8f, stops, colors,
                    java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fillOval(0, 0, SPRITE, SPRITE);
        } finally {
            g.dispose();
        }
        return image;
    }

    private record Puff(int dx, int dy, int radius, double phase, double speed) {
        Puff(int dx, int dy, int radius, Random random) {
            this(dx, dy, radius, random.nextDouble() * Math.PI * 2, 0.03 + random.nextDouble() * 0.03);
        }
    }
}
