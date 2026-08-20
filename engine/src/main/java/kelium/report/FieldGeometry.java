package kelium.report;

import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kelium.core.BuildingType;
import kelium.core.UnitType;

/**
 * FieldGeometry — ЕДИНЫЙ источник геометрии поля и форм жетонов.
 *
 * <p>Раньше вся геометрия жила внутри {@link SvgFieldRenderer}, и когда рядом
 * появился второй рисовальщик (конструктор раскладок), они разошлись по стилю и
 * сводить их пришлось вручную. Чтобы это не повторилось с проигрывателем партий,
 * координаты гексов, углы сторон, посадка зданий на секторы и сами авторские
 * силуэты вынесены сюда. SVG-рендер печатает отсюда пути и матрицы в текст,
 * Swing-рендер берёт те же данные как {@link Path2D} и {@link AffineTransform}.
 *
 * <p>Договорённости (проверены, заново не выводить):
 * <ul>
 *   <li>гекс «остриём вверх»: {@code cx = SIZE·√3·(q + r/2)}, {@code cy = SIZE·1.5·r};</li>
 *   <li>направление наружу через сторону i равно {@code −60·i} градусов (y вниз);</li>
 *   <li>углы гекса — {@code 60k − 90}; ребро между углами c и c+1 = сторона
 *       {@code (2−c) mod 6};</li>
 *   <li>авторские SVG жетонов экспортированы ОДНОЙ шириной (32 мм), поэтому
 *       масштаб у них РАЗНЫЙ. Настоящий масштаб задаёт геометрия: внешняя граница
 *       здания состоит ровно из N сторон гекса, длина стороны в единицах формы —
 *       это {@link Shape#edgeUnit()}.</li>
 * </ul>
 */
public final class FieldGeometry {

    private FieldGeometry() {
    }

    /** Радиус гекса по умолчанию (тот же, что исторически в SVG-рендере). */
    public static final double DEFAULT_SIZE = 52;

    /** Уменьшение силуэта здания, чтобы по краям была видна подложка сектора. */
    public static final double BUILDING_SHRINK = 0.88;

    // ===================== ПОРЯДОК СЛОЁВ (общий для обоих рендеров) =========
    /**
     * ПОРЯДОК РИСОВАНИЯ содержимого гекса — обязателен и для SVG-рендера
     * отчётов, и для Swing-вида проигрывателя. Держать одинаковым, иначе одна
     * и та же партия выглядит в двух местах по-разному:
     * <ol>
     *   <li>сам гекс (заливка и контур);</li>
     *   <li>тайл зарождения — он закрывает гекс целиком;</li>
     *   <li>нейтральные постройки на рёбрах;</li>
     *   <li>подпись гекса;</li>
     *   <li>ЗДАНИЯ: подложка занятых секторов + силуэт + подпись и кубики;</li>
     *   <li><b>ВОЗДУШНАЯ ЯЧЕЙКА</b> — ПОВЕРХ зданий (решение дизайнера
     *       2026-08-12): она принадлежит гексу целиком, а не сектору, и должна
     *       быть видна даже когда здание занимает середину. Раньше её рисовали
     *       до зданий, и бледная подложка сектора её закрывала;</li>
     *   <li>войска (авиация садится в эту самую ячейку и рисуется ПОСЛЕ неё);</li>
     *   <li>контейнеры и пометка запретного гекса.</li>
     * </ol>
     */
    public static final String LAYER_ORDER =
        "гекс, ПЕЧАТНЫЙ КОНТЕЙНЕР (нарисован на картоне — под всем), тайл, "
        + "нейтралы, подпись, здания, ВОЗДУШНАЯ ЯЧЕЙКА, войска";

    /** Радиус воздушной ячейки в долях радиуса гекса. */
    public static final double AIR_CELL_R = 0.26;
    /** Цвет контура воздушной ячейки. */
    public static final String AIR_CELL_STROKE = "#c9cdd2";
    /** Толщина контура воздушной ячейки (экранных пикселей). */
    public static final double AIR_CELL_WIDTH = 1.4;

    // ===================== цвета игроков (общие для обоих рендеров) =========
    /** Бледная заливка (подложка сектора, панели). */
    public static final String[] SEAT_FILL = {"#cfe8ff", "#ffe0cf", "#d8f0d0", "#f0d8ef"};
    /** Обводка жетонов и рамок игрока. */
    public static final String[] SEAT_STROKE = {"#2b6cb0", "#c05621", "#2f855a", "#97266d"};
    /** Насыщенный цвет игрока — заливка САМОЙ формы жетона. */
    public static final String[] SEAT_TOKEN = {"#3b82d0", "#e07038", "#3f9e60", "#b04a96"};

    // ===================== силуэты жетонов ==================================
    /**
     * Силуэт жетона.
     *
     * @param d        путь SVG в собственных координатах формы
     * @param vbW      ширина viewBox формы
     * @param vbH      высота viewBox формы
     * @param edgeUnit длина СТОРОНЫ ГЕКСА в единицах формы (0 = форма не садится
     *                 на секторы, масштабируется по ширине)
     * @param hexCx    где в координатах формы лежит ЦЕНТР ГЕКСА (x)
     * @param hexCy    где в координатах формы лежит ЦЕНТР ГЕКСА (y)
     * @param outward  куда в координатах формы смотрит «наружу» (градусы, y вниз)
     * @param baseUp   широкая сторона нарисована ВВЕРХУ viewBox (вышка)
     */
    public record Shape(String d, double vbW, double vbH, double edgeUnit,
                        double hexCx, double hexCy, double outward, boolean baseUp) {

        /** Жетон войска: масштабируется по ширине, привязки к секторам нет. */
        public Shape(String d, double vbW, double vbH) {
            this(d, vbW, vbH, 0, vbW / 2, vbH / 2, 90, false);
        }

        /** Садится ли форма ровно на занятые стороны гекса (здания). */
        public boolean seatsOnSectors() {
            return edgeUnit > 0;
        }

        /** Контур формы как {@link Path2D} (кэшируется по строке пути). */
        public Path2D.Double path() {
            return FieldGeometry.path(d);
        }
    }

