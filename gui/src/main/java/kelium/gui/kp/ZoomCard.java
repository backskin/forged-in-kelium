package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * УВЕЛИЧЕННАЯ КАРТА ПРИ НАВЕДЕНИИ (приём Hearthstone): миниатюра в руке мелкая,
 * полный текст всплывает рядом — НЕ под курсором, чтобы не перекрывать руку.
 * Живёт в POPUP-слое окна.
 *
 * <p>РАМКА ОДНА. Прежде увеличенный приказ рисовался картой ВНУТРИ карты:
 * подложка со своей цветной шапкой и обводкой, а в ней — настоящее лицо карты
 * со своей шапкой и обводкой (дизайнер 31.08.2026: «че за глупая шапка дважды —
 * карта, а внутри как будто ещё карта»). Теперь у приказа лицо карты и ЕСТЬ
 * подложка, а печатное описание лежит под ним на общем листе.
 */
public final class ZoomCard extends JComponent {

    private String name = "";
    private String typeLabel = "";
    private Color band = Theme.border();
    private String detail = "";
    private double progress = -1;
    private OrderCardFace.Info orderInfo;
    private final Anim fade = new Anim();

    public void show(String name, String typeLabel, Color band, String detail, double progress) {
        this.orderInfo = null;
        this.name = name;
        this.typeLabel = typeLabel;
        this.band = band;
        this.detail = detail == null ? "" : detail;
        this.progress = progress;
        setVisible(true);
        fade.snap(0);
        fade.play(1, 120, v -> repaint(), null);
    }

    /** Увеличенный ПРИКАЗ: настоящее лицо карты + печатное описание. */
    public void showOrder(OrderCardFace.Info info, String name, String description) {
        this.name = name;
        this.typeLabel = "Приказ";
        this.band = ActionIcons.deckColor(info == null ? null : info.deck());
        this.detail = description == null ? "" : description;
        this.progress = -1;
        // ЛИЦО НАЗНАЧАЕТСЯ ДО ПОКАЗА. Раньше showOrder звал show(), который
        // обнулял лицо, и только потом ставил его обратно — первый кадр
        // появления успевал нарисоваться обычной карточкой, и поверх листа
        // оставалась её цветная шапка.
        this.orderInfo = info;
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
        int w = getWidth() - Theme.px(6);
        int h = getHeight() - Theme.px(8);
        int arc = Theme.px(12);
        int pad = Theme.px(12);

        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(Theme.px(4), Theme.px(6), w, h, arc, arc);
        g.setColor(Theme.tile());
        g.fillRoundRect(0, 0, w, h, arc, arc);

        int y;
        if (orderInfo != null) {
            // ЛИЦО КАРТЫ И ЕСТЬ ПОДЛОЖКА, и лежит оно ВРОВЕНЬ с краем: у карты
            // своя шапка и своя обводка, и всякая рамка вокруг читается второй
            // картой («карта, а внутри как будто ещё карта»). Видимый край
            // ровно один — край самой карты.
            int faceH = Math.min((int) (w * 1.30), (int) (h * 0.66));
            OrderCardFace.paint(g, orderInfo, 0, 0, w, faceH, false);
            y = faceH + Theme.px(8);
        } else {
            g.setColor(band);
            g.fillRoundRect(0, 0, w, Theme.px(16), arc, arc);
            g.fillRect(0, Theme.px(9), w, Theme.px(7));
            y = Theme.px(24);
            g.setFont(Theme.font(10, Font.BOLD));
            g.setColor(Theme.ink3());
            g.drawString(typeLabel.toUpperCase(java.util.Locale.ROOT), pad, y + Theme.px(4));
            y += Theme.px(14);
            g.setFont(Theme.font(14.5, Font.BOLD));
            g.setColor(Theme.ink());
            FontMetrics fm = g.getFontMetrics();
            for (String line : CardTile.wrap(name, fm, w - pad * 2, 3)) {
                y += fm.getHeight();
                g.drawString(line, pad, y);
            }
            if (progress >= 0) {
                y += Theme.px(14);
                int barW = w - pad * 2;
                int barH = Theme.px(8);
                g.setColor(Theme.bg());
                g.fillRoundRect(pad, y, barW, barH, barH, barH);
                g.setColor(Theme.kelium());
                g.fillRoundRect(pad, y, (int) Math.round(barW * Math.min(1, progress)),
                    barH, barH, barH);
                g.setColor(Theme.border());
                g.drawRoundRect(pad, y, barW, barH, barH, barH);
                y += barH + Theme.px(6);
            }
            y += Theme.px(8);
        }

        paintDetail(g, pad, y, w - pad * 2, h - pad);

        if (orderInfo == null) {
            g.setColor(Theme.border());
            g.setStroke(new BasicStroke(Theme.pxf(1.2)));
            g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
        }
        g.dispose();
    }

    /**
     * ТЕКСТ КАРТЫ АБЗАЦАМИ. Куски приходят разделёнными пустой строкой —
     * условие, награда, усиленная награда, утиль, — и слипаться им нельзя:
     * иначе «НАГРАДА» читается как продолжение условия. Подписи-заголовки
     * («НАГРАДА:», «УТИЛЬ (сжечь):») выделяются отдельной строкой.
     */
    private void paintDetail(Graphics2D g, int x, int y, int w, int bottom) {
        if (detail.isBlank()) {
            return;
        }
        for (String кусок : detail.split(String.valueOf((char) 10))) {
            if (кусок.isBlank()) {
                continue;
            }
            String абзац = кусок;
            int двоеточие = абзац.indexOf(": ");
            boolean заголовок = двоеточие > 0 && двоеточие < 20
                && абзац.substring(0, двоеточие).equals(
                    абзац.substring(0, двоеточие).toUpperCase(java.util.Locale.ROOT));
            if (заголовок) {
                g.setFont(Theme.font(9.5, Font.BOLD));
                g.setColor(Theme.ink3());
                FontMetrics hf = g.getFontMetrics();
                y += hf.getHeight();
                if (y > bottom) {
                    return;
                }
                g.drawString(абзац.substring(0, двоеточие), x, y);
                абзац = абзац.substring(двоеточие + 2);
            }
            g.setFont(Theme.font(11, Font.PLAIN));
            g.setColor(заголовок ? Theme.ink() : Theme.ink2());
            FontMetrics fm = g.getFontMetrics();
            List<String> строки = CardTile.wrap(абзац, fm, w, 8);
            for (String line : строки) {
                y += fm.getHeight();
                if (y > bottom) {
                    return;
                }
                g.drawString(line, x, y);
            }
            y += Theme.px(6);
        }
    }
}
