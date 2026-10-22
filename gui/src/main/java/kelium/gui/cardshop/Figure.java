package kelium.gui.cardshop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * ФИГУРА ИЗ СЕКТОРОВ (дизайнер 03.10.2026): на общем рисунке гексов закрашены
 * секторы, которые надо занять своими жетонами — любыми, без рисунка зданий и
 * войск. Фигуру можно поворачивать, отражать нельзя.
 *
 * <p>В файле карты: {@code фигура: [{q: 0, r: 0, секторы: [0, 1]}, …]} —
 * осевые координаты гекса на сетке редактора (плоский верх, как поле игры) и
 * номера закрашенных секторов 0..5. Номер сектора — номер стороны гекса, как
 * {@code Hex.neighborBySide}: середина стороны i смотрит под углом 30° − 60°·i
 * (ось y вниз) — так же поле рисует игра, поэтому нарисованная фигура не
 * зеркальна той, что проверяет движок.
 *
 * <p>Для игры фигура переводится в узел языка карт «узор» ({@link #node()}):
 * пути от опорного гекса по сторонам и секторы.
 */
public final class Figure {

    /** Осевые направления сторон 0..5 — как {@code kelium.core.Field.AXIAL_DIRS}. */
    static final int[][] DIRS = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

    /** Гекс фигуры: координаты и закрашенные секторы. */
    public record Cell(int q, int r, TreeSet<Integer> sectors) {
    }

    public final List<Cell> cells = new ArrayList<>();

    public boolean isEmpty() {
        for (Cell c : cells) {
            if (!c.sectors.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Закрашенных секторов всего. */
    public int count() {
        int n = 0;
        for (Cell c : cells) {
            n += c.sectors.size();
        }
        return n;
    }

    Cell cell(int q, int r) {
        for (Cell c : cells) {
            if (c.q == q && c.r == r) {
                return c;
            }
        }
        return null;
    }

    /** Переключить сектор гекса (q, r). */
    public void toggle(int q, int r, int sector) {
        Cell c = cell(q, r);
        if (c == null) {
            c = new Cell(q, r, new TreeSet<>());
            cells.add(c);
        }
        if (!c.sectors.remove(sector)) {
            c.sectors.add(sector);
        }
        if (c.sectors.isEmpty()) {
            cells.remove(c);
        }
    }

    public boolean filled(int q, int r, int sector) {
        Cell c = cell(q, r);
        return c != null && c.sectors.contains(sector);
    }

    /** Повернуть на 60° (против часовой на рисунке): гексы вокруг (0,0), секторы +1. */
    public Figure turned() {
        Figure f = new Figure();
        for (Cell c : cells) {
            // поворот на шаг сторон: DIRS[i] → DIRS[i+1], т. е. (q, r) → (q + r, −q)
            int nq = c.q + c.r;
            int nr = -c.q;
            TreeSet<Integer> s = new TreeSet<>();
            for (int sec : c.sectors) {
                s.add(Math.floorMod(sec + 1, 6));
            }
            f.cells.add(new Cell(nq, nr, s));
        }
        return f;
    }

    // ==================== файл ====================

    public static Figure of(Object raw) {
        Figure f = new Figure();
        if (raw instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    int q = m.get("q") instanceof Number n ? n.intValue() : 0;
                    int r = m.get("r") instanceof Number n ? n.intValue() : 0;
                    TreeSet<Integer> s = new TreeSet<>();
                    if (m.get("секторы") instanceof List<?> sl) {
                        for (Object x : sl) {
                            if (x instanceof Number n) {
                                s.add(Math.floorMod(n.intValue(), 6));
                            }
                        }
                    }
                    if (!s.isEmpty()) {
                        f.cells.add(new Cell(q, r, s));
                    }
                }
            }
        }
        return f;
    }

    public List<Object> toList() {
        List<Object> out = new ArrayList<>();
        for (Cell c : cells) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("q", c.q);
            m.put("r", c.r);
            m.put("секторы", new ArrayList<>(c.sectors));
            out.add(m);
        }
        return out;
    }

    /**
     * Узел языка карт «узор» — пути от первого гекса по сторонам (каждый шаг —
     * номер стороны) и секторы. Это и проверяет движок
     * ({@code Требование.Узор}).
     */
    public Map<String, Object> node(String name) {
        List<Object> клетки = new ArrayList<>();
        if (!cells.isEmpty()) {
            Cell base = cells.get(0);
            for (Cell c : cells) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("путь", path(c.q - base.q, c.r - base.r));
                m.put("секторы", new ArrayList<>(c.sectors));
                клетки.add(m);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("узел", "узор");
        m.put("клетки", клетки);
        m.put("имя", name == null || name.isBlank() ? "узор" : name);
        return m;
    }

    /** Путь по сторонам от (0,0) к (dq, dr): жадно, каждый шаг уменьшает расстояние. */
    static List<Integer> path(int dq, int dr) {
        List<Integer> out = new ArrayList<>();
        int q = 0;
        int r = 0;
        while (q != dq || r != dr) {
            int best = 0;
            int bestD = Integer.MAX_VALUE;
            for (int i = 0; i < 6; i++) {
                int d = dist(q + DIRS[i][0], r + DIRS[i][1], dq, dr);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            q += DIRS[best][0];
            r += DIRS[best][1];
            out.add(best);
        }
        return out;
    }

    static int dist(int q1, int r1, int q2, int r2) {
        int dq = q1 - q2;
        int dr = r1 - r2;
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }

    // ==================== рисунок ====================

    /** Центр гекса (q, r) при стороне size — плоский верх, как поле игры. */
    static double[] center(int q, int r, double size) {
        return new double[] {size * 1.5 * q, size * Math.sqrt(3) * (r + q / 2.0)};
    }

    /** Треугольник сектора i: центр и две вершины стороны i. */
    static Path2D sector(double cx, double cy, double size, int i) {
        double a1 = Math.toRadians(-60.0 * i);
        double a2 = Math.toRadians(60.0 - 60.0 * i);
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy);
        p.lineTo(cx + size * Math.cos(a1), cy + size * Math.sin(a1));
        p.lineTo(cx + size * Math.cos(a2), cy + size * Math.sin(a2));
        p.closePath();
        return p;
    }

    static Path2D hex(double cx, double cy, double size) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60.0 * i);
            double x = cx + size * Math.cos(a);
            double y = cy + size * Math.sin(a);
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.closePath();
        return p;
    }

    /**
     * Нарисовать фигуру по центру прямоугольника (в пикселях холста): гексы
     * светлые с тёмной обводкой, закрашенные секторы — цветом {@code fill},
     * тонкие лучи делят гекс на шесть секторов.
     */
    public void draw(Graphics2D g, double cx, double cy, double w, double h, Color fill, Color ink) {
        if (cells.isEmpty()) {
            return;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Cell c : cells) {
            double[] p = center(c.q, c.r, 1);
            minX = Math.min(minX, p[0] - 1);
            maxX = Math.max(maxX, p[0] + 1);
            minY = Math.min(minY, p[1] - Math.sqrt(3) / 2);
            maxY = Math.max(maxY, p[1] + Math.sqrt(3) / 2);
        }
        double size = Math.min(w / (maxX - minX), h / (maxY - minY));
        size = Math.min(size, Math.min(w, h) / 2.2);
        double ox = cx - (minX + maxX) / 2 * size;
        double oy = cy - (minY + maxY) / 2 * size;
        float line = (float) Math.max(2, size * 0.07);
        for (Cell c : cells) {
            double[] p = center(c.q, c.r, size);
            double x = ox + p[0];
            double y = oy + p[1];
            g.setColor(new Color(255, 255, 255, 215));
            g.fill(hex(x, y, size));
            g.setColor(fill);
            for (int s : c.sectors) {
                g.fill(sector(x, y, size, s));
            }
            g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 110));
            g.setStroke(new BasicStroke(line * 0.45f));
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(60.0 * i);
                g.draw(new java.awt.geom.Line2D.Double(x, y, x + size * Math.cos(a),
                    y + size * Math.sin(a)));
            }
            g.setColor(ink);
            g.setStroke(new BasicStroke(line, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(hex(x, y, size));
        }
    }
}