    public static final Shape SH_INFANTRY = new Shape(
        "M380.6 13.39l2412.02 0c216.69,0 393.99,177.3 393.99,393.99l0 2412.02c0,216.69 "
        + "-177.3,393.99 -393.99,393.99l-2412.02 0c-216.69,0 -393.99,-177.3 -393.99,-393.99"
        + "l0 -2412.02c0,-216.69 177.3,-393.99 393.99,-393.99z", 3200, 3200);

    /**
     * ТЕХНИКА: широкая сторона нарисована ВВЕРХУ viewBox — как у вышки, поэтому
     * разворот тот же. Без этого жетон вставал к стенке на 180° не в ту сторону
     * (замечание дизайнера 13.08.2026).
     */
    public static final Shape SH_VEHICLE = new Shape(
        "M1722.63 38.19l1347.49 777.92c81.12,46.83 126.16,132.97 125.1,220.82 0.48,43.36 "
        + "-10.22,87.34 -33.35,127.41l-608.46 1053.81c-68.92,119.38 -222.99,160.65 -342.38,91.73"
        + "l-615.78 -355.5 -615.78 355.5c-119.38,68.91 -273.45,27.64 -342.38,-91.73"
        + "l-608.46 -1053.81c-23.14,-40.07 -33.84,-84.05 -33.35,-127.41 -1.07,-87.85 43.98,-174 "
        + "125.1,-220.82l1347.49 -777.92 0.01 0c39.99,-23.09 83.88,-33.8 127.15,-33.36l0.44 0"
        + "c43.27,-0.43 87.15,10.27 127.15,33.36l0.01 0z", 3200, 2338.46,
        0, 1600, 1169.23, 90, true);

    public static final Shape SH_AIRCRAFT = new Shape(
        "M1168.5 2.19l429.32 -0.01 429.36 0c271.2,0.01 504.03,134.37 639.61,369.1"
        + "l214.67 371.63 214.67 371.63c135.59,234.74 135.59,503.45 -0.01,738.22"
        + "l-214.68 371.66 -214.67 371.6c-135.59,234.77 -368.41,369.13 -639.63,369.11"
        + "l-429.3 0.01 -429.36 0c-271.21,0 -504.04,-134.36 -639.63,-369.1"
        + "l-214.67 -371.63 -214.65 -371.63c-135.6,-234.74 -135.6,-503.44 0,-738.21"
        + "l214.68 -371.66 214.67 -371.61c135.6,-234.76 368.43,-369.13 639.63,-369.11z",
        3200, 2962.96);

    /** Вышка: широкая сторона нарисована ВВЕРХУ viewBox — отсюда разворот на 180°. */
    public static final Shape SH_TOWER = new Shape(
        "M2238.23 2081l-1278.45 0c-89.04,0 -165.2,-49.2 -201.83,-130.39l-738.56 -1636.9"
        + "c-31.86,-70.62 -26.2,-146.25 15.81,-211.34 42.01,-65.09 108.58,-101.36 186.02,-101.36"
        + "l2755.56 0c77.45,0 144.02,36.27 186.03,101.36 42.01,65.09 47.66,140.72 15.8,211.34"
        + "l-738.55 1636.9c-36.64,81.19 -112.79,130.39 -201.83,130.39z", 3200, 2080,
        0, 1600, 1040, 90, true);

    /**
     * ЦУ: внешняя граница — ДВЕ стороны гекса (обе ≈2140) с углом 120° в точке
     * (2135.3, 1859.63). Центр гекса = этот угол + R по внутренней биссектрисе.
     */
    public static final Shape SH_CU = new Shape(
        "M495.43 984.8L1596.95 984.8 2195.7 0.24 3199.63 0.24 2135.3 1859.63 -0.37 1859.63Z",
        3200, 1859.39, 2140, 1062, 8, 59.9, false);

    /**
     * Авиабаза: ТРИ стороны гекса (≈1595 каждая), средняя — нижняя (y≈1376,
     * от 798 до 2402). Центр гекса — на апофему выше её середины.
     */
    public static final Shape SH_AIRBASE = new Shape(
        "M2385.84 1.47L2066.29 503.64 1134.33 503.84 841.89 1.81 -0.38 1.99 798.08 1376 "
        + "2401.76 1375.65 3199.62 1.3Z", 3200, 1374.7, 1595, 1600, -5, 90, false);

    /**
     * Добытчик/энергостанция: ОДНА сторона гекса (нижняя, 3199), боковые грани
     * под 30° — это сектор гекса со срезанной верхушкой.
     */
    public static final Shape SH_MINER_PLANT = new Shape(
        "M2258.79 6.18L3195.24 1647.21 -4.77 1647.21 931.68 6.18Z",
        3200, 1641.03, 3199, 1595, -1123, 90, false);

