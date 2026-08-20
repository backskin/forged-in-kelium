package kelium.gui.replay2;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
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

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import kelium.report.ReplayRecord;

/**
 * КАРТЫ ПРИКАЗОВ ИГРОКА — панель, выезжающая из-под его полосы (заказ дизайнера
 * 19.08.2026).
 *
 * <p>ЧТО ПОКАЗЫВАЕТ, в порядке слева направо, ровно как это лежит на столе:
 * <ul>
 *   <li><b>слева</b> — СТОПКА уже разыгранных карт, лицом вверх, со сдвигом,
 *       чтобы читалась глубина стопки;</li>
 *   <li><b>по центру</b> — карта ЭТОГО круга, крупнее и с рамкой: она сейчас
 *       и работает;</li>
 *   <li><b>справа</b> — то, что осталось В РУКЕ, веером: одна карта стоит
 *       прямо, несколько разворачиваются вокруг общей точки ниже панели, как
 *       держат карты в руке.</li>
 * </ul>
 *
 * <p>ПОЧЕМУ ВЕЕР СЧИТАЕТСЯ ВОКРУГ ТОЧКИ НИЖЕ ПАНЕЛИ, а не вокруг центра карты:
 * иначе карты расходятся «звёздочкой» и наезжают друг на друга низом. Ось
 * вращения у настоящей руки — в кисти, то есть ниже карт, и от этого веер
 * раскрывается вверх, а корешки остаются рядом.
 *
 * <p>Панель НЕ ХРАНИТ СОСТОЯНИЕ ПАРТИИ: всё берётся из кадра записи в момент
 * рисования ({@code snapshot.players.get(seat)} — там лежат {@code orderPlayed},
 * {@code orderHand} и {@code orderSetAside}), а карта круга — из
 * {@code record.orderPlays}. Значит панель верна на любом кадре, включая
 * промотку назад, и не может разойтись с полосой игрока.
 *
 * <p>{@link #setOpen(double)} — доля выезда от 0 до 1; ею анимируют появление.
 * Само движение и место панели задаёт тот, кто её вставил: панель только
 * рисует себя и умеет сказать, какая карта под курсором.
 */
