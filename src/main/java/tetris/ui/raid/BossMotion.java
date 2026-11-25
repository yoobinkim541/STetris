package tetris.ui.raid;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.RaidEvent;
import tetris.ui.fx.AssetStore;
import tetris.ui.fx.StarfieldBackground;

/**
 * 보스 숨쉬기, 피격 반동, HP 잔상, 등장 낙하, 처치 파편.
 * 색을 입힌 그림은 크기마다 한 번만 만든다.
 */
final class BossMotion {
    private static final int INTRO_FRAMES = 22;
    private static final int DEFEAT_FRAMES = 28;
    /** 패턴을 쓸 때 몸을 들었다가 보드 쪽으로 내리찍는 시간 (약 0.45초) */
    static final int ATTACK_FRAMES = 28;

    private final Map<String, BufferedImage> flash = new HashMap<>();
    private final Map<String, BufferedImage> aura = new HashMap<>();
    private final Map<String, BufferedImage> crops = new HashMap<>();
    private final Shard[] shards = new Shard[16];
    private final Dust[] dust = new Dust[10];
    private int frame;
    private int recoil;
    private float trail = -1f;
    private int trailWait;
    private int intro = -1;
    private int defeat = -1;
    private int attack = -1;
    /** 처치 페이드가 끝난 뒤 다음 보스가 나올 때까지 숨긴다 (카드 고르는 동안 죽은 보스가 다시 보이지 않게) */
    private boolean gone;
    private BossId shown;
    private int shownLevel;

    void note(BossId id, int level, int hp, int maxHp, List<RaidEvent> events) {
        if (id != shown || level != shownLevel) {
            shown = id;
            shownLevel = level;
            trail = hp;
            defeat = -1;
            gone = false;
            if (events != null) {
                for (RaidEvent event : events) {
                    if (event.type() == RaidEvent.Type.BOSS_SPAWN) intro = INTRO_FRAMES;
                }
            }
        }
        if (trail < 0) trail = hp;
        if (events == null) return;
        for (RaidEvent event : events) {
            switch (event.type()) {
                case DAMAGE, CRITICAL, ULTIMATE -> {
                    int amount = Math.max(0, event.value());
                    trail = Math.min(maxHp, Math.max(trail, hp + amount));
                    trailWait = 12;
                    recoil = Math.max(recoil, Math.min(16, 4 + amount / 18));
                }
                case BOSS_DEFEATED -> {
                    if (defeat < 0 && !gone) {
                        defeat = DEFEAT_FRAMES;
                        spawnShards();
                    }
                }
                case BOSS_SPAWN -> {
                    intro = INTRO_FRAMES;
                    defeat = -1;
                    gone = false;
                }
                case BOSS_ATTACK -> attack = ATTACK_FRAMES;
                default -> {
                }
            }
        }
    }

    void tick() {
        frame++;
        if (recoil > 0) recoil--;
        if (trailWait > 0) trailWait--;
        if (intro > 0) {
            intro--;
            if (!StarfieldBackground.lowEffects() && intro % 2 == 0) puff();
        }
        if (defeat > 0) {
            defeat--;
            if (defeat == 0) {
                defeat = -1;
                gone = true;
            }
        }
        if (attack > 0) attack--;
        for (Dust bit : dust) if (bit != null && bit.life > 0) bit.tick();
        if (defeat >= 0 && !StarfieldBackground.lowEffects()) {
            for (Shard shard : shards) if (shard != null && shard.life > 0) shard.tick();
        }
    }

    float breath() {
        if (StarfieldBackground.lowEffects() || defeat >= 0) return 1f;
        return 1f + 0.014f * (float) Math.sin(frame * 0.08);
    }

    int recoilY() {
        return StarfieldBackground.lowEffects() ? 0 : recoil;
    }

    int introOffset() {
        if (intro <= 0) return 0;
        float t = intro / (float) INTRO_FRAMES;
        return (int) (-t * t * 90);
    }

