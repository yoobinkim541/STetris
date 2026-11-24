package tetris.ui.input;

import java.awt.Color;

/**
 * 화면 전체를 덮는 막(일시정지·확인·결과·카운트다운)의 색. 화면이 막을 그리지 않으면 null.
 * 전체 화면에서 {@link Stage}가 확대한 화면 밖 양옆 띠에도 같은 색을 칠해, 막이 가운데만 덮지 않게 한다.
 */
public interface ScreenVeil {
    Color screenVeil();
}
