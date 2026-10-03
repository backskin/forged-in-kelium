package kelium.gui.cardshop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * РАСКЛАДКА КАРТ ПО ПЕЧАТИ ДИЗАЙНЕРА — та же, что у рисовальщика
 * {@code tools/gen_cards_from_blanks.py}: где что лежит, каким шрифтом и цветом,
 * снято вычитанием пустого шаблона из печатной карты.
 *
 * <p>Карта — словарь полей ({@link CardSpec}); в текстах иконки пишутся в
 * фигурных скобках ({@code {25}} — по номеру из «экспорт-иконки»,
 * {@code {военное здание}} — по имени файла), жирное — {@code **так**}.
 */
public final class CardRender {

    private CardRender() {
    }

    /** Карта не рисуется: нет шаблона, текст не влез… — причина словами. */
    public static final class Problem extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Problem(String m) {
            super(m);
        }
    }

    public static BufferedImage render(CardAssets a, CardSpec c) {
        return switch (c.type().layout) {
            case OBJECTIVE -> objective(a, c);
            case ARSENAL -> arsenal(a, c);
            case MARKET -> market(a, c);
            case CONTAINER -> container(a, c);
            case HEX -> spawnHex(a, c);
            case ORDER -> order(a, c);
        };
    }

    /** Пустой шаблон карты: фон рисунка и подложка; alt — «с дополнительно» / ▶. */
    static BufferedImage template(CardAssets a, CardSpec c, boolean alt) {
        CardSpec.Type t = c.type();
        if (t.wholeFile() != null) {
            BufferedImage im = a.template(t.wholeFile());
            if (im == null) {
                throw new Problem("Нет шаблона «" + t.wholeFile() + "» в папке «" + a.templates + "»");
            }
            return im;
        }
        // ЛЮБОЙ ФОН (дизайнер 03.10.2026: «чтобы все были доступны фоны»):
        // выбранный файл фона перекрывает рисунок по номеру
        String bg = c.text("фон").isBlank() ? t.bgFile(c.integer("рисунок", 1)) : c.text("фон");
        String over = t.overFile(alt);
        BufferedImage im = a.layered(bg, over);
        if (im == null || a.template(over) == null) {
            throw new Problem("Нет шаблона: «" + bg + "» + «" + over + "» в папке «" + a.templates + "»");
        }
        return im;
    }

    /** Цвета типа: обычные задания — красные, арсенал — сине-зелёный, начальные — зелёные, супер — фиолетовые. */
    record Theme(Color text, Color stroke, Color number) {
    }

    static Theme theme(CardSpec.Type t) {
        return switch (t) {
            case OBJECTIVE_START -> new Theme(new Color(0, 92, 48), new Color(28, 120, 70),
                new Color(28, 120, 70));
            case ARSENAL_START -> new Theme(new Color(20, 80, 40), new Color(60, 130, 80),
                new Color(60, 130, 80));
            case ARSENAL_SUPER -> new Theme(new Color(62, 34, 104), new Color(112, 82, 164),
                new Color(112, 82, 164));
            case ARSENAL -> new Theme(A_TEXT, A_NAME, A_NUMBER);
            default -> new Theme(RED_TEXT, RED_STROKE, NUMBER);
        };
    }

    // ======================================================================
    //  ЗАДАНИЕ (661×1028)
    // ======================================================================
    static final Color RED_TEXT = new Color(173, 0, 0);
    static final Color RED_STROKE = new Color(199, 52, 36);
    static final Color SLATE = new Color(79, 93, 130);
    static final Color SLATE_TITLE = new Color(85, 99, 135);
    static final Color NUMBER = new Color(208, 58, 37);
    static final String F_TITLE = "TekturNarrow-SemiBold.ttf";
    static final String F_TOP = "TekturNarrow-SemiBold.ttf";
    static final String F_TOP_B = "TekturNarrow-Bold.ttf";
    static final String F_COND = "Tektur-Medium.ttf";

    static BufferedImage objective(CardAssets a, CardSpec c) {
        String dop = c.text("дополнительно");
        boolean hasDop = !dop.isBlank();
        boolean start = c.type() == CardSpec.Type.OBJECTIVE_START;
        if (start) {
            hasDop = false;         // у начального задания полосы «дополнительно» нет
        }
        Theme th = theme(c.type());
        CardCanvas k = new CardCanvas(a, template(a, c, hasDop));
        objectiveTop(k, c);
        // ИМЯ КАРТЫ — своё; длинное уменьшается, чтобы влезть в полосу
        String name = c.text("имя");
        double px = 52;
        Font f = k.font(F_TITLE, px);
        while (k.len(name, f) > 470 * CardCanvas.K && px > 34) {
            px -= 1;
            f = k.font(F_TITLE, px);
        }
        k.text(name, f, 615 * 2, 284 * 2, 'r', CardCanvas.WHITE, th.stroke(), 4 * 2);
        Figure fig = Figure.of(c.fields.get("фигура"));
        String cond = c.text("условие");
        if (cond.isBlank() && !fig.isEmpty()) {
            cond = "Займи своими жетонами закрашенные секторы";
        }
        int condLines = plates(k, cond, 344, 53, 35, 627, fig.isEmpty() ? 4 : 2, th.text());
        int shift = start ? 166 : hasDop ? 0 : 127;
        if (!fig.isEmpty()) {
            // ФИГУРА — между условием и наградой, по центру белого поля
            double top = 344 + condLines * 53 - 14;
            double bottom = 614 + shift - 66;
            fig.draw(k.g, 380 * 2, (top + bottom) / 2 * 2, 440 * 2, (bottom - top) * 2,
                th.stroke(), new Color(50, 40, 40));
        }
        // ОСНОВНАЯ НАГРАДА — блок «вид / выбор / позиции» (см. Reward)
        Reward.of(c.fields.get("награда"), true).draw(k, 500, 614 + shift, 270, 110);
        if (hasDop) {
            plates(k, dop, 767, 51, 33.4, 626, 2, th.text());
        }
        // УСИЛЕННАЯ (дополнительная) НАГРАДА — такой же блок, в нижней плашке
        if (!start) {
            Reward.of(c.fields.get("доп_награда"), false).draw(k, 393, 932, 300, 100);
        }
        String num = c.text("номер");
        if (num.matches("\\d")) {
            num = "0" + num;
        }
        k.text(num, k.font("Tektur-Bold.ttf", 33), 641 * 2, 993 * 2, 'r', th.number(), null, 0);
        return k.finish();
    }

    static void objectiveTop(CardCanvas k, CardSpec c) {
        boolean spec = "▶".equals(c.text("слот"));
        BufferedImage slot = k.icon(spec ? "26" : "28");
        if (slot != null) {
            k.putImage(CardCanvas.slate(CardAssets.fit(slot, 89 * 2, 89 * 2)), 64.5,
                spec ? 71 : 80);
        }
        List<String> lines = c.lines("верх");
        int n = lines.size();
        String kind = c.text("верх_вид");
        double px;
        double step;
        double yc;
        if ("реакция".equals(kind)) {
            BufferedImage plate = k.a.template("плашка боевого эффекта на задание.png");
            if (plate != null) {
                BufferedImage p2 = CardAssets.scale(plate, plate.getWidth() * 2,
                    plate.getHeight() * 2);
                k.g.drawImage(p2, 117 * 2, 8 * 2, null);
            }
            Font f = k.font(F_TOP_B, 51);
            k.squeezed(c.text("заголовок_верха"), f, 400, 71, CardCanvas.WHITE,
                spec ? RED_STROKE : SLATE_TITLE, 4, 0.85, 420);
            px = n <= 2 ? 36 : 34;
            step = n <= 2 ? 40 : 44;
            yc = n <= 2 ? 170 : 181;
        } else if ("2 max".equals(kind)) {
            k.put("66", 235, 78, 150, 110);
            Font f2 = k.font(F_TOP_B, 70);
            k.text("2", f2, 356 * 2, 80 * 2, 'm', CardCanvas.WHITE, SLATE, 4 * 2);
            k.put("54", 422, 80, 72, 58);
            k.text("max", f2, 522 * 2, 80 * 2, 'm', CardCanvas.WHITE, SLATE, 4 * 2);
            px = 34;
            step = 40;
            yc = 194;
        } else {
            String ic = c.text("иконка_верха");
            List<String> t = CardAssets.tokens(ic);
            String key = t.isEmpty() ? "57" : t.get(0).replaceAll("[{}]", "");
            k.put(key, 358, 80, 200, 136);
            px = 34;
            step = 40;
            yc = 194;
        }
        Font f = k.font(F_TOP, px);
        final double pxF = px;
        for (int i = 0; i < n; i++) {
            double y = yc + (i - (n - 1) / 2.0) * step;
            k.line(lines.get(i), f, f, 375, y, 'm', CardCanvas.WHITE, SLATE, 3,
                (tok, p) -> topIcon(k, tok, pxF), px, true);
        }
    }

    /** Иконка в тексте верха: войска шире, иконка зданий — с обводкой цвета текста. */
    static BufferedImage topIcon(CardCanvas k, String token, double px) {
        String key = CardAssets.key(token);
        BufferedImage ic = k.a.icon(key);
        if (ic == null) {
            return null;
        }
        return switch (key) {
            case "54" -> CardAssets.fit(ic, px * 1.6 * 2, px * 1.25 * 2);
            case "75" -> CardCanvas.recolorDark(CardAssets.fit(ic, px * 1.35 * 2, px * 1.35 * 2),
                SLATE);
            default -> CardAssets.fit(ic, px * 1.25 * 2, px * 1.25 * 2);
        };
    }

    static BufferedImage condIcon(CardCanvas k, String token, double px) {
        BufferedImage ic = k.icon(token);
        return ic == null ? null : CardAssets.fit(ic, px * 1.05 * 2, px * 1.05 * 2);
    }

    /** Строки условия: красный текст по правому краю на белых полупрозрачных плашках. */
    static int plates(CardCanvas k, String text, double y0, double step, double px,
                      double right, int maxLines, Color ink) {
        if (text.isBlank()) {
            return 0;
        }
        List<String> lines;
        while (true) {
            lines = wrapBalanced(k, text, px, 480, maxLines);
            if (lines != null || px <= 28) {
                break;
            }
            px -= 1;
        }
        if (lines == null) {
            throw new Problem("Текст не влезает в " + maxLines + " строки: «" + text + "»");
        }
        Font f = k.font(F_COND, px);
        final double pxF = px;
        k.g.setColor(new Color(255, 255, 255, 185));
        for (int i = 0; i < lines.size(); i++) {
            double w = k.line(lines.get(i), f, f, 0, 0, 'm', ink, CardCanvas.WHITE, 0,
                (t, p) -> condIcon(k, t, pxF), px, false);
            if (right - w - 10 < 132) {
                throw new Problem("Строка шире белого поля: «" + lines.get(i) + "»");
            }
            double y = y0 + i * step;
            k.g.fill(new java.awt.geom.Rectangle2D.Double((right - w - 10) * 2, (y - 22) * 2,
                (w + 20) * 2, 44 * 2));
        }
        for (int i = 0; i < lines.size(); i++) {
            k.line(lines.get(i), f, f, right, y0 + i * step, 'r', ink, CardCanvas.WHITE, 0,
                (t, p) -> condIcon(k, t, pxF), px, true);
        }
        return lines.size();
    }

    /**
     * Как можно меньше строк не шире ширины, а внутри — ровно: самая длинная строка
     * как можно короче. Строки, разделённые переводом, остаются строками.
     */
    static List<String> wrapBalanced(CardCanvas k, String text, double px, double width,
                                     int maxLines) {
        Font f = k.font(F_COND, px);
        if (text.contains("\n")) {
            List<String> out = new ArrayList<>();
            for (String l : text.split("\n")) {
                if (!l.isBlank()) {
                    out.add(l.trim());
                }
            }
            return out.size() <= maxLines ? out : null;
        }
        String[] w = text.trim().split("\\s+");
        int n = w.length;
        double[][] len = new double[n + 1][n + 1];
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b <= n; b++) {
                String s = String.join(" ", java.util.Arrays.copyOfRange(w, a, b));
                len[a][b] = k.line(s, f, f, 0, 0, 'm', RED_TEXT, CardCanvas.WHITE, 0,
                    (t, p) -> condIcon(k, t, px), px, false);
            }
        }
        for (int lines = 1; lines <= maxLines; lines++) {
            // best[b] = {максимум ширины, откуда пришли}
            double[] best = new double[n + 1];
            java.util.Arrays.fill(best, Double.MAX_VALUE);
            best[0] = 0;
            int[][] from = new int[lines + 1][n + 1];
            double[] cur = best.clone();
            for (int step = 1; step <= lines; step++) {
                double[] nxt = new double[n + 1];
                java.util.Arrays.fill(nxt, Double.MAX_VALUE);
                for (int a = 0; a < n; a++) {
                    if (cur[a] == Double.MAX_VALUE) {
                        continue;
                    }
                    for (int b = a + 1; b <= n; b++) {
                        if (len[a][b] > width) {
                            break;
                        }
                        double m = Math.max(cur[a], len[a][b]);
                        if (m < nxt[b]) {
                            nxt[b] = m;
                            from[step][b] = a;
                        }
                    }
                }
                cur = nxt;
            }
            if (cur[n] != Double.MAX_VALUE) {
                List<String> out = new ArrayList<>();
                int b = n;
                for (int step = lines; step >= 1; step--) {
                    int a = from[step][b];
                    out.add(0, String.join(" ", java.util.Arrays.copyOfRange(w, a, b)));
                    b = a;
                }
                return out;
            }
        }
        return null;
    }

    static void slash(CardCanvas k, int shift) {
        double ax = 511 * 2;
        double ay = (566 + shift) * 2;
        double bx = 487 * 2;
        double by = (660 + shift) * 2;
        k.g.setStroke(new BasicStroke(11 * 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        k.g.setColor(new Color(40, 30, 30));
        k.g.draw(new Line2D.Double(ax, ay, bx, by));
        k.g.setStroke(new BasicStroke(7 * 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        k.g.setColor(new Color(214, 60, 44));
        k.g.draw(new Line2D.Double(ax, ay + 4, bx, by - 4));
    }

    /** Дополнительная награда: иконки в ряд по центру нижней плашки; монеты внахлёст. */
    static void bonusRow(CardCanvas k, String text) {
        List<String> row = new ArrayList<>();
        for (String t : CardAssets.tokens(text)) {
            if (t.startsWith("{")) {
                row.add(CardAssets.key(t.substring(1, t.length() - 1)));
            }
        }
        if (row.isEmpty()) {
            return;
        }
        double[] xs = new double[row.size()];
        double x = 0;
        for (int i = 0; i < row.size(); i++) {
            double s = size(row.get(i));
            if (i > 0) {
                String prev = row.get(i - 1);
                x += "1".equals(row.get(i)) && "1".equals(prev) ? 0.56 * s
                    : size(prev) / 2 + s / 2 + 6;
            }
            xs[i] = x;
        }
        double cx0 = 393 - x / 2;
        for (int i = 0; i < row.size(); i++) {
            double s = size(row.get(i));
            k.put(row.get(i), cx0 + xs[i], "1".equals(row.get(i)) ? 935 : 930, s, s);
        }
    }

    private static double size(String key) {
        return "1".equals(key) ? 84 : 106;
    }

    // ======================================================================
    //  АРСЕНАЛ (803×520)
    // ======================================================================
    static final Color A_TEXT = new Color(0, 65, 77);
    static final Color A_NAME = new Color(66, 131, 150);
    static final Color A_NUMBER = new Color(90, 130, 161);
    static final Color A_TOP = new Color(135, 75, 74);
    static final Color DARK = new Color(45, 45, 50);

    static BufferedImage arsenal(CardAssets a, CardSpec c) {
        boolean spec = c.bool("спец");
        Theme th = theme(c.type());
        CardCanvas k = new CardCanvas(a, template(a, c, spec));
        arsenalTop(k, c);
        if (spec) {
            if (c.bool("контейнер")) {
                containerCell(k);
                Font f = k.font("TekturNarrow-Medium.ttf", 38);
                k.text("Место для", f, 540 * 2, 418 * 2, 'm', new Color(95, 95, 95), null, 0);
                k.text("контейнера", f, 540 * 2, 462 * 2, 'm', new Color(95, 95, 95), null, 0);
            }
            Font fz = k.font("TekturNarrow-Bold.ttf", 40);
            String price = c.text("цена");
            if (!price.isBlank()) {
                k.text("-" + price, fz, 44 * 2, 350 * 2, 'r', CardCanvas.WHITE, DARK, 4 * 2);
                List<String> pt = CardAssets.tokens(c.text("цена_иконка"));
                k.put(pt.isEmpty() ? "1" : pt.get(0).replaceAll("[{}]", ""), 76, 350, 58, 58);
            }
            List<String> st = CardAssets.tokens(c.text("спец_иконка"));
            if (!st.isEmpty()) {
                k.put(st.get(0).replaceAll("[{}]", ""), 222, 262, 132, 110);
            }
            String sign = c.text("спец_знак");
            if (!sign.isBlank()) {
                k.text(sign, fz, 268 * 2, 222 * 2, 'm', CardCanvas.WHITE, DARK, 4 * 2);
            }
            Font f = k.font("TekturNarrow-Medium.ttf", 40);
            List<String> sl = c.lines("спец_текст");
            for (int i = 0; i < sl.size(); i++) {
                k.line(sl.get(i), f, k.font("TekturNarrow-Bold.ttf", 40), 225, 356 + i * 52, 'm',
                    A_TEXT, CardCanvas.WHITE, 2, (t, p) -> arsIcon(k, t, 40), 40, true);
            }
        }
        String name = c.text("имя");
        double px = 45;
        Font fn = k.font("TekturNarrow-Bold.ttf", px);
        while (k.len(name, fn) > 590 * 2 && px > 24) {
            px -= 1;
            fn = k.font("TekturNarrow-Bold.ttf", px);
        }
        k.text(name, fn, 745 * 2, 175 * 2, 'r', CardCanvas.WHITE, th.stroke(), 4 * 2);
        String low = c.text("низ");
        Figure fig = Figure.of(c.fields.get("фигура"));
        boolean withFig = !fig.isEmpty() && !(spec && c.bool("контейнер"));
        if (withFig) {
            // ФИГУРА — условие свойства: слева в поле текста, текст правее
            fig.draw(k.g, 262 * 2, 352 * 2, 170 * 2, 230 * 2, th.stroke(), new Color(40, 50, 55));
        }
        if (!low.isBlank()) {
            // длинный текст не мельчит, а занимает поле шире — как на печати
            double rightX = spec && c.bool("контейнер") ? 322 : 749;
            double leftX = withFig ? 360 : spec && c.bool("контейнер") ? 150 : 190;
            if (lowSize(k, low, 44, leftX, rightX) < 40) {
                leftX = Math.min(leftX, 150);
            }
            arsenalLow(k, low, 44, leftX, rightX, th.text());
        }
        iconRow(k, c.text("ряд"));
        if (c.bool("звезда")) {
            k.put("22", 62, 445, 80, 80);
        }
        k.text(c.text("номер"), k.font("TekturNarrow-Bold.ttf", 34), 777 * 2, 484 * 2, 'r',
            th.number(), null, 0);
        return k.finish();
    }

    static BufferedImage arsIcon(CardCanvas k, String token, double px) {
        BufferedImage ic = k.icon(token);
        return ic == null ? null : CardAssets.fit(ic, px * 1.15 * 2, px * 1.15 * 2);
    }

    /** Верх: слева связка фишек, текст, справа связка фишек (иконки и знаки). */
    static void arsenalTop(CardCanvas k, CardSpec c) {
        List<String> lines = c.lines("верх");
        boolean centred = c.bool("верх_по_центру");
        double leftEnd = 258;
        List<String> left = CardAssets.tokens(c.text("верх_слева"));
        if (!left.isEmpty()) {
            leftEnd = chips(k, left, 258, 64, 'l', 54) + 10;
        }
        List<String> right = CardAssets.tokens(c.text("верх_справа"));
        double rightStart = 785;
        if (!right.isEmpty()) {
            double big = right.size() == 1 ? 96 : 80;
            rightStart = chips(k, right, 775, 62, 'r', big) - 10;
        }
        double width = centred ? 2 * Math.min(rightStart - 420, 420 - 222) : rightStart - leftEnd;
        double px = 35;
        Font f;
        while (true) {
            f = k.font("Tektur-Bold.ttf", px);
            double w = 0;
            for (String t : lines) {
                final double pp = px;
                w = Math.max(w, k.line(t, f, f, 0, 0, 'm', CardCanvas.WHITE, A_TOP, 4,
                    (tk, p) -> arsIcon(k, tk, pp), px, false));
            }
            if (w + 8 <= width || px <= 24) {
                break;
            }
            px -= 1;
        }
        final double pxF = px;
        for (int i = 0; i < lines.size(); i++) {
            double y = lines.size() > 1 ? 50 + i * 47 : 73;
            String t = lines.get(i);
            if (centred) {
                k.line(t, f, f, 420, y, 'm', CardCanvas.WHITE, A_TOP, 4,
                    (tk, p) -> arsIcon(k, tk, pxF), px, true);
            } else {
                k.line(t, f, f, leftEnd, y, 'l', CardCanvas.WHITE, A_TOP, 4,
                    (tk, p) -> arsIcon(k, tk, pxF), px, true);
            }
        }
    }

    /**
     * Связка фишек в строку: иконки высотой {@code size}, слова — белые с тёмной
     * обводкой. Возвращает дальний край (пиксели карты).
     */
    static double chips(CardCanvas k, List<String> toks, double x, double y, char ax,
                        double size) {
        Font fz = k.font("TekturNarrow-Bold.ttf", 40);
        List<Object> made = new ArrayList<>();
        List<Double> ws = new ArrayList<>();
        for (String t : toks) {
            if (t.startsWith("{")) {
                BufferedImage ic = k.icon(t.substring(1, t.length() - 1));
                BufferedImage f = ic == null ? null : CardAssets.fit(ic, size * 2, size * 2);
                made.add(f);
                ws.add(f == null ? size * 2 : f.getWidth());
            } else {
                made.add(t);
                ws.add(k.len(t, fz) + 8);
            }
        }
        double total = ws.stream().mapToDouble(Double::doubleValue).sum()
            + 6 * 2 * Math.max(0, toks.size() - 1);
        double cx = ax == 'r' ? x * 2 - total : x * 2;
        for (int i = 0; i < toks.size(); i++) {
            Object o = made.get(i);
            if (o instanceof BufferedImage f) {
                k.g.drawImage(f, (int) cx, (int) (y * 2 - f.getHeight() / 2.0), null);
            } else if (o instanceof String s) {
                k.text(s, fz, cx + 4, y * 2, 'l', CardCanvas.WHITE, DARK, 4 * 2);
            } else {
                k.missing((cx + ws.get(i) / 2) / 2, y, size, size, toks.get(i));
            }
            cx += ws.get(i) + 12;
        }
        return ax == 'r' ? (x * 2 - total) / 2 : (x * 2 + total) / 2;
    }

    /** Каким кеглем встанет постоянный эффект в этих границах. */
    static double lowSize(CardCanvas k, String text, double px, double leftX, double rightX) {
        while (px > 26) {
            Font f = k.font("TekturNarrow-Medium.ttf", px);
            Font fb = k.font("TekturNarrow-Bold.ttf", px);
            int n = 0;
            for (String para : text.split("\n")) {
                n += wrapGreedy(k, para, f, fb, px, (rightX - leftX) * 2).size();
            }
            if (n * px * 1.28 <= 478 - 222) {
                return px;
            }
            px -= 1;
        }
        return px;
    }

    /** Постоянный эффект: по правому краю, **жирное**, иконки в строке, свой перенос. */
    static void arsenalLow(CardCanvas k, String text, double px, double leftX, double rightX,
                           Color ink) {
        double top = 222;
        double bottom = 478;
        List<String> lines;
        double step;
        Font f;
        Font fb;
        while (true) {
            f = k.font("TekturNarrow-Medium.ttf", px);
            fb = k.font("TekturNarrow-Bold.ttf", px);
            lines = new ArrayList<>();
            for (String para : text.split("\n")) {
                lines.addAll(wrapGreedy(k, para, f, fb, px, (rightX - leftX) * 2));
            }
            step = px * 1.28;
            if (lines.size() * step <= bottom - top || px <= 26) {
                break;
            }
            px -= 1;
        }
        double y = top + step / 2;
        final double pxF = px;
        for (String l : lines) {
            k.line(l, f, fb, rightX, y, 'r', ink, CardCanvas.WHITE, 2,
                (t, p) -> arsIcon(k, t, pxF), px, true);
            y += step;
        }
    }

    static List<String> wrapGreedy(CardCanvas k, String para, Font f, Font fb, double px,
                                   double width) {
        List<String> out = new ArrayList<>();
        List<CardCanvas.Part> cur = new ArrayList<>();
        double w = 0;
        double space = k.len(" ", f);
        for (CardCanvas.Part p : CardCanvas.parts(para)) {
            double pw = p.kind() == 'i' ? px * 1.15 * 2
                : k.len(p.value(), p.kind() == 'b' ? fb : f);
            boolean stick = CardCanvas.sticks(p);
            if (!cur.isEmpty() && !stick && w + space + pw > width) {
                out.add(join(cur));
                cur.clear();
                w = 0;
            }
            if (!cur.isEmpty() && !stick) {
                w += space;
            }
            cur.add(p);
            w += pw;
        }
        if (!cur.isEmpty()) {
            out.add(join(cur));
        }
        return out;
    }

    static String join(List<CardCanvas.Part> ps) {
        StringBuilder sb = new StringBuilder();
        for (CardCanvas.Part p : ps) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(p.kind() == 'i' ? "{" + p.value() + "}"
                : p.kind() == 'b' ? "**" + p.value() + "**" : p.value());
        }
        return sb.toString();
    }

    /** Ряд под текстом: иконки и знаки по центру; {/} перечёркивает предыдущую иконку. */
    static void iconRow(CardCanvas k, String text) {
        List<String> toks = CardAssets.tokens(text);
        if (toks.isEmpty()) {
            return;
        }
        double size = 104;
        Font fz = k.font("TekturNarrow-Bold.ttf", 64);
        List<Double> ws = new ArrayList<>();
        for (String t : toks) {
            ws.add("{/}".equals(t) ? 0.0 : t.startsWith("{") ? size + 16 : k.len(t, fz) / 2 + 16);
        }
        double total = ws.stream().mapToDouble(Double::doubleValue).sum();
        double x = 470 - total / 2;
        double y = 408;
        double lastCx = x;
        for (int i = 0; i < toks.size(); i++) {
            String t = toks.get(i);
            double w = ws.get(i);
            if ("{/}".equals(t)) {
                double h = size / 2 * 0.72;
                k.g.setStroke(new BasicStroke(15 * 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                k.g.setColor(new Color(160, 20, 20));
                k.g.draw(new Line2D.Double((lastCx + h) * 2, (y - h) * 2, (lastCx - h) * 2,
                    (y + h) * 2));
                k.g.setStroke(new BasicStroke(8 * 2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                k.g.setColor(Color.WHITE);
                k.g.draw(new Line2D.Double((lastCx + h) * 2, (y - h) * 2, (lastCx - h) * 2,
                    (y + h) * 2));
                continue;
            }
            double cx = x + w / 2;
            if (t.startsWith("{")) {
                k.put(t.substring(1, t.length() - 1), cx, y, size, size);
                lastCx = cx;
            } else {
                k.text(t, fz, cx * 2, y * 2, 'm', CardCanvas.WHITE, DARK, 5 * 2);
            }
            x += w;
        }
    }

    /** Ячейка «Место для контейнера», как на печати: точки, скруглённый верх, ящик № 79. */
    static void containerCell(CardCanvas k) {
        double x0 = 332.5;
        double x1 = 745.5;
        double y0 = 176;
        double r = 25;
        double low = 530;
        k.g.setColor(new Color(232, 215, 195, 153));
        k.g.fill(new RoundRectangle2D.Double(x0 * 2, y0 * 2, (x1 - x0) * 2, (low - y0) * 2,
            r * 4, r * 4));
        List<double[]> dots = new ArrayList<>();
        double step = 9.5;
        java.util.function.BiConsumer<double[], double[]> seg = (p, q) -> {
            int n = Math.max(1, (int) (Math.hypot(q[0] - p[0], q[1] - p[1]) / step));
            for (int i = 0; i <= n; i++) {
                dots.add(new double[] {p[0] + (q[0] - p[0]) * i / n, p[1] + (q[1] - p[1]) * i / n});
            }
        };
        java.util.function.Consumer<double[]> arc = q -> {
            double cx = q[0];
            double cy = q[1];
            double from = q[2];
            double to = q[3];
            int n = Math.max(1, (int) (r * Math.abs(Math.toRadians(to - from)) * 57.3 / step));
            for (int i = 0; i <= n; i++) {
                double t = Math.toRadians(from + (to - from) * i / n);
                dots.add(new double[] {cx + r * Math.cos(t), cy + r * Math.sin(t)});
            }
        };
        arc.accept(new double[] {x0 + r, y0 + r, 180, 270});
        seg.accept(new double[] {x0 + r, y0}, new double[] {x1 - r, y0});
        arc.accept(new double[] {x1 - r, y0 + r, 270, 360});
        seg.accept(new double[] {x0, y0 + r}, new double[] {x0, low});
        seg.accept(new double[] {x1, y0 + r}, new double[] {x1, low});
        k.g.setColor(new Color(100, 100, 100));
        for (double[] d : dots) {
            k.g.fill(new java.awt.geom.Ellipse2D.Double((d[0] - 2) * 2, (d[1] - 2) * 2, 8, 8));
        }
        k.put("85", 540, 296, 132, 132);
    }

    // ======================================================================
    //  РЫНОК (1028×661): две половины — «Выполни …» и иконка действия
    // ======================================================================
    static final Color M_TEXT = new Color(30, 80, 40);

    static BufferedImage market(CardAssets a, CardSpec c) {
        CardCanvas k = new CardCanvas(a, template(a, c, false));
        Font f = k.font("TekturNarrow-SemiBold.ttf", 40);
        List<String> left = c.lines("слева");
        for (int i = 0; i < left.size(); i++) {
            marketLine(k, left.get(i), f, 72, 92 + i * 56, 'l');
        }
        List<String> right = c.lines("справа");
        for (int i = 0; i < right.size(); i++) {
            marketLine(k, right.get(i), f, 956, 92 + i * 56, 'r');
        }
        icon(k, c.text("иконка_слева"), 232, 300, 270, 210);
        icon(k, c.text("иконка_справа"), 796, 300, 270, 210);
        String num = c.text("номер");
        if (num.matches("\\d")) {
            num = "0" + num;
        }
        k.text(num, k.font("Tektur-Bold.ttf", 26), 1000 * 2, 630 * 2, 'r', M_TEXT, null, 0);
        return k.finish();
    }

    /** Строка рынка на белой полупрозрачной плашке, как на печати. */
    static void marketLine(CardCanvas k, String text, Font f, double x, double y, char ax) {
        double w = k.line(text, f, f, 0, 0, 'm', M_TEXT, CardCanvas.WHITE, 0,
            (t, p) -> condIcon(k, t, 40), 40, false);
        double x0 = ax == 'l' ? x : x - w;
        k.g.setColor(new Color(255, 255, 255, 190));
        k.g.fill(new java.awt.geom.Rectangle2D.Double((x0 - 8) * 2, (y - 24) * 2, (w + 16) * 2, 48 * 2));
        k.line(text, f, f, x, y, ax, M_TEXT, CardCanvas.WHITE, 0, (t, p) -> condIcon(k, t, 40), 40, true);
    }

    /** Связка иконок по центру точки; несколько — внахлёст, как монеты на печати. */
    static void icon(CardCanvas k, String text, double cx, double cy, double w, double h) {
        List<String> keys = new ArrayList<>();
        for (String t : CardAssets.tokens(text)) {
            if (t.startsWith("{")) {
                keys.add(t.substring(1, t.length() - 1));
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        double each = Math.min(h, w / (keys.size() * 0.78 + 0.22));
        double step = each * 0.78;
        double x = cx - step * (keys.size() - 1) / 2.0;
        for (String key : keys) {
            k.put(key, x, cy, each, each);
            x += step;
        }
    }

    // ======================================================================
    //  КОНТЕЙНЕР (402×402): буква, число, иконка, название в две строки
    // ======================================================================
    static final Color C_TEXT = new Color(40, 40, 44);
    static final Color C_LETTER = new Color(110, 72, 40);

    static BufferedImage container(CardAssets a, CardSpec c) {
        CardCanvas k = new CardCanvas(a, template(a, c, false));
        k.text(c.text("буква"), k.font("TekturNarrow-Bold.ttf", 40), 22 * 2, 46 * 2, 'l',
            C_LETTER, null, 0);
        k.text(c.text("число"), k.font("Tektur-Medium.ttf", 54), 380 * 2, 50 * 2, 'r',
            CardCanvas.WHITE, new Color(90, 90, 96), 3 * 2);
        icon(k, c.text("иконка"), 201, 128, 250, 135);
        List<String> name = c.lines("имя");
        Font f = k.font("Tektur-Medium.ttf", 36);
        for (int i = 0; i < name.size(); i++) {
            double y = name.size() == 1 ? 256 : 240 + i * 36;
            k.text(name.get(i), f, 201 * 2, y * 2, 'm', C_TEXT, CardCanvas.WHITE, 3 * 2);
        }
        return k.finish();
    }

    // ======================================================================
    //  ЖЕТОН ГЕКСА ЗАРОЖДЕНИЯ (1030×927): «Start», гекс с кубами и число,
    //  внизу ярлык «0 {5} ?» и ряд «чем платят → что делают с гексом»
    // ======================================================================
    static final Color HEX_INK = new Color(48, 52, 58);

    static BufferedImage spawnHex(CardAssets a, CardSpec c) {
        boolean orange = "рыжая".equals(c.text("сторона"));
        String file = c.type().hexFile(orange, c.integer("рисунок", 1));
        BufferedImage tpl = a.template(file);
        if (tpl == null) {
            throw new Problem("Нет шаблона «" + file + "» в папке «" + a.templates + "»");
        }
        CardCanvas k = new CardCanvas(a, tpl);
        if (c.bool("старт")) {
            Font big = k.font("Tektur-Bold.ttf", 110);
            Font small = k.font("Tektur-Bold.ttf", 64);
            double w = k.len("S", big) + k.len("tart", small);
            double x = 516 * 2 - w / 2;
            k.text("S", big, x, 100 * 2, 'l', CardCanvas.WHITE, HEX_INK, 5 * 2);
            k.text("tart", small, x + k.len("S", big) - 4, 114 * 2, 'l', CardCanvas.WHITE, HEX_INK,
                4 * 2);
        }
        k.put("51", 420, 456, 160, 150);
        k.text(c.text("число"), k.font("Tektur-Bold.ttf", 232), 606 * 2, 462 * 2, 'm',
            CardCanvas.WHITE, HEX_INK, 7 * 2);
        centreChips(k, CardAssets.tokens(c.text("ярлык")), 516, 694, 56, 60);
        centreChips(k, CardAssets.tokens(c.text("ряд")), 516, 810, 120, 70);
        return k.finish();
    }

    /** Связка фишек по центру: иконки, слова и стрелка «→». */
    static void centreChips(CardCanvas k, List<String> toks, double cx, double cy, double size,
                            double px) {
        if (toks.isEmpty()) {
            return;
        }
        Font f = k.font("Tektur-Bold.ttf", px);
        List<Double> ws = new ArrayList<>();
        for (String t : toks) {
            if (t.startsWith("{")) {
                BufferedImage ic = k.icon(t.substring(1, t.length() - 1));
                ws.add(ic == null ? size : CardAssets.fit(ic, size, size).getWidth() * 1.0);
            } else if ("→".equals(t) || "->".equals(t)) {
                ws.add(size * 0.6);
            } else {
                ws.add(k.len(t, f) / 2);
            }
        }
        double gap = size * 0.12;
        double total = ws.stream().mapToDouble(Double::doubleValue).sum() + gap * (toks.size() - 1);
        double x = cx - total / 2;
        for (int i = 0; i < toks.size(); i++) {
            String t = toks.get(i);
            double w = ws.get(i);
            if (t.startsWith("{")) {
                k.put(t.substring(1, t.length() - 1), x + w / 2, cy, size, size);
            } else if ("→".equals(t) || "->".equals(t)) {
                double y = cy * 2;
                k.g.setColor(HEX_INK);
                k.g.setStroke(new BasicStroke((float) (size * 0.045 * 2), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                k.g.draw(new Line2D.Double((x + w * 0.1) * 2, y, (x + w * 0.85) * 2, y));
                java.awt.geom.Path2D head = new java.awt.geom.Path2D.Double();
                head.moveTo((x + w * 0.95) * 2, y);
                head.lineTo((x + w * 0.7) * 2, y - size * 0.13 * 2);
                head.lineTo((x + w * 0.7) * 2, y + size * 0.13 * 2);
                head.closePath();
                k.g.fill(head);
            } else {
                k.text(t, f, (x + w / 2) * 2, cy * 2, 'm', CardCanvas.WHITE, HEX_INK, 4 * 2);
            }
            x += w + gap;
        }
    }

    // ======================================================================
    //  ПРИКАЗ (661×1028): название, два верхних кольца с подписями, плашка,
    //  полоса нижнего приказа и два нижних кольца — как tools/gen_orders_5.py
    // ======================================================================
    static final Color O_SIGN = new Color(47, 58, 52);
    static final Color O_TITLE = new Color(246, 243, 238);
    static final Color O_STROKE = new Color(78, 52, 42);

    static BufferedImage order(CardAssets a, CardSpec c) {
        String file = "карты-приказов-" + (c.text("цвет").isBlank() ? "красный" : c.text("цвет"))
            + ".png";
        BufferedImage tpl = a.template(file);
        if (tpl == null) {
            throw new Problem("Нет шаблона «" + file + "» в папке «" + a.templates + "»");
        }
        CardCanvas k = new CardCanvas(a, tpl);
        title(k, c.text("имя"), 340, 68, 520, 52, null);
        ring(k, c.text("верх_слева"), 168, 339, 78);
        ring(k, c.text("верх_справа"), 491, 340, 78);
        sign(k, c.text("подпись_слева"), 168, 339 + 112, 300, 46);
        sign(k, c.text("подпись_справа"), 491, 340 + 112, 300, 46);
        List<String> pl = CardAssets.tokens(c.text("плашка"));
        if (!pl.isEmpty()) {
            k.g.setColor(new Color(255, 255, 255, 120));
            k.g.fill(new RoundRectangle2D.Double(140 * 2, 518 * 2, 380 * 2, 76 * 2, 76 * 2, 76 * 2));
            centreChips(k, pl, 330, 556, 66, 50);
        }
        title(k, c.text("низ_имя"), 400, 680, 430, 50, O_STROKE);
        ring(k, c.text("низ_слева"), 177, 803, 61);
        ring(k, c.text("низ_справа"), 482, 803, 61);
        sign(k, c.text("низ_подпись_слева"), 177, 803 + 90, 290, 44);
        sign(k, c.text("низ_подпись_справа"), 482, 803 + 90, 290, 44);
        return k.finish();
    }

    /** Название приказа: сверху светлое, внизу — с толстой коричневой обводкой. */
    static void title(CardCanvas k, String text, double cx, double cy, double maxW, double px,
                      Color stroke) {
        if (text.isBlank()) {
            return;
        }
        Font f = k.font("TekturNarrow-Bold.ttf", px);
        while (k.len(text, f) > maxW * 2 && px > 24) {
            px -= 1;
            f = k.font("TekturNarrow-Bold.ttf", px);
        }
        k.text(text, f, cx * 2, cy * 2, 'm', O_TITLE, stroke, stroke == null ? 0 : Math.max(2, px / 14) * 2);
    }

    /** Иконки в кольце: одна — крупно, две — по диагонали. */
    static void ring(CardCanvas k, String text, double cx, double cy, double r) {
        List<String> keys = new ArrayList<>();
        for (String t : CardAssets.tokens(text)) {
            if (t.startsWith("{")) {
                keys.add(t.substring(1, t.length() - 1));
            }
        }
        if (keys.size() == 1) {
            k.put(keys.get(0), cx, cy, r * 2.05, r * 2.05);
        } else if (keys.size() >= 2) {
            k.put(keys.get(0), cx - r * 0.42, cy - r * 0.40, r * 1.25, r * 1.25);
            k.put(keys.get(1), cx + r * 0.42, cy + r * 0.38, r * 1.25, r * 1.25);
        }
    }

    static void sign(CardCanvas k, String text, double cx, double y, double maxW, double px) {
        if (text.isBlank()) {
            return;
        }
        Font f = k.font("TekturNarrow-Regular.ttf", px);
        while (k.len(text, f) > maxW * 2 && px > 12) {
            px -= 1;
            f = k.font("TekturNarrow-Regular.ttf", px);
        }
        k.text(text, f, cx * 2, y * 2, 'm', O_SIGN, null, 0);
    }

    static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v);
    }
}
