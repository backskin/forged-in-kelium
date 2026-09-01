package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Map;

import kelium.gui.replay2.Theme;

/**
 * ЛИЦО КАРТЫ ПРИКАЗА — рисуется из ДАННЫХ карты (orders.yaml), как напечатано
 * (требование дизайнера 24.08: «карты приказов ни черта не содержат — где
 * верхний/нижний приказ, где действия с иконками?»):
 *
 * <ul>
 *   <li>ВЕРХНЯЯ секция: категория верхнего приказа + её ДВА действия с
 *       глифами ({@link ActionIcons});</li>
 *   <li>разделитель-переворот: нижний приказ открывается при СОВПАДЕНИИ
 *       (кто-то вскрыл ту же карту);</li>
 *   <li>НИЖНЯЯ секция: то же для нижнего приказа, приглушённо;</li>
 *   <li>плашка МАНЁВРА, если напечатана; ДЖОКЕР — сетка всех восьми действий.</li>
 * </ul>
 */
public final class OrderCardFace {

    private OrderCardFace() {
    }

    /** Разобранные данные карты приказа из контента. */
    public record Info(String id, String deck, String top, String bottom,
                        boolean joker, boolean maneuver) {

        @SuppressWarnings("unchecked")
        public static Info of(String id, Map<String, Object> data) {
            if (data == null) {
                return null;
            }
            return new Info(id,
                String.valueOf(data.getOrDefault("deck", "")),
                data.get("top") == null ? null : String.valueOf(data.get("top")),
                data.get("bottom") == null ? null : String.valueOf(data.get("bottom")),
                Boolean.TRUE.equals(data.get("joker")),
                Boolean.TRUE.equals(data.get("maneuver")));
        }
    }

