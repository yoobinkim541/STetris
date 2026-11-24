package tetris.feature.raid;

import java.util.Objects;
import tetris.feature.raid.api.BossId;

/**
 * 보스 소개({@code BOSS_INTRO})의 룰렛 위치. 남은 소개 시간만으로 정해져서
 * 로컬 화면·온라인 화면·효과음이 같은 칸에서 멈춘다. 칸 순서는 {@link BossId} 선언 순서다.
 */
public final class BossRoulette {
    /** 멈추기 전에 도는 바퀴 수 */
    private static final int LAPS = 3;
    /** 룰렛 시간 중 도는 구간. 나머지는 멈춘 칸을 보여 준다 */
    private static final long MOTION_MILLIS = RaidRules.INTRO_SPIN_MILLIS * 4 / 5;

    private BossRoulette() {
    }

    public static int slots() {
        return BossId.values().length;
    }

    /** 소개 시작 뒤 지난 시간 */
    public static long elapsed(long introMillis) {
        return Math.max(0, RaidRules.INTRO_MILLIS - Math.max(0, introMillis));
    }

    /** 룰렛이 뽑힌 칸에 멈췄는가 (능력 소개 직전 잠깐 포함) */
    public static boolean stopped(long introMillis) {
        return elapsed(introMillis) >= MOTION_MILLIS;
    }

    /** 룰렛이 멈추고 능력 소개가 보이는 단계인가 */
    public static boolean revealed(long introMillis) {
        return elapsed(introMillis) >= RaidRules.INTRO_SPIN_MILLIS;
    }

    /** 지금 불이 들어온 칸. 처음엔 빠르게 돌다가 느려지며 뽑힌 보스에서 멈춘다 */
    public static int slot(long introMillis, BossId chosen) {
        Objects.requireNonNull(chosen, "chosen");
        int steps = LAPS * slots() + chosen.ordinal();
        double t = Math.min(1.0, elapsed(introMillis) / (double) MOTION_MILLIS);
        double eased = 1 - Math.pow(1 - t, 3);
        int position = t >= 1.0 ? steps : (int) Math.floor(eased * steps);
        return position % slots();
    }

    /** 능력 소개가 끝나기까지 남은 초(올림). 룰렛이 도는 동안은 소개 전체 길이 */
    public static int secondsLeft(long introMillis) {
        long left = Math.min(Math.max(0, introMillis), RaidRules.INTRO_INFO_MILLIS);
        return (int) ((left + 999) / 1000);
    }
}
