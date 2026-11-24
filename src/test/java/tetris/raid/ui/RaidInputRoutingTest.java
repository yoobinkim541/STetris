package tetris.raid.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.KeyMap;
import tetris.feature.versus.api.PlayerId;
import tetris.ui.raid.FakeRaidClient;
import tetris.ui.raid.RaidPanel;
import tetris.ui.raid.RaidSamples;
import tetris.ui.raid.RaidScreenController;

/** 협동 카드는 두 사람이 각자 자기 키로 고르고, 필살은 R / Enter가 각자 플레이어로 간다 */
@Timeout(30)
class RaidInputRoutingTest {
    @Test
    void cardPickAndUltimateRouteToTheRightPlayer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JRootPane root = new JRootPane();
            FakeRaidClient coop = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.CARD_PICK));
            RaidPanel panel = new RaidPanel(false);
            List<String> left = new ArrayList<>();
            try (var controller = new RaidScreenController(root, coop, panel, new KeyMap(), false, ControlStyle.MODERN,
                    () -> left.add("menu"), () -> left.add("retry"))) {
                assertEquals(1, controller.cardSelection(PlayerId.ONE));
                key(root, KeyEvent.VK_D);
                key(root, KeyEvent.VK_F);
                assertEquals(List.of(new FakeRaidClient.Pick(PlayerId.ONE, 2)), coop.picks(), "P1은 자기 키로 자기 카드를 고른다");
                assertEquals(1, controller.cardSelection(PlayerId.TWO), "P1 키는 P2 커서를 움직이지 않는다");
                key(root, KeyEvent.VK_LEFT);
                key(root, KeyEvent.VK_SPACE);
                assertEquals(List.of(new FakeRaidClient.Pick(PlayerId.ONE, 2), new FakeRaidClient.Pick(PlayerId.TWO, 0)),
                        coop.picks());
            }

            FakeRaidClient playing = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.PLAYING));
            try (var controller = new RaidScreenController(root, playing, new RaidPanel(false), new KeyMap(), false,
                    ControlStyle.MODERN, () -> left.add("menu"), () -> left.add("retry"))) {
                key(root, KeyEvent.VK_R);
                key(root, KeyEvent.VK_ENTER);
                assertEquals(List.of(PlayerId.ONE, PlayerId.TWO), playing.ultimates());
                playing.publish(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.FINISHED));
                controller.activateResult();
            }
            assertEquals(List.of("menu"), left);

            FakeRaidClient solo = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.SOLO, RaidPhase.PLAYING));
            try (var ignored = new RaidScreenController(root, solo, new RaidPanel(false), new KeyMap(), true,
                    ControlStyle.MODERN, () -> { }, () -> { })) {
                key(root, KeyEvent.VK_ENTER);
                key(root, KeyEvent.VK_R);
                assertEquals(List.of(PlayerId.ONE), solo.ultimates(), "솔로 Enter는 P2 필살이 아니다");
            }
        });
    }

    /** 한 화면 협동: 클릭은 P1, Enter는 P2 몫이라 한 사람이 같은 입력을 두 번 눌러 두 사람 몫을 채우지 못한다 */
    @Test
    void oneInputCannotActForBothPlayers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JRootPane root = new JRootPane();
            FakeRaidClient intro = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.BOSS_INTRO));
            RaidPanel panel = new RaidPanel(false);
            try (var ignored = new RaidScreenController(root, intro, panel, new KeyMap(), false, ControlStyle.MODERN,
                    () -> { }, () -> { })) {
                Runnable click = (Runnable) field(panel, "onSkipIntro");
                click.run();
                click.run();
                key(root, KeyEvent.VK_ENTER);
                key(root, KeyEvent.VK_ENTER);
                List<PlayerId> pressedBy = intro.sent().stream()
                        .filter(c -> c.action() == tetris.feature.setting.GameAction.HARD_DROP && c.pressed())
                        .map(c -> c.playerId()).toList();
                assertEquals(List.of(PlayerId.ONE, PlayerId.ONE, PlayerId.TWO, PlayerId.TWO), pressedBy,
                        "클릭은 두 번 눌러도 P1, Enter는 두 번 눌러도 P2");
            }

            FakeRaidClient cards = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.CARD_PICK));
            RaidPanel cardPanel = new RaidPanel(false);
            try (var ignored = new RaidScreenController(root, cards, cardPanel, new KeyMap(), false, ControlStyle.MODERN,
                    () -> { }, () -> { })) {
                @SuppressWarnings("unchecked")
                var pointer = (java.util.function.BiConsumer<Integer, Boolean>) field(cardPanel, "cardPointer");
                pointer.accept(2, false);
                pointer.accept(2, true);
                pointer.accept(0, false);
                pointer.accept(0, true);
                assertTrue(!cards.picks().isEmpty() && cards.picks().stream().allMatch(p -> p.player() == PlayerId.ONE),
                        "마우스는 P1 몫만 고른다: " + cards.picks());
            }
        });
    }

    /**
     * 온라인 손님(P2): 이 PC의 키는 P1 자리(BattleKeyMap.online)로 들어온다. 예전에는 P2 차례에 이 키가 버려져
     * 손님이 카드를 못 골랐고 10초 뒤 자동으로 넘어갔다. 이제 손님 키는 손님(P2)의 카드를 고른다
     */
    @Test
    void onlineGuestKeysPickTheGuestsCard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JRootPane root = new JRootPane();
            FakeRaidClient cards = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.CARD_PICK));
            try (var ignored = RaidScreenController.online(root, cards, new RaidPanel(false), PlayerId.TWO,
                    ControlStyle.MODERN, () -> { }, () -> { })) {
                key(root, KeyEvent.VK_RIGHT);
                key(root, KeyEvent.VK_SPACE);
                assertEquals(List.of(new FakeRaidClient.Pick(PlayerId.TWO, 2)), cards.picks());
            }

            FakeRaidClient hostCards = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.CARD_PICK));
            try (var ignored = RaidScreenController.online(root, hostCards, new RaidPanel(false), PlayerId.ONE,
                    ControlStyle.MODERN, () -> { }, () -> { })) {
                key(root, KeyEvent.VK_LEFT);
                key(root, KeyEvent.VK_SPACE);
                assertEquals(List.of(new FakeRaidClient.Pick(PlayerId.ONE, 0)), hostCards.picks());
            }

            FakeRaidClient intro = new FakeRaidClient(RaidSamples.phase(tetris.feature.raid.api.RaidMode.COOP, RaidPhase.BOSS_INTRO));
            try (var ignored = RaidScreenController.online(root, intro, new RaidPanel(false), PlayerId.TWO,
                    ControlStyle.MODERN, () -> { }, () -> { })) {
                key(root, KeyEvent.VK_SPACE);
                assertTrue(intro.sent().stream().anyMatch(c -> c.action() == tetris.feature.setting.GameAction.HARD_DROP
                        && c.pressed()), "손님의 하드 드롭은 소개 준비로 보낸다 (ClientRaidClient가 P2로 실어 보낸다)");
            }
        });
    }

    private static Object field(Object owner, String name) {
        try {
            var found = owner.getClass().getDeclaredField(name);
            found.setAccessible(true);
            return found.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void key(JRootPane root, int code) {
        Object name = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(code, 0, false));
        assertTrue(name != null, "missing binding " + code);
        var action = root.getActionMap().get(name);
        assertTrue(action != null && action.isEnabled());
        action.actionPerformed(new ActionEvent(root, 0, "test"));
    }
}
