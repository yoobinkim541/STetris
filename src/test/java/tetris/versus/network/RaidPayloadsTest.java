package tetris.versus.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import tetris.feature.network.protocol.FrameDecoder;
import tetris.feature.network.protocol.FrameEncoder;
import tetris.feature.network.protocol.Message;
import tetris.feature.network.protocol.RaidPayloads;
import tetris.feature.network.protocol.WireFormat;
import tetris.feature.raid.api.BossId;
import tetris.feature.raid.api.PatternId;
import tetris.feature.raid.api.RaidEvent;
import tetris.feature.raid.api.RaidMode;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.GameAction;
import tetris.feature.versus.api.MatchSnapshot;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.feature.versus.api.VersusRules;

class RaidPayloadsTest {
    @Test
    void inputStateAndResultRoundTrip() throws Exception {
        Message command = overWire(RaidPayloads.commandMessage("m1",
                new PlayerCommand(PlayerId.TWO, GameAction.HARD_DROP, true, 4)));
        RaidPayloads.RaidInput input = RaidPayloads.readInput(command);
        assertEquals(RaidPayloads.Kind.COMMAND, input.kind());
        assertEquals(PlayerId.TWO, input.command().playerId());
        assertEquals(4, input.sequence());

        Message ultimate = overWire(RaidPayloads.ultimateMessage("m1", PlayerId.TWO, 5));
        assertEquals(RaidPayloads.Kind.ULTIMATE, RaidPayloads.readInput(ultimate).kind());

        Message pick = overWire(RaidPayloads.pickMessage("m1", PlayerId.ONE, 2, 6));
        assertEquals(2, RaidPayloads.readInput(pick).index());

        RaidSnapshot snapshot = busy(RaidMode.RACE);
        Message state = overWire(RaidPayloads.stateMessage(snapshot));
        RaidSnapshot read = RaidPayloads.readState(state);
        assertEquals(snapshot.mode(), read.mode());
        assertEquals(snapshot.bossFor(1).hp(), read.bossFor(1).hp());
        assertEquals(snapshot.raceKills()[0], read.raceKills()[0]);
        assertEquals(snapshot.players().get(1).board().score(), read.players().get(1).board().score());
        assertEquals(RaidEvent.Type.DAMAGE, read.events().get(0).type());
        assertEquals(1, read.result().winner());

        Message result = overWire(RaidPayloads.resultMessage("raid-busy", snapshot.result()));
        assertEquals(1, RaidPayloads.readResult(result).winner());
        assertNull(RaidPayloads.readResult(overWire(RaidPayloads.resultMessage("raid-busy",
                new RaidSnapshot.Result(1, 2, 3)))).winner());
    }

    @Test
    void busySnapshotStaysUnderTheFrameLimit() throws Exception {
        byte[] frame = new FrameEncoder().encode(RaidPayloads.stateMessage(busy(RaidMode.COOP)));
        int body = frame.length - WireFormat.LENGTH_PREFIX_BYTES;
        assertTrue(body < WireFormat.MAX_FRAME_BYTES, "body " + body);
        byte[] race = new FrameEncoder().encode(RaidPayloads.stateMessage(busy(RaidMode.RACE)));
        assertTrue(race.length - WireFormat.LENGTH_PREFIX_BYTES < WireFormat.MAX_FRAME_BYTES);
    }

    @Test
    void bossIntroPhaseAndRemainingTimeCrossTheWire() throws Exception {
        var engine = new tetris.feature.raid.RaidEngine(new tetris.feature.raid.api.RaidConfig(RaidMode.COOP, 5L,
                tetris.feature.setting.ControlStyle.CLASSIC), () -> 0L);
        engine.advance(1_234);
        RaidSnapshot read = RaidPayloads.readState(overWire(RaidPayloads.stateMessage(engine.snapshot())));
        assertEquals(RaidPhase.BOSS_INTRO, read.phase());
        assertEquals(tetris.feature.raid.RaidRules.INTRO_MILLIS - 1_234, read.introMillis());
        assertEquals(engine.snapshot().boss().id(), read.boss().id());

        engine.submit(new PlayerCommand(PlayerId.TWO, GameAction.HARD_DROP, true, 0));
        engine.advance(tetris.feature.raid.RaidRules.INTRO_SPIN_MILLIS + 1);
        RaidSnapshot ready = RaidPayloads.readState(overWire(RaidPayloads.stateMessage(engine.snapshot())));
        assertEquals(RaidPhase.BOSS_INTRO, ready.phase(), "한 사람만 준비하면 소개가 이어진다");
        assertTrue(ready.readyFor(1) && !ready.readyFor(0), "누가 준비했는지 상대 화면에도 보인다");
        engine.close();

        RaidSnapshot picking = tetris.ui.raid.RaidSamples.phase(RaidMode.COOP, RaidPhase.CARD_PICK);
        RaidSnapshot.CardOffer half = new RaidSnapshot.CardOffer(picking.offer().cards(), new int[] {2, -1}, -1);
        RaidSnapshot withPick = new RaidSnapshot(picking.raidId(), picking.revision(), picking.mode(), picking.phase(),
                picking.boss(), picking.players(), picking.kills(), picking.elapsedMillis(), picking.gauge(), half,
                List.of(), null);
        RaidSnapshot.CardOffer crossed = RaidPayloads.readState(overWire(RaidPayloads.stateMessage(withPick))).offer();
        assertArrayEquals(new int[] {2, -1}, crossed.picks());
        assertEquals(-1, crossed.remainingMillis());
    }

