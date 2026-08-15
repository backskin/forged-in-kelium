package kelium.gui.replay2;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

/**
 * Ui2 — мелкие сборщики элементов интерфейса 2.0.
 *
 * <p>Нужны, чтобы одинаковые вещи выглядели одинаково: кнопка-значок, подпись,
 * подсказка. В 1.0 каждый элемент настраивался на месте — отсюда пять разных
 * размеров шрифта на одном экране.
 */
public final class Ui2 {

    private Ui2() {
    }

    /**
     * Подсказка при наведении. Ширину задаём явно: иначе Swing режет длинную строку
     * многоточием, и полный текст (а в нём как раз полный перечень зданий) не достать.
     */
    public static String tip(String text) {
        return tip(text, 360);
    }

    public static String tip(String text, int widthPx) {
        String body = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br>");
        return "<html><div style='width:" + Theme.px(widthPx) + "px'>" + body + "</div></html>";
    }

    /** Кнопка-значок пульта: одинаковая коробка, без рамки фокуса, с подсказкой. */
    public static JButton iconButton(Icon icon, String tip, int side, Runnable action) {
        JButton b = new JButton(icon);
        b.setToolTipText(tip(tip));
        b.setFocusable(false);
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        Dimension d = new Dimension(Theme.px(side), Theme.px(side));
        b.setPreferredSize(d);
        b.setMinimumSize(d);
        b.setMaximumSize(d);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Переключатель-значок: то же, но с состоянием (например «следить за логом»). */
    public static JToggleButton iconToggle(Icon icon, String tip, int side,
                                          java.util.function.Consumer<Boolean> action) {
        JToggleButton b = new JToggleButton(icon);
        b.setToolTipText(tip(tip));
        b.setFocusable(false);
        Dimension d = new Dimension(Theme.px(side), Theme.px(side));
        b.setPreferredSize(d);
        b.setMinimumSize(d);
        b.addActionListener(e -> action.accept(b.isSelected()));
        return b;
    }

    /** Кнопка словами — для мест, где значок был бы загадкой. */
    public static JButton textButton(String text, String tip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tip(tip));
        b.setFocusable(false);
        // НАДПИСЬ НА КНОПКЕ — КРУПНЕЕ ОБЫЧНОГО ТЕКСТА. Кнопку читают одним
        // взглядом и по ней сразу жмут; общий кегль для неё мелковат
        // (замечание дизайнера 14.08.2026).
        b.setFont(Theme.font(14, java.awt.Font.PLAIN));
        b.setMargin(new Insets(Theme.px(4), Theme.px(10), Theme.px(4), Theme.px(10)));
        b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * ПОМЕТКА ЦВЕТА ТЕКСТА. Цвет, выставленный руками, при переключении темы сам не
     * меняется — надпись остаётся тёмной на тёмном. Поэтому каждый такой элемент
     * помечается ролью, а окно после смены темы проходит по дереву и красит заново
     * (см. {@code Replay2Gui.restyle}).
     */
    public static final String FG_ROLE = "kelium.fg";

    /** Задать цвет текста ПО РОЛИ: ink, ink2, ink3, accent. */
    public static <T extends JComponent> T fg(T c, String role) {
        c.putClientProperty(FG_ROLE, role);
        c.setForeground(colourOf(role));
        return c;
    }

    /** Цвет по роли — им же пользуется перекраска после смены темы. */
    public static java.awt.Color colourOf(String role) {
        return switch (role) {
            case "ink2" -> Theme.ink2();
            case "ink3" -> Theme.ink3();
            case "accent" -> Theme.accent();
            default -> Theme.ink();
        };
    }

    /** Подпись ЗАГЛАВНЫМИ: заголовок блока или столбца. */
    public static JLabel caption(String text) {
        JLabel l = new JLabel(text.toUpperCase(java.util.Locale.ROOT));
        l.setFont(Theme.caption());
        return fg(l, "ink3");
    }

    public static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.body());
        return fg(l, "ink");
    }

    /** Пустая панель нужного цвета — подложка блока. */
    public static JPanel panel(java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(Theme.panel());
        p.setOpaque(true);
        return p;
    }

    /** Разделитель-волосок между блоками. */
    public static Component hairline(boolean vertical) {
        JPanel p = new JPanel();
        p.setBackground(Theme.border());
        Dimension d = vertical ? new Dimension(1, Integer.MAX_VALUE)
                               : new Dimension(Integer.MAX_VALUE, 1);
        p.setPreferredSize(vertical ? new Dimension(1, Theme.px(20))
                                    : new Dimension(Theme.px(20), 1));
        p.setMaximumSize(d);
        return p;
    }

    /**
     * ТОНКАЯ ПОЛОСА ПРОКРУТКИ ВМЕСТО ОБЫЧНОЙ: без кнопок-стрелок, высотой в
     * волосок. Ставится там, где прокрутка — не полноценный орган управления, а
     * подсказка «содержимое шире окна, тяни вбок»: лента настроек и конвейер
     * полос игроков. Обычный скроллбар в этих местах съедает высоту, которой и
     * так нет, и тянет на себя внимание.
     *
     * @param sc      панель прокрутки, чью горизонталь утоньшаем
     * @param thickPx толщина полосы в логических точках темы
     */
    public static void thinHorizontalBar(javax.swing.JScrollPane sc, int thickPx) {
        javax.swing.JScrollBar bar = sc.getHorizontalScrollBar();
        bar.setPreferredSize(new Dimension(0, Theme.px(thickPx)));
        bar.putClientProperty("JScrollBar.showButtons", false);
        bar.setUnitIncrement(Theme.px(24));
        bar.setBlockIncrement(Theme.px(120));
    }

    /** Шрифт с разрядкой — для подписей ЗАГЛАВНЫМИ. */
    public static Font tracked(Font base, double extra) {
        java.util.Map<java.awt.font.TextAttribute, Object> a = new java.util.HashMap<>();
        a.put(java.awt.font.TextAttribute.TRACKING, extra);
        return base.deriveFont(a);
    }
}
