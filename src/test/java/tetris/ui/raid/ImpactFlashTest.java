package tetris.ui.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.VersusRules;

/** 새로 박힌 회색 칸만 반짝인다. 줄을 지워 기존 쓰레기 줄이 내려온 것은 새 칸이 아니다 */
class ImpactFlashTest {
    private static final int GRAY = VersusRules.ATTACK_CELL;

    @Test
    void lineClearThatMovesOldGarbageDownDoesNotFlash() throws Exception {
        RaidPanel panel = new RaidPanel(false);
        panel.setSnapshot(snapshot(1, new int[] {17, 18}));
        panel.setSnapshot(snapshot(2, new int[] {18, 19}));
        assertEquals(Long.MIN_VALUE, impactSince(panel), "자리만 옮긴 쓰레기 줄은 반짝이지 않는다");
        panel.removeNotify();
    }

    @Test
    void arrivingGarbageRowFlashes() throws Exception {
        RaidPanel panel = new RaidPanel(false);
        panel.setSnapshot(snapshot(1, new int[] {19}));
        panel.setSnapshot(snapshot(2, new int[] {18, 19}));
        assertNotEquals(Long.MIN_VALUE, impactSince(panel), "새 쓰레기 줄이 들어오면 반짝인다");
        panel.removeNotify();
    }

    private static long impactSince(RaidPanel panel) throws Exception {
        Field field = RaidPanel.class.getDeclaredField("impactSince");
        field.setAccessible(true);
        return ((long[]) field.get(panel))[0];
    }

    private static RaidSnapshot snapshot(long revision, int[] grayRows) {
        int[][] cells = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        for (int row : grayRows) {
            for (int col = 0; col < VersusRules.BOARD_COLS; col++) cells[row][col] = col == 3 ? 0 : GRAY;
        }
        var board = new MatchSnapshot.PlayerView(cells, new int[0][], 0, List.of(),
                new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS], new int[0][],
                new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS], List.of(), 0);
        var player = new RaidSnapshot.PlayerView(board, true, new int[20][10], List.of(), 0, 8, 0, List.of());
        var boss = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 1, 100, 165, PatternId.ROCK_THROW, 0, 5, new int[0], -1);
        return new RaidSnapshot("flash", revision, RaidMode.SOLO, RaidPhase.PLAYING, boss, List.of(player), 0, 1000L,
                0, null, List.of(), null);
    }
}
