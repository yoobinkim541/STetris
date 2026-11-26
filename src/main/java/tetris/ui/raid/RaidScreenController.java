package tetris.ui.raid;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import tetris.feature.raid.api.RaidClient;
import tetris.feature.raid.api.RaidPhase;
import tetris.feature.raid.api.RaidSnapshot;
import tetris.feature.setting.ControlStyle;
import tetris.feature.setting.GameAction;
import tetris.feature.setting.KeyMap;
import tetris.feature.versus.api.BattleClient;
import tetris.feature.versus.api.PlayerCommand;
import tetris.feature.versus.api.PlayerId;
import tetris.ui.Messages;
import tetris.ui.fx.GameSounds;
import tetris.ui.versus.BattleInputRouter;
import tetris.ui.versus.BattleKeyMap;

/**
 * 레이드 한 판의 키. 솔로는 설정의 {@link KeyMap}, 협동은 로컬 {@link BattleKeyMap}.
 * 필살은 P1 {@code R}, 협동 P2 {@code Enter}. 카드 선택은 선택권자의 좌우·하드 드롭.
 * 모던 DAS는 누르고 떼는 명령만 보내고 엔진이 반복한다 (대전 라우터와 같은 규칙).
 */
public final class RaidScreenController implements AutoCloseable {
    private final RaidClient client;
    private final RaidPanel panel;
    private final boolean solo;
    private final Runnable onMenu;
    private final Runnable onRetry;
    private final JRootPane root;
    /** 참가자별로 누르고 있는 카드 키. 한 화면 협동은 두 사람이 같은 동작을 다른 키로 누른다 */
    private final List<Set<GameAction>> cardHeld = List.of(EnumSet.noneOf(GameAction.class), EnumSet.noneOf(GameAction.class));
    private BattleInputRouter battleRouter;
    private SoloInputRouter soloRouter;
    private BattleClient.Subscription subscription;
    private RaidSnapshot current;
    /** 참가자별 카드 커서. 협동은 두 사람이 각자 고른다 */
    private final int[] cardIndex = {1, 1};
    private boolean pausedForConfirm;
    private boolean confirming;
    private boolean confirmYes;
    private boolean closed;
    private long sequence;
    private final PlayerId skipAs;
    /** 온라인에서 이 PC가 조작하는 참가자. 한 화면 플레이는 null */
    private final PlayerId viewer;

    public RaidScreenController(JRootPane root, RaidClient client, RaidPanel panel, KeyMap soloKeys, boolean solo,
                                ControlStyle style, Runnable onMenu, Runnable onRetry) {
        this(root, client, panel, soloKeys, solo, style, onMenu, onRetry, null);
    }

