package tetris.ui.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.ui.theme.GameTheme;

/** 엔딩 크레딧의 글자는 모두 게임 글꼴(Do Hyeon)로 그린다. 못 그리는 글자가 섞이면 그 줄만 시스템 글꼴로 바뀐다 */
class EndingCreditsFontTest {
    @Test
    void everyCreditLineUsesTheGameFont() {
        String game = GameTheme.text(14, false).getFontName();
        List<String> fallback = new ArrayList<>();
        for (EndingCredits.Line line : EndingCredits.LINES) {
            if (line.text().isEmpty()) continue;
            if (!GameTheme.fontFor(line.text(), 14, false).getFontName().equals(game)) fallback.add(line.text());
        }
        assertEquals(List.of(), fallback, "게임 글꼴로 못 그리는 크레딧 줄");
    }

    @Test
    void creditsScreenCaptionsUseTheGameFont() {
        String game = GameTheme.text(14, false).getFontName();
        for (String caption : List.of("P1 계속 완료   " + EndingCredits.DOT + "   P2 보는 중",
                "잠시 감상해 주세요 " + EndingCredits.DOT + " 3초 뒤 계속할 수 있습니다",
                "보스 4종을 모두 물리쳤습니다")) {
            assertTrue(GameTheme.fontFor(caption, 14, true).getFontName().equals(game), caption);
        }
    }
}
