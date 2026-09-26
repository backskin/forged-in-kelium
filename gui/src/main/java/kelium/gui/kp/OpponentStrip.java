package kelium.gui.kp;

import java.awt.Color;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.JComponent;

import kelium.gui.replay2.MarkIcons;
import kelium.gui.replay2.Theme;

/**
 * СОПЕРНИКИ В ВЕРХНЕЙ СТРОКЕ (блокер приёмки 24.08.2026 — «я принимаю решение
 * о бое, не видя счёта соперника»; ревью 26.09.2026 — отдельная полоса съедала
 * высоту поля, а четвёртый соперник обрезался). Для каждого соперника — чип
 * цветом места: имя, очки, монеты, келемий, боеприпасы, карты в руках и
 * уничтоженное на свалке. Чипы делят ширину поровну; тесно — остаются имя,
 * очки, монеты и келемий, остальное в подсказке. Щелчок по чипу — посмотреть
 * стол этого игрока (только открытое).
 */
public final class OpponentStrip extends JComponent {

    public record Row(int seat, String name, boolean me, int vp, int coin,
                       int kelium, int ammo, int orderCards, int objectiveCards,
                       int arsenalCards, int destroyedValue, boolean first) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Rectangle> chips = new ArrayList<>();
    private IntConsumer onSeat = s -> { };
    private int hover = -1;
    /** Чей стол сейчас в зоне игрока (чип подсвечен); −1 — свой. */
    private int shown = -1;

    public OpponentStrip() {
        setOpaque(false);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int h = chipAt(e.getX(), e.getY());
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
                int h = chipAt(e.getX(), e.getY());
                if (h >= 0 && h < rows.size()) {
                    onSeat.accept(rows.get(h).seat());
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** Щелчок по чипу соперника — посмотреть его стол. */
    public void onSeat(IntConsumer c) {
        this.onSeat = c == null ? s -> { } : c;
    }

    /** Чей стол сейчас открыт в зоне (−1 — свой). */
    public void setShown(int seat) {
        this.shown = seat;
        repaint();
    }

    public void update(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        repaint();
    }

    private int chipAt(int x, int y) {
        for (int i = 0; i < chips.size(); i++) {
            if (chips.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = chipAt(e.getX(), e.getY());
        if (i < 0 || i >= rows.size()) {
            return null;
        }
        Row r = rows.get(i);
        return "<html><b>" + r.name() + "</b>" + (r.first() ? " · жетон первого игрока" : "")
            + "<br>очки " + r.vp() + " · монеты " + r.coin() + " · келемий " + r.kelium()
            + " · боеприпасы " + r.ammo()
            + "<br>в руке: заданий " + r.objectiveCards() + ", арсенала " + r.arsenalCards()
            + ", приказов " + r.orderCards()
            + "<br>уничтожено на свалке: " + r.destroyedValue()
            + "<br><i>щелчок — посмотреть его стол</i></html>";
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(300), Theme.px(48));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        chips.clear();
        int h = getHeight();
        int n = rows.size();
        if (n == 0) {
            g.dispose();
            return;
        }
        int gap = Theme.px(8);
        int cw = Math.min(Theme.px(560), (getWidth() - gap * (n - 1)) / n);
        int x = 0;
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            Rectangle chip = new Rectangle(x, Theme.px(3), cw, h - Theme.px(6));
            chips.add(chip);
            boolean hot = i == hover;
            boolean on = r.seat() == shown;
            g.setColor(on ? Theme.seatWash(r.seat(), 0.30) : hot ? Theme.hover() : Theme.tile());
            g.fillRoundRect(chip.x, chip.y, chip.width, chip.height, Theme.px(10), Theme.px(10));
            g.setColor(on || hot ? Theme.seat(r.seat()) : Theme.border());
            g.drawRoundRect(chip.x, chip.y, chip.width, chip.height, Theme.px(10), Theme.px(10));
            paintChip(g, r, chip);
            x += cw + gap;
        }
        g.dispose();
    }

    /** Чип в две строки: сверху имя (и жетон первого игрока), снизу счёт. */
    private void paintChip(Graphics2D g, Row r, Rectangle c) {
        int x0 = c.x + Theme.px(8);
        int right = c.x + c.width - Theme.px(6);
        g.setColor(Theme.seat(r.seat()));
        g.fillRoundRect(x0, c.y + Theme.px(5), Theme.px(4), c.height - Theme.px(10), 3, 3);
        int cx = x0 + Theme.px(10);
        int line1 = c.y + c.height * 30 / 100;
        int line2 = c.y + c.height * 72 / 100;
        if (r.first()) {
            java.awt.image.BufferedImage token = kelium.report.Textures.icon("first_player");
            int ts = Theme.px(18);
            if (token != null) {
                kelium.report.Mips.draw(g, token, cx, line1 - ts / 2, ts, ts);
            }
            cx += ts + Theme.px(4);
        }
        g.setFont(Theme.font(12.5, Font.BOLD));
        FontMetrics nf = g.getFontMetrics();
        g.setColor(Theme.seatInk(r.seat()));
        g.drawString(clip(nf, r.name(), right - cx), cx,
            line1 + (nf.getAscent() - nf.getDescent()) / 2);
        // счёт — сколько влезает, главное первым
        List<Object[]> all = new ArrayList<>();
        all.add(new Object[]{"SUPER", Theme.points(), String.valueOf(r.vp())});
        all.add(new Object[]{"COIN", Theme.points(), String.valueOf(r.coin())});
        all.add(new Object[]{"KELIUM", Theme.kelium(), String.valueOf(r.kelium())});
        all.add(new Object[]{"AMMO", Theme.energy(), String.valueOf(r.ammo())});
        all.add(new Object[]{"CARD", Theme.neutral(), String.valueOf(r.objectiveCards())});
        all.add(new Object[]{"ARSENAL", Theme.neutral(), String.valueOf(r.arsenalCards())});
        all.add(new Object[]{"DESTROYED", Theme.destroyed(), String.valueOf(r.destroyedValue())});
        g.setFont(Theme.mono(13, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        int statW = Theme.px(15) + fm.stringWidth("0") + Theme.px(7);
        int sx = x0 + Theme.px(8);
        for (Object[] e : all) {
            if (sx + statW > right + Theme.px(4)) {
                break;
            }
            sx = stat(g, sx, line2, (String) e[0], (Color) e[1], (String) e[2], statW);
        }
    }

    private static String clip(FontMetrics fm, String s, int w) {
        if (fm.stringWidth(s) <= w) {
            return s;
        }
        int n = s.length();
        while (n > 1 && fm.stringWidth(s.substring(0, n) + "…") > w) {
            n--;
        }
        return s.substring(0, n) + "…";
    }

    private int stat(Graphics2D g, int x, int cy, String icon, Color color, String value,
                     int statW) {
        double s = Theme.px(15);
        MarkIcons.paint(g, icon, x + s / 2, cy, s, color);
        g.setFont(Theme.mono(13, Font.BOLD));
        g.setColor(Theme.ink());
        FontMetrics fm = g.getFontMetrics();
        g.drawString(value, (int) (x + s + Theme.px(2)),
            cy + (fm.getAscent() - fm.getDescent()) / 2);
        return x + statW;
    }
}
