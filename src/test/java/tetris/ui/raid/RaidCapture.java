package tetris.ui.raid;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.ScreenSize;
import tetris.ui.GamePanel;

/** 레이드 화면 검수용 오프스크린 캡처. 테스트 스위트에는 넣지 않는다 */
public final class RaidCapture {
    private static final Path OUT = Path.of(System.getProperty("tetris.capture.dir", "build/verification/raid"));

    private RaidCapture() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        var medium = GamePanel.preferredSizeFor(ScreenSize.MEDIUM.cellPixels());
        var small = GamePanel.preferredSizeFor(ScreenSize.SMALL.cellPixels());
        capture("coop", panel(false, aliveCoop()), medium.width, medium.height);
        capture("coop-downed", panel(false, RaidSamples.playing(RaidMode.COOP)), medium.width, medium.height);
        capture("solo", panel(false, RaidSamples.playing(RaidMode.SOLO)), medium.width, medium.height);
        capture("race", panel(false, race()), medium.width, medium.height);
        capture("cards", panel(false, RaidSamples.phase(RaidMode.COOP, RaidPhase.CARD_PICK)), medium.width, medium.height);
        capture("result", panel(false, RaidSamples.phase(RaidMode.COOP, RaidPhase.FINISHED)), medium.width, medium.height);
        capture("boss-hit", hit(RaidEvent.Type.DAMAGE, 70), medium.width, medium.height);
        capture("critical", hit(RaidEvent.Type.CRITICAL, 90), medium.width, medium.height);
        capture("telegraph", panel(false, telegraph()), medium.width, medium.height);
        capture("defeat-mid", defeat(), medium.width, medium.height);
        capture("cloud", panel(false, cloud()), medium.width, medium.height);
        capture("cloud-colorblind", panel(true, cloud()), medium.width, medium.height);
        capture("bomb-mid", burst(true), medium.width, medium.height);
        capture("line-clear-mid", burst(false), medium.width, medium.height);
        capture("coop-small", panel(false, aliveCoop()), small.width, small.height);
        capture("coop-1920x1080", panel(false, aliveCoop()), 1920, 1080);
        capture("intro-spin", panel(false, intro(BossId.NIGHT_WITCH, 1, 1_200)), medium.width, medium.height);
        capture("intro-stopped", panel(false, intro(BossId.NIGHT_WITCH, 1, 2_700)), medium.width, medium.height);
        capture("intro-info-witch", panel(false, intro(BossId.NIGHT_WITCH, 1, 4_000)), medium.width, medium.height);
        capture("intro-info-jelly-lv2", panel(false, intro(BossId.JELLY_KING, 2, 9_000)), medium.width, medium.height);
        capture("intro-info-dragon-small", panel(false, intro(BossId.MECHA_DRAGON, 3, 6_000)), small.width, small.height);
        capture("intro-info-golem-cb", panel(true, intro(BossId.STONE_GOLEM, 4, 5_000)), medium.width, medium.height);
        capture("intro-info-golden", panel(false, intro(BossId.GOLDEN_TETRO, 2, 5_000)), medium.width, medium.height);
        capture("intro-info-1920", panel(false, intro(BossId.MECHA_DRAGON, 1, 5_000)), 1920, 1080);
        capture("pause", panel(false, RaidSamples.phase(RaidMode.COOP, RaidPhase.PAUSED)), medium.width, medium.height);
        capture("pause-1920", panel(false, RaidSamples.phase(RaidMode.SOLO, RaidPhase.PAUSED)), 1920, 1080);
        RaidPanel confirm = panel(false, aliveCoop());
        confirm.setConfirm(true, false);
        capture("confirm", confirm, medium.width, medium.height);
        capture("skill-strike", skill(RaidMode.COOP, 11, 120), medium.width, medium.height);
        capture("skill-callout", skill(RaidMode.SOLO, 28, 900), medium.width, medium.height);
        capture("skill-callout-race", skill(RaidMode.RACE, 28, 900), medium.width, medium.height);
        long busy = timeBusy();
        System.out.println("raid-paint-ms " + (busy / 1_000_000.0));
        compare();
    }

    private static RaidPanel panel(boolean colorBlind, RaidSnapshot snapshot) {
        RaidPanel panel = new RaidPanel(colorBlind);
        panel.setSnapshot(snapshot);
        panel.setGuide("←/→ 이동 · ↑/↓ 회전 · R 필살");
        panel.setCardCursors(snapshot.players().size() > 1 ? new int[] {1, 2} : new int[] {1});
        return panel;
    }

    /** 보스가 바위 투척을 쓴 뒤 frames 프레임, wait 밀리초가 지난 화면 */
    private static RaidPanel skill(RaidMode mode, int frames, long wait) throws InterruptedException {
        var boss = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 2, 300, 580, PatternId.PILLAR, 0, 7, new int[0], -1);
        var events = List.of(new RaidEvent(RaidEvent.Type.BOSS_ATTACK, 0, PatternId.ROCK_THROW.ordinal()));
        var players = mode == RaidMode.SOLO ? List.of(RaidSamples.fighter(true))
                : List.of(RaidSamples.fighter(true), RaidSamples.fighter(false));
        RaidSnapshot snapshot = mode == RaidMode.RACE
                ? new RaidSnapshot("raid-preview", 13, mode, RaidPhase.PLAYING, boss, players, 1, 60_000L, 0, null,
                        events, null, List.of(boss, boss), new int[] {1, 0}, new int[] {20, 40}, 0)
                : new RaidSnapshot("raid-preview", 13, mode, RaidPhase.PLAYING, boss, players, 1, 60_000L, 30, null,
                        events, null);
        RaidPanel panel = panel(false, snapshot);
        panel.advance(frames);
        Thread.sleep(wait);
        return panel;
    }

    private static RaidSnapshot aliveCoop() {
        var boss = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 2, 412, 580, PatternId.ROCK_THROW,
                0, 3, new int[0], -1);
        return new RaidSnapshot("raid-preview", 2, RaidMode.COOP, RaidPhase.PLAYING, boss,
                List.of(RaidSamples.fighter(true), RaidSamples.fighter(false)),
                3, 192_000L, 62, null, List.of(), null);
    }

    private static RaidSnapshot intro(BossId id, int level, long elapsed) {
        int max = tetris.feature.raid.RaidRules.hp(RaidMode.SOLO, level);
        var boss = new RaidSnapshot.BossView(id, level, max, max,
                id == BossId.GOLDEN_TETRO ? null : tetris.feature.raid.PatternPicker.roster(id)[0], 0,
                tetris.feature.raid.RaidRules.countdown(level), new int[0], id == BossId.GOLDEN_TETRO ? 15_000 : -1);
        return new RaidSnapshot("raid-preview", 12, RaidMode.SOLO, RaidPhase.BOSS_INTRO, boss,
                List.of(RaidSamples.fighter(true)), level - 1, 60_000L, 30, null, List.of(), null,
                List.of(), new int[0], new int[0], tetris.feature.raid.RaidRules.INTRO_MILLIS - elapsed);
    }

    private static RaidSnapshot race() {
        var one = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 2, 180, 400, PatternId.ROCK_THROW,
                0, 2, new int[0], -1);
        var two = new RaidSnapshot.BossView(BossId.NIGHT_WITCH, 2, 260, 400, PatternId.SEAL_PREVIEW,
                1, 5, new int[0], -1);
        var stolen = List.of(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, 0, BossId.STONE_GOLEM.ordinal()));
        return new RaidSnapshot("raid-preview", 3, RaidMode.RACE, RaidPhase.PLAYING, one,
                List.of(RaidSamples.fighter(true), RaidSamples.fighter(false)),
                4, 192_000L, 40, null, stolen, null,
                List.of(one, two), new int[] {3, 1}, new int[] {80, 35}, 0);
    }

    private static void capture(String name, JComponent panel, int width, int height) throws Exception {
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
            panel.removeNotify();
        }
        ImageIO.write(image, "png", OUT.resolve(name + ".png").toFile());
    }
    private static RaidPanel hit(RaidEvent.Type type, int value) {
        var boss = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 3, 300, 580, PatternId.ROCK_THROW,
                0, 2, new int[0], -1);
        var events = List.of(new RaidEvent(type, 0, value));
        RaidPanel panel = panel(false, new RaidSnapshot("raid-preview", 8, RaidMode.COOP, RaidPhase.PLAYING, boss,
                List.of(RaidSamples.fighter(true), RaidSamples.fighter(false)),
                3, 192_000L, 62, null, events, null));
        panel.advance(1);
        return panel;
    }

    private static RaidSnapshot telegraph() {
        var boss = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 2, 80, 580, PatternId.ROCK_THROW,
                0, 1, new int[0], -1);
        return new RaidSnapshot("raid-preview", 9, RaidMode.COOP, RaidPhase.PLAYING, boss,
                List.of(RaidSamples.fighter(true), RaidSamples.fighter(false)),
                3, 192_000L, 90, null, List.of(), null);
    }

    private static RaidPanel defeat() {
        var boss = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 2, 0, 580, PatternId.ROCK_THROW,
                0, 0, new int[0], -1);
        var events = List.of(new RaidEvent(RaidEvent.Type.BOSS_DEFEATED, -1, BossId.STONE_GOLEM.ordinal()));
        RaidPanel panel = panel(false, new RaidSnapshot("raid-preview", 10, RaidMode.COOP, RaidPhase.PLAYING, boss,
                List.of(RaidSamples.fighter(true), RaidSamples.fighter(false)),
                4, 200_000L, 100, null, events, null));
        panel.advance(10);
        return panel;
    }

    private static RaidSnapshot cloud() {
        var boss = new RaidSnapshot.BossView(BossId.NIGHT_WITCH, 2, 260, 400, PatternId.CLOUD,
                0, 4, new int[0], -1);
        var fighter = new RaidSnapshot.PlayerView(RaidSamples.board(), true, new int[20][10],
                List.of(new RaidSnapshot.Effect(PatternId.CLOUD, 4, new int[] {6, 1, 8, 8})),
                0, 8, 640);
        return new RaidSnapshot("raid-preview", 11, RaidMode.COOP, RaidPhase.PLAYING, boss,
                List.of(fighter, RaidSamples.fighter(false)), 2, 80_000L, 40, null, List.of(), null);
    }

    private static RaidPanel burst(boolean bomb) {
        RaidPanel panel = panel(false, aliveCoop());
        if (bomb) panel.previewBomb();
        else panel.previewLineClear();
        panel.advance(4);
        return panel;
    }

    private static long timeBusy() {
        RaidPanel panel = panel(false, aliveCoop());
        panel.previewLineClear();
        panel.advance(3);
        panel.setSize(1920, 1080);
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D warm = image.createGraphics();
        panel.paint(warm);
        warm.dispose();
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            Graphics2D g = image.createGraphics();
            long start = System.nanoTime();
            panel.paint(g);
            best = Math.min(best, System.nanoTime() - start);
            g.dispose();
        }
        panel.removeNotify();
        return best;
    }

    private static void compare() throws Exception {
        Path mock = Path.of("C:/Users/asus/Desktop/SE-tetris/assets-work/ui-concept/mock-raid.png");
        Path shot = OUT.resolve("coop.png");
        if (!Files.exists(mock) || !Files.exists(shot)) return;
        BufferedImage left = ImageIO.read(mock.toFile());
        BufferedImage right = ImageIO.read(shot.toFile());
        int height = 720;
        int leftW = Math.max(1, left.getWidth() * height / left.getHeight());
        int rightW = Math.max(1, right.getWidth() * height / right.getHeight());
        BufferedImage out = new BufferedImage(leftW + rightW + 8, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(left, 0, 0, leftW, height, null);
        g.drawImage(right, leftW + 8, 0, rightW, height, null);
        g.dispose();
        ImageIO.write(out, "png", mock.getParent().resolve("compare-raid.png").toFile());
    }

}
