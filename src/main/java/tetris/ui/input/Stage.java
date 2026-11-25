package tetris.ui.input;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import tetris.ui.fx.AssetStore;
import tetris.ui.theme.UiKit;

/**
 * 카드 덱을 세로 논리 크기로 두고, 창이 더 크면 g.scale로 가운데에 맞춘다.
 * 좌우 여백은 넓은 배경이다. 작은 버퍼를 늘려 그리지 않는다.
 * 마우스는 페인트 변환의 역으로 덱 좌표에 다시 보낸다.
 */
public final class Stage extends JPanel {
    public static final String WIDE_RAID = "/assets/raid/scene-raid-wide.jpg";

    private final JPanel deck;
    private final JPanel shim;
    private int logicalW = 1;
    private int logicalH = 1;
    private String backdrop;
    private Runnable pauseAction;
    private Runnable minimizeAction;
    private String notice = "";
    private final Rectangle pauseBox = new Rectangle();
    private final Rectangle minimizeBox = new Rectangle();
    private AffineTransform paint = new AffineTransform();
    private Color sideVeil;

    public Stage(JPanel deck) {
        super(null);
        this.deck = deck;
        shim = new JPanel(null) {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }
        };
        shim.setOpaque(false);
        shim.add(deck);
        setOpaque(true);
        add(shim);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    public void setLogicalSize(int width, int height) {
        logicalW = Math.max(1, width);
        logicalH = Math.max(1, height);
        revalidate();
        repaint();
    }

    public int logicalWidth() {
        return logicalW;
    }

    public int logicalHeight() {
        return logicalH;
    }

    public void setBackdrop(String path) {
        backdrop = path;
        repaint();
    }

    /** null이면 일시정지 아이콘을 그리지 않는다 */
    public void setPauseAction(Runnable action) {
        pauseAction = action;
        repaint();
    }

    /** 전체 화면에서만 건다. null이면 최소화 아이콘을 그리지 않는다 (테두리 없는 창에는 최소화 버튼이 없다) */
    public void setMinimizeAction(Runnable action) {
        minimizeAction = action;
        repaint();
    }

    /** 예외 안내. 화면 전체를 덮지 않는 한 줄이다 */
    public void setNotice(String text) {
        notice = text == null ? "" : text;
        repaint();
    }

    public Fit fit() {
        return Fit.contain(Math.max(1, getWidth()), Math.max(1, getHeight()), logicalW, logicalH);
    }

    /** 지금 그리기에 거는 변환. 테스트가 역변환을 확인한다 */
    public AffineTransform paintTransform() {
        return new AffineTransform(layoutTransform());
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(logicalW, logicalH);
    }

    @Override
    public void doLayout() {
        shim.setBounds(0, 0, logicalW, logicalH);
        deck.setBounds(0, 0, logicalW, logicalH);
        deck.doLayout();
    }

    /**
     * 화면 패널이 일부만 다시 그려도 이 Stage부터 그린다. 패널이 스스로 그리면 확대 변환이 빠져
     * 전체 화면 왼쪽 위에 원래 크기 화면이 한 장 더 겹친다.
     */
    @Override
    protected boolean isPaintingOrigin() {
        return true;
    }

    /** 자식이 요청한 영역은 덱 좌표라서 그리기 변환을 거친 영역도 함께 다시 그린다. 자기 영역 요청도 그대로 덮는다 */
    @Override
    public void paintImmediately(int x, int y, int w, int h) {
        AffineTransform layout = layoutTransform();
        if (layout.isIdentity()) {
            super.paintImmediately(x, y, w, h);
            return;
        }
        Rectangle asked = new Rectangle(x, y, w, h);
        Rectangle scaled = layout.createTransformedShape(asked).getBounds();
        scaled.grow(1, 1);
        Rectangle area = asked.union(scaled);
        super.paintImmediately(area.x, area.y, area.width, area.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            paintWide(g2);
        } finally {
            g2.dispose();
        }
    }

