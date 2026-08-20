package kelium.gui.replay2;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import kelium.report.ReplayRecord;

/**
 * КАРТЫ ПРИКАЗОВ ИГРОКА — панель, выезжающая из-под его полосы (заказ дизайнера
 * 19–20.08.2026).
 *
 * <p>ЧТО ПОКАЗЫВАЕТ, СЛЕВА НАПРАВО — в том же порядке, в каком карта и живёт
 * (решение дизайнера 20.08.2026):
 * <ul>
 *   <li><b>слева</b> — РУКА веером: одна карта стоит прямо, несколько
 *       разворачиваются вокруг общей точки ниже панели, как держат карты;</li>
 *   <li><b>по центру</b> — карта ЭТОГО круга, крупнее и в рамке; когда круг
 *       ещё не начался, здесь пустое место с пунктирной рамкой, а не провал;</li>
 *   <li><b>справа</b> — СБРОС: отыгранные карты стопкой, в порядке кругов.</li>
 * </ul>
 *
 * <p>КАРТЫ ЕЗДЯТ, А НЕ ПЕРЕПРЫГИВАЮТ. Место каждой карты считается в раскладку
 * «номер карты → место», и панель помнит ПРЕДЫДУЩУЮ раскладку: при смене кадра
 * карта плавно идёт со своего старого места на новое — из руки в центр, когда её
 * разыграли, и из центра в сброс, когда круг кончился. Направление движения
 * совпадает с чтением, слева направо, поэтому глаз успевает за картой.
 *
 * <p>КАРТЫ ВЫХОДЯТ ЗА ВЕРХНЮЮ КРОМКУ ПОДЛОЖКИ — так и задумано (просьба
 * дизайнера 20.08.2026). Подложка занимает лишь нижнюю часть компонента, а карты
 * рисуются выше её края. Поэтому сам компонент делается ВЫШЕ подложки: Swing
 * обрезает рисование по границам компонента, и без запаса сверху карты обрубались
 * бы ровно там, где должны выступать.
 *
 * <p>ЦВЕТ КАРТ — ЦВЕТ КОЛОДЫ ИГРОКА ({@code orderColor}), а не общий серый: у
 * каждого своя колода приказов, это главная асимметрия партии, и когда открыты
 * четыре панели сразу, цвет — единственный быстрый способ не спутать, чьи карты
 * перед тобой.
 *
 * <p>Панель НЕ ХРАНИТ СОСТОЯНИЕ ПАРТИИ: всё берётся из кадра записи в момент
 * рисования. Разыгранные и текущая — из {@code record.orderPlays} (только там
 * есть действия приказов, совпадение и открытие низа), рука — из
 * {@code snapshot.players.get(seat).orderHand}, а приказы карты, ещё лежащей в
 * руке, спрашиваются у каталога приказов.
 */
public final class OrderCardsPanel extends JComponent {

    private static final long serialVersionUID = 1L;

    /**
     * ПРОПОРЦИИ КАРТЫ. Шире, чем печатная карта приказа (184/132), СОЗНАТЕЛЬНО:
     * подписи идут ГОРИЗОНТАЛЬНО (требование дизайнера 20.08.2026 — «тупо что
     * на картах всё написано вертикально, поверни нормально горизонтально»), а
     * названия приказов длинные («ИНФРАСТРУКТУРА», «ПРИОБРЕТЕНИЕ»). На узкой
     * карте горизонтальный текст пришлось бы либо резать, либо мельчить до
     * нечитаемого — поэтому карта здесь не копия печатной, а её читаемый вид.
     */
    private static final double CARD_RATIO = 1.24;
    /** Наклон между соседними картами веера, градусы. */
    private static final double FAN_STEP_DEG = 16;
    /** Максимальный разворот крайней карты веера, градусы. */
    private static final double FAN_MAX_DEG = 46;
    /**
     * Какую долю ширины панели веер имеет право занять. Больше — и он полезет на
     * место текущей карты, которое стоит по центру.
     */
    private static final double FAN_ROOM = 0.44;
    /**
     * Какую долю высоты компонента занимает ПОДЛОЖКА. Остальное сверху — запас,
     * в который карты законно выступают за её кромку.
     */
    private static final double BACK_FRACTION = 0.72;

