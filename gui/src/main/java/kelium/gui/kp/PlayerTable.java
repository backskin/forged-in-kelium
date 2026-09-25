package kelium.gui.kp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JComponent;

import kelium.gui.replay2.Theme;

/**
 * СТОЛ ИГРОКА — нижняя зона живой партии, собранная из КОМПОНЕНТОВ, а не из
 * кнопок (просьба дизайнера 25.09.2026: «никаких кнопок слева снизу и справа
 * снизу; я хочу взаимодействовать со своими компонентами — внизу с картой,
 * которую я разыграл, нажимать на ней; на планшете выбрать жетончик для
 * стройки»).
 *
 * <p>Слева направо: печатные планшеты хранилища и войск с живыми жетонами;
 * вскрытый приказ круга — его действия нажимаются прямо на карте, по кругам
 * напечатанных действий; плашка «Завершить ход» рядом с картой; руки заданий и
 * арсенала лицами карт, веером.
 *
 * <p>Что сейчас можно выбрать, задаёт окно партии одним списком «ключ →
 * варианты» ({@link #setChoices}): {@code action:build}, {@code end},
 * {@code card:o12}, {@code building:miner:2}, {@code red:infantry}… Ключ с
 * одним вариантом играется щелчком сразу; с несколькими — щелчок раскрывает
 * пузырь вариантов у самой детали. Выбираемое обведено цветом места.
 */