    /** 화면 → 양옆 막 → 구석 아이콘·안내 순서. 아이콘은 막 위에 있어야 일시정지 중에도 보인다 */
    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.transform(layoutTransform());
            super.paintChildren(g2);
        } finally {
            g2.dispose();
        }
        paintSideVeil(g);
        Graphics2D top = (Graphics2D) g.create();
        try {
            layoutCorner();
            paintMinimize(top);
            paintPause(top);
            paintNotice(top);
        } finally {
            top.dispose();
        }
    }

    /** 지금 화면이 막을 그리면 확대 영역 밖 띠도 같은 색으로 덮는다. 막이 바뀌면 띠까지 다시 그린다 */
    private void paintSideVeil(Graphics g) {
        Color veil = visibleVeil();
        if (veil != null) {
            Area sides = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
            sides.subtract(new Area(paint.createTransformedShape(new Rectangle(0, 0, logicalW, logicalH))));
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(veil);
                g2.fill(sides);
            } finally {
                g2.dispose();
            }
        }
        if (!java.util.Objects.equals(veil, sideVeil)) {
            sideVeil = veil;
            Rectangle clip = g.getClipBounds();
            if (clip != null && !clip.contains(0, 0, getWidth(), getHeight())) repaint();
        }
    }

    private Color visibleVeil() {
        for (Component screen : deck.getComponents()) {
            if (screen.isVisible() && screen instanceof ScreenVeil veiled) return veiled.screenVeil();
        }
        return null;
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED && pauseHit(e)) {
            e.consume();
            pauseAction.run();
            return;
        }
        if (e.getID() == MouseEvent.MOUSE_PRESSED && minimizeHit(e)) {
            e.consume();
            minimizeAction.run();
            return;
        }
        forward(e);
        super.processMouseEvent(e);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        if ((pauseAction != null && pauseBox.contains(e.getPoint()))
                || (minimizeAction != null && minimizeBox.contains(e.getPoint()))) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            forward(e);
        }
        super.processMouseMotionEvent(e);
    }

    private AffineTransform layoutTransform() {
        Fit fit = fit();
        AffineTransform layout = fit.transform();
        paint = layout;
        return layout;
    }

    private void paintWide(Graphics2D g) {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        int dw = AssetStore.devicePixels(g, w);
        int dh = AssetStore.devicePixels(g, h);
        BufferedImage image = backdrop == null ? null : AssetStore.SHARED.cover(backdrop, dw, dh);
        if (image == null && WIDE_RAID.equals(backdrop)) {
            image = AssetStore.SHARED.cover(AssetStore.BG_RAID, dw, dh);
        }
        if (image != null) {
            g.drawImage(image, 0, 0, w, h, null);
            return;
        }
        UiKit.paintScene(g, w, h);
    }

    /**
     * 오른쪽 위 모서리: 일시정지, 그 왼쪽에 최소화. 하나만 있으면 맨 오른쪽.
     * 확대한 화면 옆에 여백이 있으면(전체 화면) 여백에 큰 아이콘을 둔다. 여백이 없으면(창 모드) 화면 위에 작은 일시정지 아이콘만 둔다.
     * 그 자리(오른쪽 위 34px)는 레이드 타이머·대전·클래식 어느 화면에서도 글자를 가리지 않는다.
     */
    private void layoutCorner() {
        pauseBox.setBounds(0, 0, 0, 0);
        minimizeBox.setBounds(0, 0, 0, 0);
        if (getWidth() < 96 || getHeight() < 48) return;
        int icons = (pauseAction != null ? 1 : 0) + (minimizeAction != null ? 1 : 0);
        if (icons == 0) return;
        int size = 36;
        AffineTransform layout = layoutTransform();
        double margin = getWidth() - (layout.getTranslateX() + logicalW * layout.getScaleX());
        if (margin < 16 + icons * size + (icons - 1) * 10 + 8) {
            if (pauseAction != null) pauseBox.setBounds(getWidth() - 8 - 26, 8, 26, 26);
            return;
        }
        int right = getWidth() - 16;
        if (pauseAction != null) {
            pauseBox.setBounds(right - size, 16, size, size);
            right -= size + 10;
        }
        if (minimizeAction != null) minimizeBox.setBounds(right - size, 16, size, size);
    }

    private void paintMinimize(Graphics2D g) {
        if (minimizeBox.width <= 0) return;
        g.setColor(new Color(8, 12, 28, 190));
        g.fillRoundRect(minimizeBox.x, minimizeBox.y, minimizeBox.width, minimizeBox.height, 12, 12);
        g.setColor(Color.WHITE);
        int barW = 14;
        g.fillRoundRect(minimizeBox.x + (minimizeBox.width - barW) / 2, minimizeBox.y + minimizeBox.height / 2 + 3,
                barW, 3, 2, 2);
    }

    private void paintPause(Graphics2D g) {
        if (pauseBox.width <= 0) return;
        g.setColor(new Color(8, 12, 28, 190));
        g.fillRoundRect(pauseBox.x, pauseBox.y, pauseBox.width, pauseBox.height, 12, 12);
        g.setColor(Color.WHITE);
        int barW = 4;
        int gap = 4;
        int barH = 14;
        int x = pauseBox.x + (pauseBox.width - barW * 2 - gap) / 2;
        int y = pauseBox.y + (pauseBox.height - barH) / 2;
        g.fillRoundRect(x, y, barW, barH, 2, 2);
        g.fillRoundRect(x + barW + gap, y, barW, barH, 2, 2);
    }

    private void paintNotice(Graphics2D g) {
        if (notice.isEmpty() || getWidth() < 80) return;
        g.setFont(tetris.ui.theme.GameTheme.text(13, false));
        int width = Math.min(getWidth() - 24, g.getFontMetrics().stringWidth(notice) + 24);
        int x = (getWidth() - width) / 2;
        int y = getHeight() - 36;
        g.setColor(new Color(8, 12, 28, 210));
        g.fillRoundRect(x, y, width, 26, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(notice, x + 12, y + 18);
    }

    private boolean pauseHit(MouseEvent e) {
        return pauseAction != null && e.getButton() == MouseEvent.BUTTON1 && pauseBox.contains(e.getPoint());
    }

    private boolean minimizeHit(MouseEvent e) {
        return minimizeAction != null && e.getButton() == MouseEvent.BUTTON1 && minimizeBox.contains(e.getPoint());
    }

    private void forward(MouseEvent e) {
        Point2D.Double logical = new Point2D.Double();
        try {
            layoutTransform().inverseTransform(new Point2D.Double(e.getX(), e.getY()), logical);
        } catch (NoninvertibleTransformException ex) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        int x = (int) Math.floor(logical.x);
        int y = (int) Math.floor(logical.y);
        if (x < 0 || y < 0 || x >= logicalW || y >= logicalH) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        Component target = SwingUtilities.getDeepestComponentAt(deck, x, y);
        if (target == null) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        Point inTarget = SwingUtilities.convertPoint(deck, new Point(x, y), target);
        MouseEvent converted = new MouseEvent(target, e.getID(), e.getWhen(), e.getModifiersEx(),
                inTarget.x, inTarget.y, e.getXOnScreen(), e.getYOnScreen(),
                e.getClickCount(), e.isPopupTrigger(), e.getButton());
        target.dispatchEvent(converted);
        setCursor(target.getCursor());
    }
}
