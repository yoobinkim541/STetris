package tetris.feature.raid.api;

/** 레이드 진행 단계 */
public enum RaidPhase {
    /** 보스 추첨(룰렛) 뒤 능력 소개. 보드는 정지하고, 소개 단계에서는 하드 드롭으로 건너뛸 수 있다 */
    BOSS_INTRO,
    /** 소개가 끝난 뒤 3초 */
    COUNTDOWN,
    PLAYING,
    /** 보스 처치 연출 뒤 보상 카드 선택 (보드 정지) */
    CARD_PICK,
    PAUSED,
    FINISHED
}
