package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import kelium.gui.replay2.BoardSheet;
import kelium.gui.replay2.Theme;

/**
 * ПЛАНШЕТ КРУПНО — поверх всего окна (заказ дизайнера 25.09.2026: «при
 * нажатии на планшет — отдельно на каждый — увеличить на весь экран как
 * модальное окно, и чтобы можно было получить подсказку по каждому
 * элементу»).
 *
 * <p>Рисует тот же лист игрока, что и стол ({@link BoardSheet#paintBoardZoom}),
 * но ОДИН РАЗ в картинку: на движение мыши перерисовываются только обводка
 * детали и карточка подсказки (тормоза 26.09.2026 — перерисовка планшета целиком
 * стоила ~100 мс, и подсветка отставала на полсекунды). Карточка — своя, не
 * всплывающее окно Swing: появляется сразу, щелчок по детали её закрепляет.
 * Закрыть — щелчок мимо планшета, кнопка «Закрыть» или Esc.
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
    /** Нарисованный планшет — перерисовывается при открытии, смене размера и кадра. */
    private BufferedImage layer;
    private boolean dirty = true;
    private Rectangle hot;
    private Rectangle pinned;
    private Point mouse;
    private final Rectangle closeBtn = new Rectangle();

    public BoardZoom() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                Rectangle was = hot;
                hot = detailAt(e.getPoint());
                boolean onClose = closeBtn.contains(e.getPoint());
                setCursor(onClose || hot != null
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                if (!java.util.Objects.equals(was, hot) || pinned == null) {
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();
                if (closeBtn.contains(p) || board == null || !board.contains(p)) {
                    close();
                    return;
                }
                // щелчок по детали закрепляет подсказку, повторный — снимает
                Rectangle r = detailAt(p);
                pinned = r == null || r.equals(pinned) ? null : r;
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        // колесо не должно проваливаться на стол под окном
        addMouseWheelListener(e -> { });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                dirty = true;
            }
        });
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
        pinned = null;
        dirty = true;
        setVisible(true);
        requestFocusInWindow();
        repaint();
    }

    /** Партия ушла вперёд — планшет перерисуется при следующем показе. */
    public void refresh() {
        if (isVisible()) {
            dirty = true;
            repaint();
        }
    }

    public boolean isOpen() {
        return isVisible();
    }

    public void close() {
        setVisible(false);
        hot = null;
        pinned = null;
        layer = null;
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
            if ("troop".equals(e.getKey()) || "storage".equals(e.getKey()) || !r.contains(p)) {
                continue;
            }
            if (area(r) < bestArea) {
                best = r;
                bestArea = area(r);
            }
        }
        return best;
    }

    private static long area(Rectangle r) {
        return (long) r.width * r.height;
    }

    /** Что сказать о детали. */
    private String describe(Rectangle r) {
        if (r == null || sheet == null) {
            return null;
        }
        Object[] mod = modules.get(r);
        if (mod != null) {
            return BoardSheet.moduleTip(mod);
        }
        String st = stores.get(r);
        if (st != null) {
            return BoardSheet.storeTip(st);
        }
        for (Map.Entry<String, Rectangle> en : hits.entrySet()) {
            if (en.getValue() == r) {
                return sheet.describeHit(en.getKey());
            }
        }
        return null;
    }

    private void render() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        layer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = layer.createGraphics();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        hits.clear();
        modules.clear();
        stores.clear();
        board = null;
        int pad = Theme.px(24);
        if (sheet != null) {
            Rectangle view = new Rectangle(pad, pad + Theme.px(44), w - pad * 2,
                h - pad * 2 - Theme.px(44));
            board = sheet.paintBoardZoom(g, view, which, hits, modules, stores);
        }
        g.dispose();
        dirty = false;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (dirty || layer == null || layer.getWidth() != getWidth()
                || layer.getHeight() != getHeight()) {
            render();
        }
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.alpha(Theme.bg(), 0.94));
        g.fillRect(0, 0, getWidth(), getHeight());
        int pad = Theme.px(24);
        g.setFont(Theme.font(16, Font.BOLD));
        g.setColor(Theme.ink());
        g.drawString(title, pad, pad + Theme.px(8));
        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(Theme.ink2());
        g.drawString("Наведите на деталь — подсказка; щелчок по детали её закрепляет. "
            + "Щелчок мимо планшета, «Закрыть» или Esc — закрыть.", pad, pad + Theme.px(28));
        paintClose(g);
        g.drawImage(layer, 0, 0, null);
        if (board == null) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("печатного планшета у этого места нет", pad, pad + Theme.px(64));
        }
        Rectangle show = pinned != null ? pinned : hot;
        if (show != null) {
            RoundRectangle2D rr = new RoundRectangle2D.Double(show.x - 3, show.y - 3,
                show.width + 6, show.height + 6, Theme.px(8), Theme.px(8));
            g.setColor(Theme.alpha(Theme.seat(seat), 0.18));
            g.fill(rr);
            g.setColor(Theme.seat(seat));
            g.setStroke(new BasicStroke(Theme.pxf(2.5)));
            g.draw(rr);
            String text = describe(show);
            if (text != null) {
                paintInfo(g, show, text);
            }
        }
        g.dispose();
    }

    /** Кнопка «Закрыть» в правом верхнем углу: крестик фигурой и слово. */
    private void paintClose(Graphics2D g) {
        int bw = Theme.px(112);
        int bh = Theme.px(34);
        int x = getWidth() - Theme.px(24) - bw;
        int y = Theme.px(16);
        closeBtn.setBounds(x, y, bw, bh);
        boolean hov = mouse != null && closeBtn.contains(mouse);
        g.setColor(hov ? Theme.hover() : Theme.panel());
        g.fill(new RoundRectangle2D.Double(x, y, bw, bh, Theme.px(10), Theme.px(10)));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, bw, bh, Theme.px(10), Theme.px(10)));
        int cx = x + Theme.px(18);
        int cy = y + bh / 2;
        int r = Theme.px(6);
        g.setColor(Theme.ink());
        g.setStroke(new BasicStroke(Theme.pxf(2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx - r, cy - r, cx + r, cy + r);
        g.drawLine(cx - r, cy + r, cx + r, cy - r);
        g.setFont(Theme.font(13, Font.BOLD));
        g.drawString("Закрыть", cx + Theme.px(14), cy + g.getFontMetrics().getAscent() / 2 - Theme.px(2));
    }

    /** Карточка подсказки у детали: справа, если влезает, иначе слева. */
    private void paintInfo(Graphics2D g, Rectangle at, String text) {
        Font head = Theme.font(13, Font.BOLD);
        Font body = Theme.font(12, Font.PLAIN);
        int maxW = Theme.px(360);
        int pad = Theme.px(12);
        List<String[]> lines = new ArrayList<>();     // {текст, 1 — заголовок}
        String[] rows = text.split("\n");
        for (int i = 0; i < rows.length; i++) {
            g.setFont(i == 0 ? head : body);
            for (String l : FieldBubbles.wrap(g.getFontMetrics(), rows[i], maxW - pad * 2, 4)) {
                lines.add(new String[]{l, i == 0 ? "1" : "0"});
            }
        }
        int w = 0;
        int h = pad * 2;
        for (String[] l : lines) {
            g.setFont("1".equals(l[1]) ? head : body);
            FontMetrics fm = g.getFontMetrics();
            w = Math.max(w, fm.stringWidth(l[0]));
            h += fm.getHeight();
        }
        w += pad * 2;
        int x = at.x + at.width + Theme.px(12);
        if (x + w > getWidth() - Theme.px(8)) {
            x = at.x - w - Theme.px(12);
        }
        x = Math.max(Theme.px(8), x);
        int y = Math.max(Theme.px(8), Math.min(getHeight() - h - Theme.px(8), at.y));
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y, w, h, Theme.px(10), Theme.px(10));
        g.setColor(Theme.alpha(java.awt.Color.BLACK, 0.35));
        g.translate(0, Theme.px(3));
        g.fill(box);
        g.translate(0, -Theme.px(3));
        g.setColor(Theme.panel());
        g.fill(box);
        g.setColor(Theme.seat(seat));
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.draw(box);
        int ty = y + pad;
        for (String[] l : lines) {
            boolean isHead = "1".equals(l[1]);
            g.setFont(isHead ? head : body);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(isHead ? Theme.ink() : Theme.ink2());
            g.drawString(l[0], x + pad, ty + fm.getAscent());
            ty += fm.getHeight();
        }
    }
}
