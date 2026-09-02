package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;

import kelium.report.ReplayRecord;

/**
 * ResultsPanel — ЭКРАН ИТОГОВ: кто победил, за счёт чего и где партия повернула.
 *
 * <p>Пьедестал из 1.0 сделан хорошо — включая проверку «это настоящий финал, а не
 * стартовая расстановка», на которой раньше итоги показывались ещё до начала матча.
 * Здесь он сохранён и дополнен тем, чего не хватало: РАЗБИВКОЙ очков по источникам
 * (видно, кто выехал на заданиях, а кто на науке), КРИВОЙ очков по раундам и
 * ПОВОРОТНЫМИ МОМЕНТАМИ, по которым можно сразу перейти.
 *
 * <p>Поворотные моменты — арифметика: шаги с наибольшим изменением счёта, снос ЦУ и
 * закрытие супер-задания. Никаких оценок характера вроде «играл агрессивно»: такую
 * фразу дизайнер примет за факт, а она ничем не подтверждена.
 */
public final class ResultsPanel extends JComponent {

    private static final long serialVersionUID = 1L;

    private final Session session;
    private final List<Rectangle> momentRows = new ArrayList<>();

    /** Кнопки-переключатели графика: прямоугольник на каждый показатель. */
    private final List<Rectangle> tabRects = new ArrayList<>();
    private Metrics.Kind shown = Metrics.Kind.VP;
    /** Показатели считаются один раз на запись. */
    private Metrics metrics;
    private ReplayRecord metricsOf;

