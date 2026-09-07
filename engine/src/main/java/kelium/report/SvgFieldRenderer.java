package kelium.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.HexKind;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.Scoring;
import kelium.report.FieldGeometry.Shape;

/**
 * SvgFieldRenderer — ПОЛНАЯ визуализация состояния партии в SVG (по картинке на
 * раунд): поле со ВСЕМИ жетонами (здания с уровнем/уроном/энергией, войска с
 * уроном, нейтралы на рёбрах), остатки келемия и состояние тайлов зарождения,
 * контейнеры, плюс панель каждого игрока справа (ресурсы, склад, войска, здания,
 * треки, модули, арсенал, ПО с разбивкой).
 */
public final class SvgFieldRenderer {

    private SvgFieldRenderer() {
    }

    private static final double SIZE = FieldGeometry.DEFAULT_SIZE;   // радиус гекса (px)
    private static final double MARGIN = 20;
    private static final double PANEL_W = 320;

    // Цвета игроков и авторские силуэты жетонов живут в общей геометрии
    // (kelium.report.FieldGeometry): оттуда же их берёт Swing-проигрыватель,
    // чтобы два рисовальщика больше никогда не разъехались.
    private static final String[] SEAT_FILL = FieldGeometry.SEAT_FILL;
    private static final String[] SEAT_STROKE = FieldGeometry.SEAT_STROKE;
    private static final String[] SEAT_TOKEN = FieldGeometry.SEAT_TOKEN;

    private static final Shape SH_AIRCRAFT = FieldGeometry.SH_AIRCRAFT;

    private static Shape buildingShape(BuildingType t) {
        return FieldGeometry.building(t);
    }

    private static Shape unitShape(UnitType t) {
        return FieldGeometry.unit(t);
    }

    /**
     * Нарисовать силуэт жетона по коэффициенту масштаба k (пропорции формы
     * сохраняются). Центр (cx,cy), поворот rotDeg (0 = «широкой частью вниз»).
     * Толщина обводки компенсируется масштабом, чтобы визуально быть ~1.4px.
     */
    private static void emitShapeScaled(StringBuilder sb, Shape sh, double cx, double cy,
                                        double rotDeg, double k, String fill, String stroke) {
        sb.append(String.format(Locale.ROOT,
            "<g transform='translate(%.1f,%.1f) rotate(%.1f) scale(%.5f) translate(%.1f,%.1f)'>"
            + "<path d='%s' fill='%s' stroke='%s' stroke-width='%.0f'/></g>%n",
            cx, cy, rotDeg, k, -sh.vbW() / 2, -sh.vbH() / 2, sh.d(), fill, stroke,
            Math.max(30, 1.4 / k)));
    }

    /** Силуэт по целевой ширине (для авиации/перегруза — без привязки к сектору). */
    private static void emitShapeWidth(StringBuilder sb, Shape sh, double cx, double cy,
                                       double rotDeg, double targetW, String fill, String stroke) {
        emitShapeScaled(sb, sh, cx, cy, rotDeg, targetW / sh.vbW(), fill, stroke);
    }

    /** Точка на радиусе r от (cx,cy) под углом angDeg (общая геометрия). */
    private static double[] polar(double cx, double cy, double r, double angDeg) {
        return FieldGeometry.polar(cx, cy, r, angDeg);
    }

    /**
     * Полигон-клин гекса, покрывающий набор смежных сторон: центр + внешние
     * вершины рёбер (порядок и координаты — из {@link FieldGeometry}).
     */
    private static String sectorPath(double cx, double cy, List<Integer> sides) {
        List<double[]> pts = FieldGeometry.sectorPolygon(cx, cy, SIZE, sides);
        StringBuilder p = new StringBuilder();
        p.append(String.format(Locale.ROOT, "M%.1f,%.1f ", pts.get(0)[0], pts.get(0)[1]));
        for (int i = 1; i < pts.size(); i++) {
            p.append(String.format(Locale.ROOT, "L%.1f,%.1f ", pts.get(i)[0], pts.get(i)[1]));
        }
        p.append("Z");
        return p.toString();
    }