    /** 보스 소개가 끝나 턱에 처음 드러날 때 등장 연출을 다시 한다. 소개 막 뒤에서 끝나 버리지 않게 */
    void replayEntrance() {
        intro = INTRO_FRAMES;
    }

    /** 움직임이 없으면 화면은 정지 그림 한 장을 찍는다 (매 프레임 보스를 다시 합성하지 않는다) */
    boolean idle() {
        return intro <= 0 && recoil <= 0 && attack <= 0 && defeat < 0;
    }

    /** 처치 연출이 끝나 다음 보스를 기다리는 중이면 보스를 그리지 않는다 */
    boolean gone() {
        return gone;
    }
    /** 공격 진행 0..1. 공격 중이 아니면 -1 */
    float attackProgress() {
        if (attack <= 0 || StarfieldBackground.lowEffects()) return -1f;
        return 1f - attack / (float) ATTACK_FRAMES;
    }

    /** 들어 올림(위로 −8) → 내리찍기(아래로 +14) → 천천히 제자리 */
    int attackLunge() {
        float t = attackProgress();
        if (t < 0) return 0;
        if (t < 0.3f) return Math.round(-8f * easeOut(t / 0.3f));
        if (t < 0.45f) return Math.round(-8f + 22f * easeIn((t - 0.3f) / 0.15f));
        return Math.round(14f * (1f - easeOut((t - 0.45f) / 0.55f)));
    }

    /** 내리찍는 순간 살짝 커졌다가 돌아온다. 두 단계로만 바꿔 장치 크기 그림을 매 프레임 새로 만들지 않는다 */
    float attackScale() {
        float t = attackProgress();
        if (t < 0.3f || t > 0.8f) return 1f;
        double bump = Math.sin(Math.PI * (t - 0.3f) / 0.5f);
        return bump > 0.66 ? 1.06f : bump > 0.2 ? 1.03f : 1f;
    }

