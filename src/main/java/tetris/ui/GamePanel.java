package tetris.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.Timer;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.PlayFx;
import tetris.ui.theme.UiKit;
import tetris.component.block.Block;
import tetris.component.block.BlockType;
import tetris.feature.data.Board;
import tetris.feature.game.GameListener;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.setting.Settings;

/**
 * 게임 화면: Board, Next, Score, Level, GameState, Key Guide. 기존 BoardScreen 대체.
 * Pause 메뉴와 확인창은 이 패널의 자식 오버레이로 보드 위에 겹쳐 그린다 (명세 9: CardLayout만으로는 겹칠 수 없음).
 *
 * 배치 (c = 셀 크기, p = 여백 c/2): [p][보드 10c][p][정보 6c][p] x [p][보드 20c][p]
 */
public class GamePanel extends JPanel implements GameListener {
    private static final int INFO_COLUMNS = 6;
    private static final int NEXT_BOX_ROWS = 4;

    private final BlockPalette palette = new BlockPalette();
    private final PausePanel pausePanel;
    private final Consumer<GameSnapshot> onGameOver;
    private ConfirmDialog confirmDialog; // 떠 있는 확인창, 없으면 null
    private GameSnapshot snapshot;
    private int cellSize;
    private boolean colorBlindMode;
    private String[] keyGuide = new String[0];
    private final PlayFx fx = new PlayFx();
    private final Timer fxTimer = new Timer(16, event -> {
        fx.tick();
        if (fx.active()) repaint();
    });
    private GameSnapshot previous;
    private long startedAt = -1;

    /** onGameOver: Game Over 화면으로 넘어가도록 화면 흐름에 알린다 */
    public GamePanel(Settings settings, PausePanel pausePanel, Consumer<GameSnapshot> onGameOver) {
        if (settings == null || pausePanel == null || onGameOver == null)
            throw new IllegalArgumentException("settings, pausePanel, onGameOver cannot be null");
        this.pausePanel = pausePanel;
        this.onGameOver = onGameOver;

        setLayout(null); // 오버레이는 doLayout에서 패널 전체 크기로 맞춘다
        setBackground(Theme.BACKGROUND);
        setFocusable(true);
        enableInputMethods(false); // 한글 IME가 게임 키 입력을 가로채지 않도록 (KEY-10)

        pausePanel.setVisible(false);
        add(pausePanel);
        fxTimer.setRepeats(true);
        fxTimer.setCoalesce(true);
        setCellSize(settings.getScreenSize().cellPixels());
        applySettings(settings);
    }

    /** 셀 크기에 맞는 패널 크기 (창 크기 계산·자동 축소용, RND-2·RND-3) */
    public static Dimension preferredSizeFor(int cellSize) {
        tetris.ui.theme.SoloLayout layout = tetris.ui.theme.SoloLayout.of(cellSize);
        return new Dimension(layout.width, layout.height);
    }

    /** 빈 칸 배경. 평평한 회색 대신 짙은 남색 */
    public static final Color BOARD_BG = new Color(0x0B1024);

    public int cellSize() {
        return cellSize;
    }

    public java.awt.Rectangle boardBounds() {
        return tetris.ui.theme.SoloLayout.of(cellSize).board;
    }

    public java.awt.Rectangle boardCellBounds(int row, int col) {
        return tetris.ui.theme.SoloLayout.of(cellSize).boardCell(row, col);
    }

    public java.awt.Rectangle nextBounds() {
        return firstNextBox();
    }

    /** 지금 그리는 다음 조각의 (localRow, localCol) 칸. 감사 테스트가 이 좌표를 읽는다 */
    public java.awt.Rectangle nextCellBounds(int localRow, int localCol) {
        Block next = snapshot == null ? null : snapshot.nextBlock();
        Rectangle box = firstNextBox();
        int pieceCell = tetris.ui.theme.PieceFit.cellSize(next == null ? null : next.getShape(), box.width, box.height, cellSize);
        return tetris.ui.theme.SoloLayout.of(cellSize).centeredCell(box, next, localRow, localCol, pieceCell);
    }

    public java.awt.Rectangle effectsBounds() {
        return tetris.ui.theme.SoloLayout.of(cellSize).effects;
    }

    public java.awt.Rectangle keyGuideBounds() {
        return tetris.ui.theme.SoloLayout.of(cellSize).guide;
    }

    @Override
    public void onChanged(GameSnapshot snapshot) {
        observe(snapshot);
        this.snapshot = snapshot;
        // 이 화면은 창을 띄울 때 메뉴 뒤에 숨은 채로 붙어 addNotify에서는 타이머가 시작되지 않는다.
        // 타이머가 멈춰 있으면 점수 표시가 0에서 올라가지 않으므로 게임 상태가 오면 켠다
        if (!fxTimer.isRunning()) fxTimer.start();
        updateOverlays();
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (isShowing() && !fxTimer.isRunning()) fxTimer.start();
    }

