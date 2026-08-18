package kelium.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Map;

import javax.swing.JPanel;

import kelium.gui.LayoutEditor.LHex;
import kelium.gui.LayoutEditor.Model;
import kelium.gui.replay2.Theme;
import kelium.report.FieldGeometry;

/**
 * ПОЛОТНО KeliumBuilder — рендер поля конструктора на движке {@link
 * FieldGeometry}/{@link Theme}, том же, что рисует разбор партии (заказ
 * дизайнера 18.08.2026: «переезжаем на новый движок рендера, как в replay2»).
 *
 * <p>ПЕРВЫЙ СРЕЗ МИГРАЦИИ, не полная замена {@link LayoutEditor}. Читает
 * {@link Model}/{@link LHex} как есть (они уже без единой зависимости от
 * Swing — см. заметку в {@code LayoutEditor.Model}), но пока не умеет
 * редактировать: только показывает раскладку. Инструменты, ghost-сетка и
 * PNG-экспорт — следующие срезы; {@link LayoutEditor} остаётся рабочим
 * инструментом, пока сюда не переедет всё.
 *
 * <p>Гекс «плашмя вверх» (см. {@link FieldGeometry#TILT}) — тот же поворот,
 * что у поля в разборе партии, а не «остриём вверх», как рисовало старое
 * полотно {@code LayoutEditor.Canvas}.
 */
final class BuilderScene extends JPanel {

    private final Model model;
    private double size = FieldGeometry.DEFAULT_SIZE;
    private double panX = 0;
    private double panY = 0;

    BuilderScene(Model model) {
        this.model = model;
        setBackground(Theme.paper());
    }

    void setPan(double x, double y) {
        panX = x;
        panY = y;
    }

    void setSize(double s) {
        size = s;
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(getWidth() / 2.0 + panX, getHeight() / 2.0 + panY);

        for (LHex h : model.hexes.values()) {
            double[] c = FieldGeometry.hexCenter(h.q, h.r, size);
            drawHex(g2, c[0], c[1], h);
        }
        g2.dispose();
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
            g2.drawString(String.valueOf(h.faceKelium()), (float) cx - 4, (float) cy + 4);
        }

        // ВОЗДУШНАЯ ЯЧЕЙКА — поверх всего, как и в разборе партии.
        double airR = size * FieldGeometry.AIR_CELL_R;
        g2.setColor(Color.decode(FieldGeometry.AIR_CELL_STROKE));
        g2.draw(new java.awt.geom.Ellipse2D.Double(cx - airR, cy - airR, airR * 2, airR * 2));

        if (h.containers > 0) {
            g2.setColor(Theme.container());
            double r = size * 0.16;
            g2.fillOval((int) (cx - r), (int) (cy + size * 0.35 - r), (int) (r * 2), (int) (r * 2));
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
