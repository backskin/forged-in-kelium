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
    private final String caption;
    private String value = "";
    private String cap = "";

    public ChipLabel(String icon, Color iconColor, String caption) {
        this.icon = icon;
        this.iconColor = iconColor;
        this.caption = caption;
        setOpaque(false);
    }

    public void set(String value, String cap) {
        String было = this.value + "/" + this.cap;
        this.value = value;
        this.cap = cap == null ? "" : cap;
        if (!было.equals(this.value + "/" + this.cap)) {
            revalidate();                      // ширина считается по тексту
        }
        repaint();
    }

    /**
     * Ширина — ПО ИЗМЕРЕННОМУ ТЕКСТУ, а не по прикидке. Прежняя формула
     * «96 плюс шесть пикселей за букву подписи» давала фишке «трофеи» 144
     * пикселя при нужных сотне: пять фишек съедали пол-панели, и картам
     * приказов внизу не оставалось места.
     */
    @Override
    public Dimension getPreferredSize() {
        var fmV = getFontMetrics(Theme.mono(12.5, Font.BOLD));
        var fmC = getFontMetrics(Theme.mono(10.5, Font.PLAIN));
        var fmL = getFontMetrics(Theme.font(9.5, Font.PLAIN));
        int w = Theme.px(10) + Theme.px(14) + Theme.px(4)
            + fmV.stringWidth(value.isEmpty() ? "00" : value)
            + (cap.isEmpty() ? 0 : Theme.px(1) + fmC.stringWidth("/" + cap))
            + Theme.px(5) + fmL.stringWidth(caption) + Theme.px(8);
        return new Dimension(w, Theme.px(26));
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
        int vx = x + fm.stringWidth(value);
        if (!cap.isEmpty()) {
            g.setFont(Theme.mono(10.5, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString("/" + cap, vx + Theme.px(1), y);
            vx += Theme.px(1) + g.getFontMetrics().stringWidth("/" + cap);
        }
        // ПОДПИСЬ — не подсказка: значение читают каждый взгляд (приёмка
        // агентом-игроком: «пять неподписанных пиктограмм — экзамен»).
        g.setFont(Theme.font(9.5, Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString(caption, vx + Theme.px(5), y);
        g.dispose();
    }
}