    @Override
    public void removeNotify() {
        fxTimer.stop();
        super.removeNotify();
    }

    @Override
    public void onGameOver(GameSnapshot snapshot) {
        this.snapshot = snapshot; // 마지막 정상 상태 유지 (SPN-4)
        updateOverlays();
        repaint();
        onGameOver.accept(snapshot);
    }

    /** 설정 변경 시 셀 크기·색맹 모드 다시 적용 */
    public void applySettings(Settings settings) {
        colorBlindMode = settings.isColorBlindMode();
        keyGuide = Messages.keyGuide(MainFrame.Screen.GAME, settings.getKeyMap()).split("\n");
        pausePanel.setKeyGuide(Messages.pauseKeyGuide(settings.getKeyMap()));
        repaint();
    }

    /** 실제로 그릴 셀 크기. 화면에 맞추느라 설정값보다 작을 수 있다 (RND-3) */
    public void setCellSize(int cellSize) {
        this.cellSize = cellSize;
        setPreferredSize(preferredSizeFor(cellSize));
        revalidate();
        repaint();
    }

    /** 확인창을 띄운다. 떠 있는 동안 Pause 메뉴는 숨겨 키가 겹치지 않게 한다 */
    public void showConfirm(ConfirmDialog dialog) {
        if (confirmDialog != null) remove(confirmDialog);
        confirmDialog = dialog;
        add(dialog, 0); // 맨 앞에 그린다
        updateOverlays();
        revalidate();
        repaint();
    }

    /** 확인창을 닫는다. 아직 PAUSED면 Pause 메뉴가 다시 보인다 */
    public void closeConfirm() {
        if (confirmDialog == null) return;
        remove(confirmDialog);
        confirmDialog = null;
        updateOverlays();
        repaint();
    }

    public boolean isConfirmShowing() {
        return confirmDialog != null;
    }

    // PAUSED이고 확인창이 없을 때만 Pause 메뉴를 보인다. 게임이 다시 돌면 확인창도 닫는다
    private void updateOverlays() {
        boolean paused = snapshot != null && snapshot.state() == GameState.PAUSED;
        if (!paused && confirmDialog != null) {
            remove(confirmDialog);
            confirmDialog = null;
        }
        boolean showPause = paused && confirmDialog == null;
        if (showPause && !pausePanel.isVisible()) pausePanel.resetSelection();
        pausePanel.setVisible(showPause);
    }

    @Override
    public void doLayout() {
        for (Component child : getComponents()) child.setBounds(0, 0, getWidth(), getHeight());
    }

