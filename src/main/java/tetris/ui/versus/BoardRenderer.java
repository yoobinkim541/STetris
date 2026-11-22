package tetris.ui.versus;
import java.awt.Color;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import tetris.component.block.BlockType;
import tetris.ui.BlockPalette;
import tetris.feature.versus.api.VersusRules;

/** Shared local/P2P board drawing. Attack is a display value, never a generated BlockType. */
public final class BoardRenderer {
    private final BlockPalette palette = new BlockPalette();
    private final boolean colorBlind;
    /** true면 블록 글자를 클래식 게임 화면과 같은 모양으로 그린다 (레이드). 대전은 false */
    private final boolean classicCells;
    public BoardRenderer(boolean colorBlind) { this(colorBlind, false); }
    public BoardRenderer(boolean colorBlind, boolean classicCells) {
        this.colorBlind = colorBlind;
        this.classicCells = classicCells;
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
     * 클래식 게임 화면과 같은 착지 표시: 점선 착지 지점과 떨어지는 조각 아래 빛기둥 (레이드).
     * cells에는 떨어지는 조각이 합쳐져 있다. 착지 지점 위로 곧장 떨어지는 길은 비어 있으므로,
     * 착지 지점 칸에서 위로 올라가 처음 만나는 칸이 그 열에서 떨어지는 조각의 맨 아래 칸이다.
     * 조각과 겹치는 착지 칸은 그리지 않는다 (클래식처럼 조각이 위에 보인다).
     */
    public void paintLanding(Graphics2D g, int[][] cells, int[][] ghost, int x, int y, int size) {
        if (cells == null || ghost == null || size <= 0) return;
        int ghostTop = Integer.MAX_VALUE;
        int pieceBottom = Integer.MIN_VALUE;
        boolean[] columns = new boolean[cells.length == 0 ? 0 : cells[0].length];
        for (int r = 0; r < ghost.length && r < cells.length; r++) {
            for (int c = 0; c < ghost[r].length && c < columns.length; c++) {
                if (ghost[r][c] == 0) continue;
                ghostTop = Math.min(ghostTop, r);
                if (columns[c]) continue;
                columns[c] = true;
                int above = r - 1;
                while (above >= 0 && cells[above][c] == 0 && ghost[above][c] == 0) above--;
                if (above >= 0 && cells[above][c] != 0) pieceBottom = Math.max(pieceBottom, above);
            }
        }
        if (ghostTop == Integer.MAX_VALUE) return;
        if (!colorBlind && pieceBottom != Integer.MIN_VALUE && ghostTop > pieceBottom) {
            for (int c = 0; c < columns.length; c++) {
                if (!columns[c]) continue;
                tetris.ui.theme.BlockCells.paintBeam(g, x + c * size, y + (pieceBottom + 1) * size, y + ghostTop * size, size);
            }
        }
        for (int r = 0; r < ghost.length && r < cells.length; r++) {
            for (int c = 0; c < ghost[r].length && c < columns.length; c++) {
                if (ghost[r][c] == 0 || cells[r][c] != 0) continue;
                tetris.ui.theme.BlockCells.paintGhost(g, x + c * size, y + r * size, size, colorBlind);
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
        tetris.ui.theme.GemTiles.paint(g, x, y, size, Color.WHITE);
        if (size < 9) return;
        g.setFont(tetris.ui.theme.GameTheme.digits(Math.max(8, size * 2 / 3)));
        String text = String.valueOf(symbol);
        tetris.ui.theme.UiKit.drawInBox(g, text, new java.awt.Rectangle(x, y, size, size), tetris.ui.theme.UiKit.Align.CENTER);
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
        paintCell(g, value, x, y, size, true);
    }

    private void paintCell(Graphics2D g, int value, int x, int y, int size, boolean glyph) {
        if (size <= 0) return;
        if (value < 0 || value > VersusRules.ATTACK_CELL) throw new IllegalArgumentException("unknown cell");
        boolean attack = value == VersusRules.ATTACK_CELL;
        Color fill = value == 0 ? new Color(0x262A33) : attack ? new Color(0x929AA5)
            : palette.color(BlockType.values()[value-1],colorBlind);
        if (value == 0) {
            g.setColor(fill);
            g.fillRect(x, y, size, size);
            g.setColor(new Color(0x323744));
            g.drawRect(x, y, size - 1, size - 1);
            return;
        }
        if (classicCells && glyph && !attack) {
            // 클래식 게임 화면과 같은 보석 칸·글자 (BlockCells)
            tetris.ui.theme.BlockCells.paintBlock(g, x, y, size, fill,
                    String.valueOf(BlockType.values()[value - 1].letter()), palette.textColor(fill));
            return;
        }
        tetris.ui.theme.GemTiles.paint(g, x, y, size, fill);
        if (!glyph) return;
        if (attack) {
            g.setColor(Color.BLACK);
            int inset = Math.max(2,size/4);
            g.drawLine(x+inset,y+inset,x+size-inset-1,y+size-inset-1);
            g.drawLine(x+size-inset-1,y+inset,x+inset,y+size-inset-1);
        } else if (size >= 8) {
            g.setColor(palette.textColor(fill));
            g.setFont(tetris.ui.theme.GameTheme.text(Math.max(8, size * 2 / 3), false));
            String letter = String.valueOf(BlockType.values()[value - 1].letter());
            tetris.ui.theme.UiKit.drawInBox(g, letter, new java.awt.Rectangle(x, y, size, size), tetris.ui.theme.UiKit.Align.CENTER);
        }
    }

}
