package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/**
 * Collapsible — рамка с заголовком и кнопкой «свернуть/развернуть».
 *
 * <p>Свёрнутая рамка занимает ровно одну полоску заголовка: содержимое
 * прячется, а место отдаётся соседям. Нужна проигрывателю, где на экране тесно
 * от четырёх зон игроков, лога и панели настроек, — дизайнер попросил уметь
 * убирать всё лишнее, кроме самого поля (12.08.2026).
 *
 * <p>Если рамка живёт внутри {@link JSplitPane}, свяжи её через
 * {@link #bindTo(JSplitPane, boolean)}: тогда сворачивание не просто прячет
 * содержимое, а отводит разделитель до упора, освобождая место по-настоящему.
 */
public final class Collapsible extends JPanel {

    private static final Color BAR_BG = new Color(0xE8E6DE);
    private static final Color BAR_INK = new Color(0x3A3730);

    private final JButton toggle = new JButton();
    private final JLabel title = new JLabel();
    private final Component content;
    private final JPanel bar = new JPanel(new BorderLayout(4, 0));

    private boolean open = true;
    private JSplitPane split;
    private boolean contentIsFirst;
    private int savedDivider = -1;

    public Collapsible(String titleText, Component content) {
        super(new BorderLayout(0, 0));
        this.content = content;

        title.setText(titleText);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(BAR_INK);

        toggle.setMargin(new Insets(0, 4, 0, 4));
        toggle.setFocusPainted(false);
        toggle.setFont(toggle.getFont().deriveFont(Font.BOLD, 10f));
        toggle.addActionListener(e -> setOpen(!open));

        bar.setBackground(BAR_BG);
        bar.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 2));
        bar.add(title, BorderLayout.WEST);
        bar.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        bar.add(toggle, BorderLayout.EAST);

        add(bar, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        refresh();
    }

    /** Заголовок полоски (можно менять на ходу — например имя игрока). */
    public void setTitleText(String text) {
        title.setText(text);
    }

    /** Вторая кнопка полоски — например «сжать» для компактного вида. */
    private JButton extra;
    private boolean extraOn;

    /**
     * ВТОРАЯ СТЕПЕНЬ СВОРАЧИВАНИЯ (просьба дизайнера 12.08.2026: «сворачивать
     * двумя способами — полностью и чуть-чуть»). Рядом с кнопкой «свернуть»
     * появляется вторая: она не прячет содержимое, а переключает его в
     * компактный вид — за это отвечает сам владелец через {@code onToggle}.
     *
     * @param onLabel  подпись кнопки, когда компактный вид ВЫКЛЮЧЕН
     * @param offLabel подпись, когда компактный вид ВКЛЮЧЁН
     * @param tip      подсказка
     * @param onToggle куда сообщать новое состояние (true = компактно)
     */
    public void addExtraToggle(String onLabel, String offLabel, String tip,
                               java.util.function.Consumer<Boolean> onToggle) {
        extra = new JButton(onLabel);
        extra.setMargin(new Insets(0, 4, 0, 4));
        extra.setFocusPainted(false);
        extra.setFocusable(false);
        extra.setFont(extra.getFont().deriveFont(Font.BOLD, 10f));
        extra.setToolTipText(tip);
        extra.addActionListener(e -> {
            extraOn = !extraOn;
            extra.setText(extraOn ? offLabel : onLabel);
            onToggle.accept(extraOn);
            revalidate();
            repaint();
        });
        JPanel right = new JPanel(new BorderLayout(3, 0));
        right.setOpaque(false);
        right.add(extra, BorderLayout.WEST);
        right.add(toggle, BorderLayout.EAST);
        bar.remove(toggle);
        bar.add(right, BorderLayout.EAST);
    }

    /** Включён ли компактный вид (вторая степень сворачивания). */
    public boolean isCompact() {
        return extraOn;
    }

    /**
     * Привязать к разделителю: при сворачивании он уходит до упора, при
     * разворачивании возвращается на прежнее место.
     *
     * @param contentIsFirst true, если эта рамка — ПЕРВЫЙ компонент разделителя
     *                       (левый или верхний)
     */
    public void bindTo(JSplitPane pane, boolean contentIsFirst) {
        this.split = pane;
        this.contentIsFirst = contentIsFirst;
    }

    public boolean isOpen() {
        return open;
    }

    /** Свернуть или развернуть рамку. */
    public void setOpen(boolean value) {
        if (open == value) {
            return;
        }
        if (split != null && open) {
            savedDivider = split.getDividerLocation();
        }
        open = value;
        refresh();
        if (split == null) {
            return;
        }
        if (!open) {
            // отдать место соседу: разделитель до упора в сторону этой рамки
            int barSize = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
                ? bar.getPreferredSize().width + 8
                : bar.getPreferredSize().height + 8;
            split.setDividerLocation(contentIsFirst ? barSize
                : Math.max(0, splitExtent() - barSize - split.getDividerSize()));
        } else if (savedDivider >= 0) {
            split.setDividerLocation(savedDivider);
        }
        split.revalidate();
    }

    private int splitExtent() {
        return split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
            ? split.getWidth() : split.getHeight();
    }

    private void refresh() {
        content.setVisible(open);
        toggle.setText(open ? "▾" : "▸");
        toggle.setToolTipText(open ? "Свернуть до полоски" : "Развернуть");
        if (open) {
            setMinimumSize(null);
            setMaximumSize(null);
            setPreferredSize(null);
        } else {
            int h = bar.getPreferredSize().height + 2;
            setPreferredSize(new Dimension(90, h));
            setMinimumSize(new Dimension(60, h));
        }
        revalidate();
        repaint();
    }
}