    private RaidScreenController(JRootPane root, RaidClient client, RaidPanel panel, KeyMap soloKeys, boolean solo,
                                 ControlStyle style, Runnable onMenu, Runnable onRetry, PlayerId onlineViewer) {
        this.root = Objects.requireNonNull(root, "root");
        this.client = Objects.requireNonNull(client, "client");
        this.panel = Objects.requireNonNull(panel, "panel");
        this.solo = solo;
        this.onMenu = Objects.requireNonNull(onMenu, "onMenu");
        this.onRetry = Objects.requireNonNull(onRetry, "onRetry");
        Objects.requireNonNull(style, "style");
        this.skipAs = onlineViewer == null ? PlayerId.ONE : onlineViewer;
        this.viewer = onlineViewer;
        panel.setGuide(onlineViewer != null ? onlineGuide(soloKeys) : guide(solo, soloKeys));
        panel.setResultActions(this::goMenu, this::goRetry);
        panel.setConfirmAction(this::cancelConfirm);
        panel.setPauseActions(this::togglePause, this::beginQuit);
        panel.setCardPointer(this::onCardPointer);
        panel.setIntroControls(introHint(solo, soloKeys, onlineViewer != null), () -> sendReady(skipAs));
        panel.setCreditsControls(creditsHint(solo, soloKeys, onlineViewer != null), () -> sendReady(skipAs), this::beginQuit);
        try {
            if (onlineViewer != null) {
                battleRouter = new BattleInputRouter(root, BattleKeyMap.online(soloKeys), this::onCommand, this::accepts);
                install(KeyEvent.VK_R, () -> {
                    if (!closed && !confirming && current != null && current.phase() == RaidPhase.PLAYING) {
                        client.ultimate(onlineViewer);
                    }
                });
            } else if (solo) soloRouter = new SoloInputRouter(root, soloKeys, this::onCommand, this::accepts);
            else battleRouter = new BattleInputRouter(root, new BattleKeyMap(), this::onCommand, this::accepts);
            if (onlineViewer == null) install(KeyEvent.VK_R, this::onUltimateP1);
            install(KeyEvent.VK_ENTER, this::onEnter); // 메뉴 상자 고르기. 솔로 키에는 Enter를 배정할 수 없다
            subscription = client.subscribe(this::onSnapshot);
        } catch (RuntimeException | Error failure) {
            close();
            try { client.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** 온라인. 이 PC의 키는 솔로 기본 키이고 필살은 R. 호스트는 P1, 클라이언트는 P2 */
    public static RaidScreenController online(JRootPane root, RaidClient client, RaidPanel panel, PlayerId viewer,
                                              ControlStyle style, Runnable onMenu, Runnable onRetry) {
        return online(root, client, panel, viewer, new KeyMap(), style, onMenu, onRetry);
    }

    /** 온라인. 이 PC의 키는 설정의 솔로 키({@code soloKeys})이고 필살은 R. 호스트는 P1, 클라이언트는 P2 */
    public static RaidScreenController online(JRootPane root, RaidClient client, RaidPanel panel, PlayerId viewer,
                                              KeyMap soloKeys, ControlStyle style, Runnable onMenu, Runnable onRetry) {
        return new RaidScreenController(root, client, panel, Objects.requireNonNull(soloKeys, "soloKeys"), false,
                style, onMenu, onRetry, viewer);
    }

    /** 테스트·결과 버튼이 그 참가자가 고르고 있는 카드를 본다 */
    public int cardSelection(PlayerId player) {
        return cardIndex[player.ordinal()];
    }

    public void focusLost() {
        if (closed) return;
        if (soloRouter != null) soloRouter.clearPressedKeys();
        if (battleRouter != null) battleRouter.clearPressedKeys();
        if (current != null && !confirming && (current.phase() == RaidPhase.PLAYING
                || current.phase() == RaidPhase.BOSS_INTRO || current.phase() == RaidPhase.COUNTDOWN)) {
            // 소개·카운트다운 중에 창을 떠나도 자리를 비운 채 판이 시작되지 않게 멈춘다
            sendPause();
        }
    }

    /** 결과 화면에서 고른 버튼을 누른다 */
    public void activateResult() {
        if (current != null && current.phase() == RaidPhase.FINISHED) panel.activateResult();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (soloRouter != null) soloRouter.close();
        if (battleRouter != null) battleRouter.close();
        uninstallExtra();
        if (subscription != null) subscription.close();
        client.close();
    }

    private void onSnapshot(RaidSnapshot snapshot) {
        if (closed) return;
        RaidSnapshot previous = current;
        current = snapshot;
        GameSounds.SHARED.onRaid(snapshot);
        boolean newOffer = snapshot.phase() == RaidPhase.CARD_PICK && (previous == null
                || previous.phase() != RaidPhase.CARD_PICK
                || !snapshot.offer().continues(previous.offer()));
        if (newOffer) {
            Arrays.fill(cardIndex, 1);
            for (Set<GameAction> held : cardHeld) held.clear();
        }
        panel.setCardCursors(cardCursors(snapshot));
        panel.setSnapshot(snapshot);
        if (snapshot.phase() == RaidPhase.FINISHED) {
            closeGameplayKeys();
            installResultKeys();
        }
    }

    private boolean accepts(GameAction action) {
        if (closed || current == null || current.phase() == RaidPhase.FINISHED) return false;
        if (confirming || current.phase() == RaidPhase.PAUSED) {
            return isMenuKey(action) || action == GameAction.QUIT
                    || (action == GameAction.PAUSE && !confirming);
        }
        if (current.phase() == RaidPhase.CARD_PICK) {
            return action == GameAction.MOVE_LEFT || action == GameAction.MOVE_RIGHT
                    || action == GameAction.HARD_DROP || action == GameAction.QUIT;
        }
        if (current.phase() == RaidPhase.BOSS_INTRO || current.phase() == RaidPhase.CREDITS) {
            return action == GameAction.HARD_DROP || action == GameAction.PAUSE || action == GameAction.QUIT;
        }
        if (action == GameAction.PAUSE || action == GameAction.QUIT) return true;
        return current.phase() == RaidPhase.PLAYING;
    }

    private void onCommand(PlayerCommand command) {
        if (closed || current == null) return;
        if (confirming) {
            handleConfirm(command);
            return;
        }
        if (command.action() == GameAction.QUIT && command.pressed()) {
            beginQuit();
            return;
        }
        if (current.phase() == RaidPhase.CARD_PICK) {
            handleCard(command);
            return;
        }
        if (current.phase() == RaidPhase.PAUSED && isMenuKey(command.action())) {
            handlePauseMenu(command);
            return;
        }
        client.send(new PlayerCommand(command.playerId(), command.action(), command.pressed(), sequence++));
    }

    /** 한 화면이면 누른 키의 참가자가, 온라인이면 이 PC의 참가자가 자기 카드를 고른다 */
    private void handleCard(PlayerCommand command) {
        RaidSnapshot.CardOffer offer = current.offer();
        int player = viewer != null ? viewer.ordinal() : command.playerId().ordinal();
        if (offer == null || player >= offer.picks().length || offer.picked(player)) return;
        Set<GameAction> held = cardHeld.get(player);
        if (!command.pressed()) {
            held.remove(command.action());
            return;
        }
        if (!held.add(command.action())) return;
        int next = switch (command.action()) {
            case MOVE_LEFT -> cardIndex[player] - 1;
            case MOVE_RIGHT -> cardIndex[player] + 1;
            case HARD_DROP -> {
                client.pickCard(PlayerId.values()[player], cardIndex[player]);
                yield cardIndex[player];
            }
            default -> cardIndex[player];
        };
        cardIndex[player] = Math.max(0, Math.min(2, next));
        panel.setCardCursors(cardCursors(current));
    }

    /**
     * 마우스는 한 사람 몫이다: 온라인이면 이 PC의 참가자, 한 화면이면 P1. P2는 자기 키로 고른다.
     * (마우스가 "아직 안 고른 사람"을 따라가면 한 사람이 두 번 눌러 두 사람 몫을 채울 수 있었다)
     * 커서가 있는 카드를 누르면 확정
     */
    private void onCardPointer(int index, boolean click) {
        if (closed || current == null || current.phase() != RaidPhase.CARD_PICK || current.offer() == null) return;
        int player = viewer != null ? viewer.ordinal() : 0;
        if (current.offer().picked(player)) return;
        int card = Math.max(0, Math.min(2, index));
        if (click && cardIndex[player] == card) client.pickCard(PlayerId.values()[player], card);
        cardIndex[player] = card;
        panel.setCardCursors(cardCursors(current));
    }

    /** 화면에 보일 커서. 이미 고른 사람과, 온라인에서 상대의 커서는 숨긴다(-1) */
    private int[] cardCursors(RaidSnapshot snapshot) {
        RaidSnapshot.CardOffer offer = snapshot.offer();
        int count = offer == null ? snapshot.players().size() : offer.picks().length;
        int[] cursors = new int[count];
        for (int i = 0; i < count; i++) {
            boolean mine = viewer == null || viewer.ordinal() == i;
            cursors[i] = mine && (offer == null || !offer.picked(i)) ? cardIndex[i] : -1;
        }
        return cursors;
    }

    /** 일시정지 상자: 두 사람 누구의 이동·회전·소프트 드롭 키로든 버튼을 옮기고, 하드 드롭으로 누른다 */
    private void handlePauseMenu(PlayerCommand command) {
        if (!command.pressed()) return;
        if (command.action() == GameAction.HARD_DROP) {
            GameSounds.SHARED.menuSelect();
            panel.activatePause();
            return;
        }
        GameSounds.SHARED.menuMove();
        panel.movePause(command.action() == GameAction.MOVE_LEFT || command.action() == GameAction.ROTATE ? -1 : 1);
    }

    /** 메뉴 상자에서 방향키 역할을 하는 키. 좌우 이동·회전(위)·소프트 드롭(아래)과 고르기(하드 드롭) */
    private static boolean isMenuKey(GameAction action) {
        return action == GameAction.MOVE_LEFT || action == GameAction.MOVE_RIGHT || action == GameAction.ROTATE
                || action == GameAction.SOFT_DROP || action == GameAction.HARD_DROP;
    }

    private void handleConfirm(PlayerCommand command) {
        if (!command.pressed()) return;
        switch (command.action()) {
            case MOVE_LEFT, MOVE_RIGHT, ROTATE, SOFT_DROP -> {
                confirmYes = !confirmYes;
                panel.setConfirm(true, confirmYes);
            }
            case HARD_DROP -> activateConfirm();
            case QUIT -> cancelConfirm();
            default -> {
            }
        }
    }

    private void onUltimateP1() {
        if (closed || confirming || current == null || current.phase() != RaidPhase.PLAYING) return;
        client.ultimate(PlayerId.ONE);
    }

    private void onEnter() {
        if (closed) return;
        if (confirming) {
            activateConfirm();
            return;
        }
        if (current != null && current.phase() == RaidPhase.PAUSED) {
            GameSounds.SHARED.menuSelect();
            panel.activatePause();
            return;
        }
        if (current != null && current.phase() == RaidPhase.FINISHED) {
            GameSounds.SHARED.menuSelect();
            panel.activateResult();
            return;
        }
        if (current != null && (current.phase() == RaidPhase.BOSS_INTRO || current.phase() == RaidPhase.CREDITS)) {
            // 한 화면 협동에서 Enter는 P2 키다. 온라인은 이 PC의 참가자
            sendReady(viewer != null ? viewer : solo ? PlayerId.ONE : PlayerId.TWO);
            return;
        }
        if (!solo && viewer == null && current != null && current.phase() == RaidPhase.PLAYING) client.ultimate(PlayerId.TWO);
    }

    /**
     * 보스 소개 건너뛰기·엔딩 크레딧 계속. 클릭은 온라인이면 이 PC의 참가자, 한 화면이면 P1이고, Enter는 P2다.
     * 한 사람이 같은 입력을 두 번 눌러 두 사람 몫을 채우지 못한다. 룰렛 중에는 엔진이 무시한다
     */
    private void sendReady(PlayerId who) {
        if (closed || confirming || current == null) return;
        if (current.phase() != RaidPhase.BOSS_INTRO && current.phase() != RaidPhase.CREDITS) return;
        client.send(new PlayerCommand(who, GameAction.HARD_DROP, true, sequence++));
        client.send(new PlayerCommand(who, GameAction.HARD_DROP, false, sequence++));
    }

    static String introHint(boolean solo, KeyMap keys, boolean online) {
        return readyHint(solo, keys, online, "바로 시작");
    }

    static String creditsHint(boolean solo, KeyMap keys, boolean online) {
        return readyHint(solo, keys, online, "계속 (다음 보스와 보상 카드)");
    }

    private static String readyHint(boolean solo, KeyMap keys, boolean online, String what) {
        if (online) return "두 사람 모두 하드 드롭을 누르면 " + what;
        if (!solo) return "두 사람 모두 하드 드롭(또는 Enter)을 누르면 " + what;
        KeyMap map = keys == null ? new KeyMap() : keys;
        return Messages.keyName(map, GameAction.HARD_DROP) + " 또는 Enter로 " + what;
    }

    private void beginQuit() {
        confirming = true;
        confirmYes = false;
        panel.setConfirm(true, false);
        if (battleRouter != null) battleRouter.clearGameplayKeys();
        if (soloRouter != null) soloRouter.clearPressedKeys();
        if (current != null && (current.phase() == RaidPhase.PLAYING
                || current.phase() == RaidPhase.BOSS_INTRO || current.phase() == RaidPhase.COUNTDOWN)) {
            pausedForConfirm = true;
            sendPause();
        }
    }

    private void activateConfirm() {
        if (confirmYes) goMenu();
        else cancelConfirm();
    }

    private void cancelConfirm() {
        confirming = false;
        panel.setConfirm(false, false);
        if (pausedForConfirm) {
            pausedForConfirm = false;
            sendPause();
        }
    }

    /** 화면 구석 일시정지 아이콘과 일시정지 상자의 계속 버튼. 일시정지 키와 같다 (키를 바꿔도 동작한다) */
    public void togglePause() {
        if (closed || current == null || confirming || !accepts(GameAction.PAUSE)) return;
        sendPause();
    }

    private void sendPause() {
        client.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, true, sequence++));
        client.send(new PlayerCommand(PlayerId.ONE, GameAction.PAUSE, false, sequence++));
    }

    private void goMenu() {
        close();
        onMenu.run();
    }

    private void goRetry() {
        close();
        onRetry.run();
    }

    private void closeGameplayKeys() {
        if (soloRouter != null) {
            soloRouter.close();
            soloRouter = null;
        }
        if (battleRouter != null) {
            battleRouter.close();
            battleRouter = null;
        }
    }

    private void installResultKeys() {
        install(KeyEvent.VK_LEFT, () -> moveResult(-1));
        install(KeyEvent.VK_RIGHT, () -> moveResult(1));
        install(KeyEvent.VK_UP, () -> moveResult(-1));
        install(KeyEvent.VK_DOWN, () -> moveResult(1));
    }

    private void moveResult(int delta) {
        GameSounds.SHARED.menuMove();
        panel.moveResult(delta);
    }

    private final java.util.Map<KeyStroke, Object> extra = new java.util.LinkedHashMap<>();

    private void install(int code, Runnable command) {
        KeyStroke stroke = KeyStroke.getKeyStroke(code, 0, false);
        Object name = new Object();
        extra.put(stroke, name);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!closed) command.run();
            }
        });
    }

    private void uninstallExtra() {
        var map = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        for (var entry : extra.entrySet()) {
            if (map.get(entry.getKey()) == entry.getValue()) map.remove(entry.getKey());
            root.getActionMap().remove(entry.getValue());
        }
        extra.clear();
    }

    public static String raceGuide() {
        BattleKeyMap battle = new BattleKeyMap();
        return "P1 " + String.join("  ", battle.playerLines(PlayerId.ONE)) + "  R 필살\n"
                + "P2 " + String.join("  ", battle.playerLines(PlayerId.TWO)) + "  Enter 필살\n"
                + battle.sharedLine();
    }

    private static String onlineGuide(KeyMap soloKeys) {
        return BattleKeyMap.online(soloKeys).guide() + "\nR 필살";
    }

    static String guide(boolean solo, KeyMap keys) {
        if (!solo) return raceGuide() + "\n카드는 좌우 후 하드 드롭";
        KeyMap map = keys == null ? new KeyMap() : keys;
        return Messages.keyName(map, GameAction.MOVE_LEFT) + "/" + Messages.keyName(map, GameAction.MOVE_RIGHT) + " 이동"
                + " · " + Messages.keyName(map, GameAction.ROTATE) + "/" + Messages.keyName(map, GameAction.ROTATE_CCW) + " 회전"
                + " · " + Messages.keyName(map, GameAction.HARD_DROP) + " 하드"
                + " · " + Messages.keyName(map, GameAction.HOLD) + " 홀드"
                + " · R 필살"
                + " · " + Messages.keyName(map, GameAction.PAUSE) + " 일시정지"
                + " · " + Messages.keyName(map, GameAction.QUIT) + " 나가기";
    }

}