    /** Средний угол наружу для набора рёбер (общая геометрия). */
    private static double meanEdgeAngle(List<Integer> sides) {
        return FieldGeometry.meanEdgeAngle(sides);
    }

    /**
     * Посадить силуэт жетона в клин из sides: центр — на радиусе к внешнему ребру,
     * низ силуэта (широкая часть на печати) смотрит НАРУЖУ, размер вписан в сектор
     * (хорда внешних вершин × глубина клина), пропорции сохранены. drawBacking —
     * рисовать ли подложку-клин (для ЖЁСТКИХ зданий true, для мягких войск false).
     */
    /**
     * Все ЗАНЯТЫЕ наземные ячейки гекса — по владельцу стороны, кем бы он ни был.
     *
     * <p>Стенки нейтральных построек стоят в {@code sideOwner} с ОТРИЦАТЕЛЬНЫМ
     * uid. Раньше рендер собирал занятость только по зданиям игроков, и войска
     * рисовались поверх нейтралов — будто стоят «внутри» чужого здания
     * (баг, найден дизайнером 12.08.2026). Метод — единственный источник правды
     * о занятости для рендера.
     */
    public static java.util.List<Integer> occupiedSides(Hex hex) {
        java.util.List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (hex.sideOwner[i] != null) {
                out.add(i);
            }
        }
        return out;
    }

    private static double[] seatToken(StringBuilder sb, Shape sh, double cx, double cy,
                                      List<Integer> sides, String fill, String stroke,
                                      boolean drawBacking, String backingFill) {
        double face = meanEdgeAngle(sides);   // куда смотрит занятая часть гекса
        if (drawBacking) {
            sb.append(String.format(Locale.ROOT,
                "<path d='%s' fill='%s' stroke='%s' stroke-width='1.2' "
                + "stroke-linejoin='round' opacity='0.85'/>%n",
                sectorPath(cx, cy, sides), backingFill, stroke));
        }
        if (sh.seatsOnSectors()) {
            // ТОЧНАЯ посадка: масштаб такой, чтобы сторона формы совпала со
            // стороной гекса, а «центр гекса» формы — с центром гекса поля.
            // Наружу у КАЖДОЙ формы смотрит своё направление (у ЦУ 60°, у
            // казармы 120°, у добытчика и авиабазы 90°) — отсюда разный доворот.
            // Жетон чуть уменьшен, чтобы по краям была видна подложка сектора.
            double k = FieldGeometry.seatScale(sh, SIZE);
            double rot = face - sh.outward();
            sb.append(String.format(Locale.ROOT,
                "<g transform='translate(%.1f,%.1f) rotate(%.1f) scale(%.5f) "
                + "translate(%.1f,%.1f)'><path d='%s' fill='%s' stroke='%s' "
                + "stroke-width='%.0f' stroke-linejoin='round'/></g>%n",
                cx, cy, rot, k, -sh.hexCx(), -sh.hexCy(), sh.d(), fill, stroke,
                Math.max(30, 1.4 / k)));
            // точка для подписи — в середине занятой части, ближе к стенке
            return polar(cx, cy, SIZE * 0.52, face);
        }
        // Войска: небольшие жетоны в поясе у стенки (сектор они не заполняют).
        int span = Math.max(1, Math.min(3, sides.size()));
        double targetW = FieldGeometry.unitWidth(span, SIZE);
        double k = targetW / sh.vbW();
        double[] c = polar(cx, cy, FieldGeometry.unitSeatRadius(SIZE), face);
        emitShapeScaled(sb, sh, c[0], c[1], FieldGeometry.unitRotation(sh, face), k, fill, stroke);
        return c;
    }

    /** Короткий код здания для подписи на жетоне. */
    private static String buildingCode(BuildingType t) {
        return switch (t) {
            case COMMAND_CENTER -> "ЦУ";
            case FACTORY -> "Зв";
            case AIRBASE -> "Ав";
            case BARRACKS -> "Кз";
            case MINER -> "Д";
            case POWER_PLANT -> "Э";
        };
    }

    /** Подпись поверх жетона: белый текст с тёмной обводкой — читается на любом цвете. */
    private static void label(StringBuilder sb, String text, double x, double y, double size) {
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' text-anchor='middle' font-size='%.1f' font-weight='bold' "
            + "fill='#fff' stroke='#00000099' stroke-width='%.1f' paint-order='stroke' "
            + "style='paint-order:stroke'>%s</text>%n", x, y, size, size * 0.28, esc(text)));
    }

    /** Кубик урона — КРАСНЫЙ КВАДРАТ (как настоящий кубик урона на жетоне). */
    private static void damageCubes(StringBuilder sb, int damage, double x, double y) {
        double s = SIZE * 0.085;
        int n = Math.min(damage, 4);
        double x0 = x - (n * (s + 1.2) - 1.2) / 2;
        for (int d = 0; d < n; d++) {
            sb.append(String.format(Locale.ROOT,
                "<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' rx='0.8' fill='#d32f2f' "
                + "stroke='#fff' stroke-width='0.7'/>%n", x0 + d * (s + 1.2), y, s, s));
        }
    }

    // Кубик энергии рисует общий FieldPainter (ячейки, кубики и зона хранения
    // источника) — своей версии здесь больше нет, чтобы SVG и проигрыватель
    // не разошлись.

    /** Собрать SVG-строку для текущего состояния партии. */
    public static String render(GameState s, int round) {
        List<double[]> centers = new ArrayList<>();
        List<Hex> hexes = new ArrayList<>();
        double minx = Double.MAX_VALUE;
        double miny = Double.MAX_VALUE;
        double maxx = -Double.MAX_VALUE;
        double maxy = -Double.MAX_VALUE;
        for (Hex h : s.field.hexes.values()) {
            int[] qr = FieldGeometry.parseQR(h.id);
            if (qr == null) {
                continue;
            }
            double[] c0 = FieldGeometry.hexCenter(qr[0], qr[1], SIZE);
            double cx = c0[0];
            double cy = c0[1];
            centers.add(new double[]{cx, cy});
            hexes.add(h);
            minx = Math.min(minx, cx);
            maxx = Math.max(maxx, cx);
            miny = Math.min(miny, cy);
            maxy = Math.max(maxy, cy);
        }
        if (hexes.isEmpty()) {
            return "<svg xmlns='http://www.w3.org/2000/svg' width='200' height='40'>"
                + "<text x='6' y='22'>поле без осевых координат</text></svg>";
        }
        double legendH = 6 * 15 + 24;                    // место под легенду внизу
        double fieldW = (maxx - minx) + 2 * SIZE + 2 * MARGIN;
        double fieldH = (maxy - miny) + 2 * SIZE + 2 * MARGIN + 30;
        double panelH = MARGIN + 30 + s.numPlayers() * 148;
        double w = Math.max(fieldW + PANEL_W + MARGIN, 900);
        double h = Math.max(fieldH + legendH, panelH + legendH) + 16;
        double ox = MARGIN + SIZE - minx;
        double oy = MARGIN + SIZE - miny + 24;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
            "<svg xmlns='http://www.w3.org/2000/svg' width='%.0f' height='%.0f' "
            + "font-family='sans-serif' font-size='11'>%n", w, h));
        sb.append("<rect width='100%' height='100%' fill='#fbfbfb'/>\n");
        sb.append(String.format(Locale.ROOT,
            "<text x='%.0f' y='18' font-size='15' font-weight='bold'>Раунд %d — игроков %d</text>%n",
            MARGIN, round, s.numPlayers()));

        // ОДИН рендер на всё приложение: строим тот же снимок, что и запись
        // партии, и отдаём его общему FieldPainter.
        List<ReplayRecord.HexInfo> infos = new ArrayList<>();
        for (Hex hex : hexes) {
            int[] qr = FieldGeometry.parseQR(hex.id);
            ReplayRecord.HexInfo hi = new ReplayRecord.HexInfo();
            hi.id = hex.id;
            hi.q = qr[0];
            hi.r = qr[1];
            hi.kind = hex.kind.name();
            infos.add(hi);
        }
        FieldPainter.paintField(new SvgCanvas(sb), SIZE, infos,
            ReplayRecord.snapshotOf(s, null), ox, oy, true);
        sb.append(String.format(Locale.ROOT,
            "<line x1='%.0f' y1='%.1f' x2='%.0f' y2='%.1f' stroke='#ddd' stroke-width='1'/>%n",
            MARGIN, Math.max(fieldH, panelH) - 6, w - MARGIN, Math.max(fieldH, panelH) - 6));
        sb.append(legend(MARGIN, Math.max(fieldH, panelH) + 12, s.numPlayers()));

        double px = fieldW + MARGIN / 2;
        double py = MARGIN + 8;
        for (PlayerState p : s.players) {
            drawPanel(sb, s, p, px, py);
            py += 148;
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    // Отрисовка гекса живёт в FieldPainter — ОДНА на отчёты и на приложение.

    // ==================== панель игрока ===================================
    private static void drawPanel(StringBuilder sb, GameState s, PlayerState p,
                                  double x, double y) {
        String stroke = SEAT_STROKE[p.seat % 4];
        String fill = SEAT_FILL[p.seat % 4];
        sb.append(String.format(Locale.ROOT,
            "<rect x='%.0f' y='%.0f' width='%.0f' height='140' rx='6' fill='#fff' "
            + "stroke='%s' stroke-width='1.5'/>%n", x, y, PANEL_W - 8, stroke));
        sb.append(String.format(Locale.ROOT,
            "<rect x='%.0f' y='%.0f' width='%.0f' height='18' rx='6' fill='%s'/>%n",
            x, y, PANEL_W - 8, fill));
        Map<String, Integer> bd = Scoring.scorePlayer(s, p.seat);
        int vp = bd.getOrDefault("total", 0);
        sb.append(String.format(Locale.ROOT,
            "<text x='%.0f' y='%.0f' font-size='11' font-weight='bold'>Игрок %d (место %d, %s)"
            + "  —  %d ПО</text>%n", x + 8, y + 13, p.seat + 1, p.seat,
            esc(p.board.troop.side), vp));

        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT,
            "монеты %d · келемий %d · БП %d · трофеи %d (трофеев %d)",
            p.resources.coin(), p.resources.kelium(), p.resources.ammo(),
            p.resources.trophy(), p.destroyedTokens.size()));
        lines.add(String.format(Locale.ROOT,
            "контейнеры %d · рука: заданий %d, арсенала %d · установлено арс. %d",
            p.containers, p.objectiveHand.size(), p.arsenalHand.size(),
            p.arsenalInstalled.size()));
        // войска по типам (на поле / в резерве)
        Map<String, Integer> ut = new LinkedHashMap<>();
        int reserve = 0;
        for (UnitToken u : p.units) {
            if (u.hexId != null && u.alive()) {
                ut.merge(unit(u.type), 1, Integer::sum);
            } else if (u.hexId == null) {
                reserve++;
            }
        }
        StringBuilder us = new StringBuilder();
        for (var e : ut.entrySet()) {
            us.append(e.getKey()).append("×").append(e.getValue()).append(" ");
        }
        lines.add("войска: " + (us.length() > 0 ? us.toString().trim() : "—")
            + " (резерв " + reserve + ")");
        // здания по типам + энергия
        Map<String, Integer> bt = new LinkedHashMap<>();
        int powered = 0;
        int total = 0;
        int energyCubes = 0;
        for (BuildingToken b : p.buildingsOnField()) {
            bt.merge(bld(b.type), 1, Integer::sum);
            total++;
            energyCubes += b.energyPlaced;
            if (b.powered()) {
                powered++;
            }
        }
        StringBuilder bs = new StringBuilder();
        for (var e : bt.entrySet()) {
            bs.append(e.getKey()).append("×").append(e.getValue()).append(" ");
        }
        lines.add("здания: " + (bs.length() > 0 ? bs.toString().trim() : "—"));
        lines.add(String.format(Locale.ROOT,
            "энергия: %d кубов, запитано %d/%d зданий", energyCubes, powered, total));
        // треки науки
        StringBuilder ts = new StringBuilder();
        for (var e : p.techSteps.entrySet()) {
            if (e.getValue() > 0) {
                ts.append(e.getKey()).append(":").append(e.getValue()).append(" ");
            }
        }
        lines.add("треки: " + (ts.length() > 0 ? ts.toString().trim() : "—")
            + String.format(Locale.ROOT, " · модули: кр.%d син.%d зол.%d",
                p.redModules, p.blueModules, p.goldModules));
        // топ-3 источника ПО
        List<Map.Entry<String, Integer>> src = new ArrayList<>(bd.entrySet());
        src.removeIf(e -> "total".equals(e.getKey()) || e.getValue() == 0);
        src.sort((a, b2) -> b2.getValue() - a.getValue());
        StringBuilder vs = new StringBuilder();
        for (int i = 0; i < Math.min(3, src.size()); i++) {
            vs.append(src.get(i).getKey()).append("=").append(src.get(i).getValue()).append(" ");
        }
        lines.add("ПО из: " + (vs.length() > 0 ? vs.toString().trim() : "—"));

        double ly = y + 32;
        for (String line : lines) {
            sb.append(String.format(Locale.ROOT,
                "<text x='%.0f' y='%.0f' font-size='9.5'>%s</text>%n", x + 8, ly, esc(line)));
            ly += 15;
        }
    }

    /**
     * Значок «войско укрыто в своём здании» (§5.3): кружок цвета игрока с буквой
     * рода войск. Маленький и сбоку — здание под ним остаётся видно.
     */
    private static void hiddenMark(StringBuilder sb, UnitToken u, double x, double y) {
        double r = SIZE * 0.115;
        sb.append(String.format(Locale.ROOT,
            "<circle cx='%.1f' cy='%.1f' r='%.1f' fill='%s' stroke='#fff' "
            + "stroke-width='1.4'/>%n", x, y, r, SEAT_TOKEN[u.owner % 4]));
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' text-anchor='middle' font-size='%.1f' "
            + "font-weight='bold' fill='#fff'>%s</text>%n",
            x, y + r * 0.62, r * 1.5, unit(u.type)));
        if (u.damage > 0) {
            damageCubes(sb, u.damage, x, y - r * 2.2);
        }
    }

    private static void drawUnitDamage(StringBuilder sb, UnitToken u, double x, double y) {
        damageCubes(sb, u.damage, x, y - SIZE * 0.20);
    }

    // ==================== примитивы =======================================
    private static String hexPolygon(double cx, double cy, String fill, String stroke) {
        StringBuilder pts = new StringBuilder();
        for (int k = 0; k < 6; k++) {
            double ang = Math.PI / 180 * (60 * k - 90 + FieldGeometry.TILT);
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ",
                cx + SIZE * Math.cos(ang), cy + SIZE * Math.sin(ang)));
        }
        return String.format(Locale.ROOT,
            "<polygon points='%s' fill='%s' stroke='%s' stroke-width='1.5'/>%n",
            pts.toString().trim(), fill, stroke);
    }

    /** Блок нейтрального здания вдоль рёбер (как в ScenarioSvgRenderer). */
    private static String neutralBlock(double cx, double cy, Hex.NeutralBuilding nb) {
        final double outer = 0.86;
        final double inner = 0.50;
        StringBuilder pts = new StringBuilder();
        for (int corner : nb.corners) {
            double ang = Math.PI / 180 * (60 * (corner - 1) - 90 + FieldGeometry.TILT);
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ",
                cx + SIZE * outer * Math.cos(ang), cy + SIZE * outer * Math.sin(ang)));
        }
        for (int i = nb.corners.size() - 1; i >= 0; i--) {
            double ang = Math.PI / 180 * (60 * (nb.corners.get(i) - 1) - 90
                + FieldGeometry.TILT);
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ",
                cx + SIZE * inner * Math.cos(ang), cy + SIZE * inner * Math.sin(ang)));
        }
        return String.format(Locale.ROOT,
            "<polygon points='%s' fill='%s' stroke='#33383E' stroke-width='3.0' "
            + "stroke-linejoin='round'/>%n", pts.toString().trim(),
            nb.big ? "#7C838B" : "#9AA0A6");
    }

    /** Гекс произвольного радиуса с заданной заливкой и обводкой. */
    private static String hexPolygonAt(double cx, double cy, double r, String fill,
                                       String stroke, double width) {
        StringBuilder pts = new StringBuilder();
        for (int k = 0; k < 6; k++) {
            double ang = Math.PI / 180 * (60 * k - 90 + FieldGeometry.TILT);
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ",
                cx + r * Math.cos(ang), cy + r * Math.sin(ang)));
        }
        return String.format(Locale.ROOT,
            "<polygon points='%s' fill='%s' stroke='%s' stroke-width='%.1f'/>%n",
            pts.toString().trim(), fill, stroke, width);
    }

    /** Контейнеры — карточки со скруглением; двойной лежит с диагональным сдвигом. */
    private static void drawContainers(StringBuilder sb, int count, double cx, double cy) {
        double s = SIZE * 0.26;
        double off = count >= 2 ? SIZE * 0.07 : 0;
        for (int i = Math.min(count, 2) - 1; i >= 0; i--) {
            double x = cx - s / 2 + (i == 0 ? -off : off);
            double y = cy - s / 2 + (i == 0 ? off : -off);
            sb.append(String.format(Locale.ROOT,
                "<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' rx='%.1f' fill='#E8C77B' "
                + "stroke='#6E4E13' stroke-width='1.6'/>%n", x, y, s, s, s * 0.28));
        }
    }

    private static final String SPAWN_NORMAL = "#2E7D32";   // тёмно-зелёный
    private static final String SPAWN_START = "#A5D6A7";    // светло-зелёный

    private static String hexBaseFill(Hex hex) {
        if (hex.kind == HexKind.FORBIDDEN) {
            return "#4a4844";
        }
        return "#ffffff";   // тайл зарождения рисуется отдельным жетоном поверх
    }

    private static String bld(BuildingType t) {
        return switch (t) {
            case COMMAND_CENTER -> "ЦУ";
            case FACTORY -> "Зв";
            case AIRBASE -> "Ав";
            case BARRACKS -> "Кз";
            case MINER -> "Д";
            case POWER_PLANT -> "Э";
        };
    }

    private static String unit(UnitType t) {
        return switch (t) {
            case INFANTRY -> "п";
            case VEHICLE -> "т";
            case AIRCRAFT -> "а";
            case TOWER -> "в";
        };
    }

    /** Легенда под полем: несколько строк с образцами, а не одна длинная строка. */
    private static String legend(double x, double y, int players) {
        StringBuilder sb = new StringBuilder();
        double lh = 15;
        sb.append(String.format(Locale.ROOT,
            "<text x='%.0f' y='%.1f' font-size='12' font-weight='bold' fill='#333'>"
            + "Условные обозначения</text>%n", x, y));

        double ly = y + lh + 2;
        // образцы жетонов игроков
        double sx = x + 4;
        for (int seat = 0; seat < players; seat++) {
            sb.append(String.format(Locale.ROOT,
                "<rect x='%.1f' y='%.1f' width='11' height='11' rx='2' fill='%s' "
                + "stroke='%s' stroke-width='1.2'/>%n", sx, ly - 9,
                SEAT_TOKEN[seat % 4], SEAT_STROKE[seat % 4]));
            sb.append(String.format(Locale.ROOT,
                "<text x='%.1f' y='%.1f' font-size='10' fill='#444'>Игрок %d</text>%n",
                sx + 15, ly, seat + 1));
            sx += 66;
        }

        ly += lh;
        sb.append(String.format(Locale.ROOT,
            "<text x='%.0f' y='%.1f' font-size='10' fill='#444'>"
            + "Здания (подпись на жетоне): ЦУ — центр управления · Кз — казарма · "
            + "Зв — завод · Ав — авиабаза · Э — энергостанция · Д — добытчик "
            + "(цифра рядом — номер/уровень)</text>%n", x, ly));

        ly += lh;
        sb.append(String.format(Locale.ROOT,
            "<text x='%.0f' y='%.1f' font-size='10' fill='#444'>"
            + "Войска: квадрат — пехота · шестиугольник — техника (занимает 2 ячейки) · "
            + "восьмиугольник — авиация (в центре гекса) · трапеция — вышка. "
            + "Белый мелкий силуэт на здании — войско укрыто внутри.</text>%n", x, ly));

        ly += lh;
        sb.append(String.format(Locale.ROOT,
            "<rect x='%.1f' y='%.1f' width='10' height='10' rx='1' fill='#ffc400' "
            + "stroke='#a07800' stroke-width='0.9'/>%n", x + 4, ly - 8));
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' font-size='10' fill='#444'>— кубик энергии "
            + "(пустой контур = здание не запитано)</text>%n", x + 18, ly));
        sb.append(String.format(Locale.ROOT,
            "<rect x='%.1f' y='%.1f' width='10' height='10' rx='1' fill='#d32f2f' "
            + "stroke='#fff' stroke-width='0.7'/>%n", x + 250, ly - 8));
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' font-size='10' fill='#444'>— кубик урона</text>%n",
            x + 264, ly));

        ly += lh;
        sb.append(String.format(Locale.ROOT,
            "<polygon points='%s' fill='%s' stroke='#1B5E20' stroke-width='1.2'/>%n",
            miniHex(x + 9, ly - 4, 7), SPAWN_NORMAL));
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' font-size='10' fill='#444'>— тайл зарождения (K), "
            + "светло-зелёный — стартовый (S); подпись — остаток келемия, "
            + "×2 — двойной тайл, (об) — оборот</text>%n", x + 20, ly));

        ly += lh;
        sb.append(String.format(Locale.ROOT,
            "<rect x='%.1f' y='%.1f' width='10' height='10' rx='3' fill='#E8C77B' "
            + "stroke='#6E4E13' stroke-width='1.2'/>%n", x + 4, ly - 8));
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' font-size='10' fill='#444'>— контейнер</text>%n",
            x + 18, ly));
        sb.append(String.format(Locale.ROOT,
            "<rect x='%.1f' y='%.1f' width='14' height='9' fill='#9AA0A6' "
            + "stroke='#33383E' stroke-width='2'/>%n", x + 118, ly - 8));
        sb.append(String.format(Locale.ROOT,
            "<text x='%.1f' y='%.1f' font-size='10' fill='#444'>— нейтральное здание "
            + "(стоит на стенках гекса) · тёмный гекс — запретный</text>%n", x + 136, ly));
        return sb.toString();
    }

    private static String miniHex(double cx, double cy, double r) {
        StringBuilder pts = new StringBuilder();
        for (int k = 0; k < 6; k++) {
            double a = Math.PI / 180 * (60 * k - 90);
            pts.append(String.format(Locale.ROOT, "%.1f,%.1f ",
                cx + r * Math.cos(a), cy + r * Math.sin(a)));
        }
        return pts.toString().trim();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