public final class PlayerTable extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Кто рисует печатные планшеты (их умеет лист игрока проигрывателя). */
    public interface BoardsArt {
        /** Ширина к высоте; 0 — печатных планшетов нет. */
        double aspect();

        /** Нарисовать и записать зоны щелчка и контуры деталей; вернуть высоту. */
        int paint(Graphics2D g, int x, int y, int width, Map<String, Rectangle> hits,
                  Map<String, java.awt.Shape> outlines);
    }

    /**
     * Что лежит перед игроком.
     *
     * @param orderId  вскрытый в этом круге приказ; {@code null} — ещё не вскрыт
     * @param orderArt печатное лицо приказа; {@code null} — рисуем сами
     */
    public record State(int seat, String seatName, String orderId, OrderCardFace.Info orderInfo,
                        BufferedImage orderArt, List<String> objectives,
                        List<String> arsenalHand, List<String> arsenalInstalled,
                        int ordersInHand, String status) {
    }

    private BoardsArt boards;
    private State state;
    private Function<String, BufferedImage> faceOf = id -> null;
    private Function<String, String> nameOf = id -> id;
    private Function<String, String> tagOf = id -> null;
    private BiConsumer<String, Rectangle> onHoverCard = (id, r) -> { };
    private Runnable onHoverOff = () -> { };

    private final FieldBubbles bubbles = new FieldBubbles();
    private Map<String, List<FieldBubbles.Opt>> choices = Map.of();
    private Color seatColor = Theme.accent();

    /** Где что лежит после последней отрисовки. */
    private final Map<String, Rectangle> spots = new LinkedHashMap<>();
    private final Map<String, Rectangle> cardSpots = new LinkedHashMap<>();
    private final Map<String, java.awt.Shape> outlines = new LinkedHashMap<>();
    private String hoverKey;
    private String hoverCard;

    public PlayerTable() {
        setOpaque(true);
        setFont(Theme.body());
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                boolean repaint = bubbles.hover(p);
                String key = bubbles.covers(p) ? null : keyAt(p);
                String card = bubbles.covers(p) ? null : cardAt(p);
                if (!java.util.Objects.equals(key, hoverKey)) {
                    hoverKey = key;
                    repaint = true;
                }
                if (!java.util.Objects.equals(card, hoverCard)) {
                    hoverCard = card;
                    repaint = true;
                    if (card == null) {
                        onHoverOff.run();
                    } else {
                        onHoverCard.accept(card, cardSpots.get(card));
                    }
                }
                boolean hand = bubbles.hovering() || key != null && choices.containsKey(key);
                setCursor(hand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
                if (repaint) {
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverKey = null;
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

    public void setCards(Function<String, BufferedImage> faceOf, Function<String, String> nameOf,
                         Function<String, String> tagOf) {
        this.faceOf = faceOf;
        this.nameOf = nameOf;
        this.tagOf = tagOf;
    }

    public void onCardHover(BiConsumer<String, Rectangle> over, Runnable off) {
        this.onHoverCard = over;
        this.onHoverOff = off;
    }

    public void setState(State s) {
        this.state = s;
        dirty = true;
        repaint();
    }

    /** Что можно выбрать на столе сейчас (пусто — ничего). */
    public void setChoices(Map<String, List<FieldBubbles.Opt>> byKey, Color seatColor) {
        this.choices = byKey == null ? Map.of() : new LinkedHashMap<>(byKey);
        this.seatColor = seatColor == null ? Theme.accent() : seatColor;
        // Единственная деталь с несколькими вариантами раскрывает пузырь сама
        // (так устроен FieldBubbles); при нескольких деталях — по щелчку.
        bubbles.set(this.choices, null, null, null, this.seatColor);
        repaint();
    }

    public void clearChoices() {
        setChoices(null, null);
    }

    public void closeBubble() {
        bubbles.closeBubble();
        repaint();
    }

    /** Раскрыть пузырь вариантов у детали (например, у карты по наведению). */
    public void openBubble(String key) {
        if (choices.containsKey(key)) {
            bubbles.clickHex(key);
            repaint();
        }
    }

    /**
     * Нарисована ли деталь с таким ключом. Зоны щелчка знает только
     * отрисовка, а решение приходит раньше перерисовки — поэтому, если стол
     * менялся после последней отрисовки, он прорисовывается в пустую картинку,
     * чтобы зоны были свежими.
     */
    public boolean hasSpot(String key) {
        if (dirty && getWidth() > 0 && getHeight() > 0) {
            BufferedImage scratch = new BufferedImage(getWidth(), getHeight(),
                BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scratch.createGraphics();
            paintComponent(g);
            g.dispose();
        }
        return spots.containsKey(key);
    }

    /** Стол менялся после последней отрисовки. */
    private boolean dirty = true;

    public Rectangle cardRect(String id) {
        Rectangle r = cardSpots.get(id);
        return r == null ? null : new Rectangle(r);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(1200), Theme.px(290));
    }

    private String keyAt(Point p) {
        // Карты — поверх планшетов, мелкие детали планшета — поверх самого
        // планшета: ищем от самого узкого к самому широкому.
        String best = null;
        long bestArea = Long.MAX_VALUE;
        for (Map.Entry<String, Rectangle> e : spots.entrySet()) {
            Rectangle r = e.getValue();
            if (r.contains(p)) {
                long a = (long) r.width * r.height;
                boolean chosen = choices.containsKey(e.getKey());
                // выбираемое важнее невыбираемого того же места
                if (chosen) {
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

    private String cardAt(Point p) {
        String hit = null;
        for (Map.Entry<String, Rectangle> e : cardSpots.entrySet()) {
            if (e.getValue().contains(p)) {
                hit = e.getKey();     // последняя нарисованная лежит сверху
            }
        }
        return hit;
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
        // СТОЛ — чуть темнее поля: компоненты игрока лежат на своей части стола.
        g.setColor(Theme.isDark() ? Theme.bg() : Theme.lighten(Theme.border(), 0.35));
        g.fillRect(0, 0, w, h);
        g.setColor(Theme.border());
        g.fillRect(0, 0, w, 1);
        spots.clear();
        cardSpots.clear();
        if (state == null) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("стол игрока появится, когда партия начнётся", Theme.px(16), Theme.px(28));
            g.dispose();
            return;
        }
        int pad = Theme.px(10);
        int innerH = h - pad * 2;
        int x = pad;

        // ---- печатные планшеты
        if (boards != null && boards.aspect() > 0) {
            // Под сцепкой из паза торчат карты арсенала — оставляем им место.
            int bw = (int) Math.min(w * 0.55, innerH * 0.9 * boards.aspect());
            int bh = (int) Math.round(bw / boards.aspect());
            int by = pad + Math.max(0, (int) (innerH * 0.9) - bh) / 2;
            Map<String, Rectangle> hits = new LinkedHashMap<>();
            outlines.clear();
            boards.paint(g, x, by, bw, hits, outlines);
            spots.putAll(hits);
            x += bw + Theme.px(14);
        }

        // ---- вскрытый приказ круга
        int cardH = innerH;
        int cardW = (int) Math.round(cardH * 661 / 1028.0);
        paintOrder(g, x, pad, cardW, cardH);
        x += cardW + Theme.px(8);

        // ---- плашка «Завершить ход»
        int endW = Theme.px(112);
        paintEnd(g, x, pad, endW, cardH);
        x += endW + Theme.px(16);

        // ---- руки: задания и арсенал
        paintHands(g, x, pad, w - x - pad, innerH);

        // ---- обводка всего, что можно выбрать
        for (String key : choices.keySet()) {
            Rectangle r = spots.get(key);
            if (r == null || key.startsWith("action:") || "end".equals(key)) {
                continue;
            }
            boolean hot = key.equals(hoverKey);
            // по настоящему контуру детали, если он известен (повёрнутые жетоны)
            java.awt.Shape outline = outlines.get(key);
            java.awt.Shape glow = outline != null ? outline
                : new RoundRectangle2D.Double(r.x - 3, r.y - 3, r.width + 6, r.height + 6,
                    Theme.px(10), Theme.px(10));
            g.setColor(Theme.alpha(seatColor, hot ? 0.28 : 0.12));
            g.fill(glow);
            g.setColor(seatColor);
            g.setStroke(new BasicStroke(hot ? Theme.pxf(3) : Theme.pxf(2),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(glow);
        }

        // ---- пузырь вариантов у детали
        bubbles.paint(g, w, h, key -> {
            Rectangle r = spots.get(key);
            return r == null ? null : new Point2D.Double(r.getCenterX(), r.getCenterY());
        }, Theme.px(30));
        g.dispose();
    }

    /**
     * Точки действий на ПЕЧАТНОЙ карте приказа: два круга в верхней половине,
     * два в нижней — так нарисованы все карты набора «симметрия».
     */
    private static final double[][] SPOTS = {{0.245, 0.335}, {0.735, 0.335},
        {0.245, 0.80}, {0.735, 0.80}};

    private void paintOrder(Graphics2D g, int x, int y, int w, int h) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, w * 0.08, w * 0.08);
        if (state.orderId() == null) {
            g.setColor(Theme.border());
            g.setStroke(new BasicStroke(Theme.pxf(1.5), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 10f, new float[]{Theme.pxf(6), Theme.pxf(5)}, 0f));
            g.draw(shape);
            g.setFont(Theme.font(11, Font.PLAIN));
            g.setColor(Theme.ink3());
            centred(g, "приказ круга", x + w / 2, y + h / 2 - Theme.px(6));
            centred(g, "ещё не вскрыт", x + w / 2, y + h / 2 + Theme.px(10));
            if (state.ordersInHand() > 0) {
                centred(g, "в руке: " + state.ordersInHand(), x + w / 2, y + h - Theme.px(14));
            }
            return;
        }
        // тень-торец картонки
        g.setColor(Theme.alpha(Color.BLACK, 0.18));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 4, w, h, w * 0.08, w * 0.08));
        if (state.orderArt() != null) {
            java.awt.Shape clip = g.getClip();
            g.clip(shape);
            g.drawImage(state.orderArt(), x, y, w, h, null);
            g.setClip(clip);
        } else if (state.orderInfo() != null) {
            paintOrderBack(g, state.orderInfo(), shape, x, y, w, h);
        }
        spots.put("order", new Rectangle(x, y, w, h));

        // действия — по кругам напечатанных действий
        List<String> actions = orderActions();
        boolean deciding = choices.keySet().stream().anyMatch(k -> k.startsWith("action:"));
        for (int i = 0; i < actions.size(); i++) {
            String a = actions.get(i);
            double[] c;
            double r;
            if (state.orderInfo() != null && state.orderInfo().joker()) {
                // БЕЗОПАСНОСТЬ: восемь действий сеткой 2×4
                c = new double[]{i % 2 == 0 ? 0.27 : 0.73, 0.30 + (i / 2) * 0.17};
                r = w * 0.11;
            } else {
                if (i >= SPOTS.length) {
                    break;
                }
                c = SPOTS[i];
                r = w * 0.155;
            }
            double cx = x + c[0] * w;
            double cy = y + c[1] * h;
            Rectangle hit = new Rectangle((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));
            String key = "action:" + a;
            spots.put(key, hit);
            if (state.orderArt() == null && !a.isEmpty()) {
                // ПЕЧАТИ НЕТ — рисуем круг действия так же, как на печати:
                // светлый диск, значок, подпись под ним.
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
                String nm = ActionBar.ACTIONS.getOrDefault(a, a).toLowerCase(java.util.Locale.ROOT);
                centred(g, nm, (int) cx, (int) (cy + r + g.getFontMetrics().getAscent()));
            }
            if (!deciding) {
                continue;
            }
            boolean can = choices.containsKey(key);
            Ellipse2D ring = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
            if (!can) {
                // недоступное гасится, но место сохраняет
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
        if (deciding) {
            // Половина, которой сейчас не играют, гасится целиком.
            for (int half = 0; half < 2; half++) {
                boolean any = false;
                for (int i = half * 2; i < Math.min(actions.size(), half * 2 + 2); i++) {
                    any |= choices.containsKey("action:" + actions.get(i));
                }
                if (!any && !(state.orderInfo() != null && state.orderInfo().joker())) {
                    int top = half == 0 ? (int) (y + h * 0.16) : (int) (y + h * 0.62);
                    int bot = half == 0 ? (int) (y + h * 0.58) : (int) (y + h * 0.97);
                    g.setColor(Theme.alpha(Theme.isDark() ? Color.BLACK : Color.WHITE, 0.35));
                    g.fillRect(x + 2, top, w - 4, bot - top);
                }
            }
        }
        g.setColor(Theme.alpha(Color.BLACK, 0.35));
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);
    }

    /**
     * ОСНОВА КАРТЫ ПРИКАЗА, если печатного лица нет: та же раскладка, что у
     * печати «симметрия» — полоса колоды с названием верхней половины, внизу
     * полоса нижней. Круги действий поверх рисует {@link #paintOrder}.
     */
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
        String top = info.joker() ? "БЕЗОПАСНОСТЬ" : ActionIcons.categoryRu(info.top());
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
            g.setColor(Theme.alpha(Color.BLACK, 0.25));
            g.fillRect(x, by - 1, w, 1);
        } else if (info.joker()) {
            g.setFont(Theme.font(Math.max(9, w / 16.0), Font.PLAIN));
            g.setColor(Theme.ink2());
            centred(g, "любые два разных действия", x + w / 2, (int) (y + h * 0.17));
        }
        g.setClip(clip);
    }

    /** Действия карты по порядку печати: сверху два, снизу два. */
    private List<String> orderActions() {
        return actionsOf(state.orderInfo());
    }

    /**
     * Какие действия напечатаны на карте, по порядку мест: два сверху, два
     * снизу (у безопасности — все восемь). Окно по нему узнаёт, есть ли у
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
        int eh = Math.min(h, Theme.px(120));
        int ey = y + (h - eh) / 2;
        RoundRectangle2D r = new RoundRectangle2D.Double(x, ey, w, eh, Theme.px(14), Theme.px(14));
        g.setColor(can ? (hot ? Theme.lighten(seatColor, 0.12) : seatColor) : Theme.tile());
        g.fill(r);
        g.setColor(can ? Theme.darken(seatColor, 0.2) : Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(r);
        g.setColor(can ? Color.WHITE : Theme.ink3());
        g.setFont(Theme.font(14, Font.BOLD));
        String a = can ? "Завершить" : "Ход";
        String b = can ? "ход" : "соперника";
        if (!can && state.status() != null) {
            // «сначала решение», «ход соперника» — двумя строками
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
    }

    private void paintHands(Graphics2D g, int x, int y, int w, int h) {
        if (w < Theme.px(60)) {
            return;
        }
        int capH = Theme.px(16);
        int maxH = h - capH - Theme.px(4);
        List<String> obj = state.objectives();
        List<String> ars = new ArrayList<>(state.arsenalInstalled());
        ars.addAll(state.arsenalHand());
        int groups = (obj.isEmpty() ? 0 : 1) + (ars.isEmpty() ? 0 : 1);
        int gapGroup = Theme.px(18);
        int gapCard = Theme.px(8);
        int total = obj.size() + ars.size();
        if (total == 0) {
            g.setFont(Theme.italic());
            g.setColor(Theme.ink3());
            g.drawString("на руке нет ни заданий, ни арсенала", x, y + Theme.px(20));
            return;
        }
        // СНАЧАЛА КАРТЫ УМЕНЬШАЮТСЯ (до двух третей роста), и только потом
        // заходят друг на друга веером: лицо карты должно читаться.
        int gaps = gapGroup * Math.max(0, groups - 1) + gapCard * Math.max(0, total - groups);
        int fitW = (w - gaps) / total;
        int cardW = (int) Math.round(maxH * 0.70);
        cardW = Math.max((int) (cardW * 0.66), Math.min(cardW, fitW));
        int cardH = (int) Math.round(cardW / 0.70);
        double step = cardW + gapCard;
        if (fitW < cardW) {
            int room = w - gapGroup * Math.max(0, groups - 1) - cardW * groups;
            step = Math.max(cardW * 0.28, room / (double) Math.max(1, total - groups));
        }
        int cx = x;
        if (!obj.isEmpty()) {
            cx = group(g, "ЗАДАНИЯ", obj, cx, y, capH, cardW, cardH, step, false);
            cx += gapGroup;
        }
        if (!ars.isEmpty()) {
            group(g, "АРСЕНАЛ", ars, cx, y, capH, cardW, cardH, step, true);
        }
    }

    private int group(Graphics2D g, String caption, List<String> ids, int x, int y, int capH,
                      int cardW, int cardH, double step, boolean arsenal) {
        g.setFont(Theme.caption());
        g.setColor(Theme.ink3());
        g.drawString(caption + " · " + ids.size(), x, y + capH - Theme.px(4));
        int cy = y + capH + Theme.px(2);
        double cx = x;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            boolean installed = arsenal && state.arsenalInstalled().contains(id);
            boolean hot = id.equals(hoverCard);
            int lift = hot ? Theme.px(10) : 0;
            paintCard(g, id, (int) Math.round(cx), cy - lift, cardW, cardH, installed);
            cardSpots.put(id, new Rectangle((int) Math.round(cx), cy - lift, cardW, cardH));
            spots.put("card:" + id, new Rectangle((int) Math.round(cx), cy - lift, cardW, cardH));
            cx += i < ids.size() - 1 ? step : cardW;
        }
        return (int) Math.round(cx);
    }

    private void paintCard(Graphics2D g, String id, int x, int y, int w, int h, boolean installed) {
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, w * 0.08, w * 0.08);
        g.setColor(Theme.alpha(Color.BLACK, 0.2));
        g.fill(new RoundRectangle2D.Double(x + 2, y + 3, w, h, w * 0.08, w * 0.08));
        BufferedImage face = faceOf.apply(id);
        if (face != null) {
            java.awt.Shape clip = g.getClip();
            g.clip(shape);
            g.drawImage(face, x, y, w, h, null);
            g.setClip(clip);
        } else {
            g.setColor(Theme.panel());
            g.fill(shape);
            g.setColor(installed ? Theme.container() : Theme.points());
            g.fill(new RoundRectangle2D.Double(x, y, w, Theme.px(12), w * 0.08, w * 0.08));
            g.setColor(Theme.ink());
            g.setFont(Theme.font(11, Font.BOLD));
            wrap(g, nameOf.apply(id), x + Theme.px(6), y + Theme.px(28), w - Theme.px(12), 3);
        }
        g.setColor(Theme.alpha(Color.BLACK, 0.3));
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);
        String tag = tagOf.apply(id);
        if (installed) {
            tag = "установлена";
        }
        if (tag != null) {
            g.setFont(Theme.font(9, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(tag) + Theme.px(10);
            int th = Theme.px(16);
            int tx = x + w - tw - Theme.px(4);
            int ty = y + h - th - Theme.px(4);
            g.setColor(installed ? Theme.container() : Theme.kelium());
            g.fill(new RoundRectangle2D.Double(tx, ty, tw, th, th, th));
            g.setColor(Color.WHITE);
            g.drawString(tag, tx + Theme.px(5), ty + th - Theme.px(4));
        }
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
