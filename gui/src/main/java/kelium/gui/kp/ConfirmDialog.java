package kelium.gui.kp;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * МОДАЛЬНОЕ ОКНО НЕОБРАТИМОГО РЕШЕНИЯ (концепт §6): затемняющая шторка на всё
 * окно + карточка по центру с плавным появлением (масштаб 0.94→1, прозрачность
 * 0→1, ~160 мс). Красная полоса и предупреждение прямо называют цену: «после
 * этого откат станет недоступен». Варианты — красные плашки с подписью расхода,
 * отказ — серая; Esc = отказ. Всё нарисовано одним компонентом — и шторка, и
 * карточка, — чтобы прозрачность анимировалась честно.
 */
public final class ConfirmDialog extends JComponent {

    public record Option(String label, String sub, Runnable onPick) {
    }

    private String title = "";
    private String warn = "";
    private final List<String> info = new ArrayList<>();
    private final List<Option> options = new ArrayList<>();
    private Option cancel;
    private final Anim anim = new Anim();
    private int hoverIdx = -2;      // -1 = отказ, -2 = ничего

    public ConfirmDialog() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = optionAt(e.getPoint());
                if (i != hoverIdx) {
                    hoverIdx = i;
                    setCursor(i != -2 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int i = optionAt(e.getPoint());
                if (i >= 0 && i < options.size()) {
                    options.get(i).onPick().run();
                } else if (i == -1 && cancel != null) {
                    cancel.onPick().run();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // шторка глотает клики — под ней ничего не нажимается
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        registerKeyboardAction(e -> {
            if (cancel != null) {
                cancel.onPick().run();
            }
        }, javax.swing.KeyStroke.getKeyStroke("ESCAPE"), WHEN_IN_FOCUSED_WINDOW);
    }

    /** Показать окно с появлением. {@code cancel} может быть null (нет отказа). */
    public void open(String title, String warn, List<String> info,
                      List<Option> options, Option cancel) {
        this.title = title;
        this.warn = warn == null ? "" : warn;
        this.info.clear();
        this.info.addAll(info);
        this.options.clear();
        this.options.addAll(options);
        this.cancel = cancel;
        this.hoverIdx = -2;
        setVisible(true);
        anim.snap(0);
        anim.play(1, 160, v -> repaint(), null);
    }

    public void close() {
        anim.play(0, 120, v -> repaint(), () -> setVisible(false));
    }

    public boolean isOpen() {
        return isVisible() && anim.value() > 0.5;
    }

    /** Варианты (для прогонщиков/тестов). */
    public List<Option> options() {
        return List.copyOf(options);
    }

    public Option cancelOption() {
        return cancel;
    }

    // ==================== геометрия ====================

    private int cardW() {
        return Math.min(Theme.px(460), getWidth() - Theme.px(40));
    }

    private int rowH() {
        return Theme.px(40);
    }

    private int cardH() {
        int h = Theme.px(56);                       // шапка
        if (!warn.isEmpty()) {
            h += Theme.px(24);
        }
        h += info.size() * Theme.px(20) + Theme.px(10);
        h += options.size() * (rowH() + Theme.px(6));
        if (cancel != null) {
            h += rowH() + Theme.px(10);
        }
        return h + Theme.px(16);
    }

    private Rectangle cardRect() {
        int w = cardW();
        int h = cardH();
        return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
    }

    private Rectangle optionRect(int i) {
        Rectangle c = cardRect();
        int y = c.y + Theme.px(56)
            + (warn.isEmpty() ? 0 : Theme.px(24))
            + info.size() * Theme.px(20) + Theme.px(10)
            + i * (rowH() + Theme.px(6));
        return new Rectangle(c.x + Theme.px(14), y, c.width - Theme.px(28), rowH());
    }

    private Rectangle cancelRect() {
        Rectangle last = optionRect(options.size());
        return new Rectangle(last.x, last.y + Theme.px(4), last.width, rowH());
    }

    /** −2 — мимо, −1 — отказ, иначе номер варианта. */
    private int optionAt(java.awt.Point p) {
        for (int i = 0; i < options.size(); i++) {
            if (optionRect(i).contains(p)) {
                return i;
            }
        }
        if (cancel != null && cancelRect().contains(p)) {
            return -1;
        }
        return -2;
    }

    // ==================== рисование ====================

    @Override
    protected void paintComponent(Graphics g0) {
        double a = anim.value();
        if (a <= 0.01) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // шторка
        g.setComposite(AlphaComposite.SrcOver.derive((float) (0.45 * a)));
        g.setColor(new Color(0x10, 0x14, 0x1A));
        g.fillRect(0, 0, getWidth(), getHeight());

        // карточка с масштабом появления
        g.setComposite(AlphaComposite.SrcOver.derive((float) a));
        Rectangle c = cardRect();
        double sc = 0.94 + 0.06 * a;
        g.translate(c.getCenterX(), c.getCenterY());
        g.scale(sc, sc);
        g.translate(-c.getCenterX(), -c.getCenterY());

        int arc = Theme.px(14);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRoundRect(c.x + Theme.px(3), c.y + Theme.px(5), c.width, c.height, arc, arc);
        g.setColor(Theme.panel());
        g.fillRoundRect(c.x, c.y, c.width, c.height, arc, arc);
        g.setColor(Theme.bad());
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.drawRoundRect(c.x, c.y, c.width, c.height, arc, arc);
        g.fillRoundRect(c.x, c.y, c.width, Theme.px(6), arc, arc);
        g.fillRect(c.x, c.y + Theme.px(3), c.width, Theme.px(3));

        int x = c.x + Theme.px(16);
        int y = c.y + Theme.px(34);
        g.setFont(Theme.font(15, Font.BOLD));
        g.setColor(Theme.ink());
        g.drawString(KpButton.ellipsize(title, g.getFontMetrics(), c.width - Theme.px(32)), x, y);
        y += Theme.px(20);
        if (!warn.isEmpty()) {
            g.setFont(Theme.font(11.5, Font.BOLD));
            g.setColor(Theme.bad());
            g.drawString(KpButton.ellipsize(warn, g.getFontMetrics(), c.width - Theme.px(32)),
                x, y);
            y += Theme.px(24);
        }
        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(Theme.ink2());
        for (String line : info) {
            g.drawString(KpButton.ellipsize(line, g.getFontMetrics(), c.width - Theme.px(32)),
                x, y);
            y += Theme.px(20);
        }

        for (int i = 0; i < options.size(); i++) {
            paintPlate(g, optionRect(i), options.get(i).label(), options.get(i).sub(),
                true, hoverIdx == i);
        }
        if (cancel != null) {
            paintPlate(g, cancelRect(), cancel.label(), cancel.sub(), false, hoverIdx == -1);
        }
        g.dispose();
    }

    private void paintPlate(Graphics2D g, Rectangle r, String label, String sub,
                            boolean danger, boolean hovered) {
        Color line = danger ? Theme.bad() : Theme.border();
        Color bg = hovered
            ? (danger ? Theme.alpha(Theme.bad(), Theme.isDark() ? 0.28 : 0.12) : Theme.hover())
            : Theme.tile();
        g.setColor(bg);
        g.fillRoundRect(r.x, r.y, r.width, r.height, Theme.px(9), Theme.px(9));
        g.setColor(line);
        g.setStroke(new BasicStroke(hovered ? Theme.pxf(1.8) : 1f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, Theme.px(9), Theme.px(9));
        g.setFont(Theme.font(12.5, Font.BOLD));
        g.setColor(danger ? (Theme.isDark() ? Theme.bad() : Theme.darken(Theme.bad(), 0.15))
            : Theme.ink2());
        var fm = g.getFontMetrics();
        boolean hasSub = sub != null && !sub.isBlank();
        int ty = hasSub ? r.y + Theme.px(17) : r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(KpButton.ellipsize(label, fm, r.width - Theme.px(20)),
            r.x + Theme.px(10), ty);
        if (hasSub) {
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString(KpButton.ellipsize(sub, g.getFontMetrics(), r.width - Theme.px(20)),
                r.x + Theme.px(10), r.y + Theme.px(31));
        }
    }
}
