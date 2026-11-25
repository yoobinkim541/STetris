package tetris.feature.raid.api;

/** 보스 패턴 (§3.2). 이름과 아이콘 파일 키는 화면이 쓴다 */
public enum PatternId {
    CLOUD("cloud", "점액 구름"),
    SUMMON_BLOCK("summon", "덩어리 소환"),
    ROCK_THROW("rock", "바위 투척"),
    PILLAR("pillar", "기둥 세우기"),
    CURSE_BLOCKS("curse", "저주 블록"),
    SEAL_PREVIEW("seal", "미리보기 봉인"),
    OVERLOAD("overload", "과부하"),
    CHAIN("chain", "연결 공격"),
    REVERSE("reverse", "반전 주문"),
    SPLIT("split", "분열"),
    EARTHQUAKE("quake", "지진"),
    FIRE_BREATH("fire", "화염 브레스");

    private final String assetKey;
    private final String displayName;

    PatternId(String assetKey, String displayName) {
        this.assetKey = assetKey;
        this.displayName = displayName;
    }

    public String assetKey() {
        return assetKey;
    }

    public String displayName() {
        return displayName;
    }

    /** 화면이 보여 주는 효과 설명(Patterns·RaidEngine의 실제 수치). 봉인은 바로 다음 1개를 가리지 않는다 */
    public String description() {
        return switch (this) {
            case CLOUD -> "보드 4×3칸을 5조각 동안 구름으로 가린다";
            case SUMMON_BLOCK -> "아래쪽 빈칸에 회색 덩어리 3~4칸을 박는다";
            case ROCK_THROW -> "쓰레기 줄 2줄을 밀어 올린다 (Lv.4부터 3줄)";
            case PILLAR -> "한 열에 회색 기둥 4칸을 세운다";
            case CURSE_BLOCKS -> "보여 준 다음 블록은 그대로 두고 그 뒤 2개를 S/Z로 바꾼다";
            case SEAL_PREVIEW -> "4조각 동안 바로 다음 1개만 보이고, 2·3번째와 홀드를 가린다";
            case OVERLOAD -> "5조각 동안 블록이 2배 빠르게 떨어진다";
            case CHAIN -> "쓰레기 1줄. 협동은 둘 다 맞고, 8조각 안에 둘 다 줄을 지워야 풀린다";
            case REVERSE -> "3조각 동안 좌우 이동이 반대로 바뀐다";
            case SPLIT -> "HP가 절반이 되면 미니 젤리로 나뉜다. 협동은 각자 1마리씩 잡는다";
            case EARTHQUAKE -> "구멍 1칸짜리 쓰레기 줄로 보드를 1줄 들어 올린다";
            case FIRE_BREATH -> "쓰레기 줄 2줄 (Lv.4부터 3줄). 협동은 둘 다, 솔로는 2배";
        };
    }

    /** 예고 띠에 이름과 함께 늘 보이는 짧은 효과. 발동까지 남은 조각 동안 읽을 수 있다 */
    public String shortEffect() {
        return switch (this) {
            case CLOUD -> "보드 일부 가림";
            case SUMMON_BLOCK -> "회색 덩어리";
            case ROCK_THROW -> "쓰레기 줄 2~3";
            case PILLAR -> "회색 기둥 4칸";
            case CURSE_BLOCKS -> "다음 2개 S/Z";
            case SEAL_PREVIEW -> "미리보기·홀드 가림";
            case OVERLOAD -> "낙하 2배속";
            case CHAIN -> "쓰레기 줄 1";
            case REVERSE -> "좌우 반대";
            case SPLIT -> "미니 젤리로 분열";
            case EARTHQUAKE -> "보드 1줄 상승";
            case FIRE_BREATH -> "쓰레기 줄 여러 개";
        };
    }
}
