package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

import javax.swing.JComponent;

import kelium.gui.replay2.MarkIcons;
import kelium.gui.replay2.Theme;

/**
 * СЧЁТЧИК СО СТРЕЛКАМИ «−  число  +» — рисованный, для стартовых значений в
 * меню запуска.
 *
 * <p>Рядом с числом всегда видно ПЕЧАТНОЕ значение из правил, и пока игрок его
 * не тронул, счётчик выглядит спокойно. Отличается от печатного — число
 * подсвечивается: партия с такими значениями уже не обычная, и экран обязан
 * это показывать, а не прятать.
 */
public final class Stepper extends JComponent {

    private final String label;
    private final String icon;
    private final int printed;
    private final int min;
    private final int max;
    private final IntConsumer onChange;
    private int value;
    private int hotZone = -1;   // 0 — минус, 1 — плюс

    public Stepper(String label, String icon, int printed, int min, int max,
                    IntConsumer onChange) {
        this.label = label;
        this.icon = icon;
        this.printed = printed;
        this.min = min;
        this.max = max;
        this.value = printed;
        this.onChange = onChange;
        setOpaque(false);
        setPreferredSize(new Dimension(Theme.px(230), Theme.px(26)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(26)));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int z = zoneAt(e.getX());
                if (z != hotZone) {
                    hotZone = z;
                    setCursor(z < 0 ? Cursor.getDefaultCursor()
                        : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hotZone = -1;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int z = zoneAt(e.getX());
                if (z == 0) {
                    step(-1);
                } else if (z == 1) {
                    step(1);
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** Для прогонщиков и тестов — то же, что клик по стрелке. */
    public void step(int d) {
        int v = Math.max(min, Math.min(max, value + d));
        if (v == value) {
            return;
        }
        value = v;
        repaint();
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    public int value() {
        return value;
    }

    /** Отличается ли от печатного в правилах. */
    public boolean changed() {
        return value != printed;
    }

    /** Вернуть как в правилах. */
    public void reset() {
        if (value != printed) {
            value = printed;
            repaint();
            if (onChange != null) {
                onChange.accept(value);
            }
        }
    }

    private int btn() {
        return Theme.px(22);
    }

    private int zoneAt(int x) {
        int h = getHeight();
        int right = getWidth();
        if (x >= right - btn()) {
            return 1;
        }
        if (x >= right - btn() * 2 - Theme.px(30) && x < right - btn() * 2 + h) {
            return 0;
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int b = btn();

        if (icon != null) {
            MarkIcons.paint(g, icon, Theme.px(8), h / 2.0, Theme.px(13),
                changed() ? Theme.points() : Theme.ink3());
        }
        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(Theme.ink2());
        var fm = g.getFontMetrics();
        g.drawString(label, Theme.px(20), (h + fm.getAscent() - fm.getDescent()) / 2);

        // печатное значение — тихой подписью, чтобы видеть, от чего отступили
        g.setFont(Theme.font(10, Font.PLAIN));
        g.setColor(Theme.ink3());
        String was = "в правилах " + printed;
        int wasW = g.getFontMetrics().stringWidth(was);
        int numX = w - b * 2 - Theme.px(30);
        if (changed()) {
            g.drawString(was, numX - wasW - Theme.px(8),
                (h + g.getFontMetrics().getAscent()) / 2 - Theme.px(1));
        }

        drawBtn(g, new Rectangle(numX, (h - b) / 2, b, b), "−", hotZone == 0,
            value > min);
        g.setFont(Theme.mono(13, Font.BOLD));
        g.setColor(changed() ? Theme.points() : Theme.ink());
        var fm2 = g.getFontMetrics();
        String v = String.valueOf(value);
        g.drawString(v, numX + b + (Theme.px(30) - fm2.stringWidth(v)) / 2,
            (h + fm2.getAscent() - fm2.getDescent()) / 2);
        drawBtn(g, new Rectangle(w - b, (h - b) / 2, b, b), "+", hotZone == 1,
            value < max);
        g.dispose();
    }

    private void drawBtn(Graphics2D g, Rectangle r, String sign, boolean hot, boolean live) {
        g.setColor(hot && live ? Theme.hover() : Theme.tile());
        g.fillRoundRect(r.x, r.y, r.width, r.height, Theme.px(5), Theme.px(5));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, Theme.px(5), Theme.px(5));
        g.setFont(Theme.font(13, Font.BOLD));
        g.setColor(live ? Theme.ink2() : Theme.ink3());
        var fm = g.getFontMetrics();
        g.drawString(sign, r.x + (r.width - fm.stringWidth(sign)) / 2,
            r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
    }
}
