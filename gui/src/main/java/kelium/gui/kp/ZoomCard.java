package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * УВЕЛИЧЕННАЯ КАРТА ПРИ НАВЕДЕНИИ (приём Hearthstone, концепт §2 «всё в один
 * клик или наведение»): миниатюра в руке мелкая, полный текст всплывает рядом
 * — НЕ под курсором, чтобы не перекрывать руку. Живёт в POPUP-слое окна.
 */
public final class ZoomCard extends JComponent {

    private String name = "";
    private String typeLabel = "";
    private Color band = Theme.border();
    private String detail = "";
    private double progress = -1;
    private final Anim fade = new Anim();

    public void show(String name, String typeLabel, Color band, String detail, double progress) {
        this.name = name;
        this.typeLabel = typeLabel;
        this.band = band;
        this.detail = detail == null ? "" : detail;
        this.progress = progress;
        setVisible(true);
        fade.snap(0);
        fade.play(1, 120, v -> repaint(), null);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setComposite(java.awt.AlphaComposite.SrcOver.derive((float) fade.value()));
        int w = getWidth();
        int h = getHeight();
        int arc = Theme.px(12);
        // тень, чтобы карта «лежала над» экраном
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(Theme.px(4), Theme.px(6), w - Theme.px(8), h - Theme.px(8), arc, arc);
        g.setColor(Theme.tile());
        g.fillRoundRect(0, 0, w - Theme.px(6), h - Theme.px(8), arc, arc);
        g.setColor(band);
        g.fillRoundRect(0, 0, w - Theme.px(6), Theme.px(16), arc, arc);
        g.fillRect(0, Theme.px(9), w - Theme.px(6), Theme.px(7));
        g.setColor(Theme.accent());
        g.setStroke(new BasicStroke(Theme.pxf(1.4)));
        g.drawRoundRect(0, 0, w - Theme.px(6) - 1, h - Theme.px(8) - 1, arc, arc);

        int pad = Theme.px(12);
        int y = Theme.px(24);
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.ink3());
        g.drawString(typeLabel.toUpperCase(java.util.Locale.ROOT), pad, y + Theme.px(4));
        y += Theme.px(14);
        g.setFont(Theme.font(14.5, Font.BOLD));
        g.setColor(Theme.ink());
        var fm = g.getFontMetrics();
        for (String line : CardTile.wrap(name, fm, w - Theme.px(6) - pad * 2, 3)) {
            y += fm.getHeight();
            g.drawString(line, pad, y);
        }
        if (progress >= 0) {
            y += Theme.px(16);
            int barW = w - Theme.px(6) - pad * 2;
            int barH = Theme.px(8);
            g.setColor(Theme.bg());
            g.fillRoundRect(pad, y, barW, barH, barH, barH);
            g.setColor(Theme.kelium());
            g.fillRoundRect(pad, y, (int) Math.round(barW * Math.min(1, progress)),
                barH, barH, barH);
            g.setColor(Theme.border());
            g.drawRoundRect(pad, y, barW, barH, barH, barH);
            y += barH + Theme.px(4);
        }
        if (!detail.isBlank()) {
            y += Theme.px(12);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.setColor(Theme.ink2());
            var fm2 = g.getFontMetrics();
            for (String line : CardTile.wrap(detail, fm2, w - Theme.px(6) - pad * 2, 7)) {
                g.drawString(line, pad, y);
                y += fm2.getHeight();
            }
        }
        g.dispose();
    }
}