    /**
     * Казарма/завод: ДВЕ стороны гекса (≈2138) с углом в точке (1063.95, 1860.35)
     * — зеркально ЦУ.
     */
    public static final Shape SH_BARRACKS_FACTORY = new Shape(
        "M2560.6 732.81L1754.47 732.81 1309.41 0.96 -0.37 0.96 1063.95 1860.35 3199.62 1860.35Z",
        3200, 1859.39, 2138, 2134, 7, 120.0, false);

    /** Силуэт здания по его типу. */
    public static Shape building(BuildingType t) {
        return switch (t) {
            case COMMAND_CENTER -> SH_CU;
            case AIRBASE -> SH_AIRBASE;
            case BARRACKS, FACTORY -> SH_BARRACKS_FACTORY;
            case MINER, POWER_PLANT -> SH_MINER_PLANT;
        };
    }

    /** Силуэт войска по его роду. */
    public static Shape unit(UnitType t) {
        return switch (t) {
            case INFANTRY -> SH_INFANTRY;
            case VEHICLE -> SH_VEHICLE;
            case AIRCRAFT -> SH_AIRCRAFT;
            case TOWER -> SH_TOWER;
        };
    }

    /** Силуэт здания по короткому коду типа (для записи партии). */
    public static Shape buildingByCode(String code) {
        return building(BuildingType.fromCode(code));
    }

    /** Силуэт войска по короткому коду рода (для записи партии). */
    public static Shape unitByCode(String code) {
        return unit(UnitType.fromCode(code));
    }

    // ===================== координаты и углы ================================

