package tetris.ui.raid;

import java.awt.AlphaComposite;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.JPanel;
import tetris.component.block.BlockType;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.CardId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.versus.api.VersusRules;
import tetris.ui.BlockPalette;
import tetris.ui.fx.A11yColors;
import tetris.ui.fx.AssetStore;
import tetris.ui.fx.FxLayer;
import tetris.ui.fx.StarfieldBackground;
import tetris.ui.theme.GameTheme;
import tetris.ui.theme.GemTiles;
import tetris.ui.theme.PieceFit;
import tetris.ui.theme.UiKit;
import tetris.ui.versus.BoardRenderer;

/**
 * 보스 레이드 화면. 턱 그림이 위를 덮고, 보드는 화려한 틀 안에서 폭을 채운다.
 * 600×720 가상 화면은 창과 같은 비라서 세 크기에서 겹침 없이 커진다.
 */
public final class RaidPanel extends JPanel {
    static final int VW = 600;
    static final int VH = 720;

    private static final Color TEXT = new Color(0xF4F7FF);
    private static final Color DIM = new Color(0xC9D2EA);
    private static final Color NIGHT = new Color(8, 12, 28, 210);

    private final BoardRenderer boards;
    private final boolean colorBlind;
    private final StarfieldBackground stars;
    private final FxLayer fx = new FxLayer();
    private final AssetStore assets = AssetStore.SHARED;
    private final RaidFrames frames = new RaidFrames(assets);
    private final BossMotion motion = new BossMotion();
    private final CloudFog fog = new CloudFog();
    private final Scene scene = new Scene();
    private RaidSnapshot snapshot;
    private String guide = "";
    private int cardSelection = 1;
    private int resultIndex;
    private boolean confirming;
    private boolean confirmYes;
    private String menuLabel = "메뉴로";
    private String retryLabel = "다시 하기";
    private String connectionNotice = "";
    private Runnable onMenu = () -> { };
    private Runnable onRetry = () -> { };
    private Runnable onCancelConfirm = () -> setConfirm(false, false);
    private BiConsumer<Integer, Boolean> cardPointer = (index, confirm) -> { };
    private long countdownAnchor = -1;
    private long lastRevision = Long.MIN_VALUE;
    private int stolenSlower = -1;
    private long stolenUntil;
    private final Rectangle bossBounds = new Rectangle();
    private double scale = 1;
    private int originX;
    private int originY;
    private Rectangle menuButton = new Rectangle();
    private Rectangle retryButton = new Rectangle();
    private final Rectangle cueTarget = new Rectangle();
    private final Rectangle cueLucky = new Rectangle();
    private final Rectangle cueScore = new Rectangle();
    private final BufferedImage[] fieldCache = new BufferedImage[2];
    private final int[] fieldKey = new int[2];
    private final Rectangle cueNext = new Rectangle();
    private int previewRow = -1;
    private boolean previewBomb;
    private BufferedImage virtual;
    private BufferedImage backdrop;
    private int backdropW;
    private int backdropH;
    private BufferedImage plate;
    private long plateStamp = Long.MIN_VALUE;
    private boolean restPose;

