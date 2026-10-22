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
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * МЕНЮ СТРОЙКИ — выезжает слева, пока игрок решает, что строить (заказ
 * дизайнера 27.09.2026: «стол — только для просмотра; когда игрок разыгрывает
 * Стройку, слева выезжает меню-список с картинками зданий и коротким
 * описанием, по категориям»). Прежде здание выбирали щелчком по мелкому
 * жетону на планшете, и случайный щелчок по планшету открывал его крупно.
 *
 * <p>Строка — печатный жетон здания, имя, цена и что оно делает. Что по
 * деньгам или правилам поставить нельзя — серое, с причиной словами. Щелчок
 * по доступному — выбор здания, дальше гекс и поворот на поле, как прежде.
 * Внизу — «Закончить стройку» и «Отмена».
 *
 * <p>Меню ловит мышь только в своих границах: поле рядом можно двигать и
 * приближать, пока меню открыто.
 */
public final class BuildMenu extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Строка меню: доступна, если {@code onPick} не null; иначе {@code why} — почему нет. */
    public record Row(String section, BufferedImage art, String title, String sub,
                      String why, Runnable onPick) {
        public boolean enabled() {
            return onPick != null;
        }
    }

    /** Кнопка внизу меню. */
    public record Button(String label, Runnable onPick) {
    }

    private String title = "";
    private String subtitle = "";
    private final List<Row> rows = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final List<Rectangle> rowRects = new ArrayList<>();
    private final List<Rectangle> buttonRects = new ArrayList<>();
    private Color accent = Theme.accent();
    private int hoverRow = -1;
    private int hoverButton = -1;
    private int scroll;
    private int contentH;
    private final Anim anim = new Anim();
    /** Меню открыто для решения (при уходе ещё гаснет, но уже ничего не выбирает). */
    private boolean live;

    public BuildMenu() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = rowAt(e.getX(), e.getY());
                int b = buttonAt(e.getX(), e.getY());
                if (r != hoverRow || b != hoverButton) {
                    hoverRow = r;
                    hoverButton = b;
                    boolean hand = b >= 0 || r >= 0 && rows.get(r).enabled();
                    setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                hoverButton = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!live) {
                    return;              // гаснущее меню прошлого решения
                }
                int b = buttonAt(e.getX(), e.getY());
                if (b >= 0) {
                    buttons.get(b).onPick().run();
                    return;
                }
                int r = rowAt(e.getX(), e.getY());
                if (r >= 0 && rows.get(r).enabled()) {
                    rows.get(r).onPick().run();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(e -> {
            int max = Math.max(0, contentH - listH());
            scroll = Math.max(0, Math.min(max, scroll + e.getWheelRotation() * Theme.px(40)));
            repaint();
        });
    }

    /** Ширина меню. */
    public static int menuWidth() {
        return Theme.px(390);
    }

    public void open(String title, String subtitle, List<Row> newRows, List<Button> newButtons,
                     Color seatColor) {
        boolean was = isVisible() && anim.value() > 0.5;
        this.title = title;
        this.subtitle = subtitle == null ? "" : subtitle;
        rows.clear();
        rows.addAll(newRows);
        buttons.clear();
        buttons.addAll(newButtons);
        accent = seatColor == null ? Theme.accent() : seatColor;
        hoverRow = -1;
        hoverButton = -1;
        scroll = 0;
        live = true;
        setVisible(true);
        // всегда ведём к «открыто»: иначе недоигранное закрытие (прошлое решение
        // только что закрыло меню) спрятало бы его уже после открытия
        if (!was) {
            anim.snap(Math.min(anim.value(), 1));
        }
        anim.play(1, 200, v -> repaint(), null);
        repaint();
    }

    public void close() {
        live = false;
        if (!isVisible()) {
            return;
        }
        anim.play(0, 140, v -> repaint(), () -> {
            setVisible(false);
            rows.clear();
            buttons.clear();
        });
    }

    public boolean isOpen() {
        return live && isVisible() && !rows.isEmpty();
    }

    /** Строки и кнопки — для прогонщиков и тестов. */
    public List<Row> rowsForTest() {
        return List.copyOf(rows);
    }

    public List<Button> buttonsForTest() {
        return List.copyOf(buttons);
    }

    /** Где лежит строка на меню (после отрисовки) — для щелчков в тестах. */
    public Rectangle rowRectForTest(int i) {
        return i < rowRects.size() ? new Rectangle(rowRects.get(i)) : null;
    }

    public Rectangle buttonRectForTest(int i) {
        return i < buttonRects.size() ? new Rectangle(buttonRects.get(i)) : null;
    }

    /** Прокрутить меню так, чтобы строка была видна (для тестов). */
    public void revealForTest(int i) {
        Rectangle r = rowRectForTest(i);
        if (r == null) {
            return;
        }
        int top = headH();
        if (r.y < top) {
            scroll = Math.max(0, scroll - (top - r.y));
        } else if (r.y + r.height > top + listH()) {
            scroll += r.y + r.height - top - listH();
        }
        repaint();
    }

    private int rowAt(int x, int y) {
        if (y < headH() || y > headH() + listH()) {
            return -1;
        }
        for (int i = 0; i < rowRects.size(); i++) {
            if (rowRects.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private int buttonAt(int x, int y) {
        for (int i = 0; i < buttonRects.size(); i++) {
            if (buttonRects.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private static int headH() {
        return Theme.px(76);
    }

    private static int footH() {
        return Theme.px(64);
    }

    private int listH() {
        return Math.max(Theme.px(60), getHeight() - headH() - footH());
    }

    private static int rowH() {
        return Theme.px(66);
    }

    @Override
    public boolean contains(int x, int y) {
        // за пределами нарисованной панели мышь уходит полю
        return live && isVisible() && anim.value() > 0.3 && x >= 0 && x < menuWidth() && y >= 0
            && y < getHeight();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        double a = anim.value();
        if (a <= 0.01) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // выезд слева
        g.translate(-(1 - a) * menuWidth(), 0);
        g.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, a * 1.2)));
        int w = menuWidth();
        int h = getHeight();
        RoundRectangle2D panel = new RoundRectangle2D.Double(0, 0, w, h, Theme.px(18),
            Theme.px(18));
        g.setColor(Theme.alpha(Color.BLACK, 0.35));
        g.fill(new RoundRectangle2D.Double(Theme.px(3), Theme.px(5), w, h, Theme.px(18),
            Theme.px(18)));
        g.setColor(Theme.alpha(Theme.bg(), 0.95));
        g.fill(panel);
        g.setColor(accent);
        g.fillRect(0, Theme.px(14), Theme.px(5), h - Theme.px(28));
        g.setColor(Theme.alpha(accent, 0.8));
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.draw(panel);

        int pad = Theme.px(16);
        g.setFont(Theme.font(19, Font.BOLD));
        g.setColor(Color.WHITE);
        g.drawString(title, pad + Theme.px(4), Theme.px(32));
        g.setFont(Theme.font(12.5, Font.PLAIN));
        g.setColor(Theme.ink2());
        FontMetrics sfm = g.getFontMetrics();
        int sy = Theme.px(52);
        for (String line : CardTile.wrap(subtitle, sfm, w - 2 * pad, 2)) {
            g.drawString(line, pad + Theme.px(4), sy);
            sy += sfm.getHeight();
        }

        // ---- список
        java.awt.Shape clip = g.getClip();
        g.clipRect(0, headH(), w, listH());
        rowRects.clear();
        int y = headH() - scroll;
        String section = null;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (!r.section().equals(section)) {
                section = r.section();
                y += Theme.px(6);
                g.setFont(Theme.font(12.5, Font.BOLD));
                g.setColor(Theme.alpha(accent, 1));
                g.drawString(section.toUpperCase(java.util.Locale.ROOT), pad + Theme.px(4),
                    y + Theme.px(14));
                g.setColor(Theme.alpha(accent, 0.35));
                g.fillRect(pad + Theme.px(4), y + Theme.px(20), w - 2 * pad, Math.max(1, Theme.px(1)));
                y += Theme.px(26);
            }
            Rectangle rr = new Rectangle(Theme.px(8), y, w - Theme.px(16), rowH() - Theme.px(4));
            rowRects.add(rr);
            paintRow(g, r, rr, i == hoverRow);
            y += rowH();
        }
        contentH = y + scroll - headH() + Theme.px(8);
        g.setClip(clip);
        // след, что есть ещё ниже или выше
        if (contentH > listH()) {
            g.setColor(Theme.ink3());
            g.setFont(Theme.font(11.5, Font.PLAIN));
            String more = scroll < contentH - listH() ? "ниже ещё — колесо мыши" : "";
            if (!more.isEmpty()) {
                // затухание низа списка и подпись на нём — строки под ней не видно
                int fy = headH() + listH() - Theme.px(24);
                g.setPaint(new java.awt.GradientPaint(0, fy, Theme.alpha(Theme.bg(), 0f), 0,
                    fy + Theme.px(24), Theme.alpha(Theme.bg(), 0.98f)));
                g.fillRect(Theme.px(6), fy, w - Theme.px(12), Theme.px(24));
                g.setColor(Theme.ink2());
                g.drawString(more, w - pad - g.getFontMetrics().stringWidth(more),
                    headH() + listH() - Theme.px(5));
            }
        }

        // ---- кнопки
        buttonRects.clear();
        int bh = Theme.px(40);
        int by = h - footH() + (footH() - bh) / 2;
        int n = Math.max(1, buttons.size());
        int gap = Theme.px(10);
        int bw = (w - 2 * pad - gap * (n - 1)) / n;
        for (int i = 0; i < buttons.size(); i++) {
            Rectangle br = new Rectangle(pad + i * (bw + gap), by, bw, bh);
            buttonRects.add(br);
            boolean hot = i == hoverButton;
            RoundRectangle2D rb = new RoundRectangle2D.Double(br.x, br.y, br.width, br.height,
                Theme.px(10), Theme.px(10));
            g.setColor(hot ? Theme.alpha(accent, 0.45) : Theme.alpha(Color.WHITE, 0.08));
            g.fill(rb);
            g.setColor(Theme.alpha(accent, hot ? 1 : 0.7));
            g.setStroke(new BasicStroke(Theme.pxf(1.4)));
            g.draw(rb);
            g.setFont(Theme.font(14, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            String lab = FieldBubbles.clip(fm, buttons.get(i).label(), br.width - Theme.px(10));
            g.setColor(Color.WHITE);
            g.drawString(lab, br.x + (br.width - fm.stringWidth(lab)) / 2,
                br.y + (br.height + fm.getAscent() - fm.getDescent()) / 2);
        }
        // зоны щелчка — в координатах компонента, а он уже на месте после выезда
        g.dispose();
    }

    private void paintRow(Graphics2D g, Row r, Rectangle rr, boolean hot) {
        boolean on = r.enabled();
        RoundRectangle2D bg = new RoundRectangle2D.Double(rr.x, rr.y, rr.width, rr.height,
            Theme.px(12), Theme.px(12));
        if (on && hot) {
            g.setColor(Theme.alpha(accent, 0.30));
            g.fill(bg);
            g.setColor(accent);
            g.setStroke(new BasicStroke(Theme.pxf(2)));
            g.draw(bg);
        } else {
            g.setColor(Theme.alpha(Color.WHITE, on ? 0.06 : 0.025));
            g.fill(bg);
        }
        int img = rr.height - Theme.px(8);
        int ix = rr.x + Theme.px(6);
        int iy = rr.y + Theme.px(4);
        Graphics2D gi = (Graphics2D) g.create();
        if (!on) {
            gi.setComposite(AlphaComposite.SrcOver.derive(0.38f));
        }
        if (r.art() != null) {
            double k = Math.min(img / (double) r.art().getWidth(), img / (double) r.art().getHeight());
            int dw = (int) Math.round(r.art().getWidth() * k);
            int dh = (int) Math.round(r.art().getHeight() * k);
            kelium.report.Mips.draw(gi, r.art(), ix + (img - dw) / 2, iy + (img - dh) / 2, dw, dh);
        }
        gi.dispose();
        int tx = ix + img + Theme.px(10);
        int tw = rr.x + rr.width - tx - Theme.px(8);
        g.setFont(Theme.font(15, Font.BOLD));
        FontMetrics f1 = g.getFontMetrics();
        g.setColor(on ? Color.WHITE : Theme.ink3());
        g.drawString(FieldBubbles.clip(f1, r.title(), tw), tx, rr.y + Theme.px(22));
        g.setFont(Theme.font(12.5, Font.PLAIN));
        FontMetrics f2 = g.getFontMetrics();
        int ly = rr.y + Theme.px(39);
        if (r.sub() != null && !r.sub().isBlank()) {
            g.setColor(on ? Theme.ink2() : Theme.ink3());
            g.drawString(FieldBubbles.clip(f2, r.sub(), tw), tx, ly);
            ly += f2.getHeight();
        }
        if (!on && r.why() != null) {
            g.setColor(new Color(0xE8, 0x9A, 0x86));
            g.drawString(FieldBubbles.clip(f2, r.why(), tw), tx, ly);
        }
    }
}
