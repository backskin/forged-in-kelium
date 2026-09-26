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

import kelium.gui.replay2.MarkIcons;
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
public final class PlayerTable extends JComponent implements javax.swing.Scrollable {

    private static final long serialVersionUID = 1L;

    /** Кто рисует печатные планшеты (их умеет лист игрока проигрывателя). */
    public interface BoardsArt {
        /** Ширина к высоте; 0 — печатных планшетов нет. */
        double aspect();

        /** Где в сцепке планшет хранилища: {@code [левый край, ширина, низ]} долями. */
        double[] storageBox();

        /** Ширина карты арсенала — доля ширины сцепки (натуральный размер). */
        double cardWidth();

        /** Насколько вставленные карты свисают под сцепкой — доля её высоты. */
        double hang();

        /**
         * Ширина сцепки в пикселях печати (экспорт 300 точек на дюйм). Через
         * неё всё на столе рисуется ОДНОЙ МЕРОЙ: карта 661×1028 печати рядом с
         * планшетом 3354×886 — ровно как на столе.
         */
        default double printWidth() {
            return 0;
        }

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
                        List<Trophy> dump, int dumpValue, String status,
                        boolean hidden, BufferedImage orderBack) {
    }

    /**
     * ВКЛАДКИ МЕСТ над зоной: на чей стол смотрим. Щелчок — посмотреть стол
     * другого игрока, в том числе бота (просьба дизайнера 25.09.2026).
     */
    public record SeatTab(int seat, String name, boolean own) {
    }

    private List<SeatTab> seatTabs = List.of();
    private Consumer<Integer> onSeat = s -> { };

    public void setSeatTabs(List<SeatTab> tabs, Consumer<Integer> onSeat) {
        this.seatTabs = tabs == null ? List.of() : List.copyOf(tabs);
        this.onSeat = onSeat == null ? s -> { } : onSeat;
        repaint();
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
                boolean onTab = tabRects.keySet().stream().anyMatch(r -> r.contains(p));
                boolean hand = bubbles.hovering() || group != null || onTab
                    || "back".equals(key)
                    || key != null && choices.containsKey(key)
                    || !overBubble && boardAt(p) != null;
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
                for (Map.Entry<Rectangle, Integer> t : tabRects.entrySet()) {
                    if (t.getKey().contains(p)) {
                        onSeat.accept(t.getValue());
                        return;
                    }
                }
                Rectangle back = spots.get("back");
                if (state != null && state.hidden() && back != null && back.contains(p)) {
                    seatTabs.stream().filter(SeatTab::own).findFirst()
                        .ifPresent(t -> onSeat.accept(t.seat()));
                    return;
                }
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
                    // стопка контейнеров под планшетом — раскладывается сама по себе
                    if ("containers".equals(key) && state != null) {
                        onHoverOff.run();
                        onOpen.accept("containers");
                        return;
                    }
                    String board = boardAt(p);
                    if (board != null && state != null) {
                        onHoverOff.run();
                        onOpen.accept("board:" + board);
                        return;
                    }
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
        int h = getHeight() > 0 ? getHeight() : Theme.px(320);
        return new Dimension(Math.max(Theme.px(600), contentWidth(h)), h);
    }

