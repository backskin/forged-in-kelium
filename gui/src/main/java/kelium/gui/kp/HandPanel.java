package kelium.gui.kp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import kelium.gui.replay2.MarkIcons;
import kelium.gui.replay2.Theme;

/**
 * ТРИ РУКИ ИГРОКА КАРТОЧКАМИ — приказы, задания, арсенал (концепт §2, этап 3).
 *
 * <p>Каждая группа: рисованная подпись со значком и числом + ряд {@link
 * CardTile}. В фазах выбора карты рука сама становится органом ввода
 * ({@link #setPickable}): совпавшие карты приподняты, прочие пригашены —
 * семья решений B из концепта §3.
 */
public final class HandPanel extends JPanel {

    /** Наведение на карту: (плитка, группа) — окно показывает увеличенную. */
    public interface HoverSink {
        void onHover(CardTile tile, String group);

        void onHoverOff();
    }

    private final Map<String, JPanel> rows = new LinkedHashMap<>();
    private final Map<String, GroupCaption> captions = new LinkedHashMap<>();
    private final List<CardTile> tiles = new ArrayList<>();
    private final HoverSink hover;

    public HandPanel(HoverSink hover) {
        this.hover = hover;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        addGroup("Приказы", "CARD");
        addGroup("Задания", "SUPER");
        addGroup("Арсенал", "ARSENAL");
    }

    private void addGroup(String name, String icon) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        GroupCaption cap = new GroupCaption(name, icon);
        cap.setAlignmentX(LEFT_ALIGNMENT);
        col.add(cap);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.px(4), 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        col.add(row);
        rows.put(name, row);
        captions.put(name, cap);
        add(col);
        add(javax.swing.Box.createHorizontalStrut(Theme.px(12)));
    }

    /** Перестроить группу карт. {@code tag} — ярлык на плитку (прогресс и т.п.). */
    public void setCards(String group, List<String> ids,
                          java.util.function.Function<String, String> nameOf,
                          Color band,
                          java.util.function.Function<String, String> tagOf,
                          Color tagColor) {
        JPanel row = rows.get(group);
        for (var c : row.getComponents()) {
            if (c instanceof CardTile t) {
                tiles.remove(t);
            }
        }
        row.removeAll();
        for (String id : ids) {
            String grp = group;
            CardTile t = new CardTile(id, nameOf.apply(id), band,
                tile -> hover.onHover(tile, grp), hover::onHoverOff);
            t.setPreferredSize(new Dimension(Theme.px(64), Theme.px(88)));
            if (tagOf != null) {
                String tag = tagOf.apply(id);
                if (tag != null) {
                    t.tag(tag, tagColor);
                }
            }
            tiles.add(t);
            row.add(t);
        }
        captions.get(group).setCount(ids.size());
        row.revalidate();
        row.repaint();
    }

    /**
     * Точка решения «карта из руки»: карты из {@code cardToOption} подняты и
     * кликабельны, остальные пригашены. {@code onPick} получает номер опции.
     */
    public void setPickable(Map<String, Integer> cardToOption, BiConsumer<String, Integer> onPick) {
        for (CardTile t : tiles) {
            Integer idx = cardToOption.get(t.cardId);
            if (idx != null) {
                t.setMode(CardTile.Mode.RAISED, () -> onPick.accept(t.cardId, idx));
            } else {
                t.setMode(CardTile.Mode.DIMMED, null);
            }
        }
    }

    /** Вернуть руку в спокойное состояние (выбор карты закончен). */
    public void clearPickable() {
        for (CardTile t : tiles) {
            t.setMode(CardTile.Mode.NORMAL, null);
        }
    }

    /** Все плитки (для прогонщиков/тестов). */
    public List<CardTile> tiles() {
        return List.copyOf(tiles);
    }

    /** Рисованная подпись группы: значок + имя + число карт. */
    private static final class GroupCaption extends JComponent {
        private final String name;
        private final String icon;
        private int count;

        GroupCaption(String name, String icon) {
            this.name = name;
            this.icon = icon;
            setOpaque(false);
            setPreferredSize(new Dimension(Theme.px(120), Theme.px(18)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(18)));
        }

        void setCount(int n) {
            count = n;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double s = Theme.px(11);
            MarkIcons.paint(g, icon, s / 2 + 1, getHeight() / 2.0, s, Theme.ink3());
            g.setFont(Theme.caption());
            g.setColor(Theme.ink3());
            var fm = g.getFontMetrics();
            g.drawString(name.toUpperCase(java.util.Locale.ROOT) + " · " + count,
                (int) s + Theme.px(6),
                (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }
}
