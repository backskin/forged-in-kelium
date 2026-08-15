package kelium.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kelium.core.Field;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.engine.Scenario;

/**
 * ScenarioSvgRenderer — рисует РАСКЛАДКУ поля прямо из файла сценария (без запуска
 * партии). Pointy-top гексы: ряды горизонтальны, нечётные ряды визуально сдвинуты
 * вправо (соответствует координатной модели дизайнера «ряд/столбец»).
 *
 * <p>Каждый гекс: контур + osевой id + содержимое (грядка/стартовая грядка с
 * модификатором, контейнер, нейтрал, старт игрока, запрет). Внизу — легенда всех
 * обозначений с emoji.
 */
public final class ScenarioSvgRenderer {

    private ScenarioSvgRenderer() {
    }

    private static final double SIZE = 34;
    private static final double MARGIN = 20;
    private static final double LEGEND_H = 150;

    // цвета мест игроков (заливка старта)
    private static final String[] SEAT_FILL = {"#cfe8ff", "#ffe0cf", "#d8f0d0", "#f0d8ef"};
    private static final String[] SEAT_STROKE = {"#2b6cb0", "#c05621", "#2f855a", "#97266d"};

    /** Собрать SVG раскладки из одного сценария (map с shape/special или hexes). */
    public static String render(Map<String, Object> scenario, String title) {
        Scenario.FieldWithStarts fw = Scenario.buildFieldFromScenario(scenario);
        Field field = fw.field();
        Map<Integer, String> starts = fw.starts();
        // обратная карта: id старта -> seat
        Map<String, Integer> startSeat = new java.util.HashMap<>();
        for (Map.Entry<Integer, String> e : starts.entrySet()) {
            startSeat.put(e.getValue(), e.getKey());
        }

        List<double[]> centers = new ArrayList<>();
        List<Hex> hexes = new ArrayList<>();
        double minx = Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE;
        double maxy = -Double.MAX_VALUE;
        for (Hex h : field.hexes.values()) {
            int[] qr = parseQR(h.id);
            if (qr == null) {
                continue;
            }
            // поле развёрнуто на 30° — геометрию берём из общего источника
            double[] p = FieldGeometry.hexCenter(qr[0], qr[1], SIZE);
            double cx = p[0];
            double cy = p[1];
            centers.add(new double[]{cx, cy});
            hexes.add(h);
            minx = Math.min(minx, cx);
            maxx = Math.max(maxx, cx);
            miny = Math.min(miny, cy);
            maxy = Math.max(maxy, cy);
        }
        double w = (maxx - minx) + 2 * SIZE + 2 * MARGIN;
        double h = (maxy - miny) + 2 * SIZE + 2 * MARGIN + 30 + LEGEND_H;
        double ox = MARGIN + SIZE - minx;
        double oy = MARGIN + SIZE - miny + 24;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
            "<svg xmlns='http://www.w3.org/2000/svg' width='%.0f' height='%.0f' "
            + "font-family='sans-serif' font-size='11'>%n", w, h));
        sb.append("<rect width='100%' height='100%' fill='#fbfbfb'/>\n");
        sb.append(String.format(Locale.ROOT,
            "<text x='%.0f' y='18' font-size='15' font-weight='bold'>%s</text>%n",
            MARGIN, esc(title)));

        for (int i = 0; i < hexes.size(); i++) {
            Hex hex = hexes.get(i);
            double cx = centers.get(i)[0] + ox;
            double cy = centers.get(i)[1] + oy;
            Integer seat = startSeat.get(hex.id);
            String fill = seat != null ? SEAT_FILL[seat % 4] : hexBaseFill(hex);
            String stroke = seat != null ? SEAT_STROKE[seat % 4] : "#888";
            sb.append(hexPolygon(cx, cy, fill, stroke));
            // нейтралы с углами: объёмные блоки-здания вдоль своих рёбер
            // (на гексе их может быть несколько)
            for (Hex.NeutralBuilding nb : hex.neutrals) {
                if (nb.corners != null && nb.corners.size() >= 2) {
                    sb.append(buildingBlock(cx, cy, nb.corners, nb.big));
                }
            }
            // id гекса (мелко сверху)
            sb.append(String.format(Locale.ROOT,
                "<text x='%.1f' y='%.1f' text-anchor='middle' fill='#999' font-size='7'>%s</text>%n",
                cx, cy - SIZE * 0.55, hex.id));
            // emoji-содержимое (крупно)
            String emo = emoji(hex, seat);
            if (!emo.isEmpty()) {
                sb.append(String.format(Locale.ROOT,
                    "<text x='%.1f' y='%.1f' text-anchor='middle' font-size='18'>%s</text>%n",
                    cx, cy + 2, emo));
            }
            // подпись-текст (мелко снизу)
            String cap = caption(hex, seat);
            if (!cap.isEmpty()) {
                sb.append(String.format(Locale.ROOT,
                    "<text x='%.1f' y='%.1f' text-anchor='middle' fill='#333' font-size='8'>%s</text>%n",
                    cx, cy + SIZE * 0.62, esc(cap)));
            }
        }
        sb.append(legend(MARGIN, oy + (maxy - miny) + SIZE + MARGIN));
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static String emoji(Hex hex, Integer seat) {
        if (seat != null) {
            return "📡";                               // старт игрока
        }
        if (hex.kind == HexKind.FORBIDDEN) {
            return "⬛";                               // недоступный
        }
        if (hasCornerlessNeutral(hex)) {
            return hex.anyNeutralBig() ? "🏢" : "🏠";  // нейтрал без углов — по центру
        }
        if (hex.spawnTile != null) {
            return hex.spawnTile.isStart ? "⭐" : "🌱";     // стартовая/обычная грядка
        }
        if (hex.containerCell >= 0) {
            return "📦";                               // печатный контейнер на ячейке
        }
        return "";
    }

    private static boolean hasCornerlessNeutral(Hex hex) {
        for (Hex.NeutralBuilding nb : hex.neutrals) {
            if (nb.corners == null || nb.corners.size() < 2) {
                return true;
            }
        }
        return false;
    }

    private static String caption(Hex hex, Integer seat) {
        String base = "";
        if (seat != null) {
            base = "Игрок " + (seat + 1);
        } else if (hex.kind == HexKind.FORBIDDEN) {
            base = "недоступ.";
        } else if (hex.spawnTile != null) {
            base = hex.spawnTile.isStart ? "старт-гр." : "грядка";
            if (hex.spawnTile.stack >= 2) {
                base += " x2";
            }
            base += " К" + Math.max(0, hex.spawnTile.kelium);
        } else if (hex.containerCell >= 0) {
            base = hex.containerCell == 6 ? "конт. возд." : "конт. я" + hex.containerCell;
        }
        for (Hex.NeutralBuilding nb : hex.neutrals) {
            String neu = nb.big ? "Н2" : "Н1";
            base = base.isEmpty() ? neu : base + "+" + neu;
        }
        return base;
    }

    private static String hexBaseFill(Hex hex) {
        if (hex.kind == HexKind.FORBIDDEN) {
            return "#3a5a3a";                          // тёмно-зелёный «недоступный»
        }
        if (hex.spawnTile != null) {
            return hex.spawnTile.isStart ? "#ffd98a" : "#fff2b0";
        }
        if (hex.containerCell >= 0) {
            return "#e8e8e8";
        }
        if (hasCornerlessNeutral(hex)) {
            return hex.anyNeutralBig() ? "#c8a878" : "#e0c0a0";
        }
        return "#ffffff";
    }

    /**
     * Объёмный блок нейтрального здания вдоль рёбер между перечисленными углами
     * (1=север, по часовой). Одинарное (2 угла) — трапеция вдоль одной стены,
     * двойное (3 угла) — один сплошной блок через две стены. Блок отступает от
     * ребра, поэтому два здания через общую стенку читаются как ДВА блока со
     * стеной между ними. Внутри — метка «Н1»/«Н2».
     */
    private static String buildingBlock(double cx, double cy, List<Integer> corners,
                                        boolean big) {
        final double outer = 0.86;   // внешний край блока (зазор от ребра гекса)
        final double inner = 0.50;   // внутренний край (толщина блока)
        StringBuilder pts = new StringBuilder();
        for (int corner : corners) {
            double[] p = cornerPoint(cx, cy, corner, outer);
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ", p[0], p[1]));
        }
        for (int i = corners.size() - 1; i >= 0; i--) {
            double[] p = cornerPoint(cx, cy, corners.get(i), inner);
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ", p[0], p[1]));
        }
        String bodyFill = big ? "#8a5a2a" : "#b07c42";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
            "<polygon points='%s' fill='%s' stroke='#4d2e0c' stroke-width='1.6' "
            + "stroke-linejoin='round'/>%n", pts.toString().trim(), bodyFill));
        // метка размера в центре блока
        double mx = 0;
        double my = 0;
        for (int corner : corners) {
            double[] p = cornerPoint(cx, cy, corner, (outer + inner) / 2);
            mx += p[0];
            my += p[1];
        }
        mx /= corners.size();
        my /= corners.size();
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' text-anchor='middle' fill='#fff' "
            + "font-size='9' font-weight='bold'>%s</text>%n",
            mx, my + 3, big ? "Н2" : "Н1"));
        return sb.toString();
    }

    private static double[] cornerPoint(double cx, double cy, int corner) {
        return cornerPoint(cx, cy, corner, 1.0);
    }

    private static double[] cornerPoint(double cx, double cy, int corner, double k) {
        double ang = Math.PI / 180 * (60 * (corner - 1) - 90 + FieldGeometry.TILT);
        return new double[]{cx + SIZE * k * Math.cos(ang), cy + SIZE * k * Math.sin(ang)};
    }

    private static String hexPolygon(double cx, double cy, String fill, String stroke) {
        StringBuilder pts = new StringBuilder();
        for (int k = 0; k < 6; k++) {
            double ang = Math.PI / 180 * (60 * k - 90 + FieldGeometry.TILT);
            double px = cx + SIZE * Math.cos(ang);
            double py = cy + SIZE * Math.sin(ang);
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ", px, py));
        }
        return String.format(Locale.ROOT,
            "<polygon points='%s' fill='%s' stroke='%s' stroke-width='1.5'/>%n",
            pts.toString().trim(), fill, stroke);
    }

    private static String legend(double x, double y) {
        String[] rows = {
            "ЛЕГЕНДА:",
            "📡 старт игрока (цвет = место)   ⭐ стартовая грядка (spawn-start)",
            "🌱 обычная грядка (x2 = сдвоенная; К = келемий на грядке)",
            "📦 контейнер (x2 = два контейнера)   🏠/🏢 нейтрал малый/большой",
            "коричневый блок у стены = нейтрал: Н1 одинарное (1 стена), Н2 двойное (2 стены, темнее)",
            "⬛ недоступный гекс   ·   пустой светлый = обычное поле",
            "модификаторы грядок: +1/-1 меняют стартовый келемий, x2 — два тайла",
        };
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
            "<rect x='%.0f' y='%.0f' width='560' height='%.0f' fill='#ffffff' "
            + "stroke='#ccc'/>%n", x - 6, y - 14, LEGEND_H - 6));
        for (int i = 0; i < rows.length; i++) {
            sb.append(String.format(Locale.ROOT,
                "<text x='%.0f' y='%.0f' font-size='12'%s>%s</text>%n",
                x, y + i * 20.0, i == 0 ? " font-weight='bold'" : "", esc(rows[i])));
        }
        return sb.toString();
    }

    private static int[] parseQR(String id) {
        if (id == null || !id.startsWith("h") || !id.contains("_")) {
            return null;
        }
        try {
            String body = id.substring(1);
            int us = body.indexOf('_');
            return new int[]{Integer.parseInt(body.substring(0, us)),
                Integer.parseInt(body.substring(us + 1))};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
