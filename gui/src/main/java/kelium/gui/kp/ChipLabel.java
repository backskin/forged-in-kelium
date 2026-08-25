package kelium.gui.kp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

import kelium.gui.replay2.MarkIcons;
import kelium.gui.replay2.Theme;

/**
 * ФИШКА-СЧЁТЧИК полосы хода: рисованный значок смысла ({@link MarkIcons}) +
 * значение табличным шрифтом. «Значение / предел» — по правилу скилла: показатель
 * без потолка бесполезен.
 */
public final class ChipLabel extends JComponent {

    private final String icon;
    private final Color iconColor;
    private String value = "";
    private String cap = "";

    public ChipLabel(String icon, Color iconColor) {
        this.icon = icon;
        this.iconColor = iconColor;
        setOpaque(false);
    }

    public void set(String value, String cap) {
        this.value = value;
        this.cap = cap == null ? "" : cap;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(96), Theme.px(28));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g.setColor(Theme.tile());
        g.fillRoundRect(0, 0, w - 1, h - 1, Theme.px(8), Theme.px(8));
        g.setColor(Theme.border());
        g.drawRoundRect(0, 0, w - 1, h - 1, Theme.px(8), Theme.px(8));
        double s = Theme.px(14);
        MarkIcons.paint(g, icon, Theme.px(7) + s / 2, h / 2.0, s, iconColor);
        g.setFont(Theme.mono(12.5, Font.BOLD));
        g.setColor(Theme.ink());
        var fm = g.getFontMetrics();
        int x = Theme.px(10) + (int) s + Theme.px(4);
        int y = (h + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(value, x, y);
        if (!cap.isEmpty()) {
            int vw = fm.stringWidth(value);
            g.setFont(Theme.mono(10.5, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString("/" + cap, x + vw + Theme.px(1), y);
        }
        g.dispose();
    }
}
