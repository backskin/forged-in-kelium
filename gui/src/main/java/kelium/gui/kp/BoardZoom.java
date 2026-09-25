package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import kelium.gui.replay2.BoardSheet;
import kelium.gui.replay2.Theme;
import kelium.gui.replay2.Ui2;

/**
 * ПЛАНШЕТ КРУПНО — поверх всего окна (заказ дизайнера 25.09.2026: «планшеты
 * маленькие на экране; при нажатии на планшет — отдельно на каждый — увеличить
 * на весь экран как модальное окно, и чтобы можно было получить подсказку по
 * каждому элементу»).
 *
 * <p>Рисует тот же лист игрока, что и стол ({@link BoardSheet#paintBoardZoom}),
 * поэтому на увеличенном планшете лежит ровно то, что на столе. Деталь под
 * мышью обводится цветом места, подсказка говорит, что это и в каком она
 * состоянии. Щелчок мимо планшета или Esc — закрыть.
 */
public final class BoardZoom extends JComponent {

    private static final long serialVersionUID = 1L;

    private BoardSheet sheet;
    private String which = "troop";
    private String title = "";
    private int seat;
    private Rectangle board;
    private final Map<String, Rectangle> hits = new LinkedHashMap<>();
    private final Map<Rectangle, Object[]> modules = new LinkedHashMap<>();
    private final Map<Rectangle, String> stores = new LinkedHashMap<>();
    private Rectangle hot;

    public BoardZoom() {
        setOpaque(false);
        setVisible(false);
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Rectangle was = hot;
                hot = detailAt(e.getPoint());
                if (!java.util.Objects.equals(was, hot)) {
                    repaint();
                }
                setCursor(board != null && board.contains(e.getPoint())
                    ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (board == null || !board.contains(e.getPoint())) {
                    close();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        // колесо и щелчки не должны проваливаться на стол под окном
        addMouseWheelListener(e -> { });
    }

    /**
     * Открыть планшет.
     *
     * @param which {@code troop} или {@code storage}
     */
    public void open(BoardSheet sheet, int seat, String which, String playerName) {
        this.sheet = sheet;
        this.seat = seat;
        this.which = which;
        this.title = ("storage".equals(which) ? "Планшет хранилища" : "Планшет войск")
            + " · " + playerName;
        hot = null;
        setVisible(true);
        requestFocusInWindow();
        repaint();
    }

    public boolean isOpen() {
        return isVisible();
    }

    public void close() {
        setVisible(false);
        hot = null;
    }

    /** Самая мелкая деталь под точкой: модули и жетоны раньше фона планшета. */
    private Rectangle detailAt(Point p) {
        Rectangle best = null;
        long bestArea = Long.MAX_VALUE;
        for (Rectangle r : modules.keySet()) {
            if (r.contains(p) && area(r) < bestArea) {
                best = r;
                bestArea = area(r);
            }
        }
        for (Rectangle r : stores.keySet()) {
            if (r.contains(p) && area(r) < bestArea) {
                best = r;
                bestArea = area(r);
            }
        }
        for (Map.Entry<String, Rectangle> e : hits.entrySet()) {
            Rectangle r = e.getValue();
            if (isBackground(e.getKey()) || !r.contains(p)) {
                continue;
            }
            long a = area(r);
            if (a < bestArea) {
                best = r;
                bestArea = a;
            }
        }
        return best;
    }

    private static boolean isBackground(String key) {
        return "troop".equals(key) || "storage".equals(key);
    }

    private static long area(Rectangle r) {
        return (long) r.width * r.height;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (sheet == null) {
            return null;
        }
        Rectangle r = detailAt(e.getPoint());
        if (r != null) {
            Object[] mod = modules.get(r);
            if (mod != null) {
                return Ui2.tip(BoardSheet.moduleTip(mod), Theme.px(360));
            }
            String st = stores.get(r);
            if (st != null) {
                return Ui2.tip(BoardSheet.storeTip(st), Theme.px(360));
            }
            for (Map.Entry<String, Rectangle> en : hits.entrySet()) {
                if (en.getValue() == r) {
                    String t = sheet.describeHit(en.getKey());
                    return t == null ? null : Ui2.tip(t, Theme.px(360));
                }
            }
        }
        if (board != null && board.contains(e.getPoint())) {
            String t = sheet.describeHit(which);
            return t == null ? null : Ui2.tip(t, Theme.px(420));
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(Theme.alpha(Theme.bg(), 0.92));
        g.fillRect(0, 0, getWidth(), getHeight());

        int pad = Theme.px(24);
        g.setFont(Theme.font(16, Font.BOLD));
        g.setColor(Theme.ink());
        g.drawString(title, pad, pad + Theme.px(8));
        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(Theme.ink2());
        g.drawString("Наведите на деталь — подсказка. Щелчок мимо планшета или Esc — закрыть.",
            pad, pad + Theme.px(28));

        hits.clear();
        modules.clear();
        stores.clear();
        board = null;
        if (sheet != null) {
            Rectangle view = new Rectangle(pad, pad + Theme.px(44), getWidth() - pad * 2,
                getHeight() - pad * 2 - Theme.px(44));
            board = sheet.paintBoardZoom(g, view, which, hits, modules, stores);
        }
        if (board == null) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("печатного планшета у этого места нет", pad, pad + Theme.px(64));
        } else if (hot != null) {
            g.setColor(Theme.alpha(Theme.seat(seat), 0.18));
            RoundRectangle2D rr = new RoundRectangle2D.Double(hot.x - 3, hot.y - 3,
                hot.width + 6, hot.height + 6, Theme.px(8), Theme.px(8));
            g.fill(rr);
            g.setColor(Theme.seat(seat));
            g.setStroke(new BasicStroke(Theme.pxf(2.5f)));
            g.draw(rr);
        }
        g.dispose();
    }
}
