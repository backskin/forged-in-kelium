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
import java.awt.geom.RoundRectangle2D;
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
 * <p>КНОПКА «СПЕЦ-ДЕЙСТВИЕ» раскрывает над полосой список: чем можно потратить
 * спец-действие прямо сейчас — и что нельзя, серым, с причиной.
 *
 * <p>Фона у полосы нет: она лежит поверх поля и не должна его закрывать.
 */
public final class ActionStrip extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Строка меню спец-действия: доступна ({@code onPick} не null) или нет — с причиной. */
    public record SubItem(String label, String sub, Runnable onPick) {
        public boolean enabled() {
            return onPick != null;
        }
    }

    /**
     * Кнопка полосы: код действия (иконка; {@code null} — «Завершить ход»,
     * {@code "spec"} — спец-действие), подпись, что сделать по щелчку, и меню.
     */
    public record Item(String action, String label, String sub, Runnable onPick,
                       List<SubItem> menu) {
        public Item(String action, String label, String sub, Runnable onPick) {
            this(action, label, sub, onPick, null);
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final List<Rectangle> rects = new ArrayList<>();
    private final List<Rectangle> menuRects = new ArrayList<>();
    private int hover = -1;
    private int menuHover = -1;
    /** Какая кнопка раскрыла меню (−1 — меню закрыто). */
    private int menuOf = -1;
    private Color accent = Theme.accent();

    public ActionStrip() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int h = at(e.getX(), e.getY());
                int mh = menuAt(e.getX(), e.getY());
                if (h != hover || mh != menuHover) {
                    hover = h;
                    menuHover = mh;
                    boolean hand = h >= 0 || mh >= 0 && menuItem(mh) != null
                        && menuItem(mh).enabled();
                    setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                menuHover = -1;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int mh = menuAt(e.getX(), e.getY());
                if (mh >= 0) {
                    SubItem si = menuItem(mh);
                    if (si != null && si.enabled()) {
                        hide0();
                        si.onPick().run();
                    }
                    return;
                }
                int h = at(e.getX(), e.getY());
                if (h >= 0 && h < items.size()) {
                    Item it = items.get(h);
                    if (it.menu() != null) {
                        menuOf = menuOf == h ? -1 : h;
                        repaint();
                        return;
                    }
                    Runnable r = it.onPick();
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

    private SubItem menuItem(int i) {
        if (menuOf < 0 || menuOf >= items.size() || items.get(menuOf).menu() == null) {
            return null;
        }
        List<SubItem> menu = items.get(menuOf).menu();
        return i >= 0 && i < menu.size() ? menu.get(i) : null;
    }

    /** Показать полосу с этими кнопками. */
    public void show(String caption, List<Item> list, Color seatColor) {
        items.clear();
        items.addAll(list);
        this.accent = seatColor == null ? Theme.accent() : seatColor;
        hover = -1;
        menuHover = -1;
        menuOf = -1;
        setVisible(!items.isEmpty());
        repaint();
    }

    public void hide0() {
        items.clear();
        menuOf = -1;
        setVisible(false);
    }

    /** Раскрыть меню спец-действия (как щелчок) — для снимков и тестов. */
    public void openSpecMenu() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).menu() != null) {
                menuOf = i;
            }
        }
        repaint();
    }

    /** Кнопки полосы — для прогонщиков и тестов. */
    public List<Item> itemsForTest() {
        return List.copyOf(items);
    }

    /** Не ловить мышь там, где кнопок нет: поле под полосой остаётся живым. */
    @Override
    public boolean contains(int x, int y) {
        return at(x, y) >= 0 || menuAt(x, y) >= 0 || menuPanelContains(x, y);
    }

    private Rectangle menuPanel;

    private boolean menuPanelContains(int x, int y) {
        return menuOf >= 0 && menuPanel != null && menuPanel.contains(x, y);
    }

    private int at(int x, int y) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private int menuAt(int x, int y) {
        if (menuOf < 0) {
            return -1;
        }
        for (int i = 0; i < menuRects.size(); i++) {
            if (menuRects.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /** Высота самой полосы (кружки с подписями). */
    public static int stripHeight() {
        return Theme.px(150);
    }

    /** Высота с запасом под раскрытое меню спец-действия. */
    public static int boundsHeight() {
        return Theme.px(560);
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
        int d = Theme.px(76);
        int cell = Theme.px(136);
        int total = cell * items.size();
        int x0 = (w - total) / 2;
        int cy = h - Theme.px(56) - d / 2;
        // мягкое пятно тени под кружками — без прямоугольника
        double rx = total / 2.0 + Theme.px(80);
        double ry = stripHeight() * 0.62;
        double scy = cy + Theme.px(16);
        java.awt.geom.AffineTransform squash = new java.awt.geom.AffineTransform();
        squash.translate(w / 2.0, scy);
        squash.scale(1, ry / rx);
        squash.translate(-w / 2.0, -scy);
        java.awt.RadialGradientPaint spot = new java.awt.RadialGradientPaint(
            new java.awt.geom.Point2D.Double(w / 2.0, scy), (float) rx,
            new java.awt.geom.Point2D.Double(w / 2.0, scy),
            new float[]{0f, 0.6f, 1f},
            new Color[]{Theme.alpha(Theme.bg(), 0.62f),
                Theme.alpha(Theme.bg(), 0.32f),
                Theme.alpha(Theme.bg(), 0f)},
            java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE,
            java.awt.MultipleGradientPaint.ColorSpaceType.SRGB, squash);
        Graphics2D gs = (Graphics2D) g.create();
        gs.setPaint(spot);
        gs.fillRect(0, h - stripHeight() - Theme.px(20), w, stripHeight() + Theme.px(20));
        gs.dispose();
        rects.clear();
        BufferedImage ring = kelium.report.Textures.icon("action_ring");
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int cx = x0 + cell * i + cell / 2;
            boolean hot = i == hover || i == menuOf;
            int dd = hot ? d + Theme.px(6) : d;
            Rectangle r = new Rectangle(cx - cell / 2 + Theme.px(4), cy - dd / 2 - Theme.px(4),
                cell - Theme.px(8), dd + Theme.px(46));
            rects.add(r);
            for (int k = 6; k >= 1; k--) {
                double gr = dd / 2.0 + Theme.px(hot ? 16 : 9) * k / 6.0;
                g.setColor(Theme.alpha(accent, (hot ? 0.17 : 0.10) * (1 - (k - 1) / 6.0)));
                g.fill(new Ellipse2D.Double(cx - gr, cy - gr, gr * 2, gr * 2));
            }
            boolean dim = it.menu() != null && it.menu().stream().noneMatch(SubItem::enabled);
            Graphics2D gi = (Graphics2D) g.create();
            if (dim) {
                gi.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            }
            if (ring != null && it.action() != null && !"spec".equals(it.action())) {
                kelium.report.Mips.draw(gi, ring, cx - dd / 2, cy - dd / 2, dd, dd);
            } else {
                gi.setColor(Theme.alpha(Color.BLACK, 0.55));
                gi.fill(new Ellipse2D.Double(cx - dd / 2.0, cy - dd / 2.0, dd, dd));
                gi.setColor(accent);
                gi.setStroke(new BasicStroke(Theme.pxf(2.6)));
                gi.draw(new Ellipse2D.Double(cx - dd / 2.0, cy - dd / 2.0, dd, dd));
            }
            String iconName = it.action() == null ? "condition"
                : "spec".equals(it.action()) ? "spec" : "action_" + it.action();
            BufferedImage icon = kelium.report.Textures.icon(iconName);
            if (icon != null) {
                Graphics2D gi2 = (Graphics2D) gi.create();
                gi2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    (dim ? 0.45f : 1f) * (hot ? 0.95f : 0.84f)));
                int is = (int) Math.round(dd * ("spec".equals(it.action()) ? 0.86 : 0.74));
                kelium.report.Mips.draw(gi2, icon, cx - is / 2, cy - is / 2, is, is);
                gi2.dispose();
            }
            gi.dispose();
            g.setFont(Theme.font(15, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            String lab = it.label();
            int ty = cy + dd / 2 + Theme.px(22);
            g.setColor(Theme.alpha(Color.BLACK, 0.7));
            g.drawString(lab, cx - fm.stringWidth(lab) / 2 + 1, ty + 1);
            g.setColor(dim ? Theme.ink2() : hot ? Color.WHITE : Theme.ink());
            g.drawString(lab, cx - fm.stringWidth(lab) / 2, ty);
            if (it.sub() != null && !it.sub().isBlank()) {
                g.setFont(Theme.font(12.5, Font.PLAIN));
                FontMetrics fs = g.getFontMetrics();
                g.setColor(Theme.ink2());
                g.drawString(it.sub(), cx - fs.stringWidth(it.sub()) / 2, ty + Theme.px(18));
            }
        }
        paintMenu(g, x0, cell, cy - d / 2 - Theme.px(14));
        g.dispose();
    }

    /** Меню спец-действия над своей кнопкой: доступное ярко, недоступное серым с причиной. */
    private void paintMenu(Graphics2D g, int x0, int cell, int bottom) {
        menuRects.clear();
        menuPanel = null;
        if (menuOf < 0 || menuOf >= items.size() || items.get(menuOf).menu() == null) {
            return;
        }
        List<SubItem> menu = items.get(menuOf).menu();
        Font f1 = Theme.font(15, Font.BOLD);
        Font f2 = Theme.font(12.5, Font.PLAIN);
        int rowH = Theme.px(50);
        int pad = Theme.px(12);
        int mw = Theme.px(420);
        g.setFont(f1);
        for (SubItem si : menu) {
            mw = Math.max(mw, g.getFontMetrics().stringWidth(si.label()) + Theme.px(40));
        }
        mw = Math.min(mw, getWidth() - Theme.px(40));
        int mh = pad * 2 + Theme.px(28) + rowH * menu.size();
        int cx = x0 + cell * menuOf + cell / 2;
        int mx = Math.max(Theme.px(20), Math.min(getWidth() - mw - Theme.px(20), cx - mw / 2));
        int my = Math.max(Theme.px(6), bottom - mh);
        menuPanel = new Rectangle(mx, my, mw, mh);
        g.setColor(Theme.alpha(Theme.bg(), 0.94f));
        g.fill(new RoundRectangle2D.Double(mx, my, mw, mh, Theme.px(16), Theme.px(16)));
        g.setColor(accent);
        g.setStroke(new BasicStroke(Theme.pxf(2)));
        g.draw(new RoundRectangle2D.Double(mx, my, mw, mh, Theme.px(16), Theme.px(16)));
        g.setFont(Theme.font(13, Font.BOLD));
        g.setColor(Theme.ink2());
        g.drawString("ЧЕМ ПОТРАТИТЬ СПЕЦ-ДЕЙСТВИЕ", mx + pad, my + pad + Theme.px(16));
        int y = my + pad + Theme.px(28);
        for (int i = 0; i < menu.size(); i++) {
            SubItem si = menu.get(i);
            Rectangle r = new Rectangle(mx + Theme.px(6), y, mw - Theme.px(12), rowH - Theme.px(4));
            menuRects.add(r);
            if (si.enabled() && i == menuHover) {
                g.setColor(Theme.alpha(accent, 0.28f));
                g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
                    Theme.px(10), Theme.px(10)));
            }
            g.setFont(f1);
            g.setColor(si.enabled() ? Color.WHITE : Theme.ink3());
            g.drawString(si.label(), r.x + Theme.px(10), r.y + Theme.px(20));
            if (si.sub() != null) {
                g.setFont(f2);
                g.setColor(si.enabled() ? Theme.ink2() : Theme.ink3());
                g.drawString(si.sub(), r.x + Theme.px(10), r.y + Theme.px(38));
            }
            y += rowH;
        }
    }
}
