package kelium.gui.kp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * МЕНЮ КАРТ — руку раскладывают перед собой и разбирают.
 *
 * <p>Просьба дизайнера 27.08.2026: вызвать в свой ход список карт заданий,
 * пролистать его вбок, выбрать нужную — она подрастает, — и нажать «выполнить»
 * или «сжечь ради утиль-эффекта». То же для зоны арсенала.
 *
 * <p>ЧТО МОЖНО, РЕШАЕТ ДВИЖОК, А НЕ ЭТО ОКНО. Кнопка действия есть только у той
 * карты, для которой движок прислал соответствующий вариант: выполнить задание
 * можно лишь выполнимое, сжечь — лишь то, у чего есть верхний эффект. Окно
 * ничего не проверяет само и потому не может соврать про правила; недоступное
 * действие показано погашенным, с причиной.
 */
public final class CardMenu extends JComponent {

    /** Действие над картой: подпись, доступность и причина отказа. */
    public record Act(String label, String sub, boolean enabled, String why, Runnable onPick) {
    }

    /** Карта в меню: чем подписана, что на ней написано и что с ней можно. */
    public record Card(String id, String title, String note, String text,
                        Color band, List<Act> acts) {
    }

    private String title = "";
    private final List<Card> cards = new ArrayList<>();
    private Runnable onClose;
    private int selected;
    private int hoverCard = -1;
    private int hoverAct = -1;
    private final Anim fade = new Anim();
    private final Anim slide = new Anim();
    private double offset;
    private double targetOffset;

