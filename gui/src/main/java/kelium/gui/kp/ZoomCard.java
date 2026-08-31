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
 * КАРТА ПОД КУРСОРОМ — РАЗБОРЧИВЫМ ТЕКСТОМ. Всплывает рядом с рукой, не под
 * курсором, чтобы не перекрывать карты. Живёт в POPUP-слое окна.
 *
 * <p>КАРТИНКИ КАРТЫ ЗДЕСЬ НЕТ. Сама карта уже нарисована в руке, и повторять её
 * во всплывашке — значит отдать всё место второму экземпляру того же рисунка и
 * ужать текст до нечитаемого (дизайнер 31.08.2026: «зачем в приказе popup
 * копировать карту, если можно укрупнить текст и убрать эту карту — просто
 * текстом подробно описать»). Всё окно отдано тексту, и текст крупный.
 *
 * <p>ПУСТОЙ ЭТА КАРТОЧКА БЫТЬ НЕ МОЖЕТ: не нашлось ни строчки — так и написано,
 * потому что молчащая белая карточка неотличима от поломки.
 */
public final class ZoomCard extends JComponent {

    private String name = "";
    private String typeLabel = "";
    private Color band = Theme.border();
    private String detail = "";
    private double progress = -1;
    private final Anim fade = new Anim();

    public void show(String name, String typeLabel, Color band, String detail, double progress) {
        this.name = name == null ? "" : name;
        this.typeLabel = typeLabel == null ? "" : typeLabel;
        this.band = band;
        this.detail = detail == null ? "" : detail;
        this.progress = progress;
        setVisible(true);
        fade.snap(0);
        fade.play(1, 120, v -> repaint(), null);
    }

    /** Приказ — тем же текстом, что и остальные карты. */
    public void showOrder(OrderCardFace.Info info, String name, String description) {
        show(name, "Приказ", ActionIcons.deckColor(info == null ? null : info.deck()),
            description, -1);
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
        int pad = Theme.px(14);

        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(Theme.px(4), Theme.px(6), w, h, arc, arc);
        g.setColor(Theme.tile());
        g.fillRoundRect(0, 0, w, h, arc, arc);
        g.setColor(band);
        g.fillRoundRect(0, 0, w, Theme.px(18), arc, arc);
        g.fillRect(0, Theme.px(10), w, Theme.px(8));

        int y = Theme.px(32);
        g.setFont(Theme.font(10.5, Font.BOLD));
        g.setColor(Theme.ink3());
        g.drawString(typeLabel.toUpperCase(java.util.Locale.ROOT), pad, y);
        y += Theme.px(8);

        g.setFont(Theme.font(17, Font.BOLD));
        g.setColor(Theme.ink());
        FontMetrics fm = g.getFontMetrics();
        for (String line : CardTile.wrap(name, fm, w - pad * 2, 3)) {
            y += fm.getHeight();
            g.drawString(line, pad, y);
        }

        if (progress >= 0) {
            y += Theme.px(12);
            int barW = w - pad * 2;
            int barH = Theme.px(9);
            g.setColor(Theme.bg());
            g.fillRoundRect(pad, y, barW, barH, barH, barH);
            g.setColor(Theme.kelium());
            g.fillRoundRect(pad, y, (int) Math.round(barW * Math.min(1, progress)),
                barH, barH, barH);
            g.setColor(Theme.border());
            g.drawRoundRect(pad, y, barW, barH, barH, barH);
            y += barH;
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            y += g.getFontMetrics().getHeight();
            g.drawString("выполнено " + Math.round(progress * 100) + "%", pad, y);
        }

        y += Theme.px(10);
        g.setColor(Theme.divider());
        g.fillRect(pad, y, w - pad * 2, 1);
        y += Theme.px(4);

        paintDetail(g, pad, y, w - pad * 2, h - Theme.px(8));

        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(Theme.pxf(1.2)));
        g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
        g.dispose();
    }

    /** Начинается ли подпись абзаца словом из ЗАГЛАВНЫХ («НАГРАДА», «УТИЛЬ»). */
    private static boolean заглавноеСлово(String подпись) {
        String обрезанная = подпись.trim();
        int пробел = обрезанная.indexOf(' ');
        String первое = пробел < 0 ? обрезанная : обрезанная.substring(0, пробел);
        return первое.length() >= 3
            && первое.equals(первое.toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * ТЕКСТ КАРТЫ АБЗАЦАМИ. Куски приходят разделёнными переводом строки —
     * условие, печатный текст, награда, усиленная награда, утиль, — и слипаться
     * им нельзя: иначе «НАГРАДА» читается как продолжение условия. Подпись
     * абзаца («НАГРАДА:», «УТИЛЬ (сжечь):») выносится отдельной строкой.
     */
    private void paintDetail(Graphics2D g, int x, int y, int w, int bottom) {
        if (detail.isBlank()) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("текста карты нет в наборе партии",
                x, y + g.getFontMetrics().getHeight());
            return;
        }
        for (String кусок : detail.split(String.valueOf((char) 10))) {
            if (кусок.isBlank()) {
                continue;
            }
            String абзац = кусок;
            int двоеточие = абзац.indexOf(": ");
            // ЗАГОЛОВОК УЗНАЁТСЯ ПО ПЕРВОМУ СЛОВУ. Сравнивать с ЗАГЛАВНЫМИ всю
            // подпись нельзя: «УТИЛЬ (сжечь)» заглавным целиком не является и
            // заголовком не считался.
            boolean заголовок = двоеточие > 0 && двоеточие < 22
                && заглавноеСлово(абзац.substring(0, двоеточие));
            if (заголовок) {
                g.setFont(Theme.font(10, Font.BOLD));
                g.setColor(Theme.accent());
                FontMetrics hf = g.getFontMetrics();
                y += hf.getHeight() + Theme.px(2);
                if (y > bottom) {
                    return;
                }
                g.drawString(абзац.substring(0, двоеточие), x, y);
                абзац = абзац.substring(двоеточие + 2);
            }
            g.setFont(Theme.font(12.5, Font.PLAIN));
            g.setColor(заголовок ? Theme.ink() : Theme.ink2());
            FontMetrics fm = g.getFontMetrics();
            List<String> строки = CardTile.wrap(абзац, fm, w, 14);
            for (String line : строки) {
                y += fm.getHeight();
                if (y > bottom) {
                    return;
                }
                g.drawString(line, x, y);
            }
            y += Theme.px(8);
        }
    }
}