public final class OrderCardsPanel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Пропорции карты приказа — те же, что у таблицы приказов. */
    private static final double CARD_RATIO = 184.0 / 132.0;
    /** Наклон между соседними картами веера, градусы. */
    private static final double FAN_STEP_DEG = 11;
    /** Максимальный разворот крайней карты веера, градусы. */
    private static final double FAN_MAX_DEG = 34;

    private final Session session;
    private int seat;
    private double open = 1.0;

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

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(320), Theme.px(96));
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
     * Карта ЭТОГО круга: последняя вскрытая на текущем кадре.
     *
     * <p>Берётся из {@code orderPlays}, а не из руки: только там видно, какая
     * карта уже вскрыта, а какая ещё лежит рубашкой вверх — по самой руке этого
     * не понять.
     */
    private ReplayRecord.OrderPlay current() {
        ReplayRecord rec = session.record();
        ReplayRecord.Frame f = session.frame();
        if (rec == null || f == null) {
            return null;
        }
        int idx = session.cursor();
        ReplayRecord.OrderPlay best = null;
        for (ReplayRecord.OrderPlay op : rec.orderPlays) {
            if (op.seat != seat || op.round != f.round || op.revealFrame > idx) {
                continue;
            }
            if (best == null || op.circle > best.circle) {
                best = op;
            }
        }
        return best;
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
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();
        double pad = Theme.px(6);

        ReplayRecord.Player p = me();
        if (p == null) {
            g.dispose();
            return;
        }

        // ВЫЕЗД ЦЕЛИКОМ, ВМЕСТЕ С ПОДЛОЖКОЙ. Сдвиг ставится ДО подложки, иначе
        // фон панели возникал бы скачком в полный размер, а карты подъезжали
        // внутри него — это читалось бы не как «выехала панель», а как «мигнул
        // прямоугольник». Сдвиг на полную высоту: при open = 0 всё содержимое
        // уходит ниже своей же нижней кромки и обрезается, то есть панель
        // буквально прячется под полосу игрока.
        g.translate(0, (1 - open) * h);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
            (float) Math.min(1.0, 0.35 + 0.65 * open)));

        // ПОДЛОЖКА. Панель перекрывает поле, поэтому она обязана быть плотной —
        // сквозь полупрозрачную читались бы гексы, и карты стали бы неразборчивы.
        Shape back = new RoundRectangle2D.Double(0, 0, w - 1, h - 1,
            Theme.px(10), Theme.px(10));
        g.setColor(Theme.panel());
        g.fill(back);
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(back);

        double cardH = h - 2 * pad;
        double cardW = cardH / CARD_RATIO;

        // ---------- слева: стопка разыгранных ----------
        double x = pad;
        List<String> played = new ArrayList<>(p.orderPlayed);
        ReplayRecord.OrderPlay cur = current();
        // Карта круга не должна попасть и в стопку: на столе она лежит отдельно,
        // перед игроком, а не поверх отыгранных.
        if (cur != null && !played.isEmpty() && played.get(played.size() - 1).equals(cur.card)) {
            played.remove(played.size() - 1);
        }
        double stackStep = Theme.px(11);
        for (int i = 0; i < played.size(); i++) {
            double cx = x + i * stackStep;
            drawCard(g, cx, pad, cardW, cardH, played.get(i), false, 0,
                "Разыграно: " + Names.order(played.get(i)));
        }
        double stackW = played.isEmpty() ? 0 : cardW + (played.size() - 1) * stackStep;

        // ---------- по центру: карта этого круга ----------
        double centreX = Math.max(x + stackW + Theme.px(10), (w - cardW) / 2.0);
        if (cur != null) {
            StringBuilder tip = new StringBuilder();
            tip.append("Круг ").append(cur.circle).append(" · ").append(Names.order(cur.top));
            if (cur.coincided) {
                tip.append("\nПриказ СОВПАЛ — верх не сработал");
            }
            if (cur.bottom != null && cur.bottomOpen) {
                tip.append("\nСнизу: ").append(Names.order(cur.bottom));
            }
            drawCard(g, centreX, pad - Theme.px(3), cardW, cardH + Theme.px(6),
                cur.top, true, 0, tip.toString());
        }

        // ---------- справа: рука веером ----------
        List<String> hand = p.orderHand;
        if (!hand.isEmpty()) {
            int n = hand.size();
            double step = n <= 1 ? 0 : Math.min(FAN_STEP_DEG, FAN_MAX_DEG * 2 / (n - 1));
            double first = -step * (n - 1) / 2.0;
            // ОСЬ ВРАЩЕНИЯ НИЖЕ ПАНЕЛИ — как кисть руки, держащей карты.
            double pivotY = h + cardH * 0.55;
            // МЕСТО ПОД РАЗВОРОТ. Поворот вокруг точки НИЖЕ панели уводит верх
            // карты в сторону тем сильнее, чем дальше ось: при первой сборке
            // крайняя карта веера уезжала за правый край панели и обрезалась.
            // Считаем этот вылет и отступаем на него от края.
            double maxAngle = Math.toRadians(Math.max(Math.abs(first), Math.abs(first + step * (n - 1))));
            double lean = (pivotY - pad) * Math.sin(maxAngle);
            double pivotX = w - pad - cardW / 2.0 - lean;
            for (int i = 0; i < n; i++) {
                double angle = first + step * i;
                AffineTransform saved = g.getTransform();
                g.rotate(Math.toRadians(angle), pivotX, pivotY);
                drawCard(g, pivotX - cardW / 2.0, pad, cardW, cardH,
                    hand.get(i), false, angle, "В руке: " + Names.order(hand.get(i)));
                g.setTransform(saved);
            }
        }

        // ---------- отложенная карта ----------
        if (p.orderSetAside != null && !p.orderSetAside.isBlank()) {
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.ink3());
            g.drawString("отложена: " + Names.order(p.orderSetAside),
                (float) pad, (float) (h - Theme.px(3)));
        }
        g.dispose();
    }

    /**
     * Одна карта приказа: подложка, рамка, название.
     *
     * <p>Область для подсказки запоминается В ЭКРАННЫХ координатах — с учётом
     * поворота веера. Иначе подсказка у наклонённой карты ловилась бы по
     * невернутому прямоугольнику и срабатывала бы не там, где карта нарисована.
     */
    private void drawCard(Graphics2D g, double x, double y, double w, double h,
                          String code, boolean active, double angleDeg, String tip) {
        Shape card = new RoundRectangle2D.Double(x, y, w, h, Theme.px(6), Theme.px(6));
        g.setColor(active ? Theme.paper() : Theme.tile());
        g.fill(card);
        g.setColor(active ? Theme.accent() : Theme.border());
        g.setStroke(new BasicStroke(active ? 2f : 1f));
        g.draw(card);

        // НАЗВАНИЕ ВДОЛЬ КАРТЫ. Карта узкая, поперёк текст не влезает даже
        // сокращённым, поэтому подпись повёрнута на 90 градусов — читается
        // снизу вверх, как на корешке книги.
        String name = Names.order(code);
        g.setFont(Theme.font(active ? 11 : 10, active ? Font.BOLD : Font.PLAIN));
        AffineTransform saved = g.getTransform();
        g.translate(x + w / 2.0, y + h - Theme.px(6));
        g.rotate(-Math.PI / 2);
        g.setColor(active ? Theme.ink() : Theme.ink2());
        var fm = g.getFontMetrics();
        String shown = name;
        int maxW = (int) (h - Theme.px(12));
        while (shown.length() > 1 && fm.stringWidth(shown) > maxW) {
            shown = shown.substring(0, shown.length() - 1);
        }
        g.drawString(shown, 0, (float) (fm.getAscent() / 2.0 - Theme.px(1)));
        g.setTransform(saved);

        hits.add(new Hit(g.getTransform().createTransformedShape(card), tip));
    }

    // ==================================================================
    //  ПОДСКАЗКИ
    // ==================================================================

    @Override
    public String getToolTipText(MouseEvent e) {
        // ПОСЛЕДНЯЯ НАРИСОВАННАЯ ПОБЕЖДАЕТ: карты веера и стопки перекрывают
        // друг друга, и под курсором должна оказаться та, что лежит СВЕРХУ.
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).area().contains(e.getX(), e.getY())) {
                return "<html>" + hits.get(i).tip().replace("\n", "<br>") + "</html>";
            }
        }
        return null;
    }

    @Override
    public java.awt.Point getToolTipLocation(MouseEvent e) {
        // Подсказка чуть в стороне от курсора: иначе она накрывает ту самую
        // карту, о которой рассказывает.
        return new java.awt.Point(e.getX() + Theme.px(12), e.getY() - Theme.px(8));
    }
}
