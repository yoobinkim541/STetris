package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import tetris.feature.versus.api.MatchPhase;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.VersusRules;
import tetris.ui.versus.BattlePanel;

/**
 * 대전 화면에 착지 지점이 실제로 보이는지 확인한다.
 * 예전에는 착지 지점을 먼저 그리고 보드 배경을 위에 칠해서, 착지 정보가 있어도 화면에는 보이지 않았다.
 */
class BattleLandingRenderingTest {
    private static final int T = 6; // T 블록 칸 값

    @Test
    void 착지_지점이_있으면_대전_화면이_달라진다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (boolean colorBlind : new boolean[] {false, true}) {
                BufferedImage with = render(colorBlind, true);
                BufferedImage without = render(colorBlind, false);
                int changed = 0;
                for (int y = 0; y < with.getHeight(); y++) {
                    for (int x = 0; x < with.getWidth(); x++) if (with.getRGB(x, y) != without.getRGB(x, y)) changed++;
                }
                assertTrue(changed > 200, "landing mark is not visible (colorBlind=" + colorBlind + ", changed=" + changed + ")");
            }
        });
    }

    private static BufferedImage render(boolean colorBlind, boolean ghost) {
        BattlePanel panel = new BattlePanel(colorBlind);
        panel.setSnapshot(new MatchSnapshot("landing", 0, player(ghost), player(ghost), -1, MatchPhase.PLAYING, null));
        panel.setSize(1280, 800);
        panel.doLayout();
        BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        return image;
    }

    // 맨 위에 떨어지는 T 블록, 맨 아래에 그 착지 지점
    private static MatchSnapshot.PlayerView player(boolean ghost) {
        int rows = VersusRules.BOARD_ROWS;
        int cols = VersusRules.BOARD_COLS;
        int[][] cells = new int[rows][cols];
        cells[0][4] = T;
        cells[1][3] = T;
        cells[1][4] = T;
        cells[1][5] = T;
        int[][] landing = new int[rows][cols];
        if (ghost) {
            landing[rows - 2][4] = T;
            landing[rows - 1][3] = T;
            landing[rows - 1][4] = T;
            landing[rows - 1][5] = T;
        }
        return new MatchSnapshot.PlayerView(cells, new int[0][], 0, List.of(), new int[rows][cols], new int[0][], landing,
                List.of(), 0);
    }
}
