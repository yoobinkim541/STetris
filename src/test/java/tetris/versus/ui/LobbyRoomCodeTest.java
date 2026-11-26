package tetris.versus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tetris.feature.network.lobby.LobbySnapshot;
import tetris.feature.versus.api.BattleMode;
import tetris.feature.versus.api.MatchConfig;
import tetris.relay.RelayRoomMode;
import tetris.ui.versus.P2pLobbyPanel;

/** 방을 만들면 지은 제목이 대기실에 보이고, 방 코드는 버튼·C 키로 클립보드에 복사된다 */
@Timeout(30)
class LobbyRoomCodeTest {
    @Test
    void 대기실의_코드_복사_버튼과_C_키가_방_코드를_복사한다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> copied = new ArrayList<>();
            P2pLobbyPanel panel = panel();
            panel.setClipboard(copied::add);
            panel.render(waiting(), "같은 LAN 대기");
            assertTrue(panel.hitScreenBounds("btn-copy").isEmpty(), "방 코드가 없는 LAN 대기실에는 복사 버튼이 없다");

            panel.setInternetRoom("K7Q-M2X");
            panel.render(waiting(), "상대 접속 대기 중");
            click(panel, "btn-copy");
            assertEquals(List.of("K7Q-M2X"), copied);
            assertTrue(panel.statusText().contains("복사했습니다"), panel.statusText());

            key(panel, KeyEvent.VK_C);
            assertEquals(List.of("K7Q-M2X", "K7Q-M2X"), copied);
        });
    }

    @Test
    void 클립보드를_못_쓰면_코드를_직접_알려_주라고_안내한다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            P2pLobbyPanel panel = panel();
            panel.setClipboard(text -> { throw new IllegalStateException("busy"); });
            panel.setInternetRoom("K7Q-M2X");
            panel.render(waiting(), "");
            panel.copyRoomCode();
            assertTrue(panel.statusText().contains("K7Q-M2X") && panel.statusText().contains("직접"), panel.statusText());
        });
    }

    @Test
    void 방을_만들면_지은_제목이_대기실_제목이_된다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            P2pLobbyPanel panel = panel();
            panel.prepareCreate("금요일 한 판", true, RelayRoomMode.CLASSIC);
            click(panel, "btn-create");
            click(panel, "btn-make");
            assertEquals("금요일 한 판", panel.roomTitle());
        });
    }

    private static LobbySnapshot waiting() {
        return new LobbySnapshot(LobbySnapshot.Role.HOST, LobbySnapshot.State.LOBBY,
                MatchConfig.of(BattleMode.CLASSIC), 1, false, false, "peer");
    }

    private static P2pLobbyPanel panel() {
        P2pLobbyPanel panel = new P2pLobbyPanel(new P2pLobbyPanel.Actions() {
            @Override public void host(String port) { }
            @Override public void connect(String address, String port) { }
            @Override public void selectMode(BattleMode mode) { }
            @Override public void selectRaid(tetris.feature.raid.api.RaidMode mode) { }
            @Override public void toggleReady() { }
            @Override public void cancelOrBack() { }
            @Override public void hostInternet() { }
            @Override public void joinInternet(String code) { }
            @Override public void refreshRooms() { }
            @Override public void joinPublic(int routeIndex, String id) { }
        });
        panel.setPreferredSize(new java.awt.Dimension(640, 760));
        panel.setSize(640, 760);
        return panel;
    }

    private static void click(P2pLobbyPanel panel, String id) {
        java.awt.Rectangle box = panel.hitScreenBounds(id);
        assertTrue(box != null, "missing " + id);
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0,
                box.x + 8, box.y + 4, 1, false, MouseEvent.BUTTON1));
    }

    private static void key(P2pLobbyPanel panel, int code) {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(panel,
                new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
    }
}
