package kelium.gui.kp;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * КОРЕШОК ЯЩИКА — вертикальная рисованная вкладка у левого края («Наука и
 * рынок», «Планшет», «Журнал»). Постоянные органы управления окна: всегда на
 * месте, открывают панель поверх поля. Выбранный корешок помечен полосой
 * акцента и светлым фоном.
 */
public final class KpTab extends JComponent {

    private final String label;
    private boolean selected;
    private boolean hover;
    private final Runnable onClick;

    public KpTab(String label, Runnable onClick) {
        this.label = label;
        this.onClick = onClick;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
                onClick.run();
            }
        });
    }

    public void setSelected(boolean on) {
        selected = on;
        repaint();
    }

    /** Для прогонщиков/тестов. */
    public void click() {
        onClick.run();
    }

    public String label() {
        return label;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int arc = Theme.px(8);
        g.setColor(selected ? Theme.hover() : hover ? Theme.hover() : Theme.tile());
        g.fillRoundRect(Theme.px(3), 0, w - Theme.px(4), h - 1, arc, arc);
        g.setColor(selected ? Theme.accent() : Theme.border());
        g.drawRoundRect(Theme.px(3), 0, w - Theme.px(4), h - 1, arc, arc);
        if (selected) {
            g.setColor(Theme.accent());
            g.fillRoundRect(0, Theme.px(6), Theme.px(3), h - Theme.px(12), 2, 2);
        }
        // Текст повёрнут на 90° против часовой вокруг ЦЕНТРА корешка: после
        // поворота точка центра неподвижна, поэтому строка просто центрируется
        // относительно неё в повернутых координатах.
        g.rotate(-Math.PI / 2, w / 2.0, h / 2.0);
        g.setFont(Theme.font(11.5, Font.BOLD));
        g.setColor(selected ? Theme.ink() : hover ? Theme.ink() : Theme.ink2());
        var fm = g.getFontMetrics();
        g.drawString(label, w / 2 - fm.stringWidth(label) / 2,
            h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }
}
