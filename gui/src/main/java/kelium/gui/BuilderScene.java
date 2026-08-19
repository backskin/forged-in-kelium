package kelium.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import kelium.core.Field;
import kelium.gui.LayoutEditor.LHex;
import kelium.gui.LayoutEditor.Model;
import kelium.gui.replay2.Theme;
import kelium.report.FieldGeometry;

/**
 * ПОЛОТНО KeliumBuilder — рендер поля конструктора на движке {@link
 * FieldGeometry}/{@link Theme}, том же, что рисует разбор партии (заказ
 * дизайнера 18.08.2026: «переезжаем на новый движок рендера, как в replay2»).
 *
 * <p>ВТОРОЙ СРЕЗ МИГРАЦИИ (первый был только просмотром): базовое
 * редактирование — сетка призраков вокруг поля, клик по призраку приклеивает
 * гекс, клик по существующему убирает его (или ставит старт/зарождение,
 * смотря какой инструмент выбран). Приказы правки, ghost-сетка добавления и
 * PNG-экспорт LayoutEditor.Canvas переносятся сюда постепенно; {@link
 * LayoutEditor} остаётся рабочим инструментом, пока сюда не переедет всё.
 *
 * <p>Гекс «плашмя вверх» (см. {@link FieldGeometry#TILT}) — тот же поворот,
 * что у поля в разборе партии, а не «остриём вверх», как рисовало старое
 * полотно {@code LayoutEditor.Canvas}.
 */
final class BuilderScene extends JPanel {

    /** Инструменты этого среза — подмножество набора LayoutEditor.Tool. */
    enum Tool { ADD_REMOVE, PLAYER_START, SPAWN_SMALL, SPAWN_BIG, FORBIDDEN, CLEAR,
        STACK, KELIUM_DELTA, CONTAINER }

    private final Model model;
    private double size = FieldGeometry.DEFAULT_SIZE;
    private double panX = 0;
    private double panY = 0;
    private Tool tool = Tool.ADD_REMOVE;
    private int lastMx;
    private int lastMy;
    private Runnable onChange = () -> { };

    /**
     * ОТМЕНА — снимки модели целиком (просто и надёжно на её размере: поле
     * редко больше пары сотен гексов). Снимок кладётся ДО правки, а не после,
     * поэтому «Отменить» всегда возвращает к состоянию непосредственно перед
     * последним кликом.
     */
    private final java.util.Deque<Map<Long, LHex>> undoStack = new java.util.ArrayDeque<>();
    private static final int UNDO_LIMIT = 200;

    private static LHex copyOf(LHex h) {
        LHex c = new LHex(h.q, h.r);
        c.content = h.content;
        c.seat = h.seat;
        c.stack = h.stack;
        c.keliumDelta = h.keliumDelta;
        c.containers = h.containers;
        for (LayoutEditor.Neutral n : h.neutrals) {
            c.neutrals.add(new LayoutEditor.Neutral(n.big, n.corner));
        }
        return c;
    }

    private void pushUndoSnapshot() {
        Map<Long, LHex> snap = new java.util.LinkedHashMap<>();
        for (var e : model.hexes.entrySet()) {
            snap.put(e.getKey(), copyOf(e.getValue()));
        }
        undoStack.push(snap);
        while (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();
        }
    }

