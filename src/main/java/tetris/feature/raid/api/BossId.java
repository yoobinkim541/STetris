package tetris.feature.raid.api;

/** 보스 4종 + 숨은 보스 (§3.1). 에셋 파일 이름은 {@link #assetKey()} */
public enum BossId {
    JELLY_KING("jelly", "젤리 킹", "거대 슬라임. 시야를 가리고 덩어리를 떨군다"),
    STONE_GOLEM("golem", "석상 골렘", "고대 돌 거인. 쓰레기 줄과 기둥으로 바닥을 밀어 올린다"),
    NIGHT_WITCH("witch", "밤의 마녀", "마법사. 다음 블록과 미리보기, 조작을 뒤튼다"),
    MECHA_DRAGON("dragon", "기계 용", "메카 드래곤. 낙하 속도를 올리고 불을 뿜는다"),
    /** 숨은 보스. 패턴 없이 15초 뒤 도망간다 */
    GOLDEN_TETRO("golden", "황금 테트로", "숨은 보스. 공격하지 않지만 15초 안에 못 잡으면 도망간다");

    private final String assetKey;
    private final String displayName;
    private final String tagline;

    BossId(String assetKey, String displayName, String tagline) {
        this.assetKey = assetKey;
        this.displayName = displayName;
        this.tagline = tagline;
    }

    public String assetKey() {
        return assetKey;
    }

    public String displayName() {
        return displayName;
    }

    /** 보스 소개 화면의 한 줄 설명 */
    public String tagline() {
        return tagline;
    }
}