    // ── 그리기 ───────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = Theme.smooth(g);
        tetris.ui.theme.SoloLayout layout = tetris.ui.theme.SoloLayout.of(cellSize);
        UiKit.paintNight(g2, getWidth(), getHeight());
        UiKit.paintMoon(g2, getWidth() - layout.crown.height, layout.cell / 2, layout.crown.height * 2 / 3);
        int shake = fx.shakeX();
        g2.translate(shake, 0);
        paintCrownAboveBoard(g2, layout);
        drawBoard(g2, layout.board.x, layout.board.y);
        paintBoardFrame(g2, layout.board);
        drawTSpin(g2, layout.board.x, layout.board.y);
        fx.paintSparks(g2);
        fx.paintPopup(g2, layout.board.x + layout.board.width / 2, layout.board.y + cellSize * 4, getHeight());
        g2.translate(-shake, 0);
        drawSideTabs(g2, layout);
        drawHud(g2, layout);
        drawKeyGuide(g2, layout);
    }

    /** 왕관은 보드 위 여백에만 그린다. 플레이 칸을 덮지 않는다 */
    private void paintCrownAboveBoard(Graphics2D g, tetris.ui.theme.SoloLayout layout) {
        var clip = g.getClip();
        g.clipRect(layout.crown.x, layout.crown.y, layout.crown.width, layout.crown.height);
        UiKit.paintCrown(g, layout.crown.x, layout.crown.y, layout.crown.width, layout.crown.height);
        g.setClip(clip);
    }

    private void paintBoardFrame(Graphics2D g, java.awt.Rectangle board) {
        tetris.ui.theme.SoloLayout layout = tetris.ui.theme.SoloLayout.of(cellSize);
        int margin = layout.board.x - (layout.hold.x + layout.hold.width);
        int thick = Math.max(14, margin - 2);
        var clip = g.getClip();
        java.awt.geom.Area outside = new java.awt.geom.Area(new java.awt.Rectangle(0, 0, getWidth(), getHeight()));
        outside.subtract(new java.awt.geom.Area(board));
        g.setClip(outside);
        UiKit.paintFrame(g, UiKit.Tone.CYAN, board.x - thick, board.y - thick, board.width + thick * 2, board.height + thick * 2);
        g.setClip(clip);
    }

    private void drawBoard(Graphics2D g, int x, int y) {
        int boardW = cellSize * Board.WIDTH;
        int boardH = cellSize * Board.HEIGHT;
        g.setColor(BOARD_BG);
        g.fillRect(x, y, boardW, boardH);
        g.setColor(new Color(0x8FB7D8));
        int dot = Math.max(1, cellSize / 16);
        for (int row = 0; row < Board.HEIGHT; row++) {
            for (int col = 0; col < Board.WIDTH; col++) {
                int cx = x + col * cellSize + (cellSize - dot) / 2;
                int cy = y + row * cellSize + (cellSize - dot) / 2;
                UiKit.softDot(g, cx, cy, Math.max(2, dot), new Color(0x8FB7D8));
            }
        }
        if (snapshot != null) {
            int[][] cells = snapshot.cells();
            for (int row = 0; row < cells.length; row++) {
                for (int col = 0; col < cells[row].length; col++) {
                    int value = cells[row][col];
                    if (value <= 0) continue;
                    tetris.feature.item.ItemType item = snapshot.lockedItems().get(new tetris.feature.item.Cell(row, col));
                    drawCell(g, x + col * cellSize, y + row * cellSize, cellSize, BlockType.values()[value - 1], item);
                }
            }
            for (var cell : snapshot.clearingCells()) {
                if (cell.row() < 0 || cell.row() >= Board.HEIGHT || cell.col() < 0 || cell.col() >= Board.WIDTH) continue;
                g.setColor(Color.WHITE);
                g.fillRect(x + cell.col() * cellSize, y + cell.row() * cellSize, cellSize, cellSize);
            }
            Block current = snapshot.currentBlock();
            if (current != null) {
                drawGhost(g, snapshot.ghostBlock(), x, y);
                drawLandingBeam(g, current, snapshot.ghostBlock(), x, y);
                int[][] shape = current.getShape();
                for (int r = 0; r < shape.length; r++) {
                    for (int c = 0; c < shape[r].length; c++) {
                        int row = current.getRow() + r;
                        if (shape[r][c] == 0 || row < 0) continue;
                        drawCell(g, x + (current.getCol() + c) * cellSize, y + row * cellSize, cellSize,
                                current.getType(), current.itemCellAt(r, c).orElse(null));
                    }
                }
            }
        }

    }



    // 가장 최근 Lock이 T-스핀이면 보드 위쪽에 표시한다 (TSP-4). 다음 Lock에서 판정이 바뀌면 사라진다
    private void drawTSpin(Graphics2D g, int x, int y) {
        if (snapshot == null || !snapshot.lastTSpin().isTSpin()) return;
        String text = snapshot.lastTSpin() == tetris.feature.rule.TSpinType.MINI ? "T-SPIN MINI" : "T-SPIN!";
        int fontSize = Math.max(15, cellSize * 4 / 5);
        int boardWidth = cellSize * Board.WIDTH;
        g.setFont(GameTheme.display(fontSize));
        g.setColor(Color.WHITE);
        UiKit.drawInBox(g, text, new Rectangle(x, y + cellSize, boardWidth, cellSize * 2), UiKit.Align.CENTER);
    }

    private void drawSideTabs(Graphics2D g, tetris.ui.theme.SoloLayout layout) {
        drawTabLabelAbove(g, "홀드", layout.hold);
        UiKit.paintTab(g, false, layout.hold.x, layout.hold.y, layout.hold.width, layout.hold.height);
        Rectangle holdInner = pieceBox(layout.hold);
        if (snapshot != null && snapshot.holdBlock() != null) {
            drawPiece(g, snapshot.holdBlock(), holdInner);
        } else if (snapshot == null || snapshot.mode() != tetris.feature.game.GameMode.ITEM) {
            g.setFont(Theme.font("비어 있음", Math.max(10, Math.min(13, cellSize * 2 / 5)), false));
            g.setColor(new Color(0x8EA0BE));
            UiKit.drawInBox(g, "홀드 없음", holdInner, UiKit.Align.CENTER);
        }

        drawTabLabelAbove(g, "다음", layout.next);
        UiKit.paintTab(g, true, layout.next.x, layout.next.y, layout.next.width, layout.next.height);
        Rectangle nextInner = pieceBox(layout.next);
        boolean modern = snapshot != null && snapshot.nextBlocks().size() > 1;
        if (modern) {
            int count = snapshot.nextBlocks().size();
            int gap = Math.max(4, cellSize / 4);
            int boxH = Math.max(8, (nextInner.height - gap * (count - 1)) / count);
            int y = nextInner.y;
            for (Block preview : snapshot.nextBlocks()) {
                drawPiece(g, preview, new Rectangle(nextInner.x, y, nextInner.width, boxH));
                y += boxH + gap;
            }
        } else if (snapshot != null && snapshot.nextBlock() != null) {
            drawPiece(g, snapshot.nextBlock(), nextInner);
        }

        drawStatus(g, layout);
        drawLeftPanel(g, layout);
    }

    private void drawLeftPanel(Graphics2D g, tetris.ui.theme.SoloLayout layout) {
        Rectangle box = layout.effects;
        if (box.width < 12 || box.height < 16) return;
        // 다크 글래스 카드 배경 (노란색 금지: EffectsPanelTest가 효과가 없을 때 ACCENT 픽셀 0을 검증함)
        g.setColor(new Color(8, 12, 28, 195));
        g.fillRoundRect(box.x, box.y, box.width, box.height, 12, 12);
        g.setColor(new Color(61, 224, 255, 85));
        g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 12, 12);

        if (snapshot != null && !snapshot.effects().isEmpty()) {
            int label = Math.max(11, Math.min(13, cellSize / 2));
            Graphics2D clip = (Graphics2D) g.create();
            try {
                clip.clipRect(box.x + 2, box.y + 2, Math.max(1, box.width - 4), Math.max(1, box.height - 4));
                drawEffects(clip, snapshot.effects(), box.x + 6, box.y + 6, Math.max(12, box.width - 12), label,
                        box.y + box.height - 6);
            } finally {
                clip.dispose();
            }
            return;
        }
        drawPlayInfoCard(g, box);
    }

    private void drawPlayInfoCard(Graphics2D g, Rectangle box) {
        int pad = Math.max(6, Math.min(10, box.width / 12));
        int innerW = Math.max(8, box.width - pad * 2);
        int headerSize = Math.max(10, Math.min(13, cellSize * 2 / 5));
        int rowLabelSize = Math.max(9, Math.min(11, cellSize / 3 + 1));
        int rowValSize = Math.max(10, Math.min(13, cellSize * 2 / 5));
        int y = box.y + pad;

        String title = colorBlindMode ? "색맹 설정"
                : (snapshot != null && snapshot.mode() == tetris.feature.game.GameMode.ITEM ? "아이템 정보" : "플레이 상태");
        g.setFont(Theme.font(title, headerSize, true));
        g.setColor(UiKit.CYAN);
        UiKit.drawInBox(g, title, new Rectangle(box.x + pad, y, innerW, headerSize + 4), UiKit.Align.CENTER);
        y += headerSize + 8;

        g.setColor(new Color(61, 224, 255, 60));
        g.drawLine(box.x + pad, y, box.x + box.width - pad, y);
        y += 10;

        String modeVal = snapshot == null ? "준비"
                : (snapshot.mode() == tetris.feature.game.GameMode.ITEM ? "아이템" : "클래식");
        String diffVal = snapshot == null ? "보통" : switch (snapshot.difficulty()) {
            case EASY -> "쉬움";
            case HARD -> "어려움";
            default -> "보통";
        };
        String thirdLabel;
        String thirdVal;
        if (colorBlindMode) {
            thirdLabel = "팔레트";
            thirdVal = "Okabe-Ito";
        } else if (snapshot != null && snapshot.mode() == tetris.feature.game.GameMode.ITEM) {
            thirdLabel = "다음 아이템";
            thirdVal = snapshot.nextItemLines() + "줄 후";
        } else {
            thirdLabel = "미리보기";
            thirdVal = snapshot != null && snapshot.nextBlocks().size() > 1
                    ? "NEXT " + snapshot.nextBlocks().size() : "NEXT 1";
        }
        String fourthLabel = "홀드";
        String fourthVal = snapshot != null && snapshot.holdBlock() != null ? "보관 중" : "대기";

        String[][] rows = {
                {"모드", modeVal},
                {"난이도", diffVal},
                {thirdLabel, thirdVal},
                {fourthLabel, fourthVal}
        };
        int blockH = rowLabelSize + rowValSize + 10;
        int avail = Math.max(blockH, box.y + box.height - pad - y);
        int rowStep = Math.min(blockH + 22, Math.max(blockH, avail / rows.length));
        for (int i = 0; i < rows.length; i++) {
            String[] row = rows[i];
            if (y + rowLabelSize + rowValSize > box.y + box.height - 4) break;
            g.setFont(Theme.font(row[0], rowLabelSize, false));
            g.setColor(new Color(0x9FB0CC));
            UiKit.drawInBox(g, row[0], new Rectangle(box.x + pad, y, innerW, rowLabelSize + 2), UiKit.Align.LEFT);
            g.setFont(Theme.font(row[1], rowValSize, false));
            g.setColor(Color.WHITE);
            UiKit.drawInBox(g, row[1], new Rectangle(box.x + pad, y + rowLabelSize + 3, innerW, rowValSize + 4), UiKit.Align.LEFT);
            if (i < rows.length - 1 && y + rowStep - 6 < box.y + box.height - 6) {
                g.setColor(new Color(61, 224, 255, 28));
                g.drawLine(box.x + pad, y + rowStep - 6, box.x + box.width - pad, y + rowStep - 6);
            }
            y += rowStep;
        }
    }

    private void drawHud(Graphics2D g, tetris.ui.theme.SoloLayout layout) {
        drawCapsule(g, layout.level, "레벨", String.valueOf(snapshot == null ? 1 : snapshot.level()));
        drawCapsule(g, layout.score, "점수", String.valueOf(snapshot == null ? 0 : fx.displayScore()));
        drawCapsule(g, layout.lines, "줄", String.valueOf(snapshot == null ? 0 : snapshot.lines()));
        drawCapsule(g, layout.time, "시간", elapsedText());
    }

    private void drawKeyGuide(Graphics2D g, tetris.ui.theme.SoloLayout layout) {
        Rectangle bar = layout.guide;
        if (bar.width < 16 || bar.height < 16) return;
        g.setColor(new Color(8, 12, 28, 210));
        g.fillRoundRect(bar.x, bar.y, bar.width, bar.height, 12, 12);
        g.setColor(new Color(61, 224, 255, 110));
        g.drawRoundRect(bar.x, bar.y, bar.width - 1, bar.height - 1, 12, 12);

        String[] lines = fullGuideLines();
        int padX = 12;
        int padY = 4;
        int innerH = Math.max(8, bar.height - padY * 2);
        int lineH = Math.max(8, innerH / lines.length);
        int fontSize = Math.max(10, Math.min(13, lineH - 4));
        g.setColor(new Color(0xE8EEF8));
        for (int i = 0; i < lines.length; i++) {
            g.setFont(Theme.font(lines[i], fontSize, false));
            UiKit.drawInBox(g, lines[i],
                    new Rectangle(bar.x + padX, bar.y + padY + i * lineH, Math.max(8, bar.width - padX * 2), lineH),
                    UiKit.Align.CENTER);
        }
    }

    private String[] fullGuideLines() {
        String move = keyGuide.length > 0 ? compactSpaces(keyGuide[0]) : "←/→ 이동";
        String soft = keyGuide.length > 1 ? compactSpaces(keyGuide[1]) : "↓ 소프트 드롭";
        String rot = expandTrailingKey(keyGuide.length > 2 ? compactSpaces(keyGuide[2]) : "↑ 회전 / Z", "반시계");
        String hard = expandTrailingKey(keyGuide.length > 3 ? compactSpaces(keyGuide[3]) : "Space 하드 드롭 / C", "홀드");
        String pause = keyGuide.length > 4 ? compactSpaces(keyGuide[4]) : "P 일시정지";
        String quit = keyGuide.length > 5 ? compactSpaces(keyGuide[5]) : "Q 게임 끝내기";
        return new String[] {
                move + "   " + rot + "   " + soft,
                hard + "   " + pause + "   " + quit
        };
    }

    private static String expandTrailingKey(String raw, String suffixLabel) {
        int slash = raw.lastIndexOf(" / ");
        if (slash <= 0 || slash + 3 >= raw.length()) return raw;
        return raw.substring(0, slash).trim() + "   " + raw.substring(slash + 3).trim() + " " + suffixLabel;
    }

    private static String compactSpaces(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s{2,}", " ");
    }

    private void drawTabLabelAbove(Graphics2D g, String label, Rectangle tab) {
        int labelH = Math.max(12, Math.min(16, cellSize / 2));
        g.setFont(Theme.font(label, labelH, true));
        g.setColor(new Color(0xD8E4F8));
        UiKit.drawInBox(g, label, new Rectangle(tab.x, Math.max(0, tab.y - labelH - 3), tab.width, labelH), UiKit.Align.CENTER);
    }

    private void drawStatus(Graphics2D g, tetris.ui.theme.SoloLayout layout) {
        if (layout.status.height < 8 || layout.status.width < 8) return;
        int labelH = Math.max(10, Math.min(13, cellSize * 2 / 5));
        g.setFont(Theme.font("모드", labelH, false));
        g.setColor(new Color(0x9FB0CC));
        UiKit.drawInBox(g, "모드",
                new Rectangle(layout.status.x, layout.status.y - labelH - 3, layout.status.width, labelH),
                UiKit.Align.CENTER);
        String text = statusText();
        Rectangle box = layout.status;
        UiKit.paintCapsule(g, box.x, box.y, box.width, box.height);
        Rectangle inner = UiKit.contentBox(UiKit.AssetSlot.CAPSULE, box);
        int size = fitCapsuleFont(g, text, Math.max(16, inner.width - 6), Math.max(10, Math.min(13, box.height - 10)), 9, false);
        g.setFont(Theme.font(text, size, false));
        g.setColor(Color.WHITE);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, box, text, UiKit.Align.CENTER);
    }

    private String statusText() {
        if (snapshot == null) return "준비";
        String mode = snapshot.mode() == tetris.feature.game.GameMode.ITEM ? "아이템" : "클래식";
        String diff = switch (snapshot.difficulty()) {
            case EASY -> "쉬움";
            case HARD -> "어려움";
            default -> "보통";
        };
        return mode + " · " + diff;
    }

    private void drawCapsule(Graphics2D g, Rectangle box, String label, String value) {
        if (box.width < 4 || box.height < 4) return;
        UiKit.paintCapsule(g, box.x, box.y, box.width, box.height);
        int labelH = Math.max(10, Math.min(13, cellSize * 2 / 5));
        int gap = 3;
        g.setFont(Theme.font(label, labelH, false));
        g.setColor(UiKit.CYAN);
        UiKit.drawInBox(g, label, new Rectangle(box.x, box.y - gap - labelH, box.width, labelH), UiKit.Align.CENTER);
        Rectangle inner = UiKit.contentBox(UiKit.AssetSlot.CAPSULE, box);
        int size = fitCapsuleFont(g, value, Math.max(16, inner.width - 8), Math.max(11, Math.min(18, box.height * 3 / 5)), 10, true);
        g.setFont(Theme.font(value, size, true));
        g.setColor(Color.WHITE);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, box, value, UiKit.Align.CENTER);
    }

    private static int fitCapsuleFont(Graphics2D g, String text, int maxWidth, int maxSize, int minSize, boolean bold) {
        for (int size = maxSize; size >= minSize; size--) {
            g.setFont(Theme.font(text, size, bold));
            if (GameTheme.width(g, text) <= maxWidth) return size;
        }
        return minSize;
    }

    private void drawPiece(Graphics2D g, Block piece, Rectangle box) {
        if (piece == null || box.width <= 1 || box.height <= 1) return;
        int[][] shape = piece.getShape();
        tetris.ui.theme.PieceFit fit = tetris.ui.theme.PieceFit.of(shape, box.x, box.y, box.width, box.height, cellSize);
        if (fit.rows == 0) return;
        var clip = g.getClip();
        g.clipRect(box.x, box.y, box.width, box.height);
        try {
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] == 0) continue;
                    drawCell(g, fit.originX + (c - fit.minCol) * fit.cell, fit.originY + (r - fit.minRow) * fit.cell,
                            fit.cell, piece.getType(), piece.itemCellAt(r, c).orElse(null));
                }
            }
        } finally {
            g.setClip(clip);
        }
    }

    private Rectangle pieceBox(Rectangle tab) {
        int inset = Math.max(8, UiKit.tabCorner(tab.width, tab.height) / 2);
        return new Rectangle(tab.x + inset, tab.y + inset, Math.max(1, tab.width - inset * 2),
                Math.max(1, tab.height - inset * 2));
    }

    private Rectangle firstNextBox() {
        tetris.ui.theme.SoloLayout layout = tetris.ui.theme.SoloLayout.of(cellSize);
        return pieceBox(layout.next);
    }

    private static final int GAUGE_SEGMENTS = 10; // 지속 효과의 최대 남은 블록 수

    /**
     * 지속 효과 섹션: 노란색으로 [이름  설명 ... N블록]과 그 아래 10칸 게이지를 그린다.
     * limitY(조작 안내 위쪽)를 넘으면 게이지는 빼고 글자 줄만 그려 안내를 덮지 않는다.
     */
    private void drawEffects(Graphics2D g, java.util.List<tetris.feature.item.ActiveEffect> effects, int x, int y,
                             int width, int labelSize, int limitY) {
        if (effects.isEmpty()) return;

        int step = labelSize + labelSize / 2;
        int barHeight = Math.max(3, labelSize / 3);
        int barGap = Math.max(2, labelSize / 4);
        int headerStep = labelSize + labelSize / 4; // 제목 줄은 여백을 줄여 큰 화면에서도 게이지가 들어가게 한다
        boolean gauge = y + headerStep + effects.size() * (step + barGap + barHeight) <= limitY;
        int indent = Math.max(6, labelSize * 2 / 3);
        int contentX = x + indent;
        int contentWidth = width - indent;
        int top = y;

        g.setFont(Theme.text(labelSize, true));
        g.setColor(Theme.DIM_TEXT);
        g.drawString(Messages.get("game.effects"), contentX, y += headerStep);

        g.setFont(Theme.text(labelSize, true));
        int tagWidth = 0;
        for (var effect : effects) tagWidth = Math.max(tagWidth, g.getFontMetrics().stringWidth(effectTag(effect)));
        for (var effect : effects) {
            y += step;
            g.setColor(Theme.ACCENT);
            g.setFont(Theme.text(labelSize, true));
            g.drawString(effectTag(effect), contentX, y);
            String remaining = remainingText(effect);
            g.drawString(remaining, x + width - g.getFontMetrics().stringWidth(remaining), y);
            g.setFont(Theme.text(labelSize, false));
            g.drawString(Messages.get("effect." + effect.type().name() + ".desc"), contentX + tagWidth + labelSize / 2, y);
            if (!gauge) continue;

            int gap = Math.max(1, labelSize / 6);
            int segment = (contentWidth - gap * (GAUGE_SEGMENTS - 1)) / GAUGE_SEGMENTS;
            int filled = Math.min(effect.remainingBlocks(), GAUGE_SEGMENTS);
            for (int i = 0; i < GAUGE_SEGMENTS; i++) {
                g.setColor(i < filled ? Theme.ACCENT : Theme.ACCENT.darker().darker());
                g.fillRect(contentX + i * (segment + gap), y + barGap, segment, barHeight);
            }
            y += barGap + barHeight;
        }

        // 섹션 왼쪽의 세로선으로 다른 정보와 구분한다
        g.setColor(Theme.ACCENT.darker().darker());
        int barTop = top + step / 3;
        g.fillRect(x, barTop, Math.max(2, labelSize / 5), y - barTop);
    }

    private static String effectTag(tetris.feature.item.ActiveEffect effect) {
        return Messages.get("effect." + effect.type().name() + ".tag");
    }

    // 남은 블록 수. 슬로우는 마지막 블록이 떨어지는 동안 0이라 "이번 블록"으로 보여준다
    private static String remainingText(tetris.feature.item.ActiveEffect effect) {
        return effect.remainingBlocks() == 0
                ? Messages.get("effect.thisBlock")
                : Messages.format("effect.blocks", effect.remainingBlocks());
    }

    // Next 블록을 값 있는 칸 기준으로 상자 가운데에 그린다
    private void drawNext(Graphics2D g, Block next, int boxX, int boxY, int boxWidth, int boxHeight) {
        int[][] shape = next.getShape();
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
        if (maxRow < 0) return;

        int startX = boxX + (boxWidth - (maxCol - minCol + 1) * cellSize) / 2;
        int startY = boxY + (boxHeight - (maxRow - minRow + 1) * cellSize) / 2;
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                if (shape[r][c] == 0) continue;
                drawCell(g, startX + (c - minCol) * cellSize, startY + (r - minRow) * cellSize, cellSize, next.getType(), next.itemCellAt(r, c).orElse(null));
            }
        }
    }

    /** 착지 미리보기. 색맹 모드는 점선과 대각선, 그 외는 청록 점선 윤곽 (GST-2) */
    private void drawGhost(Graphics2D g, Block ghost, int originX, int originY) {
        if (ghost == null) return;
        int[][] shape = ghost.getShape();
        Stroke previous = g.getStroke();
        if (!colorBlindMode) {
            float dash = Math.max(2f, cellSize / 4f);
            g.setStroke(new BasicStroke(Math.max(1.5f, cellSize / 8f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    1f, new float[] {dash, dash}, 0f));
            g.setColor(UiKit.CYAN);
        }
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                int row = ghost.getRow() + r;
                if (shape[r][c] == 0 || row < 0) continue;
                int x = originX + (ghost.getCol() + c) * cellSize;
                int y = originY + row * cellSize;
                if (colorBlindMode) {
                    tetris.ui.fx.A11yColors.paintGhostCell(g, x, y, cellSize, true);
                } else {
                    int inset = Math.max(2, cellSize / 8);
                    g.drawRect(x + inset, y + inset, Math.max(1, cellSize - inset * 2 - 1), Math.max(1, cellSize - inset * 2 - 1));
                }
            }
        }
        g.setStroke(previous);
    }

    /** 떨어지는 조각 아래 빛기둥. 칸 가운데만 칠해 감사 픽셀을 피한다 */
    private void drawLandingBeam(Graphics2D g, Block current, Block ghost, int originX, int originY) {
        if (colorBlindMode || current == null || ghost == null) return;
        int top = current.getRow();
        int[][] shape = current.getShape();
        int bottomRow = top;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) if (shape[r][c] != 0) bottomRow = Math.max(bottomRow, top + r);
        }
        int ghostTop = ghost.getRow();
        if (ghostTop <= bottomRow) return;
        Composite previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        g.setColor(UiKit.CYAN);
        int beam = Math.max(2, cellSize / 5);
        for (int c = 0; c < shape[0].length; c++) {
            boolean used = false;
            for (int r = 0; r < shape.length; r++) if (shape[r][c] != 0) used = true;
            if (!used) continue;
            int cx = originX + (current.getCol() + c) * cellSize + (cellSize - beam) / 2;
            int y0 = originY + (bottomRow + 1) * cellSize;
            int y1 = originY + ghostTop * cellSize;
            if (y1 > y0) g.fillRect(cx, y0, beam, y1 - y0);
        }
        g.setComposite(previous);
    }

    private int drawHold(Graphics2D g, Block hold, int x, int y, int width, int labelSize) {
        y = drawLabel(g, Messages.get("game.hold"), x, y, labelSize);
        int boxHeight = cellSize * 2;
        g.setColor(Theme.PANEL);
        g.fillRect(x, y, width, boxHeight);
        UiKit.paintTab(g, false, x, y, width, boxHeight);
        if (hold != null) drawNext(g, hold, x, y, width, boxHeight);
        return y + boxHeight + cellSize / 4;
    }

    /** 현재 블록·고정 블록·Next 공용: 색 + 문자를 항상 함께 그린다 (RND-4, RND-5) */
    private void drawCell(Graphics2D g, int x, int y, int size, BlockType type) {
        drawCell(g, x, y, size, type, null);
    }

    private void drawCell(Graphics2D g, int x, int y, int size, BlockType type, tetris.feature.item.ItemType item) {
        Color color = item == null ? palette.color(type, colorBlindMode) : Color.WHITE;
        tetris.ui.theme.GemTiles.paint(g, x, y, size, color);

        if (size < 8) return;
        g.setFont(Theme.blockLetter(Math.max(8, size * 3 / 5)));
        g.setColor(item == null ? palette.textColor(color) : Color.BLACK);
        String letter = item != null ? String.valueOf(item.symbol()) : String.valueOf(type.letter());
        UiKit.drawInBox(g, letter, new Rectangle(x, y, size, size), UiKit.Align.CENTER);
    }

    private static int drawLabel(Graphics2D g, String label, int x, int y, int size) {
        g.setFont(Theme.text(size, true));
        g.setColor(UiKit.CYAN);
        g.drawString(label, x, y + size);
        return y + size + size / 2;
    }

    private static int drawValue(Graphics2D g, String label, String value, int x, int y, int labelSize, int valueSize) {
        y = drawLabel(g, label, x, y, labelSize);
        boolean digits = Messages.get("game.score").equals(label) || Messages.get("game.level").equals(label)
                || Messages.get("game.lines").equals(label);
        g.setFont(digits ? GameTheme.digits(valueSize) : Theme.text(valueSize, true));
        g.setColor(Color.WHITE);
        g.drawString(value, x, y + valueSize);
        return y + valueSize + labelSize;
    }

    private String elapsedText() {
        long millis = startedAt < 0 ? 0 : Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        long seconds = millis / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void observe(GameSnapshot next) {
        if (next == null) return;
        fx.noteScore(next.score());
        if (startedAt < 0 && next.state() == GameState.PLAYING) startedAt = System.nanoTime();
        if (previous == null) {
            previous = next;
            return;
        }
        int cleared = clearingRows(next);
        boolean tSpin = next.lastTSpin() != null && next.lastTSpin().isTSpin();
        if (cleared > 0 && previous.clearingCells().isEmpty()) {
            int row = 0;
            for (var cell : next.clearingCells()) row += cell.row();
            row /= Math.max(1, next.clearingCells().size());
            fx.noteClear(cleared, tSpin, next.lastTSpin() == tetris.feature.rule.TSpinType.MINI,
                    cellSize / 2 + cellSize * Board.WIDTH / 2, cellSize / 2 + row * cellSize + cellSize / 2);
        } else if (cleared == 0 && previous.currentBlock() != null && next.currentBlock() != previous.currentBlock()) {
            fx.noteClear(0, false, false, 0, 0);
        }
        if (next.level() > previous.level()) fx.noteLevel(next.level());
        if (isHardDrop(previous, next) && previous.ghostBlock() != null) {
            fx.noteHardDrop(cellSize / 2 + previous.ghostBlock().getCol() * cellSize + cellSize / 2,
                    cellSize / 2 + previous.ghostBlock().getRow() * cellSize);
        }
        previous = next;
    }

    private static int clearingRows(GameSnapshot snapshot) {
        java.util.Set<Integer> rows = new java.util.HashSet<>();
        for (var cell : snapshot.clearingCells()) rows.add(cell.row());
        return rows.size();
    }

    private static boolean isHardDrop(GameSnapshot before, GameSnapshot after) {
        Block current = before.currentBlock();
        Block ghost = before.ghostBlock();
        if (current == null || ghost == null || ghost.getRow() < current.getRow() + 2) return false;
        Block spawned = after.currentBlock();
        return spawned == null || spawned.getRow() < current.getRow();
    }
}
