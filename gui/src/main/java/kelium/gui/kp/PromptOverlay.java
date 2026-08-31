package kelium.gui.kp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

import kelium.gui.replay2.Theme;

/**
 * КОНТЕКСТНАЯ ПАНЕЛЬ ТОЧКИ РЕШЕНИЯ — плавает ПОВЕРХ поля у нижнего края (как
 * подсказки размещения в RTS и на макете «Экран 2»), не двигая постоянную зону
 * игрока. Содержит заголовок с полосой цвета места и либо подсказку («Выберите
 * гекс на поле»), либо ряд плашек-вариантов с переносом.
 */
public final class PromptOverlay extends JPanel {

    private final Header header = new Header();
    private final JPanel body = new JPanel(new WrapFlow(Theme.px(6), Theme.px(6)));
    private final List<KpButton> options = new ArrayList<>();

    public PromptOverlay() {
        setOpaque(false);
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
        header.setAlignmentX(LEFT_ALIGNMENT);
        body.setOpaque(false);
        body.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(body);
        setVisible(false);
    }

    public void showHint(int seat, String title, String hint) {
        header.set(seat, title);
        body.removeAll();
        options.clear();
        KpButton h = new KpButton(hint, "", null);
        h.setState(KpButton.State.DISABLED);
        h.setPreferredSize(new Dimension(Theme.px(300), Theme.px(30)));
        body.add(h);
        finishLayout();
    }

    public record Option(String label, Runnable onPick) {
    }

    public void showOptions(int seat, String title, List<Option> opts) {
        header.set(seat, title);
        body.removeAll();
        options.clear();
        for (Option o : opts) {
            KpButton b = new KpButton(o.label(), "", null);
            b.setState(KpButton.State.AVAILABLE);
            b.onClick(o.onPick());
            int w = Math.min(Theme.px(320),
                Math.max(Theme.px(120), Theme.px(24) + o.label().length() * Theme.px(7)));
            b.setPreferredSize(new Dimension(w, Theme.px(32)));
            options.add(b);
            body.add(b);
        }
        finishLayout();
    }

    private void finishLayout() {
        setVisible(true);
        revalidate();
        repaint();
    }

    public void hideAll() {
        setVisible(false);
        body.removeAll();
        options.clear();
    }

    /** Плашки вариантов (для прогонщиков/тестов). */
    public List<KpButton> options() {
        return List.copyOf(options);
    }

    /** Заголовок: полоса цвета места + название точки решения. */
    private static final class Header extends JComponent {
        private int seat;
        private String text = "";

        Header() {
            setOpaque(false);
            setPreferredSize(new Dimension(Theme.px(320), Theme.px(26)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(26)));
        }

        void set(int seat, String text) {
            this.seat = seat;
            this.text = text;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(Theme.font(12.5, Font.BOLD));
            var fm = g.getFontMetrics();
            int w = Math.min(getWidth(),
                Theme.px(18) + fm.stringWidth(text) + Theme.px(12));
            int h = getHeight() - Theme.px(4);
            g.setColor(Theme.panel());
            g.fillRoundRect(0, 0, w, h, Theme.px(8), Theme.px(8));
            g.setColor(Theme.border());
            g.drawRoundRect(0, 0, w, h, Theme.px(8), Theme.px(8));
            g.setColor(Theme.seat(seat));
            g.fillRoundRect(0, Theme.px(3), Theme.px(4), h - Theme.px(6), 2, 2);
            g.setColor(Theme.seatInk(seat));
            g.drawString(text, Theme.px(12), (h + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /**
     * FlowLayout с честным переносом строк ВНУТРИ фиксированной ширины: сам
     * FlowLayout высоту под перенос не сообщает, и панель обрезалась бы.
     */
    static final class WrapFlow extends java.awt.FlowLayout {
        WrapFlow(int hgap, int vgap) {
            super(LEFT, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(java.awt.Container target) {
            int maxW = target.getWidth() > 0 ? target.getWidth()
                : target.getParent() != null && target.getParent().getWidth() > 0
                    ? target.getParent().getWidth() : Theme.px(700);
            int x = getHgap();
            int rowH = 0;
            int y = getVgap();
            for (var c : target.getComponents()) {
                Dimension d = c.getPreferredSize();
                if (x + d.width + getHgap() > maxW && x > getHgap()) {
                    x = getHgap();
                    y += rowH + getVgap();
                    rowH = 0;
                }
                x += d.width + getHgap();
                rowH = Math.max(rowH, d.height);
            }
            return new Dimension(maxW, y + rowH + getVgap());
        }
    }
}