    @Test
    void creditsPhaseAndWhoPressedContinueCrossTheWire() throws Exception {
        RaidSnapshot base = tetris.ui.raid.RaidSamples.phase(RaidMode.COOP, RaidPhase.PLAYING);
        RaidSnapshot credits = new RaidSnapshot(base.raidId(), base.revision(), base.mode(), RaidPhase.CREDITS, base.boss(),
                base.players(), 4, base.elapsedMillis(), base.gauge(), null, List.of(), null,
                List.of(), new int[0], new int[0], 0, new boolean[] {true, false});
        RaidSnapshot read = RaidPayloads.readState(overWire(RaidPayloads.stateMessage(credits)));
        assertEquals(RaidPhase.CREDITS, read.phase());
        assertEquals(4, read.kills());
        assertTrue(read.readyFor(0) && !read.readyFor(1), "누가 계속을 눌렀는지 상대 화면에도 보인다");
    }

    private static Message overWire(Message message) throws Exception {
        byte[] frame = new FrameEncoder().encode(message);
        return new FrameDecoder().feed(frame, 0, frame.length).get(0);
    }

    private static RaidSnapshot busy(RaidMode mode) {
        int[][] cells = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        int[][] ghost = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        int[][] lucky = new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS];
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                cells[row][col] = (row + col) % 8;
                ghost[row][col] = row == 19 ? 1 : 0;
                lucky[row][col] = col == 3 ? 1 : 0;
            }
        }
        int[][] shape = {{1, 1}, {1, 1}};
        var board = new MatchSnapshot.PlayerView(cells, shape, 900, List.of(new int[] {9, 0, 9, 9, 9, 9, 9, 9, 9, 9},
                new int[] {9, 9, 0, 9, 9, 9, 9, 9, 9, 9}), new int[VersusRules.BOARD_ROWS][VersusRules.BOARD_COLS],
                shape, ghost, List.of(shape, shape, shape));
        var effects = List.of(
                new RaidSnapshot.Effect(PatternId.CLOUD, 4, new int[] {8, 2, 4, 3}),
                new RaidSnapshot.Effect(PatternId.REVERSE, 2, new int[0]),
                new RaidSnapshot.Effect(PatternId.OVERLOAD, 5, new int[0]));
        java.util.ArrayList<int[][]> extra = new java.util.ArrayList<>();
        extra.add(shape);
        var player = new RaidSnapshot.PlayerView(board, true, lucky, effects, 0, 8, 400, extra);
        var boss = new RaidSnapshot.BossView(BossId.MECHA_DRAGON, 3, 120, 280, PatternId.FIRE_BREATH, 0, 4,
                new int[] {40, 80}, -1);
        var other = new RaidSnapshot.BossView(BossId.STONE_GOLEM, 3, 90, 280, PatternId.ROCK_THROW, 1, 2,
                new int[0], -1);
        boolean race = mode == RaidMode.RACE;
        return new RaidSnapshot("raid-busy", 40, mode, RaidPhase.FINISHED, boss, List.of(player, player), 3, 120_000,
                0, null, List.of(new RaidEvent(RaidEvent.Type.DAMAGE, 0, 70),
                new RaidEvent(RaidEvent.Type.DAMAGE, 0, 70), new RaidEvent(RaidEvent.Type.BOSS_ATTACK, 1, 2)),
                new RaidSnapshot.Result(2, 800, 120_000, race ? 1 : null),
                race ? List.of(boss, other) : List.of(),
                race ? new int[] {2, 1} : new int[0],
                race ? new int[] {40, 80} : new int[0], 0);
    }
}