    public RaidPanel(boolean colorBlind) {
        this.colorBlind = colorBlind;
        boards = new BoardRenderer(colorBlind); // 블록·착지 표시는 모든 게임 화면이 같은 BlockCells를 쓴다
        fx.setColorBlind(colorBlind);
        setOpaque(true);
        setBackground(new Color(0x07091A));
        stars = new StarfieldBackground(this, AssetStore.BG_RAID);
        stars.onFrame(() -> {
            fx.tick();
            motion.tick();
            fog.tick();
        });
        MouseAdapter pointer = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { click(e.getX(), e.getY()); }
            @Override public void mouseMoved(MouseEvent e) { hover(e.getX(), e.getY()); }
            @Override public void mouseExited(MouseEvent e) { setCursor(Cursor.getDefaultCursor()); }
        };
        addMouseListener(pointer);
        addMouseMotionListener(pointer);
    }

    public void setSnapshot(RaidSnapshot snapshot) {
        RaidPhase previous = this.snapshot == null ? null : this.snapshot.phase();
        this.snapshot = snapshot;
        if (snapshot != null && snapshot.phase() == RaidPhase.COUNTDOWN && previous != RaidPhase.COUNTDOWN) {
            countdownAnchor = System.currentTimeMillis();
        }
        boolean changed = snapshot != null && snapshot.revision() != lastRevision;
        if (changed) {
            fx.absorb(snapshot.events());
            noteStolen(snapshot.events());
            lastRevision = snapshot.revision();
        }
        if (snapshot != null && snapshot.boss() != null) {
            var boss = snapshot.boss();
            motion.note(boss.id(), boss.level(), boss.hp(), boss.maxHp(), changed ? snapshot.events() : List.of());
        }
        repaint();
    }

    public void setGuide(String guide) {
        this.guide = guide == null ? "" : guide;
        repaint();
    }

    public void setCardSelection(int index) {
        cardSelection = Math.max(0, Math.min(2, index));
        repaint();
    }

    public int cardSelection() {
        return cardSelection;
    }

    /** 호버는 선택, 이미 고른 카드를 누르면 확정. 키 동작은 바꾸지 않는다 */
    public void setCardPointer(BiConsumer<Integer, Boolean> pointer) {
        cardPointer = pointer == null ? (index, confirm) -> { } : pointer;
    }

    public void setConfirm(boolean showing, boolean yes) {
        confirming = showing;
        confirmYes = yes;
        repaint();
    }

    public void setResultActions(Runnable menu, Runnable retry) {
        onMenu = menu == null ? () -> { } : menu;
        onRetry = retry == null ? () -> { } : retry;
    }

    public void setConfirmAction(Runnable cancel) {
        onCancelConfirm = cancel == null ? () -> setConfirm(false, false) : cancel;
    }

    public void setResultLabels(String menu, String retry) {
        menuLabel = menu == null || menu.isBlank() ? "메뉴로" : menu;
        retryLabel = retry == null || retry.isBlank() ? "다시 하기" : retry;
    }

    public void setConnectionNotice(String notice) {
        connectionNotice = notice == null ? "" : notice;
        repaint();
    }

    public void moveResult(int delta) {
        resultIndex = Math.floorMod(resultIndex + delta, 2);
        repaint();
    }

    public int resultIndex() {
        return resultIndex;
    }

    public void activateResult() {
        if (resultIndex == 0) onMenu.run();
        else onRetry.run();
    }

    /** 캡처가 연출 중간을 찍을 때. 보이는 동안의 타이머와 같은 한 박자 */
    public void advance(int frames) {
        for (int i = 0; i < Math.max(0, frames); i++) {
            fx.tick();
            motion.tick();
            fog.tick();
        }
    }

    public void previewLineClear() {
        previewRow = 17;
        previewBomb = false;
        fx.previewBurst(0, false);
    }

    public void previewBomb() {
        previewRow = -1;
        previewBomb = true;
        fx.previewBurst(0, true);
    }

    public Rectangle cueBounds(String name) {
        Rectangle source = switch (name) {
            case "target" -> cueTarget;
            case "lucky" -> cueLucky;
            case "next" -> cueNext;
            case "score" -> cueScore;
            default -> new Rectangle();
        };
        return new Rectangle(source);
    }

    public Rectangle bossBounds() {
        return new Rectangle(bossBounds);
    }

    public void click(int x, int y) {
        if (snapshot == null || scale <= 0) return;
        int vx = virtualX(x);
        int vy = virtualY(y);
        if (snapshot.phase() == RaidPhase.CARD_PICK) {
            int hit = cardAt(vx, vy);
            if (hit < 0) return;
            boolean confirm = hit == cardSelection;
            cardSelection = hit;
            cardPointer.accept(hit, confirm);
            repaint();
            return;
        }
        if (snapshot.phase() == RaidPhase.FINISHED) {
            if (menuButton.contains(vx, vy)) {
                resultIndex = 0;
                onMenu.run();
            } else if (retryButton.contains(vx, vy)) {
                resultIndex = 1;
                onRetry.run();
            }
            return;
        }
        if (confirming) {
            if (menuButton.contains(vx, vy)) onMenu.run();
            else if (retryButton.contains(vx, vy)) onCancelConfirm.run();
        }
    }

    private void hover(int x, int y) {
        if (snapshot == null || scale <= 0) return;
        int vx = virtualX(x);
        int vy = virtualY(y);
        boolean hand = false;
        if (snapshot.phase() == RaidPhase.CARD_PICK) {
            int hit = cardAt(vx, vy);
            hand = hit >= 0;
            if (hit >= 0 && hit != cardSelection) {
                cardSelection = hit;
                cardPointer.accept(hit, false);
                repaint();
            }
        } else if (snapshot.phase() == RaidPhase.FINISHED) {
            if (menuButton.contains(vx, vy)) {
                hand = true;
                if (resultIndex != 0) {
                    resultIndex = 0;
                    repaint();
                }
            } else if (retryButton.contains(vx, vy)) {
                hand = true;
                if (resultIndex != 1) {
                    resultIndex = 1;
                    repaint();
                }
            }
        } else if (confirming) {
            hand = menuButton.contains(vx, vy) || retryButton.contains(vx, vy);
        }
        setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    private int cardAt(int vx, int vy) {
        for (int i = 0; i < scene.cards.length; i++) {
            if (scene.cards[i].contains(vx, vy)) return i;
        }
        return -1;
    }
    private int virtualX(int x) {
        return (int) ((x - originX) / scale);
    }

    private int virtualY(int y) {
        return (int) ((y - originY) / scale);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (snapshot == null || getWidth() <= 0 || getHeight() <= 0) {
                if (!paintBackdrop(g, getWidth(), getHeight())) stars.paint(g, getWidth(), getHeight());
                return;
            }
            scale = Math.min(getWidth() / (double) VW, getHeight() / (double) VH);
            originX = (int) ((getWidth() - VW * scale) / 2);
            originY = (int) ((getHeight() - VH * scale) / 2);
            compose();
            ensurePlate();
            g.drawImage(plate, 0, 0, null);
            g.translate(originX, originY);
            g.scale(scale, scale);
            g.translate(fx.shakeX(), 0);
            paintScene(g);
        } finally {
            g.dispose();
        }
    }

    private boolean paintBackdrop(Graphics2D g, int width, int height) {
        if (width <= 0 || height <= 0) return false;
        if (backdrop != null && backdropW == width && backdropH == height) {
            g.drawImage(backdrop, 0, 0, null);
            return true;
        }
        BufferedImage wide = assets.cover(RaidArt.SCENE_WIDE, width, height);
        if (wide == null && assets.image(RaidArt.SCENE) == null) return false;
        BufferedImage built = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D layer = built.createGraphics();
        try {
            if (wide != null) layer.drawImage(wide, 0, 0, null);
            double fit = Math.min(width / (double) VW, height / (double) VH);
            int columnW = Math.max(1, (int) Math.round(VW * fit));
            int columnH = Math.max(1, (int) Math.round(VH * fit));
            BufferedImage sceneImage = assets.cover(RaidArt.SCENE, columnW, columnH);
            if (sceneImage != null) layer.drawImage(sceneImage, (width - columnW) / 2, (height - columnH) / 2, null);
            else if (wide == null) return false;
        } finally {
            layer.dispose();
        }
        backdrop = built;
        backdropW = width;
        backdropH = height;
        g.drawImage(backdrop, 0, 0, null);
        return true;
    }

    private void ensurePlate() {
        int width = getWidth();
        int height = getHeight();
        var boss = snapshot.boss();
        var boss2 = snapshot.mode() == RaidMode.RACE ? snapshot.bossFor(1) : null;
        long key = (((long) width) << 32) ^ height ^ boss.id().ordinal() ^ (boss.level() << 8)
                ^ (boss2 == null ? 0L : (((long) boss2.id().ordinal() << 24) ^ ((long) boss2.level() << 28)))
                ^ (snapshot.mode().ordinal() << 16) ^ scene.boss.width ^ (scene.frame[0].width << 12) ^ scene.frame[0].y;
        if (plate != null && plateStamp == key && plate.getWidth() == width && plate.getHeight() == height) return;
        plate = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D layer = plate.createGraphics();
        try {
            layer.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (!paintBackdrop(layer, width, height)) {
                layer.setColor(new Color(0x07091A));
                layer.fillRect(0, 0, width, height);
            }
            layer.translate(originX, originY);
            layer.scale(scale, scale);
            restPose = true;
            paintChrome(layer);
            restPose = false;
        } finally {
            layer.dispose();
        }
        plateStamp = key;
    }

    private void paintChrome(Graphics2D g) {
        if (snapshot.mode() == RaidMode.RACE) {
            for (int i = 0; i < 2; i++) drawLedge(g, snapshot.bossFor(i), scene.raceArt[i], true);
        } else {
            drawLedge(g, snapshot.boss(), scene.boss, false);
            paintHpArt(g);
        }
        for (int i = 0; i < scene.players; i++) {
            Rectangle frame = scene.frame[i];
            if (frame.width <= 0) continue;
            frames.paint(g, i, frame.x, frame.y, frame.width, frame.height);
            paintNameArt(g, i);
        }
    }

    private void paintHpArt(Graphics2D g) {
        Rectangle box = scene.hp;
        if (box.width <= 0) return;
        BufferedImage image = assets.hpFrame(box.width);
        if (image != null) g.drawImage(image, box.x, box.y, null);
        else UiKit.paintFrame(g, UiKit.Tone.VIOLET, box.x, box.y, box.width, box.height);
    }

    private void paintNameArt(Graphics2D g, int index) {
        Rectangle box = scene.header[index].width > 0 ? scene.header[index] : scene.score[index];
        if (box.width <= 0) return;
        RaidArt art = RaidArt.DATA;
        BufferedImage scaled = assets.scaled(art.tabPath(index), box.width, box.height);
        if (scaled != null) {
            g.drawImage(scaled, box.x, box.y, null);
            return;
        }
        BufferedImage image = assets.imageOrTint(art.tabPath(index), RaidArt.NAMETAB_P1, index == 0 ? 0f : 0.78f);
        if (image != null) g.drawImage(image, box.x, box.y, box.width, box.height, null);
        else UiKit.paintTab(g, index == 0, box.x, box.y, box.width, box.height);
    }

    private void paintRaceLive(Graphics2D g, int index) {
        Rectangle ped = scene.racePedestal[index];
        var boss = snapshot.bossFor(index);
        if (ped.width <= 0 || boss == null) return;
        Color accent = indexColor(index);
        g.setColor(new Color(8, 12, 28, 228));
        g.fillRoundRect(ped.x, ped.y, ped.width, ped.height, 10, 10);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 190));
        g.drawRoundRect(ped.x, ped.y, ped.width - 1, ped.height - 1, 10, 10);
        g.setStroke(new BasicStroke(1f));

        String bossLabel = boss.id().displayName() + " Lv." + boss.level();
        int pct = (int) Math.round(100.0 * boss.hp() / Math.max(1, boss.maxHp()));
        g.setFont(GameTheme.fontFor(bossLabel, 12, false));
        g.setColor(TEXT);
        UiKit.drawInBox(g, bossLabel, new Rectangle(ped.x + 8, ped.y + 4, ped.width - 54, 16), UiKit.Align.LEFT);
        g.setFont(GameTheme.digits(12));
        g.setColor(accent);
        UiKit.drawInBox(g, pct + "%", new Rectangle(ped.x + ped.width - 48, ped.y + 4, 40, 16), UiKit.Align.RIGHT);

        UiKit.paintGauge(g, ped.x + 8, ped.y + 23, Math.max(8, ped.width - 16), 10,
                boss.hp() / (float) Math.max(1, boss.maxHp()), A11yColors.hp(colorBlind));

        PatternId pattern = boss.nextPattern();
        String pText = pattern == null ? "패턴 없음" : pattern.displayName() + " · " + Math.max(0, boss.piecesLeft()) + "조각";
        g.setFont(GameTheme.fontFor(pText, 11, false));
        g.setColor(DIM);
        UiKit.drawInBox(g, pText, new Rectangle(ped.x + 8, ped.y + 37, ped.width - 16, 16), UiKit.Align.CENTER);

        paintUltHud(g, scene.raceGauge[index], snapshot.gaugeFor(index), accent);
    }

    @Override
    public void removeNotify() {
        stars.dispose();
        super.removeNotify();
    }

    private void paintScene(Graphics2D g) {
        cueTarget.setBounds(0, 0, 0, 0);
        cueLucky.setBounds(0, 0, 0, 0);
        cueNext.setBounds(0, 0, 0, 0);
        cueScore.setBounds(0, 0, 0, 0);
        menuButton.setBounds(0, 0, 0, 0);
        retryButton.setBounds(0, 0, 0, 0);
        for (Rectangle card : scene.cards) card.setBounds(0, 0, 0, 0);
        compose();
        if (snapshot.mode() == RaidMode.RACE) paintRace(g);
        else paintShared(g);
        paintGuide(g);
        paintNotice(g);
        int[] centers = new int[scene.players];
        int[] mids = new int[scene.players];
        for (int i = 0; i < scene.players; i++) {
            centers[i] = scene.play[i].x + scene.play[i].width / 2;
            mids[i] = scene.play[i].y + scene.play[i].height / 2;
        }
        fx.placeSparks(centers, mids);
        fx.paintSparks(g);
        if (scene.players > 0) fx.paintNumbers(g, centers, scene.play[0].y + 28);
        fx.paintBossNumbers(g, scene.boss.x + scene.boss.width / 2, scene.boss.y + scene.boss.height / 3);
        if (snapshot.phase() != RaidPhase.CARD_PICK && snapshot.phase() != RaidPhase.FINISHED && scene.banner.width > 0) {
            fx.paintBanner(g, VW / 2, scene.banner.y + scene.banner.height / 2);
        }
        paintStolen(g);
        if (snapshot.phase() == RaidPhase.COUNTDOWN) paintVeil(g, String.valueOf(countdownDigit()), "곧 시작합니다");
        if (snapshot.phase() == RaidPhase.PAUSED && !confirming) paintVeil(g, "일시정지", "일시정지 키로 다시 시작합니다");
        if (snapshot.phase() == RaidPhase.CARD_PICK && snapshot.offer() != null) paintCards(g);
        if (confirming) paintConfirm(g);
        if (snapshot.phase() == RaidPhase.FINISHED && snapshot.result() != null) paintResult(g);
        markBoss();
    }

    private void paintShared(Graphics2D g) {
        boolean covered = snapshot.phase() == RaidPhase.FINISHED && snapshot.result() != null;
        if (!covered) {
            paintHpFill(g, snapshot.boss());
            paintTelegraph(g, snapshot.boss());
            paintUltHud(g, scene.gauge, snapshot.gauge(), A11yColors.gauge(colorBlind));
        }
        for (int i = 0; i < scene.players; i++) paintPlayer(g, snapshot.players().get(i), i);
        if (!covered) paintTopCapsules(g, snapshot.boss());
    }

    private void paintRace(Graphics2D g) {
        for (int i = 0; i < 2; i++) {
            paintRaceLive(g, i);
            paintPlayer(g, snapshot.players().get(i), i);
        }
        paintRaceTop(g);
    }

    private void compose() {
        if (snapshot.mode() == RaidMode.RACE) {
            composeRace();
            return;
        }
        boolean solo = snapshot.mode() == RaidMode.SOLO;
        placeCapsules(4, 26);
        int band = 150;
        int ledgeY = scene.kill.y + scene.kill.height + 2;
        int ledgeW = Math.min(VW * 90 / 100, band * 1680 / 720);
        scene.boss.setBounds((VW - ledgeW) / 2, ledgeY, ledgeW, band);
        int hpW = 540;
        BufferedImage hp = assets.hpFrame(hpW);
        int hpH = hp == null ? 44 : hp.getHeight();
        int hpDrawW = hp == null ? hpW : hp.getWidth();
        int hpY = ledgeY + band - hpH + 22;
        scene.hp.setBounds((VW - hpDrawW) / 2, hpY, hpDrawW, hpH);
        int bannerW = 456;
        int bannerH = 28;
        int bannerY = hpY + hpH - 4;
        scene.banner.setBounds((VW - bannerW) / 2, bannerY, bannerW, bannerH);
        scene.level.setBounds(0, 0, 0, 0);
        scene.raceArt[0].setBounds(0, 0, 0, 0);
        scene.raceArt[1].setBounds(0, 0, 0, 0);
        scene.racePedestal[0].setBounds(0, 0, 0, 0);
        scene.racePedestal[1].setBounds(0, 0, 0, 0);
        scene.raceGauge[0].setBounds(0, 0, 0, 0);
        scene.raceGauge[1].setBounds(0, 0, 0, 0);
        hugBoards(bannerY + bannerH + 14, VH - guideReserve(), solo);
        if (solo) {
            int gx = scene.next[0].x;
            int gw = scene.next[0].width;
            int gy = scene.next[0].y + scene.next[0].height + 22;
            scene.gauge.setBounds(gx, gy, gw, 56);
        } else {
            int gy = scene.frame[0].y + scene.frame[0].height + 8;
            scene.gauge.setBounds(150, gy, 300, 26);
        }
        placeGuide();
    }

    private void composeRace() {
        scene.kill.setBounds(76, 4, 240, 26);
        scene.timer.setBounds(328, 4, 196, 26);
        scene.name.setBounds(0, 0, 0, 0);
        int frameW = 208;
        int leftX = 12;
        int rightX = VW - 12 - frameW;
        int artTop = 34;
        int artH = 108;
        int pedH = 58;
        scene.players = 2;
        int[] xs = {leftX, rightX};
        for (int i = 0; i < 2; i++) {
            scene.raceArt[i].setBounds(xs[i], artTop, frameW, artH);
            scene.racePedestal[i].setBounds(xs[i], artTop + artH - 4, frameW, pedH);
        }
        scene.boss.setBounds(scene.raceArt[0]);
        scene.hp.setBounds(0, 0, 0, 0);
        scene.banner.setBounds(0, 0, 0, 0);
        scene.gauge.setBounds(0, 0, 0, 0);
        scene.level.setBounds(0, 0, 0, 0);
        int frameTop = artTop + artH + pedH + 18;
        hugBoards(frameTop, VH - guideReserve(), false);
        for (int i = 0; i < 2; i++) {
            Rectangle f = scene.frame[i];
            scene.raceGauge[i].setBounds(f.x, f.y + f.height + 8, f.width, 26);
        }
        placeGuide();
    }

    /** 보드를 화려한 틀의 정중앙(4면 균등 여백)에 맞추고, 사이드 정보는 별도 칼럼으로 분리한다 */
    private void hugBoards(int frameTop, int frameBottom, boolean solo) {
        int columns = solo ? 1 : Math.min(2, snapshot.players().size());
        scene.players = columns;
        int margin = 14;
        if (solo) {
            int cell = 20;
            int boardW = cell * 10;
            int boardH = cell * 20;
            int frameW = boardW + margin * 2;
            int frameH = boardH + margin * 2;
            int availH = Math.max(frameH, frameBottom - frameTop);
            if (frameH > availH) {
                cell = Math.max(12, (availH - margin * 2) / 20);
                boardW = cell * 10;
                boardH = cell * 20;
                frameW = boardW + margin * 2;
                frameH = boardH + margin * 2;
            }
            scene.cell = cell;
            int frameX = (VW - frameW) / 2;
            int frameY = frameTop;
            scene.frame[0].setBounds(frameX, frameY, frameW, frameH);
            scene.play[0].setBounds(frameX + margin, frameY + margin, boardW, boardH);
            scene.header[0].setBounds(frameX, frameY - 14, 134, 30);

            int sideW = Math.max(96, frameX - 28);
            int leftX = 16;
            int rightX = frameX + frameW + 12;
            int colTop = frameY + 18;
            scene.hold[0].setBounds(leftX, colTop, sideW, 74);
            scene.score[0].setBounds(leftX, frameY + frameH - 48, sideW, 44);
            scene.effects[0].setBounds(leftX, colTop + 98, sideW, Math.max(48, scene.score[0].y - (colTop + 98) - 22));
            scene.next[0].setBounds(rightX, colTop, sideW, 206);
            clearPlayer(1);
            scene.boardY = scene.play[0].y;
            return;
        }

        int cell = 18;
        int boardW = cell * 10;
        int boardH = cell * 20;
        int frameW = boardW + margin * 2;
        int frameH = boardH + margin * 2;
        int maxFrameH = Math.max(240, frameBottom - frameTop - 36);
        if (frameH > maxFrameH) {
            cell = Math.max(12, (maxFrameH - margin * 2) / 20);
            boardW = cell * 10;
            boardH = cell * 20;
            frameW = boardW + margin * 2;
            frameH = boardH + margin * 2;
        }
        scene.cell = cell;
        int leftFrameX = 12;
        int rightFrameX = VW - 12 - frameW;
        int[] frameXs = {leftFrameX, rightFrameX};
        int centerLeft = leftFrameX + frameW + 8;
        int centerRight = rightFrameX - 8;
        int colGap = 6;
        int infoW = Math.max(48, (centerRight - centerLeft - colGap) / 2);
        int[] infoXs = {centerLeft, centerLeft + infoW + colGap};

        for (int i = 0; i < 2; i++) {
            if (i >= columns) {
                clearPlayer(i);
                continue;
            }
            int frameX = frameXs[i];
            int frameY = frameTop;
            scene.frame[i].setBounds(frameX, frameY, frameW, frameH);
            scene.play[i].setBounds(frameX + margin, frameY + margin, boardW, boardH);
            int tabW = 132;
            int tabH = 30;
            int tabX = i == 0 ? frameX : frameX + frameW - tabW;
            scene.header[i].setBounds(tabX, frameY - 14, tabW, tabH);
            layoutColumn(i, infoXs[i], frameY, infoW, frameH);
        }
        scene.boardY = scene.play[0].y;
    }

    private void layoutColumn(int index, int x, int y, int width, int height) {
        int labelH = 14;
        int holdY = y + labelH + 2;
        int holdH = 46;
        int nextY = holdY + holdH + labelH + 6;
        int nextH = Math.min(144, Math.max(96, height - 196));
        int scoreH = 30;
        int scoreY = y + height - scoreH;
        int effectY = nextY + nextH + labelH + 6;
        int effectH = Math.max(24, scoreY - effectY - labelH - 6);
        scene.hold[index].setBounds(x, holdY, width, holdH);
        scene.next[index].setBounds(x, nextY, width, nextH);
        scene.effects[index].setBounds(x, effectY, width, effectH);
        scene.score[index].setBounds(x, scoreY, width, scoreH);
    }

    private void placeCapsules(int y, int height) {
        int sideW = 148;
        int midW = 208;
        int gap = 8;
        int row = sideW * 2 + midW + gap * 2;
        int x = (VW - row) / 2;
        scene.kill.setBounds(x, y, sideW, height);
        scene.name.setBounds(x + sideW + gap, y, midW, height);
        scene.timer.setBounds(x + sideW + gap + midW + gap, y, sideW, height);
    }

    private int guideReserve() {
        int block = guideBlockHeight();
        return block == 0 ? 8 : block + 16;
    }

    private int guideBlockHeight() {
        if (guide.isEmpty() || snapshot.phase() == RaidPhase.FINISHED) return 0;
        int lines = guide.split("\\R").length;
        return lines * 24 + Math.max(0, lines - 1) * 4;
    }

    private void placeGuide() {
        int block = guideBlockHeight();
        if (block == 0) {
            scene.guide.setBounds(0, 0, 0, 0);
            return;
        }
        scene.guide.setBounds(8, VH - 8 - block, VW - 16, block);
    }

    private void clearPlayer(int index) {
        scene.play[index].setBounds(0, 0, 0, 0);
        scene.frame[index].setBounds(0, 0, 0, 0);
        scene.hold[index].setBounds(0, 0, 0, 0);
        scene.next[index].setBounds(0, 0, 0, 0);
        scene.score[index].setBounds(0, 0, 0, 0);
        scene.effects[index].setBounds(0, 0, 0, 0);
        scene.header[index].setBounds(0, 0, 0, 0);
    }

    private void paintPlayer(Graphics2D g, RaidSnapshot.PlayerView player, int index) {
        Rectangle play = scene.play[index];
        if (play.width <= 0) return;
        paintPlayfield(g, player, play, index);
        paintNameTab(g, player, index);
        paintSide(g, player, index);
        paintEffects(g, player, index);
        if (snapshot.mode() != RaidMode.SOLO && isTarget(index)) paintTarget(g, index);
        if (!player.alive()) paintDowned(g, player, play);
    }

    private void paintPlayfield(Graphics2D g, RaidSnapshot.PlayerView player, Rectangle play, int index) {
        var board = player.board();
        int key = System.identityHashCode(board.cells()) ^ (scene.cell << 16) ^ play.width ^ (play.height << 8);
        BufferedImage cached = fieldCache[index];
        if (cached == null || cached.getWidth() != play.width || cached.getHeight() != play.height || fieldKey[index] != key) {
            cached = new BufferedImage(Math.max(1, play.width), Math.max(1, play.height), BufferedImage.TYPE_INT_RGB);
            Graphics2D layer = cached.createGraphics();
            try {
                boards.paintPlayfield(layer, board.cells(), board.items(), 0, 0, scene.cell);
                boards.paintLanding(layer, board.cells(), board.ghost(), 0, 0, scene.cell);
            } finally {
                layer.dispose();
            }
            fieldCache[index] = cached;
            fieldKey[index] = key;
        }
        g.drawImage(cached, play.x, play.y, null);
        if (previewRow >= 0) {
            g.setColor(Color.WHITE);
            g.fillRect(play.x, play.y + previewRow * scene.cell, play.width, scene.cell);
        }
        if (previewBomb) {
            int cx = play.x + 4 * scene.cell;
            int cy = play.y + 17 * scene.cell;
            g.setColor(new Color(255, 170, 70, 90));
            g.fillOval(cx, cy, scene.cell * 3, scene.cell * 3);
        }
        paintLucky(g, player.lucky(), play);
        paintCloud(g, player, play);
    }

    private void paintNameTab(Graphics2D g, RaidSnapshot.PlayerView player, int index) {
        Rectangle tab = scene.header[index];
        if (tab.width > 0) {
            String mark = (colorBlind ? (index == 0 ? "■ " : "● ") : "") + (index == 0 ? "P1" : "P2");
            g.setFont(GameTheme.display(Math.max(13, Math.min(17, tab.height * 3 / 5))));
            g.setColor(index == 0 ? UiKit.CYAN : UiKit.MAGENTA);
            Rectangle hole = UiKit.contentBox(UiKit.AssetSlot.NAME, tab, index);
            UiKit.drawInBox(g, mark, hole, UiKit.Align.CENTER);
        }
        Rectangle score = scene.score[index];
        if (score.width <= 0) return;
        paintMiniLabel(g, "점수", score);
        paintMiniSlot(g, score, index == 0);
        String digits = String.valueOf(player.board().score());
        g.setFont(GameTheme.digits(Math.max(11, Math.min(18, score.height * 3 / 5))));
        g.setColor(TEXT);
        UiKit.drawInBox(g, digits, new Rectangle(score.x + 4, score.y + 2, Math.max(4, score.width - 8), Math.max(4, score.height - 4)), UiKit.Align.CENTER);
        if (index == 0) mark(cueScore, score.x + 1, score.y + 1, Math.max(1, score.width - 2), Math.max(1, score.height - 2));
    }

    private void paintSide(Graphics2D g, RaidSnapshot.PlayerView player, int index) {
        boolean sealed = player.effects().stream().anyMatch(effect -> effect.pattern() == PatternId.SEAL_PREVIEW);
        paintMiniLabel(g, "홀드", scene.hold[index]);
        paintMiniLabel(g, "다음", scene.next[index]);
        paintMiniSlot(g, scene.hold[index], index == 0);
        paintMiniSlot(g, scene.next[index], index == 0);
        Rectangle hold = scene.hold[index];
        if (hold.width > 0) {
            var held = player.board().hold();
            if (!sealed) paintPreviewPiece(g, held.length == 0 ? new int[0][] : held, hold);
            else paintLock(g, hold.x, hold.y, hold.width, hold.height);
        }
        Rectangle next = scene.next[index];
        if (next.width <= 0) return;
        java.util.ArrayList<int[][]> shapes = new java.util.ArrayList<>();
        if (player.board().previews().isEmpty()) shapes.add(player.board().next());
        else shapes.addAll(player.board().previews());
        shapes.addAll(player.extendedPreviews());
        Rectangle slot = new Rectangle(next.x + 2, next.y + 2, Math.max(1, next.width - 4), Math.max(1, next.height - 4));
        Rectangle[] pieces = previewSlots(slot, shapes);
        for (int i = 0; i < shapes.size(); i++) {
            Rectangle piece = pieces[i];
            if (sealed && i > 0) paintLock(g, piece.x, piece.y, piece.width, piece.height);
            else paintPreviewPiece(g, shapes.get(i), piece);
            if (i == 0 && index == 0) mark(cueNext, piece.x, piece.y, piece.width, piece.height);
        }
    }

    private void paintMiniLabel(Graphics2D g, String label, Rectangle slot) {
        if (slot.width <= 0) return;
        g.setFont(GameTheme.text(11, false));
        g.setColor(DIM);
        UiKit.drawInBox(g, label, new Rectangle(slot.x, slot.y - 14, slot.width, 13), UiKit.Align.CENTER);
    }

    private void paintMiniSlot(Graphics2D g, Rectangle slot, boolean cyan) {
        if (slot.width <= 0) return;
        g.setColor(NIGHT);
        g.fillRoundRect(slot.x, slot.y, slot.width, slot.height, 8, 8);
        Color border = cyan ? UiKit.CYAN : UiKit.MAGENTA;
        g.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 165));
        g.drawRoundRect(slot.x, slot.y, slot.width - 1, slot.height - 1, 8, 8);
    }

    private static Rectangle[] previewSlots(Rectangle slot, java.util.List<int[][]> shapes) {
        int n = shapes.size();
        Rectangle[] out = new Rectangle[n];
        if (n == 0) return out;
        if (n == 1) {
            out[0] = new Rectangle(slot);
            return out;
        }
        int gap = 2;
        boolean row = slot.width >= slot.height;
        int main = row ? slot.width : slot.height;
        int first = Math.max(1, main / 2);
        int remain = Math.max(1, main - first - gap * (n - 1));
        int each = Math.max(1, remain / (n - 1));
        if (row) {
            out[0] = new Rectangle(slot.x, slot.y, first, slot.height);
            int x = slot.x + first + gap;
            for (int i = 1; i < n; i++) {
                int w = i == n - 1 ? slot.x + slot.width - x : each;
                out[i] = new Rectangle(x, slot.y, Math.max(1, w), slot.height);
                x += w + gap;
            }
        } else {
            out[0] = new Rectangle(slot.x, slot.y, slot.width, first);
            int y = slot.y + first + gap;
            for (int i = 1; i < n; i++) {
                int h = i == n - 1 ? slot.y + slot.height - y : each;
                out[i] = new Rectangle(slot.x, y, slot.width, Math.max(1, h));
                y += h + gap;
            }
        }
        return out;
    }

    private void paintPreviewPiece(Graphics2D g, int[][] shape, Rectangle slot) {
        if (shape == null || slot.width <= 2 || slot.height <= 2) return;
        int inset = 2;
        // 클래식 게임 화면처럼 홀드·다음 블록도 보드 칸 크기를 넘지 않게 그린다 (칸에 꽉 차게 키우지 않음)
        PieceFit fit = PieceFit.of(shape, slot.x + inset, slot.y + inset,
                Math.max(1, slot.width - inset * 2), Math.max(1, slot.height - inset * 2), scene.cell);
        if (fit.rows == 0 || fit.cell <= 0) return;
        boolean letters = !colorBlind;
        Graphics2D layer = (Graphics2D) g.create();
        try {
            layer.clipRect(slot.x + 1, slot.y + 1, Math.max(1, slot.width - 2), Math.max(1, slot.height - 2));
            for (int r = 0; r < shape.length; r++) {
                if (shape[r] == null) continue;
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] == 0) continue;
                    int x = fit.originX + (c - fit.minCol) * fit.cell;
                    int y = fit.originY + (r - fit.minRow) * fit.cell;
                    if (letters) boards.paintCell(layer, shape[r][c], x, y, fit.cell);
                    else paintQuietCell(layer, shape[r][c], x, y, fit.cell);
                }
            }
        } finally {
            layer.dispose();
        }
    }

    private void paintQuietCell(Graphics2D g, int value, int x, int y, int size) {
        if (size <= 0 || value <= 0) return;
        BlockPalette palette = new BlockPalette();
        Color fill = value == VersusRules.ATTACK_CELL ? new Color(0x929AA5)
                : value <= BlockType.values().length ? palette.color(BlockType.values()[value - 1], colorBlind)
                : new Color(0x929AA5);
        GemTiles.paint(g, x, y, size, fill);
        if (!colorBlind || size < 6) return;
        g.setColor(palette.textColor(fill));
        int kind = value == VersusRules.ATTACK_CELL ? 8 : value - 1;
        int m = Math.max(1, size / 5);
        switch (kind) {
            case 0 -> { for (int i = m; i < size - m; i += 3) g.drawLine(x + m, y + i, x + size - m, y + i); }
            case 1 -> g.fillOval(x + size / 2 - 1, y + size / 2 - 1, 3, 3);
            case 2 -> g.drawLine(x + m, y + size - m, x + size - m, y + m);
            case 3 -> g.drawOval(x + m, y + m, size - m * 2, size - m * 2);
            case 4 -> { for (int i = m; i < size - m; i += 3) g.drawLine(x + i, y + m, x + i, y + size - m); }
            case 5 -> {
                g.drawLine(x + m, y + size / 2, x + size - m, y + size / 2);
                g.drawLine(x + size / 2, y + m, x + size / 2, y + size - m);
            }
            case 6 -> g.drawLine(x + m, y + m, x + size - m, y + size - m);
            default -> g.drawRect(x + m, y + m, Math.max(1, size - m * 2), Math.max(1, size - m * 2));
        }
    }

    private void paintEffects(Graphics2D g, RaidSnapshot.PlayerView player, int index) {
        Rectangle box = scene.effects[index];
        Rectangle play = scene.play[index];
        if (box.width <= 0 || box.intersects(play)) return;
        paintMiniLabel(g, snapshot.mode() == RaidMode.SOLO ? "활성 효과" : "효과", box);
        g.setColor(new Color(8, 12, 28, 175));
        g.fillRoundRect(box.x, box.y, box.width, box.height, 8, 8);
        Color accent = index == 0 ? UiKit.CYAN : UiKit.MAGENTA;
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 95));
        g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 8, 8);

        int y = box.y + 5;
        int chipW = Math.max(24, box.width - 8);
        int chipH = chipW >= 96 ? 20 : 18;
        boolean wide = chipW >= 96;
        int pending = player.board().pendingRows().size();
        if (pending > 0 && y + chipH <= box.y + box.height - 4) {
            paintChip(g, box.x + 4, y, chipW, chipH, "대기", pending, null, A11yColors.warning(colorBlind), wide);
            y += chipH + 4;
        }
        for (RaidSnapshot.Effect effect : player.effects()) {
            if (y + chipH > box.y + box.height - 4) break;
            String mark = effectMark(effect.pattern(), wide);
            if (mark == null) continue;
            BufferedImage icon = assets.scaled(AssetStore.patternPath(effect.pattern()), 12, 12);
            paintChip(g, box.x + 4, y, chipW, chipH, mark, effect.remainingPieces(), icon, effectColor(effect.pattern()), wide);
            y += chipH + 4;
        }
    }

    private void paintChip(Graphics2D g, int x, int y, int w, int h, String mark, int count, BufferedImage icon, Color color, boolean wide) {
        g.setColor(new Color(14, 20, 44, 224));
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 140));
        g.drawRoundRect(x, y, w - 1, h - 1, 6, 6);
        int textX = x + 4;
        if (icon != null) {
            g.drawImage(icon, x + 4, y + (h - 12) / 2, null);
            textX += 15;
        }
        String label = mark + " " + count;
        g.setFont(GameTheme.fontFor(label, wide ? 12 : 11, false));
        g.setColor(color);
        UiKit.drawInBox(g, label, new Rectangle(textX, y, Math.max(8, x + w - 4 - textX), h), UiKit.Align.CENTER);
    }

    private String effectMark(PatternId pattern, boolean wide) {
        return switch (pattern) {
            case REVERSE -> wide ? "반전" : "반";
            case OVERLOAD -> wide ? "과속" : "과";
            case SEAL_PREVIEW -> wide ? "봉인" : "봉";
            case CLOUD -> wide ? "구름" : "구";
            default -> null;
        };
    }

    private Color effectColor(PatternId pattern) {
        if (!colorBlind) return pattern == PatternId.REVERSE ? UiKit.MAGENTA : UiKit.CYAN;
        return switch (pattern) {
            case REVERSE -> A11yColors.PURPLE;
            case OVERLOAD -> A11yColors.ORANGE;
            case SEAL_PREVIEW -> A11yColors.BLUE;
            default -> A11yColors.WHITE;
        };
    }

    private void paintTarget(Graphics2D g, int index) {
        Rectangle play = scene.play[index];
        Color markColor = A11yColors.target(colorBlind);
        g.setColor(markColor);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(play.x - 2, play.y - 2, play.width + 3, play.height + 3, 6, 6);
        g.setStroke(new BasicStroke(1f));
        String label = "▼ 대상";
        g.setFont(GameTheme.fontFor(label, 13, false));
        int width = g.getFontMetrics().stringWidth(label);
        int x = play.x + (play.width - width) / 2;
        int baseline = play.y + 16;
        g.setColor(new Color(6, 8, 20, 190));
        g.fillRoundRect(x - 6, baseline - 13, width + 12, 16, 8, 8);
        g.setColor(markColor);
        g.drawString(label, x, baseline);
        mark(cueTarget, x, baseline - 12, width + 2, 14);
    }

    private void paintDowned(Graphics2D g, RaidSnapshot.PlayerView player, Rectangle play) {
        g.setColor(new Color(16, 12, 32, 150));
        g.fillRect(play.x, play.y, play.width, play.height);
        int required = Math.max(1, player.reviveRequired());
        int progress = Math.max(0, Math.min(required, player.reviveProgress()));
        int cx = play.x + play.width / 2;
        int cy = play.y + play.height / 2;
        int r = Math.max(28, scene.cell * 3);
        g.setStroke(new BasicStroke(5f));
        g.setColor(new Color(96, 96, 118));
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        g.setColor(colorBlind ? A11yColors.SKY : new Color(0x8CFFC1));
        g.drawArc(cx - r, cy - r, r * 2, r * 2, 90, -(int) (360.0 * progress / required));
        g.setStroke(new BasicStroke(1f));
        String text = "부활 " + progress + "/" + required;
        g.setFont(GameTheme.text(16, true));
        g.setColor(TEXT);
        UiKit.drawCentered(g, text, cx, cy + 6);
    }

    private void paintWideBoss(Graphics2D g, RaidSnapshot.BossView boss) {
        Rectangle window = scene.boss;
        Graphics2D layer = (Graphics2D) g.create();
        try {
            layer.clipRect(Math.max(0, window.x), Math.max(0, window.y), window.width, window.height);
            drawLedge(layer, boss, window, false);
            motion.paintDust(layer, window.x + window.width / 2, window.y + window.height - 8);
            motion.paintShards(layer, window);
        } finally {
            layer.dispose();
        }
    }

    private void drawLedge(Graphics2D g, RaidSnapshot.BossView boss, Rectangle window, boolean crop) {
        BufferedImage image = crop
                ? motion.raceCrop(assets, boss.id(), boss.level(), window.width, window.height)
                : assets.ledge(boss.id(), boss.level(), window.width);
        int bottom = window.y + window.height;
        int drawW = image == null ? window.width : image.getWidth();
        int drawH = image == null ? window.height : image.getHeight();
        int x = window.x + (window.width - drawW) / 2;
        int y = bottom - drawH + (restPose ? 0 : motion.introOffset() - motion.recoilY());
        if (boss.level() >= 2) {
            BufferedImage aura = motion.aura(boss.level(), window.width);
            if (aura != null) g.drawImage(aura, window.x + window.width / 2 - aura.getWidth() / 2, bottom - aura.getHeight() - 8, null);
        }
        Graphics2D layer = (Graphics2D) g.create();
        try {
            float breath = restPose ? 1f : motion.breath();
            layer.translate(window.x + window.width / 2.0, bottom);
            layer.scale(breath, breath);
            layer.translate(-(window.x + window.width / 2.0), -bottom);
            float alpha = restPose ? 1f : motion.defeatAlpha();
            if (alpha < 0.99f) layer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.08f, alpha)));
            if (image == null) paintBossFallback(layer, boss.id(), x, y, Math.min(drawW, 180));
            else layer.drawImage(image, x, y, null);
            if (fx.flashAlpha() > 0 && image != null) {
                BufferedImage white = motion.flashed(image);
                layer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
                if (white != null) layer.drawImage(white, x, y, null);
            }
        } finally {
            layer.dispose();
        }
    }

    private void paintHpFill(Graphics2D g, RaidSnapshot.BossView boss) {
        Rectangle box = scene.hp;
        if (box.width <= 0 || boss == null) return;
        float sx = box.width / (float) Math.max(1, RaidArt.DATA.hpWidth);
        float sy = box.height / (float) Math.max(1, RaidArt.DATA.hpHeight);
        RaidArt.Rect bar = RaidArt.DATA.hpBar;
        int bx = box.x + Math.round(bar.x() * sx);
        int by = box.y + Math.round(bar.y() * sy);
        int bw = Math.max(8, Math.round(bar.w() * sx));
        int bh = Math.max(6, Math.round(bar.h() * sy));
        float now = boss.hp() / (float) Math.max(1, boss.maxHp());
        float trail = motion.trailRatio(boss.hp(), boss.maxHp());
        g.setColor(new Color(8, 12, 28, 210));
        g.fillRoundRect(bx + 2, by + 2, Math.max(4, bw - 4), Math.max(4, bh - 4), 8, 8);
        g.setColor(new Color(255, 255, 255, 90));
        int trailW = Math.round((bw - 6) * Math.max(0f, Math.min(1f, trail)));
        if (trailW > 0) g.fillRoundRect(bx + 3, by + 3, trailW, Math.max(4, bh - 6), 8, 8);
        int fillW = Math.round((bw - 6) * Math.max(0f, Math.min(1f, now)));
        if (fillW > 0) {
            g.setColor(A11yColors.hp(colorBlind));
            g.fillRoundRect(bx + 3, by + 3, fillW, Math.max(4, bh - 6), 8, 8);
        }
        g.setFont(GameTheme.digits(Math.max(11, bh * 3 / 5)));
        g.setColor(TEXT);
        UiKit.drawInAsset(g, UiKit.AssetSlot.HP, new Rectangle(bx, by, bw, bh),
                boss.hp() + "/" + boss.maxHp(), UiKit.Align.CENTER);
        RaidArt.Rect pod = RaidArt.DATA.hpPercent;
        int px = box.x + Math.round(pod.x() * sx);
        int py = box.y + Math.round(pod.y() * sy);
        int pw = Math.max(8, Math.round(pod.w() * sx));
        int ph = Math.max(8, Math.round(pod.h() * sy));
        String percent = Math.round(100.0 * boss.hp() / Math.max(1, boss.maxHp())) + "%";
        g.setFont(GameTheme.digits(Math.max(10, ph * 3 / 5)));
        g.setColor(TEXT);
        UiKit.drawInAsset(g, UiKit.AssetSlot.HP, new Rectangle(px, py, pw, ph), percent, UiKit.Align.CENTER);
        int[] split = boss.splitHp();
        if (split.length == 0) return;
        int gap = 4;
        int mini = Math.max(20, (bw - gap * (split.length - 1)) / split.length);
        for (int i = 0; i < split.length; i++) {
            int hp = Math.max(0, split[i]);
            UiKit.paintGauge(g, bx + i * (mini + gap), by + bh + 2, mini, 6, hp / (float) Math.max(hp, 1), A11yColors.splitHp(colorBlind));
        }
    }

    private void paintUltHud(Graphics2D g, Rectangle box, int value, Color accent) {
        if (box.width <= 0 || box.height <= 0) return;
        int clamped = Math.max(0, Math.min(100, value));
        boolean ready = clamped >= 100;
        g.setColor(new Color(8, 12, 28, 218));
        g.fillRoundRect(box.x, box.y, box.width, box.height, 10, 10);
        Color border = ready ? new Color(0xFFC94D) : accent;
        g.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), ready ? 225 : 140));
        g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 10, 10);

        String title = ready ? "필살기 준비!" : "필살기 · 충전 중";
        String valText = clamped + "/100";
        if (box.height >= 44) {
            g.setFont(GameTheme.fontFor(title, 11, false));
            g.setColor(ready ? new Color(0xFFC94D) : TEXT);
            UiKit.drawInBox(g, title, new Rectangle(box.x + 8, box.y + 6, box.width - 52, 16), UiKit.Align.LEFT);
            g.setFont(GameTheme.digits(12));
            g.setColor(DIM);
            UiKit.drawInBox(g, valText, new Rectangle(box.x + box.width - 48, box.y + 6, 40, 16), UiKit.Align.RIGHT);
            UiKit.paintGauge(g, box.x + 8, box.y + box.height - 16, Math.max(8, box.width - 16), 9,
                    clamped / 100f, border);
        } else {
            g.setFont(GameTheme.fontFor(title, 11, false));
            g.setColor(ready ? new Color(0xFFC94D) : TEXT);
            UiKit.drawInBox(g, title, new Rectangle(box.x + 8, box.y + 2, box.width - 54, 13), UiKit.Align.LEFT);
            g.setFont(GameTheme.digits(11));
            g.setColor(DIM);
            UiKit.drawInBox(g, valText, new Rectangle(box.x + box.width - 48, box.y + 2, 40, 13), UiKit.Align.RIGHT);
            UiKit.paintGauge(g, box.x + 8, box.y + box.height - 8, Math.max(8, box.width - 16), 5,
                    clamped / 100f, border);
        }
    }

    private void paintTelegraph(Graphics2D g, RaidSnapshot.BossView boss) {
        Rectangle box = scene.banner;
        if (box.width <= 0 || boss == null) return;
        boolean hot = motion.telegraphHot(boss.piecesLeft(), boss.fleeMillis());
        g.setColor(hot ? new Color(76, 14, 42, 232) : new Color(16, 14, 40, 224));
        g.fillRoundRect(box.x, box.y, box.width, box.height, 14, 14);
        Color border = hot ? new Color(0xFF4A9E) : new Color(0x9B6BFF);
        g.setStroke(new BasicStroke(hot ? 1.8f : 1.2f));
        g.setColor(border);
        g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 14, 14);
        g.setStroke(new BasicStroke(1f));

        PatternId pattern = boss.nextPattern();
        String name = pattern == null ? "패턴 없음" : pattern.displayName();
        boolean flee = boss.fleeMillis() >= 0;
        int pieces = flee ? (int) Math.max(0, boss.fleeMillis() / 1000) : Math.max(0, boss.piecesLeft());
        int podW = 128;
        Rectangle pod = new Rectangle(box.x + box.width - podW - 6, box.y + 3, podW, box.height - 6);
        g.setColor(new Color(8, 12, 28, 220));
        g.fillRoundRect(pod.x, pod.y, pod.width, pod.height, 10, 10);
        g.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 150));
        g.drawRoundRect(pod.x, pod.y, pod.width - 1, pod.height - 1, 10, 10);
        String detail = targetText(boss.target()) + "  " + pieces + (flee ? "초" : "조각");
        g.setFont(GameTheme.fontFor(detail, 12, false));
        g.setColor(TEXT);
        UiKit.drawInBox(g, detail, pod, UiKit.Align.CENTER);

        Color warnColor = colorBlind ? A11yColors.ORANGE : (hot ? new Color(0xFF4A9E) : new Color(0xFFE27A));
        g.setColor(new Color(warnColor.getRed(), warnColor.getGreen(), warnColor.getBlue(), 45));
        g.fillOval(box.x + 8, box.y + (box.height - 20) / 2, 20, 20);
        g.setColor(warnColor);
        g.drawOval(box.x + 8, box.y + (box.height - 20) / 2, 19, 19);
        g.setFont(GameTheme.display(14));
        UiKit.drawInBox(g, "!", new Rectangle(box.x + 8, box.y, 20, box.height), UiKit.Align.CENTER);

        g.setFont(GameTheme.display(16));
        g.setColor(TEXT);
        UiKit.drawInBox(g, name, new Rectangle(box.x + 34, box.y, Math.max(40, pod.x - box.x - 40), box.height),
                UiKit.Align.CENTER);
    }

    private void paintTopCapsules(Graphics2D g, RaidSnapshot.BossView boss) {
        paintCapsuleText(g, scene.kill, "처치 " + snapshot.kills(), false);
        String bossTitle = boss.id().displayName() + " Lv." + boss.level();
        paintCapsuleText(g, scene.name, bossTitle, false);
        paintCapsuleText(g, scene.timer, clock(snapshot.elapsedMillis()), true);
    }

    private void paintRaceTop(Graphics2D g) {
        int[] kills = snapshot.raceKills().length == 2 ? snapshot.raceKills() : new int[] {0, 0};
        Rectangle box = scene.kill;
        UiKit.paintCapsule(g, box.x, box.y, box.width, box.height);
        String left = (colorBlind ? "■ " : "◆ ") + "P1";
        String right = "P2" + (colorBlind ? " ●" : " ★");
        g.setFont(GameTheme.text(12, true));
        g.setColor(indexColor(0));
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, box, left, UiKit.Align.LEFT);
        g.setFont(GameTheme.digits(14));
        g.setColor(TEXT);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, box, kills[0] + " : " + kills[1], UiKit.Align.CENTER);
        g.setFont(GameTheme.text(12, true));
        g.setColor(indexColor(1));
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, box, right, UiKit.Align.RIGHT);
        long remain = Math.max(0, tetris.feature.raid.RaidRules.RACE_LIMIT_MILLIS - snapshot.elapsedMillis());
        paintCapsuleText(g, scene.timer, clock(remain), true);
    }

    private void paintStolen(Graphics2D g) {
        if (snapshot.mode() != RaidMode.RACE || stolenSlower < 0 || System.currentTimeMillis() > stolenUntil) return;
        if (stolenSlower >= scene.players) return;
        Rectangle play = scene.play[stolenSlower];
        int w = Math.min(play.width - 16, 168);
        int h = 26;
        int x = play.x + (play.width - w) / 2;
        int y = play.y + 8;
        Rectangle stolen = new Rectangle(x, y, w, h);
        UiKit.paintCapsule(g, stolen.x, stolen.y, stolen.width, stolen.height);
        g.setFont(GameTheme.text(12, true));
        g.setColor(TEXT);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, stolen, "선수를 빼앗김", UiKit.Align.CENTER);
    }

    private void paintCapsuleText(Graphics2D g, Rectangle box, String text, boolean digits) {
        if (box.width <= 0 || text == null || text.isEmpty()) return;
        UiKit.paintCapsule(g, box.x, box.y, box.width, box.height);
        int size = fit(g, text, Math.max(8, box.width - 16), digits ? 16 : 15, 11, digits);
        g.setFont(digits ? GameTheme.digits(size) : GameTheme.fontFor(text, size, false));
        g.setColor(TEXT);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, box, text, UiKit.Align.CENTER);
    }

    private void paintCards(Graphics2D g) {
        RaidSnapshot.CardOffer offer = snapshot.offer();
        int veilTop = Math.max(0, scene.banner.y + scene.banner.height + 2);
        g.setColor(new Color(4, 6, 16, 185));
        g.fillRect(0, veilTop, VW, VH - veilTop);
        int panelY = Math.max(228, veilTop + 2);
        int panelH = VH - panelY - 22;
        g.setColor(new Color(8, 12, 28, 238));
        g.fillRoundRect(24, panelY + 8, VW - 48, panelH - 16, 18, 18);
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, 16, panelY, VW - 32, panelH);
        String title = offer.chooser() == 0 ? "P1이 카드를 고릅니다" : "P2가 카드를 고릅니다";
        g.setFont(GameTheme.display(22));
        g.setColor(TEXT);
        GameTheme.drawCentered(g, title, VW / 2, panelY + 42);
        String remain = "남은 시간  " + String.format("%.1f", offer.remainingMillis() / 1000.0) + "초";
        g.setFont(GameTheme.fontFor(remain, 15, true));
        g.setColor(colorBlind ? A11yColors.YELLOW : new Color(0xFFE27A));
        UiKit.drawCentered(g, remain, VW / 2, panelY + 66);
        g.setFont(GameTheme.text(12, false));
        g.setColor(DIM);
        UiKit.drawCentered(g, "좌우 이동 · 하드 드롭으로 확정", VW / 2, panelY + 86);
        int cardW = 166;
        int cardH = 204;
        int gap = 12;
        int x0 = (VW - (cardW * 3 + gap * 2)) / 2;
        int y0 = panelY + 122;
        for (int i = 0; i < offer.cards().size() && i < 3; i++) {
            int x = x0 + i * (cardW + gap);
            int y = i == cardSelection ? y0 - 6 : y0 + 6;
            scene.cards[i].setBounds(x, y, cardW, cardH);
            paintCard(g, offer.cards().get(i), x, y, cardW, cardH, i == cardSelection);
        }
    }

    private void paintCard(Graphics2D g, CardId card, int x, int y, int w, int h, boolean selected) {
        g.setColor(selected ? new Color(12, 28, 52, 242) : new Color(10, 15, 34, 236));
        g.fillRoundRect(x + 8, y + 8, w - 16, h - 16, 14, 14);
        UiKit.paintFrame(g, selected ? UiKit.Tone.CYAN : UiKit.Tone.VIOLET, x, y, w, h);
        BufferedImage image = assets.scaled(AssetStore.cardPath(card), w - 48, h - 108);
        if (image != null) g.drawImage(image, x + (w - image.getWidth()) / 2, y + 18, null);
        g.setFont(GameTheme.fontFor(card.displayName(), 13, false));
        g.setColor(TEXT);
        UiKit.drawCentered(g, card.displayName(), x + w / 2, y + h - 58);
        drawDescription(g, card.description(), x + 22, y + h - 42, w - 44);
        if (!selected) return;
        g.setFont(GameTheme.text(14, true));
        g.setColor(A11yColors.selected(colorBlind));
        UiKit.drawCentered(g, "▼", x + w / 2, y - 6);
    }

    private void paintVeil(Graphics2D g, String title, String hint) {
        veilBoards(g);
        int y = scene.play[0].height > 0 ? scene.play[0].y + scene.play[0].height / 2 : VH / 2;
        g.setFont(GameTheme.display(36));
        g.setColor(TEXT);
        GameTheme.drawCentered(g, title, VW / 2, y);
        g.setFont(GameTheme.text(16, false));
        g.setColor(DIM);
        UiKit.drawCentered(g, hint, VW / 2, y + 28);
    }

    private void paintConfirm(Graphics2D g) {
        veilBoards(g);
        int boxW = 420;
        int boxH = 176;
        int boxX = (VW - boxW) / 2;
        int boxY = Math.max(160, scene.boardY + 24);
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, boxX, boxY, boxW, boxH);
        g.setFont(GameTheme.display(24));
        g.setColor(TEXT);
        GameTheme.drawCentered(g, "레이드를 끝낼까요?", VW / 2, boxY + 48);
        g.setFont(GameTheme.text(13, false));
        g.setColor(DIM);
        UiKit.drawCentered(g, "점수는 저장되지 않습니다", VW / 2, boxY + 72);
        menuButton = new Rectangle(boxX + 28, boxY + 104, boxW / 2 - 40, 42);
        retryButton = new Rectangle(VW / 2 + 12, boxY + 104, boxW / 2 - 40, 42);
        paintChoice(g, menuButton, "끝내기", confirmYes);
        paintChoice(g, retryButton, "계속", !confirmYes);
    }

    private void paintResult(Graphics2D g) {
        RaidSnapshot.Result result = snapshot.result();
        g.setColor(new Color(4, 6, 16, 214));
        g.fillRect(0, 0, VW, VH);
        int boxW = Math.min(480, VW - 48);
        int boxH = Math.min(320, VH - 48);
        int boxX = (VW - boxW) / 2;
        int boxY = (VH - boxH) / 2;
        g.setColor(new Color(8, 10, 28));
        g.fillRoundRect(boxX + 16, boxY + 16, boxW - 32, boxH - 32, 16, 16);
        UiKit.paintFrame(g, UiKit.Tone.VIOLET, boxX, boxY, boxW, boxH);
        String title = snapshot.mode() == RaidMode.RACE ? raceWinner(result) : "레이드 종료";
        g.setFont(GameTheme.display(32));
        g.setColor(Color.WHITE);
        GameTheme.drawCentered(g, title, VW / 2, boxY + 52);
        String reason = snapshot.mode() == RaidMode.RACE ? "레이스" : "처치 " + result.kills();
        Rectangle reasonBox = new Rectangle(VW / 2 - 70, boxY + 64, 140, 26);
        UiKit.paintCapsule(g, reasonBox.x, reasonBox.y, reasonBox.width, reasonBox.height);
        g.setFont(GameTheme.text(13, false));
        g.setColor(TEXT);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, reasonBox, reason, UiKit.Align.CENTER);
        int cardW = (boxW - 72) / 2;
        int cardY = boxY + 104;
        if (snapshot.players().size() == 1) {
            paintScoreCard(g, boxX + 36, cardY, boxW - 72, 78, "P1", snapshot.players().get(0).board().score(), true, true);
        } else {
            long left = snapshot.players().get(0).board().score();
            long right = snapshot.players().get(1).board().score();
            Integer winner = result.winner();
            paintScoreCard(g, boxX + 24, cardY, cardW, 78, "P1", left, winner != null && winner == 0, true);
            paintScoreCard(g, boxX + 48 + cardW, cardY, cardW, 78, "P2", right, winner != null && winner == 1, false);
        }
        g.setFont(GameTheme.text(12, false));
        g.setColor(DIM);
        UiKit.drawCentered(g, "데미지 " + result.totalDamage() + "   시간 " + clock(result.survivedMillis()), VW / 2, cardY + 96);
        menuButton = new Rectangle(boxX + 36, boxY + boxH - 92, 150, 40);
        retryButton = new Rectangle(boxX + boxW - 186, boxY + boxH - 92, 150, 40);
        paintChoice(g, menuButton, menuLabel, resultIndex == 0);
        paintChoice(g, retryButton, retryLabel, resultIndex == 1);
        Rectangle hint = new Rectangle(boxX + 24, boxY + boxH - 40, boxW - 48, 26);
        UiKit.paintCapsule(g, hint.x, hint.y, hint.width, hint.height);
        g.setFont(GameTheme.text(12, false));
        g.setColor(TEXT);
        UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, hint, "좌우 이동, Enter 확정", UiKit.Align.CENTER);
    }

    private void paintScoreCard(Graphics2D g, int x, int y, int w, int h, String name, long score, boolean win, boolean one) {
        UiKit.paintFrame(g, (win || one) ? UiKit.Tone.CYAN : UiKit.Tone.VIOLET, x, y, w, h);
        Rectangle ink = UiKit.contentBox(UiKit.AssetSlot.HP, new Rectangle(x, y, w, h));
        g.setFont(GameTheme.text(14, false));
        g.setColor(one ? UiKit.CYAN : UiKit.MAGENTA);
        UiKit.drawIconText(g, new Rectangle(ink.x, ink.y, ink.width, Math.max(1, ink.height / 2)), one ? "◆" : "★", name);
        g.setFont(GameTheme.digits(26));
        g.setColor(Color.WHITE);
        UiKit.drawInBox(g, String.valueOf(score), new Rectangle(ink.x, ink.y + ink.height / 2, ink.width, Math.max(1, ink.height / 2)), UiKit.Align.CENTER);
    }

    private void paintChoice(Graphics2D g, Rectangle box, String label, boolean selected) {
        UiKit.paintButton(g, selected, box.x, box.y, box.width, box.height, colorBlind, UiKit.pulse());
        if (selected) UiKit.paintArrow(g, box.x - 14, box.y + box.height / 2, 10, colorBlind, UiKit.pulse());
        g.setFont(GameTheme.display(16));
        g.setColor(Color.WHITE);
        UiKit.drawInAsset(g, UiKit.AssetSlot.BUTTON, box, label, UiKit.Align.CENTER, selected);
    }

    private void veilBoards(Graphics2D g) {
        g.setColor(new Color(4, 6, 16, 150));
        int top = Math.max(0, scene.boardY - 8);
        g.fillRect(0, top, VW, VH - top);
    }

    private void paintGuide(Graphics2D g) {
        if (guide.isEmpty() || snapshot.phase() == RaidPhase.FINISHED || scene.guide.height < 16) return;
        String[] lines = guide.split("\\R");
        int gap = 4;
        int row = Math.max(20, (scene.guide.height - Math.max(0, lines.length - 1) * gap) / lines.length);
        int y = scene.guide.y;
        for (String line : lines) {
            if (y + 16 > scene.guide.y + scene.guide.height) break;
            int h = Math.min(row, scene.guide.y + scene.guide.height - y);
            Rectangle box = new Rectangle(scene.guide.x, y, scene.guide.width, h);
            UiKit.paintCapsule(g, box.x, box.y, box.width, box.height);
            g.setFont(GameTheme.text(Math.max(12, Math.min(14, h - 6)), false));
            g.setColor(TEXT);
            UiKit.drawInAsset(g, UiKit.AssetSlot.CAPSULE, box, line, UiKit.Align.CENTER);
            y += h + gap;
        }
    }

    private void paintNotice(Graphics2D g) {
        if (connectionNotice.isEmpty()) return;
        g.setFont(GameTheme.text(12, true));
        g.setColor(A11yColors.warning(colorBlind));
        UiKit.drawCentered(g, connectionNotice, VW / 2, scene.name.y + scene.name.height + 12);
    }

    private void paintLucky(Graphics2D g, int[][] lucky, Rectangle play) {
        if (lucky == null) return;
        boolean marked = false;
        for (int r = 0; r < lucky.length && r < VersusRules.BOARD_ROWS; r++) {
            for (int c = 0; c < lucky[r].length && c < VersusRules.BOARD_COLS; c++) {
                if (lucky[r][c] == 0) continue;
                int x = play.x + c * scene.cell;
                int y = play.y + r * scene.cell;
                A11yColors.paintLuckyMark(g, x, y, scene.cell, colorBlind);
                if (!marked) {
                    mark(cueLucky, x, y, scene.cell, scene.cell);
                    marked = true;
                }
            }
        }
    }

    private void paintCloud(Graphics2D g, RaidSnapshot.PlayerView player, Rectangle play) {
        for (RaidSnapshot.Effect effect : player.effects()) {
            if (effect.pattern() != PatternId.CLOUD) continue;
            fog.paint(g, play, effect.area(), scene.cell, colorBlind);
        }
    }

    private void paintLock(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(12, 14, 32, 180));
        g.fillRoundRect(x, y, Math.max(1, w), Math.max(1, h), 6, 6);
        g.setColor(colorBlind ? A11yColors.YELLOW : new Color(0xFFC94D));
        int cx = x + w / 2;
        int cy = y + h / 2;
        g.drawArc(cx - 5, cy - 7, 10, 8, 0, 180);
        g.fillRoundRect(cx - 6, cy - 1, 12, 8, 2, 2);
    }

    private void paintBossFallback(Graphics2D g, BossId id, int x, int y, int size) {
        Color body = switch (id) {
            case JELLY_KING -> new Color(0x3DDC8A);
            case STONE_GOLEM -> new Color(0x8D8478);
            case NIGHT_WITCH -> new Color(0x7A4DFF);
            case MECHA_DRAGON -> new Color(0xFF5A3C);
            case GOLDEN_TETRO -> new Color(0xFFC94D);
        };
        g.setColor(body);
        g.fillRoundRect(x + size / 6, y + size / 5, size * 2 / 3, size / 2, 24, 24);
        g.setColor(UiKit.CYAN);
        g.fillPolygon(new int[] {x + size / 2, x + size / 2 - 16, x + size / 2 + 16},
                new int[] {y + 8, y + size / 3, y + size / 3}, 3);
        g.setColor(new Color(0x07091A));
        g.fillOval(x + size / 2 - 18, y + size / 2 - 4, 8, 8);
        g.fillOval(x + size / 2 + 10, y + size / 2 - 4, 8, 8);
    }

    private boolean isTarget(int index) {
        if (snapshot.mode() == RaidMode.RACE) return false;
        if (snapshot.phase() != RaidPhase.PLAYING && snapshot.phase() != RaidPhase.COUNTDOWN) return false;
        int target = snapshot.boss().target();
        return target < 0 || target == index;
    }

    private String targetText(int target) {
        if (target < 0) return "→ 모두";
        if (snapshot.mode() == RaidMode.SOLO) return "→ 나";
        return target == 0 ? "→ P1" : "→ P2";
    }

    private String raceWinner(RaidSnapshot.Result result) {
        if (result.winner() == null) return "무승부";
        return result.winner() == 0 ? "■ P1 승" : "● P2 승";
    }

    private Color indexColor(int index) {
        if (index == 0) return colorBlind ? A11yColors.mine(true) : UiKit.CYAN;
        return colorBlind ? A11yColors.SKY : UiKit.MAGENTA;
    }

    private void noteStolen(List<RaidEvent> events) {
        if (events == null) return;
        for (RaidEvent event : events) {
            if (event.type() == RaidEvent.Type.BOSS_DEFEATED && event.player() >= 0) {
                stolenSlower = 1 - event.player();
                stolenUntil = System.currentTimeMillis() + 3200;
            }
        }
    }

    private void markBoss() {
        Rectangle source = snapshot.mode() == RaidMode.RACE ? scene.raceArt[0] : scene.boss;
        int x = Math.max(0, source.x);
        int y = Math.max(0, source.y);
        int width = Math.max(1, source.width - (x - source.x));
        int height = Math.max(1, source.height - (y - source.y));
        bossBounds.setBounds(originX + (int) (x * scale), originY + (int) (y * scale),
                Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)));
    }

    private int countdownDigit() {
        if (countdownAnchor < 0) return 3;
        int gone = (int) ((System.currentTimeMillis() - countdownAnchor) / 1000);
        return Math.max(1, 3 - gone);
    }

    private static String clock(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public static java.util.List<String> captions() {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (PatternId id : PatternId.values()) {
            lines.add(shown(id.displayName()));
            lines.add(shown(id.description()));
        }
        for (CardId id : CardId.values()) {
            lines.add(shown(id.displayName()));
            lines.add(shown(id.description()));
        }
        lines.add("▼ 대상");
        lines.add("점수");
        lines.add("필살");
        lines.add("협동");
        lines.add("남은 시간");
        lines.add("P1이 카드를 고릅니다");
        lines.add("P2가 카드를 고릅니다");
        lines.add("초 · 좌우 이동, 하드 드롭 확정");
        lines.add("선수를 빼앗김");
        lines.add("부활 3/8");
        lines.add("대기 1");
        lines.add("구름 4");
        lines.add("봉인 3");
        lines.add("반전 2");
        lines.add("과부하 5");
        lines.add("홀드");
        lines.add("다음");
        lines.add("다음 1");
        lines.add("레이드 종료");
        lines.add("레이드를 끝낼까요?");
        lines.add("점수는 저장되지 않습니다");
        lines.add("P1 A D 이동 · R 필살");
        return lines;
    }

    public static String shown(String text) {
        return text == null ? "" : text;
    }

    private void paintMixed(Graphics2D g, String text, int centerX, int baseline, int size) {
        Font digits = GameTheme.digits(size);
        Font hangul = GameTheme.text(size, false);
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int j = runEnd(text, i);
            g.setFont(fontForRun(text, i, j, digits, hangul));
            width += g.getFontMetrics().stringWidth(text.substring(i, j));
            i = j;
        }
        int x = centerX - width / 2;
        g.setColor(TEXT);
        for (int i = 0; i < text.length(); ) {
            int j = runEnd(text, i);
            String part = text.substring(i, j);
            g.setFont(fontForRun(text, i, j, digits, hangul));
            g.drawString(part, x, baseline);
            x += g.getFontMetrics().stringWidth(part);
            i = j;
        }
    }

    private static int runEnd(String text, int start) {
        boolean numeric = digitish(text.charAt(start));
        int j = start + 1;
        while (j < text.length() && digitish(text.charAt(j)) == numeric) j++;
        return j;
    }

    private static Font fontForRun(String text, int start, int end, Font digits, Font hangul) {
        String part = text.substring(start, end);
        if (digitish(text.charAt(start)) && digits.canDisplayUpTo(part) == -1) return digits;
        return hangul;
    }

    private static boolean digitish(char c) {
        return Character.isDigit(c) || c == ':' || c == '.' || c == '/' || c == '%';
    }

    private static int fit(Graphics2D g, String text, int maxWidth, int preferred, int min, boolean digits) {
        int size = preferred;
        g.setFont(digits ? GameTheme.digits(size) : GameTheme.fontFor(text, size, false));
        while (size > min && g.getFontMetrics().stringWidth(text) > maxWidth) {
            size--;
            g.setFont(digits ? GameTheme.digits(size) : GameTheme.fontFor(text, size, false));
        }
        return size;
    }

    private static void drawDescription(Graphics2D g, String text, int x, int y, int width) {
        if (text == null || text.isEmpty()) return;
        int size = 11;
        g.setFont(GameTheme.text(size, false));
        java.util.List<String> lines = tetris.ui.theme.TextWrap.lines(text, g.getFontMetrics(), width, 2);
        if (tetris.ui.theme.TextWrap.overflows(lines, g.getFontMetrics(), width, 2)) {
            size = 10;
            g.setFont(GameTheme.text(size, false));
            lines = tetris.ui.theme.TextWrap.lines(text, g.getFontMetrics(), width, 2);
        }
        g.setColor(DIM);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            UiKit.drawCentered(g, lines.get(i), x + width / 2, y + i * (size + 3));
        }
    }

    private void mark(Rectangle box, int vx, int vy, int vw, int vh) {
        box.setBounds(originX + (int) (vx * scale), originY + (int) (vy * scale),
                Math.max(1, (int) (vw * scale)), Math.max(1, (int) (vh * scale)));
    }

    private static final class Scene {
        int players;
        int cell;
        int boardY;
        final Rectangle boss = new Rectangle();
        final Rectangle hp = new Rectangle();
        final Rectangle gauge = new Rectangle();
        final Rectangle banner = new Rectangle();
        final Rectangle kill = new Rectangle();
        final Rectangle timer = new Rectangle();
        final Rectangle name = new Rectangle();
        final Rectangle guide = new Rectangle();
        final Rectangle level = new Rectangle();
        final Rectangle[] play = {new Rectangle(), new Rectangle()};
        final Rectangle[] frame = {new Rectangle(), new Rectangle()};
        final Rectangle[] hold = {new Rectangle(), new Rectangle()};
        final Rectangle[] next = {new Rectangle(), new Rectangle()};
        final Rectangle[] score = {new Rectangle(), new Rectangle()};
        final Rectangle[] effects = {new Rectangle(), new Rectangle()};
        final Rectangle[] header = {new Rectangle(), new Rectangle()};
        final Rectangle[] raceArt = {new Rectangle(), new Rectangle()};
        final Rectangle[] racePedestal = {new Rectangle(), new Rectangle()};
        final Rectangle[] raceGauge = {new Rectangle(), new Rectangle()};
        final Rectangle[] cards = {new Rectangle(), new Rectangle(), new Rectangle()};
    }
}