    public CardMenu() {
        setOpaque(false);
        setVisible(false);
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int c = cardAt(e.getPoint());
                int a = actAt(e.getPoint());
                if (c != hoverCard || a != hoverAct) {
                    hoverCard = c;
                    hoverAct = a;
                    setCursor(c >= 0 || a >= 0
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int a = actAt(e.getPoint());
                if (a >= 0) {
                    List<Act> acts = cards.get(selected).acts();
                    if (a < acts.size() && acts.get(a).enabled()) {
                        Act act = acts.get(a);
                        close();
                        act.onPick().run();
                    }
                    return;
                }
                int c = cardAt(e.getPoint());
                if (c >= 0) {
                    select(c);
                } else if (closeBox().contains(e.getPoint())) {
                    Runnable r = onClose;
                    close();
                    if (r != null) {
                        r.run();
                    }
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                select(Math.max(0, Math.min(cards.size() - 1,
                    selected + (e.getWheelRotation() > 0 ? 1 : -1))));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // окно глотает клики под собой
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
        registerKeyboardAction(e -> select(selected - 1),
            javax.swing.KeyStroke.getKeyStroke("LEFT"), WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(e -> select(selected + 1),
            javax.swing.KeyStroke.getKeyStroke("RIGHT"), WHEN_IN_FOCUSED_WINDOW);
    }

    /** Открыть меню. {@code onClose} — что сделать, если игрок закрыл его. */
    public void open(String title, List<Card> newCards, Runnable onClose) {
        this.title = title;
        cards.clear();
        cards.addAll(newCards);
        this.onClose = onClose;
        selected = 0;
        hoverCard = -1;
        hoverAct = -1;
        offset = 0;
        targetOffset = 0;
        setVisible(true);
        requestFocusInWindow();
        fade.snap(0);
        fade.play(1, 170, v -> repaint(), null);
    }

    public void close() {
        onClose = null;
        setVisible(false);
        repaint();
    }

    public boolean shown() {
        return isVisible();
    }

    /** Карты меню (для прогонщиков и тестов). */
    public List<Card> cards() {
        return List.copyOf(cards);
    }

    public int selectedIndex() {
        return selected;
    }

    /**
     * Выбрать карту — она подрастает и выезжает в середину. Лента едет плавно:
     * рывок сбивает с того, какая карта была под рукой.
     */
    public void select(int i) {
        if (cards.isEmpty()) {
            return;
        }
        selected = Math.max(0, Math.min(cards.size() - 1, i));
        hoverAct = -1;
        double was = offset;
        targetOffset = -selected * (double) step();
        slide.snap(0);
        slide.play(1, 220, v -> {
            offset = was + (targetOffset - was) * v;
            repaint();
        }, null);
    }

    // ==================== геометрия ====================

    private int cardW() {
        return Math.max(Theme.px(118), Math.min(Theme.px(168), getWidth() / 7));
    }

    private int cardH() {
        return (int) (cardW() * 1.42);
    }

    private int step() {
        return cardW() + Theme.px(14);
    }

    private int stripY() {
        return getHeight() / 2 - cardH() / 2 - Theme.px(40);
    }

    private Rectangle cardRect(int i) {
        int w = cardW();
        int h = cardH();
        // Выбранная карта чуть крупнее — «слегка увеличивается», как просили.
        boolean sel = i == selected;
        int gw = sel ? (int) (w * 1.12) : w;
        int gh = sel ? (int) (h * 1.12) : h;
        int cx = getWidth() / 2 + (int) Math.round(offset) + i * step();
        return new Rectangle(cx - gw / 2, stripY() + (h - gh) / 2, gw, gh);
    }

    private int cardAt(Point p) {
        for (int i = 0; i < cards.size(); i++) {
            if (cardRect(i).contains(p)) {
                return i;
            }
        }
        return -1;
    }

    private Rectangle actRect(int i) {
        int n = Math.max(1, acts().size());
        int w = Math.min(Theme.px(300), (getWidth() - Theme.px(80)) / n);
        int h = Theme.px(54);
        int total = w * n + Theme.px(10) * (n - 1);
        int x0 = (getWidth() - total) / 2;
        // Кнопки держатся ВЫШЕ нижней зоны игрока: она видна сквозь затемнение,
        // и класть действия прямо на её кнопки — верный способ промахнуться.
        return new Rectangle(x0 + i * (w + Theme.px(10)),
            getHeight() - h - Theme.px(148), w, h);
    }

    private List<Act> acts() {
        return cards.isEmpty() ? List.of() : cards.get(selected).acts();
    }

    private int actAt(Point p) {
        List<Act> a = acts();
        for (int i = 0; i < a.size(); i++) {
            if (actRect(i).contains(p)) {
                return i;
            }
        }
        return -1;
    }

    private Rectangle closeBox() {
        int w = Theme.px(150);
        return new Rectangle((getWidth() - w) / 2, getHeight() - Theme.px(126), w, Theme.px(24));
    }

    // ==================== рисование ====================

    @Override
    protected void paintComponent(Graphics g0) {
        double a = fade.value();
        if (a <= 0.01 || cards.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.SrcOver.derive((float) (0.74 * a)));
        g.setColor(new Color(0x10, 0x14, 0x1A));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setComposite(AlphaComposite.SrcOver.derive((float) a));

        g.setFont(Theme.font(17, Font.BOLD));
        g.setColor(Color.WHITE);
        var fm = g.getFontMetrics();
        g.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, stripY() - Theme.px(26));

        // Карты: сперва все прочие, выбранная — последней, поверх соседей.
        for (int i = 0; i < cards.size(); i++) {
            if (i != selected) {
                paintCard(g, i);
            }
        }
        paintCard(g, selected);

        paintText(g, cards.get(selected));
        List<Act> acts = acts();
        for (int i = 0; i < acts.size(); i++) {
            paintAct(g, i, acts.get(i));
        }

        g.setFont(Theme.font(11.5, Font.PLAIN));
        g.setColor(new Color(255, 255, 255, 170));
        String hint = cards.size() > 1
            ? "← → или колесо — листать · клик по карте — выбрать · закрыть"
            : "закрыть";
        var f2 = g.getFontMetrics();
        Rectangle cb = closeBox();
        g.drawString(hint, (getWidth() - f2.stringWidth(hint)) / 2,
            cb.y + f2.getAscent() - Theme.px(1));
        g.dispose();
    }

    private void paintCard(Graphics2D g, int i) {
        Rectangle r = cardRect(i);
        if (r.x + r.width < -Theme.px(40) || r.x > getWidth() + Theme.px(40)) {
            return;                       // уехала за край — не рисуем
        }
        Card c = cards.get(i);
        boolean sel = i == selected;
        g.setColor(new Color(0, 0, 0, sel ? 120 : 80));
        g.fillRoundRect(r.x + 3, r.y + 6, r.width, r.height, Theme.px(10), Theme.px(10));
        g.setColor(sel ? Theme.paper() : Theme.alpha(Theme.paper(), 0.72));
        g.fillRoundRect(r.x, r.y, r.width, r.height, Theme.px(10), Theme.px(10));
        g.setColor(c.band());
        g.fillRoundRect(r.x, r.y, r.width, Theme.px(16), Theme.px(10), Theme.px(10));
        g.fillRect(r.x, r.y + Theme.px(9), r.width, Theme.px(7));
        g.setColor(sel ? Theme.accent() : Theme.alpha(Theme.border(), 0.8));
        g.setStroke(new BasicStroke(sel ? Theme.pxf(2.4) : 1f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, Theme.px(10), Theme.px(10));

        g.setFont(Theme.font(sel ? 13 : 12, Font.BOLD));
        g.setColor(sel ? Theme.ink() : Theme.ink2());
        var fm = g.getFontMetrics();
        int ty = r.y + Theme.px(30);
        for (String line : CardTile.wrap(c.title(), fm, r.width - Theme.px(16), 3)) {
            g.drawString(line, r.x + Theme.px(8), ty);
            ty += fm.getHeight();
        }
        if (c.note() != null && !c.note().isBlank()) {
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            var f2 = g.getFontMetrics();
            ty += Theme.px(4);
            for (String line : CardTile.wrap(c.note(), f2, r.width - Theme.px(16), 4)) {
                g.drawString(line, r.x + Theme.px(8), ty);
                ty += f2.getHeight();
            }
        }
    }

    /** Печатный текст выбранной карты — под лентой, на тёмном. */
    private void paintText(Graphics2D g, Card c) {
        if (c.text() == null || c.text().isBlank()) {
            return;
        }
        int w = Math.min(Theme.px(700), getWidth() - Theme.px(60));
        int x = (getWidth() - w) / 2;
        int y = stripY() + (int) (cardH() * 1.12) + Theme.px(12);
        g.setFont(Theme.font(12.5, Font.PLAIN));
        var fm = g.getFontMetrics();
        List<String> lines = CardTile.wrap(c.text(), fm, w - Theme.px(24), 5);
        int h = lines.size() * fm.getHeight() + Theme.px(18);
        g.setColor(new Color(0x1B, 0x1F, 0x26, 235));
        g.fillRoundRect(x, y, w, h, Theme.px(10), Theme.px(10));
        g.setColor(new Color(255, 255, 255, 235));
        int ty = y + Theme.px(10) + fm.getAscent() - fm.getHeight();
        for (String line : lines) {
            ty += fm.getHeight();
            g.drawString(line, x + Theme.px(12), ty);
        }
    }

    private void paintAct(Graphics2D g, int i, Act act) {
        Rectangle r = actRect(i);
        boolean hot = i == hoverAct && act.enabled();
        g.setColor(act.enabled() ? (hot ? Theme.alpha(Theme.accent(), 0.92) : Theme.accent())
            : new Color(0x2C, 0x31, 0x38, 230));
        g.fillRoundRect(r.x, r.y, r.width, r.height, Theme.px(8), Theme.px(8));
        if (!act.enabled()) {
            g.setColor(new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{Theme.pxf(4), Theme.pxf(4)}, 0f));
            g.drawRoundRect(r.x, r.y, r.width, r.height, Theme.px(8), Theme.px(8));
        }
        g.setFont(Theme.font(14, Font.BOLD));
        g.setColor(act.enabled() ? Color.WHITE : new Color(255, 255, 255, 110));
        var fm = g.getFontMetrics();
        g.drawString(act.label(), r.x + (r.width - fm.stringWidth(act.label())) / 2,
            r.y + Theme.px(24));
        String sub = act.enabled() ? act.sub() : act.why();
        if (sub != null && !sub.isBlank()) {
            g.setFont(Theme.font(10.5, Font.PLAIN));
            g.setColor(act.enabled() ? new Color(255, 255, 255, 200)
                : new Color(255, 255, 255, 120));
            var f2 = g.getFontMetrics();
            String cut = KpButton.ellipsize(sub, f2, r.width - Theme.px(14));
            g.drawString(cut, r.x + (r.width - f2.stringWidth(cut)) / 2, r.y + Theme.px(41));
        }
    }
}
