package kelium.gui.kp;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;
import kelium.report.Mips;
import kelium.report.Textures;

/**
 * ПАМЯТКА ИГРОКА В ЯЩИКЕ (просьба дизайнера 26.09.2026): четыре страницы
 * печатной памятки, листаются влево-вправо — стрелками по бокам, щелчком по
 * левой или правой половине страницы, клавишами ← →. Страница вписывается в
 * видимую высоту ящика целиком, без прокрутки: снизу его может закрывать
 * выдвинутый стол игрока, эту высоту сообщает {@code covered}.
 */
public final class MemoPanel extends JComponent {

    private final List<BufferedImage> pages = new ArrayList<>();
    private final IntSupplier covered;
    private int page;
    private Rectangle prev = new Rectangle();
    private Rectangle next = new Rectangle();
    private Rectangle pageRect = new Rectangle();
    private int hover;   // −1 — влево, 1 — вправо, 0 — ничего

    public MemoPanel(IntSupplier covered) {
        this.covered = covered == null ? () -> 0 : covered;
        for (int i = 1; i <= 8; i++) {
            BufferedImage p = Textures.memoPage(i);
            if (p == null) {
                break;
            }
            pages.add(p);
        }
        setOpaque(false);
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int h = side(e.getX(), e.getY());
                if (h != hover) {
                    hover = h;
                    setCursor(h != 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = 0;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
                int h = side(e.getX(), e.getY());
                if (h != 0) {
                    flip(h);
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "memo-prev");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "memo-next");
        getActionMap().put("memo-prev", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isShowing()) {
                    flip(-1);
                }
            }
        });
        getActionMap().put("memo-next", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isShowing()) {
                    flip(1);
                }
            }
        });
    }

    /** Сколько страниц нашлось (0 — памятки нет в данных). */
    public int pageCount() {
        return pages.size();
    }

    public int page() {
        return page;
    }

    /** Листнуть на {@code d} страниц; по кругу не листается — края честные. */
    public void flip(int d) {
        int n = Math.max(0, Math.min(pages.size() - 1, page + d));
        if (n != page) {
            page = n;
            repaint();
        }
    }

    /** Ширина ящика под страницу высотой {@code h}. */
    public int widthFor(int h) {
        if (pages.isEmpty()) {
            return Theme.px(360);
        }
        BufferedImage p = pages.get(0);
        int ph = h - Theme.px(56);
        return (int) Math.round(ph * p.getWidth() / (double) p.getHeight()) + 2 * arrowZone();
    }

    private static int arrowZone() {
        return Theme.px(46);
    }

    private int side(int x, int y) {
        if (prev.contains(x, y) || pageRect.contains(x, y) && x < pageRect.getCenterX()) {
            return page > 0 ? -1 : 0;
        }
        if (next.contains(x, y) || pageRect.contains(x, y) && x >= pageRect.getCenterX()) {
            return page < pages.size() - 1 ? 1 : 0;
        }
        return 0;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(widthFor(Math.max(Theme.px(400), getHeight())), Theme.px(600));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = Math.max(Theme.px(200), getHeight() - covered.getAsInt());
        if (pages.isEmpty()) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("памятки нет в данных игры", Theme.px(16), Theme.px(40));
            g.dispose();
            return;
        }
        BufferedImage p = pages.get(page);
        int az = arrowZone();
        int top = Theme.px(12);
        int dotsH = Theme.px(36);
        int availW = w - 2 * az;
        int availH = h - top - dotsH;
        double k = Math.min(availW / (double) p.getWidth(), availH / (double) p.getHeight());
        int pw = (int) Math.round(p.getWidth() * k);
        int ph = (int) Math.round(p.getHeight() * k);
        int px = (w - pw) / 2;
        int py = top;
        pageRect = new Rectangle(px, py, pw, ph);
        // тень под листом
        for (int i = 6; i >= 1; i--) {
            g.setColor(new java.awt.Color(0, 0, 0, 10 + (6 - i) * 4));
            g.fill(new RoundRectangle2D.Double(px - i, py - i + 3, pw + 2 * i, ph + 2 * i,
                Theme.px(22) + i, Theme.px(22) + i));
        }
        Mips.draw(g, p, px, py, pw, ph);
        // стрелки
        int ar = Theme.px(19);
        int cy = py + ph / 2;
        prev = new Rectangle(px - az - Theme.px(2), cy - ar - Theme.px(8), az, 2 * ar + Theme.px(16));
        next = new Rectangle(px + pw + Theme.px(2), cy - ar - Theme.px(8), az, 2 * ar + Theme.px(16));
        arrow(g, prev, -1, page > 0, hover == -1);
        arrow(g, next, 1, page < pages.size() - 1, hover == 1);
        // точки страниц и номер
        int n = pages.size();
        int dot = Theme.px(10);
        int gap = Theme.px(8);
        int dy = py + ph + Theme.px(16);
        g.setFont(Theme.font(13, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        String num = (page + 1) + " / " + n;
        int total = n * dot + (n - 1) * gap + Theme.px(14) + fm.stringWidth(num);
        int dx = (w - total) / 2;
        for (int i = 0; i < n; i++) {
            g.setColor(i == page ? Theme.accent() : Theme.border());
            g.fill(new Ellipse2D.Double(dx + i * (dot + gap), dy - dot / 2.0, dot, dot));
        }
        g.setColor(Theme.ink2());
        g.drawString(num, dx + n * (dot + gap) + Theme.px(6),
            dy + (fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }

    private static void arrow(Graphics2D g, Rectangle r, int dir, boolean on, boolean hot) {
        double cx = r.getCenterX();
        double cy = r.getCenterY();
        double rad = Math.min(r.width, r.height) / 2.0 - 1;
        g.setColor(hot ? Theme.hover() : Theme.tile());
        g.fill(new Ellipse2D.Double(cx - rad, cy - rad, 2 * rad, 2 * rad));
        g.setColor(on ? Theme.accent() : Theme.border());
        g.setStroke(new java.awt.BasicStroke((float) Theme.pxf(1.4)));
        g.draw(new Ellipse2D.Double(cx - rad, cy - rad, 2 * rad, 2 * rad));
        double a = rad * 0.42;
        Path2D t = new Path2D.Double();
        t.moveTo(cx + dir * a, cy);
        t.lineTo(cx - dir * a * 0.7, cy - a);
        t.lineTo(cx - dir * a * 0.7, cy + a);
        t.closePath();
        g.setColor(on ? Theme.ink() : Theme.ink3());
        g.fill(t);
    }
}
