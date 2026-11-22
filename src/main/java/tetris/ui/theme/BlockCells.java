package tetris.ui.theme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;

/**
 * 플레이 보드의 블록 칸·착지 지점·빛기둥 그리기. 클래식(GamePanel)과 레이드가 같은 모양으로 보이도록 함께 쓴다.
 * 색맹 모드의 착지 지점은 {@code A11yColors.paintGhostCell}을 그대로 쓰고 빛기둥은 그리지 않는다.
 */
public final class BlockCells {
    private BlockCells() {
    }

    /** 보석 칸 위에 블록 글자. 칸이 8px보다 작으면 글자는 생략한다 */
    public static void paintBlock(Graphics2D g, int x, int y, int size, Color fill, String letter, Color textColor) {
        GemTiles.paint(g, x, y, size, fill);
        if (size < 8 || letter == null || letter.isEmpty()) return;
        g.setFont(GameTheme.text(Math.max(8, size * 3 / 5), false));
        g.setColor(textColor);
        UiKit.drawInBox(g, letter, new Rectangle(x, y, size, size), UiKit.Align.CENTER);
    }

    /** 착지 지점 한 칸. 하늘색 점선 사각형이고, 색맹 모드는 접근성 표시를 쓴다 (GST-2) */
    public static void paintGhost(Graphics2D g, int x, int y, int size, boolean colorBlind) {
        if (size <= 1) return;
        if (colorBlind) {
            tetris.ui.fx.A11yColors.paintGhostCell(g, x, y, size, true);
            return;
        }
        Stroke previous = g.getStroke();
        Color color = g.getColor();
        float dash = Math.max(2f, size / 4f);
        g.setStroke(new BasicStroke(Math.max(1.5f, size / 8f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[] {dash, dash}, 0f));
        g.setColor(UiKit.CYAN);
        int inset = Math.max(2, size / 8);
        g.drawRect(x + inset, y + inset, Math.max(1, size - inset * 2 - 1), Math.max(1, size - inset * 2 - 1));
        g.setStroke(previous);
        g.setColor(color);
    }

    /** 떨어지는 조각 아래에서 착지 지점까지 이어지는 빛기둥 한 줄. 칸 가운데만 칠한다 */
    public static void paintBeam(Graphics2D g, int cellX, int fromY, int toY, int size) {
        if (toY <= fromY || size <= 0) return;
        Composite previous = g.getComposite();
        Color color = g.getColor();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        g.setColor(UiKit.CYAN);
        int beam = Math.max(2, size / 5);
        g.fillRect(cellX + (size - beam) / 2, fromY, beam, toY - fromY);
        g.setComposite(previous);
        g.setColor(color);
    }
}
