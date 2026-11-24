package tetris.play;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 결정적이고 90초 안에 끝나는 실제 창 시나리오. 긴 시나리오는 -Dtetris.play.long=true.
 * 보스마다 룰렛 3초와 카운트다운 3초는 건너뛸 수 없어, 보스 4마리를 만나는 빠른 시나리오도 1분을 조금 넘는다.
 */
@Timeout(120)
class SoakPlayTest {
    @Test
    void fastScenariosFinishWithoutStallOrException() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "창이 없는 검사에서는 건너뛴다");
        PlayHarness harness = new PlayHarness();
        long started = System.nanoTime();
        harness.runAll();
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        assertTrue(harness.failures().isEmpty(), harness.report());
        assertTrue(seconds < 90, "fast soak took " + seconds + "s\n" + harness.report());
    }
}