    /** Разобрать id гекса вида {@code h<q>_<r>} в осевые координаты; null — не тот формат. */
    public static int[] parseQR(String id) {
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

    /**
     * ПОВОРОТ ВСЕГО ПОЛЯ. Раньше гексы стояли «остриём вверх» (pointy-top). Дизайнер
     * попросил развернуть поле на 30° — гексы стали «плашмя вверх» (flat-top),
     * как жетон авиации (13.08.2026).
     *
     * <p>Что важно понимать про это решение: поворот на 30° — это ПОВОРОТ КАРТИНКИ,
     * а не переиндексация. Осевые координаты гексов в yaml остались прежними и
     * менять их НЕ НУЖНО — они не знают ничего про ориентацию. Вся раскладка
     * (кто с кем сосед, какая сторона куда смотрит) сохраняется точно: и центры
     * гексов, и углы сторон повёрнуты на один и тот же угол, поэтому сторона s
     * по-прежнему смотрит ровно на своего соседа s.
     *
     * <p>И математический факт, который стоит помнить: развернуть гексы на 30°,
     * оставив общий силуэт поля в прежнем повороте, НЕЛЬЗЯ. Решётка гексов имеет
     * симметрию 60°, поворот на 30° переводит её в другую решётку — силуэт поля
     * неизбежно повернётся вместе с гексами. Никакая правка координат в yaml этого
     * не отменяет (и потому ничего в yaml не тронуто: любая такая правка только
     * ломала бы раскладки).
     */
    public static final double TILT = 30;

    /** Центр гекса «плашмя вверх» по осевым координатам (поле повёрнуто на TILT). */
    public static double[] hexCenter(int q, int r, double size) {
        return new double[]{size * 1.5 * q, size * Math.sqrt(3) * (r + q / 2.0)};
    }

    /**
     * Обратно к {@link #hexCenter}: какой гекс лежит под точкой (x, y).
     * Округление ведётся в кубических координатах — это даёт ТОЧНЫЕ границы
     * шестиугольников, без «мёртвых зон» у вершин и без наложения кругов.
     *
     * @return осевые координаты {q, r}
     */
    public static int[] hexAt(double x, double y, double size) {
        double qf = (2.0 / 3 * x) / size;
        double rf = (-x / 3.0 + Math.sqrt(3) / 3 * y) / size;
        double xf = qf;
        double zf = rf;
        double yf = -xf - zf;
        long rx = Math.round(xf);
        long ry = Math.round(yf);
        long rz = Math.round(zf);
        double dx = Math.abs(rx - xf);
        double dy = Math.abs(ry - yf);
        double dz = Math.abs(rz - zf);
        if (dx > dy && dx > dz) {
            rx = -ry - rz;
        } else if (dy > dz) {
            ry = -rx - rz;
        } else {
            rz = -rx - ry;
        }
        return new int[]{(int) rx, (int) rz};
    }

    /**
     * РЕАЛЬНЫЙ угол (градусы, y вниз) наружу через середину ребра к соседу i.
     * Выведен из {@code Field.AXIAL_DIRS} в пиксельных координатах pointy-top.
     */
    public static double edgeAngle(int side) {
        return -60.0 * side + TILT;
    }

    /** Апофема гекса (расстояние от центра до середины ребра). */
    public static double apothem(double size) {
        return size * Math.cos(Math.toRadians(30));
    }

    /** Точка на радиусе r от (cx,cy) под углом angDeg. */
    public static double[] polar(double cx, double cy, double r, double angDeg) {
        double a = Math.toRadians(angDeg);
        return new double[]{cx + r * Math.cos(a), cy + r * Math.sin(a)};
    }

    /** Нормировать угол в диапазон (−180, 180]. */
    public static double norm180(double a) {
        double x = ((a % 360) + 360) % 360;
        return x > 180 ? x - 360 : x;
    }

    /**
     * ФОРМА НЕЙТРАЛЬНОЙ ПОСТРОЙКИ: трапеция на одной стороне гекса, а на двух —
     * две трапеции, слитые боком под углом. Возвращает точки многоугольника:
     * сперва внешняя кромка по углам {@code corners}, потом внутренняя в обратном
     * порядке.
     *
     * <p>Одна функция на всех: ею рисуется сам нейтрал, по ней же считается рамка
     * под текстуру и рисуется заготовка — иначе картинка ляжет не на ту форму.
     */
    public static double[][] neutralShape(double cx, double cy, double size,
                                          java.util.List<Integer> corners,
                                          double outer, double inner) {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (int corner : corners) {
            double a = Math.toRadians(60.0 * (corner - 1) - 90 + TILT);
            pts.add(new double[]{cx + size * outer * Math.cos(a),
                cy + size * outer * Math.sin(a)});
        }
        for (int i = corners.size() - 1; i >= 0; i--) {
            double a = Math.toRadians(60.0 * (corners.get(i) - 1) - 90 + TILT);
            pts.add(new double[]{cx + size * inner * Math.cos(a),
                cy + size * inner * Math.sin(a)});
        }
        return pts.toArray(new double[0][]);
    }

    /**
     * РАМКА ПОД ТЕКСТУРУ НЕЙТРАЛА в его собственных осях: {@code {cx, cy, w, h,
     * rotDeg}}. Ось X идёт вдоль стенки, ось Y — наружу, поэтому одна картинка
     * годится для любой стороны гекса: её просто поворачивают.
     */
    public static double[] neutralBox(double cx, double cy, double size,
                                      java.util.List<Integer> corners,
                                      double outer, double inner) {
        double sx = 0;
        double sy = 0;
        for (int corner : corners) {
            double a = Math.toRadians(60.0 * (corner - 1) - 90 + TILT);
            sx += Math.cos(a);
            sy += Math.sin(a);
        }
        double bl = Math.max(0.0001, Math.hypot(sx, sy));
        double vx = sx / bl;                    // наружу (биссектриса)
        double vy = sy / bl;
        double ux = -vy;                        // вдоль стенки
        double uy = vx;
        double minU = Double.MAX_VALUE;
        double maxU = -Double.MAX_VALUE;
        double minV = Double.MAX_VALUE;
        double maxV = -Double.MAX_VALUE;
        for (double[] p : neutralShape(0, 0, size, corners, outer, inner)) {
            double u = p[0] * ux + p[1] * uy;
            double v = p[0] * vx + p[1] * vy;
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        double mu = (minU + maxU) / 2;
        double mv = (minV + maxV) / 2;
        return new double[]{
            cx + mu * ux + mv * vx,
            cy + mu * uy + mv * vy,
            maxU - minU,
            maxV - minV,
            Math.toDegrees(Math.atan2(uy, ux))};
    }

    /** Средний угол наружу для набора рёбер (через векторную сумму). */
    public static double meanEdgeAngle(List<Integer> sides) {
        if (sides == null || sides.isEmpty()) {
            return -90;
        }
        double vx = 0;
        double vy = 0;
        for (int s : sides) {
            double a = Math.toRadians(edgeAngle(s));
            vx += Math.cos(a);
            vy += Math.sin(a);
        }
        return Math.toDegrees(Math.atan2(vy, vx));
    }

    /** Вершины гекса радиуса r (углы 60k−90+TILT). */
    public static double[][] hexCorners(double cx, double cy, double r) {
        double[][] pts = new double[6][];
        for (int k = 0; k < 6; k++) {
            double ang = Math.toRadians(60.0 * k - 90 + TILT);
            pts[k] = new double[]{cx + r * Math.cos(ang), cy + r * Math.sin(ang)};
        }
        return pts;
    }

    /**
     * ДОЛЯ РЕБРА, УХОДЯЩАЯ В СКРУГЛЕНИЕ УГЛА у печатных деталей — тайлов
     * зарождения, запретных гексов и блоков поля (просьба дизайнера 19.08.2026:
     * «легкие скругления своих углов»). Настоящая картонка режется с
     * закруглением, острый угол на ней — примета чертежа, а не детали.
     *
     * <p>Значение мелкое СОЗНАТЕЛЬНО: скругление должно читаться как фаска, а не
     * превращать шестиугольник в пятно. При 0.12 срезается по одной восьмой
     * каждого ребра с двух концов — на глаз это мягкий угол при сохранённой форме.
     */
    public static final double TILE_ROUND = 0.12;

    // ==================================================================
    //  ФИГУРЫ РАЗМЕТКИ ЯЧЕЕК — ОДИН ИСТОЧНИК НА ВСЕ РИСОВАЛЬЩИКИ
    // ==================================================================
    //  Вынесены сюда 19.08.2026 после замечания дизайнера: в каталоге блоков
    //  энергозона рисовалась КРУГОМ, а контейнер — ромбом по осям экрана, хотя
    //  на поле это трапеция по границам ячейки с молнией и квадрат, ПОВЁРНУТЫЙ
    //  по стороне гекса. Причина ровно одна: каждый рисовальщик выводил фигуру
    //  сам, и они разъехались. Теперь форму задаёт геометрия, а рисовальщики
    //  только заливают и обводят — разъехаться больше нечему.

    /**
     * ОБВОДКА ЭНЕРГОЯЧЕЙКИ — трапеция, повторяющая границы ячейки {@code cell}:
     * наружу по радиусу гекса, внутрь — до кромки воздушной ячейки, а боковые
     * стороны лежат на тех же лучах, что делят соседние ячейки.
     *
     * <p>Отступы не декоративные: впритык к кромке гекса на трапецию налезали
     * краями соседние здания и разметка читалась грязно (замечание дизайнера
     * 16.08.2026), а основание ровно по радиусу воздушной ячейки пряталось под
     * ней, и трапеция читалась треугольником до центра.
     *
     * @param stroke толщина линии, которой её потом обведут — учитывается, чтобы
     *               линия не вылезала за кромку гекса
     */
    public static double[][] energyCellOutline(double cx, double cy, double size,
                                               int cell, double stroke) {
        double ang = edgeAngle(cell);
        double margin = size * 0.07;
        double side = 30 - 4;
        double outer = size - stroke / 2 - margin;
        double inner = size * (AIR_CELL_R + 0.10) + stroke / 2;
        return new double[][]{
            polar(cx, cy, inner, ang - side),
            polar(cx, cy, outer, ang - side),
            polar(cx, cy, outer, ang + side),
            polar(cx, cy, inner, ang + side)
        };
    }

    /** Середина энергоячейки — куда ставится молния (и подпись у здания). */
    public static double[] energyCellSpot(double cx, double cy, double size, int cell) {
        return polar(cx, cy, size * 0.62, edgeAngle(cell));
    }

    /** Знак молнии высотой {@code h} с центром в точке (x, y). */
    public static double[][] boltPolygon(double x, double y, double h) {
        double w = h * 0.52;
        return new double[][]{
            {x + 0.10 * w, y - 0.50 * h},
            {x - 0.50 * w, y + 0.08 * h},
            {x - 0.06 * w, y + 0.08 * h},
            {x - 0.16 * w, y + 0.50 * h},
            {x + 0.50 * w, y - 0.12 * h},
            {x + 0.04 * w, y - 0.12 * h}
        };
    }

    /**
     * КВАДРАТ ЯЧЕЙКИ КОНТЕЙНЕРА, ПОВЁРНУТЫЙ ПО СВОЕЙ СТОРОНЕ ГЕКСА. Именно
     * поворот и отличает метку ячейки от «ромбика рядом с гексом»: на печатном
     * поле ячейка лежит вдоль своей стороны, а не по осям листа.
     *
     * @param cell 0..5 — ячейка на стороне, 6 — воздушная (в центре, без поворота)
     * @param s    сторона квадрата
     */
    public static double[][] containerCellQuad(double cx, double cy, double size,
                                               int cell, double s) {
        double x = cx;
        double y = cy;
        double ang = 0;
        if (cell != 6) {
            ang = Math.toRadians(edgeAngle(cell));
            x = cx + size * 0.62 * Math.cos(ang);
            y = cy + size * 0.62 * Math.sin(ang);
        }
        // оси ячейки: u — наружу по нормали стороны, v — вдоль стороны
        double ux = Math.cos(ang);
        double uy = Math.sin(ang);
        double vx = -uy;
        double vy = ux;
        double h = s / 2;
        return new double[][]{
            {x + (-h) * ux + (-h) * vx, y + (-h) * uy + (-h) * vy},
            {x + (h) * ux + (-h) * vx, y + (h) * uy + (-h) * vy},
            {x + (h) * ux + (h) * vx, y + (h) * uy + (h) * vy},
            {x + (-h) * ux + (h) * vx, y + (-h) * uy + (h) * vy}
        };
    }

    /**
     * КАКИЕ СТОРОНЫ СХОДЯТСЯ В УГЛУ {@code k} — пара номеров сторон (0..5).
     *
     * <p>Выведено из того, что угол {@code k} лежит под {@code 60k - 90 + TILT},
     * а сторона {@code s} — под {@code -60s + TILT}: в угол смотрят ровно те две
     * стороны, чьи направления отстоят от него на ±30°.
     */
    public static int[] sidesAtCorner(int k) {
        return new int[]{Math.floorMod(2 - k, 6), Math.floorMod(1 - k, 6)};
    }

    /**
     * ГЕКС, СКРУГЛЁННЫЙ ТОЛЬКО ПО ВНЕШНЕМУ КОНТУРУ ПОЛЯ.
     *
     * <p>ЗАЧЕМ ИМЕННО ТАК (правка дизайнера 19.08.2026). Скругление КАЖДОГО угла
     * каждого гекса даёт дырки: два соседних гекса сходятся стыком, и если оба
     * срезали общий угол, между ними появляется просвет, которого на картоне
     * нет. Поэтому угол скругляется ТОЛЬКО когда он и правда внешний — то есть
     * когда ни с одной из двух сходящихся в нём сторон соседа нет. Стыки внутри
     * поля остаются острыми и сходятся вплотную, а мягким становится ровно
     * контур поля целиком.
     *
     * @param neighbor {@code neighbor[s] == true}, если с стороны {@code s} есть
     *                 соседний гекс поля; длина 6
     */
    public static Path2D.Double outlineRoundedHexPath(double cx, double cy, double r,
                                                      double round, boolean[] neighbor) {
        double[][] p = hexCorners(cx, cy, r);
        Path2D.Double path = new Path2D.Double();
        double k = Math.min(0.5, Math.max(0, round));
        boolean started = false;
        for (int i = 0; i < 6; i++) {
            int[] sides = sidesAtCorner(i);
            boolean outer = k > 0 && neighbor != null
                && !neighbor[sides[0]] && !neighbor[sides[1]];
            double[] cur = p[i];
            if (!outer) {
                if (!started) {
                    path.moveTo(cur[0], cur[1]);
                    started = true;
                } else {
                    path.lineTo(cur[0], cur[1]);
                }
                continue;
            }
            double[] prev = p[(i + 5) % 6];
            double[] next = p[(i + 1) % 6];
            double ax = cur[0] + (prev[0] - cur[0]) * k;
            double ay = cur[1] + (prev[1] - cur[1]) * k;
            double bx = cur[0] + (next[0] - cur[0]) * k;
            double by = cur[1] + (next[1] - cur[1]) * k;
            if (!started) {
                path.moveTo(ax, ay);
                started = true;
            } else {
                path.lineTo(ax, ay);
            }
            path.quadTo(cur[0], cur[1], bx, by);
        }
        path.closePath();
        return path;
    }

    /** То же точками — для {@link FieldCanvas#polygon}, который кривых не умеет. */
    public static double[][] outlineRoundedHexPoints(double cx, double cy, double r,
                                                     double round, boolean[] neighbor,
                                                     int seg) {
        double[][] p = hexCorners(cx, cy, r);
        double k = Math.min(0.5, Math.max(0, round));
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int[] sides = sidesAtCorner(i);
            boolean outer = k > 0 && neighbor != null
                && !neighbor[sides[0]] && !neighbor[sides[1]];
            double[] cur = p[i];
            if (!outer) {
                out.add(new double[]{cur[0], cur[1]});
                continue;
            }
            double[] prev = p[(i + 5) % 6];
            double[] next = p[(i + 1) % 6];
            double ax = cur[0] + (prev[0] - cur[0]) * k;
            double ay = cur[1] + (prev[1] - cur[1]) * k;
            double bx = cur[0] + (next[0] - cur[0]) * k;
            double by = cur[1] + (next[1] - cur[1]) * k;
            out.add(new double[]{ax, ay});
            for (int s = 1; s < Math.max(1, seg); s++) {
                double t = (double) s / seg;
                double u = 1 - t;
                out.add(new double[]{
                    u * u * ax + 2 * u * t * cur[0] + t * t * bx,
                    u * u * ay + 2 * u * t * cur[1] + t * t * by
                });
            }
            out.add(new double[]{bx, by});
        }
        return out.toArray(new double[0][]);
    }

    /**
     * СКРУГЛЁННЫЙ ГЕКС ТОЧКАМИ — тот же контур, что даёт {@link #roundedHexPath},
     * но выложенный многоугольником.
     *
     * <p>ЗАЧЕМ ВТОРАЯ ФОРМА ТОЙ ЖЕ ФИГУРЫ. Рисовальщик картинок работает через
     * {@link FieldCanvas#polygon}, который принимает только точки: кривую в него
     * не передать, а завести ради скругления новый метод пришлось бы сразу в двух
     * его воплощениях — и в Swing, и в SVG. Дуги считаются по ТЕМ ЖЕ квадратичным
     * кривым, что и в {@link #roundedHexPath}, поэтому обе формы совпадают на
     * глаз, и вид не разъезжается между экраном и выгрузкой.
     *
     * @param round доля ребра под скругление; 0 — обычные шесть точек
     * @param seg   на сколько отрезков дробится дуга каждого угла
     */
    public static double[][] roundedHexPoints(double cx, double cy, double r,
                                              double round, int seg) {
        double[][] p = hexCorners(cx, cy, r);
        if (round <= 0 || seg < 1) {
            return p;
        }
        double k = Math.min(0.5, round);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double[] prev = p[(i + 5) % 6];
            double[] cur = p[i];
            double[] next = p[(i + 1) % 6];
            double ax = cur[0] + (prev[0] - cur[0]) * k;
            double ay = cur[1] + (prev[1] - cur[1]) * k;
            double bx = cur[0] + (next[0] - cur[0]) * k;
            double by = cur[1] + (next[1] - cur[1]) * k;
            out.add(new double[]{ax, ay});
            for (int s = 1; s < seg; s++) {
                double t = (double) s / seg;
                double u = 1 - t;
                // квадратичная кривая с опорой в самом углу — та же, что в path
                out.add(new double[]{
                    u * u * ax + 2 * u * t * cur[0] + t * t * bx,
                    u * u * ay + 2 * u * t * cur[1] + t * t * by
                });
            }
            out.add(new double[]{bx, by});
        }
        return out.toArray(new double[0][]);
    }

