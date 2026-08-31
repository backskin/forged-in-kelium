package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

import kelium.gui.replay2.Theme;

/**
 * ВЫБОР ИЗ СПИСКА — рисованный, вместо стокового {@code JComboBox}.
 *
 * <p>Правило проекта (разнос дизайнера 24.08.2026): в игровых зонах не бывает
 * стоковых Swing-виджетов, иначе экран выглядит «из 2001-го». Здесь кнопка с
 * текущим значением, а по щелчку поверх окна разворачивается список: у каждой
 * строки заголовок и, если есть, поясняющая строка — так соперника выбирают,
 * читая, как он играет, а не угадывая по имени.
 */
public final class KpChooser extends JComponent {

    /** Строка списка: что показать и что вернуть. */
    public record Item(String value, String title, String note) {
        public Item(String value, String title) {
            this(value, title, null);
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Consumer<Item> onPick;
    private final String caption;
    private int index;
    private boolean hover;
    private Popup popup;
    /** Прозрачная подложка под списком: ловит щелчок мимо и закрывает его. */
    private JComponent glass;

    public KpChooser(String caption, List<Item> items, Consumer<Item> onPick) {
        this.caption = caption;
        this.items.addAll(items);
        this.onPick = onPick;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(Theme.px(180), Theme.px(caption == null ? 30 : 40)));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }
        });
    }

    /** Текущий выбор (null — список пуст). */
    public Item selected() {
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    /** Поставить выбор по значению; нет такого — оставить как было. */
    public void select(String value) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).value().equals(value)) {
                index = i;
                repaint();
                return;
            }
        }
    }

    /** Заменить список целиком (сменилось число игроков — сменились раскладки). */
    public void setItems(List<Item> newItems, String keep) {
        items.clear();
        items.addAll(newItems);
        index = 0;
        if (keep != null) {
            select(keep);
        }
        repaint();
    }

    /** Для прогонщиков и тестов: выбрать строку по номеру, как щелчком. */
    public void pick(int i) {
        if (i < 0 || i >= items.size()) {
            return;
        }
        index = i;
        close();
        repaint();
        if (onPick != null) {
            onPick.accept(items.get(i));
        }
    }

    public List<Item> items() {
        return List.copyOf(items);
    }

    // ==================== раскрытый список ====================

    private void toggle() {
        if (popup != null) {
            close();
            return;
        }
        JLayeredPane layer = layerOf();
        if (layer == null || items.isEmpty()) {
            return;
        }
        popup = new Popup();
        int w = Math.max(getWidth(), Theme.px(310));
        int h = popup.wanted(w);
        Point p = SwingUtilities.convertPoint(this, 0, getHeight(), layer);
        // Список не должен уходить за нижнюю кромку окна — не влезает вниз,
        // раскрываем вверх.
        int y = p.y + h > layer.getHeight() - Theme.px(8)
            ? Math.max(Theme.px(8), p.y - getHeight() - h) : p.y;
        int x = Math.max(Theme.px(4), Math.min(p.x, layer.getWidth() - w - Theme.px(4)));
        popup.setBounds(x, y, w, h);
        // Щелчок МИМО списка должен его закрывать — иначе он висит поверх окна,
        // пока не ткнёшь в саму строку. Ловит подложка во весь слой, а сам
        // список лежит выше неё.
        glass = new JComponent() {
        };
        glass.setOpaque(false);
        glass.setBounds(0, 0, layer.getWidth(), layer.getHeight());
        glass.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                close();
            }
        });
        layer.add(glass, JLayeredPane.POPUP_LAYER);
        layer.add(popup, JLayeredPane.DRAG_LAYER);
        layer.revalidate();
        layer.repaint();
    }

    private void close() {
        if (popup == null) {
            return;
        }
        JLayeredPane layer = layerOf();
        if (layer != null) {
            layer.remove(popup);
            if (glass != null) {
                layer.remove(glass);
            }
            layer.repaint();
        }
        popup = null;
        glass = null;
    }

    private JLayeredPane layerOf() {
        java.awt.Window w = SwingUtilities.getWindowAncestor(this);
        return w instanceof javax.swing.RootPaneContainer rc ? rc.getLayeredPane() : null;
    }

    private final class Popup extends JComponent {
        private int hoverRow = -1;

        Popup() {
            setOpaque(false);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int r = rowAt(e.getY());
                    if (r != hoverRow) {
                        hoverRow = r;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverRow = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int r = rowAt(e.getY());
                    if (r >= 0) {
                        pick(r);
                    } else {
                        close();
                    }
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        int rowH() {
            boolean notes = items.stream().anyMatch(i -> i.note() != null && !i.note().isBlank());
            return notes ? Theme.px(38) : Theme.px(24);
        }

        int wanted(int w) {
            return items.size() * rowH() + Theme.px(8);
        }

        int rowAt(int y) {
            int r = (y - Theme.px(4)) / rowH();
            return r >= 0 && r < items.size() ? r : -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(new Color(0, 0, 0, 60));
            g.fillRoundRect(Theme.px(2), Theme.px(3), w - Theme.px(4), h - Theme.px(4),
                Theme.px(8), Theme.px(8));
            g.setColor(Theme.panel());
            g.fillRoundRect(0, 0, w - Theme.px(2), h - Theme.px(2), Theme.px(8), Theme.px(8));
            g.setColor(Theme.accent());
            g.setStroke(new BasicStroke(Theme.pxf(1.4)));
            g.drawRoundRect(0, 0, w - Theme.px(2) - 1, h - Theme.px(2) - 1,
                Theme.px(8), Theme.px(8));

            int rh = rowH();
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                int y = Theme.px(4) + i * rh;
                if (i == hoverRow) {
                    g.setColor(Theme.hover());
                    g.fillRoundRect(Theme.px(3), y, w - Theme.px(8), rh,
                        Theme.px(5), Theme.px(5));
                }
                if (i == index) {
                    g.setColor(Theme.accent());
                    g.fillRect(Theme.px(3), y + Theme.px(4), Theme.pxf(2.4) < 2 ? 2
                        : (int) Theme.pxf(2.4), rh - Theme.px(8));
                }
                g.setFont(Theme.font(12, Font.BOLD));
                g.setColor(Theme.ink());
                var fm = g.getFontMetrics();
                int tx = Theme.px(11);
                int tw = w - tx - Theme.px(10);
                g.drawString(KpButton.ellipsize(it.title(), fm, tw), tx,
                    y + (it.note() == null || it.note().isBlank()
                        ? (rh + fm.getAscent() - fm.getDescent()) / 2 : Theme.px(15)));
                if (it.note() != null && !it.note().isBlank()) {
                    g.setFont(Theme.font(10.5, Font.PLAIN));
                    g.setColor(Theme.ink3());
                    var fm2 = g.getFontMetrics();
                    g.drawString(KpButton.ellipsize(it.note(), fm2, tw), tx, y + Theme.px(29));
                }
            }
            g.dispose();
        }
    }

    // ==================== сама кнопка ====================

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int top = 0;
        if (caption != null) {
            g.setFont(Theme.caption());
            g.setColor(Theme.ink3());
            g.drawString(caption.toUpperCase(java.util.Locale.ROOT), 0,
                g.getFontMetrics().getAscent());
            top = Theme.px(13);
        }
        Rectangle box = new Rectangle(0, top, w - 1, h - top - 1);
        g.setColor(hover || popup != null ? Theme.hover() : Theme.tile());
        g.fillRoundRect(box.x, box.y, box.width, box.height, Theme.px(6), Theme.px(6));
        g.setColor(popup != null ? Theme.accent() : Theme.border());
        g.setStroke(new BasicStroke(popup != null ? Theme.pxf(1.6) : 1f));
        g.drawRoundRect(box.x, box.y, box.width, box.height, Theme.px(6), Theme.px(6));

        Item it = selected();
        g.setFont(Theme.font(12, Font.BOLD));
        g.setColor(Theme.ink());
        var fm = g.getFontMetrics();
        int arrow = Theme.px(16);
        String text = it == null ? "—" : it.title();
        g.drawString(KpButton.ellipsize(text, fm, box.width - Theme.px(16) - arrow),
            Theme.px(8), box.y + (box.height + fm.getAscent() - fm.getDescent()) / 2);

        // Уголок «раскроется список» — не буква «v», а нарисованная галочка.
        int ax = box.x + box.width - arrow;
        int ay = box.y + box.height / 2;
        int d = Theme.px(4);
        g.setColor(Theme.ink3());
        g.setStroke(new BasicStroke(Theme.pxf(1.6), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[]{ax - d, ax, ax + d},
            new int[]{ay - d / 2, ay + d / 2, ay - d / 2}, 3);
        g.dispose();
    }
}
