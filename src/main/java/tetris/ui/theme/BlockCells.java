package tetris.ui.theme;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * 플레이 보드의 블록 칸과 착지 지점(고스트). 클래식·아이템(GamePanel), 대전·레이드(BoardRenderer)가
 * 모두 이 모양 하나를 쓴다. 블록은 보스 레이드의 보석 칸 + 글자, 고스트는 같은 블록을 반투명으로 그린다.
 */
public final class BlockCells {
    /** 고스트 불투명도. 아래 보드가 비쳐 보이고 고정된 칸과는 밝기로 구분된다 */
    public static final float GHOST_ALPHA = 0.5f;

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

    /**
     * 착지 지점 한 칸: 떨어지는 블록과 같은 칸을 반투명으로 그린다.
     * 색맹 모드는 색 밝기만으로 고정 칸과 구분하지 않도록 점선 윤곽과 대각선을 위에 덧그린다 (GST-2).
     */
    public static void paintGhost(Graphics2D g, int x, int y, int size, Color fill, String letter, Color textColor,
                                  boolean colorBlind) {
        if (size <= 1) return;
        Composite previous = g.getComposite();
        Color color = g.getColor();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, GHOST_ALPHA));
        try {
            paintBlock(g, x, y, size, fill, letter, textColor);
        } finally {
            g.setComposite(previous);
        }
        if (colorBlind) tetris.ui.fx.A11yColors.paintGhostCell(g, x, y, size, true);
        g.setColor(color);
    }
}
