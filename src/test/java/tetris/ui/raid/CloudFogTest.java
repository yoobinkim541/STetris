package tetris.ui.raid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class CloudFogTest {
    private static final int CELL = 24;
    private static final Rectangle PLAY = new Rectangle(0, 0, 10 * CELL, 20 * CELL);
    private static final int[] AREA = {16, 1, 4, 3};

    @Test
    void cloudHidesWhatIsUnderIt() {
        BufferedImage onBlack = render(Color.BLACK, false, 1f);
        BufferedImage onWhite = render(Color.WHITE, false, 1f);
        assertHidden(onBlack, onWhite);
    }

    @Test
    void colorBlindCloudAlsoHides() {
        assertHidden(render(Color.BLACK, true, 1f), render(Color.WHITE, true, 1f));
    }

    @Test
    void outsideTheAreaIsUntouched() {
        BufferedImage image = render(Color.BLACK, false, 1f);
        assertTrue((image.getRGB(5 * CELL + 3, 3 * CELL) & 0xFFFFFF) == 0, "구름 영역 밖은 그대로여야 한다");
    }

    /** 배경이 검정이든 흰색이든 구름 안쪽 색이 거의 같으면, 그 아래 블록은 보이지 않는다 */
    private static void assertHidden(BufferedImage a, BufferedImage b) {
        int inset = CELL / 2;
        int x0 = AREA[1] * CELL + inset;
        int y0 = AREA[0] * CELL + inset;
        int x1 = (AREA[1] + AREA[3]) * CELL - inset;
        int y1 = (AREA[0] + AREA[2]) * CELL - inset;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int diff = maxChannelDiff(a.getRGB(x, y), b.getRGB(x, y));
                assertTrue(diff <= 24, "구름 아래가 비친다 (" + x + "," + y + ") 차이 " + diff);
            }
        }
    }

    private static BufferedImage render(Color background, boolean colorBlind, float alpha) {
        BufferedImage image = new BufferedImage(PLAY.width, PLAY.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(background);
            g.fillRect(0, 0, PLAY.width, PLAY.height);
            new CloudFog().paint(g, PLAY, AREA, CELL, colorBlind, alpha);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int maxChannelDiff(int p, int q) {
        int max = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            max = Math.max(max, Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF)));
        }
        return max;
    }
}
