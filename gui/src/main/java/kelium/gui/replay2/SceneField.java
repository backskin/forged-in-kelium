package kelium.gui.replay2;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.MouseInputAdapter;

import kelium.report.FieldGeometry;
import kelium.report.ReplayRecord;

/**
 * SceneField — ПОЛЕ КАК СЦЕНА: главный и единственный крупный вид приложения.
 *
 * <p>Отрисовка гекса идёт через общий {@link kelium.report.FieldPainter} — тот же
 * код, что рисует картинки в отчётах. Своё здесь только то, чего в отчёте нет:
 *
 * <ul>
 *   <li><b>Фокус кадра.</b> Всё, что не участвует в событии этого шага, пригашается,
 *       а участники остаются в полную силу. В версии 1.0 «что произошло» объяснялось
 *       рамкой на треть поля со строкой лога внутри — она отбирала место у того
 *       самого поля, которому его и не хватало.</li>
 *   <li><b>Разметка действия:</b> дуга перемещения, зубец удара, кольцо стройки,
 *       серый контур на месте уничтоженного жетона.</li>
 *   <li><b>Титр</b> 240×64 в углу поля вместо панели.</li>
 *   <li><b>Слои</b> сверх базовых: шлейфы движения за раунд, тепловая карта боёв,
 *       номера кругов у построек.</li>
 * </ul>
 *
 * <p>Поле лежит на «бумажной» подложке: авторские цвета жетонов рисовались для белой
 * бумаги, и на тёмном фоне их надо показывать в том же окружении.
 */
