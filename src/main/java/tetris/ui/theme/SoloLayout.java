package tetris.ui.theme;

import java.awt.Rectangle;
import tetris.component.block.Block;
import tetris.feature.data.Board;

/**
 * 싱글 화면 배치. 왕관은 보드 위 여백에만 있고, 홀드·다음은 보드 좌우 탭이다.
 * 테스트는 이 사각형으로 픽셀을 읽는다.
 */
public final class SoloLayout {
    public final int cell;
    public final int width;
    public final int height;
    public final Rectangle board;
    public final Rectangle crown;
    public final Rectangle hold;
    public final Rectangle next;
    public final Rectangle effects;
    public final Rectangle guide;
    public final Rectangle score;
    public final Rectangle level;
    public final Rectangle lines;
    public final Rectangle time;
    public final Rectangle status;

    private SoloLayout(int cell, int width, int height, Rectangle board, Rectangle crown, Rectangle hold,
                       Rectangle next, Rectangle effects, Rectangle guide, Rectangle score, Rectangle level,
                       Rectangle lines, Rectangle time, Rectangle status) {
        this.cell = cell;
        this.width = width;
        this.height = height;
        this.board = board;
        this.crown = crown;
        this.hold = hold;
        this.next = next;
        this.effects = effects;
        this.guide = guide;
        this.score = score;
        this.level = level;
        this.lines = lines;
        this.time = time;
        this.status = status;
    }

    public static SoloLayout of(int cell) {
        int safe = Math.max(1, cell);
        int frame = GameTheme.snap8(Math.max(12, safe / 2));
        int gap = 8;
        int side = GameTheme.snap8(safe * 5 + frame);
        int crownH = GameTheme.snap8(Math.max(48, safe * 2));
        int bottomGap = 16;
        int guideH = GameTheme.snap8(Math.max(40, safe + 24));
        int width = side * 2 + safe * Board.WIDTH;
        int height = crownH + safe * Board.HEIGHT + bottomGap + guideH;
        Rectangle board = new Rectangle(side, crownH, safe * Board.WIDTH, safe * Board.HEIGHT);
        Rectangle crown = new Rectangle(board.x, 0, board.width, board.y);
        int tabW = Math.max(8, side - frame - gap);
        int labelH = Math.max(14, Math.min(20, safe * 2 / 3));

        // 좌측 칼럼: 홀드 탭 + 모드 캡슐 + 지속 효과 / 플레이 상태 카드
        Rectangle hold = new Rectangle(gap, board.y + labelH, tabW, GameTheme.snap8(safe * 4));
        int statusH = Math.max(22, Math.min(34, safe + 4));
        int statusY = hold.y + hold.height + labelH + 6;
        Rectangle status = new Rectangle(gap, statusY, tabW, statusH);
        int effectsTop = status.y + status.height + 10;
        int effectsBottom = board.y + board.height;
        Rectangle effects = new Rectangle(gap, effectsTop, tabW, Math.max(safe * 2, effectsBottom - effectsTop));

        // 우측 칼럼: 다음 탭 + 스탯 4캡슐 (레벨 / 점수 / 줄 / 시간)
        int rightX = board.x + board.width + frame;
        Rectangle next = new Rectangle(rightX, board.y + labelH, tabW, GameTheme.snap8(safe * 7));
        int statTop = next.y + next.height + 10;
        int statAvail = Math.max(64, (board.y + board.height) - statTop);
        int slotH = statAvail / 4;
        int statLabelH = Math.max(10, Math.min(14, slotH / 4));
        int capGap = 3;
        int capH = Math.max(16, Math.min(40, slotH - statLabelH - capGap - 4));
        int y0 = statTop + statLabelH + capGap;
        Rectangle level = new Rectangle(rightX, y0, tabW, capH);
        Rectangle score = new Rectangle(rightX, y0 + slotH, tabW, capH);
        Rectangle lines = new Rectangle(rightX, y0 + slotH * 2, tabW, capH);
        Rectangle time = new Rectangle(rightX, y0 + slotH * 3, tabW, capH);

        // 하단 통합 조작키 바 (양쪽 모서리 16px는 밤하늘 유지)
        Rectangle guide = new Rectangle(16, board.y + board.height + bottomGap,
                Math.max(16, width - 32), Math.max(24, guideH - 8));
        return new SoloLayout(safe, width, height, board, crown, hold, next, effects, guide, score, level, lines, time, status);
    }

    public Rectangle boardCell(int row, int col) {
        return new Rectangle(board.x + col * cell, board.y + row * cell, cell, cell);
    }

    /** 다음 칸 상자 안에 조각을 가운데 둔 뒤, 모양의 (localRow, localCol) 칸 */
    public Rectangle centeredCell(Rectangle box, Block piece, int localRow, int localCol, int pieceCell) {
        if (piece == null || box.width <= 0 || box.height <= 0) return new Rectangle();
        int[][] shape = piece.getShape();
        int minRow = Integer.MAX_VALUE, maxRow = -1, minCol = Integer.MAX_VALUE, maxCol = -1;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 0) continue;
                minRow = Math.min(minRow, r);
                maxRow = Math.max(maxRow, r);
                minCol = Math.min(minCol, c);
                maxCol = Math.max(maxCol, c);
            }
        }
        if (maxRow < 0) return new Rectangle();
        int startX = box.x + (box.width - (maxCol - minCol + 1) * pieceCell) / 2;
        int startY = box.y + (box.height - (maxRow - minRow + 1) * pieceCell) / 2;
        return new Rectangle(startX + (localCol - minCol) * pieceCell, startY + (localRow - minRow) * pieceCell, pieceCell, pieceCell);
    }
}
