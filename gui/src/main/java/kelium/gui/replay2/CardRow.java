package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Dimension;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import kelium.gui.CardArt;

/**
 * РЯДЫ КАРТ ПЕЧАТНЫМИ ЛИЦАМИ — для ящика-инспектора игрока.
 *
 * <p>Каждая группа («задания на руке», «арсенал установлен», «трофеи») — своя
 * подпись и ряд настоящих карт. Все карты ряда одной высоты, ширина — по
 * картинке: стоячее задание узкое, лежачий арсенал широкий, трофейный жетон
 * квадратный. Не влезло в ширину — перенос на следующую строку. Навёл —
 * карта крупно ({@link CardZoom}).
 *
 * <p>Лица нет — на месте карты рамка в пропорции рубашки её колоды с
 * названием: место за столом есть, печати пока нет.
 */
public final class CardRow extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Одна карта: печать (или null), имя для замены и подсказки. */
    public record Item(BufferedImage face, String name, double fallbackAspect) {
    }

    /** Группа карт под одной подписью; пустая группа пишет {@code empty}. */
    public record Group(String title, List<Item> items, String empty) {
    }

    private List<Group> groups = List.of();
    private final Map<Rectangle, Item> spots = new LinkedHashMap<>();
    private Rectangle hot;

    /** Высота карты в ряду, точки вёрстки. */
    private static final int CARD_H = 96;

    public CardRow() {
        setOpaque(false);
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Rectangle r = spotAt(e.getX(), e.getY());
                if (r == hot) {
                    return;
                }
                hot = r;
                Item it = r == null ? null : spots.get(r);
                if (it != null && it.face() != null) {
                    CardZoom.show(CardRow.this, it.face(), r);
                } else {
                    CardZoom.hide();
                }
                setCursor(it != null && it.face() != null
                    ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                    : java.awt.Cursor.getDefaultCursor());
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hot = null;
                CardZoom.hide();
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        hot = null;
        CardZoom.hide();
        revalidate();
        repaint();
    }

    private Rectangle spotAt(int x, int y) {
        for (Rectangle r : spots.keySet()) {
            if (r.contains(x, y)) {
                return r;
            }
        }
        return null;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Rectangle r = spotAt(e.getX(), e.getY());
        Item it = r == null ? null : spots.get(r);
        return it == null || it.name() == null ? null : Ui2.tip("«" + it.name() + "»", 260);
    }

    @Override
    public Dimension getPreferredSize() {
        int w = getWidth() > 0 ? getWidth() : Theme.px(400);
        return new Dimension(Theme.px(200), layout(null, w));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int h = layout(g, getWidth());
        g.dispose();
        if (h != getPreferredSize().height) {
            revalidate();
        }
    }

    private static double aspect(Item it) {
        return it.face() != null ? CardArt.aspect(it.face())
            : it.fallbackAspect() > 0 ? it.fallbackAspect() : 1.0;
    }

    /**
     * Разложить и (если {@code g} не null) нарисовать. Возвращает занятую
     * высоту — одно правило и для размера компонента, и для рисования.
     */
    private int layout(Graphics2D g, int width) {
        if (g != null) {
            spots.clear();
        }
        int cardH = Theme.px(CARD_H);
        int gap = Theme.px(Theme.GAP_TILE);
        int capH = Theme.px(16);
        int y = 0;
        Font cap = Theme.caption();
        for (Group gr : groups) {
            if (g != null) {
                g.setFont(cap);
                g.setColor(Theme.ink3());
                String t = gr.title() + (gr.items().isEmpty() ? "" : " · " + gr.items().size());
                g.drawString(t, 0, y + Theme.px(11));
            }
            y += capH;
            if (gr.items().isEmpty()) {
                if (g != null) {
                    g.setFont(Theme.italic());
                    g.setColor(Theme.ink3());
                    g.drawString(gr.empty() == null ? "нет" : gr.empty(), 0, y + Theme.px(12));
                }
                y += Theme.px(20) + gap;
                continue;
            }
            int x = 0;
            for (Item it : gr.items()) {
                int w = (int) Math.round(cardH * aspect(it));
                if (x > 0 && x + w > width) {
                    x = 0;
                    y += cardH + gap;
                }
                if (g != null) {
                    Rectangle r = new Rectangle(x, y, w, cardH);
                    paintItem(g, it, r, r.equals(hot));
                    spots.put(r, it);
                }
                x += w + gap;
            }
            y += cardH + Theme.px(Theme.GAP_BLOCK);
        }
        return Math.max(y, Theme.px(20));
    }

    private static void paintItem(Graphics2D g, Item it, Rectangle r, boolean over) {
        double rad = Theme.px(Theme.R_TILE);
        if (it.face() != null) {
            CardArt.draw(g, it.face(), r, rad);
        } else {
            RoundRectangle2D box = new RoundRectangle2D.Double(r.x + 0.5, r.y + 0.5,
                r.width - 1, r.height - 1, rad * 2, rad * 2);
            g.setColor(Theme.tile());
            g.fill(box);
            g.setColor(Theme.border());
            g.setStroke(new BasicStroke(1f));
            g.draw(box);
            g.setFont(Theme.font(9, Font.PLAIN));
            g.setColor(Theme.ink2());
            wrap(g, it.name() == null ? "?" : it.name(), r.x + Theme.px(4),
                r.y + Theme.px(12), r.width - Theme.px(8), r.y + r.height - Theme.px(4));
        }
        if (over) {
            g.setColor(Theme.accent());
            g.setStroke(new BasicStroke(Theme.pxf(2)));
            g.draw(new RoundRectangle2D.Double(r.x + 1, r.y + 1, r.width - 2, r.height - 2,
                rad * 2, rad * 2));
        }
    }

    /** Имя в несколько строк по ширине; не влезло по высоте — многоточие. */
    private static void wrap(Graphics2D g, String text, int x, int y, int w, int bottom) {
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String t = cur.isEmpty() ? word : cur + " " + word;
            if (fm.stringWidth(t) > w && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(t);
            }
        }
        if (!cur.isEmpty()) {
            lines.add(cur.toString());
        }
        int line = fm.getHeight();
        for (int i = 0; i < lines.size(); i++) {
            boolean last = y + line * (i + 1) > bottom;
            String s = lines.get(i);
            if (last && i + 1 < lines.size()) {
                s = s + "…";
            }
            g.drawString(s, x, y + line * i);
            if (last) {
                break;
            }
        }
    }
}