    public ResultsPanel(Session session) {
        this.session = session;
        setOpaque(true);
        setFont(Theme.body());
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                Metrics.Kind[] kinds = Metrics.Kind.values();
                for (int i = 0; i < tabRects.size() && i < kinds.length; i++) {
                    if (tabRects.get(i).contains(e.getPoint())) {
                        shown = kinds[i];
                        repaint();
                        return;
                    }
                }
                for (int i = 0; i < momentRows.size(); i++) {
                    if (momentRows.get(i).contains(e.getPoint())
                            && i < session.turningPoints().size()) {
                        session.seek(session.turningPoints().get(i).frame());
                        return;
                    }
                }
            }
        });
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    /** Сколько места заняло содержимое в последнюю отрисовку — под прокрутку. */
    private int contentH;

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(900), Math.max(Theme.px(700), contentH));
    }

    private Metrics metrics(ReplayRecord rec) {
        if (metrics == null || metricsOf != rec) {
            metrics = Metrics.of(rec);
            metricsOf = rec;
        }
        return metrics;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gg.setColor(Theme.bg());
        gg.fillRect(0, 0, getWidth(), getHeight());
        momentRows.clear();
        ReplayRecord rec = session.record();
        if (rec == null || rec.frames.isEmpty()) {
            gg.dispose();
            return;
        }
        ReplayRecord.Frame last = rec.frames.get(rec.frames.size() - 1);
        if (last.snapshot == null) {
            gg.dispose();
            return;
        }

        int pad = Theme.px(20);
        int y = pad;

        // ---- шапка
        gg.setFont(Theme.display(24));
        gg.setColor(Theme.ink());
        String head = "ИТОГ ПАРТИИ · " + rec.rounds + " раундов · сид " + rec.seed
            + " · правила " + rec.ruleset;
        gg.drawString(head, pad, y + Theme.px(24));
        y += Theme.px(42);
        gg.setFont(Theme.note(17));
        gg.setColor(Theme.ink2());
        gg.drawString(Names.conditionLong(rec.condition, rec.spawnLeft, rec.spawnThreshold), pad, y + Theme.px(18));
        y += Theme.px(38);

        // ---- пьедестал
        List<ReplayRecord.Player> ps = new ArrayList<>(last.snapshot.players);
        Integer winner = rec.winner;
        ps.sort((a, b) -> {
            if (winner != null && a.seat == winner) {
                return -1;
            }
            if (winner != null && b.seat == winner) {
                return 1;
            }
            return Integer.compare(b.vp.getOrDefault("total", 0),
                a.vp.getOrDefault("total", 0));
        });
        // ---- ДВА БЛОКА В ОДНУ ГОРИЗОНТАЛЬ: слева пьедестал, справа разбивка очков.
        // Раньше полоски игроков растягивались во всю ширину окна и выглядели
        // пустыми, а таблица очков жалась внизу мелким кеглем (замечание
        // дизайнера 13.08.2026).
        // Доля левого столбца уменьшена: справа теперь таблица очков крупным
        // кеглем, и ей нужно заметно больше места, чем полоскам пьедестала.
        int gap = Theme.px(18);
        int leftW = Math.max(Theme.px(360), (getWidth() - 2 * pad - gap) * 42 / 100);
        int rightX = pad + leftW + gap;
        int rightW = getWidth() - pad - rightX;
        int top = y;

        int rowH = Theme.px(50);
        for (int i = 0; i < ps.size(); i++) {
            ReplayRecord.Player p = ps.get(i);
            boolean champ = i == 0;
            int h = rowH - Theme.px(9);
            gg.setColor(champ ? Theme.alpha(Theme.points(), 0.16) : Theme.tile());
            gg.fill(new RoundRectangle2D.Double(pad, y, leftW, h,
                Theme.R_PANEL * 2, Theme.R_PANEL * 2));
            gg.setColor(champ ? Theme.points() : Theme.border());
            gg.setStroke(new BasicStroke(champ ? Theme.pxf(1.6) : 1f));
            gg.draw(new RoundRectangle2D.Double(pad, y, leftW, h,
                Theme.R_PANEL * 2, Theme.R_PANEL * 2));

            gg.setFont(Theme.mono(champ ? 24 : 20, Font.BOLD));
            gg.setColor(champ ? Theme.points() : Theme.ink3());
            gg.drawString(String.valueOf(i + 1), pad + Theme.px(12), y + h / 2 + Theme.px(8));

            int chip = champ ? Theme.px(26) : Theme.px(20);
            gg.setColor(Theme.seat(p.seat));
            gg.fill(new Ellipse2D.Double(pad + Theme.px(40), y + (h - chip) / 2.0, chip, chip));

            // ОЧКИ МЕРЯЕМ ПЕРВЫМИ, ИМЯ ВПИСЫВАЕМ В ОСТАТОК. Имя бота длинное
            // («Просчёт вперёд · агрессивный»), и в узком окне оно наезжало прямо
            // на число очков (замечание дизайнера 15.08.2026). Теперь у числа своё
            // место у правого края полосы, а имени достаётся всё, что осталось,
            // с многоточием на конце.
            String vp = p.vp.getOrDefault("total", 0) + " ПО";
            gg.setFont(Theme.mono(champ ? 24 : 20, Font.BOLD));
            int vpW = gg.getFontMetrics().stringWidth(vp);
            int nameX = pad + Theme.px(40) + chip + Theme.px(10);
            int nameRoom = pad + leftW - Theme.px(14) - vpW - Theme.px(10) - nameX;

            gg.setFont(Theme.font(champ ? 19 : 17, champ ? Font.BOLD : Font.PLAIN));
            gg.setColor(Theme.ink());
            gg.drawString(fit(gg, rec.playerName(p.seat), nameRoom), nameX,
                y + h / 2 + Theme.px(8));

            gg.setFont(Theme.mono(champ ? 24 : 20, Font.BOLD));
            gg.setColor(champ ? Theme.points() : Theme.ink());
            gg.drawString(vp, pad + leftW - Theme.px(14) - vpW, y + h / 2 + Theme.px(8));
            y += rowH;
        }
        int leftBottom = y;

        // ---- справа: откуда очки
        int rightBottom = paintBreakdown(gg, rightX, top, rightW, ps);

        y = Math.max(leftBottom, rightBottom) + Theme.px(14);

        // ---- ПОД НИМИ: большой график развития с переключателями
        // ВЫСОТА ГРАФИКА ЗАДАНА, а не «весь остаток окна»: остаток считался от
        // высоты панели, а она теперь тянется за содержимым — график раздувался на
        // пол-экрана и линии терялись в пустоте.
        int chartH = Theme.px(300);
        y = paintChart(gg, pad, y, getWidth() - 2 * pad, chartH, ps, rec);

        // ---- поворотные моменты
        y += Theme.px(18);
        gg.setFont(Theme.font(14, Font.BOLD));
        gg.setColor(Theme.ink3());
        gg.drawString("ПОВОРОТНЫЕ МОМЕНТЫ", pad, y);
        y += Theme.px(12);
        gg.setFont(Theme.note(16));
        for (Session.Moment m : session.turningPoints()) {
            Rectangle r = new Rectangle(pad, y, getWidth() - 2 * pad, Theme.px(22));
            momentRows.add(r);
            MarkIcons.paint(gg, "UNIT", pad + Theme.px(6), y + Theme.px(13), Theme.px(9),
                Theme.accent());
            gg.setColor(Theme.ink2());
            gg.drawString(m.text(), pad + Theme.px(16), y + Theme.px(16));
            y += Theme.px(22);
        }
        if (session.turningPoints().isEmpty()) {
            gg.setColor(Theme.ink3());
            gg.drawString("в этой партии счёт менялся ровно — резких поворотов не нашлось",
                pad, y + Theme.px(19));
            y += Theme.px(22);
        }
        // ВЫСОТА ПО СОДЕРЖИМОМУ. Кегль вырос, блоки стали выше окна, и с прежней
        // прибитой высотой поворотные моменты просто обрезались снизу.
        int need = y + pad;
        if (need != contentH) {
            contentH = need;
            javax.swing.SwingUtilities.invokeLater(this::revalidate);
        }
        gg.dispose();
    }

    /** Обрезать надпись под ширину: лучше многоточие, чем заезд на соседний столбец. */
    private static String fit(Graphics2D g, String s, int w) {
        if (g.getFontMetrics().stringWidth(s) <= w) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && g.getFontMetrics().stringWidth(t + "…") > w) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    /** Таблица «источник очков × игрок». Возвращает Y низа таблицы. */
    private int paintBreakdown(Graphics2D g, int x, int y, int w,
                               List<ReplayRecord.Player> ps) {
        Set<String> keys = new LinkedHashSet<>();
        for (ReplayRecord.Player p : ps) {
            for (String k : p.vp.keySet()) {
                if (!"total".equals(k) && p.vp.get(k) != 0) {
                    keys.add(k);
                }
            }
        }
        // КЕГЕЛЬ ЗАМЕТНО КРУПНЕЕ: цифры «за что и сколько» — главное на этом экране,
        // а стояли одиннадцатым размером и не читались (замечание дизайнера
        // 13.08.2026).
        g.setFont(Theme.font(17, Font.BOLD));
        g.setColor(Theme.ink3());
        g.drawString("ОТКУДА ОЧКИ", x, y);
        int ty = y + Theme.px(34);
        // СТОЛБЕЦ ЦИФР ПОДГОНЯЕТСЯ ПОД ШИРИНУ. Кегль здесь в два с половиной раза
        // больше прежнего (просьба дизайнера 13.08.2026), и прибитые 50 пикселей
        // на игрока перестали вмещать число: столбцы налезали друг на друга. Теперь
        // ширина считается от самого широкого числа, а названиям достаётся остаток —
        // но не меньше трети блока, иначе они обрезаются.
        g.setFont(Theme.mono(24, Font.PLAIN));
        int digits = g.getFontMetrics().stringWidth("00") + Theme.px(16);
        int colW = Math.max(digits, Theme.px(56));
        int nameW = Math.max(w / 3, w - ps.size() * colW);
        colW = Math.max(Theme.px(30), (w - nameW) / Math.max(1, ps.size()));

        g.setFont(Theme.font(18, Font.BOLD));
        for (int i = 0; i < ps.size(); i++) {
            g.setColor(Theme.seatInk(ps.get(i).seat));
            String s = "И" + (ps.get(i).seat + 1);
            g.drawString(s, x + nameW + i * colW + colW / 2
                - g.getFontMetrics().stringWidth(s) / 2, ty);
        }
        ty += Theme.px(10);
        boolean stripe = false;
        int rowH = Theme.px(36);
        for (String k : keys) {
            ty += rowH;
            stripe = !stripe;
            if (stripe) {
                g.setColor(Theme.alpha(Theme.tile(), 0.8));
                g.fillRect(x, ty - Theme.px(26), w, rowH);
            }
            g.setColor(Theme.ink2());
            g.setFont(Theme.note(19));
            g.drawString(fit(g, Names.vp(k), nameW - Theme.px(8)), x, ty);
            g.setFont(Theme.mono(24, Font.PLAIN));
            for (int i = 0; i < ps.size(); i++) {
                int v = ps.get(i).vp.getOrDefault(k, 0);
                String s = v == 0 ? "·" : String.valueOf(v);
                g.setColor(v == 0 ? Theme.ink3() : Theme.ink());
                g.drawString(s, x + nameW + i * colW + colW / 2
                    - g.getFontMetrics().stringWidth(s) / 2, ty);
            }
        }
        ty += Theme.px(14);
        g.setColor(Theme.border());
        g.drawLine(x, ty, x + w, ty);
        ty += Theme.px(34);
        g.setFont(Theme.font(20, Font.BOLD));
        g.setColor(Theme.ink());
        g.drawString("ИТОГ", x, ty);
        g.setFont(Theme.mono(27, Font.BOLD));
        for (int i = 0; i < ps.size(); i++) {
            String s = String.valueOf(ps.get(i).vp.getOrDefault("total", 0));
            g.setColor(Theme.points());
            g.drawString(s, x + nameW + i * colW + colW / 2
                - g.getFontMetrics().stringWidth(s) / 2, ty);
        }
        return ty + Theme.px(10);
    }

    /**
     * КНОПКИ-ПЕРЕКЛЮЧАТЕЛИ ГРАФИКА. Возвращает Y низа последнего ряда.
     *
     * <p>Раньше ширина кнопки мерилась обычным начертанием, а выбранная кнопка
     * печаталась полужирным — надпись не влезала в свою рамку, а соседи от каждого
     * щелчка разъезжались, будто кнопки скачут (замечание дизайнера 13.08.2026).
     * Теперь ЛЮБОЕ состояние набирается ОДНИМ шрифтом (широким обычным), а выбор
     * показан только цветом и рамкой — от щелчка ничего не двигается.
     *
     * <p>Ряд РАСТЯГИВАЕТСЯ НА ВСЮ ШИРИНУ: сначала считается своя ширина каждой
     * надписи, потом остаток делится между кнопками ряда поровну. Кнопки стоят
     * ровным строем, а не жмутся к левому краю с пустотой справа.
     */
    private int paintTabs(Graphics2D g, int x, int y, int w) {
        Metrics.Kind[] kinds = Metrics.Kind.values();
        int bh = Theme.px(28);
        int gap = Theme.px(6);
        // Кегль подписей на графике и его кнопках держим ОТДЕЛЬНО от общего:
        // на график смотрят издали, целиком, и после общего уменьшения текста
        // его цифры и ярлыки просели ниже читаемого (замечание дизайнера
        // 14.08.2026 — «на графике мелковат ещё сильнее»).
        Font face = Theme.wideText(14.5);
        g.setFont(face);
        java.awt.FontMetrics fm = g.getFontMetrics();

        // ---- разложить по рядам по своей ширине
        List<List<Integer>> rows = new ArrayList<>();
        List<Integer> row = new ArrayList<>();
        int[] own = new int[kinds.length];
        int used = 0;
        for (int i = 0; i < kinds.length; i++) {
            own[i] = fm.stringWidth(kinds[i].label) + Theme.px(22);
            int need = own[i] + (row.isEmpty() ? 0 : gap);
            if (!row.isEmpty() && used + need > w) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
                need = own[i];
            }
            row.add(i);
            used += need;
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }

        // ---- растянуть каждый ряд на всю ширину и нарисовать
        Rectangle[] boxes = new Rectangle[kinds.length];
        int by = y;
        for (List<Integer> r : rows) {
            int sum = 0;
            for (int i : r) {
                sum += own[i];
            }
            int extra = w - sum - gap * (r.size() - 1);
            int bx = x;
            for (int j = 0; j < r.size(); j++) {
                int i = r.get(j);
                // Остаток делится поровну, а «хвост» от деления уходит последней
                // кнопке — иначе ряд не дотягивает до края на пару пикселей.
                int add = extra / r.size() + (j == r.size() - 1 ? extra % r.size() : 0);
                boxes[i] = new Rectangle(bx, by, own[i] + add, bh);
                bx += boxes[i].width + gap;
            }
            by += bh + gap;
        }
        for (int i = 0; i < kinds.length; i++) {
            Rectangle r = boxes[i];
            tabRects.add(r);
            boolean on = kinds[i] == shown;
            g.setColor(on ? Theme.alpha(Theme.accent(), 0.18) : Theme.tile());
            g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
                Theme.R_PANEL * 2, Theme.R_PANEL * 2));
            g.setColor(on ? Theme.accent() : Theme.border());
            g.setStroke(new BasicStroke(on ? Theme.pxf(2.0) : 1f));
            g.draw(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height,
                Theme.R_PANEL * 2, Theme.R_PANEL * 2));
            g.setColor(on ? Theme.ink() : Theme.ink2());
            g.setFont(face);
            int tw = fm.stringWidth(kinds[i].label);
            g.drawString(kinds[i].label, r.x + (r.width - tw) / 2,
                r.y + (bh + fm.getAscent() - fm.getDescent()) / 2);
        }
        return by - gap;
    }

    /**
     * БОЛЬШОЙ ГРАФИК РАЗВИТИЯ с кнопками-переключателями показателя.
     *
     * <p>У расходуемых ресурсов рисуются ДВЕ линии на игрока: жирная — сколько
     * держит в руках прямо сейчас, тонкая пунктирная — сколько потратил за партию
     * накопительно. Возвращает Y низа блока.
     */
    private int paintChart(Graphics2D g, int x, int y, int w, int h,
                           List<ReplayRecord.Player> ps, ReplayRecord rec) {
        Metrics m = metrics(rec);
        tabRects.clear();

        // ---- кнопки-переключатели
        int by = paintTabs(g, x, y, w);
        // ОТСТУП ПОСЛЕ КНОПОК. Между рядом переключателей и графиком стояли
        // четыре точки, и подпись «ПО по ходу партии» прижималась к кнопкам
        // вплотную — ряд кнопок и график читались как одно пятно (замечание
        // дизайнера 14.08.2026). Здесь заложено место и под саму подпись, и под
        // просвет над ней.
        int top = by + Theme.px(30);
        int bottom = y + h - Theme.px(18);
        if (bottom < top + Theme.px(40)) {
            bottom = top + Theme.px(40);
        }

        // ---- рамка и сетка
        int max = Math.max(1, m.max(shown, true));
        g.setColor(Theme.alpha(Theme.border(), 0.9));
        g.drawLine(x, bottom, x + w, bottom);
        g.setFont(Theme.font(12, Font.PLAIN));
        for (int i = 0; i <= 4; i++) {
            int gy = bottom - (bottom - top) * i / 4;
            g.setColor(Theme.alpha(Theme.border(), i == 0 ? 0.9 : 0.35));
            g.drawLine(x, gy, x + w, gy);
            g.setColor(Theme.ink3());
            g.drawString(String.valueOf(max * i / 4), x + w + Theme.px(4), gy + Theme.px(4));
        }

        // ---- границы раундов
        int n = m.frames();
        int rounds = Math.max(1, session.roundCount());
        for (int r = 1; r <= rounds; r++) {
            int f = rec.firstFrameOfRound(r);
            double px = n <= 1 ? x : x + w * f / (double) (n - 1);
            g.setColor(Theme.alpha(Theme.border(), 0.5));
            g.drawLine((int) px, top, (int) px, bottom);
            g.setColor(Theme.ink3());
            g.drawString("Р" + r, (float) (px + Theme.px(3)), bottom + Theme.px(12));
        }

        // ---- линии игроков
        for (ReplayRecord.Player p : ps) {
            int seat = p.seat;
            g.setColor(Theme.seatInk(seat));
            g.setStroke(new BasicStroke(Theme.pxf(2.0), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            g.draw(line(m.instant(shown, seat), x, w, top, bottom, max));
            if (shown.hasSpent) {
                g.setStroke(new BasicStroke(Theme.pxf(1.2), BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_ROUND, 10f,
                    new float[]{Theme.pxf(4), Theme.pxf(4)}, 0f));
                g.setColor(Theme.alpha(Theme.seatInk(seat), 0.75));
                g.draw(line(m.spent(shown, seat), x, w, top, bottom, max));
            }
        }

        // ---- что за линии
        g.setFont(Theme.font(12, Font.BOLD));
        g.setColor(Theme.ink3());
        String legend = shown.hasSpent
            ? shown.label.toUpperCase(java.util.Locale.ROOT)
                + " — жирная линия: сколько на руках · пунктир: сколько потрачено за партию"
            : shown.label.toUpperCase(java.util.Locale.ROOT) + " по ходу партии";
        if (shown == Metrics.Kind.DEBRIS) {
            legend += "   (в счёт входят и обломки на складе, и очки с жетонов "
                + "на карте трофеев, ещё не сданных — наука платит и тем, и другим)";
        }
        g.drawString(legend, x, top - Theme.px(8));
        return bottom + Theme.px(14);
    }

    /** Ломаная по кадрам: значения ряда в рамке графика. */
    private static Path2D line(int[] values, int x, int w, int top, int bottom, int max) {
        Path2D p = new Path2D.Double();
        int n = values.length;
        for (int i = 0; i < n; i++) {
            double px = n <= 1 ? x : x + w * i / (double) (n - 1);
            double py = bottom - (bottom - top) * values[i] / (double) max;
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        return p;
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        Metrics.Kind[] kinds = Metrics.Kind.values();
        for (int i = 0; i < tabRects.size() && i < kinds.length; i++) {
            if (tabRects.get(i).contains(e.getPoint())) {
                return Ui2.tip(kinds[i].hasSpent
                    ? "График «" + kinds[i].label + "»: жирная линия — сколько на руках, "
                        + "пунктир — сколько потрачено за партию (сумма всех списаний)."
                    : "График «" + kinds[i].label + "» по ходу партии.");
            }
        }
        for (int i = 0; i < momentRows.size(); i++) {
            if (momentRows.get(i).contains(e.getPoint())) {
                return Ui2.tip("Щелчок — перейти к этому шагу партии.");
            }
        }
        return null;
    }
}
