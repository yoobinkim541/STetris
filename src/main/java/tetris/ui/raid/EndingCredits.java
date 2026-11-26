package tetris.ui.raid;

import java.util.List;

/**
 * 레이드에서 보스 4종을 처음 모두 물리치면 올라가는 엔딩 크레딧 내용.
 * 사람별 역할은 저장소 커밋 기록(작성자별로 고친 영역과 커밋 제목)으로 정리했다.
 * 그림ㆍ소리ㆍ글꼴 출처는 CREDITS.md, fonts/CREDITS.txt와 같다.
 */
final class EndingCredits {
    /** Do Hyeon에는 가운뎃점(U+00B7)이 없어 그 줄만 시스템 글꼴로 바뀌었다. 크레딧은 한글 아래아(ㆍ)로 쓴다 */
    static final String DOT = "ㆍ";

    enum Kind { TITLE, SUBTITLE, SECTION, NAME, TEXT, GAP }

    record Line(Kind kind, String text) {
    }

    static final List<Line> LINES = List.of(
            line(Kind.TITLE, "SE TETRIS"),
            line(Kind.SUBTITLE, "Team 6"),
            gap(),
            line(Kind.SECTION, "만든 사람들"),
            line(Kind.NAME, "Yoobin Kim"),
            line(Kind.TEXT, "요구사항ㆍ설계 명세, 게임 보드와 줄 삭제 처리"),
            line(Kind.TEXT, "로컬ㆍ온라인 대전 화면, 입력, 결과"),
            line(Kind.TEXT, "보스 레이드 엔진과 화면, 보스 추첨, 보상 카드"),
            line(Kind.TEXT, "크리스털 테마, 효과음ㆍ배경음, 전체 화면ㆍ마우스"),
            line(Kind.TEXT, "요구사항 감사, CI, 릴리스"),
            gap(),
            line(Kind.NAME, "IAmNew"),
            line(Kind.TEXT, "온라인 대전 네트워크: 전송 프레임, P2P 로비"),
            line(Kind.TEXT, "연결 끊김 판정, 최근 접속 주소"),
            line(Kind.TEXT, "인터넷 방 중계 서버와 배포"),
            line(Kind.TEXT, "LAN 지연 측정 도구, 평가 빌드 번들"),
            line(Kind.TEXT, "난이도별 블록 생성기, 아이템과 지속 효과 표시"),
            gap(),
            line(Kind.NAME, "simsgood2"),
            line(Kind.TEXT, "블록 구조와 첫 화면들(메뉴ㆍ게임ㆍ일시정지ㆍ스코어보드)"),
            line(Kind.TEXT, "대전 계약: 모드, 스냅샷, 공격 줄, 10줄 대기열"),
            line(Kind.TEXT, "SRS 회전, T-스핀, Lock Delay, 7-bag 생성기"),
            line(Kind.TEXT, "난이도 설정 저장"),
            gap(),
            line(Kind.SECTION, "그림"),
            line(Kind.TEXT, "보스ㆍ카드ㆍ배경ㆍ아이콘: gpt-image-2(Codex CLI)로 생성"),
            gap(),
            line(Kind.SECTION, "소리"),
            line(Kind.TEXT, "효과음ㆍ배경음: 팀 스크립트로 직접 합성"),
            gap(),
            line(Kind.SECTION, "글꼴"),
            line(Kind.TEXT, "Black Han Sans ㆍ Do Hyeon ㆍ Orbitron (SIL OFL 1.1)"),
            gap(),
            line(Kind.SECTION, "만든 도구"),
            line(Kind.TEXT, "Java 21 ㆍ Swing ㆍ Gradle ㆍ Jackson ㆍ JUnit 5"),
            gap(),
            gap(),
            line(Kind.SUBTITLE, "플레이해 주셔서 감사합니다"));

    private EndingCredits() {
    }

    private static Line line(Kind kind, String text) {
        return new Line(kind, text);
    }

    private static Line gap() {
        return new Line(Kind.GAP, "");
    }
}
