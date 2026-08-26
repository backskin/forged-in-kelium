package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * МИНИАТЮРА КАРТЫ В РУКЕ — рисованная карточка, а не строка текста.
 *
 * <p>Верхняя полоса — цвет типа (приказ/задание/арсенал), имя переносится на
 * две строки, в углу — короткий ярлык (прогресс задания «2/3», джокер и т.п.).
 * Состояния: обычная; ПОДНЯТАЯ (её сейчас можно сыграть — приподнята и обведена
 * акцентом, курсор-рука); ПРИГАШЕННАЯ (сыграть нельзя, пока идёт выбор карты).
 * Наведение сообщается наружу — окно показывает увеличенную карту.
 */
public final class CardTile extends JComponent {

    public enum Mode { NORMAL, RAISED, DIMMED }

    public final String cardId;
    private final String name;
    private final Color band;
    /** Лицо карты приказа (null — обычная миниатюра с названием). */
    private OrderCardFace.Info orderFace;
    private String tag;
    private Color tagColor = Theme.ink3();
    private Mode mode = Mode.NORMAL;
    private Runnable onClick;
    private boolean hover;

    public CardTile(String cardId, String name, Color band,
                     Consumer<CardTile> onHover, Runnable onHoverOff) {
        this.cardId = cardId;
        this.name = name == null ? cardId : name;
        this.band = band;
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
                if (onHover != null) {
                    onHover.accept(CardTile.this);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
                if (onHoverOff != null) {
                    onHoverOff.run();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (mode == Mode.RAISED && onClick != null) {
                    onClick.run();
                }
            }
        };
        addMouseListener(m);
    }

    public String cardName() {
        return name;
    }

    /** Сделать миниатюру НАСТОЯЩИМ лицом карты приказа. */
    public void orderFace(OrderCardFace.Info info) {
        this.orderFace = info;
        repaint();
    }

    public OrderCardFace.Info orderFaceInfo() {
        return orderFace;
    }

    public Color bandColor() {
        return band;
    }

    public void tag(String tag, Color color) {
        this.tag = tag;
        if (color != null) {
            this.tagColor = color;
        }
        repaint();
    }

    public String tagText() {
        return tag;
    }

    public void setMode(Mode m, Runnable click) {
        this.mode = m;
        this.onClick = click;
        setCursor(m == Mode.RAISED ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : Cursor.getDefaultCursor());
        repaint();
    }

    public Mode mode() {
        return mode;
    }

    /** Для прогонщиков/тестов — то же, что клик по поднятой карте. */
    public void click() {
        if (mode == Mode.RAISED && onClick != null) {
            onClick.run();
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int lift = mode == Mode.RAISED || (hover && mode != Mode.DIMMED) ? Theme.px(5) : 0;
        int top = Theme.px(6) - lift + Theme.px(2);
        int cardH = h - Theme.px(8);
        int arc = Theme.px(7);

        if (orderFace != null) {
            // Приказ рисуется НАСТОЯЩИМ лицом: верх/низ, действия с глифами.
            OrderCardFace.paint(g, orderFace, 0, top, w - 1, cardH - 1,
                mode == Mode.DIMMED);
            if (mode == Mode.RAISED || (hover && mode != Mode.DIMMED)) {
                g.setColor(Theme.accent());
                g.setStroke(new BasicStroke(Theme.pxf(1.8)));
                g.drawRoundRect(0, top, w - 1, cardH - 1, Theme.px(7), Theme.px(7));
            }
            g.dispose();
            return;
        }
        g.setColor(hover && mode != Mode.DIMMED ? Theme.hover() : Theme.tile());
        g.fillRoundRect(0, top, w - 1, cardH - 1, arc, arc);
        // цветная полоса типа
        g.setColor(mode == Mode.DIMMED ? Theme.alpha(band, 0.45) : band);
        g.fillRoundRect(0, top, w - 1, Theme.px(9), arc, arc);
        g.fillRect(0, top + Theme.px(5), w - 1, Theme.px(4));
        g.setColor(mode == Mode.RAISED ? Theme.accent() : Theme.border());
        g.setStroke(new BasicStroke(mode == Mode.RAISED ? Theme.pxf(1.8) : 1f));
        g.drawRoundRect(0, top, w - 1, cardH - 1, arc, arc);

        Color ink = mode == Mode.DIMMED ? Theme.ink3() : Theme.ink();
        g.setFont(Theme.font(9.5, Font.BOLD));
        g.setColor(ink);
        var fm = g.getFontMetrics();
        int textX = Theme.px(5);
        int textW = w - textX * 2;
        int y = top + Theme.px(12) + fm.getAscent();
        for (String line : wrap(name, fm, textW, 3)) {
            g.drawString(line, textX, y);
            y += fm.getHeight();
        }
        if (tag != null && !tag.isBlank()) {
            g.setFont(Theme.mono(9.5, Font.BOLD));
            g.setColor(mode == Mode.DIMMED ? Theme.ink3() : tagColor);
            var fm2 = g.getFontMetrics();
            g.drawString(tag, w - Theme.px(5) - fm2.stringWidth(tag),
                top + cardH - Theme.px(5));
        }
        g.dispose();
    }

    static List<String> wrap(String s, java.awt.FontMetrics fm, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split("\\s+")) {
            // Слово ДЛИННЕЕ строки само по себе («ИНФРАСТРУКТУРА») раньше
            // рисовалось как есть и обрезалось краем компонента без многоточия
            // (блокер приёмки агентом-игроком) — теперь честно сокращается.
            if (fm.stringWidth(word) > maxW) {
                word = KpButton.ellipsize(word, fm, maxW);
            }
            String probe = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(probe) <= maxW || line.isEmpty()) {
                line = new StringBuilder(probe);
            } else {
                out.add(line.toString());
                line = new StringBuilder(word);
                if (out.size() == maxLines - 1) {
                    break;
                }
            }
        }
        if (out.size() < maxLines && !line.isEmpty()) {
            out.add(KpButton.ellipsize(line.toString(), fm, maxW));
        }
        return out;
    }
}