public final class SceneField extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final double BASE = FieldGeometry.DEFAULT_SIZE;

    /** Слои поля. Порядок — как в меню и на клавишах 1…9. */
    public enum Layer {
        IDS("координаты гексов", false),
        DAMAGE("кубики урона", true),
        ENERGY("ячейки и кубики энергии", true),
        KELIUM("остаток келемия на тайлах", true),
        OWNERSHIP("подкраску владения", true),
        BUILD_ZONES("зоны стройки", false),
        TRAILS("шлейфы движения за раунд", false),
        HEATMAP("тепловую карту боёв", false),
        CIRCLES("номера кругов у построек", false);

        public final String label;
        public final boolean byDefault;

        Layer(String label, boolean byDefault) {
            this.label = label;
            this.byDefault = byDefault;
        }
    }

    private final Session session;
    private final Set<Layer> layers = new HashSet<>();
    private boolean focusMode = true;
    private boolean showTitle = true;

    private double zoom = 1.0;
    private double panX;
    private double panY;
    private boolean autoFit = true;
    private boolean fitPending = true;

    /** Мигание урона и вспышек: заметно, но не мельтешит. */
    private boolean blink = true;
    private final Timer blinkTimer;
    /** Пока идёт быстрая прокрутка, украшения не рисуем — иначе окно захлёбывается. */
    private boolean cheapMode;

    private Consumer<String> onHexClick = id -> { };
    /** Координаты гексов — считаются раз на запись, а не на каждую отрисовку. */
    private Map<String, ReplayRecord.HexInfo> hexIndex = new LinkedHashMap<>();

    public SceneField(Session session) {
        this.session = session;
        for (Layer l : Layer.values()) {
            if (l.byDefault) {
                layers.add(l);
            }
        }
        applyPainterFlags();
        setOpaque(true);
        // НА ПОЛЕ — УЗКОЕ НАЧЕРТАНИЕ: подписи жетонов идут вдоль стенок, места там
        // на две-три буквы, и узкий шрифт влезает туда, где обычный пришлось бы
        // мельчить (решение 13.08.2026).
        setFont(Theme.narrow(13, java.awt.Font.PLAIN));
        setFocusable(true);
        ToolTipManager.sharedInstance().registerComponent(this);

        MouseInputAdapter mouse = new MouseInputAdapter() {
            private Point drag;
            private boolean moved;

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                requestFocusInWindow();
                drag = e.getPoint();
                moved = false;
            }

            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (drag == null) {
                    return;
                }
                autoFit = false;
                moved = true;
                panX += e.getX() - drag.x;
                panY += e.getY() - drag.y;
                drag = e.getPoint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                repaint();
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                setCursor(Cursor.getDefaultCursor());
                drag = null;
                // КЛИК (а не перетаскивание) по гексу — выделение и биография гекса
                if (!moved && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    String id = hexAt(e.getPoint());
                    session.selectHex(id);
                    onHexClick.accept(id);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(e -> {
            autoFit = false;
            zoomAt(e.getX(), e.getY(), e.getWheelRotation() < 0 ? 1.12 : 1 / 1.12);
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (autoFit) {
                    fitToWindow();
                }
            }
        });
        blinkTimer = new Timer(280, e -> {
            blink = !blink;
            if (!cheapMode && needsBlink()) {
                repaint();
            }
        });
        session.whenRecordChanged(s -> {
            hexIndex = new LinkedHashMap<>();
            if (s.record() != null) {
                for (ReplayRecord.HexInfo h : s.record().hexes) {
                    hexIndex.put(h.id, h);
                }
            }
            // НОВАЯ ЗАПИСЬ — ВСЕГДА ВПИСЫВАЕМ ПОЛЕ ЗАНОВО (просьба дизайнера
            // 16.08.2026). Раньше здесь стояло fitPending = autoFit, то есть
            // ручной зум или сдвиг отменял подгонку навсегда: сменил карту или
            // нажал «всё случайно» — и поле оставалось в прежнем масштабе и
            // прежней точке, а значит прыгало и частью уезжало за край окна.
            // Прежний масштаб принадлежал ПРЕЖНЕМУ полю, у нового и размер, и
            // центр другие — сохранять его нечестно.
            autoFit = true;
            fitPending = true;
            repaint();
        });
        session.whenFrameChanged(s -> repaint());
    }

    @Override
    public void addNotify() {
        super.addNotify();
        blinkTimer.start();
    }

    @Override
    public void removeNotify() {
        blinkTimer.stop();
        super.removeNotify();
    }

    public void setOnHexClick(Consumer<String> listener) {
        this.onHexClick = listener;
    }

    /** Дешёвый режим: на авторепите и большой скорости украшения не рисуем. */
    public void setCheapMode(boolean cheap) {
        this.cheapMode = cheap;
    }

    // ==================== слои ====================
    public boolean layer(Layer l) {
        return layers.contains(l);
    }

    public void setLayer(Layer l, boolean on) {
        if (on) {
            layers.add(l);
        } else {
            layers.remove(l);
        }
        applyPainterFlags();
        repaint();
    }

    public void toggleLayer(Layer l) {
        setLayer(l, !layer(l));
    }

    /** Флаги общего отрисовщика — они статические, так уж он устроен. */
    private void applyPainterFlags() {
        kelium.report.FieldPainter.showDamage = layers.contains(Layer.DAMAGE);
        kelium.report.FieldPainter.showEnergy = layers.contains(Layer.ENERGY);
        kelium.report.FieldPainter.showKelium = layers.contains(Layer.KELIUM);
        kelium.report.FieldPainter.showOwnership = layers.contains(Layer.OWNERSHIP);
    }

    public boolean isFocusMode() {
        return focusMode;
    }

    public void setFocusMode(boolean on) {
        focusMode = on;
        repaint();
    }

    public void setShowTitle(boolean on) {
        showTitle = on;
        repaint();
    }

    public boolean isShowTitle() {
        return showTitle;
    }

    // ==================== масштаб ====================
    public double zoom() {
        return zoom;
    }

    public void zoomBy(double factor) {
        autoFit = false;
        zoomAt(getWidth() / 2, getHeight() / 2, factor);
    }

    private void zoomAt(int sx, int sy, double factor) {
        double next = Math.max(0.35, Math.min(4.0, zoom * factor));
        double k = next / zoom;
        panX = sx - k * (sx - panX);
        panY = sy - k * (sy - panY);
        zoom = next;
        repaint();
    }

    public void fitToWindow() {
        autoFit = true;
        ReplayRecord rec = session.record();
        if (rec == null || rec.hexes.isEmpty() || getWidth() < 40 || getHeight() < 40) {
            fitPending = true;
            return;
        }
        double minx = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxy = -Double.MAX_VALUE;
        for (ReplayRecord.HexInfo h : rec.hexes) {
            double[] c = FieldGeometry.hexCenter(h.q, h.r, BASE);
            minx = Math.min(minx, c[0] - BASE);
            maxx = Math.max(maxx, c[0] + BASE);
            miny = Math.min(miny, c[1] - BASE);
            maxy = Math.max(maxy, c[1] + BASE);
        }
        double margin = Theme.px(20);
        zoom = Math.max(0.35, Math.min(4.0,
            Math.min((getWidth() - 2 * margin) / (maxx - minx),
                     (getHeight() - 2 * margin) / (maxy - miny))));
        panX = getWidth() / 2.0 - zoom * (minx + maxx) / 2;
        panY = getHeight() / 2.0 - zoom * (miny + maxy) / 2;
        fitPending = false;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(Theme.FIELD_MIN_W + 200),
            Theme.px(Theme.FIELD_MIN_H + 120));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(Theme.px(Theme.FIELD_MIN_W), Theme.px(Theme.FIELD_MIN_H));
    }

    // ==================== отрисовка ====================
    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        g.setColor(Theme.bg());
        g.fillRect(0, 0, getWidth(), getHeight());

        ReplayRecord.Frame f = session.frame();
        if (f == null || f.snapshot == null) {
            paintEmpty(g);
            g.dispose();
            return;
        }
        if (fitPending) {
            fitToWindow();
        }
        // ПОДЛОЖКА-«БУМАГА» ровно под полем, а не на весь экран: авторские цвета
        // жетонов рисовались для белой бумаги, и показывать их надо в том же
        // окружении — но тёмная рама вокруг должна остаться, иначе поле сливается
        // с интерфейсом и перестаёт быть главным объектом.
        paintPaper(g);

        // ПОЛЕ ЗНАЕТ ПРО ТЕМУ: на тёмной теме гексы темнеют, а жетоны и тайлы
        // наоборот светлеют — см. FieldPainter.dark (просьба дизайнера 13.08.2026).
        kelium.report.FieldPainter.dark = Theme.isDark();
        Graphics2D gf = (Graphics2D) g.create();
        gf.translate(panX, panY);
        gf.scale(zoom, zoom);
        kelium.report.FieldPainter.paintField(
            new kelium.report.Java2DCanvas(gf, zoom, getFont()),
            BASE, session.record().hexes, f.snapshot, 0, 0, layers.contains(Layer.IDS));
        if (layers.contains(Layer.HEATMAP)) {
            paintHeatmap(gf);
        }
        if (layers.contains(Layer.TRAILS)) {
            paintTrails(gf, f);
        }
        if (layers.contains(Layer.CIRCLES)) {
            paintCircles(gf, f);
        }
        gf.dispose();

        // ФОКУС КАДРА: пригашаем всё, кроме участников события
        if (focusMode && !cheapMode) {
            paintDimming(g, f);
        }
        Graphics2D gm = (Graphics2D) g.create();
        gm.translate(panX, panY);
        gm.scale(zoom, zoom);
        if (!cheapMode) {
            paintMarkup(gm, f);
        }
        paintSelection(gm);
        gm.dispose();

        if (showTitle && !cheapMode) {
            paintTitleCard(g, f);
        }
        g.dispose();
    }

    /** «Бумага» под полем: скруглённая плашка по границам гексов, с мягкой тенью. */
    private void paintPaper(Graphics2D g) {
        ReplayRecord rec = session.record();
        if (rec == null || rec.hexes.isEmpty()) {
            return;
        }
        double minx = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxy = -Double.MAX_VALUE;
        for (ReplayRecord.HexInfo h : rec.hexes) {
            double[] c = FieldGeometry.hexCenter(h.q, h.r, BASE);
            minx = Math.min(minx, c[0] - BASE);
            maxx = Math.max(maxx, c[0] + BASE);
            miny = Math.min(miny, c[1] - BASE);
            maxy = Math.max(maxy, c[1] + BASE);
        }
        double m = Theme.px(14);
        double x1 = panX + zoom * minx - m;
        double y1 = panY + zoom * miny - m;
        double w = zoom * (maxx - minx) + 2 * m;
        double h = zoom * (maxy - miny) + 2 * m;
        double arc = Theme.px(14);
        g.setColor(Theme.alpha(java.awt.Color.BLACK, Theme.isDark() ? 0.35 : 0.12));
        g.fill(new RoundRectangle2D.Double(x1 + Theme.px(2), y1 + Theme.px(4), w, h,
            arc, arc));
        g.setColor(Theme.paper());
        g.fill(new RoundRectangle2D.Double(x1, y1, w, h, arc, arc));
    }

    private void paintEmpty(Graphics2D g) {
        // ПУСТОЕ СОСТОЯНИЕ: не серый прямоугольник, а бледный контур гексов и одна
        // понятная фраза — видно, что сюда придёт поле.
        g.setColor(Theme.alpha(Theme.neutral(), 0.35));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10f, new float[]{5, 5}, 0));
        double r = Theme.px(46);
        double cx = getWidth() / 2.0;
        double cy = getHeight() / 2.0 - Theme.px(20);
        for (int q = -2; q <= 2; q++) {
            for (int rr = -2; rr <= 2; rr++) {
                if (Math.abs(q + rr) > 2) {
                    continue;
                }
                double[] c = FieldGeometry.hexCenter(q, rr, r);
                g.draw(hexPath(cx + c[0], cy + c[1], r * 0.94));
            }
        }
        g.setFont(Theme.subtitle());
        g.setColor(Theme.darken(Theme.neutral(), 0.2));
        String s = "Сыграй партию или открой запись";
        g.drawString(s, (float) (cx - g.getFontMetrics().stringWidth(s) / 2.0),
            (float) (getHeight() - Theme.px(40)));
    }

    /**
     * Пригашение непричастного. Реализовано «дыркой в шторе»: полупрозрачная
     * заслонка кладётся на всё поле, а из неё вычитаются гексы участников события.
     * Так участники остаются в полную силу без повторной отрисовки поля.
     */
    private void paintDimming(Graphics2D g, ReplayRecord.Frame f) {
        Set<String> keep = participants(f);
        if (keep.isEmpty()) {
            return;
        }
        Area shade = new Area(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
        AffineTransform at = new AffineTransform();
        at.translate(panX, panY);
        at.scale(zoom, zoom);
        for (String id : keep) {
            ReplayRecord.HexInfo hi = hexIndex.get(id);
            if (hi == null) {
                continue;
            }
            double[] c = FieldGeometry.hexCenter(hi.q, hi.r, BASE);
            shade.subtract(new Area(at.createTransformedShape(
                hexPath(c[0], c[1], BASE * 1.02))));
        }
        g.setColor(Theme.alpha(Theme.paper(), 0.55));
        g.fill(shade);
    }

    /** Гексы, причастные к событию этого шага. */
    private Set<String> participants(ReplayRecord.Frame f) {
        Set<String> out = new HashSet<>();
        for (String[] mv : f.highlight.moves) {
            out.add(mv[0]);
            out.add(mv[1]);
        }
        for (String[] at : f.highlight.attacks) {
            out.add(at[0]);
            out.add(at[1]);
        }
        out.addAll(f.highlight.builds);
        out.addAll(f.highlight.damaged);
        out.addAll(f.highlight.destroyed);
        out.remove(null);
        return out;
    }

    /** Разметка действия: дуга, зубец, кольцо, крест. */
    private void paintMarkup(Graphics2D g, ReplayRecord.Frame f) {
        Integer seat = f.snapshot.active;
        Color accent = seat != null ? Theme.seat(seat) : Theme.neutral();

        for (String[] mv : f.highlight.moves) {
            double[] a = centre(mv[0]);
            double[] b = centre(mv[1]);
            if (a != null && b != null) {
                arc(g, a, b, accent);
            }
        }
        for (String[] at : f.highlight.attacks) {
            double[] a = centre(at[0]);
            double[] b = centre(at[1]);
            if (a != null && b != null) {
                hit(g, a, b);
            }
        }
        for (String id : f.highlight.builds) {
            double[] c = centre(id);
            if (c == null) {
                continue;
            }
            g.setColor(Theme.alpha(accent, 0.85));
            g.setStroke(pen(2.6));
            g.draw(hexPath(c[0], c[1], BASE * 0.88));
            g.setStroke(pen(1.4));
            g.draw(hexPath(c[0], c[1], BASE * 0.99));
        }
        for (String id : f.highlight.damaged) {
            double[] c = centre(id);
            if (c == null) {
                continue;
            }
            g.setColor(Theme.alpha(Theme.damage(), blink ? 0.85 : 0.35));
            g.setStroke(pen(3.0));
            g.draw(hexPath(c[0], c[1], BASE * 0.93));
        }
        for (String id : f.highlight.destroyed) {
            double[] c = centre(id);
            if (c == null) {
                continue;
            }
            g.setColor(Theme.alpha(new Color(0x55, 0x55, 0x55), 0.75));
            g.setStroke(pen(2.4));
            g.draw(hexPath(c[0], c[1], BASE * 0.86));
            // КРЕСТ НЕСИММЕТРИЧНЫЙ, как из комикса: два разных мазка накрест
            Shape s1 = slash(c[0], c[1], BASE * 0.46, 41, BASE * 0.13);
            Shape s2 = slash(c[0] + BASE * 0.03, c[1] - BASE * 0.02,
                BASE * 0.38, -52, BASE * 0.10);
            g.setColor(Theme.alpha(Color.WHITE, 0.7));
            g.setStroke(pen(2.6));
            g.draw(s1);
            g.draw(s2);
            g.setColor(Theme.alpha(Theme.damage(), blink ? 0.95 : 0.55));
            g.fill(s1);
            g.fill(s2);
        }
    }

    /** Выделенный кликом гекс — тонкая яркая обводка, без пригашения остального. */
    private void paintSelection(Graphics2D g) {
        String id = session.selectedHex();
        if (id == null) {
            return;
        }
        double[] c = centre(id);
        if (c == null) {
            return;
        }
        g.setColor(Theme.accent());
        g.setStroke(pen(2.6));
        g.draw(hexPath(c[0], c[1], BASE * 1.0));
    }

    /**
     * Дуга перемещения. Именно дуга, а не прямая: жетон УЖЕ стоит на новом месте, и
     * линия должна читаться как пояснение «откуда пришёл», а не как сам ход.
     */
    private void arc(Graphics2D g, double[] a, double[] b, Color colour) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double len = Math.hypot(dx, dy);
        if (len < 1) {
            return;
        }
        double ux = dx / len;
        double uy = dy / len;
        double sx = a[0] + ux * BASE * 0.34;
        double sy = a[1] + uy * BASE * 0.34;
        double ex = b[0] - ux * BASE * 0.38;
        double ey = b[1] - uy * BASE * 0.38;
        // приподнятая середина — та самая «дуга»
        double mx = (sx + ex) / 2 - uy * len * 0.18;
        double my = (sy + ey) / 2 + ux * len * 0.18;
        QuadCurve2D curve = new QuadCurve2D.Double(sx, sy, mx, my, ex, ey);
        g.setColor(Theme.alpha(Color.WHITE, 0.75));
        g.setStroke(pen(4.6));
        g.draw(curve);
        g.setColor(colour);
        g.setStroke(pen(2.6));
        g.draw(curve);
        // наконечник по касательной в конце
        double tx = ex - mx;
        double ty = ey - my;
        double tl = Math.max(0.001, Math.hypot(tx, ty));
        tx /= tl;
        ty /= tl;
        double head = BASE * 0.20;
        Path2D tip = new Path2D.Double();
        tip.moveTo(ex, ey);
        tip.lineTo(ex - head * (tx * 0.87 - ty * 0.5), ey - head * (ty * 0.87 + tx * 0.5));
        tip.lineTo(ex - head * (tx * 0.87 + ty * 0.5), ey - head * (ty * 0.87 - tx * 0.5));
        tip.closePath();
        g.fill(tip);
    }

    /**
     * УДАР. Молния переменной толщины: тонкая на концах, толстая в середине, с
     * изломом — и вспышка взрыва на том, по кому били. Прежняя ровная линия
     * толщиной в два пикселя выглядела как чертёжная стрелка, а не как удар
     * (просьба дизайнера 13.08.2026).
     */
    private void hit(Graphics2D g, double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double len = Math.max(0.001, Math.hypot(dx, dy));
        double ux = dx / len;
        double uy = dy / len;
        double sx = a[0] + ux * BASE * 0.3;
        double sy = a[1] + uy * BASE * 0.3;
        double ex = b[0] - ux * BASE * 0.3;
        double ey = b[1] - uy * BASE * 0.3;

        Shape bolt = bolt(sx, sy, ex, ey, BASE * (blink ? 0.15 : 0.12));
        // белая подложка — молния читается на любом цвете гекса
        g.setColor(Theme.alpha(Color.WHITE, 0.75));
        g.setStroke(pen(3.0));
        g.draw(bolt);
        g.setColor(Theme.alpha(Theme.damage(), blink ? 1.0 : 0.7));
        g.fill(bolt);

        burst(g, ex, ey, BASE * (blink ? 0.34 : 0.27));
    }

    /**
     * МОЛНИЯ как ЗАЛИТАЯ фигура: середина широкая, концы сходятся в остриё, по
     * дороге излом. Обводкой такое не нарисовать — у линии одна толщина на всю
     * длину, поэтому фигура строится по точкам: сначала одна сторона от начала к
     * концу, потом вторая обратно.
     */
    private Shape bolt(double sx, double sy, double ex, double ey, double maxW) {
        double dx = ex - sx;
        double dy = ey - sy;
        double len = Math.max(0.001, Math.hypot(dx, dy));
        double ux = dx / len;
        double uy = dy / len;
        double nx = -uy;
        double ny = ux;
        double zig = len * 0.10;
        int steps = 18;
        Path2D p = new Path2D.Double();
        double[] xs = new double[steps + 1];
        double[] ys = new double[steps + 1];
        double[] ws = new double[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            // излом: уходит в одну сторону к трети пути и в другую к двум третям
            double off = zig * (t < 0.5 ? t * 2 : (1 - t) * -2);
            xs[i] = sx + ux * len * t + nx * off;
            ys[i] = sy + uy * len * t + ny * off;
            // толщина: ноль на концах, наибольшая посередине
            ws[i] = maxW * Math.pow(Math.sin(Math.PI * t), 0.55);
        }
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i <= steps; i++) {
            p.lineTo(xs[i] + nx * ws[i] / 2, ys[i] + ny * ws[i] / 2);
        }
        for (int i = steps - 1; i >= 0; i--) {
            p.lineTo(xs[i] - nx * ws[i] / 2, ys[i] - ny * ws[i] / 2);
        }
        p.closePath();
        return p;
    }

    /** ВСПЫШКА ВЗРЫВА: рваная звезда с ярким ядром — там, куда пришёлся удар. */
    private void burst(Graphics2D g, double cx, double cy, double r) {
        int rays = 11;
        Path2D star = new Path2D.Double();
        for (int i = 0; i < rays * 2; i++) {
            // лучи РАЗНОЙ длины, но всегда одни и те же: звезда не должна дрожать
            double k = i % 2 == 0 ? 1.0 - 0.22 * ((i / 2) % 3) : 0.42;
            double ang = Math.PI * i / rays - Math.PI / 2;
            double x = cx + r * k * Math.cos(ang);
            double y = cy + r * k * Math.sin(ang);
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        g.setColor(Theme.alpha(new Color(0xFF, 0x8A, 0x1E), blink ? 0.90 : 0.45));
        g.fill(star);
        g.setColor(Theme.alpha(new Color(0xFF, 0xE0, 0x70), blink ? 0.95 : 0.5));
        double core = r * 0.42;
        g.fill(new Ellipse2D.Double(cx - core, cy - core, 2 * core, 2 * core));
    }

    /**
     * КОМИКСНЫЙ ШТРИХ: полоса с острыми концами и утолщением в середине. Из двух
     * таких, поставленных под РАЗНЫМИ углами и разной длины, получается «живой»
     * крест — не аккуратные две линии под 90°.
     */
    private Shape slash(double cx, double cy, double half, double angDeg, double maxW) {
        double a = Math.toRadians(angDeg);
        return bolt(cx - Math.cos(a) * half, cy - Math.sin(a) * half,
            cx + Math.cos(a) * half, cy + Math.sin(a) * half, maxW);
    }

    /** Тепловая карта боёв за всю партию: где чаще всего били. */
    private void paintHeatmap(Graphics2D g) {
        Map<String, Integer> hits = new LinkedHashMap<>();
        int max = 1;
        for (ReplayRecord.Frame f : session.record().frames) {
            for (String[] at : f.highlight.attacks) {
                int v = hits.merge(at[1], 1, Integer::sum);
                max = Math.max(max, v);
            }
            for (String id : f.highlight.destroyed) {
                int v = hits.merge(id, 2, Integer::sum);
                max = Math.max(max, v);
            }
        }
        for (Map.Entry<String, Integer> e : hits.entrySet()) {
            double[] c = centre(e.getKey());
            if (c == null) {
                continue;
            }
            double k = e.getValue() / (double) max;
            g.setColor(new Color(0xD3, 0x2F, 0x2F, (int) (40 + 150 * k)));
            g.fill(hexPath(c[0], c[1], BASE * 0.97));
        }
    }

    /** Шлейфы движения за текущий раунд — куда войска ходили в этом раунде. */
    private void paintTrails(Graphics2D g, ReplayRecord.Frame cur) {
        int round = cur.round;
        List<ReplayRecord.Frame> frames = session.record().frames;
        int upTo = session.cursor();
        for (int i = 0; i <= upTo && i < frames.size(); i++) {
            ReplayRecord.Frame f = frames.get(i);
            if (f.round != round) {
                continue;
            }
            for (String[] mv : f.highlight.moves) {
                double[] a = centre(mv[0]);
                double[] b = centre(mv[1]);
                if (a == null || b == null) {
                    continue;
                }
                Color c = f.seat == null ? Theme.neutral() : Theme.seat(f.seat);
                g.setColor(Theme.alpha(c, 0.35));
                g.setStroke(pen(1.8));
                g.draw(new Line2D.Double(a[0], a[1], b[0], b[1]));
            }
        }
    }

    /** Номер круга у построек: «когда это встало». */
    private void paintCircles(Graphics2D g, ReplayRecord.Frame cur) {
        Map<String, Integer> when = new LinkedHashMap<>();
        List<ReplayRecord.Frame> frames = session.record().frames;
        for (int i = 0; i <= session.cursor() && i < frames.size(); i++) {
            ReplayRecord.Frame f = frames.get(i);
            for (String id : f.highlight.builds) {
                when.putIfAbsent(id, f.circle > 0 ? f.circle : 1);
            }
        }
        g.setFont(getFont().deriveFont(Font.BOLD, (float) (BASE * 0.30)));
        for (Map.Entry<String, Integer> e : when.entrySet()) {
            double[] c = centre(e.getKey());
            if (c == null) {
                continue;
            }
            String s = String.valueOf(e.getValue());
            double w = g.getFontMetrics().stringWidth(s);
            g.setColor(Theme.alpha(Color.WHITE, 0.85));
            g.fill(new Ellipse2D.Double(c[0] - BASE * 0.22, c[1] + BASE * 0.42,
                BASE * 0.44, BASE * 0.44));
            g.setColor(new Color(0x33, 0x33, 0x33));
            g.drawString(s, (float) (c[0] - w / 2), (float) (c[1] + BASE * 0.76));
        }
    }

    /**
     * ТИТР события — карточка в углу поля. Заменяет прежнюю плашку на треть поля:
     * говорит то же самое, но не отбирает место у главного вида.
     */
    private void paintTitleCard(Graphics2D g, ReplayRecord.Frame f) {
        String head = Names.eventType(f.type);
        String body = Names.printable(f.log);
        if (body.isBlank() && f.thoughts.isEmpty()) {
            return;
        }
        // ТИТР — ШИРОКИМ НАКЛОННЫМ: это реплика о происходящем, и она должна
        // звучать иначе, чем подписи интерфейса (просьба дизайнера 13.08.2026).
        // Панель шире прежней и держит четыре строки: на трёх текст обрезался
        // на полуслове.
        int pad = Theme.px(12);
        int w = Theme.px(340);
        g.setFont(Theme.wide(12, true));
        List<String> lines = wrap(g, body, w - 2 * pad - Theme.px(8), 4);
        ReplayRecord.Thought t = f.thoughts.isEmpty() ? null
            : f.thoughts.get(f.thoughts.size() - 1);
        int lineH = g.getFontMetrics().getHeight();
        int h = pad * 2 + Theme.px(14) + lines.size() * lineH + (t == null ? 0 : lineH);
        int x = getWidth() - w - Theme.px(14);
        int y = getHeight() - h - Theme.px(14);

        g.setColor(Theme.alpha(Color.BLACK, 0.14));
        g.fill(new RoundRectangle2D.Double(x + 2, y + 3, w, h, Theme.R_OVERLAY * 2,
            Theme.R_OVERLAY * 2));
        // ТИТР КРАСИТСЯ ТЕМОЙ. Он был белой всплывашкой при любой теме и на тёмной
        // светил в углу поля (замечание дизайнера 13.08.2026).
        Color card = Theme.panel();
        g.setColor(new Color(card.getRed(), card.getGreen(), card.getBlue(), 242));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, Theme.R_OVERLAY * 2,
            Theme.R_OVERLAY * 2));
        g.setColor(Theme.border());
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, Theme.R_OVERLAY * 2,
            Theme.R_OVERLAY * 2));
        Color accent = f.seat != null ? Theme.seat(f.seat)
            : (f.snapshot.active != null ? Theme.seat(f.snapshot.active) : Theme.neutral());
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Double(x, y, Theme.px(4), h, Theme.px(4), Theme.px(4)));

        int ty = y + pad + Theme.px(10);
        g.setFont(Theme.caption());
        g.setColor(Theme.seatInk(f.seat != null ? f.seat
            : (f.snapshot.active != null ? f.snapshot.active : 0)));
        g.drawString(head.toUpperCase(java.util.Locale.ROOT), x + pad + Theme.px(6), ty);
        ty += Theme.px(6);
        // тем же широким наклонным, каким текст и размечался в строки
        g.setFont(Theme.wide(12, true));
        g.setColor(Theme.ink());
        for (String s : lines) {
            ty += lineH;
            g.drawString(s, x + pad + Theme.px(6), ty);
        }
        if (t != null) {
            ty += lineH;
            g.setFont(Theme.italic());
            g.setColor(Theme.seatInk(t.seat));
            g.drawString(clip(g, "«" + t.text + "»", w - 2 * pad),
                x + pad + Theme.px(6), ty);
        }
    }

    // ==================== подсказка ====================
    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        String id = hexAt(e.getPoint());
        if (id == null) {
            return null;
        }
        ReplayRecord.Frame f = session.frame();
        if (f == null || f.snapshot == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("<html><b>Гекс ").append(id).append("</b>");
        for (ReplayRecord.HexState st : f.snapshot.hexes) {
            if (!st.id.equals(id)) {
                continue;
            }
            if (st.spawn != null) {
                sb.append("<br>тайл зарождения").append(st.spawn.start ? " (стартовый)" : "")
                  .append(": келемия ").append(st.spawn.kelium)
                  .append(st.spawn.stack > 1 ? ", двойной" : "");
            }
            if (st.containerCell >= 0) {
                sb.append("<br>печатный контейнер: ").append(st.containerCell == 6
                    ? "воздушная ячейка" : "ячейка " + st.containerCell);
            }
            if (st.energyCell >= 0) {
                sb.append("<br>жёлтая ячейка: ").append(st.energyCell)
                  .append(" (только на ней энергостанция даёт номинал)");
            }
            for (ReplayRecord.Neutral nt : st.neutrals) {
                sb.append("<br>нейтральная постройка (")
                  .append(nt.big ? "большая" : "малая").append(')');
            }
        }
        for (ReplayRecord.Tok t : f.snapshot.tokens) {
            if (!id.equals(t.hexId) || !t.alive) {
                continue;
            }
            sb.append("<br>").append(session.record().playerName(t.owner)).append(": ")
              .append(t.building ? Names.building(t.type) : Names.unit(t.type));
            if (t.level != null) {
                sb.append(" №").append(t.level);
            }
            sb.append(" — прочность ").append(Math.max(0, t.hp - t.damage))
              .append('/').append(t.hp);
            if (t.damage > 0) {
                // РАНЕН: на поле он обведён красным, а на сколько — говорим здесь
                sb.append(" <b>(ранен на ").append(t.damage).append(")</b>");
            }
            if (t.building && t.energySlots > 0) {
                sb.append(", энергия ").append(t.energyPlaced).append('/').append(t.energySlots);
            }
        }
        sb.append("<br><i>клик — что здесь происходило за партию</i>");
        return sb.append("</html>").toString();
    }

    /** Гекс под точкой экрана (или null). */
    private String hexAt(Point p) {
        if (session.record() == null) {
            return null;
        }
        try {
            AffineTransform at = new AffineTransform();
            at.translate(panX, panY);
            at.scale(zoom, zoom);
            Point2D w = at.createInverse().transform(p, null);
            int[] qr = FieldGeometry.hexAt(w.getX(), w.getY(), BASE);
            for (ReplayRecord.HexInfo hi : session.record().hexes) {
                if (hi.q == qr[0] && hi.r == qr[1]) {
                    return hi.id;
                }
            }
        } catch (java.awt.geom.NoninvertibleTransformException ex) {
            return null;
        }
        return null;
    }

    // ==================== мелочи ====================
    private double[] centre(String hexId) {
        ReplayRecord.HexInfo hi = hexIndex.get(hexId);
        return hi == null ? null : FieldGeometry.hexCenter(hi.q, hi.r, BASE);
    }

    private boolean needsBlink() {
        ReplayRecord.Frame f = session.frame();
        return f != null && (!f.highlight.damaged.isEmpty()
            || !f.highlight.destroyed.isEmpty() || !f.highlight.attacks.isEmpty());
    }

    /** Обводка в ЭКРАННЫХ пикселях: контекст уже отмасштабирован на zoom. */
    private BasicStroke pen(double screenPx) {
        return new BasicStroke((float) (screenPx / Math.max(0.01, zoom)),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private static Path2D hexPath(double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        double[][] pts = FieldGeometry.hexCorners(cx, cy, r);
        p.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < 6; i++) {
            p.lineTo(pts[i][0], pts[i][1]);
        }
        p.closePath();
        return p;
    }

    private static List<String> wrap(Graphics2D g, String text, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(probe) > width && line.length() > 0) {
                out.add(line.toString());
                if (out.size() == maxLines) {
                    return out;
                }
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0 && out.size() < maxLines) {
            out.add(line.toString());
        }
        return out;
    }

    private static String clip(Graphics2D g, String text, int width) {
        if (g.getFontMetrics().stringWidth(text) <= width) {
            return text;
        }
        int n = text.length();
        while (n > 1 && g.getFontMetrics().stringWidth(text.substring(0, n) + "…") > width) {
            n--;
        }
        return text.substring(0, n) + "…";
    }
}