    /** Откатить последнюю правку. Возвращает false, если отменять нечего. */
    boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        Map<Long, LHex> snap = undoStack.pop();
        model.hexes.clear();
        model.hexes.putAll(snap);
        onChange.run();
        repaint();
        return true;
    }

    BuilderScene(Model model) {
        this.model = model;
        setBackground(Theme.paper());

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                lastMx = e.getX();
                lastMy = e.getY();
                if (SwingUtilities.isLeftMouseButton(e)) {
                    click(e.getX(), e.getY(), false);
                } else if (SwingUtilities.isRightMouseButton(e)
                        && (tool == Tool.KELIUM_DELTA || tool == Tool.CONTAINER)) {
                    // ПКМ = «в обратную сторону», но ТОЛЬКО у счётных
                    // инструментов: правая кнопка вообще-то панорамирует, и
                    // отдавать ей правку на всех инструментах значило бы
                    // менять поле при каждой попытке подвинуть вид.
                    click(e.getX(), e.getY(), true);
                }
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
                    panX += e.getX() - lastMx;
                    panY += e.getY() - lastMy;
                    lastMx = e.getX();
                    lastMy = e.getY();
                    repaint();
                }
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                size = Math.max(16, Math.min(120, size * (e.getPreciseWheelRotation() < 0 ? 1.12 : 1 / 1.12)));
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    void setTool(Tool t) {
        tool = t;
    }

    void onChange(Runnable r) {
        onChange = r;
    }

    void setPan(double x, double y) {
        panX = x;
        panY = y;
    }

    void setSize(double s) {
        size = s;
    }

    /** Гекс под экранной точкой (учитывая пан и центр полотна) — осевые координаты. */
    private int[] hexUnderScreen(int sx, int sy) {
        double x = sx - getWidth() / 2.0 - panX;
        double y = sy - getHeight() / 2.0 - panY;
        return FieldGeometry.hexAt(x, y, size);
    }

    /** Соседние ПУСТЫЕ гексы вокруг занятых — та же сетка призраков, что у LayoutEditor. */
    private Set<Long> ghostHexes() {
        Set<Long> ghosts = new HashSet<>();
        for (LHex h : model.hexes.values()) {
            for (int[] d : Field.AXIAL_DIRS) {
                int nq = h.q + d[0];
                int nr = h.r + d[1];
                if (model.get(nq, nr) == null) {
                    ghosts.add(Model.key(nq, nr));
                }
            }
        }
        if (model.hexes.isEmpty()) {
            ghosts.add(Model.key(0, 0));
        }
        return ghosts;
    }

    private void click(int sx, int sy, boolean secondary) {
        int[] qr = hexUnderScreen(sx, sy);
        int q = qr[0];
        int r = qr[1];
        LHex existing = model.get(q, r);

        pushUndoSnapshot();
        boolean changed;
        if (tool == Tool.ADD_REMOVE) {
            if (existing == null) {
                changed = ghostHexes().contains(Model.key(q, r));
                if (changed) {
                    model.hexes.put(Model.key(q, r), new LHex(q, r));
                }
            } else {
                // ПОСЛЕДНИЙ ГЕКС НЕ УБИРАЕМ: пустой модели не к чему клеить
                // сетку призраков (та же защита, что в LayoutEditor).
                changed = model.hexes.size() > 1;
                if (changed) {
                    model.hexes.remove(Model.key(q, r));
                }
            }
        } else {
            changed = existing != null && applyTool(existing, secondary);
        }
        if (!changed) {
            undoStack.pop();   // ничего не поменяли — не засоряем историю
            return;
        }
        onChange.run();
        repaint();
    }

    /** Применить инструмент к гексу. Возвращает false, если правка не состоялась. */
    private boolean applyTool(LHex h, boolean secondary) {
        switch (tool) {
            case PLAYER_START -> {
                if ("player_start".equals(h.content)) {
                    h.content = "normal";
                    h.seat = -1;
                    renumberSeats();
                } else {
                    if (model.players() >= LayoutEditor.MAX_SEATS) {
                        return false;
                    }
                    h.content = "player_start";
                    h.seat = model.players();
                    h.stack = 1;
                    h.keliumDelta = 0;
                }
            }
            case SPAWN_SMALL -> h.content = "spawn_start".equals(h.content) ? "normal" : "spawn_start";
            case SPAWN_BIG -> h.content = "kelium_tile".equals(h.content) ? "normal" : "kelium_tile";
            case FORBIDDEN -> {
                if ("forbidden".equals(h.content)) {
                    h.content = "normal";
                } else {
                    h.content = "forbidden";
                    h.seat = -1;
                    h.stack = 1;
                    h.keliumDelta = 0;
                    h.containers = 0;
                    h.neutrals.clear();
                }
            }
            case CLEAR -> {
                h.content = "normal";
                h.seat = -1;
                h.stack = 1;
                h.keliumDelta = 0;
                h.containers = 0;
                h.neutrals.clear();
            }
            // СТОПКА бывает только у тайлов зарождения: удвоенный тайл
            // вырабатывается дважды, а удваивать нечего на обычном гексе.
            case STACK -> {
                if (!h.isSpawn()) {
                    return false;
                }
                h.stack = h.stack >= 2 ? 1 : 2;
            }
            // ПРАВКА КЕЛЕМИЯ — ЛКМ +1, ПКМ −1, предел ±4 (как в LayoutEditor).
            case KELIUM_DELTA -> {
                if (!h.isSpawn()) {
                    return false;
                }
                int nv = h.keliumDelta + (secondary ? -1 : 1);
                if (nv < -4 || nv > 4) {
                    return false;
                }
                h.keliumDelta = nv;
            }
            // КОНТЕЙНЕРЫ — по кругу 0 → 1 → 2 → 0, ПКМ в обратную сторону.
            // На запретном гексе контейнеру не на чем стоять: он вне поля.
            case CONTAINER -> {
                if ("forbidden".equals(h.content)) {
                    return false;
                }
                h.containers = Math.floorMod(h.containers + (secondary ? -1 : 1), 3);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Старты игроков нумеруются подряд от 0 — как в LayoutEditor.Tool.PLAYER. */
    private void renumberSeats() {
        int n = 0;
        for (LHex h : model.hexes.values()) {
            if ("player_start".equals(h.content)) {
                h.seat = n++;
            }
        }
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(getWidth() / 2.0 + panX, getHeight() / 2.0 + panY);

        if (tool == Tool.ADD_REMOVE) {
            for (long key : ghostHexes()) {
                int q = (int) (key >> 32);
                int r = (int) (long) key;
                double[] c = FieldGeometry.hexCenter(q, r, size);
                drawGhost(g2, c[0], c[1]);
            }
        }
        for (LHex h : model.hexes.values()) {
            double[] c = FieldGeometry.hexCenter(h.q, h.r, size);
            drawHex(g2, c[0], c[1], h);
        }
        g2.dispose();
    }

    private void drawGhost(Graphics2D g2, double cx, double cy) {
        double[][] corners = FieldGeometry.hexCorners(cx, cy, size * 0.96);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(corners[0][0], corners[0][1]);
        for (int i = 1; i < corners.length; i++) {
            path.lineTo(corners[i][0], corners[i][1]);
        }
        path.closePath();
        g2.setColor(Theme.ink3());
        g2.setStroke(new java.awt.BasicStroke(1f,
            java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 1f,
            new float[]{4f, 4f}, 0f));
        g2.draw(path);
    }

    private void drawHex(Graphics2D g2, double cx, double cy, LHex h) {
        double[][] corners = FieldGeometry.hexCorners(cx, cy, size * 0.96);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(corners[0][0], corners[0][1]);
        for (int i = 1; i < corners.length; i++) {
            path.lineTo(corners[i][0], corners[i][1]);
        }
        path.closePath();

        g2.setColor(hexFill(h));
        g2.fill(path);
        g2.setColor(Theme.ink());
        g2.setStroke(new java.awt.BasicStroke(1.2f));
        g2.draw(path);

        // Тайл зарождения — закрывает гекс целиком, тот же порядок слоёв, что
        // в FieldGeometry.LAYER_ORDER.
        if (h.isSpawn()) {
            g2.setColor("spawn_start".equals(h.content)
                ? new Color(0xA5D6A7) : new Color(0x2E7D32));
            g2.fill(path);
            g2.setColor(Color.WHITE);
            String face = h.faceKelium() + (h.stack >= 2 ? " ×2" : "");
            g2.drawString(face, (float) (cx - size * 0.28), (float) cy + 4);
        }

        // ВОЗДУШНАЯ ЯЧЕЙКА — поверх всего, как и в разборе партии.
        double airR = size * FieldGeometry.AIR_CELL_R;
        g2.setColor(Color.decode(FieldGeometry.AIR_CELL_STROKE));
        g2.draw(new java.awt.geom.Ellipse2D.Double(cx - airR, cy - airR, airR * 2, airR * 2));

        // Контейнеров бывает два — рисуем оба, иначе «один» и «два» на глаз
        // неотличимы и инструмент нечем проверить.
        for (int i = 0; i < h.containers; i++) {
            g2.setColor(Theme.container());
            double r = size * 0.14;
            double ox = cx + (h.containers == 2 ? (i == 0 ? -r * 1.2 : r * 1.2) : 0);
            g2.fillOval((int) (ox - r), (int) (cy + size * 0.35 - r), (int) (r * 2), (int) (r * 2));
        }

        if ("player_start".equals(h.content) && h.seat >= 0 && h.seat < FieldGeometry.SEAT_FILL.length) {
            g2.setColor(Color.decode(FieldGeometry.SEAT_TOKEN[h.seat]));
            double r = size * 0.3;
            g2.fillOval((int) (cx - r), (int) (cy - r), (int) (r * 2), (int) (r * 2));
        }

        if ("forbidden".equals(h.content)) {
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fill(path);
        }
    }

    private Color hexFill(LHex h) {
        if ("forbidden".equals(h.content)) {
            return Theme.panel();
        }
        return Theme.paper().brighter();
    }

    /** Загрузить сценарий (тот же формат, что открывает LayoutEditor) в свежую модель. */
    @SuppressWarnings("unchecked")
    static Model loadScenario(Map<String, Object> scn) {
        Model m = new Model();
        for (Object o : kelium.engine.Scenario.expandedHexes(scn)) {
            Map<String, Object> e = (Map<String, Object>) o;
            LHex h = new LHex(((Number) e.get("q")).intValue(), ((Number) e.get("r")).intValue());
            h.content = String.valueOf(e.getOrDefault("content", "normal"));
            if (e.get("seat") instanceof Number sn) {
                h.seat = sn.intValue();
            }
            if ("container".equals(h.content)) {
                h.content = "normal";
                h.containers = 1;
            }
            if (e.get("containers") instanceof Number cn) {
                h.containers = Math.max(0, Math.min(2, cn.intValue()));
            }
            m.hexes.put(Model.key(h.q, h.r), h);
        }
        return m;
    }
}
