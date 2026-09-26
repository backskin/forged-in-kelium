package kelium.gui.kp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * ПОЛОСА ДЕЙСТВИЙ НАД ЗОНОЙ ИГРОКА (просьба дизайнера 26.09.2026): когда карта
 * круга вскрыта и пора выбирать действие, прямо в поле видимости, над
 * планшетами, появляется прозрачная полоса. На ней — кружки доступных
 * действий: печатный кружок действия, поверх — прозрачная иконка самого
 * действия, под ним — подпись. Щелчок по кружку играет действие.
 *
 * <p>Фона у полосы нет: она лежит поверх поля и не должна его закрывать.
 * Отдельно — кнопка «Завершить ход», если действия ещё остались.
 */
public final class ActionStrip extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Кнопка полосы: код действия (иконка), подпись, что сделать по щелчку. */
    public record Item(String action, String label, String sub, Runnable onPick) {
    }

    private final List<Item> items = new ArrayList<>();
    private final List<Rectangle> rects = new ArrayList<>();
    private int hover = -1;
    private Color accent = Theme.accent();
    private String caption;

    public ActionStrip() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int h = at(e.getX(), e.getY());
                if (h != hover) {
                    hover = h;
                    setCursor(h >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int h = at(e.getX(), e.getY());
                if (h >= 0 && h < items.size()) {
                    Runnable r = items.get(h).onPick();
                    hide0();
                    if (r != null) {
                        r.run();
                    }
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** Показать полосу с этими кнопками; {@code caption} — строка над ними. */
    public void show(String caption, List<Item> list, Color seatColor) {
        items.clear();
        items.addAll(list);
        this.caption = caption;
        this.accent = seatColor == null ? Theme.accent() : seatColor;
        hover = -1;
        setVisible(!items.isEmpty());
        repaint();
    }

    public void hide0() {
        items.clear();
        setVisible(false);
    }

    /** Кнопки полосы — для прогонщиков и тестов. */
    public List<Item> itemsForTest() {
        return List.copyOf(items);
    }

    /** Не ловить мышь там, где кнопок нет: поле под полосой остаётся живым. */
    @Override
    public boolean contains(int x, int y) {
        return at(x, y) >= 0;
    }

    private int at(int x, int y) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /** Высота, которая нужна полосе. */
    public static int stripHeight() {
        return Theme.px(128);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (items.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int d = Theme.px(64);
        int cell = Theme.px(112);
        int total = cell * items.size();
        int x0 = (w - total) / 2;
        int cy = h - Theme.px(44) - d / 2;
        // мягкое пятно тени под кружками — без прямоугольника: полоса читается
        // поверх светлого поля, но ничего не закрывает
        double rx = total / 2.0 + Theme.px(70);
        double ry = h * 0.62;
        double scy = cy + Theme.px(14);
        java.awt.geom.AffineTransform squash = new java.awt.geom.AffineTransform();
        squash.translate(w / 2.0, scy);
        squash.scale(1, ry / rx);
        squash.translate(-w / 2.0, -scy);
        java.awt.RadialGradientPaint spot = new java.awt.RadialGradientPaint(
            new java.awt.geom.Point2D.Double(w / 2.0, scy), (float) rx,
            new java.awt.geom.Point2D.Double(w / 2.0, scy),
            new float[]{0f, 0.6f, 1f},
            new Color[]{Theme.alpha(new Color(0x0E2029), 0.6f),
                Theme.alpha(new Color(0x0E2029), 0.3f),
                Theme.alpha(new Color(0x0E2029), 0f)},
            java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE,
            java.awt.MultipleGradientPaint.ColorSpaceType.SRGB, squash);
        Graphics2D gs = (Graphics2D) g.create();
        gs.setPaint(spot);
        gs.fillRect(0, 0, w, h);
        gs.dispose();
        rects.clear();
        BufferedImage ring = kelium.report.Textures.icon("action_ring");
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int cx = x0 + cell * i + cell / 2;
            boolean hot = i == hover;
            int dd = hot ? d + Theme.px(6) : d;
            Rectangle r = new Rectangle(cx - cell / 2 + Theme.px(4), cy - dd / 2 - Theme.px(4),
                cell - Theme.px(8), dd + Theme.px(34));
            rects.add(r);
            // свечение цвета места под кружком
            for (int k = 6; k >= 1; k--) {
                double gr = dd / 2.0 + Theme.px(hot ? 14 : 8) * k / 6.0;
                g.setColor(Theme.alpha(accent, (hot ? 0.16 : 0.09) * (1 - (k - 1) / 6.0)));
                g.fill(new Ellipse2D.Double(cx - gr, cy - gr, gr * 2, gr * 2));
            }
            if (ring != null && it.action() != null) {
                kelium.report.Mips.draw(g, ring, cx - dd / 2, cy - dd / 2, dd, dd);
            } else {
                g.setColor(Theme.alpha(Color.BLACK, 0.55));
                g.fill(new Ellipse2D.Double(cx - dd / 2.0, cy - dd / 2.0, dd, dd));
                g.setColor(accent);
                g.setStroke(new BasicStroke(Theme.pxf(2.4)));
                g.draw(new Ellipse2D.Double(cx - dd / 2.0, cy - dd / 2.0, dd, dd));
            }
            // иконка действия — прозрачно поверх кружка
            // «Завершить ход» — финишным флажком из иконок игры
            BufferedImage icon = kelium.report.Textures.icon(
                it.action() == null ? "condition" : "action_" + it.action());
            if (icon != null) {
                Graphics2D gi = (Graphics2D) g.create();
                gi.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    hot ? 0.95f : 0.82f));
                int is = (int) Math.round(dd * 0.74);
                kelium.report.Mips.draw(gi, icon, cx - is / 2, cy - is / 2, is, is);
                gi.dispose();
            }
            // подпись
            g.setFont(Theme.font(12.5, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            String lab = it.label();
            int ty = cy + dd / 2 + Theme.px(18);
            g.setColor(Theme.alpha(Color.BLACK, 0.6));
            g.drawString(lab, cx - fm.stringWidth(lab) / 2 + 1, ty + 1);
            g.setColor(hot ? Color.WHITE : new Color(0xEEF7FA));
            g.drawString(lab, cx - fm.stringWidth(lab) / 2, ty);
            if (it.sub() != null && !it.sub().isBlank()) {
                g.setFont(Theme.font(10, Font.PLAIN));
                FontMetrics fs = g.getFontMetrics();
                g.setColor(new Color(0xB8D3DD));
                g.drawString(it.sub(), cx - fs.stringWidth(it.sub()) / 2, ty + Theme.px(14));
            }
        }
        g.dispose();
    }
}