    private static float easeOut(float t) {
        float u = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - u) * (1f - u);
    }

    private static float easeIn(float t) {
        float u = Math.max(0f, Math.min(1f, t));
        return u * u;
    }

    float defeatAlpha() {
        if (defeat < 0) return 1f;
        return Math.max(0f, defeat / (float) DEFEAT_FRAMES);
    }

    boolean telegraphHot(int piecesLeft, long fleeMillis) {
        return fleeMillis < 0 && piecesLeft <= 1;
    }

    float telegraphPulse() {
        if (StarfieldBackground.lowEffects()) return 0.35f;
        return 0.35f + 0.65f * (float) (0.5 + 0.5 * Math.sin(frame * 0.45));
    }

    float trailRatio(int hp, int maxHp) {
        float current = hp / (float) Math.max(1, maxHp);
        if (trailWait > 0) return Math.max(current, trail / Math.max(1, maxHp));
        float step = Math.max(1f, maxHp / 36f);
        trail = Math.max(hp, trail - step);
        return Math.max(current, trail / Math.max(1, maxHp));
    }

    BufferedImage flashed(BufferedImage source) {
        if (source == null) return null;
        String key = System.identityHashCode(source) + ":" + source.getWidth() + "x" + source.getHeight();
        BufferedImage cached = flash.get(key);
        if (cached != null) return cached;
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) continue;
                int red = (argb >> 16) & 0xff;
                int green = (argb >> 8) & 0xff;
                int blue = argb & 0xff;
                red = red + (255 - red) * 3 / 4;
                green = green + (255 - green) * 3 / 4;
                blue = blue + (255 - blue) * 3 / 4;
                out.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        flash.put(key, out);
        return out;
    }

    BufferedImage aura(int level, int width) {
        if (width <= 0) return null;
        int size = Math.max(32, width / 3);
        String key = (level >= 3 ? "gold" : "cyan") + ":" + size;
        BufferedImage cached = aura.get(key);
        if (cached != null) return cached;
        BufferedImage image = new BufferedImage(size, size / 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            Color color = level >= 3 ? new Color(255, 196, 64, 90) : new Color(80, 220, 255, 70);
            g.setColor(color);
            g.fillOval(0, size / 8, size, size / 3);
        } finally {
            g.dispose();
        }
        aura.put(key, image);
        return image;
    }

    /** 레이스. 턱 그림의 가운데를 잘라 열 너비에 맞춘다 */
    BufferedImage raceCrop(AssetStore assets, BossId id, int level, int width, int height) {
        if (id == null || width <= 0 || height <= 0) return null;
        String path = "/assets/raid/ledge-" + id.assetKey() + ".png";
        String key = path + ":" + level + ":" + width + "x" + height;
        BufferedImage cached = crops.get(key);
        if (cached != null) return cached;
        BufferedImage source = assets.image(path);
        if (source == null) return null;
        int sx = (int) (source.getWidth() * 0.18);
        int sw = (int) (source.getWidth() * 0.64);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, 0, 0, width, height, sx, 0, sx + sw, source.getHeight(), null);
        } finally {
            g.dispose();
        }
        if (level >= 2) out = tint(out, level >= 3 ? 0.08f : 0.04f);
        crops.put(key, out);
        return out;
    }

    void paintDust(Graphics2D g, int x, int y) {
        if (StarfieldBackground.lowEffects()) return;
        for (Dust bit : dust) {
            if (bit == null || bit.life <= 0) continue;
            g.setColor(new Color(210, 230, 255, Math.min(180, bit.life * 18)));
            g.fillOval(x + (int) bit.x, y + (int) bit.y, 4, 3);
        }
    }

    void paintShards(Graphics2D g, Rectangle window) {
        if (defeat < 0 || StarfieldBackground.lowEffects()) return;
        int cx = window.x + window.width / 2;
        int cy = window.y + window.height / 2;
        for (Shard shard : shards) {
            if (shard == null || shard.life <= 0) continue;
            g.setColor(new Color(shard.rgb, true));
            int px = cx + (int) shard.x;
            int py = cy + (int) shard.y;
            g.fillRoundRect(px, py, shard.size, shard.size / 2 + 2, 3, 3);
        }
    }

    void paintFade(Graphics2D g, float alpha) {
        if (alpha >= 0.99f) return;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.05f, alpha)));
    }

    private void spawnShards() {
        for (int i = 0; i < shards.length; i++) {
            double angle = i * Math.PI * 2 / shards.length;
            int rgb = (i % 2 == 0 ? 0xC8F2FF : 0xE7A0FF) | 0xE6000000;
            shards[i] = new Shard(Math.cos(angle) * 2.4, Math.sin(angle) * 1.6 - 1.2, rgb, 6 + i % 4);
        }
    }

    private void puff() {
        for (int i = 0; i < dust.length; i++) {
            if (dust[i] == null || dust[i].life <= 0) {
                dust[i] = new Dust((i - dust.length / 2f) * 8, 0, (i - dust.length / 2f) * 0.3, -0.6f);
                return;
            }
        }
    }

    private static BufferedImage tint(BufferedImage source, float amount) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float shift = amount - (float) Math.floor(amount);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) continue;
                int red = (argb >> 16) & 0xff;
                int green = (argb >> 8) & 0xff;
                int blue = argb & 0xff;
                float[] hsb = Color.RGBtoHSB(red, green, blue, null);
                int rgb = Color.HSBtoRGB((hsb[0] + shift) % 1f, Math.min(1f, hsb[1] * 1.08f), hsb[2]);
                out.setRGB(x, y, (alpha << 24) | (rgb & 0x00ffffff));
            }
        }
        return out;
    }

    private static final class Shard {
        double x;
        double y;
        double vx;
        double vy;
        final int rgb;
        final int size;
        int life = 22;

        Shard(double vx, double vy, int rgb, int size) {
            this.vx = vx;
            this.vy = vy;
            this.rgb = rgb;
            this.size = size;
        }

        void tick() {
            x += vx;
            y += vy;
            vy += 0.12;
            life--;
        }
    }

    private static final class Dust {
        double x;
        double y;
        double vx;
        double vy;
        int life = 12;

        Dust(double x, double y, double vx, double vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        void tick() {
            x += vx;
            y += vy;
            life--;
        }
    }
}
