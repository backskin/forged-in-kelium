package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * ЗОНА ИГРОКА — его часть стола, собранная из компонентов (просьба дизайнера
 * 25.09.2026: «никаких кнопок в углах; у игрока должна быть зона — красивая, с
 * картами, которая раскрывается; я хочу взаимодействовать со своими
 * компонентами»).
 *
 * <p>Что лежит перед игроком — так же, как на столе:
 * <ul>
 *   <li><b>планшеты</b> хранилища и войск сцепкой, с живыми жетонами; в
 *       центре хранилища — три места под жетоны хранилища; в пазах под
 *       планшетом войск — установленный арсенал лицом и контейнеры;</li>
 *   <li><b>стопка закрытого арсенала</b> торчит из-под планшета хранилища —
 *       сколько угодно карт; щелчок раскрывает их;</li>
 *   <li><b>свалка</b> — отложенный приказ рубашкой, повёрнутый на четверть
 *       оборота против часовой, на нём лежат уничтоженные жетоны врагов
 *       трофейной стороной;</li>
 *   <li><b>вскрытый приказ круга</b> — действия нажимаются прямо по кругам
 *       на карте, рядом — «Завершить ход»;</li>
 *   <li><b>руки веером</b>: задания, супер-задания, приказы в руке; щелчок по
 *       вееру раскрывает карты крупно ({@link CardSpread}), там и действия с
 *       ними.</li>
 * </ul>
 *
 * <p>Что сейчас можно выбрать, задаёт окно одним списком «ключ → варианты»
 * ({@link #setChoices}): {@code action:build}, {@code end},
 * {@code card:o12}, {@code building:miner:2}, {@code red:infantry}… Деталь
 * планшета и действие на карте с одним вариантом играются щелчком сразу;
 * карты — через раскрытие руки.
 */
public final class PlayerTable extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Кто рисует печатные планшеты (их умеет лист игрока проигрывателя). */
    public interface BoardsArt {
        /** Ширина к высоте; 0 — печатных планшетов нет. */
        double aspect();

        /** Где в сцепке планшет хранилища: {@code [левый край, ширина, низ]} долями. */
        double[] storageBox();

        /** Нарисовать и записать зоны щелчка и контуры деталей; вернуть высоту. */
        int paint(Graphics2D g, int x, int y, int width, Map<String, Rectangle> hits,
                  Map<String, Shape> outlines);
    }

    /** Трофей на свалке: чей жетон и какой стороной лежит. */
    public record Trophy(BufferedImage face, int value) {
    }

    /**
     * Что лежит перед игроком.
     *
     * @param orderId  вскрытый в этом круге приказ; {@code null} — ещё не вскрыт
     * @param orderArt печатное лицо приказа; {@code null} — рисуем сами
     * @param dumpBack рубашка отложенного приказа (свалки); {@code null} — свалки нет
     */
    public record State(int seat, String seatName, String orderId, OrderCardFace.Info orderInfo,
                        BufferedImage orderArt, List<String> objectives,
                        List<String> superObjectives, List<String> arsenalHand,
                        List<String> arsenalInstalled, List<String> ordersInHand,
                        List<String> ordersPlayed, BufferedImage dumpBack,
                        List<Trophy> dump, int dumpValue, String status) {
    }

    private BoardsArt boards;
    private State state;
    private Function<String, BufferedImage> faceOf = id -> null;
    private Function<String, BufferedImage> backOf = kind -> null;
    private Function<String, String> nameOf = id -> id;
    private Function<String, String> tagOf = id -> null;
    private BiConsumer<String, Rectangle> onHoverCard = (id, r) -> { };
    private Runnable onHoverOff = () -> { };
    private Consumer<String> onOpen = group -> { };

    private final FieldBubbles bubbles = new FieldBubbles();
    private Map<String, List<FieldBubbles.Opt>> choices = Map.of();
    private Color seatColor = Theme.accent();

    /** Где что лежит после последней отрисовки. */
    private final Map<String, Rectangle> spots = new LinkedHashMap<>();
    private final Map<String, Shape> outlines = new LinkedHashMap<>();
    /** Карты вееров — повёрнутые, в порядке рисования (верхняя — последняя). */
    private final List<Object[]> fanCards = new ArrayList<>();
    /** Группы, которые раскрываются щелчком: ключ группы → место. */
    private final Map<String, Shape> groups = new LinkedHashMap<>();
    private String hoverKey;
    private String hoverCard;
    private String hoverGroup;
    private boolean dirty = true;

    public PlayerTable() {
        setOpaque(true);
        setFont(Theme.body());
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                boolean repaint = bubbles.hover(p);
                boolean overBubble = bubbles.covers(p);
                String card = overBubble ? null : fanCardAt(p);
                String key = overBubble || card != null ? null : keyAt(p);
                String group = overBubble ? null : groupAt(p);
                if (!java.util.Objects.equals(key, hoverKey)) {
                    hoverKey = key;
                    repaint = true;
                }
                if (!java.util.Objects.equals(group, hoverGroup)) {
                    hoverGroup = group;
                    repaint = true;
                }
                if (!java.util.Objects.equals(card, hoverCard)) {
                    hoverCard = card;
                    repaint = true;
                    if (card == null) {
                        onHoverOff.run();
                    } else {
                        onHoverCard.accept(card, fanRect(card));
                    }
                }
                boolean hand = bubbles.hovering() || group != null
                    || key != null && choices.containsKey(key);
                setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
                if (repaint) {
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverKey = null;
                hoverGroup = null;
                if (hoverCard != null) {
                    hoverCard = null;
                    onHoverOff.run();
                }
                bubbles.hover(null);
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                Point p = e.getPoint();
                if (bubbles.covers(p)) {
                    FieldBubbles.Opt o = bubbles.optAt(p);
                    if (o != null && o.pick() != null) {
                        o.pick().run();
                    }
                    repaint();
                    return;
                }
                String group = groupAt(p);
                if (group != null) {
                    onHoverOff.run();
                    onOpen.accept(group);
                    return;
                }
                String key = keyAt(p);
                if (key != null && choices.containsKey(key)) {
                    bubbles.clickHex(key);
                } else {
                    bubbles.closeBubble();
                }
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setBoards(BoardsArt art) {
        this.boards = art;
        repaint();
    }

    /**
     * Откуда брать картинки и подписи.
     *
     * @param backOf рубашка по виду: {@code arsenal}, {@code objective},
     *               {@code order:<цвет>}
     */
    public void setCards(Function<String, BufferedImage> faceOf,
                         Function<String, BufferedImage> backOf,
                         Function<String, String> nameOf, Function<String, String> tagOf) {
        this.faceOf = faceOf;
        this.backOf = backOf;
        this.nameOf = nameOf;
        this.tagOf = tagOf;
    }

    public void onCardHover(BiConsumer<String, Rectangle> over, Runnable off) {
        this.onHoverCard = over;
        this.onHoverOff = off;
    }

    /**
     * Щелчок по группе карт: {@code objectives}, {@code super},
     * {@code arsenal}, {@code orders}, {@code dump}, {@code installed}.
     */
    public void onOpen(Consumer<String> open) {
        this.onOpen = open;
    }

    public void setState(State s) {
        this.state = s;
        dirty = true;
        repaint();
    }

    public State state() {
        return state;
    }

    /** Что можно выбрать на столе сейчас (пусто — ничего). */
    public void setChoices(Map<String, List<FieldBubbles.Opt>> byKey, Color seatColor) {
        this.choices = byKey == null ? Map.of() : new LinkedHashMap<>(byKey);
        this.seatColor = seatColor == null ? Theme.accent() : seatColor;
        // Пузырь — только у деталей планшета; карты раскрываются рукой.
        Map<String, List<FieldBubbles.Opt>> board = new LinkedHashMap<>();
        for (var e : this.choices.entrySet()) {
            if (!e.getKey().startsWith("card:")) {
                board.put(e.getKey(), e.getValue());
            }
        }
        bubbles.set(board, null, null, null, this.seatColor);
        dirty = true;
        repaint();
    }

    /** Варианты по ключу (для раскрытой руки). */
    public List<FieldBubbles.Opt> choicesFor(String key) {
        return choices.getOrDefault(key, List.of());
    }

    public Map<String, List<FieldBubbles.Opt>> choicesForTest() {
        return Map.copyOf(choices);
    }

    public void clearChoices() {
        setChoices(null, null);
    }

    public void closeBubble() {
        bubbles.closeBubble();
        repaint();
    }

    /**
     * Нарисована ли деталь с таким ключом. Зоны щелчка знает только
     * отрисовка, а решение приходит раньше перерисовки — поэтому, если стол
     * менялся после последней отрисовки, он прорисовывается в пустую картинку.
     */
    public boolean hasSpot(String key) {
        ensurePainted();
        return spots.containsKey(key);
    }

    /** Лежит ли карта в одной из раскрываемых групп (руки, стопка, пазы). */
    public boolean hasCard(String id) {
        State s = state;
        return s != null && (s.objectives().contains(id) || s.superObjectives().contains(id)
            || s.arsenalHand().contains(id) || s.arsenalInstalled().contains(id)
            || s.ordersInHand().contains(id));
    }

    private void ensurePainted() {
        if (dirty && getWidth() > 0 && getHeight() > 0) {
            BufferedImage scratch = new BufferedImage(getWidth(), getHeight(),
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scratch.createGraphics();
            paintComponent(g);
            g.dispose();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(1200), Theme.px(320));
    }

    private String keyAt(Point p) {
        String best = null;
        long bestArea = Long.MAX_VALUE;
        for (Map.Entry<String, Rectangle> e : spots.entrySet()) {
            Rectangle r = e.getValue();
            if (r.contains(p)) {
                long a = (long) r.width * r.height;
                if (choices.containsKey(e.getKey())) {
                    a /= 4;
                }
                if (a < bestArea) {
                    bestArea = a;
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    private String groupAt(Point p) {
        String hit = null;
        for (Map.Entry<String, Shape> e : groups.entrySet()) {
            if (e.getValue().contains(p)) {
                hit = e.getKey();
            }
        }
        return hit;
    }

    private String fanCardAt(Point p) {
        for (int i = fanCards.size() - 1; i >= 0; i--) {
            if (((Shape) fanCards.get(i)[1]).contains(p)) {
                return (String) fanCards.get(i)[0];
            }
        }
        return null;
    }

    private Rectangle fanRect(String id) {
        for (Object[] f : fanCards) {
            if (f[0].equals(id)) {
                return ((Shape) f[1]).getBounds();
            }
        }
        return null;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (bubbles.covers(e.getPoint())) {
            return bubbles.tipAt(e.getPoint());
        }
        String key = keyAt(e.getPoint());
        List<FieldBubbles.Opt> opts = key == null ? null : choices.get(key);
        if (opts != null && opts.size() == 1) {
            FieldBubbles.Opt o = opts.get(0);
            return o.sub() == null ? o.label() : o.label() + " — " + o.sub();
        }
        String g = groupAt(e.getPoint());
        if (g != null) {
            return switch (g) {
                case "arsenal" -> "Закрытые карты арсенала — щелчок раскрывает";
                case "dump" -> "Свалка: уничтоженные жетоны врагов на отложенном приказе";
                case "orders" -> "Приказы в руке — щелчок раскрывает";
                default -> "Щелчок раскрывает карты";
            };
        }
        return null;
    }

    // ==================== отрисовка ====================

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        kelium.report.Сглаживание.включить(g);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int w = getWidth();
        int h = getHeight();
        dirty = false;
        spots.clear();
        outlines.clear();
        fanCards.clear();
        groups.clear();
        paintMat(g, w, h);
        if (state == null) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("зона игрока появится, когда партия начнётся", Theme.px(24), Theme.px(34));
            g.dispose();
            return;
        }
        int pad = Theme.px(12);
        int top = pad + Theme.px(4);
        int innerH = h - top - pad;
        int x = pad + Theme.px(8);

        // ---- планшеты и стопка арсенала под хранилищем
        if (boards != null && boards.aspect() > 0) {
            int bandH = (int) (innerH * 0.17);      // полоса под стопку арсенала
            int bh = innerH - bandH;
            int bw = (int) Math.min(w * 0.50, bh * boards.aspect());
            bh = (int) Math.round(bw / boards.aspect());
            int by = top;
            double[] sb = boards.storageBox();
            int sx = x + (int) (sb[0] * bw);
            int sw = (int) (sb[1] * bw);
            int sBottom = by + (int) (sb[2] * bh);
            paintArsenalStack(g, sx, sw, sBottom, top + innerH);
            Map<String, Rectangle> hits = new LinkedHashMap<>();
            boards.paint(g, x, by, bw, hits, outlines);
            spots.putAll(hits);
            for (String k : hits.keySet()) {
                if (k.startsWith("installed:")) {
                    groups.put("installed", hits.get(k));
                }
            }
            x += bw + Theme.px(18);
        }

        // ---- свалка — отложенный приказ, лёжа
        int dumpW = (int) Math.round(innerH * 0.66);
        int dumpH = (int) Math.round(dumpW * 661 / 1028.0);
        paintDump(g, x, top + (innerH - dumpH) / 2 - Theme.px(6), dumpW, dumpH);
        x += dumpW + Theme.px(18);

        // ---- вскрытый приказ круга и «Завершить ход»
        int cardH = innerH;
        int cardW = (int) Math.round(cardH * 661 / 1028.0);
        paintOrder(g, x, top, cardW, cardH);
        x += cardW + Theme.px(6);
        int endW = Theme.px(104);
        paintEnd(g, x, top, endW, cardH);
        x += endW + Theme.px(22);

        // ---- руки веером
        paintHands(g, x, top, w - x - pad, innerH);

        // ---- обводка всего, что можно выбрать
        for (String key : choices.keySet()) {
            Rectangle r = spots.get(key);
            if (r == null || key.startsWith("action:") || "end".equals(key)
                    || key.startsWith("card:")) {
                continue;
            }
            boolean hot = key.equals(hoverKey);
            Shape outline = outlines.get(key);
            Shape glow = outline != null ? outline
                : new RoundRectangle2D.Double(r.x - 3, r.y - 3, r.width + 6, r.height + 6,
                    Theme.px(10), Theme.px(10));
            g.setColor(Theme.alpha(seatColor, hot ? 0.28 : 0.12));
            g.fill(glow);
            g.setColor(seatColor);
            g.setStroke(new BasicStroke(hot ? Theme.pxf(3) : Theme.pxf(2),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(glow);
        }

        bubbles.paint(g, w, h, key -> {
            Rectangle r = spots.get(key);
            return r == null ? null : new Point2D.Double(r.getCenterX(), r.getCenterY());
        }, Theme.px(30));
        g.dispose();
    }

    /** Коврик зоны игрока: тёплая подложка стола и полоса цвета места. */
    private void paintMat(Graphics2D g, int w, int h) {
        Color base = Theme.isDark() ? new Color(0x1A1E24) : new Color(0xE9E4DA);
        Color deep = Theme.isDark() ? new Color(0x12151A) : new Color(0xD9D2C4);
        g.setPaint(new GradientPaint(0, 0, base, 0, h, deep));
        g.fillRect(0, 0, w, h);
        g.setColor(Theme.alpha(Color.BLACK, Theme.isDark() ? 0.5 : 0.18));
        g.fillRect(0, 0, w, Theme.px(2));
        if (state != null) {
            Color seat = Theme.seat(state.seat());
            g.setColor(seat);
            g.fillRect(0, 0, Theme.px(6), h);
            g.setColor(Theme.alpha(seat, 0.08));
            g.fillRect(Theme.px(6), 0, w, h);
        }
    }

    // ---------- стопка закрытого арсенала ----------

    /**
     * СТОПКА ЗАКРЫТОГО АРСЕНАЛА — торчит из-под нижней кромки планшета
     * хранилища, рисуется РАНЬШЕ планшетов: планшет лежит поверх неё. Сколько
     * карт — столько и видно (до шести, дальше цифрой).
     */
    private void paintArsenalStack(Graphics2D g, int sx, int sw, int boardBottom, int bottom) {
        List<String> hand = state.arsenalHand();
        int visible = Math.max(Theme.px(30), bottom - boardBottom);
        int ch = (int) Math.round(visible / 0.52);
        int cw = (int) Math.round(ch * 0.69);
        int cx = sx + sw / 2;
        int y0 = bottom - ch;
        int n = Math.min(6, hand.size());
        int spread = Theme.px(14);
        int total = cw + Math.max(0, n - 1) * spread;
        int x0 = cx - total / 2;
        Rectangle area = new Rectangle(x0 - Theme.px(4), boardBottom - Theme.px(6),
            Math.max(total, cw) + Theme.px(8), bottom - boardBottom + Theme.px(6));
        if (hand.isEmpty()) {
            // Пусто — тихой подписью под кромкой: место не должно кричать.
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            centred(g, "закрытого арсенала нет", cx, bottom - Theme.px(6));
            return;
        }
        BufferedImage back = backOf.apply("arsenal");
        boolean chosen = hand.stream().anyMatch(id -> choices.containsKey("card:" + id));
        boolean hot = "arsenal".equals(hoverGroup);
        for (int i = 0; i < n; i++) {
            int x = x0 + i * spread;
            int y = y0 - (hot ? Theme.px(10) : 0) + (i % 2) * Theme.px(2);
            paintBack(g, back, x, y, cw, ch, Theme.container());
        }
        if (chosen) {
            g.setColor(seatColor);
            g.setStroke(new BasicStroke(Theme.pxf(2.6)));
            g.draw(new RoundRectangle2D.Double(x0 - 3, y0 - 3 - (hot ? Theme.px(10) : 0),
                total + 6, ch + 6, cw * 0.1, cw * 0.1));
        }
        badge(g, x0 + total + Theme.px(4), bottom - Theme.px(18),
            "арсенал · " + hand.size(), chosen ? seatColor : Theme.container());
        groups.put("arsenal", area);
    }

    // ---------- свалка ----------

    /**
     * СВАЛКА — отложенный в начале раунда приказ, рубашкой вверх, повёрнут на
     * четверть оборота против часовой. На нём лежат уничтоженные жетоны
     * трофейной стороной; под ним — сколько они стоят.
     */
    private void paintDump(Graphics2D g, int x, int y, int w, int h) {
        g.setFont(Theme.caption());
        g.setColor(Theme.ink3());
        g.drawString("СВАЛКА", x, y - Theme.px(6));
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, h * 0.08, h * 0.08);
        if (state.dumpBack() == null) {
            g.setColor(Theme.alpha(Theme.ink3(), 0.6));
            g.setStroke(new BasicStroke(Theme.pxf(1.3), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{Theme.pxf(5), Theme.pxf(4)}, 0f));
            g.draw(shape);
            g.setFont(Theme.font(10, Font.PLAIN));
            centred(g, "приказ ещё не отложен", x + w / 2, y + h / 2 + Theme.px(4));
            return;
        }
        g.setColor(Theme.alpha(Color.BLACK, 0.22));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 4, w, h, h * 0.08, h * 0.08));
        // рубашка повёрнута на 90° против часовой: вписываем повёрнутую картинку
        BufferedImage back = state.dumpBack();
        java.awt.Shape clip = g.getClip();
        g.clip(shape);
        AffineTransform at = new AffineTransform();
        at.translate(x, y + h);
        at.rotate(-Math.PI / 2);
        at.scale(h / (double) back.getWidth(), w / (double) back.getHeight());
        g.drawImage(back, at, null);
        g.setClip(clip);
        g.setColor(Theme.alpha(Color.BLACK, 0.3));
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);
        // трофеи на карте
        List<Trophy> tokens = state.dump();
        if (!tokens.isEmpty()) {
            int cols = Math.min(4, tokens.size());
            int rows = (tokens.size() + cols - 1) / cols;
            double cell = Math.min((w - Theme.px(16)) / (double) cols,
                (h - Theme.px(16)) / (double) rows);
            double gx = x + (w - cols * cell) / 2;
            double gy = y + (h - rows * cell) / 2;
            for (int i = 0; i < tokens.size(); i++) {
                Trophy t = tokens.get(i);
                double cx = gx + (i % cols + 0.5) * cell;
                double cy = gy + (i / cols + 0.5) * cell;
                if (t.face() != null) {
                    double k = cell * 0.92 / Math.max(t.face().getWidth(), t.face().getHeight());
                    AffineTransform tt = new AffineTransform();
                    tt.translate(cx, cy);
                    tt.rotate(Math.toRadians((i * 37) % 30 - 15));
                    tt.scale(k, k);
                    tt.translate(-t.face().getWidth() / 2.0, -t.face().getHeight() / 2.0);
                    g.drawImage(t.face(), tt, null);
                } else {
                    g.setColor(Theme.trophy());
                    g.fill(new Ellipse2D.Double(cx - cell * 0.3, cy - cell * 0.3,
                        cell * 0.6, cell * 0.6));
                }
            }
        }
        String cap = tokens.isEmpty() ? "пусто"
            : tokens.size() + " жет. · трофеев " + state.dumpValue();
        g.setFont(Theme.font(10, Font.BOLD));
        g.setColor(Theme.ink2());
        centred(g, cap, x + w / 2, y + h + Theme.px(14));
        groups.put("dump", shape);
        if ("dump".equals(hoverGroup)) {
            g.setColor(Theme.alpha(seatColor, 0.8));
            g.setStroke(new BasicStroke(Theme.pxf(2)));
            g.draw(shape);
        }
    }

    // ---------- вскрытый приказ ----------

    /**
     * Точки действий на ПЕЧАТНОЙ карте приказа: два круга в верхней половине,
     * два в нижней — так нарисованы все карты набора «симметрия».
     */
    private static final double[][] SPOTS = {{0.245, 0.335}, {0.735, 0.335},
        {0.245, 0.80}, {0.735, 0.80}};

    /**
     * «Затаиться» (бывшая безопасность): восемь действий сеткой 2×4 — места
     * кругов сняты с печати.
     */
    private static final double[][] JOKER = {{0.27, 0.28}, {0.73, 0.28}, {0.27, 0.46},
        {0.73, 0.46}, {0.27, 0.645}, {0.73, 0.645}, {0.27, 0.83}, {0.73, 0.83}};

    private void paintOrder(Graphics2D g, int x, int y, int w, int h) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, w * 0.08, w * 0.08);
        if (state.orderId() == null) {
            g.setColor(Theme.alpha(Theme.ink3(), 0.7));
            g.setStroke(new BasicStroke(Theme.pxf(1.5), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{Theme.pxf(6), Theme.pxf(5)}, 0f));
            g.draw(shape);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.setColor(Theme.ink3());
            centred(g, "приказ круга", x + w / 2, y + h / 2 - Theme.px(6));
            centred(g, "ещё не вскрыт", x + w / 2, y + h / 2 + Theme.px(10));
            return;
        }
        g.setColor(Theme.alpha(Color.BLACK, 0.22));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 5, w, h, w * 0.08, w * 0.08));
        if (state.orderArt() != null) {
            java.awt.Shape clip = g.getClip();
            g.clip(shape);
            g.drawImage(state.orderArt(), x, y, w, h, null);
            g.setClip(clip);
        } else if (state.orderInfo() != null) {
            paintOrderBack(g, state.orderInfo(), shape, x, y, w, h);
        }
        spots.put("order", new Rectangle(x, y, w, h));

        List<String> actions = orderActions();
        boolean deciding = choices.keySet().stream().anyMatch(k -> k.startsWith("action:"));
        boolean joker = state.orderInfo() != null && state.orderInfo().joker();
        for (int i = 0; i < actions.size(); i++) {
            String a = actions.get(i);
            if (a.isEmpty()) {
                continue;
            }
            double[] c;
            double r;
            if (joker) {
                if (i >= JOKER.length) {
                    break;
                }
                c = JOKER[i];
                r = w * 0.12;
            } else {
                if (i >= SPOTS.length) {
                    break;
                }
                c = SPOTS[i];
                r = w * 0.155;
            }
            double cx = x + c[0] * w;
            double cy = y + c[1] * h;
            Rectangle hit = new Rectangle((int) (cx - r), (int) (cy - r), (int) (2 * r),
                (int) (2 * r));
            String key = "action:" + a;
            spots.put(key, hit);
            if (state.orderArt() == null) {
                Ellipse2D disk = new Ellipse2D.Double(cx - r * 0.86, cy - r * 0.86,
                    r * 1.72, r * 1.72);
                g.setColor(Theme.tile());
                g.fill(disk);
                g.setColor(Theme.border());
                g.setStroke(new BasicStroke(1f));
                g.draw(disk);
                ActionIcons.paint(g, a, cx, cy, r * 0.95, Theme.ink());
                g.setFont(Theme.narrow(Math.max(9f, (float) (w / 13.0)), Font.BOLD));
                g.setColor(Theme.ink());
                String nm = ActionBar.ACTIONS.getOrDefault(a, a)
                    .toLowerCase(java.util.Locale.ROOT);
                centred(g, nm, (int) cx, (int) (cy + r + g.getFontMetrics().getAscent()));
            }
            if (!deciding) {
                continue;
            }
            boolean can = choices.containsKey(key);
            Ellipse2D ring = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
            if (!can) {
                g.setColor(Theme.alpha(Theme.isDark() ? Color.BLACK : Color.WHITE, 0.55));
                g.fill(ring);
                continue;
            }
            boolean hot = key.equals(hoverKey);
            g.setColor(Theme.alpha(seatColor, hot ? 0.30 : 0.10));
            g.fill(ring);
            g.setColor(seatColor);
            g.setStroke(new BasicStroke(hot ? Theme.pxf(4) : Theme.pxf(2.6)));
            g.draw(ring);
        }
        if (deciding && !joker) {
            for (int half = 0; half < 2; half++) {
                boolean any = false;
                for (int i = half * 2; i < Math.min(actions.size(), half * 2 + 2); i++) {
                    any |= choices.containsKey("action:" + actions.get(i));
                }
                if (!any) {
                    int t = half == 0 ? (int) (y + h * 0.16) : (int) (y + h * 0.62);
                    int b = half == 0 ? (int) (y + h * 0.58) : (int) (y + h * 0.97);
                    g.setColor(Theme.alpha(Theme.isDark() ? Color.BLACK : Color.WHITE, 0.35));
                    g.fillRect(x + 2, t, w - 4, b - t);
                }
            }
        }
        g.setColor(Theme.alpha(Color.BLACK, 0.35));
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);
    }

    private void paintOrderBack(Graphics2D g, OrderCardFace.Info info, RoundRectangle2D shape,
                                int x, int y, int w, int h) {
        Color deck = ActionIcons.deckColor(info.deck());
        g.setColor(Theme.panel());
        g.fill(shape);
        java.awt.Shape clip = g.getClip();
        g.clip(shape);
        g.setColor(Theme.alpha(deck, 0.10));
        g.fillRect(x, y, w, h);
        int band = (int) (h * 0.10);
        g.setColor(deck);
        g.fillRect(x, y, w, band);
        g.setFont(Theme.font(Math.max(11, w / 11.0), Font.BOLD));
        g.setColor(Color.WHITE);
        String top = info.joker() ? "ЗАТАИТЬСЯ" : ActionIcons.categoryRu(info.top());
        centred(g, FieldBubbles.clip(g.getFontMetrics(), top, w - Theme.px(12)),
            x + w / 2, y + band / 2 + g.getFontMetrics().getAscent() / 2 - Theme.px(1));
        if (!info.joker() && info.bottom() != null) {
            int by = (int) (y + h * 0.60);
            g.setColor(Theme.alpha(deck, 0.55));
            g.fillRect(x, by, w, (int) (h * 0.075));
            g.setFont(Theme.font(Math.max(10, w / 13.0), Font.BOLD));
            g.setColor(Color.WHITE);
            centred(g, ActionIcons.categoryRu(info.bottom()), x + w / 2,
                by + (int) (h * 0.075) / 2 + g.getFontMetrics().getAscent() / 2 - Theme.px(1));
        }
        g.setClip(clip);
    }

    private List<String> orderActions() {
        return actionsOf(state.orderInfo());
    }

    /**
     * Какие действия напечатаны на карте, по порядку мест: два сверху, два
     * снизу (у «Затаиться» — все восемь). Окно по нему узнаёт, есть ли у
     * действия круг на карте, или его надо предложить иначе.
     */
    public static List<String> actionsOf(OrderCardFace.Info info) {
        List<String> out = new ArrayList<>();
        if (info == null) {
            return out;
        }
        if (info.joker()) {
            out.addAll(ActionBar.ACTIONS.keySet());
            return out;
        }
        out.addAll(ActionIcons.CATEGORY_ACTIONS.getOrDefault(info.top(), List.of()));
        while (out.size() < 2) {
            out.add("");
        }
        out.addAll(ActionIcons.CATEGORY_ACTIONS.getOrDefault(info.bottom(), List.of()));
        return out;
    }

    private void paintEnd(Graphics2D g, int x, int y, int w, int h) {
        boolean can = choices.containsKey("end");
        boolean hot = can && "end".equals(hoverKey);
        int eh = Math.min(h, Theme.px(118));
        int ey = y + h - eh;
        RoundRectangle2D r = new RoundRectangle2D.Double(x, ey, w, eh, Theme.px(14), Theme.px(14));
        if (can) {
            g.setColor(Theme.alpha(Color.BLACK, 0.2));
            g.fill(new RoundRectangle2D.Double(x + 2, ey + 4, w, eh, Theme.px(14), Theme.px(14)));
        }
        g.setColor(can ? (hot ? Theme.lighten(seatColor, 0.12) : seatColor)
            : Theme.alpha(Theme.tile(), 0.8));
        g.fill(r);
        g.setColor(can ? Theme.darken(seatColor, 0.2) : Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(r);
        g.setColor(can ? Color.WHITE : Theme.ink3());
        g.setFont(Theme.font(14, Font.BOLD));
        String a = can ? "Завершить" : "Ход";
        String b = can ? "ход" : "соперника";
        if (!can && state.status() != null) {
            String[] w2 = state.status().split(" ", 2);
            a = Character.toUpperCase(w2[0].charAt(0)) + w2[0].substring(1);
            b = w2.length > 1 ? w2[1] : "";
        }
        centred(g, a, x + w / 2, ey + eh / 2 - Theme.px(4));
        if (!b.isEmpty()) {
            centred(g, b, x + w / 2, ey + eh / 2 + Theme.px(14));
        }
        List<FieldBubbles.Opt> end = choices.get("end");
        if (can && end != null && !end.isEmpty() && end.get(0).sub() != null) {
            g.setFont(Theme.font(9, Font.PLAIN));
            g.setColor(new Color(255, 255, 255, 210));
            centred(g, end.get(0).sub(), x + w / 2, ey + eh - Theme.px(10));
        }
        spots.put("end", new Rectangle(x, ey, w, eh));
        // над плашкой — сыгранные в раунде приказы маленькой стопкой
        List<String> played = state.ordersPlayed();
        if (!played.isEmpty()) {
            int ch = Math.min(eh, h - eh - Theme.px(26));
            int cw = (int) (ch * 0.64);
            if (ch > Theme.px(40)) {
                int cx = x + (w - cw) / 2;
                int cy = y + Theme.px(16);
                g.setFont(Theme.caption());
                g.setColor(Theme.ink3());
                g.drawString("СЫГРАНО · " + played.size(), x, y + Theme.px(10));
                for (int i = 0; i < Math.min(4, played.size()); i++) {
                    BufferedImage img = faceOf.apply(played.get(played.size() - 1 - i));
                    paintBack(g, img, cx + i * Theme.px(4), cy + i * Theme.px(3), cw, ch,
                        Theme.tile());
                }
                groups.put("played", new Rectangle(cx, cy, cw + Theme.px(16), ch + Theme.px(12)));
            }
        }
    }

    // ---------- руки веером ----------

    private void paintHands(Graphics2D g, int x, int y, int w, int h) {
        if (w < Theme.px(80)) {
            return;
        }
        List<String> obj = state.objectives();
        List<String> sup = state.superObjectives();
        List<String> ord = state.ordersInHand();
        // Задания — главный веер, крупно; ниже узкой полосой — приказы в руке
        // и супер-задания.
        int subH = ord.isEmpty() && sup.isEmpty() ? 0 : (int) (h * 0.30);
        int mainH = h - subH - (subH > 0 ? Theme.px(8) : 0);
        fan(g, "objectives", "ЗАДАНИЯ", obj, x, y, w, mainH, false);
        if (subH > 0) {
            int sy = y + mainH + Theme.px(8);
            int half = sup.isEmpty() ? w : ord.isEmpty() ? 0 : w / 2;
            if (!ord.isEmpty()) {
                fan(g, "orders", "ПРИКАЗЫ В РУКЕ", ord, x, sy, half - Theme.px(8), subH, true);
            }
            if (!sup.isEmpty()) {
                fan(g, "super", "СУПЕР-ЗАДАНИЯ", sup, x + half, sy, w - half, subH, true);
            }
        }
    }

    /**
     * ВЕЕР КАРТ: карты чуть повёрнуты вокруг точки под веером и заходят друг
     * на друга, наведённая поднимается. Щелчок раскрывает всю группу.
     */
    private void fan(Graphics2D g, String group, String caption, List<String> ids,
                     int x, int y, int w, int h, boolean small) {
        int capH = Theme.px(16);
        g.setFont(Theme.caption());
        boolean chosen = ids.stream().anyMatch(id -> choices.containsKey("card:" + id));
        g.setColor(chosen ? seatColor : Theme.ink3());
        g.drawString(caption + " · " + ids.size() + (chosen ? " — щёлкните, чтобы сыграть" : ""),
            x, y + capH - Theme.px(4));
        int ch = h - capH - Theme.px(6);
        int cw = (int) Math.round(ch * 0.69);
        int n = ids.size();
        if (n == 0) {
            g.setColor(Theme.alpha(Theme.ink3(), 0.6));
            g.setStroke(new BasicStroke(Theme.pxf(1.3), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{Theme.pxf(5), Theme.pxf(4)}, 0f));
            g.draw(new RoundRectangle2D.Double(x, y + capH + Theme.px(2), cw, ch,
                cw * 0.08, cw * 0.08));
            return;
        }
        double step = n <= 1 ? 0 : Math.min(cw * 0.78, (w - cw) / (double) (n - 1));
        step = Math.max(cw * 0.22, step);
        double spreadDeg = small ? 0 : Math.min(18, n * 4.0);
        int baseY = y + capH + Theme.px(4);
        Rectangle area = new Rectangle(x, y, (int) (cw + step * (n - 1)) + Theme.px(8), h);
        groups.put(group, area);
        String hot = hoverCard;
        for (int i = 0; i < n; i++) {
            String id = ids.get(i);
            double t = n == 1 ? 0 : i / (double) (n - 1) - 0.5;
            double ang = Math.toRadians(spreadDeg * t);
            boolean isHot = id.equals(hot);
            double lift = (small ? 0 : Math.abs(t) * ch * 0.10) - (isHot ? Theme.px(14) : 0);
            double cx = x + i * step + cw / 2.0;
            double cy = baseY + ch / 2.0 + lift;
            AffineTransform at = new AffineTransform();
            at.translate(cx, cy);
            at.rotate(isHot ? 0 : ang);
            at.translate(-cw / 2.0, -ch / 2.0);
            Shape card = at.createTransformedShape(
                new RoundRectangle2D.Double(0, 0, cw, ch, cw * 0.08, cw * 0.08));
            Graphics2D gc = (Graphics2D) g.create();
            gc.transform(at);
            gc.setColor(Theme.alpha(Color.BLACK, 0.22));
            gc.fill(new RoundRectangle2D.Double(2, 4, cw, ch, cw * 0.08, cw * 0.08));
            BufferedImage face = faceOf.apply(id);
            RoundRectangle2D local = new RoundRectangle2D.Double(0, 0, cw, ch, cw * 0.08, cw * 0.08);
            if (face != null) {
                gc.clip(local);
                gc.drawImage(face, 0, 0, cw, ch, null);
                gc.setClip(null);
            } else {
                gc.setColor(Theme.panel());
                gc.fill(local);
                gc.setColor(Theme.points());
                gc.fillRect(0, 0, cw, Math.max(4, ch / 14));
                gc.setColor(Theme.ink());
                gc.setFont(Theme.font(small ? 9 : 11, Font.BOLD));
                wrap(gc, nameOf.apply(id), Theme.px(5), Theme.px(small ? 18 : 26),
                    cw - Theme.px(10), 3);
            }
            boolean can = choices.containsKey("card:" + id);
            gc.setColor(can ? seatColor : Theme.alpha(Color.BLACK, 0.3));
            gc.setStroke(new BasicStroke(can ? Theme.pxf(2.6) : 1f));
            gc.draw(local);
            String tag = tagOf.apply(id);
            if (tag != null && !small) {
                gc.setFont(Theme.font(9, Font.BOLD));
                FontMetrics fm = gc.getFontMetrics();
                int tw = fm.stringWidth(tag) + Theme.px(10);
                int th = Theme.px(16);
                gc.setColor(Theme.kelium());
                gc.fill(new RoundRectangle2D.Double(cw - tw - Theme.px(4), ch - th - Theme.px(4),
                    tw, th, th, th));
                gc.setColor(Color.WHITE);
                gc.drawString(tag, cw - tw + Theme.px(1), ch - Theme.px(8));
            }
            gc.dispose();
            fanCards.add(new Object[]{id, card});
            spots.put("card:" + id, card.getBounds());
        }
    }

    private void paintBack(Graphics2D g, BufferedImage img, int x, int y, int w, int h,
                           Color fallback) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, w * 0.08, w * 0.08);
        g.setColor(Theme.alpha(Color.BLACK, 0.25));
        g.fill(new RoundRectangle2D.Double(x + 2, y + 3, w, h, w * 0.08, w * 0.08));
        if (img != null) {
            java.awt.Shape clip = g.getClip();
            g.clip(shape);
            g.drawImage(img, x, y, w, h, null);
            g.setClip(clip);
        } else {
            g.setColor(fallback);
            g.fill(shape);
        }
        g.setColor(Theme.alpha(Color.BLACK, 0.35));
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);
    }

    private void badge(Graphics2D g, int x, int y, String text, Color c) {
        g.setFont(Theme.font(10, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(text) + Theme.px(12);
        int bh = Theme.px(18);
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(x, y, bw, bh, bh, bh));
        g.setColor(Color.WHITE);
        g.drawString(text, x + Theme.px(6), y + bh - Theme.px(5));
    }

    private static void wrap(Graphics2D g, String text, int x, int y, int w, int maxLines) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : words) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(next) > w && line.length() > 0) {
                g.drawString(line.toString(), x, y + lines * fm.getHeight());
                lines++;
                if (lines >= maxLines) {
                    return;
                }
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0 && lines < maxLines) {
            g.drawString(FieldBubbles.clip(fm, line.toString(), w), x, y + lines * fm.getHeight());
        }
    }

    private static void centred(Graphics2D g, String s, int cx, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, baseline);
    }
}