    // ---------- прокрутка вбок ----------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
        return Theme.px(40);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
        return Math.max(Theme.px(40), r.width - Theme.px(80));
    }

    /** Высота — всегда во весь просвет: листается только вбок. */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    /** Помещается в окно — растягивается по окну; не помещается — ползунок. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof javax.swing.JViewport v
            && v.getWidth() >= contentWidth(v.getHeight());
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
                } else if (e.getKey().startsWith("cell:")) {
                    a *= 8;     // ячейка склада — только для подсказки, жетон важнее
                }
                if (a < bestArea) {
                    bestArea = a;
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    /**
     * КАКОЙ ПЛАНШЕТ ПОД ТОЧКОЙ: {@code troop} или {@code storage}, иначе null.
     * Щелчок по планшету, когда на нём ничего не выбирают, открывает его крупно
     * (заказ дизайнера 25.09.2026).
     */
    private String boardAt(Point p) {
        String hit = null;
        for (String b : List.of("storage", "troop")) {
            Rectangle r = spots.get(b);
            if (r != null && r.contains(p)) {
                hit = b;
            }
        }
        if (hit != null) {
            return hit;
        }
        // жетоны военных зданий лежат над планшетом войск, добытчики и
        // энергостанции — на хранилище
        String key = keyAt(p);
        if (key == null) {
            return null;
        }
        if (key.startsWith("building:miner") || key.startsWith("building:power_plant")
                || key.startsWith("store:") || key.startsWith("cell:")) {
            return "storage";
        }
        if (key.startsWith("building:") || key.startsWith("red:") || key.startsWith("blue:")
                || key.startsWith("unit:") || key.startsWith("installed:")
                || "containers".equals(key)) {
            return "troop";
        }
        return null;
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
                case "dump" -> "Свалка: отложенный приказ и уничтоженные жетоны врагов на нём — "
                    + "щелчок раскрывает";
                case "orders" -> "Приказы в руке — щелчок раскрывает";
                default -> "Щелчок раскрывает карты";
            };
        }
        String board = boardAt(e.getPoint());
        if (board != null) {
            return ("storage".equals(board) ? "Планшет хранилища" : "Планшет войск")
                + " — щелчок открывает его крупно, с подсказкой по каждой детали";
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
            g.setColor(MAT_INK2);
            g.drawString("зона игрока появится, когда партия начнётся", Theme.px(24), Theme.px(34));
            g.dispose();
            return;
        }
        int pad = Theme.px(12);
        int top = pad + Theme.px(4);
        int tabsH = paintSeatTabs(g, w);
        int resH = paintResources(g, pad + Theme.px(8), Theme.px(8));
        top += Math.max(tabsH, resH);
        int innerH = h - top - pad;
        Layout L = layout(w, h, top, innerH);

        // ---- СЛЕВА: руки стопками и свалка
        int sx0 = L.leftX();
        for (Object[] hd : handGroups()) {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) hd[2];
            int hw = stackWidth(ids.size(), L.stackH());
            fan(g, (String) hd[0], (String) hd[1], ids, sx0,
                top + (innerH - L.stackH()) / 2, hw, L.stackH(), true);
            sx0 += hw + L.gap();
        }
        paintDump(g, L.dumpX(), top + (innerH - L.dumpH()) / 2 - Theme.px(6), L.dumpW(),
            L.dumpH());

        // ---- ПО ЦЕНТРУ: планшеты хранилища и войск, под хранилищем — стопка арсенала
        if (L.bw() > 0) {
            int bw = L.bw();
            int x = L.boardsX();
            int bh = (int) Math.round(bw / boards.aspect());
            int by = top;
            int cardsBottom = by + (int) Math.round(bh * (1 + Math.max(0, boards.hang())));
            double[] sb = boards.storageBox();
            int stx = x + (int) (sb[0] * bw);
            int stw = (int) (sb[1] * bw);
            int sBottom = by + (int) (sb[2] * bh);
            int cardW = (int) Math.round(boards.cardWidth() * bw);
            paintArsenalStack(g, stx, stw, sBottom, cardsBottom, cardW);
            Map<String, Rectangle> hits = new LinkedHashMap<>();
            boards.paint(g, x, by, bw, hits, outlines);
            spots.putAll(hits);
            for (String k : hits.keySet()) {
                if (k.startsWith("installed:")) {
                    groups.put("installed", hits.get(k));
                }
            }
        }

        // ---- СПРАВА: вскрытый приказ круга и «Завершить ход»
        int orderH = (int) Math.round(L.orderW() * CARD_H / (double) CARD_W);
        int oy = top + Math.max(0, (innerH - orderH) / 2);
        paintOrder(g, L.orderX(), oy, L.orderW(), orderH);
        paintEnd(g, L.orderX() + L.orderW() + Theme.px(10), oy, L.endW(), orderH);

        // ---- обводка всего, что можно выбрать
        for (String key : choices.keySet()) {
            Rectangle r = spots.get(key);
            if (r == null || key.startsWith("action:") || "end".equals(key)
                    || key.startsWith("card:")) {
                continue;
            }
            boolean hot = key.equals(hoverKey);
            // ПОДСВЕТКА ПО ФОРМЕ ДЕТАЛИ (26.09.2026): у жетона — его силуэт,
            // свечение позади в прозрачность и контур по краске.
            var sil = kelium.gui.replay2.TokenSilhouettes.LAST.get(key);
            if (sil != null) {
                kelium.gui.replay2.TokenSilhouettes.glow(g, sil, seatColor, hot);
                continue;
            }
            Shape outline = outlines.get(key);
            Shape glow = outline != null ? outline
                : new RoundRectangle2D.Double(r.x - 2, r.y - 2, r.width + 4, r.height + 4,
                    Theme.px(8), Theme.px(8));
            softGlow(g, glow, seatColor, hot);
        }

        bubbles.paint(g, w, h, key -> {
            Rectangle r = spots.get(key);
            return r == null ? null : new Point2D.Double(r.getCenterX(), r.getCenterY());
        }, Theme.px(30));
        g.dispose();
    }

    /**
     * МЯГКОЕ СВЕЧЕНИЕ ВОКРУГ ФОРМЫ — для деталей без картинки (ячейки модулей,
     * хранилище): несколько расширяющихся обводок всё прозрачнее, по краю —
     * контур. Никаких сплошных заливок поверх печати.
     */
    private static void softGlow(Graphics2D g, Shape shape, Color c, boolean hot) {
        int passes = 6;
        float reach = Theme.pxf(hot ? 14 : 10);
        for (int i = passes; i >= 1; i--) {
            float wdt = reach * i / passes * 2;
            g.setColor(Theme.alpha(c, (hot ? 0.16 : 0.11) * (1.0 - (i - 1) / (double) passes)));
            g.setStroke(new BasicStroke(wdt, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
        }
        g.setColor(Theme.alpha(c, hot ? 0.18 : 0.08));
        g.fill(shape);
        g.setColor(c);
        g.setStroke(new BasicStroke(Theme.pxf(hot ? 3 : 2.2), BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND));
        g.draw(shape);
    }

    // ---------- раскладка ряда ----------

    /**
     * РАСКЛАДКА ЗОНЫ (просьба дизайнера 26.09.2026): планшеты — ПО ЦЕНТРУ
     * перед игроком, слева руки стопками и свалка, справа приказ и «Завершить
     * ход». Всё растёт от высоты зоны одним масштабом; не влезает в ширину —
     * зона шире окна, и её листают ползунком.
     */
    private record Layout(int leftX, int dumpX, int dumpW, int dumpH, int stackH,
                          int boardsX, int bw, int orderX, int orderW, int endW, int gap,
                          int width) {
    }

    private List<Object[]> handGroups() {
        List<Object[]> hands = new ArrayList<>();
        if (state == null) {
            return hands;
        }
        hands.add(new Object[]{"objectives", "ЗАДАНИЯ", state.objectives()});
        if (!state.superObjectives().isEmpty()) {
            hands.add(new Object[]{"super", "СУПЕР", state.superObjectives()});
        }
        hands.add(new Object[]{"orders", "ПРИКАЗЫ", state.ordersInHand()});
        return hands;
    }

    /** Ширина стопки из {@code n} карт: карты заходят друг на друга, видна кромка каждой. */
    private static int stackWidth(int n, int stackH) {
        double cw = (stackH - capH()) * CARD_W / (double) CARD_H;
        return (int) Math.round(cw * (1 + 0.22 * (Math.max(1, n) - 1)) + Theme.px(4));
    }

    /** Подпись над стопкой руки. */
    private static int capH() {
        return Theme.px(26);
    }

    /**
     * ОДНА МЕРА НА ВЕСЬ СТОЛ (замечание дизайнера 26.09.2026: «карта под
     * свалку — такая же карта, как остальные, только в повороте; приказы не
     * того размера; гигантская карта справа»). Все компоненты экспортированы
     * в 300 точек на дюйм, поэтому пиксель печати — одна и та же доля
     * миллиметра у планшета, карты и жетона. {@code s} — экранных точек на
     * пиксель печати; выбирается так, чтобы по высоте влезла самая высокая
     * вещь зоны — сцепка планшетов со свисающими картами или карта с подписью.
     */
    private double printScale(int innerH) {
        double pw = boards == null ? 0 : boards.printWidth();
        if (pw <= 0 || boards.aspect() <= 0) {
            return innerH / 1300.0;
        }
        double pairH = pw / boards.aspect() * (1 + Math.max(0, boards.hang()));
        return Math.min(innerH / pairH, (innerH - capH()) / (double) CARD_H);
    }

    /** Карта приказа и задания в печати: 661×1028 (56×87 мм). */
    private static final int CARD_W = 661;
    private static final int CARD_H = 1028;

    private Layout layout(int w, int h, int top, int innerH) {
        int pad = Theme.px(20);
        int gap = Theme.px(18);
        double sc = printScale(innerH);
        int cardW = (int) Math.round(CARD_W * sc);
        int cardH = (int) Math.round(CARD_H * sc);
        int stackH = cardH + capH();
        int left = 0;
        for (Object[] hd : handGroups()) {
            left += stackWidth(((List<?>) hd[2]).size(), stackH) + gap;
        }
        // свалка — та же карта приказа, лёжа
        int dumpW = cardH;
        int dumpH = cardW;
        left += dumpW;
        int bw = 0;
        if (boards != null && boards.aspect() > 0) {
            bw = boards.printWidth() > 0 ? (int) Math.round(boards.printWidth() * sc)
                : (int) Math.round(innerH / (1 + Math.max(0, boards.hang())) * boards.aspect());
        }
        int orderW = cardW;
        int endW = Theme.px(150);
        int right = orderW + Theme.px(10) + endW;
        int minW = pad + left + gap + bw + gap + right + pad;
        // планшеты по центру окна, пока соседям хватает места
        int bx = w / 2 - bw / 2;
        bx = Math.max(bx, pad + left + gap);
        bx = Math.min(bx, Math.max(pad + left + gap, w - pad - right - gap - bw));
        int leftX = bx - gap - left;
        int dumpX = bx - gap - dumpW;
        int orderX = bx + bw + gap;
        return new Layout(leftX, dumpX, dumpW, dumpH, stackH, bx, bw, orderX, orderW, endW,
            gap, minW);
    }

    /** Сколько ширины нужно зоне при высоте {@code h} (для ползунка прокрутки). */
    public int contentWidth(int h) {
        int pad = Theme.px(12);
        int top = pad + Theme.px(4) + Theme.px(30);
        return layout(0, h, top, Math.max(Theme.px(80), h - top - pad)).width();
    }

    /** Ресурс игрока в строке над столом: значок, цвет, значение, предел (или null). */
    public record Res(String icon, Color color, String value, String cap, String label) {
    }

    private List<Res> resources = List.of();

    /**
     * РЕСУРСЫ — НА СВОЁМ СТОЛЕ, А НЕ В ВЕРХНЕЙ ПОЛОСЕ (просьба дизайнера
     * 26.09.2026: «почему деньги и очки сверху, а не там же, где моя зона?»).
     */
    public void setResources(List<Res> res) {
        this.resources = res == null ? List.of() : res;
        repaint();
    }

    /** Строка ресурсов крупно; возвращает её высоту. */
    private int paintResources(Graphics2D g, int x, int y) {
        if (resources.isEmpty()) {
            return 0;
        }
        int h = Theme.px(34);
        double s = Theme.px(24);
        Font num = Theme.mono(19, Font.BOLD);
        Font cap = Theme.font(13, Font.PLAIN);
        int cy = y + h / 2;
        for (Res r : resources) {
            MarkIcons.paint(g, r.icon(), x + s / 2, cy, s, r.color());
            x += (int) s + Theme.px(6);
            g.setFont(num);
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            String v = r.value() + (r.cap() == null ? "" : "/" + r.cap());
            g.drawString(v, x, cy + (fm.getAscent() - fm.getDescent()) / 2);
            x += fm.stringWidth(v) + Theme.px(5);
            g.setFont(cap);
            g.setColor(MAT_INK2);
            fm = g.getFontMetrics();
            g.drawString(r.label(), x, cy + (fm.getAscent() - fm.getDescent()) / 2);
            x += fm.stringWidth(r.label()) + Theme.px(18);
        }
        return h + Theme.px(6);
    }

    /** Где лежат вкладки мест после отрисовки. */
    private final Map<Rectangle, Integer> tabRects = new LinkedHashMap<>();

    /**
     * ВКЛАДКИ МЕСТ — узкой полосой у верхнего края зоны: чей стол сейчас
     * перед глазами. Своя вкладка подписана «вы», чужая — «смотрим».
     *
     * @return сколько высоты заняла полоса
     */
    private int paintSeatTabs(Graphics2D g, int w) {
        tabRects.clear();
        if (seatTabs.size() < 2 || state == null) {
            return 0;
        }
        int th = Theme.px(24);
        int x = w - Theme.px(12);
        int y = Theme.px(8);
        g.setFont(Theme.font(13, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        for (int i = seatTabs.size() - 1; i >= 0; i--) {
            SeatTab t = seatTabs.get(i);
            String label = t.name() + (t.own() ? " · вы" : "");
            int tw = fm.stringWidth(label) + Theme.px(26);
            x -= tw;
            boolean on = t.seat() == state.seat();
            Color c = Theme.seat(t.seat());
            RoundRectangle2D r = new RoundRectangle2D.Double(x, y, tw, th, th, th);
            g.setColor(on ? c : Theme.alpha(Color.BLACK, 0.25));
            g.fill(r);
            g.setColor(on ? Theme.lighten(c, 0.3) : Theme.alpha(c, 0.8));
            g.setStroke(new BasicStroke(Theme.pxf(1.4)));
            g.draw(r);
            g.setColor(on ? Color.WHITE : MAT_INK);
            g.fillOval(x + Theme.px(8), y + th / 2 - Theme.px(3), Theme.px(6), Theme.px(6));
            g.drawString(label, x + Theme.px(18), y + (th + fm.getAscent()) / 2 - Theme.px(2));
            tabRects.put(new Rectangle(x, y, tw, th), t.seat());
            x -= Theme.px(6);
        }
        if (state.hidden()) {
            g.setFont(Theme.font(13, Font.PLAIN));
            g.setColor(MAT_INK2);
            String s = "стол " + state.seatName() + " — видно только открытое";
            g.drawString(s, x - g.getFontMetrics().stringWidth(s) - Theme.px(8),
                y + (th + g.getFontMetrics().getAscent()) / 2 - Theme.px(2));
        }
        return th + Theme.px(2);
    }

    /** Подписи на коврике: светлые, коврик тёмный. */
    private static final Color MAT_INK = new Color(0xDCEAF0);
    private static final Color MAT_INK2 = new Color(0x9FBCC9);

    /**
     * КОВРИК ЗОНЫ ИГРОКА — тёмно-бирюзовое сукно стола (просьба дизайнера
     * 25.09.2026: «всё серо-белое, как поделка; должно быть цветасто, чётко,
     * круто»). Печатные планшеты и карты светлые, и на глубоком фоне они
     * горят; подложка подкрашена цветом места, сверху — его светящаяся кромка.
     */
    private void paintMat(Graphics2D g, int w, int h) {
        Color seat = state == null ? new Color(0x3B82D0) : Theme.seat(state.seat());
        g.setPaint(new GradientPaint(0, 0, new Color(0x21414F), 0, h, new Color(0x0D1B23)));
        g.fillRect(0, 0, w, h);
        // лёгкий отсвет цвета места из левого верхнего угла
        java.awt.RadialGradientPaint glow = new java.awt.RadialGradientPaint(
            new Point2D.Double(w * 0.18, 0), (float) Math.max(w, h) * 0.7f,
            new float[]{0f, 1f},
            new Color[]{Theme.alpha(seat, 0.22), Theme.alpha(seat, 0.0)});
        g.setPaint(glow);
        g.fillRect(0, 0, w, h);
        // ткань: редкие диагонали
        g.setColor(new Color(255, 255, 255, 7));
        g.setStroke(new BasicStroke(1f));
        for (int x = -h; x < w; x += Theme.px(9)) {
            g.drawLine(x, h, x + h, 0);
        }
        // светящаяся кромка цвета места
        g.setPaint(new GradientPaint(0, 0, seat, w, 0, Theme.alpha(seat, 0.15)));
        g.fillRect(0, 0, w, Theme.px(3));
        g.setColor(seat);
        g.fillRect(0, 0, Theme.px(5), h);
    }

    // ---------- стопка закрытого арсенала ----------

    /**
     * СТОПКА ЗАКРЫТОГО АРСЕНАЛА — торчит из-под нижней кромки планшета
     * хранилища, рисуется РАНЬШЕ планшетов: планшет лежит поверх неё. Сколько
     * карт — столько и видно (до шести, дальше цифрой).
     */
    private void paintArsenalStack(Graphics2D g, int sx, int sw, int boardBottom, int bottom,
                                   int cardW) {
        List<String> hand = state.arsenalHand();
        BufferedImage back = backOf.apply("arsenal");
        // КАРТА НАТУРАЛЬНОГО РАЗМЕРА — та же ширина, что у вставленной в паз
        // (ширина паза в печати), и своя пропорция с картинки: карта
        // горизонтальная (замечание дизайнера 25.09.2026: «стопка гигантская и
        // не соответствует размеру карты»).
        double ratio = aspect(back, 1.544);
        int cw = Math.max(Theme.px(40), cardW);
        int ch = (int) Math.round(cw / ratio);
        int cx = sx + sw / 2;
        int y0 = bottom - ch;
        int n = Math.min(6, hand.size());
        int spread = Math.max(Theme.px(4), cw / 16);
        int total = cw + Math.max(0, n - 1) * spread;
        int x0 = cx - total / 2;
        Rectangle area = new Rectangle(x0 - Theme.px(4), boardBottom - Theme.px(6),
            Math.max(total, cw) + Theme.px(8), bottom - boardBottom + Theme.px(6));
        if (hand.isEmpty()) {
            // Пусто — тихой подписью под кромкой: место не должно кричать.
            g.setFont(Theme.font(12.5, Font.PLAIN));
            g.setColor(MAT_INK2);
            centred(g, "закрытого арсенала нет", cx, bottom - Theme.px(6));
            return;
        }
        boolean chosen = hand.stream().anyMatch(id -> choices.containsKey("card:" + id));
        boolean hot = "arsenal".equals(hoverGroup);
        for (int i = 0; i < n; i++) {
            int x = x0 + i * spread;
            int y = y0 - (hot ? Theme.px(10) : 0) + (i % 2) * Theme.px(2);
            // рубашка СВОЕЙ колоды: начальный, обычный, супер-арсенал
            BufferedImage own = backOf.apply("arsenal:" + hand.get(hand.size() - n + i));
            paintBack(g, own != null ? own : back, x, y, cw, ch, Theme.container());
        }
        if (chosen) {
            g.setColor(seatColor);
            g.setStroke(new BasicStroke(Theme.pxf(2.6)));
            g.draw(new RoundRectangle2D.Double(x0 - 3, y0 - 3 - (hot ? Theme.px(10) : 0),
                total + 6, ch + 6, cw * 0.1, cw * 0.1));
        }
        badge(g, x0 + total + Theme.px(4), bottom - Theme.px(18),
            "арсенал · " + hand.size(), chosen ? seatColor : Theme.container());
        if (!state.hidden()) {
            groups.put("arsenal", area);
        }
    }

    // ---------- свалка ----------

    /**
     * СВАЛКА — отложенный в начале раунда приказ, рубашкой вверх, повёрнут на
     * четверть оборота против часовой. На нём лежат уничтоженные жетоны
     * трофейной стороной; под ним — сколько они стоят.
     */
    private void paintDump(Graphics2D g, int x, int y, int w, int h) {
        g.setFont(Theme.font(13, Font.BOLD));
        g.setColor(MAT_INK2);
        g.drawString("СВАЛКА", x, y - Theme.px(6));
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, h * 0.08, h * 0.08);
        if (state.dumpBack() == null) {
            g.setColor(Theme.alpha(MAT_INK2, 0.6));
            g.setStroke(new BasicStroke(Theme.pxf(1.3), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{Theme.pxf(5), Theme.pxf(4)}, 0f));
            g.draw(shape);
            g.setFont(Theme.font(12.5, Font.PLAIN));
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
        kelium.report.Mips.draw(g, back, at);
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
                    kelium.report.Mips.draw(g, t.face(), tt);
                } else {
                    g.setColor(Theme.trophy());
                    g.fill(new Ellipse2D.Double(cx - cell * 0.3, cy - cell * 0.3,
                        cell * 0.6, cell * 0.6));
                }
            }
        }
        String cap = tokens.isEmpty() ? "пусто"
            : tokens.size() + " жет. · трофеев " + state.dumpValue();
        g.setFont(Theme.font(12.5, Font.BOLD));
        g.setColor(MAT_INK);
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
            g.setColor(Theme.alpha(MAT_INK2, 0.7));
            g.setStroke(new BasicStroke(Theme.pxf(1.5), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{Theme.pxf(6), Theme.pxf(5)}, 0f));
            g.draw(shape);
            g.setFont(Theme.font(13, Font.PLAIN));
            g.setColor(MAT_INK2);
            centred(g, "приказ круга", x + w / 2, y + h / 2 - Theme.px(6));
            centred(g, "ещё не вскрыт", x + w / 2, y + h / 2 + Theme.px(10));
            return;
        }
        g.setColor(Theme.alpha(Color.BLACK, 0.22));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 5, w, h, w * 0.08, w * 0.08));
        if (state.orderArt() != null) {
            java.awt.Shape clip = g.getClip();
            g.clip(shape);
            kelium.report.Mips.draw(g, state.orderArt(), x, y, w, h);
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
        if (state.hidden()) {
            paintBackToOwn(g, x, y, w, h);
            return;
        }
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
        g.setColor(can ? Color.WHITE : MAT_INK2);
        g.setFont(Theme.font(19, Font.BOLD));
        // ВСЕГДА «Завершить ход»: гаснет, когда нажать нельзя, а под ним — почему,
        // обычными словами (вместо «Сначала решение», которое никто не понял).
        String a = "Завершить";
        String b = "ход";
        if (!can && state.status() != null) {
            List<String> why = List.of(state.status());
            g.setFont(Theme.font(12.5, Font.PLAIN));
            int ly = ey + eh - Theme.px(12);
            for (String line : why) {
                centred(g, line, x + w / 2, ly);
            }
            g.setFont(Theme.font(19, Font.BOLD));
        }
        centred(g, a, x + w / 2, ey + eh / 2 - Theme.px(4));
        if (!b.isEmpty()) {
            centred(g, b, x + w / 2, ey + eh / 2 + Theme.px(14));
        }
        List<FieldBubbles.Opt> end = choices.get("end");
        if (can && end != null && !end.isEmpty() && end.get(0).sub() != null) {
            g.setFont(Theme.font(12, Font.PLAIN));
            g.setColor(new Color(255, 255, 255, 210));
            centred(g, end.get(0).sub(), x + w / 2, ey + eh - Theme.px(10));
        }
        spots.put("end", new Rectangle(x, ey, w, eh));
        // над плашкой — сыгранные в раунде приказы маленькой стопкой
        List<String> played = state.ordersPlayed();
        if (!played.isEmpty()) {
            int ch = Math.min(eh, h - eh - Theme.px(26));
            int cw = (int) Math.round(ch * aspect(faceOf.apply(played.get(0)), 0.643));
            if (ch > Theme.px(70)) {
                int cx = x + (w - cw) / 2;
                int cy = y + Theme.px(16);
                g.setFont(Theme.font(13, Font.BOLD));
                g.setColor(MAT_INK2);
                g.drawString("СЫГРАНО · " + played.size(), x, y + Theme.px(10));
                for (int i = 0; i < Math.min(4, played.size()); i++) {
                    BufferedImage img = faceOf.apply(played.get(played.size() - 1 - i));
                    paintBack(g, img, cx + i * Theme.px(4), cy + i * Theme.px(3), cw, ch,
                        Theme.tile());
                }
                groups.put("played", new Rectangle(cx, cy, cw + Theme.px(16), ch + Theme.px(12)));
            } else {
                // Места под стопку нет (низкое окно) — плашка со счётом,
                // щелчок раскрывает сыгранные карты так же, как стопка.
                int ph = Theme.px(26);
                int py = Math.max(y, ey - ph - Theme.px(8));
                boolean hotP = "played".equals(hoverGroup);
                RoundRectangle2D pill = new RoundRectangle2D.Double(x, py, w, ph, ph, ph);
                g.setColor(hotP ? Theme.alpha(Color.WHITE, 0.16) : Theme.alpha(Color.BLACK, 0.25));
                g.fill(pill);
                g.setColor(Theme.alpha(MAT_INK2, 0.8));
                g.setStroke(new BasicStroke(1f));
                g.draw(pill);
                g.setFont(Theme.font(13, Font.BOLD));
                g.setColor(MAT_INK);
                centred(g, "сыграно · " + played.size(), x + w / 2, py + ph / 2 + Theme.px(4));
                groups.put("played", pill.getBounds());
            }
        }
    }

    /**
     * На ЧУЖОМ СТОЛЕ вместо «Завершить ход» — возврат к своему: управлять
     * чужим столом нельзя, а дорогу назад видно сразу.
     */
    private void paintBackToOwn(Graphics2D g, int x, int y, int w, int h) {
        int eh = Math.min(h, Theme.px(118));
        int ey = y + h - eh;
        boolean hot = "back".equals(hoverKey);
        Color own = seatTabs.stream().filter(SeatTab::own).findFirst()
            .map(t -> Theme.seat(t.seat())).orElse(Theme.accent());
        RoundRectangle2D r = new RoundRectangle2D.Double(x, ey, w, eh, Theme.px(14), Theme.px(14));
        g.setColor(hot ? Theme.alpha(own, 0.35) : Theme.alpha(Color.BLACK, 0.22));
        g.fill(r);
        g.setColor(own);
        g.setStroke(new BasicStroke(Theme.pxf(1.6)));
        g.draw(r);
        g.setColor(MAT_INK);
        g.setFont(Theme.font(15, Font.BOLD));
        centred(g, "К своему", x + w / 2, ey + eh / 2 - Theme.px(4));
        centred(g, "столу", x + w / 2, ey + eh / 2 + Theme.px(13));
        g.setFont(Theme.font(12.5, Font.PLAIN));
        g.setColor(MAT_INK2);
        centred(g, "только смотреть", x + w / 2, ey + eh - Theme.px(10));
        spots.put("back", new Rectangle(x, ey, w, eh));
    }

    /** Строка, обрезанная многоточием по ширине. */
    private static String clipText(Graphics2D g, String s, int w) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= w) {
            return s;
        }
        int n = s.length();
        while (n > 1 && fm.stringWidth(s.substring(0, n) + "…") > w) {
            n--;
        }
        return s.substring(0, n) + "…";
    }

    // ---------- руки веером ----------

    /**
     * СКОЛЬКО ШИРИНЫ ЗАНИМАЮТ РУКИ НА ЕДИНИЦУ ВЫСОТЫ — по настоящему числу карт
     * (вёрстка стола, 26.09.2026). Прежде бралась оценка «2,4 карты», руки же
     * бывают шире, и их дожимал собственный множитель: зону уменьшали — приказ
     * и планшеты сужались, рукам доставалось больше места, и карты заданий
     * РОСЛИ. Теперь высота ряда подбирается по честной ширине, и уменьшается
     * всё вместе.
     */
    private double handsPerHeight() {
        if (state == null) {
            return 2.4 * 0.643;
        }
        double sum = 0;
        for (int n : new int[]{state.objectives().size(), state.superObjectives().size(),
                state.ordersInHand().size()}) {
            sum += 1 + 0.3 * (Math.max(1, n) - 1);
        }
        return sum * 0.643;
    }

    private void paintHands(Graphics2D g, int x, int y, int w, int h) {
        if (w < Theme.px(80)) {
            return;
        }
        // ТРИ РУКИ В ОДИН РЯД НА ПОЛНУЮ ВЫСОТУ (замечание дизайнера 25.09.2026:
        // супер-задание и приказы в руке ютились полосой-марками под заданиями).
        // Карты всех рук одного роста, ширина ряда делится по числу карт.
        List<Object[]> hands = new ArrayList<>();
        hands.add(new Object[]{"objectives", "ЗАДАНИЯ", state.objectives()});
        if (!state.superObjectives().isEmpty()) {
            hands.add(new Object[]{"super", "СУПЕР", state.superObjectives()});
        }
        // Рука приказов видна и пустой — пунктирным местом с «· 0»: пропавшая
        // группа читается как баг рисования, а не как «всё сыграно».
        hands.add(new Object[]{"orders", "ПРИКАЗЫ", state.ordersInHand()});
        int gap = Theme.px(16);
        int ch = h - Theme.px(22);
        double cw = ch * 0.643;
        double[] need = new double[hands.size()];
        double total = gap * (hands.size() - 1);
        for (int k = 0; k < hands.size(); k++) {
            int n = Math.max(1, ((List<?>) hands.get(k)[2]).size());
            need[k] = cw * (1 + 0.3 * (n - 1)) + Theme.px(4);
            total += need[k];
        }
        // Не влезает в ширину — весь ряд уменьшается целиком, карты не
        // наезжают друг на друга и не уходят за край зоны.
        double scale = total > w
            ? Math.max(0.35, (w - gap * (hands.size() - 1)) / (total - gap * (hands.size() - 1)))
            : 1;
        int cap = Theme.px(22);
        int hh = (int) (cap + (h - cap) * scale);
        int hy = y + (h - hh) / 2;
        int hx = x;
        for (int k = 0; k < hands.size(); k++) {
            Object[] hd = hands.get(k);
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) hd[2];
            int hw = (int) Math.round(need[k] * scale);
            fan(g, (String) hd[0], (String) hd[1], ids, hx, hy, hw, hh, false);
            hx += hw + gap;
        }
    }

    /**
     * ВЕЕР КАРТ: карты чуть повёрнуты вокруг точки под веером и заходят друг
     * на друга, наведённая поднимается. Щелчок раскрывает всю группу.
     */
    private void fan(Graphics2D g, String group, String caption, List<String> ids,
                     int x, int y, int w, int h, boolean small) {
        int capH = capH();
        // подпись стопки крупно: её читают с расстояния, как надпись на столе
        g.setFont(Theme.font(15, Font.BOLD));
        boolean chosen = ids.stream().anyMatch(id -> choices.containsKey("card:" + id));
        g.setColor(chosen ? seatColor : MAT_INK2);
        g.drawString(clipText(g, caption + " · " + ids.size()
                + (chosen ? " — щёлкните, чтобы сыграть" : ""), Math.max(w, Theme.px(40))),
            x, y + capH - Theme.px(8));
        int ch = h - capH;
        // ПРОПОРЦИЯ — ПЕЧАТНАЯ, 661×1028: у приказов и заданий она одна
        double ratio = CARD_W / (double) CARD_H;
        int cw = (int) Math.round(ch * ratio);
        int n = ids.size();
        if (n == 0) {
            g.setColor(Theme.alpha(MAT_INK2, 0.6));
            g.setStroke(new BasicStroke(Theme.pxf(1.3), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{Theme.pxf(5), Theme.pxf(4)}, 0f));
            g.draw(new RoundRectangle2D.Double(x, y + capH + Theme.px(2), cw, ch,
                cw * 0.08, cw * 0.08));
            return;
        }
        double step = n <= 1 ? 0 : Math.min(cw * 0.78, (w - cw) / (double) (n - 1));
        step = Math.min(step, cw * 0.78);
        step = Math.max(cw * 0.22, step);
        double spreadDeg = small ? 0 : Math.min(18, n * 4.0);
        int baseY = y + capH + Theme.px(4);
        Rectangle area = new Rectangle(x, y, (int) (cw + step * (n - 1)) + Theme.px(8), h);
        if (!state.hidden()) {
            groups.put(group, area);
        }
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
            // ЧУЖАЯ РУКА — рубашками: смотреть можно только открытое
            boolean closed = state.hidden();
            BufferedImage face = closed
                ? ("orders".equals(group) ? state.orderBack()
                    : backOf.apply("super".equals(group) ? "super" : "objective"))
                : faceOf.apply(id);
            RoundRectangle2D local = new RoundRectangle2D.Double(0, 0, cw, ch, cw * 0.08, cw * 0.08);
            if (face != null) {
                gc.clip(local);
                kelium.report.Mips.draw(gc, face, 0, 0, cw, ch);
                gc.setClip(null);
            } else {
                // ПЕЧАТИ НЕТ — рисуем карту, а не белый прямоугольник: цвет
                // колоды, название колоды и имя карты (супер-задания пока без
                // печатного лица).
                boolean sup = "super".equals(group);
                Color top = sup ? new Color(0xE0B04A) : new Color(0x3F7FB8);
                Color bot = sup ? new Color(0x7A5212) : new Color(0x1E3F5E);
                gc.setPaint(new GradientPaint(0, 0, top, 0, ch, bot));
                gc.fill(local);
                gc.setColor(new Color(255, 255, 255, 60));
                gc.setStroke(new BasicStroke(Theme.pxf(1.2)));
                gc.draw(new RoundRectangle2D.Double(Theme.px(3), Theme.px(3), cw - Theme.px(6),
                    ch - Theme.px(6), cw * 0.07, cw * 0.07));
                gc.setColor(new Color(255, 255, 255, 220));
                gc.setFont(Theme.font(small ? 7 : 9, Font.BOLD));
                String head = sup ? "СУПЕР-ЗАДАНИЕ" : "ЗАДАНИЕ";
                FontMetrics hm = gc.getFontMetrics();
                gc.drawString(FieldBubbles.clip(hm, head, cw - Theme.px(8)),
                    (cw - Math.min(cw - Theme.px(8), hm.stringWidth(head))) / 2,
                    Theme.px(small ? 11 : 15));
                gc.setColor(Color.WHITE);
                gc.setFont(Theme.font(small ? 9 : 12, Font.BOLD));
                wrap(gc, nameOf.apply(id), Theme.px(5), ch / 2 - Theme.px(4),
                    cw - Theme.px(10), 3);
            }
            boolean can = choices.containsKey("card:" + id);
            gc.setColor(can ? seatColor : Theme.alpha(Color.BLACK, 0.3));
            gc.setStroke(new BasicStroke(can ? Theme.pxf(2.6) : 1f));
            gc.draw(local);
            String tag = tagOf.apply(id);
            if (tag != null && !small) {
                gc.setFont(Theme.font(12, Font.BOLD));
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
            if (!closed) {
                fanCards.add(new Object[]{id, card});
            }
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
            kelium.report.Mips.draw(g, img, x, y, w, h);
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
        g.setFont(Theme.font(12.5, Font.BOLD));
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

    /** Ширина к высоте у картинки; нет картинки — запасная пропорция. */
    static double aspect(BufferedImage img, double fallback) {
        return img == null || img.getHeight() == 0 ? fallback
            : img.getWidth() / (double) img.getHeight();
    }

    private static void centred(Graphics2D g, String s, int cx, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, baseline);
    }
}