    private final Session session;
    private int seat;
    private double open = 1.0;

    /**
     * ПЕРЕХОД МЕЖДУ РАСКЛАДКАМИ. {@code from} — где карты были, {@code to} — где
     * должны стоять, {@code t} — насколько переход прошёл (0..1). Пока t < 1,
     * каждая карта рисуется между своими двумя местами.
     */
    private Map<String, Slot> from = Map.of();
    private Map<String, Slot> to = Map.of();
    private double t = 1;
    private javax.swing.Timer move;
    /** По какому состоянию посчитана {@code to} — чтобы не пересчитывать зря. */
    private String stamp = "";
    /**
     * ИДЁТ ЛИ ВОСПРОИЗВЕДЕНИЕ. Панель не знает про пульт и не должна знать —
     * ей достаточно ответа «да/нет», который подставляет окно. Нужно, чтобы
     * анимировать только на плее: при шаге по кадрам руками движение мешает.
     */
    private java.util.function.BooleanSupplier playing;

    /** Место одной карты: где, какого размера, под каким углом и что за карта. */
    private record Slot(double x, double y, double w, double h, double angle,
                        boolean active, String top, String bottom, String tip) {
    }

    /** Что нарисовано и где — для подсказок под курсором. */
    private final List<Hit> hits = new ArrayList<>();

    /** Одна нарисованная карта: её область на экране и текст подсказки. */
    private record Hit(Shape area, String tip) {
    }

    public OrderCardsPanel(Session session, int seat) {
        this.session = session;
        this.seat = seat;
        setOpaque(false);
        setFont(Theme.body());
        ToolTipManager.sharedInstance().registerComponent(this);
        session.whenFrameChanged(s -> repaint());
        session.whenRecordChanged(s -> repaint());
    }

    public void setSeat(int seat) {
        this.seat = seat;
        repaint();
    }

    /** Доля выезда: 0 — панель спрятана, 1 — раскрыта полностью. */
    public void setOpen(double fraction) {
        this.open = Math.max(0, Math.min(1, fraction));
        repaint();
    }

    public double open() {
        return open;
    }