    /** Скруглённый гекс точками с настройками по умолчанию ({@link #TILE_ROUND}). */
    public static double[][] roundedHexPoints(double cx, double cy, double r) {
        return roundedHexPoints(cx, cy, r, TILE_ROUND, 4);
    }

    /** Собрать замкнутый путь по точкам — для рисовальщиков на Graphics2D. */
    public static Path2D.Double path(double[][] pts) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(pts[0][0], pts[0][1]);
        for (int i = 1; i < pts.length; i++) {
            p.lineTo(pts[i][0], pts[i][1]);
        }
        p.closePath();
        return p;
    }

    /**
     * СКРУГЛЁННЫЙ ГЕКС — тот же шестиугольник, что даёт {@link #hexCorners}, но с
     * закруглёнными углами. Живёт здесь, рядом с остальной геометрией поля,
     * потому что нужен сразу трём рисовальщикам: конструктору раскладок, разбору
     * партии и выгрузке картинок. Заводить его в каждом значило бы получить три
     * разных скругления и разъезжающийся вид — ровно та беда, из-за которой
     * геометрия и была собрана в один класс.
     *
     * @param r     радиус гекса (как у {@link #hexCorners})
     * @param round доля ребра под скругление; 0 — обычный острый шестиугольник
     */
    public static Path2D.Double roundedHexPath(double cx, double cy, double r, double round) {
        double[][] p = hexCorners(cx, cy, r);
        Path2D.Double path = new Path2D.Double();
        if (round <= 0) {
            path.moveTo(p[0][0], p[0][1]);
            for (int i = 1; i < 6; i++) {
                path.lineTo(p[i][0], p[i][1]);
            }
            path.closePath();
            return path;
        }
        double k = Math.min(0.5, round);
        for (int i = 0; i < 6; i++) {
            double[] prev = p[(i + 5) % 6];
            double[] cur = p[i];
            double[] next = p[(i + 1) % 6];
            // Точки отхода от угла по обоим рёбрам; сам угол становится опорной
            // точкой квадратичной кривой — так скругление повторяет фаску, а не
            // срезает угол прямой.
            double ax = cur[0] + (prev[0] - cur[0]) * k;
            double ay = cur[1] + (prev[1] - cur[1]) * k;
            double bx = cur[0] + (next[0] - cur[0]) * k;
            double by = cur[1] + (next[1] - cur[1]) * k;
            if (i == 0) {
                path.moveTo(ax, ay);
            } else {
                path.lineTo(ax, ay);
            }
            path.quadTo(cur[0], cur[1], bx, by);
        }
        path.closePath();
        return path;
    }

    /**
     * Полигон-клин гекса, покрывающий набор смежных сторон (реальная «часть
     * гекса», которую ЖЁСТКО занимает здание): ЦЕНТР + внешние вершины рёбер,
     * упорядоченные вокруг среднего угла. Первая точка — центр гекса.
     */
    public static List<double[]> sectorPolygon(double cx, double cy, double size,
                                               List<Integer> sides) {
        double mid = meanEdgeAngle(sides);
        java.util.TreeMap<Double, double[]> byAngle = new java.util.TreeMap<>();
        for (int s : sides) {
            for (double delta : new double[]{-30, 30}) {
                double a = edgeAngle(s) + delta;
                byAngle.put(norm180(a - mid), polar(cx, cy, size, a));
            }
        }
        List<double[]> out = new ArrayList<>();
        out.add(new double[]{cx, cy});
        out.addAll(byAngle.values());
        return out;
    }

    // ===================== посадка жетонов ==================================

    /**
     * Матрица посадки ЗДАНИЯ на его секторы: масштаб такой, чтобы сторона формы
     * совпала со стороной гекса, а «центр гекса» формы — с центром гекса поля.
     *
     * @param face средний угол занятых сторон ({@link #meanEdgeAngle})
     */
    public static AffineTransform seatTransform(Shape sh, double cx, double cy,
                                                double size, double face) {
        double k = size / sh.edgeUnit() * BUILDING_SHRINK;
        AffineTransform t = new AffineTransform();
        t.translate(cx, cy);
        t.rotate(Math.toRadians(face - sh.outward()));
        t.scale(k, k);
        t.translate(-sh.hexCx(), -sh.hexCy());
        return t;
    }

    /** Коэффициент масштаба посадки здания (для толщины обводки). */
    public static double seatScale(Shape sh, double size) {
        return size / sh.edgeUnit() * BUILDING_SHRINK;
    }

    /**
     * Матрица «форма шириной targetW в точке (cx,cy) с поворотом rotDeg»
     * — для войск и любых жетонов без привязки к сектору.
     */
    public static AffineTransform widthTransform(Shape sh, double cx, double cy,
                                                 double rotDeg, double targetW) {
        double k = targetW / sh.vbW();
        AffineTransform t = new AffineTransform();
        t.translate(cx, cy);
        t.rotate(Math.toRadians(rotDeg));
        t.scale(k, k);
        t.translate(-sh.vbW() / 2, -sh.vbH() / 2);
        return t;
    }

    /**
     * Ширина жетона войска, посаженного в пояс у стенки: одиночный жетон
     * (пехота/вышка) уже, техника на двух смежных ячейках — шире.
     */
    public static double unitWidth(int span, double size) {
        return size * (span >= 2 ? 0.50 : 0.36);
    }

    /**
     * Ширина жетона войска С УЧЁТОМ РОДА. Общая мера по числу занятых сторон
     * оказалась мелковата для вышки и техники: на поле они читались хуже пехоты,
     * хотя на столе крупнее (замечание дизайнера 13.08.2026).
     */
    public static double unitWidth(String type, int span, double size) {
        double k = switch (type == null ? "" : type) {
            case "vehicle" -> 0.62;
            case "tower" -> 0.46;
            case "aircraft" -> 0.42;
            default -> span >= 2 ? 0.50 : 0.36;
        };
        return size * k;
    }

    /**
     * Радиус посадки жетона войска (центр силуэта от центра гекса). Войска, как и
     * здания, прижаты к кромке гекса: между соседними жетонами остаётся щель, а
     * поля по внешнему краю почти нет (просьба дизайнера 13.08.2026).
     */
    public static double unitSeatRadius(double size) {
        return size * 0.60;
    }

    /** Поворот жетона войска, стоящего у стенки под углом face. */
    public static double unitRotation(Shape sh, double face) {
        return face + (sh.baseUp() ? 90 : -90);
    }

    // ===================== разбор пути SVG ==================================
    private static final Map<String, Path2D.Double> PATH_CACHE = new ConcurrentHashMap<>();

    /** Путь SVG (подмножество M/L/H/V/C/S/Q/T/Z) как {@link Path2D}; результат кэшируется. */
    public static Path2D.Double path(String d) {
        Path2D.Double cached = PATH_CACHE.get(d);
        if (cached != null) {
            return (Path2D.Double) cached.clone();
        }
        Path2D.Double p = parsePath(d);
        PATH_CACHE.put(d, p);
        return (Path2D.Double) p.clone();
    }

    private static Path2D.Double parsePath(String d) {
        List<Object> tok = tokenize(d);
        Path2D.Double p = new Path2D.Double(Path2D.WIND_NON_ZERO);
        double cx = 0;
        double cy = 0;
        double sx = 0;
        double sy = 0;
        double lastCtrlX = 0;
        double lastCtrlY = 0;
        char cmd = 0;
        char prevCmd = 0;
        int i = 0;
        while (i < tok.size()) {
            if (tok.get(i) instanceof Character c) {
                cmd = c;
                i++;
                if (cmd == 'Z' || cmd == 'z') {
                    p.closePath();
                    cx = sx;
                    cy = sy;
                    prevCmd = cmd;
                    continue;
                }
            } else if (cmd == 'M') {
                cmd = 'L';           // повтор аргументов у M = L (правило SVG)
            } else if (cmd == 'm') {
                cmd = 'l';
            }
            boolean rel = Character.isLowerCase(cmd);
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> {
                    double x = num(tok, i++);
                    double y = num(tok, i++);
                    cx = rel ? cx + x : x;
                    cy = rel ? cy + y : y;
                    p.moveTo(cx, cy);
                    sx = cx;
                    sy = cy;
                }
                case 'L' -> {
                    double x = num(tok, i++);
                    double y = num(tok, i++);
                    cx = rel ? cx + x : x;
                    cy = rel ? cy + y : y;
                    p.lineTo(cx, cy);
                }
                case 'H' -> {
                    double x = num(tok, i++);
                    cx = rel ? cx + x : x;
                    p.lineTo(cx, cy);
                }
                case 'V' -> {
                    double y = num(tok, i++);
                    cy = rel ? cy + y : y;
                    p.lineTo(cx, cy);
                }
                case 'C' -> {
                    double x1 = num(tok, i++);
                    double y1 = num(tok, i++);
                    double x2 = num(tok, i++);
                    double y2 = num(tok, i++);
                    double x = num(tok, i++);
                    double y = num(tok, i++);
                    if (rel) {
                        x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy;
                    }
                    p.curveTo(x1, y1, x2, y2, x, y);
                    lastCtrlX = x2;
                    lastCtrlY = y2;
                    cx = x;
                    cy = y;
                }
                case 'S' -> {
                    double x2 = num(tok, i++);
                    double y2 = num(tok, i++);
                    double x = num(tok, i++);
                    double y = num(tok, i++);
                    if (rel) {
                        x2 += cx; y2 += cy; x += cx; y += cy;
                    }
                    boolean smooth = prevCmd == 'C' || prevCmd == 'c'
                        || prevCmd == 'S' || prevCmd == 's';
                    double x1 = smooth ? 2 * cx - lastCtrlX : cx;
                    double y1 = smooth ? 2 * cy - lastCtrlY : cy;
                    p.curveTo(x1, y1, x2, y2, x, y);
                    lastCtrlX = x2;
                    lastCtrlY = y2;
                    cx = x;
                    cy = y;
                }
                case 'Q' -> {
                    double x1 = num(tok, i++);
                    double y1 = num(tok, i++);
                    double x = num(tok, i++);
                    double y = num(tok, i++);
                    if (rel) {
                        x1 += cx; y1 += cy; x += cx; y += cy;
                    }
                    p.quadTo(x1, y1, x, y);
                    lastCtrlX = x1;
                    lastCtrlY = y1;
                    cx = x;
                    cy = y;
                }
                case 'T' -> {
                    double x = num(tok, i++);
                    double y = num(tok, i++);
                    if (rel) {
                        x += cx; y += cy;
                    }
                    boolean smooth = prevCmd == 'Q' || prevCmd == 'q'
                        || prevCmd == 'T' || prevCmd == 't';
                    double x1 = smooth ? 2 * cx - lastCtrlX : cx;
                    double y1 = smooth ? 2 * cy - lastCtrlY : cy;
                    p.quadTo(x1, y1, x, y);
                    lastCtrlX = x1;
                    lastCtrlY = y1;
                    cx = x;
                    cy = y;
                }
                default -> throw new IllegalArgumentException(
                    "неподдерживаемая команда пути SVG: " + cmd);
            }
            prevCmd = cmd;
        }
        return p;
    }

    private static double num(List<Object> tok, int i) {
        Object o = i < tok.size() ? tok.get(i) : null;
        if (o instanceof Double dd) {
            return dd;
        }
        throw new IllegalArgumentException("в пути SVG ожидалось число на позиции " + i);
    }

    private static List<Object> tokenize(String d) {
        List<Object> out = new ArrayList<>();
        int i = 0;
        int n = d.length();
        while (i < n) {
            char c = d.charAt(i);
            if (Character.isLetter(c)) {
                out.add(c);
                i++;
            } else if (c == ',' || Character.isWhitespace(c)) {
                i++;
            } else {
                int j = i;
                if (d.charAt(j) == '+' || d.charAt(j) == '-') {
                    j++;
                }
                while (j < n && (Character.isDigit(d.charAt(j)) || d.charAt(j) == '.')) {
                    j++;
                }
                if (j < n && (d.charAt(j) == 'e' || d.charAt(j) == 'E')) {
                    j++;
                    if (j < n && (d.charAt(j) == '+' || d.charAt(j) == '-')) {
                        j++;
                    }
                    while (j < n && Character.isDigit(d.charAt(j))) {
                        j++;
                    }
                }
                if (j == i) {
                    throw new IllegalArgumentException(
                        "непонятный символ в пути SVG: '" + c + "' на позиции " + i);
                }
                out.add(Double.parseDouble(d.substring(i, j)));
                i = j;
            }
        }
        return out;
    }
}