    /** Нарисовать лицо карты в прямоугольнике (x,y,w,h). {@code dim} — приглушить. */
    public static void paint(Graphics2D g, Info info, int x, int y, int w, int h,
                              boolean dim) {
        Color deck = ActionIcons.deckColor(info.deck());
        Color ink = dim ? Theme.ink3() : Theme.ink();
        int arc = Math.max(6, w / 9);

        g.setColor(dim ? Theme.tile() : Theme.paper());
        g.fillRoundRect(x, y, w, h, arc, arc);
        g.setColor(deck);
        g.fillRoundRect(x, y, w, Math.max(5, h / 14), arc, arc);
        g.fillRect(x, y + Math.max(3, h / 28), w, Math.max(2, h / 28));
        g.setColor(dim ? Theme.border() : deck);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(x, y, w, h, arc, arc);

        int pad = Math.max(4, w / 12);
        if (info.joker()) {
            paintJoker(g, info, x, y, w, h, pad, ink);
            return;
        }

        int half = (h - h / 14) / 2;
        int topY = y + h / 14 + pad / 2;
        paintSection(g, info.top(), x + pad, topY, w - 2 * pad,
            half - pad, ink, false);

        // разделитель-переворот
        int midY = y + h / 14 + half;
        g.setColor(dim ? Theme.border() : Theme.divider());
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[]{4f, 3f}, 0f));
        g.drawLine(x + pad, midY, x + w - pad, midY);
        // значок переворота: две дуговые стрелки
        int fr = Math.max(5, w / 12);
        g.setStroke(new BasicStroke(Math.max(1.4f, w / 60f), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.drawArc(x + w / 2 - fr, midY - fr / 2, fr, fr, 30, 220);
        g.drawArc(x + w / 2, midY - fr / 2, fr, fr, 210, 220);

        paintSection(g, info.bottom(), x + pad, midY + pad / 2 + 2,
            w - 2 * pad, half - pad, Theme.ink3(), true);

        if (info.maneuver()) {
            paintManeuverPlate(g, x, y, w, h, arc, dim);
        }
    }

    /** Секция приказа: имя категории + два действия с глифами. */
    private static void paintSection(Graphics2D g, String cat, int x, int y, int w, int h,
                                      Color ink, boolean bottomHalf) {
        if (cat == null) {
            return;
        }
        float nameSize = Math.max(8f, w / 8.5f);
        g.setFont(Theme.narrow(nameSize, Font.BOLD));
        g.setColor(ink);
        var fm = g.getFontMetrics();
        String name = ActionIcons.categoryRu(cat);
        g.drawString(KpButton.ellipsize(name, fm, w), x, y + fm.getAscent());

        List<String> actions = ActionIcons.CATEGORY_ACTIONS.getOrDefault(cat, List.of());
        double icon = Math.min(h * 0.34, w / 5.2);
        float labSize = Math.max(7f, w / 10.5f);
        int rowY = y + fm.getHeight() + (int) icon / 2 + 3;
        int step = (int) (h - fm.getHeight()) / Math.max(1, actions.size());
        for (String a : actions) {
            ActionIcons.paint(g, a, x + icon / 2, rowY, icon, ink);
            g.setFont(Theme.narrow(labSize, Font.PLAIN));
            g.setColor(ink);
            var lf = g.getFontMetrics();
            g.drawString(KpButton.ellipsize(
                    ActionBar.ACTIONS.getOrDefault(a, a), lf, (int) (w - icon - 6)),
                (int) (x + icon + 5), rowY + (lf.getAscent() - lf.getDescent()) / 2);
            rowY += Math.max((int) icon + 4, step);
        }
    }

    /** Джокер БЕЗОПАСНОСТЬ: сетка всех восьми действий. */
    private static void paintJoker(Graphics2D g, Info info, int x, int y, int w, int h,
                                    int pad, Color ink) {
        float nameSize = Math.max(8f, w / 8.5f);
        g.setFont(Theme.narrow(nameSize, Font.BOLD));
        g.setColor(ink);
        var fm = g.getFontMetrics();
        g.drawString(KpButton.ellipsize("БЕЗОПАСНОСТЬ", fm, w - 2 * pad),
            x + pad, y + h / 14 + fm.getAscent() + 2);
        g.setFont(Theme.narrow(Math.max(7f, w / 11f), Font.PLAIN));
        g.setColor(Theme.ink3());
        g.drawString(KpButton.ellipsize("любые 2 разных действия", g.getFontMetrics(),
            w - 2 * pad), x + pad, y + h / 14 + fm.getHeight() + g.getFontMetrics().getAscent());

        int gridTop = y + h / 14 + fm.getHeight() + g.getFontMetrics().getHeight() + pad / 2;
        double icon = Math.min((w - 2.0 * pad) / 4.6, (y + h - pad - gridTop) / 2.4);
        List<String> all = List.copyOf(ActionBar.ACTIONS.keySet());
        for (int i = 0; i < all.size(); i++) {
            double cx = x + pad + icon / 2 + (i % 4) * (w - 2.0 * pad - icon) / 3;
            double cy = gridTop + icon / 2 + (i / 4) * (icon + pad);
            ActionIcons.paint(g, all.get(i), cx, cy, icon, ink);
        }
    }

    /** Плашка манёвра — печатный уголок со стрелкой (спец-ход одним жетоном). */
    private static void paintManeuverPlate(Graphics2D g, int x, int y, int w, int h,
                                            int arc, boolean dim) {
        int pw = Math.max(14, w / 3);
        int ph = Math.max(9, h / 10);
        // ОТСТУП ОТ УГЛА СЧИТАЕТСЯ ОТ РАДИУСА СКРУГЛЕНИЯ САМОЙ КАРТЫ, а не
        // фиксированными 3px: у крупных карт (маленький кегль веера) arc растёт
        // быстрее плашки, и жёсткий отступ выпускал плашку за скруглённый угол
        // (снято на ревью «Командного пункта» — плашка торчала за рамку карты).
        int margin = Math.max(3, arc / 2);
        int px = x + w - pw - margin;
        int py = y + h - ph - margin;
        g.setColor(dim ? Theme.hover() : Theme.alpha(Theme.kelium(), 0.25));
        g.fillRoundRect(px, py, pw, ph, ph, ph);
        g.setColor(dim ? Theme.border() : Theme.kelium());
        g.drawRoundRect(px, py, pw, ph, ph, ph);
        ActionIcons.paint(g, "movement", px + pw / 2.0, py + ph / 2.0, ph * 0.72,
            dim ? Theme.ink3() : Theme.darken(Theme.kelium(), 0.2));
    }
}