    /** Откуда узнавать, идёт ли воспроизведение (см. {@link #playing}). */
    public void setPlayingSource(java.util.function.BooleanSupplier source) {
        this.playing = source;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(320), Theme.px(132));
    }

    // ==================================================================
    //  ДАННЫЕ КАДРА
    // ==================================================================

    private ReplayRecord.Player me() {
        ReplayRecord.Frame f = session.frame();
        if (f == null || f.snapshot == null || seat >= f.snapshot.players.size()) {
            return null;
        }
        return f.snapshot.players.get(seat);
    }

    /**
     * ВСЁ ВСКРЫТОЕ ЗА ЭТОТ РАУНД, ПО ПОРЯДКУ КРУГОВ.
     *
     * <p>Берётся из {@code orderPlays}, а не из {@code orderPlayed}: в списке
     * игрока лежат только коды карт, а здесь есть и действия приказов, и
     * совпадение, и открытие низа — то, ради чего дизайнер и просил показывать
     * состояние. Порядок кругов заодно даёт правильную последовательность стопки.
     */
    private List<ReplayRecord.OrderPlay> revealed() {
        List<ReplayRecord.OrderPlay> out = new ArrayList<>();
        ReplayRecord rec = session.record();
        ReplayRecord.Frame f = session.frame();
        if (rec == null || f == null) {
            return out;
        }
        int idx = session.cursor();
        for (ReplayRecord.OrderPlay op : rec.orderPlays) {
            if (op.seat == seat && op.round == f.round && op.revealFrame <= idx) {
                out.add(op);
            }
        }
        out.sort((a, b) -> Integer.compare(a.circle, b.circle));
        return out;
    }

    /** Приказы карты, ещё лежащей в руке: {@code {верх, низ}} или null. */
    private String[] handOrders(String cardId) {
        try {
            Map<String, Object> c = session.content().get("orders").byId(cardId);
            if (Boolean.TRUE.equals(c.get("joker"))) {
                return new String[]{"joker", null};
            }
            Object bottom = c.get("bottom");
            return new String[]{String.valueOf(c.get("top")),
                bottom == null ? null : String.valueOf(bottom)};
        } catch (RuntimeException e) {
            // Каталог приказов может быть недоступен (запись открыта без правил).
            // Это не повод падать: покажем карту без приказов.
            return null;
        }
    }

    private Color deckColour(ReplayRecord.Player p) {
        if (p.orderColor == null || p.orderColor.isBlank()) {
            return Theme.tile();
        }
        return Names.orderDeckColour(p.orderColor);
    }

    // ==================================================================
    //  РИСОВАНИЕ
    // ==================================================================

    @Override
    protected void paintComponent(Graphics g0) {
        hits.clear();
        if (open <= 0.001) {
            return;
        }
        ReplayRecord.Player p = me();
        if (p == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();
        double pad = Theme.px(6);
        double backTop = h * (1 - BACK_FRACTION);
        double backH = h - backTop;

        // ВЫЕЗД ЦЕЛИКОМ, ВМЕСТЕ С ПОДЛОЖКОЙ: сдвиг ставится ДО неё, иначе фон
        // возникал бы скачком в полный размер, а карты подъезжали внутри него —
        // это читалось бы не как «выехала панель», а как «мигнул прямоугольник».
        g.translate(0, (1 - open) * h);
        float slide = (float) Math.min(1.0, 0.35 + 0.65 * open);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, slide));

        Shape back = new RoundRectangle2D.Double(0, backTop, w - 1, backH - 1,
            Theme.px(10), Theme.px(10));
        g.setColor(Theme.panel());
        g.fill(back);
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(back);

        Color deck = deckColour(p);
        // РАЗМЕР КАРТЫ С ЗАПАСОМ НА ПОВОРОТ. Карта выступает за кромку подложки,
        // но обязана остаться внутри компонента: у повёрнутой карты веера
        // ДАЛЬНИЙ ВЕРХНИЙ УГОЛ поднимается выше её собственного верха примерно на
        // полширины, умноженную на синус наклона, — и первые две сборки этот
        // угол срезали краем (найдено снимками 20.08.2026).
        //
        // Запас берётся от FAN_MAX_DEG, а не от текущего числа карт: карта в руке
        // появляется и уходит на ходу, и размер не должен от этого прыгать.
        // КАРТА ЗАМЕТНО ВЫШЕ ПОДЛОЖКИ (просьба дизайнера 20.08.2026: «карты
        // могут выезжать и быть даже выше краями, чем края своей панели»).
        // Потолок держит один запас сверху: рисование обрезается границей
        // КОМПОНЕНТА, и карта, выросшая до самого края, потеряла бы там угол.
        // Разворот веера в этот запас не лезет — он ограничен отдельно (FAN_ROOM).
        double cardH = Math.min(backH * 1.46, (h - 2 * pad) * 0.92);
        double cardW = cardH / CARD_RATIO;
        double cardTop = h - pad - cardH;

        Map<String, Slot> want = layout(p, w, h, cardTop, cardW, cardH, pad);
        syncTransition(want);

        // ПУСТОЕ МЕСТО ПОД ТЕКУЩУЮ КАРТУ. Круг может быть ещё не начат — тогда в
        // центре карты нет, и без метки панель выглядела бы поломанной.
        boolean anyActive = false;
        for (Slot s : want.values()) {
            anyActive |= s.active();
        }
        if (!anyActive) {
            Shape hole = new RoundRectangle2D.Double((w - cardW) / 2.0, cardTop,
                cardW, cardH, Theme.px(6), Theme.px(6));
            g.setColor(Theme.alpha(Theme.ink3(), 0.55));
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 0, new float[]{5, 5}, 0));
            g.draw(hole);
        }

        // КАРТЫ РИСУЮТСЯ МЕЖДУ ДВУМЯ РАСКЛАДКАМИ. Карта, которой в новой
        // раскладке нет, гаснет на месте, новая — проявляется: так уход в сброс и
        // приход новой карты видны, а не случаются мгновенно.
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>(from.keySet());
        ids.addAll(to.keySet());
        List<String> order = new ArrayList<>(ids);
        // Активная — последней, чтобы легла поверх веера и стопки.
        order.sort((a, b) -> Boolean.compare(isActive(a), isActive(b)));
        for (String id : order) {
            Slot a = from.get(id);
            Slot b = to.get(id);
            if (a == null && b == null) {
                continue;
            }
            Slot at = (a != null && b != null) ? lerp(a, b, t) : (b != null ? b : a);
            double fade = a == null ? t : (b == null ? 1 - t : 1);
            java.awt.Composite savedComp = g.getComposite();
            if (fade < 1) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    (float) Math.max(0, Math.min(1, fade)) * slide));
            }
            AffineTransform savedTr = g.getTransform();
            if (Math.abs(at.angle()) > 0.01) {
                g.rotate(Math.toRadians(at.angle()),
                    at.x() + at.w() / 2.0, h + at.h() * 0.42);
            }
            drawCard(g, at.x(), at.y(), at.w(), at.h(), deck, at.active(),
                at.top(), at.bottom(), at.tip());
            g.setTransform(savedTr);
            g.setComposite(savedComp);
        }
        g.dispose();
    }

    private boolean isActive(String id) {
        Slot s = to.get(id);
        return s != null && s.active();
    }

    /** Промежуточное место карты; сглаживание, чтобы движение не было рывком. */
    private static Slot lerp(Slot a, Slot b, double t) {
        double k = t * t * (3 - 2 * t);
        return new Slot(a.x() + (b.x() - a.x()) * k, a.y() + (b.y() - a.y()) * k,
            a.w() + (b.w() - a.w()) * k, a.h() + (b.h() - a.h()) * k,
            a.angle() + (b.angle() - a.angle()) * k,
            b.active(), b.top(), b.bottom(), b.tip());
    }

    /**
     * РАСКЛАДКА: где какая карта стоит при текущем кадре.
     *
     * <p>Слева направо — рука, текущая, сброс (решение дизайнера 20.08.2026): так
     * карта едет в ту же сторону, в какую читают, и движение «из руки в центр, из
     * центра в сброс» понятно без объяснений.
     */
    private Map<String, Slot> layout(ReplayRecord.Player p, int w, int h,
                                     double cardTop, double cardW, double cardH,
                                     double pad) {
        Map<String, Slot> out = new java.util.LinkedHashMap<>();
        List<ReplayRecord.OrderPlay> seen = revealed();
        ReplayRecord.OrderPlay cur = seen.isEmpty() ? null : seen.get(seen.size() - 1);

        // ---------- слева: рука веером ----------
        List<String> hand = p.orderHand;
        int n = hand.size();
        if (n > 0) {
            double radius = h + cardH * 0.42 - cardTop;
            // ВЕЕР НЕ ДОЛЖЕН ЗАЛЕЗАТЬ НА МЕСТО ТЕКУЩЕЙ КАРТЫ (найдено снимком
            // полной руки 20.08.2026: при шести картах веер доходил до 281-го
            // столбца из 470, то есть накрывал центр). Ограничение считается, а
            // не подбирается на глаз: веер занимает
            //     pad + cardW + 2 * lean,   где lean = radius * sin(разворот),
            // значит из отведённой ему доли ширины прямо следует предельный
            // разворот. Так веер сам поджимается, когда карт много, вместо того
            // чтобы вылезать.
            double room = w * FAN_ROOM - pad - cardW;
            double maxLean = Math.max(0, room / 2.0);
            double capDeg = Math.toDegrees(Math.asin(
                Math.max(0, Math.min(1, maxLean / Math.max(1, radius)))));
            double halfSpread = Math.min(FAN_MAX_DEG, capDeg);
            double step = n <= 1 ? 0
                : Math.min(FAN_STEP_DEG, halfSpread * 2.0 / (n - 1));
            double first = -step * (n - 1) / 2.0;
            double maxAngle = Math.toRadians(Math.max(Math.abs(first),
                Math.abs(first + step * (n - 1))));
            double lean = radius * Math.sin(maxAngle);
            double pivotX = pad + cardW / 2.0 + lean;
            for (int i = 0; i < n; i++) {
                String[] orders = handOrders(hand.get(i));
                out.put(hand.get(i), new Slot(pivotX - cardW / 2.0, cardTop, cardW, cardH,
                    first + step * i, false,
                    orders == null ? null : orders[0],
                    orders == null ? null : orders[1],
                    tipForHand(orders)));
            }
        }

        // ---------- по центру: карта этого круга ----------
        if (cur != null) {
            out.put(cur.card, new Slot((w - cardW) / 2.0, cardTop - Theme.px(6),
                cardW, cardH + Theme.px(6), 0, true, cur.top, cur.bottom,
                tipFor(cur, "Круг " + cur.circle + " — играется сейчас")));
        }

        // ---------- справа: сброс стопкой, в порядке кругов ----------
        int done = Math.max(0, seen.size() - 1);
        double stackStep = Theme.px(13);
        double right = w - pad - cardW;
        for (int i = 0; i < done; i++) {
            ReplayRecord.OrderPlay op = seen.get(i);
            // Первая сыгранная — дальше всех вправо, последняя ближе к центру:
            // стопка растёт в ту же сторону, куда карты и уходят.
            double x = right - (done - 1 - i) * stackStep;
            out.put(op.card, new Slot(x, cardTop, cardW, cardH, 0, false,
                op.top, op.bottom, tipFor(op, "Сброс, круг " + op.circle)));
        }
        return out;
    }

    /**
     * Заметить смену состояния и завести переход.
     *
     * <p>АНИМАЦИЯ ТОЛЬКО НА ВОСПРОИЗВЕДЕНИИ (решение дизайнера 20.08.2026). Когда
     * разбор стоит и человек шагает по кадрам руками, движение только мешает: шаг
     * должен показывать состояние сразу, а не догонять его полсекунды.
     */
    private void syncTransition(Map<String, Slot> want) {
        StringBuilder mark = new StringBuilder();
        for (Map.Entry<String, Slot> e : want.entrySet()) {
            mark.append(e.getKey()).append(':').append(e.getValue().active())
                .append(',').append(Math.round(e.getValue().x()))
                .append(',').append(Math.round(e.getValue().angle())).append(';');
        }
        String now = mark.toString();
        if (now.equals(stamp)) {
            to = want;
            return;
        }
        stamp = now;
        if (playing == null || !playing.getAsBoolean()) {
            from = want;
            to = want;
            t = 1;
            return;
        }
        // Отсчёт с ТЕКУЩЕГО нарисованного положения, а не с прошлой цели: иначе
        // карта, застигнутая на полпути, прыгнула бы назад и поехала заново.
        Map<String, Slot> mid = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Slot> e : to.entrySet()) {
            Slot a = from.get(e.getKey());
            mid.put(e.getKey(), a == null ? e.getValue() : lerp(a, e.getValue(), t));
        }
        from = mid;
        to = want;
        t = 0;
        if (move == null) {
            move = new javax.swing.Timer(16, e -> {
                t = Math.min(1, t + 0.09);
                if (t >= 1) {
                    from = to;
                    ((javax.swing.Timer) e.getSource()).stop();
                }
                repaint();
            });
        }
        move.restart();
    }


    /**
     * Одна карта приказа: подложка цвета колоды, рамка и ДВА приказа — верхний и
     * нижний (просьба дизайнера 20.08.2026).
     *
     * <p>ДВА ЗАЛИВА, А НЕ ОДИН ПОЛУПРОЗРАЧНЫЙ. Сперва плотный цвет панели, потом
     * поверх — оттенок колоды. Одной полупрозрачной заливкой цвета колоды карта
     * стала бы просвечивать, а карты ВЫСТУПАЮТ за кромку подложки — сквозь них
     * читались бы гексы поля, и ни цвет, ни подписи разобрать было бы нельзя.
     *
     * <p>Верх и низ разделены чертой и подписаны разным весом: верхний приказ
     * играется всегда, нижний — только когда открылся, и путать их нельзя.
     */
    private void drawCard(Graphics2D g, double x, double y, double w, double h,
                          Color deck, boolean active, String top, String bottom,
                          String tip) {
        Shape card = new RoundRectangle2D.Double(x, y, w, h, Theme.px(6), Theme.px(6));
        g.setColor(Theme.panel());
        g.fill(card);
        g.setColor(Theme.alpha(deck, active ? 0.42 : 0.24));
        g.fill(card);
        g.setColor(active ? Theme.accent() : Theme.alpha(deck, 0.85));
        g.setStroke(new BasicStroke(active ? 2.2f : 1.2f));
        g.draw(card);

        // ЧЕРТА МЕЖДУ ВЕРХОМ И НИЗОМ — там же, где она на печатной карте.
        double split = y + h * 0.56;
        g.setColor(Theme.alpha(deck, 0.7));
        g.setStroke(new BasicStroke(1f));
        g.draw(new java.awt.geom.Line2D.Double(x + Theme.px(3), split,
            x + w - Theme.px(3), split));

        // ПОДПИСИ ГОРИЗОНТАЛЬНО — верх карты и низ карты, каждый в своей половине.
        drawAcross(g, top == null ? "?" : Names.order(top), x, y, w, split - y,
            active ? Font.BOLD : Font.PLAIN, active ? 11 : 10, Theme.ink());
        drawAcross(g, bottom == null ? "нет" : Names.order(bottom), x, split, w,
            y + h - split, Font.PLAIN, 9, Theme.ink3());

        hits.add(new Hit(g.getTransform().createTransformedShape(card), tip));
    }

    /**
     * НАДПИСЬ ГОРИЗОНТАЛЬНО, в отведённой половине карты.
     *
     * <p>Раньше текст был повёрнут на 90 градусов и читался снизу вверх, как
     * корешок книги: так он влезал на узкую карту, но читать панель приходилось,
     * наклоняя голову (замечание дизайнера 20.08.2026). Теперь карта шире, а
     * длинное название переносится по слогам на две строки — резать его
     * многоточием на видном месте хуже, чем перенести.
     */
    private void drawAcross(Graphics2D g, String text, double x, double top,
                            double w, double h, int style, int size, Color ink) {
        g.setFont(Theme.font(size, style));
        g.setColor(ink);
        var fm = g.getFontMetrics();
        int maxW = (int) (w - Theme.px(8));
        java.util.List<String> lines = split(fm, text, maxW, 2);
        double lineH = fm.getHeight() * 0.92;
        double startY = top + (h - lines.size() * lineH) / 2 + fm.getAscent() * 0.92;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            g.drawString(line, (float) (x + (w - fm.stringWidth(line)) / 2),
                (float) (startY + i * lineH));
        }
    }

    /**
     * Разбить название на строки под ширину карты.
     *
     * <p>Приказы — ОДНО слово, поэтому переносить по пробелам нечего: рвём по
     * буквам с дефисом, как переносят в книге. Если и так не влезает — последняя
     * строка кончается многоточием, чтобы обрезка была видна.
     */
    private static java.util.List<String> split(java.awt.FontMetrics fm, String text,
                                                int maxW, int maxLines) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String rest = text == null ? "" : text;
        while (!rest.isEmpty() && out.size() < maxLines) {
            if (fm.stringWidth(rest) <= maxW) {
                out.add(rest);
                return out;
            }
            if (out.size() == maxLines - 1) {
                String cut = rest;
                while (cut.length() > 1 && fm.stringWidth(cut + "…") > maxW) {
                    cut = cut.substring(0, cut.length() - 1);
                }
                out.add(cut + "…");
                return out;
            }
            int take = rest.length();
            while (take > 1 && fm.stringWidth(rest.substring(0, take) + "-") > maxW) {
                take--;
            }
            out.add(rest.substring(0, take) + "-");
            rest = rest.substring(take);
        }
        if (out.isEmpty()) {
            out.add(rest);
        }
        return out;
    }

    // ==================================================================
    //  ПОДСКАЗКИ — С ДЕЙСТВИЯМИ И СОСТОЯНИЕМ
    // ==================================================================

    /**
     * Подсказка вскрытой карты: что за приказы, какие у них действия и в каком
     * карта состоянии — заблокирован ли верх и можно ли играть низ.
     *
     * <p>Именно СОСТОЯНИЕ дизайнер и просил показывать: по самой карте не понять,
     * сработал ли верхний приказ, — он молча пропадает, если такой же приказ
     * вскрыл кто-то ещё за столом.
     */
    private String tipFor(ReplayRecord.OrderPlay op, String head) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(head).append("</b>");
        sb.append("<br>Верх: <b>").append(Names.order(op.top)).append("</b>");
        if (!op.topActions.isEmpty()) {
            sb.append(" — ").append(actions(op.topActions));
        }
        if (op.coincided) {
            sb.append("<br><b>ВЕРХ ЗАБЛОКИРОВАН</b>: тот же приказ вскрыл кто-то ещё");
        } else {
            sb.append("<br>верх сработал, действий разрешено ").append(op.topAllowed);
        }
        if (op.bottom != null) {
            sb.append("<br>Низ: ").append(Names.order(op.bottom));
            if (!op.bottomActions.isEmpty()) {
                sb.append(" — ").append(actions(op.bottomActions));
            }
            sb.append(op.bottomOpen ? "<br>низ ОТКРЫТ — его можно играть"
                : "<br>низ закрыт");
        } else {
            sb.append("<br>нижнего приказа у карты нет");
        }
        if (op.maneuver) {
            sb.append("<br>карта-манёвр");
        }
        return sb.toString();
    }

    /** Подсказка карты в руке: приказы известны, состояния ещё нет. */
    private String tipForHand(String[] orders) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>В руке</b>");
        if (orders == null) {
            sb.append("<br>приказы неизвестны: каталог приказов недоступен");
            return sb.toString();
        }
        sb.append("<br>Верх: <b>").append(Names.order(orders[0])).append("</b>");
        sb.append(orders[1] == null ? "<br>нижнего приказа у карты нет"
            : "<br>Низ: " + Names.order(orders[1]));
        sb.append("<br><i>ещё не вскрыта: сработает ли верх, зависит от того, не "
            + "вскроет ли тот же приказ кто-то ещё</i>");
        return sb.toString();
    }

    private static String actions(List<String> codes) {
        List<String> out = new ArrayList<>();
        for (String c : codes) {
            out.add(Names.action(c));
        }
        return String.join(", ", out);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        // ПОСЛЕДНЯЯ НАРИСОВАННАЯ ПОБЕЖДАЕТ: карты веера и стопки перекрывают
        // друг друга, и под курсором должна оказаться та, что лежит СВЕРХУ.
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).area().contains(e.getX(), e.getY())) {
                return "<html><div style='width:280px'>" + hits.get(i).tip() + "</div></html>";
            }
        }
        return null;
    }

    @Override
    public java.awt.Point getToolTipLocation(MouseEvent e) {
        // Подсказка в стороне от курсора: иначе она накрывает ту самую карту, о
        // которой рассказывает.
        return new java.awt.Point(e.getX() + Theme.px(14), e.getY() - Theme.px(10));
    }
}
