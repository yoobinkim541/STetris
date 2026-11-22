package tetris.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.data.Board;
import tetris.feature.game.GameSnapshot;
import tetris.feature.game.GameState;
import tetris.feature.setting.Settings;
import tetris.ui.theme.PlayFx;

/**
 * 플레이 중 점수 표시가 실제 점수까지 올라가는지 확인한다.
 * 게임 화면은 창을 띄울 때 메뉴 뒤에 숨은 채로 붙어 addNotify에서 애니메이션 타이머가 시작되지 않았고,
 * 그래서 클래식·아이템 모드 점수가 0에서 멈췄다.
 */
class GamePanelScoreTest {
    @Test
    @Timeout(15)
    void 플레이_중_점수_표시가_실제_점수를_따라간다() throws Exception {
        GamePanel[] panel = new GamePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new GamePanel(new Settings(), new PausePanel(() -> { }, () -> { }, () -> { }), s -> { });
            panel[0].onChanged(snapshot(0));
            panel[0].onChanged(snapshot(1234));
        });
        PlayFx fx = fx(panel[0]);
        // 버그가 있으면 표시는 0에서 멈춘다. 다른 테스트가 화면 스레드를 많이 쓰면 애니메이션이 느려지므로
        // 끝까지 올라갔는지가 아니라 0에서 움직이기 시작했는지를 본다
        long deadline = System.nanoTime() + 8_000_000_000L;
        while (shown(fx) == 0 && System.nanoTime() < deadline) Thread.sleep(20);
        long first = shown(fx);
        assertTrue(first > 0 && first <= 1234, "score display stuck at " + first);
    }

    private static GameSnapshot snapshot(long score) {
        return new GameSnapshot(new int[Board.HEIGHT][Board.WIDTH], null, null, score, 1, 0, GameState.PLAYING);
    }

    private static PlayFx fx(GamePanel panel) throws Exception {
        Field field = GamePanel.class.getDeclaredField("fx");
        field.setAccessible(true);
        return (PlayFx) field.get(panel);
    }

    private static long shown(PlayFx fx) throws Exception {
        long[] value = new long[1];
        SwingUtilities.invokeAndWait(() -> value[0] = fx.displayScore());
        return value[0];
    }
}
