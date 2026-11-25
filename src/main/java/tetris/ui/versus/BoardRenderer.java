package tetris.ui.versus;
import java.awt.Color;
import java.awt.Graphics2D;
import tetris.component.block.BlockType;
import tetris.ui.BlockPalette;
import tetris.ui.theme.BlockCells;
import tetris.feature.versus.api.VersusRules;

/**
 * 대전·레이드 공용 보드 그리기. 블록과 착지 지점은 클래식 화면과 같은 {@link BlockCells}를 쓴다.
 * 공격 줄은 표시 값이고 블록 종류가 아니다.
 */
public final class BoardRenderer {
    private final BlockPalette palette = new BlockPalette();
    private final boolean colorBlind;
    public BoardRenderer(boolean colorBlind) {
        this.colorBlind = colorBlind;
    }
    public void paint(Graphics2D g, int[][] cells, int x, int y, int size) {
        Graphics2D board=(Graphics2D) g.create();
        try {
            for (int r=0;r<cells.length;r++) for (int c=0;c<cells[r].length;c++)
                paintCell(board,cells[r][c],x+c*size,y+r*size,size);
        } finally { board.dispose(); }
    }
    /** 보드 칸. 빈 칸은 짙은 남색과 점이고, 대기열은 {@link #paint}를 그대로 쓴다 */
    public void paintPlayfield(Graphics2D g, int[][] cells, int[][] items, int x, int y, int size) {
        if (cells.length == 0 || size <= 0) return;
        int cols = cells[0].length;
        g.setColor(new Color(0x0B1024));
        g.fillRect(x, y, cols * size, cells.length * size);
        g.setColor(new Color(0x8FB7D8));
        int dot = Math.max(1, size / 16);
        for (int r = 0; r < cells.length; r++) for (int c = 0; c < cells[r].length; c++) {
            if (cells[r][c] != 0) continue;
            tetris.ui.theme.UiKit.softDot(g, x + c * size + (size - dot) / 2, y + r * size + (size - dot) / 2,
                    Math.max(2, dot), new Color(0x8FB7D8));
        }
        for (int r = 0; r < cells.length; r++) for (int c = 0; c < cells[r].length; c++) {
            if (cells[r][c] == 0) continue;
            paintCell(g, cells[r][c], x + c * size, y + r * size, size);
        }
        if (items == null) return;
        for (int r = 0; r < items.length && r < cells.length; r++) {
            for (int c = 0; c < items[r].length && c < cols; c++) {
                if (items[r][c] != 0) paintItem(g, (char) items[r][c], x + c * size, y + r * size, size);
            }
        }
    }
    /**
     * 착지 지점: 떨어지는 조각과 같은 칸을 반투명으로 그린다 (클래식 화면과 같다).
     * ghost 칸 값은 블록 칸 값이고, 조각과 겹치는 칸은 조각이 위에 보이도록 건너뛴다.
     */
    public void paintLanding(Graphics2D g, int[][] cells, int[][] ghost, int x, int y, int size) {
        if (cells == null || ghost == null || size <= 0) return;
        for (int r = 0; r < ghost.length && r < cells.length; r++) {
            for (int c = 0; c < ghost[r].length && c < cells[r].length; c++) {
                int value = ghost[r][c];
                if (value <= 0 || value >= VersusRules.ATTACK_CELL || cells[r][c] != 0) continue;
                BlockType type = BlockType.values()[value - 1];
                Color fill = palette.color(type, colorBlind);
                BlockCells.paintGhost(g, x + c * size, y + r * size, size, fill, String.valueOf(type.letter()),
                        palette.textColor(fill), colorBlind);
            }
        }
    }
    /** items: cells와 같은 크기. 0이 아닌 칸은 아이템 문자(L·B·D·S)를 흰 바탕에 그린다 (R3-ITEM) */
    public void paint(Graphics2D g, int[][] cells, int[][] items, int x, int y, int size) {
        paint(g, cells, x, y, size);
        Graphics2D board=(Graphics2D) g.create();
        try {
            for (int r=0;r<items.length;r++) for (int c=0;c<items[r].length;c++)
                if (items[r][c] != 0) paintItem(board,(char) items[r][c],x+c*size,y+r*size,size);
        } finally { board.dispose(); }
    }
    private void paintItem(Graphics2D g, char symbol, int x, int y, int size) {
        // 아이템 칸은 클래식 화면과 같이 흰 칸에 검은 글자 (RND-8)
        BlockCells.paintBlock(g, x, y, size, Color.WHITE, String.valueOf(symbol), Color.BLACK);
    }
    public void paintFitted(Graphics2D g, int[][] shape, int x, int y, int width, int height) {
        if (shape == null || width <= 1 || height <= 1) return;
        tetris.ui.theme.PieceFit fit = tetris.ui.theme.PieceFit.of(shape, x, y, width, height, 0);
        if (fit.rows == 0) return;
        var clip = g.getClip();
        g.clipRect(x, y, width, height);
        try {
            for (int r = 0; r < shape.length; r++) {
                if (shape[r] == null) continue;
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] == 0) continue;
                    paintCell(g, shape[r][c],
                            fit.originX + (c - fit.minCol) * fit.cell,
                            fit.originY + (r - fit.minRow) * fit.cell, fit.cell);
                }
            }
        } finally {
            g.setClip(clip);
        }
    }

    public void paintCell(Graphics2D g, int value, int x, int y, int size) {
        if (size <= 0) return;
        if (value < 0 || value > VersusRules.ATTACK_CELL) throw new IllegalArgumentException("unknown cell");
        if (value == 0) {
            g.setColor(new Color(0x262A33));
            g.fillRect(x, y, size, size);
            g.setColor(new Color(0x323744));
            g.drawRect(x, y, size - 1, size - 1);
            return;
        }
        if (value == VersusRules.ATTACK_CELL) {
            tetris.ui.theme.GemTiles.paint(g, x, y, size, new Color(0x929AA5));
            g.setColor(Color.BLACK);
            int inset = Math.max(2, size / 4);
            g.drawLine(x + inset, y + inset, x + size - inset - 1, y + size - inset - 1);
            g.drawLine(x + size - inset - 1, y + inset, x + inset, y + size - inset - 1);
            return;
        }
        // 보스 레이드와 같은 보석 칸·글자 (BlockCells)
        BlockType type = BlockType.values()[value - 1];
        Color fill = palette.color(type, colorBlind);
        BlockCells.paintBlock(g, x, y, size, fill, String.valueOf(type.letter()), palette.textColor(fill));
    }

}
