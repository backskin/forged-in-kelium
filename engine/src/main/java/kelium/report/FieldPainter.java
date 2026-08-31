package kelium.report;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kelium.engine.Placement;

/**
 * FieldPainter — ЕДИНСТВЕННЫЙ код отрисовки поля. И картинка в отчёте, и вид в
 * проигрывателе рисуются этим классом, отличается только поверхность
 * ({@link FieldCanvas}).
 *
 * <p>Так закрывается главная беда прежней сборки: отрисовка была написана
 * дважды, и правка в одном месте расходилась с другим (у воздушной ячейки
 * порядок слоёв разъехался ровно так). Порядок слоёв описан в
 * {@link FieldGeometry#LAYER_ORDER} и живёт здесь в одном экземпляре.
 *
 * <p>Рисует из {@link ReplayRecord.Snapshot} — той же структуры, которой
 * пользуется запись партии. Живое состояние движка превращается в неё через
 * {@link ReplayRecord#snapshotOf}.
 */
public final class FieldPainter {

    private FieldPainter() {
    }

    // ==================== цвета и доли (одни на оба рендера) ====================

    /**
     * ТЁМНЫЙ ЛИСТ. Поле нарисовано для белой бумаги, и на тёмной теме оно
     * оставалось белым пятном — приходилось смотреть на лист бумаги в тёмной
     * комнате (замечание дизайнера 13.08.2026).
     *
     * <p>Правило простое и на нём всё держится: ГЕКСЫ ТЕМНЕЮТ (но остаются
     * разноцветными и не сливаются с фоном окна), а ВСЁ, ЧТО НА НИХ ЛЕЖИТ —
     * жетоны игроков, тайлы зарождения, нейтральные постройки — наоборот, слегка
     * СВЕТЛЕЕТ. Тогда картинка читается тем же способом, что и на белом: жетон
     * светлее своего гекса.
     *
     * <p>Отчёты в SVG печатаются на белом всегда — там этот выключатель не
     * трогают.
     */
    public static boolean dark;

    private static final String HEX_FILL = "#ffffff";
    private static final String HEX_FILL_DARK = "#2B2F35";
    private static final String HEX_STROKE = "#7d7a70";
    private static final String HEX_STROKE_DARK = "#565C66";
    private static final String FORBIDDEN_FILL = "#4a4844";
    private static final String FORBIDDEN_FILL_DARK = "#191B1F";

    /** Заливка обычного гекса под текущую тему. */
    static String hexFill() {
        return dark ? HEX_FILL_DARK : HEX_FILL;
    }

    /** Обводка гекса под текущую тему. */
    static String hexStroke() {
        return dark ? HEX_STROKE_DARK : HEX_STROKE;
    }

    /**
     * Цвет ЖЕТОНА (или тайла, или нейтрала) под текущую тему: на тёмном листе
     * он светлеет, чтобы остаться светлее своего гекса.
     */
    static String tone(String colour) {
        return dark ? shade(colour, 1.22) : colour;
    }
    private static final String SPAWN_NORMAL = "#2E7D32";
    private static final String SPAWN_START = "#A5D6A7";
    private static final String SPAWN_EDGE = "#1B5E20";
    private static final String SPAWN_UNDER_NORMAL = "#1f5c22";
    private static final String SPAWN_UNDER_START = "#7bb97f";
    private static final String SPAWN_UNDER_EDGE = "#17431a";
    // ВЫКЛЮЧАТЕЛИ ВТОРОСТЕПЕННЫХ ЭЛЕМЕНТОВ (меню проигрывателя «Что показывать
    // на поле»). Это только цифры и пометки ПОВЕРХ жетонов: сами жетоны, тайлы,
    // контейнеры и нейтралы не выключаются никогда — без них картинка врёт.
    // Статические, потому что рисовальщик без состояния и общий для SVG и Swing.
    public static boolean showDamage = true;
    public static boolean showKelium = true;
    public static boolean showEnergy = true;
    public static boolean showOwnership = true;

    // ЭНЕРГИЯ НА ЖЕТОНЕ: ЯЧЕЙКА — чёрный квадрат, кубик — жёлтый и заметно
    // меньше, чтобы было видно, что он ЛЕЖИТ В ячейке, а не рядом с ней.
    /**
     * Сдвиг жетона НАРУЖУ, к кромке гекса, в долях радиуса. Половина зазора,
     * который даёт усадка силуэта: (1 − 0,88) · апофема / 2 ≈ 0,052.
     */
    private static final double EDGE_SHIFT = 0.052;

    /**
     * ТОЛЩИНА ОБВОДКИ ЖЕТОНОВ ИГРОКА — одна на здания и войска (было 2,6 и 2,4).
     * Обводка тут не украшение: цветом её кромки жетон и приписан к игроку, и на
     * плотном поле её надо видеть с первого взгляда (просьба дизайнера 13.08.2026).
     */
    private static final double TOKEN_STROKE = 3.2;
    // Кубик энергии — главный «живой» значок на жетоне, и он читался мелковато
    // (замечание дизайнера 13.08.2026): ячейка чуть крупнее, кубик в ней заметно.
    private static final double ENERGY_SLOT = 0.158;        // сторона ячейки в долях гекса
    private static final double ENERGY_CUBE_IN_SLOT = 0.84; // кубик от стороны ячейки
    private static final String SLOT_FILL = "#26241F";
    private static final String SLOT_EDGE = "#0F0E0C";
    /** Зона хранения кубиков на источнике — жёлтая полосатая площадка. */
    // ЗОНА СВОБОДНОЙ ЭНЕРГИИ. Была бледно-жёлтой — и жёлтые кубики на ней почти
    // не читались, а пустая зона вообще терялась на цветном жетоне. Теперь она
    // тёмно-синяя: и от жетона отличается, и жёлтый кубик на ней виден издалека
    // (просьба дизайнера 13.08.2026).
    private static final String STORE_FILL = "#C6B053";
    private static final String STORE_EDGE = "#7A6A24";
    private static final String STORE_STRIPE = "#E2D289";

    /**
     * ШТРИХОВКА ОБОРОТА ТАЙЛА — В ЦВЕТ ГЕКСА. Тайл, выработанный наполовину,
     * перевёрнут, и сквозь него «просвечивает» поле: поэтому полосы всегда того
     * же цвета, что и гексы, и в тёмной теме темнеют вместе с ними (правило
     * дизайнера 13.08.2026). Раньше они были жёлтыми при любой теме.
     */
    private static String spawnHatch() {
        return hexFill();
    }
    private static final String SPAWN_FLIPPED_EDGE_LIGHT = "#E8862A";
    private static final String SPAWN_FLIPPED_EDGE = SPAWN_FLIPPED_EDGE_LIGHT;
    /** Кубик КЕЛЕМИЯ — классический зелёный, тот же, что на планшете рынка. */
    private static final String KELIUM_CUBE = "#2e9e44";
    private static final String KELIUM_CUBE_EDGE = "#14431f";
    private static final String SPAWN_TEXT_DARK = "#14401a";
    private static final String SPAWN_TEXT_LIGHT = "#eaffea";
    private static final String NEUTRAL_SMALL = "#9AA0A6";
    private static final String NEUTRAL_BIG = "#7C838B";
    private static final String NEUTRAL_EDGE = "#33383E";
    // Печатный контейнер: приглушённее и светлее прежнего (#E8C77B/#6E4E13) —
    // он подложка под жетоны и не должен спорить с ними за внимание
    // (просьба дизайнера 12.08.2026). Знак вопроса — цвет обводки.
    /**
     * ЖЁЛТАЯ ЯЧЕЙКА — та, где энергостанция даёт номинал (правило 16.08.2026).
     * На тёмной теме цвет приглушённый: чистая жёлтая линия на тёмном поле
     * светится и перетягивает взгляд с жетонов, а разметка картона должна
     * лежать ПОД ними и по яркости тоже.
     */
    private static final String ENERGY_CELL_MARK = "#E8B71E";
    private static final String ENERGY_CELL_MARK_DARK = "#8A7020";
    private static final String CONTAINER_FILL = "#F0E2BE";
    private static final String CONTAINER_EDGE = "#9A8455";
    private static final String CONTAINER_MARK = "#7A6636";
    private static final String DAMAGE_FILL = "#d32f2f";
    /** Тёмная контрастная обводка кубика урона (просьба дизайнера 17.08.2026):
     * тонкая белая, как раньше, терялась на светлом поле — кубик легко спутать
     * с чем угодно красным на жетоне. */
    private static final String DAMAGE_EDGE = "#3a0000";
    /** ГНЁЗДА ПРОЧНОСТИ (проект «гнёзда прочности», эксперт 26.08.2026): ряд
     * длиной hp пипсов в осях жетона — белый заполненный пипс «осталось»,
     * тёмное пустое гнездо «потеряно». Один язык вместо трёх красных пятен. */
    private static final double HP_PIP = 0.095;
    private static final double HP_STEP = 1.25;
    /** Мельче этого пипс не читается (≈3.2 px) — остаётся красная обводка. */
    private static final double HP_MIN = 34;
    /** Сердечки прочности нейтральных построек. */
    private static final String HEART_FILL = "#E03A3A";
    private static final String HEART_EDGE = "#7A1414";
    private static final String ENERGY_ON = "#ffc400";
    private static final String ENERGY_OFF = "#ffffff";
    private static final String ENERGY_EDGE = "#a07800";
    private static final String HEX_ID_COLOR = "#a8a49a";
    private static final String WHITE = "#ffffff";
    private static final String LABEL_OUTLINE = "#00000099";

    private static final double SPAWN_R = 0.92;
    private static final double NEUTRAL_OUTER = 0.86;
    private static final double NEUTRAL_INNER = 0.50;
    private static final double CONTAINER_DY = 0.42;

    /** Нарисовать ВСЁ поле снимка. Начало координат — {@code (ox, oy)}. */
    public static void paintField(FieldCanvas c, double size, List<ReplayRecord.HexInfo> hexes,
                                  ReplayRecord.Snapshot snap, double ox, double oy,
                                  boolean showIds) {
        Map<String, ReplayRecord.HexState> states = new LinkedHashMap<>();
        for (ReplayRecord.HexState h : snap.hexes) {
            states.put(h.id, h);
        }
        Map<String, List<ReplayRecord.Tok>> byHex = new LinkedHashMap<>();
        for (ReplayRecord.Tok t : snap.tokens) {
            if (t.hexId != null && t.alive) {
                byHex.computeIfAbsent(t.hexId, k -> new ArrayList<>()).add(t);
            }
        }
        // КТО С КЕМ СОСЕДИТ — нужно для скругления ТОЛЬКО внешнего контура поля
        // (правка дизайнера 19.08.2026). Считается один раз на всё поле, а не в
        // каждом гексе: иначе на каждую клетку шёл бы проход по всему списку.
        java.util.Set<Long> занято = new java.util.HashSet<>();
        for (ReplayRecord.HexInfo hi : hexes) {
            занято.add((((long) hi.q) << 32) ^ (hi.r & 0xffffffffL));
        }
        for (ReplayRecord.HexInfo hi : hexes) {
            double[] centre = FieldGeometry.hexCenter(hi.q, hi.r, size);
            boolean[] nb = new boolean[6];
            for (int s = 0; s < 6; s++) {
                int[] d = kelium.core.Field.AXIAL_DIRS[s];
                int nq = hi.q + d[0];
                int nr = hi.r + d[1];
                nb[s] = занято.contains((((long) nq) << 32) ^ (nr & 0xffffffffL));
            }
            paintHex(c, size, hi, states.get(hi.id),
                byHex.getOrDefault(hi.id, List.of()),
                centre[0] + ox, centre[1] + oy, showIds, nb);
        }
    }

    /**
     * Нарисовать содержимое ОДНОГО гекса. Порядок слоёв — см.
     * {@link FieldGeometry#LAYER_ORDER}; менять его можно только здесь.
     */
    public static void paintHex(FieldCanvas c, double size, ReplayRecord.HexInfo hi,
                                ReplayRecord.HexState st, List<ReplayRecord.Tok> tokens,
                                double cx, double cy, boolean showIds) {
        // БЕЗ СВЕДЕНИЙ О СОСЕДЯХ — БЕЗ СКРУГЛЕНИЙ. Скруглять «на всякий случай»
        // здесь нельзя: у гекса в середине поля срезанный угол даёт дырку на
        // стыке с соседом (правка дизайнера 19.08.2026).
        paintHex(c, size, hi, st, tokens, cx, cy, showIds, null);
    }

    /**
     * То же, но со знанием СОСЕДЕЙ — тогда внешний контур поля скругляется, а
     * стыки внутри остаются острыми и сходятся вплотную.
     *
     * @param neighbor {@code neighbor[s]} — есть ли гекс с стороны {@code s};
     *                 null — рисовать острым шестиугольником
     */
    public static void paintHex(FieldCanvas c, double size, ReplayRecord.HexInfo hi,
                                ReplayRecord.HexState st, List<ReplayRecord.Tok> tokens,
                                double cx, double cy, boolean showIds,
                                boolean[] neighbor) {
        boolean forbidden = "FORBIDDEN".equals(hi.kind);

        // 1. сам гекс + слабая подкраска «чей он»
        java.awt.image.BufferedImage hexTex = Textures.field(
            forbidden ? "hex_forbidden" : "hex");
        if (hexTex != null) {
            drawHexTexture(c, hexTex, cx, cy, size, 0);
        } else {
            // СКРУГЛЯЕТСЯ ТОЛЬКО ВНЕШНИЙ КОНТУР ПОЛЯ (правка дизайнера
            // 19.08.2026): у гекса внутри поля срезанный угол давал дырку на
            // стыке с соседом.
            c.polygon(FieldGeometry.outlineRoundedHexPoints(cx, cy, size,
                    FieldGeometry.TILE_ROUND, neighbor, 4),
                forbidden ? (dark ? FORBIDDEN_FILL_DARK : FORBIDDEN_FILL) : hexFill(),
                hexStroke(), 1.5);
        }
        if (showOwnership && !forbidden && st != null && st.ownerTint >= 0) {
            // Гекс со своим зданием — заметнее, гекс зоны стройки — еле-еле.
            c.alpha(st.ownerBuilt ? 0.30 : 0.13);
            c.polygon(FieldGeometry.outlineRoundedHexPoints(cx, cy, size,
                    FieldGeometry.TILE_ROUND, neighbor, 4),
                FieldGeometry.SEAT_FILL[FieldGeometry.seatColor(st.ownerTint)], null, 0);
            c.alpha(1);
        }

        // 2. ПЕЧАТНЫЙ КОНТЕЙНЕР — он НАРИСОВАН НА САМОМ КАРТОНЕ, поэтому идёт
        //    сразу за гексом и лежит ПОД ВСЕМ, что на гекс кладут: под тайлом
        //    зарождения, под нейтральной постройкой, под зданиями и войсками.
        //    (Сначала он рисовался последним и просвечивал сквозь жетоны, потом
        //    попал между нейтралом и зданиями — и лёг поверх нейтрала.)
        if (st != null && st.containerCell >= 0) {
            paintPrintedContainer(c, size, st.containerCell, cx, cy);
        }

        // 2б. ЖЁЛТАЯ ЯЧЕЙКА — тоже печать на картоне, тот же слой: лежит под
        //     всем, что на гекс кладут. Только стоя на ней, энергостанция даёт
        //     свой номинал.
        if (st != null && st.energyCell >= 0 && st.energyCell < 6) {
            paintEnergyCell(c, size, st.energyCell, cx, cy);
        }

        // 3. тайл зарождения — закрывает гекс целиком
        boolean hasSpawn = st != null && st.spawn != null;
        if (hasSpawn) {
            paintSpawn(c, size, st.spawn, cx, cy);
        }

        // 4. нейтральные постройки на рёбрах
        if (st != null) {
            for (ReplayRecord.Neutral nb : st.neutrals) {
                paintNeutral(c, size, nb, cx, cy);
            }
        }

        // 5. подпись гекса
        if (showIds) {
            c.text(hi.id, cx, cy - size * 0.72, size * 0.15, false, HEX_ID_COLOR);
        }

        // 6. ЗДАНИЯ: силуэт + подпись, ячейки и кубики энергии
        Set<Integer> taken = new HashSet<>();
        Map<String, double[]> hideSpots = new LinkedHashMap<>();
        for (ReplayRecord.Tok b : tokens) {
            if (!b.building) {
                continue;
            }
            List<Integer> sides = new ArrayList<>();
            if (st != null) {
                for (int i = 0; i < 6; i++) {
                    if (st.sideOwner[i] == b.uid) {
                        sides.add(i);
                        taken.add(i);
                    }
                }
            }
            if (sides.isEmpty()) {
                int fp = kelium.engine.Placement.footprint(
                    kelium.core.BuildingType.fromCode(b.type));
                for (int i = 0; i < fp; i++) {
                    sides.add(i);
                }
            }
            double[] spot = paintBuilding(c, size, b, sides, cx, cy);
            // Место для значка «войско внутри» помечается по UID ЗДАНИЯ: войско
            // теперь ЯВНО указывает, в каком здании оно стоит. Раньше ключом был
            // «владелец:тип здания», и проигрыватель показывал спрятанными войска,
            // которые просто стояли на гексе своего здания подходящего рода.
            hideSpots.put(String.valueOf(b.uid), spot);
        }

        // 7. ВОЗДУШНАЯ ЯЧЕЙКА — ПОВЕРХ зданий: она принадлежит гексу целиком,
        // а не сектору, и должна быть видна даже там, где здание заняло середину.
        if (!forbidden && !hasSpawn) {
            c.polygon(FieldGeometry.hexCorners(cx, cy, size * FieldGeometry.AIR_CELL_R),
                "none", FieldGeometry.AIR_CELL_STROKE, FieldGeometry.AIR_CELL_WIDTH);
        }

        // 8. войска — поверх всего, включая печатный контейнер
        paintUnits(c, size, tokens, st, taken, hideSpots, cx, cy);

        if (forbidden) {
            c.text("X", cx, cy + size * 0.08, size * 0.25, false, "#eeeeee");
        }
    }

    // ==================== слои ====================
    private static void paintSpawn(FieldCanvas c, double size, ReplayRecord.Spawn sp,
                                   double cx, double cy) {
        boolean start = sp.start;
        // ДВОЙНОЙ ТАЙЛ — это ДВЕ картонки одна на другой, а не особый жетон: рисуем
        // тот же тайл со сдвигом, отдельной текстуры под него не нужно
        // (уточнение дизайнера 13.08.2026).
        java.awt.image.BufferedImage tex = spawnTexture(sp);
        if (sp.stack > 1) {
            if (tex != null) {
                drawHexTexture(c, tex, cx + size * 0.07, cy + size * 0.07,
                    size * SPAWN_R, 0);
            } else {
                c.polygon(FieldGeometry.roundedHexPoints(cx + size * 0.07,
                        cy + size * 0.07, size * SPAWN_R),
                    tone(start ? SPAWN_UNDER_START : SPAWN_UNDER_NORMAL), SPAWN_UNDER_EDGE, 1.4);
            }
        }
        if (tex != null) {
            drawHexTexture(c, tex, cx, cy, size * SPAWN_R, 0);
            paintSpawnText(c, size, sp, cx, cy);
            return;                     // штриховку и заливку заменяет сама картинка
        }
        // ТАЙЛ ЗАРОЖДЕНИЯ — СО СКРУГЛЁННЫМИ УГЛАМИ (просьба дизайнера
        // 20.08.2026). Это отдельная картонка, лежащая на поле, а не клетка
        // сетки: скругляется целиком и ни с чем не стыкуется, поэтому дырок,
        // из-за которых сетку скругляют лишь по контуру, здесь не бывает.
        c.polygon(FieldGeometry.roundedHexPoints(cx, cy, size * SPAWN_R),
            tone(start ? SPAWN_START : SPAWN_NORMAL), SPAWN_EDGE, 2.0);
        if (sp.flipped) {
            // ВЫРАБОТАН НАПОЛОВИНУ: тайл перевёрнут на оборот. Помечаем его
            // жёлтой диагональной штриховкой и оранжевой обводкой, чтобы
            // «доедаемые» жилы читались с одного взгляда (просьба дизайнера).
            paintFlippedHatch(c, size, cx, cy);
        }
        paintSpawnText(c, size, sp, cx, cy);
    }

    /** Буква тайла, остаток келемия и САМИ КУБИКИ — поверх любой картинки. */
    private static void paintSpawnText(FieldCanvas c, double size, ReplayRecord.Spawn sp,
                                       double cx, double cy) {
        boolean start = sp.start;
        c.text(start ? "S" : "K", cx, cy + size * 0.12, size * 0.46, true,
            start ? SPAWN_TEXT_DARK : WHITE);
        if (showKelium) {
            String note = Math.max(0, sp.kelium) + " кел"
                + (sp.stack > 1 ? " ×2" : "")
                + (sp.flipped ? " (об)" : "");
            c.text(note, cx, cy - size * 0.30, size * 0.19, true,
                start ? SPAWN_TEXT_DARK : SPAWN_TEXT_LIGHT);
            paintKeliumCubes(c, size, Math.max(0, sp.kelium), cx, cy);
        }
    }

    /**
     * КУБИКИ КЕЛЕМИЯ ПРЯМО НА ТАЙЛЕ — объёмные, как на планшете науки, и вдвое
     * крупнее кубиков энергии в ячейках зданий (просьба дизайнера 13.08.2026).
     * Цифра рядом остаётся: она отвечает на «сколько», кубики — на «много или
     * мало» с одного взгляда.
     *
     * <p>Кубиков рисуется столько, сколько осталось, но не больше восьми: дальше
     * они перестают помещаться на тайл, а число рядом и так всё говорит.
     */
    private static void paintKeliumCubes(FieldCanvas c, double size, int count,
                                         double cx, double cy) {
        if (count <= 0) {
            return;
        }
        int shown = Math.min(count, 8);
        double cube = size * ENERGY_SLOT * ENERGY_CUBE_IN_SLOT * 2;
        int perRow = shown <= 4 ? shown : (shown + 1) / 2;
        int rows = shown <= 4 ? 1 : 2;
        double step = cube * 1.16;
        double y0 = cy + size * 0.34 - (rows - 1) * step / 2;
        int left = shown;
        for (int r = 0; r < rows; r++) {
            int n = Math.min(perRow, left);
            left -= n;
            double x0 = cx - (n - 1) * step / 2;
            for (int i = 0; i < n; i++) {
                tokenCube(c, x0 + i * step, y0 + r * step, cube,
                    1, 0, 0, 1, KELIUM_CUBE, KELIUM_CUBE_EDGE);
            }
        }
    }

    /** Картинка тайла: от точного состояния к общему. */
    private static java.awt.image.BufferedImage spawnTexture(ReplayRecord.Spawn sp) {
        if (sp.start && sp.flipped) {
            return Textures.field("spawn_start_flipped", "spawn_start", "spawn");
        }
        if (sp.start) {
            return Textures.field("spawn_start", "spawn");
        }
        if (sp.flipped) {
            return Textures.field("spawn_flipped", "spawn");
        }
        return Textures.field("spawn");
    }

    /**
     * КАРТИНКА НА ГЕКСЕ. Полотно — рамка гекса «плашмя вверх»: ширина 2·r, высота
     * √3·r, привязка по центру. Тот же контракт у самого гекса, тайла зарождения и
     * любой другой картонки, лежащей гексом.
     */
    private static void drawHexTexture(FieldCanvas c, java.awt.image.BufferedImage tex,
                                       double cx, double cy, double r, double rotDeg) {
        double boxW = 2 * r;
        c.image(tex, cx, cy, rotDeg, boxW / tex.getWidth(),
            tex.getWidth() / 2.0, tex.getHeight() / 2.0);
    }

    /**
     * Пометка ВЫРАБОТАННОГО НАПОЛОВИНУ тайла зарождения: мелкая жёлтая
     * диагональная штриховка поверх жетона плюс оранжевая обводка.
     *
     * <p>Штрихи рисуются с запасом и обрезаются самим шестиугольником
     * ({@link FieldCanvas#clipTo}) — так они доходят ровно до кромки и никуда не
     * вылезают.
     */
    private static void paintFlippedHatch(FieldCanvas c, double size, double cx, double cy) {
        // ПОЛОСЫ ДО САМОГО КРАЯ, обрезанные по шестиугольнику. Раньше их длину
        // считали по хорде ВПИСАННОЙ окружности — у углов жетона штриховка не
        // доходила до края, и это бросалось в глаза (замечание дизайнера
        // 13.08.2026). Правильный приём один: рисуем с запасом и обрезаем формой.
        // ОБРЕЗКА ПО ТОЙ ЖЕ ФОРМЕ, что и сам тайл: у скруглённого тайла полосы,
        // обрезанные острым шестиугольником, вылезали бы за его углы.
        double[][] hex = FieldGeometry.roundedHexPoints(cx, cy, size * SPAWN_R);
        double r = size * SPAWN_R;
        double step = size * 0.20;              // шаг между полосами
        double half = size * 0.035;             // полуширина полосы
        double ang = Math.toRadians(-45);       // диагональ
        double dx = Math.cos(ang);
        double dy = Math.sin(ang);
        double nx = -dy;                        // перпендикуляр к полосе
        double ny = dx;
        c.clipTo(hex);
        for (double d = -r; d <= r; d += step) {
            double mx = cx + nx * d;
            double my = cy + ny * d;
            c.polygon(new double[][]{
                {mx - dx * r - nx * half, my - dy * r - ny * half},
                {mx + dx * r - nx * half, my + dy * r - ny * half},
                {mx + dx * r + nx * half, my + dy * r + ny * half},
                {mx - dx * r + nx * half, my - dy * r + ny * half}
            }, spawnHatch(), null, 0);
        }
        c.clipOff();
        c.polygon(hex, null, SPAWN_FLIPPED_EDGE, 3.0);
    }

    private static void paintNeutral(FieldCanvas c, double size, ReplayRecord.Neutral nb,
                                     double cx, double cy) {
        if (nb.corners.size() < 2) {
            return;
        }
        // ТЕКСТУРА СТЕНКИ: полотно — коробка самой стенки (длина по хорде между
        // крайними углами, толщина по глубине), поэтому одна картинка годится для
        // любой стороны гекса: её просто поворачивают вместе с постройкой.
        java.awt.image.BufferedImage tex = Textures.field(
            nb.big ? "neutral_big" : "neutral_small");
        if (tex != null) {
            // Рамка под картинку считается ПО САМОЙ ФОРМЕ постройки (трапеция или
            // две трапеции под углом), а не по прямоугольнику через хорду: иначе
            // текстура ложится на «палку» вместо настоящего силуэта.
            double[] box = FieldGeometry.neutralBox(cx, cy, size, nb.corners,
                NEUTRAL_OUTER, NEUTRAL_INNER);
            c.image(tex, box[0], box[1], box[4], box[2] / tex.getWidth(),
                tex.getWidth() / 2.0, tex.getHeight() / 2.0);
            paintNeutralHearts(c, size, nb, cx, cy);
            return;
        }
        c.polygon(FieldGeometry.neutralShape(cx, cy, size, nb.corners,
                NEUTRAL_OUTER, NEUTRAL_INNER),
            tone(nb.big ? NEUTRAL_BIG : NEUTRAL_SMALL), NEUTRAL_EDGE, 3.0);
        paintNeutralHearts(c, size, nb, cx, cy);
    }

    /**
     * ПРОЧНОСТЬ НЕЙТРАЛА СЕРДЕЧКАМИ (просьба дизайнера 13.08.2026): сколько ещё
     * бить — видно сразу, без наведения мыши. Целое сердце — оставшаяся прочность,
     * пустое — уже снятая.
     *
     * <p>Сердечки стоят вдоль СВОЕЙ стенки и поворачиваются вместе с ней — тем же
     * приёмом, что кубики энергии на зданиях.
     */
    private static void paintNeutralHearts(FieldCanvas c, double size,
                                           ReplayRecord.Neutral nb, double cx, double cy) {
        int max = Math.max(1, nb.hpMax);
        int left = Math.max(0, Math.min(max, nb.hp));
        // Стенка может быть ИЗОГНУТОЙ (большой нейтрал занимает две стороны), и
        // середина хорды у неё приходится на пустоту за изломом. Поэтому сердечки
        // раскладываются ПО ОТРЕЗКАМ стенки: по одному на сторону, а если сердец
        // больше, чем сторон — по несколько вдоль отрезка.
        int segments = Math.max(1, nb.corners.size() - 1);
        double r = size * 0.060;          // на пятую часть мельче прежнего
        int drawn = 0;
        for (int s = 0; s < segments && drawn < max; s++) {
            int here = countFor(max, segments, s);
            if (here == 0) {
                continue;
            }
            double a1 = Math.toRadians(60.0 * (nb.corners.get(s) - 1) - 90
                + FieldGeometry.TILT);
            double a2 = Math.toRadians(60.0 * (nb.corners.get(s + 1) - 1) - 90
                + FieldGeometry.TILT);
            double x1 = cx + size * NEUTRAL_OUTER * Math.cos(a1);
            double y1 = cy + size * NEUTRAL_OUTER * Math.sin(a1);
            double x2 = cx + size * NEUTRAL_OUTER * Math.cos(a2);
            double y2 = cy + size * NEUTRAL_OUTER * Math.sin(a2);
            double mx = (x1 + x2) / 2;
            double my = (y1 + y2) / 2;
            double len = Math.max(0.0001, Math.hypot(x2 - x1, y2 - y1));
            double ux = (x2 - x1) / len;
            double uy = (y2 - y1) / len;
            // от середины отрезка — внутрь гекса, чтобы сердечки легли НА стенку
            double nx = cx - mx;
            double ny = cy - my;
            double nl = Math.max(0.0001, Math.hypot(nx, ny));
            double depth = size * (NEUTRAL_OUTER - NEUTRAL_INNER);
            double px = mx + nx / nl * depth * 0.45;
            double py = my + ny / nl * depth * 0.45;
            double step = Math.min(r * 2.4, len / (here + 0.5));
            double start = -(here - 1) * step / 2;
            for (int i = 0; i < here; i++) {
                double d = start + i * step;
                // сердечки лежат НА стенке и повёрнуты вдоль неё — как всё
                // остальное, что напечатано на жетоне
                heart(c, px + ux * d, py + uy * d, r, drawn < left,
                    readableAngle(Math.toDegrees(Math.atan2(uy, ux))));
                drawn++;
            }
        }
    }

    /**
     * ГНЁЗДА ПРОЧНОСТИ (проект «гнёзда прочности», эксперт 26.08.2026): ряд
     * длиной hp пипсов по явной точке и углу — над подписью здания или буквой
     * рода войска, в осях жетона (перпендикуляр «вверх» от читаемого угла).
     * Белый заполненный пипс — прочность осталась, тёмное пустое гнездо —
     * потеряна (пустеет справа). Два признака сразу — заполненность и
     * светлота — читается и в сером. Целый жетон (damage = 0) ряда не несёт
     * вовсе — урон не лечится, чистый жетон значит цел. На мелком масштабе
     * (size &lt; {@link #HP_MIN}) остаётся только красная переобводка силуэта.
     */
    private static void paintHpPipsAt(FieldCanvas c, double size, int hp, int damage,
                                      double x, double y, double angleDeg) {
        if (hp <= 0 || damage <= 0 || !showDamage || size < HP_MIN) {
            return;
        }
        double a = Math.toRadians(angleDeg);
        double ux = Math.cos(a);
        double uy = Math.sin(a);
        double vx = Math.sin(a);
        double vy = -Math.cos(a);
        double pip = size * HP_PIP;
        double step = pip * HP_STEP;
        double x0 = x - ux * step * (hp - 1) / 2;
        double y0 = y - uy * step * (hp - 1) / 2;
        for (int i = 0; i < hp; i++) {
            boolean lost = i >= hp - damage;
            tokenSquare(c, x0 + ux * step * i, y0 + uy * step * i, pip,
                ux, uy, vx, vy,
                lost ? SLOT_FILL : WHITE, lost ? DAMAGE_EDGE : SLOT_EDGE, 0.9);
        }
    }

    /**
     * СЕРДЕЧКО ОДНИМ КОНТУРОМ. Сперва оно складывалось из двух кружков и
     * треугольника — и на стыках были видны их собственные обводки: получалось
     * не сердце, а три склеенных фигуры (замечание дизайнера 13.08.2026). Теперь
     * это одна замкнутая кривая: заливка и обводка кладутся один раз.
     *
     * <p>Кривая классическая: {@code x = 16 sin³t}, {@code y = 13 cos t − 5 cos 2t
     * − 2 cos 3t − cos 4t}. Ось Y на экране смотрит вниз, поэтому знак меняется.
     */
    private static void heart(FieldCanvas c, double cx, double cy, double r,
                              boolean full) {
        heart(c, cx, cy, r, full, 0);
    }

    /**
     * То же сердечко, но ПОВЁРНУТОЕ. Оно нарисовано на жетоне, значит должно ехать
     * и крутиться вместе с ним — ровно как подпись рядом. Раньше сердечко всегда
     * стояло вертикально, и на повёрнутом жетоне это било в глаз (замечание
     * дизайнера 13.08.2026).
     */
    private static void heart(FieldCanvas c, double cx, double cy, double r,
                              boolean full, double rotDeg) {
        int steps = 44;
        double[][] pts = new double[steps][];
        double k = r / 15.0;                 // кривая живёт в пределах ±16
        double ca = Math.cos(Math.toRadians(rotDeg));
        double sa = Math.sin(Math.toRadians(rotDeg));
        for (int i = 0; i < steps; i++) {
            double t = 2 * Math.PI * i / steps;
            double x = 16 * Math.pow(Math.sin(t), 3) * k;
            double y = -(13 * Math.cos(t) - 5 * Math.cos(2 * t)
                - 2 * Math.cos(3 * t) - Math.cos(4 * t)) * k;
            pts[i] = new double[]{cx + x * ca - y * sa, cy + x * sa + y * ca};
        }
        c.polygon(pts, full ? HEART_FILL : "none", HEART_EDGE, 0.8);
    }

    /**
     * ТЕКСТУРА НА МЕСТЕ СИЛУЭТА. Картинка нарисована в рамке формы (её viewBox), но
     * в пикселях, поэтому масштаб и точка привязки переводятся из единиц формы в
     * пиксели картинки — тогда текстура садится ровно туда, где стоял силуэт, и
     * крутится вместе с ним.
     */
    private static void drawTexture(FieldCanvas c, java.awt.image.BufferedImage tex,
                                    FieldGeometry.Shape sh, double cx, double cy,
                                    double rotDeg, double k) {
        double perPixel = sh.vbW() / tex.getWidth();
        double ax = sh.hexCx() / perPixel;
        double ay = sh.hexCy() / perPixel;
        c.image(tex, cx, cy, rotDeg, k * perPixel, ax, ay);
    }

    /** То же для войска: у него привязка по центру рамки, а масштаб — по ширине. */
    private static void drawUnitTexture(FieldCanvas c, java.awt.image.BufferedImage tex,
                                        FieldGeometry.Shape sh, double[] pos, double rotDeg,
                                        double targetW) {
        c.image(tex, pos[0], pos[1], rotDeg, targetW / tex.getWidth(),
            tex.getWidth() / 2.0, tex.getHeight() / 2.0);
    }

    /** Здание на своих секторах; возвращает точку для подписи и укрытых войск. */
    private static double[] paintBuilding(FieldCanvas c, double size, ReplayRecord.Tok b,
                                          List<Integer> sides, double hexCx, double hexCy) {
        double face = FieldGeometry.meanEdgeAngle(sides);
        int seat = b.owner % 4;
        // ЖЕТОН ПРИЖАТ К КРОМКЕ ГЕКСА. Силуэт ужат до 0,88 радиуса, и раньше вся
        // эта разница уходила в поля по внешнему краю. Теперь жетон того же размера
        // сдвигается наружу на половину этого зазора: у кромки становится плотно, а
        // между соседними жетонами появляется тонкая щель — она их и разделяет
        // (просьба дизайнера 13.08.2026). Всё, что печатается на жетоне, считается
        // от ЭТОЙ точки, поэтому едет вместе с ним.
        double[] shift = FieldGeometry.polar(hexCx, hexCy, size * EDGE_SHIFT, face);
        double cx = shift[0];
        double cy = shift[1];

        // ПОДКЛАДКИ ПОД ЗДАНИЕМ БОЛЬШЕ НЕТ (просьба дизайнера 12.08.2026): цветной
        // клин забивал картинку и мешал читать сам силуэт. Чей это гекс, теперь
        // видно по слабой подкраске всего гекса — см. paintOwnership().
        FieldGeometry.Shape sh = FieldGeometry.buildingByCode(b.type);
        // ТЕКСТУРА, ЕСЛИ НАРИСОВАНА. Нет файла (или там всё ещё заготовка) —
        // рисуется прежний силуэт: показ партии от текстур не зависит.
        Textures.Found found = Textures.found(b.type, b.level, seat);
        java.awt.image.BufferedImage tex = found == null ? null : found.image();
        Zones zones = found == null ? Zones.of("", null)
            : Zones.of(found.key(), Textures.folder());
        if (tex != null) {
            drawTexture(c, tex, sh, cx, cy, face - sh.outward(),
                FieldGeometry.seatScale(sh, size));
        } else {
            // ОБВОДКА ПОЖИРНЕЕ и темнее самого жетона — силуэт перестаёт сливаться
            // с подкраской гекса (просьба дизайнера 13.08.2026).
            c.shape(sh, cx, cy, face - sh.outward(), FieldGeometry.seatScale(sh, size),
                sh.hexCx(), sh.hexCy(), tone(FieldGeometry.SEAT_TOKEN[FieldGeometry.seatColor(seat)]),
                FieldGeometry.SEAT_STROKE[FieldGeometry.seatColor(seat)], TOKEN_STROKE);
        }
        // РАНЕНЫЙ ЖЕТОН обводится красным: видно издалека, а на сколько именно
        // ранен — говорит подсказка при наведении.
        if (b.damage > 0) {
            c.shape(sh, cx, cy, face - sh.outward(), FieldGeometry.seatScale(sh, size),
                sh.hexCx(), sh.hexCy(), "none", DAMAGE_FILL, 2.2);
        }

        // ПОДПИСЬ ВНУТРИ ЖЕТОНА, а не на его кромке. Раньше она стояла на радиусе
        // 0,52 размером 0,21 — и буквы вылезали за силуэт (скриншот дизайнера
        // 13.08.2026). Теперь радиус меньше, кегль меньше, и у подписи есть запас
        // от края; на узкой ячейке (одна сторона) запас нужен ещё больше.
        // ПОДПИСЬ ПРИЖАТА К ОДНОЙ СТЕНКЕ и идёт ПАРАЛЛЕЛЬНО ей. По центру жетона
        // она смотрелась чужеродно, а «вдоль средней оси» у здания на двух сторонах
        // означало вдоль стыка стенок — тоже не по-настоящему (замечание дизайнера
        // 13.08.2026). Берём ПЕРВУЮ занятую сторону: у ЦУ на последней стороне
        // лежит площадка запаса энергии, и подпись туда лезть не должна.
        int labelSide = labelSideOf(b, sides);
        double labelAngle = labelSide < 0 ? face + 90
            : FieldGeometry.edgeAngle(labelSide) + 90;
        // У СВОЕЙ СТЕНКИ, а не у центра гекса. Полоса жетона идёт примерно от 0,50
        // до 0,86 радиуса, поэтому подпись ставим на 0,66 — в середину этой полосы.
        // На 0,30 все подписи гекса сползались к центру и налезали друг на друга.
        // ЕСЛИ НА ЭТОЙ ЖЕ СТОРОНЕ ЛЕЖИТ ЗОНА СВОБОДНОЙ ЭНЕРГИИ — подпись уходит
        // ближе к центру: полоса зоны прижата к внешней кромке, и на 0,66 надпись
        // оказалась бы прямо на ней (энергостанция).
        // У ЭНЕРГОСТАНЦИИ подпись — на УЗКОЙ внутренней половине жетона: внешнюю
        // половину её стороны занимает зона свободной энергии (замечание дизайнера
        // 13.08.2026). У остальных зданий подпись по-прежнему в середине полосы.
        boolean labelInner = labelSide >= 0 && labelSide == storeSide(b, sides)
            && !"command_center".equals(b.type);
        double[] spot = labelSide < 0
            ? sectorMiddle(c, size, sides, cx, cy, face)
            : FieldGeometry.polar(cx, cy, size * (labelInner ? 0.50 : 0.66),
                FieldGeometry.edgeAngle(labelSide));
        // МЕСТО ПОД ПОДПИСЬ, ЕСЛИ ОНО РАЗМЕЧЕНО НА ТЕКСТУРЕ (зелёное пятно маски):
        // художник сам решает, где надписи место, — это надёжнее любых отступов.
        double[] marked = zones.labelSpot();
        if (marked != null) {
            spot = onToken(marked, zones, sh, tex, cx, cy, face - sh.outward(),
                FieldGeometry.seatScale(sh, size));
        }
        // ЭНЕРГИЯ РИСУЕТСЯ ДО ПОДПИСИ. Зона свободной энергии теперь накладка во
        // всё крыло, и нарисованная после подписи она просто закрывала её собой
        // (видно на энергостанциях). Порядок как на настоящем жетоне: печать
        // зоны — внизу, надпись — поверх.
        // ВСЁ, ЧТО НАПЕЧАТАНО НА ЖЕТОНЕ, ОБРЕЗАЕТСЯ ЕГО СИЛУЭТОМ: тогда зона
        // свободной энергии доходит ровно до кромки — ни зазора, ни вылета — и её
        // не приходится подгонять радиусами под каждую форму.
        c.clipToShape(sh, cx, cy, face - sh.outward(), FieldGeometry.seatScale(sh, size),
            sh.hexCx(), sh.hexCy());
        if (!zones.slots().isEmpty() || zones.energyArea() != null) {
            paintEnergyByZones(c, size, b, zones, sh, tex, cx, cy,
                face - sh.outward(), FieldGeometry.seatScale(sh, size));
        } else {
            paintEnergyOnToken(c, size, b, sides, cx, cy);
        }
        c.clipOff();
        // ОБВОДКА ЖЕТОНА ПОВЕРХ ВСЕЙ ПЕЧАТИ. Зона свободной энергии — накладка во
        // всё крыло, обрезанная силуэтом, поэтому она ложилась ровно на кромку и
        // съедала обводку с той стороны: жетон выглядел «надкусанным». Контур
        // обводится ЕЩЁ РАЗ последним слоем, уже без заливки, и рамка снова целая
        // по всему периметру (просьба дизайнера 13.08.2026).
        c.shape(sh, cx, cy, face - sh.outward(), FieldGeometry.seatScale(sh, size),
            sh.hexCx(), sh.hexCy(), "none", FieldGeometry.SEAT_STROKE[FieldGeometry.seatColor(seat)], TOKEN_STROKE);
        // ПОДПИСЬ ЖЕТОНА — общая с планшетом: «Эн-3», «Дб-1», «ЦУ»
        String code = Labels.buildingLabel(b.type, b.level);
        // ПОДПИСЬ ИДЁТ ВДОЛЬ СВОЕЙ СТЕНКИ. Первый заход брал угол самой ФОРМЫ
        // (face − outward) — это внутренняя поправка отрисовки силуэта, к тексту она
        // отношения не имеет, и надпись уезжала с жетона.
        c.outlinedTextRotated(code, spot[0], spot[1], size * 0.175,
            WHITE, LABEL_OUTLINE, readableAngle(labelAngle));
        // ГНЁЗДА ПРОЧНОСТИ — НАД подписью, в осях жетона: та же точка, где
        // стояли прежние кубики урона (радиусы из проекта — 0.80 у кромки —
        // на настоящих узких силуэтах срезались клипом, а место над подписью
        // проверено неделями с кубиками).
        {
            double ra = readableAngle(labelAngle);
            double rr = Math.toRadians(ra);
            double px = Math.sin(rr);
            double py = -Math.cos(rr);
            paintHpPipsAt(c, size, b.hp, b.damage,
                spot[0] + px * size * 0.22, spot[1] + py * size * 0.22, ra);
        }
        return spot;
    }

    /**
     * СЕРЕДИНА КЛИНА, занятого зданием: среднее по внешним углам его сторон,
     * подтянутое к центру гекса. Именно здесь надпись сидит внутри силуэта при
     * любом числе занятых сторон.
     */
    private static double[] sectorMiddle(FieldCanvas c, double size, List<Integer> sides,
                                         double cx, double cy, double face) {
        if (sides.isEmpty()) {
            return FieldGeometry.polar(cx, cy, size * 0.38, face);
        }
        double sx = 0;
        double sy = 0;
        int n = 0;
        for (int s : sides) {
            double a = Math.toRadians(FieldGeometry.edgeAngle(s));
            sx += Math.cos(a);
            sy += Math.sin(a);
            n++;
        }
        double len = Math.max(0.0001, Math.hypot(sx / n, sy / n));
        double ux = (sx / n) / len;
        double uy = (sy / n) / len;
        // 0,58 апофемы: заметно внутри силуэта, но не в самом центре гекса
        double r = size * 0.866 * 0.58;
        return new double[]{cx + ux * r, cy + uy * r};
    }

    /**
     * УМНЫЙ ПОВОРОТ ПОДПИСИ. Надпись едет вместе с жетоном — но только пока
     * остаётся читаемой ЗРИТЕЛЮ. Перевалило за прямой угол — переворачиваем на 180°
     * (то же положение на жетоне, но буквы не вверх ногами), и в любом случае
     * заваливаем не больше чем на 60°: сильнее наклонённый текст читается плохо.
     */
    static double readableAngle(double rotDeg) {
        double a = ((rotDeg % 360) + 540) % 360 - 180;     // в (−180; 180]
        if (a > 90) {
            a -= 180;
        } else if (a < -90) {
            a += 180;
        }
        // БОЛЬШЕ НИКАКОЙ ОБРЕЗКИ ДО ±60. Она и ломала параллельность: у стенок,
        // стоящих круче 60°, текст «выпрямлялся» и переставал идти вдоль кромки —
        // на части зданий это читалось как поворот не в ту сторону (замечание
        // дизайнера 13.08.2026). Разворот на 180° остаётся: буквы не должны
        // оказаться вверх ногами.
        return a;
    }

    /**
     * ТОЧКА МАСКИ → ТОЧКА НА ПОЛЕ. Маска нарисована в пикселях своего размера, а
     * жетон живёт в единицах формы, поэтому переводим через доли: доля по маске
     * равна доле по рамке формы. Дальше — то же преобразование, что и у самой
     * текстуры, поэтому размеченное место едет и крутится вместе с жетоном.
     */
    private static double[] onToken(double[] maskPoint, Zones z, FieldGeometry.Shape sh,
                                    java.awt.image.BufferedImage tex, double cx, double cy,
                                    double rotDeg, double k) {
        double fx = maskPoint[0] / Math.max(1, z.maskWidth());
        double fy = maskPoint[1] / Math.max(1, z.maskHeight());
        // в единицах формы: рамка формы и есть полотно текстуры
        double vx = fx * sh.vbW() - sh.hexCx();
        double vy = fy * sh.vbH() - sh.hexCy();
        double a = Math.toRadians(rotDeg);
        double rx = vx * Math.cos(a) - vy * Math.sin(a);
        double ry = vx * Math.sin(a) + vy * Math.cos(a);
        return new double[]{cx + rx * k, cy + ry * k};
    }

    /**
     * ЭНЕРГИЯ ПО РАЗМЕТКЕ ТЕКСТУРЫ. Ячейки стоят там, где художник нарисовал
     * красные квадраты, и под тем же углом; запас энергии ложится в синее пятно.
     * Всё это едет и крутится вместе с жетоном — разметка живёт в его координатах.
     */
    private static void paintEnergyByZones(FieldCanvas c, double size, ReplayRecord.Tok b,
                                           Zones z, FieldGeometry.Shape sh,
                                           java.awt.image.BufferedImage tex,
                                           double cx, double cy, double rotDeg, double k) {
        if (!showEnergy) {
            return;
        }
        List<Zones.Slot> slots = z.slots();
        for (int i = 0; i < slots.size() && i < Math.max(0, b.energySlots); i++) {
            Zones.Slot s = slots.get(i);
            double[] p = onToken(new double[]{s.cx(), s.cy()}, z, sh, tex, cx, cy, rotDeg, k);
            double side = s.w() / Math.max(1, z.maskWidth()) * sh.vbW() * k;
            double angle = rotDeg + s.angleDeg();
            boolean filled = i < b.energyPlaced;
            square(c, p[0], p[1], side, angle, SLOT_FILL, SLOT_EDGE);
            if (filled) {
                double a = Math.toRadians(angle);
                tokenCube(c, p[0], p[1], side * 0.74, Math.cos(a), Math.sin(a),
                    -Math.sin(a), Math.cos(a), ENERGY_ON, ENERGY_EDGE);
            }
        }
        Zones.Area area = z.energyArea();
        boolean source = "power_plant".equals(b.type) || "command_center".equals(b.type);
        if (area != null && source && b.energyIdle > 0) {
            double[] p = onToken(new double[]{area.cx(), area.cy()}, z, sh, tex,
                cx, cy, rotDeg, k);
            double along = area.w() / Math.max(1, z.maskWidth()) * sh.vbW() * k;
            double cube = Math.min(size * ENERGY_SLOT * 0.9,
                along / Math.max(1, b.energyIdle) * 0.8);
            double angle = rotDeg + area.angleDeg();
            double ux = Math.cos(Math.toRadians(angle));
            double uy = Math.sin(Math.toRadians(angle));
            double step = cube * 1.15;
            double start = -(b.energyIdle - 1) * step / 2;
            double nx = -Math.sin(Math.toRadians(angle));
            double ny = Math.cos(Math.toRadians(angle));
            for (int i = 0; i < b.energyIdle; i++) {
                double d = start + i * step;
                tokenCube(c, p[0] + ux * d, p[1] + uy * d, cube,
                    ux, uy, nx, ny, ENERGY_ON, ENERGY_EDGE);
            }
        }
    }

    /** Квадрат с поворотом — ячейка энергии или кубик в ней. */
    private static void square(FieldCanvas c, double cx, double cy, double side,
                               double angleDeg, String fill, String edge) {
        double a = Math.toRadians(angleDeg);
        double ux = Math.cos(a) * side / 2;
        double uy = Math.sin(a) * side / 2;
        double vx = -Math.sin(a) * side / 2;
        double vy = Math.cos(a) * side / 2;
        c.polygon(new double[][]{
            {cx - ux - vx, cy - uy - vy},
            {cx + ux - vx, cy + uy - vy},
            {cx + ux + vx, cy + uy + vy},
            {cx - ux + vx, cy - uy + vy}}, fill, edge, 1.2);
    }

    /**
     * ЭНЕРГИЯ НА ЖЕТОНЕ: ячейки и кубики.
     *
     * <p>Раньше кубик энергии просто сдвигался от подписи на фиксированные
     * 0.19 радиуса и у части зданий уезжал на край или вовсе за жетон. Теперь
     * всё считается от КЛИНА, который здание занимает: ряд строится по оси
     * клина, а его половинная ширина ограничена шириной самого клина на этом
     * радиусе — поэтому ничего не вылезает за силуэт (просьба дизайнера
     * 12.08.2026).
     *
     * <p>Что рисуется:
     * <ul>
     *   <li><b>потребителям</b> (ЦУ, казарма, завод, авиабаза, добытчик) — чёрные
     *       квадраты ЯЧЕЕК, чуть крупнее кубика; в занятые ячейки вписан кубик;</li>
     *   <li><b>источникам</b> (энергостанция, ЦУ) — зона хранения в жёлтую
     *       полоску и лежащие на ней ПРОСТАИВАЮЩИЕ кубики;</li>
     *   <li>у ЦУ ячейка и зона хранения разведены по разным «крыльям».</li>
     * </ul>
     */
    private static void paintEnergyOnToken(FieldCanvas c, double size, ReplayRecord.Tok b,
                                           List<Integer> sides, double cx, double cy) {
        if (!showEnergy) {
            return;
        }
        boolean source = "power_plant".equals(b.type) || "command_center".equals(b.type);
        // У ИСТОЧНИКА ЗОНА ЕСТЬ ВСЕГДА, даже когда на ней пусто: она напечатана на
        // жетоне. Раньше пустая энергостанция уходила отсюда сразу и выглядела как
        // здание вовсе без зоны (замечание дизайнера 13.08.2026).
        if (b.energySlots <= 0 && !source) {
            return;
        }
        // КАЖДЫЙ ЭЛЕМЕНТ РАВНЯЕТСЯ ПО СВОЕЙ СТЕНКЕ, а не по средней оси жетона.
        // У здания на двух-трёх ячейках внешних сторон тоже несколько, и их
        // нормали отличаются от средней на ±30°. Если считать от средней, все
        // квадраты стоят «косо» относительно кромки жетона — это и было видно
        // на скриншотах дизайнера. Поэтому ниже каждый элемент берёт ось СВОЕЙ
        // ячейки гекса: нормаль стороны s смотрит под углом −60·s градусов.
        // СТОРОНЫ ПОДРЯД (см. runOrder): первая и последняя — настоящие края здания
        List<Integer> own = runOrder(sides);
        int span = Math.max(1, own.size());

        double slot = size * ENERGY_SLOT;
        double step = slot * 1.30;
        // Радиус ряда: внутри ВПИСАННОЙ окружности (апофема 0.866·size) с
        // отступом на полквадрата; поперёк — внутри 60° раствора ОДНОЙ ячейки.
        double apothem = size * 0.866;
        double rowR = Math.min(size * 0.50, apothem - slot * 0.95);
        double roomPerSide = Math.max(slot * 0.6,
            rowR * Math.tan(Math.toRadians(30)) - slot * 0.55);

        // У источника с ячейкой (это ЦУ) стороны делятся: под запас уходит
        // ПОСЛЕДНЯЯ, ячейки занимают остальные. Так одно «крыло» жетона несёт
        // чёрную ячейку, другое — полосатую площадку с кубиками.
        // У ИСТОЧНИКА С ЯЧЕЙКАМИ (это ЦУ) стороны делятся: под запас уходит
        // ПОСЛЕДНЯЯ, ячейки занимают остальные — и площадка запаса есть всегда,
        // пустая или с кубиками (просьба дизайнера 13.08.2026).
        boolean storeOnOwnSide = source && span >= 2 && b.energySlots > 0;
        int slotSides = storeOnOwnSide ? span - 1 : span;

        if (b.energySlots > 0 && !source && span >= 2) {
            // ЗАВОД И АВИАБАЗА: все ячейки ТЕСНОЙ ГРУППОЙ У ОДНОЙ СТЕНКИ. Раньше
            // они делились поровну между занятыми сторонами и расползались по
            // жетону симметрично относительно центра — читалось как случайная
            // россыпь квадратов (замечание дизайнера 13.08.2026). Стенку берём
            // дальнюю от подписи, чтобы они не спорили за место.
            int labelSide = sides.isEmpty() ? own.get(0) : sides.get(0);
            drawSlots(c, b.energySlots, b.energyPlaced, slot, farthestSide(own, labelSide),
                rowR, cx, cy, step, roomPerSide);
        } else if (b.energySlots > 0) {
            for (int si = 0; si < slotSides; si++) {
                int mine = countFor(b.energySlots, slotSides, si);
                if (mine == 0) {
                    continue;
                }
                int before = firstIndexFor(b.energySlots, slotSides, si);
                int filled = Math.max(0, Math.min(mine, b.energyPlaced - before));
                drawSlots(c, mine, filled, slot, own.get(si), rowR,
                    cx, cy, step, roomPerSide);
            }
        }
        if (source) {
            int side = own.get(storeOnOwnSide ? span - 1 : 0);
            drawIdleStore(c, b.energyIdle, slot, side, size, cx, cy, storeOnOwnSide);
        }
    }

    /**
     * Сторона под ПОДПИСЬ. У ЦУ она обязана быть той же, где ячейка энергии, —
     * противоположной зоне свободной энергии. Раньше бралась просто первая сторона
     * из записи, а порядок там произвольный, и подпись с зоной то расходились, то
     * налезали друг на друга (замечание дизайнера 13.08.2026).
     */
    private static int labelSideOf(ReplayRecord.Tok b, List<Integer> sides) {
        List<Integer> run = runOrder(sides);
        return run.isEmpty() ? -1 : run.get(0);
    }

    /** Сторона, на которой лежит зона свободной энергии (−1 — зоны у здания нет). */
    private static int storeSide(ReplayRecord.Tok b, List<Integer> sides) {
        if (!("power_plant".equals(b.type) || "command_center".equals(b.type))
                || sides.isEmpty()) {
            return -1;
        }
        List<Integer> run = runOrder(sides);
        boolean storeOnOwnSide = run.size() >= 2 && b.energySlots > 0;
        return run.get(storeOnOwnSide ? run.size() - 1 : 0);
    }

    /**
     * СТОРОНЫ ЗДАНИЯ ПОДРЯД, КАК ОНИ ЛЕЖАТ ВОКРУГ ГЕКСА.
     *
     * <p>Раньше они просто сортировались по номеру, и на этом всё ломалось, когда
     * здание перешагивает через нулевую сторону: у набора {4, 5, 0} сортировка даёт
     * {0, 4, 5}, «последней» оказывается СРЕДНЯЯ сторона 5 — и зона свободной
     * энергии уезжала в середину жетона, причём по-разному при разных поворотах.
     * Дизайнер видел это как «зона крутится не в ту сторону» (13.08.2026).
     *
     * <p>Здесь стороны выстраиваются в непрерывную цепочку: начало — та сторона,
     * перед которой соседа нет. Тогда первая и последняя — это настоящие КРАЯ
     * здания, между ними ничего чужого не лежит.
     */
    static List<Integer> runOrder(List<Integer> sides) {
        List<Integer> out = new ArrayList<>();
        if (sides == null || sides.isEmpty()) {
            return out;
        }
        Set<Integer> set = new java.util.LinkedHashSet<>(sides);
        if (set.size() >= 6) {
            out.addAll(java.util.List.of(0, 1, 2, 3, 4, 5));
            return out;
        }
        int start = -1;
        for (int s : set) {
            if (!set.contains((s + 5) % 6)) {
                start = s;
                break;
            }
        }
        if (start < 0) {
            start = set.iterator().next();
        }
        int cur = start;
        while (set.contains(cur) && out.size() < set.size()) {
            out.add(cur);
            cur = (cur + 1) % 6;
        }
        // на всякий случай: если стороны не смежные, добьём остаток по порядку
        for (int s : set) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Сторона из {@code own}, наиболее удалённая по углу от стороны {@code from}. */
    private static int farthestSide(List<Integer> own, int from) {
        int best = own.get(0);
        double bestDiff = -1;
        for (int s : own) {
            int d = Math.abs(((s - from) % 6 + 6) % 6);
            double diff = Math.min(d, 6 - d);
            if (diff > bestDiff) {
                bestDiff = diff;
                best = s;
            }
        }
        return best;
    }

    /** Сколько ячеек досталось стороне {@code si} при равномерной раздаче. */
    private static int countFor(int total, int parts, int si) {
        return total / parts + (si < total % parts ? 1 : 0);
    }

    /** Номер первой ячейки, попавшей на сторону {@code si} (для подсчёта занятых). */
    private static int firstIndexFor(int total, int parts, int si) {
        int sum = 0;
        for (int i = 0; i < si; i++) {
            sum += countFor(total, parts, i);
        }
        return sum;
    }

    /** Оси ячейки гекса: {ux, uy, vx, vy} — вдоль стенки и наружу. */
    private static double[] sideAxes(int side) {
        double ang = Math.toRadians(FieldGeometry.edgeAngle(side));
        double vx = Math.cos(ang);
        double vy = Math.sin(ang);
        return new double[]{-vy, vx, vx, vy};
    }

    /**
     * Квадрат в осях ЖЕТОНА: центр (x,y), сторона a, оси u (вдоль стенки) и
     * v (наружу). Рисуется четырёхугольником, поэтому поворачивается вместе с
     * жетоном — прямые стороны остаются параллельны сторонам жетона.
     */
    private static void tokenSquare(FieldCanvas c, double x, double y, double a,
                                    double ux, double uy, double vx, double vy,
                                    String fill, String stroke, double sw) {
        c.polygon(padQuad(x, y, a, a, ux, uy, vx, vy), fill, stroke, sw);
    }

    /**
     * ОБЪЁМНЫЙ КУБИК ЭНЕРГИИ в осях жетона — такой же, как кубики на планшете
     * науки: лицевая грань, светлая верхняя и тёмная боковая (просьба дизайнера
     * 13.08.2026). Строится в осях u (вдоль стенки) и v (наружу), поэтому едет и
     * крутится вместе с жетоном.
     */
    private static void tokenCube(FieldCanvas c, double cx, double cy, double a,
                                  double ux, double uy, double vx, double vy,
                                  String fill, String edge) {
        double d = a * 0.26;                 // глубина «объёма»
        double f = a - d;                    // сторона лицевой грани
        double o = -a / 2;                   // левый верхний угол коробки a×a
        // верхняя грань — светлее
        c.polygon(new double[][]{
            local(cx, cy, ux, uy, vx, vy, o, o + d),
            local(cx, cy, ux, uy, vx, vy, o + d, o),
            local(cx, cy, ux, uy, vx, vy, o + a, o),
            local(cx, cy, ux, uy, vx, vy, o + f, o + d)},
            shade(fill, 1.35), null, 0);
        // боковая грань — темнее
        c.polygon(new double[][]{
            local(cx, cy, ux, uy, vx, vy, o + f, o + d),
            local(cx, cy, ux, uy, vx, vy, o + a, o),
            local(cx, cy, ux, uy, vx, vy, o + a, o + f),
            local(cx, cy, ux, uy, vx, vy, o + f, o + a)},
            shade(fill, 0.72), null, 0);
        // лицо
        c.polygon(new double[][]{
            local(cx, cy, ux, uy, vx, vy, o, o + d),
            local(cx, cy, ux, uy, vx, vy, o + f, o + d),
            local(cx, cy, ux, uy, vx, vy, o + f, o + a),
            local(cx, cy, ux, uy, vx, vy, o, o + a)},
            fill, edge, 0.9);
    }

    /** Точка (lx, ly) местных координат кубика — в мировые оси жетона. */
    private static double[] local(double cx, double cy, double ux, double uy,
                                  double vx, double vy, double lx, double ly) {
        return new double[]{cx + ux * lx + vx * ly, cy + uy * lx + vy * ly};
    }

    /** Осветлить (k>1) или затемнить (k<1) цвет «#rrggbb». */
    static String shade(String hex, double k) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
            return hex;
        }
        int r = clamp255((int) Math.round(Integer.parseInt(hex.substring(1, 3), 16) * k));
        int g = clamp255((int) Math.round(Integer.parseInt(hex.substring(3, 5), 16) * k));
        int b = clamp255((int) Math.round(Integer.parseInt(hex.substring(5, 7), 16) * k));
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Прямоугольник w×h с центром (x,y) в осях жетона: u — вдоль, v — наружу. */
    private static double[][] padQuad(double x, double y, double w, double h,
                                      double ux, double uy, double vx, double vy) {
        double a = w / 2;
        double b = h / 2;
        return new double[][]{
            {x - ux * a - vx * b, y - uy * a - vy * b},
            {x + ux * a - vx * b, y + uy * a - vy * b},
            {x + ux * a + vx * b, y + uy * a + vy * b},
            {x - ux * a + vx * b, y - uy * a + vy * b}
        };
    }

    /**
     * Ряд ЯЧЕЕК энергии на ОДНОЙ ячейке гекса: чёрные квадраты, в занятые
     * вписан кубик. Всё строится в осях этой стороны, поэтому стороны квадратов
     * параллельны её кромке.
     */
    private static void drawSlots(FieldCanvas c, int slots, int filled, double slot,
                                  int side, double rowR, double cx, double cy,
                                  double step, double halfRoom) {
        double[] ax = sideAxes(side);
        double ux = ax[0];
        double uy = ax[1];
        double vx = ax[2];
        double vy = ax[3];
        double cxRow = cx + vx * rowR;
        double cyRow = cy + vy * rowR;
        int n = Math.max(1, slots);
        // ряд обязан уместиться в отведённую ширину: сжимаем шаг, если тесно
        double useStep = Math.min(step, (2 * halfRoom) / n);
        double start = -(n - 1) / 2.0 * useStep;
        double cube = slot * ENERGY_CUBE_IN_SLOT;
        for (int i = 0; i < n; i++) {
            double off = start + i * useStep;
            double x = cxRow + ux * off;
            double y = cyRow + uy * off;
            tokenSquare(c, x, y, slot, ux, uy, vx, vy, SLOT_FILL, SLOT_EDGE, 0.9);
            if (i < filled) {
                tokenCube(c, x, y, cube, ux, uy, vx, vy, ENERGY_ON, ENERGY_EDGE);
            }
        }
    }

    /**
     * Зона хранения источника: полосатая площадка и лежащие на ней кубики.
     * Всё строится в осях ЖЕТОНА (u вдоль стенки, v наружу), поэтому площадка
     * поворачивается вместе с жетоном, а её стороны параллельны его сторонам.
     */
    private static void drawIdleStore(FieldCanvas c, int idle, double slot, int side,
                                      double size, double cx, double cy, boolean halfWide) {
        double[] ax = sideAxes(side);
        double ux = ax[0];
        double uy = ax[1];
        double vx = ax[2];
        double vy = ax[3];
        // ПЛОЩАДКА РИСУЕТСЯ ВСЕГДА, даже пустая: она НАПЕЧАТАНА на жетоне, как и
        // ячейки под энергию. Раньше её показывали только когда на ней лежали
        // кубики, и у ЦУ зоны запаса будто не существовало (замечание дизайнера
        // 13.08.2026).
        //
        // НАКЛАДКА У САМОЙ КРОМКИ. Первый заход занимал всё крыло от внутреннего
        // выреза до внешней кромки — вышло непомерно много (замечание дизайнера
        // 13.08.2026). Теперь это полоса вдоль ВНЕШНЕЙ стенки, вполовину мельче:
        // у энергостанции — во всю ширину её стороны, у ЦУ — в половину ширины,
        // прижатой к краю стороны.
        // 0,73 — внешняя кромка самого жетона (апофема 0,866 с усадкой 0,88 минус
        // обводка): дальше площадка вылезла бы за силуэт.
        // У ЭНЕРГОСТАНЦИИ зона занимает только ВНЕШНЮЮ половину крыла: на узкой
        // внутренней половине стоит подпись, и накрывать её зоной нельзя
        // (замечание дизайнера 13.08.2026). У ЦУ подпись на другом крыле, поэтому
        // там зона идёт на всю глубину.
        // ЗОНА РИСУЕТСЯ С ЗАПАСОМ, а её настоящие границы задаёт САМ СИЛУЭТ жетона:
        // вызывающая сторона включила обрезку по нему. Подбирать радиусы и отступы
        // под форму руками бессмысленно — у каждого силуэта она своя, и зона то не
        // доставала до кромки, то лезла наружу (замечание дизайнера 13.08.2026).
        double rIn = size * (halfWide ? 0.30 : 0.62);
        double rOut = size * 0.95;
        double tan30 = Math.tan(Math.toRadians(30));
        double margin = 0;
        double hIn = Math.max(size * 0.05, rIn * tan30 - margin);
        double hOut = Math.max(hIn, rOut * tan30 - margin);
        // ПОЛОВИНА ШИРИНЫ, ПРИЖАТАЯ К КРАЮ СТОРОНЫ — так зона запаса у ЦУ не спорит
        // ни с подписью, ни с ячейкой энергии на соседнем крыле.
        // ЗОНА — ПЛОТНЫЙ КУСОК У КРАЯ СТОРОНЫ, а не длинная узкая лента вдоль всей
        // стенки: она идёт на всю глубину крыла, но занимает лишь его половину по
        // длине (эскиз дизайнера 13.08.2026). Половина всегда та, что дальше от
        // подписи, — сама подпись сдвинута к противоположному краю.
        // ЗОНА У ЦУ — ВСЕГДА С КРАЮ, на дальнем от соседнего крыла конце стенки:
        // на соседнем крыле стоят ячейка энергии, имя жетона и сердечко, и зона
        // не должна к ним подходить (правило дизайнера 13.08.2026). Соседнее крыло
        // всегда со стороны +u, поэтому зона прижимается к −u.
        double sIn = -hIn;
        double sOut = -hOut;
        double eIn = -hIn * 0.05;
        double eOut = -hOut * 0.05;
        if (!halfWide) {
            // ЭНЕРГОСТАНЦИЯ: зона свободной энергии занимает ВСЮ ширину её стороны,
            // от края до края — так решил дизайнер (13.08.2026). Подпись ложится
            // поверх неё: зона печатная, надпись поверх печати.
            eIn = hIn;
            eOut = hOut;
        }
        double[][] plate = {
            {cx + vx * rIn + ux * sIn, cy + vy * rIn + uy * sIn},
            {cx + vx * rOut + ux * sOut, cy + vy * rOut + uy * sOut},
            {cx + vx * rOut + ux * eOut, cy + vy * rOut + uy * eOut},
            {cx + vx * rIn + ux * eIn, cy + vy * rIn + uy * eIn}};
        c.polygon(plate, STORE_FILL, STORE_EDGE, 1.1);

        // ПОЛОСКИ РИСУЮТСЯ С ЗАПАСОМ И ОБРЕЗАЮТСЯ ПО САМОЙ ЗОНЕ. Раньше их длину
        // и число подгоняли арифметикой, и у краёв они то не доставали, то
        // вылезали наружу (замечание дизайнера 13.08.2026).
        // СОДЕРЖИМОЕ ЗОНЫ (полоски и кубики) считается по НАСТОЯЩИМ размерам крыла,
        // а не по раздутой рамке: иначе кубики уехали бы за кромку и обрезка их
        // просто съела бы.
        double rowR = size * (halfWide ? 0.60 : 0.69);
        double hRow = rowR * tan30 - size * 0.03;
        double mid = halfWide ? -hRow / 2 : 0;
        double half = halfWide ? hRow / 2 : hRow;
        // штриховка МЕЛКАЯ И ЧАСТАЯ — крупные полосы спорили с кубиками
        double stepU = slot * 0.22;
        double sw = slot * 0.045;
        c.clipTo(plate);
        for (double off = mid - half - stepU; off <= mid + half + stepU; off += stepU) {
            c.polygon(padQuad(cx + vx * rowR + ux * off, cy + vy * rowR + uy * off,
                2 * sw, size * 1.2, ux, uy, vx, vy), STORE_STRIPE, null, 0);
        }
        c.clipOff();

        double cube = slot * ENERGY_CUBE_IN_SLOT;
        int cubes = Math.max(0, Math.min(idle, 3));
        double useStep = Math.min(cube * 1.30, 2 * half * 0.9 / Math.max(1, cubes));
        double start = mid - (cubes - 1) / 2.0 * useStep;
        for (int i = 0; i < cubes; i++) {
            double off = start + i * useStep;
            tokenCube(c, cx + vx * rowR + ux * off, cy + vy * rowR + uy * off, cube,
                ux, uy, vx, vy, ENERGY_ON, ENERGY_EDGE);
        }
        if (idle > cubes) {
            // Больше трёх кубиков площадка не вмещает — пишем остаток цифрой ПОД
            // рядом кубиков, ближе к центру гекса. У кромки жетона надпись
            // оказывалась уже за его краем и обрезалась.
            double tr = rowR - cube * 1.2;
            c.text("+" + (idle - cubes), cx + vx * tr + ux * mid,
                cy + vy * tr + uy * mid + cube * 0.35, cube * 0.95, true, ENERGY_EDGE);
        }
    }

    private static void paintUnits(FieldCanvas c, double size, List<ReplayRecord.Tok> tokens,
                                   ReplayRecord.HexState st, Set<Integer> taken,
                                   Map<String, double[]> hideSpots, double cx, double cy) {
        // авиация — в центральную воздушную ячейку
        int airDrawn = 0;
        boolean airHideUsed = false;
        for (ReplayRecord.Tok u : tokens) {
            if (u.building || !"aircraft".equals(u.type)) {
                continue;
            }
            // Внутри здания — только если войско ЯВНО туда вставлено.
            double[] hide = u.insideBuildingUid == null ? null
                : hideSpots.get(String.valueOf(u.insideBuildingUid));
            if (hide != null && !airHideUsed) {
                airHideUsed = true;
                paintHidden(c, size, u, hide[0] - size * 0.20, hide[1] - size * 0.20);
                continue;
            }
            double ax = cx + airDrawn * 10;
            double ay = cy - airDrawn * 6;
            FieldGeometry.Shape sh = FieldGeometry.SH_AIRCRAFT;
            c.shape(sh, ax, ay, 0, size * 0.40 / sh.vbW(), sh.vbW() / 2, sh.vbH() / 2,
                tone(FieldGeometry.SEAT_TOKEN[FieldGeometry.seatColor(u.owner)]),
                FieldGeometry.SEAT_STROKE[FieldGeometry.seatColor(u.owner)], TOKEN_STROKE);
            paintHpPipsAt(c, size, u.hp, u.damage, ax, ay - size * 0.20, 0);
            airDrawn++;
        }

        // наземные — по СВОБОДНЫМ сторонам (занятые нейтралами тоже несвободны)
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            boolean busy = taken.contains(i) || (st != null && st.sideOwner[i] != -1);
            if (!busy) {
                free.add(i);
            }
        }
        int overflow = 0;
        Set<String> hideUsed = new HashSet<>();
        for (ReplayRecord.Tok u : tokens) {
            if (u.building || "aircraft".equals(u.type)) {
                continue;
            }
            // Внутри здания — только по ЯВНОМУ указанию жетона, а не по догадке
            // «пехота стоит на гексе своей казармы».
            String hideKey = u.insideBuildingUid == null ? null
                : String.valueOf(u.insideBuildingUid);
            double[] spot = hideKey == null ? null : hideSpots.get(hideKey);
            if (spot != null && !hideUsed.contains(hideKey)) {
                hideUsed.add(hideKey);
                paintHidden(c, size, u, spot[0] - size * 0.20, spot[1] - size * 0.20);
                continue;
            }
            List<Integer> place = null;
            if ("vehicle".equals(u.type)) {
                outer:
                for (int a : free) {
                    for (int b : free) {
                        if ((a + 1) % 6 == b) {
                            place = List.of(a, b);
                            break outer;
                        }
                    }
                }
            } else if (!free.isEmpty()) {
                place = List.of(free.get(0));
            }
            FieldGeometry.Shape sh = FieldGeometry.unitByCode(u.type);
            double[] pos;
            java.awt.image.BufferedImage tex = Textures.unit(u.type, u.owner);
            double unitAngle;
            if (place != null) {
                free.removeAll(place);
                double face = FieldGeometry.meanEdgeAngle(place);
                double w = FieldGeometry.unitWidth(u.type, place.size(), size);
                pos = FieldGeometry.polar(cx, cy, FieldGeometry.unitSeatRadius(size), face);
                if (tex != null) {
                    drawUnitTexture(c, tex, sh, pos, FieldGeometry.unitRotation(sh, face), w);
                } else {
                    c.shape(sh, pos[0], pos[1], FieldGeometry.unitRotation(sh, face),
                        w / sh.vbW(), sh.vbW() / 2, sh.vbH() / 2,
                        tone(FieldGeometry.SEAT_TOKEN[FieldGeometry.seatColor(u.owner)]),
                        FieldGeometry.SEAT_STROKE[FieldGeometry.seatColor(u.owner)], TOKEN_STROKE);
                }
                unitAngle = face + 90;
                paintUnitLetter(c, u, pos, w, unitAngle);
            } else {
                pos = FieldGeometry.polar(cx, cy, size * 0.28,
                    60.0 * overflow - 30 + FieldGeometry.TILT);
                double w = FieldGeometry.unitWidth(u.type, 1, size) * 0.9;
                if (tex != null) {
                    drawUnitTexture(c, tex, sh, pos, 0, w);
                } else {
                    c.shape(sh, pos[0], pos[1], 0, w / sh.vbW(), sh.vbW() / 2, sh.vbH() / 2,
                        tone(FieldGeometry.SEAT_TOKEN[FieldGeometry.seatColor(u.owner)]),
                        FieldGeometry.SEAT_STROKE[FieldGeometry.seatColor(u.owner)], TOKEN_STROKE);
                }
                unitAngle = 0;
                paintUnitLetter(c, u, pos, w, unitAngle);
                overflow++;
            }
            // Гнёзда прочности — НАД буквой рода, в осях самого жетона: та же
            // точка, где раньше стояли кубики урона.
            {
                double ra = readableAngle(unitAngle);
                double rr = Math.toRadians(ra);
                double px = Math.sin(rr);
                double py = -Math.cos(rr);
                double off = size * 0.28;
                paintHpPipsAt(c, size, u.hp, u.damage,
                    pos[0] + px * off, pos[1] + py * off, ra);
            }
        }
    }

    /**
     * БУКВА РОДА НА ЖЕТОНЕ ВОЙСКА: П — пехота, Т — техника, А — авиация,
     * В — вышка (просьба дизайнера 13.08.2026). Тонкая и мелкая: жетон войска и
     * так небольшой, а буква нужна лишь чтобы отличить род с одного взгляда.
     *
     * <p>Поворот — по тому же правилу, что у подписей зданий: буква едет вместе с
     * жетоном, но через {@link #readableAngle}, чтобы не оказаться вверх ногами.
     * Кегль берётся от ШИРИНЫ жетона (0,42), поэтому у любого рода остаётся запас
     * до краёв — техника шире пехоты, и одинаковый кегль на ней смотрелся бы
     * мелко, а на вышке вылезал бы.
     */
    private static void paintUnitLetter(FieldCanvas c, ReplayRecord.Tok u, double[] pos,
                                        double w, double angle) {
        String s = unitLetter(u.type);
        if (s == null || s.isBlank()) {
            return;
        }
        // СТРОГО ПО ЦЕНТРУ ЖЕТОНА: точка (cx, cy) у outlinedTextRotated — это уже
        // середина надписи, и прежний сдвиг вниз на 0,14 ширины сбивал симметрию
        // (замечание дизайнера 13.08.2026).
        c.outlinedTextRotated(s, pos[0], pos[1], w * 0.42,
            WHITE, LABEL_OUTLINE, readableAngle(angle));
    }

    /** Значок «войско укрыто в своём здании» (§5.3 свода). */
    private static void paintHidden(FieldCanvas c, double size, ReplayRecord.Tok u,
                                    double x, double y) {
        double r = size * 0.115;
        c.circle(x, y, r, tone(FieldGeometry.SEAT_TOKEN[FieldGeometry.seatColor(u.owner)]), WHITE, 1.4);
        c.text(unitLetter(u.type), x, y + r * 0.62, r * 1.5, true, WHITE);
        // Урон укрытого войска значок не несёт — он и так мелкий; точные числа
        // даёт подсказка при наведении (проект «гнёзда прочности»).
    }

    private static void paintEnergy(FieldCanvas c, double size, boolean powered,
                                    double x, double y) {
        if (!showEnergy) {
            return;
        }
        double s = size * 0.095;
        c.roundRect(x - s / 2, y - s / 2, s, s, 0.8,
            powered ? ENERGY_ON : ENERGY_OFF, ENERGY_EDGE, 0.9);
    }

    /**
     * ПЕЧАТНЫЙ контейнер: нарисован НА ЯЧЕЙКЕ гекса, как на картонном блоке.
     * Наземная ячейка — у своей стенки, воздушная (6) — в центре гекса.
     * Кто встал на эту ячейку жетоном, тот берёт карту контейнера из запаса.
     */
    /**
     * ПЕЧАТНЫЙ КОНТЕЙНЕР на ячейке блока. Квадрат ПОВЁРНУТ по оси своей ячейки:
     * его стороны параллельны прямой стороне гекса, у которой он нарисован
     * (просьба дизайнера 12.08.2026 — как и кубики энергии, символ живёт в осях
     * жетона, а не поля). В центре знак вопроса: что внутри — неизвестно, пока
     * контейнер не вскрыт.
     */
    /**
     * ЖЁЛТАЯ ЯЧЕЙКА гекса — место, где энергостанция даёт свой номинал.
     *
     * <p>Рисуется ВНУТРЕННЕЙ ОБВОДКОЙ ПО ФОРМЕ САМОЙ ЯЧЕЙКИ и жёлтым знаком
     * молнии в середине. Линия чуть тоньше обводки жетона: это печать на
     * картоне, она лежит ПОД жетонами и по весу тоже — вдвое жирнее жетонов
     * она забивала собой всё поле (замечание дизайнера 16.08.2026).
     *
     * <p>Форма — ТРАПЕЦИЯ, а не клин до центра: в центре гекса лежит воздушная
     * ячейка авиации, и наземная ячейка её не занимает. Поэтому внутреннее
     * основание трапеции проходит по кромке воздушной ячейки, а не через центр.
     *
     * <p>Обводка именно внутренняя — трапеция ужата на полтолщины линии, чтобы
     * линия не вылезала ни за ребро гекса, ни на воздушную ячейку.
     */
    private static void paintEnergyCell(FieldCanvas c, double size, int cell,
                                        double cx, double cy) {
        double stroke = TOKEN_STROKE * 0.55;
        String mark = dark ? ENERGY_CELL_MARK_DARK : ENERGY_CELL_MARK;
        // ФОРМУ ДАЁТ ГЕОМЕТРИЯ, А НЕ ЭТОТ МЕТОД (перенесено 19.08.2026): ту же
        // трапецию рисует каталог блоков в конструкторе, и пока каждый считал её
        // сам, формы разъехались — в каталоге энергозона выродилась в круг.
        c.polygon(FieldGeometry.energyCellOutline(cx, cy, size, cell, stroke),
            "none", mark, stroke);

        // Молния — в середине трапеции, там же, где у здания стоит подпись.
        double[] spot = FieldGeometry.energyCellSpot(cx, cy, size, cell);
        paintBolt(c, spot[0], spot[1], size * 0.24, mark);
    }

    /** Знак молнии высотой {@code h} с центром в точке (x, y). */
    private static void paintBolt(FieldCanvas c, double x, double y, double h, String fill) {
        double w = h * 0.52;
        double[][] bolt = {
            {x + 0.10 * w, y - 0.50 * h},
            {x - 0.50 * w, y + 0.08 * h},
            {x - 0.06 * w, y + 0.08 * h},
            {x - 0.16 * w, y + 0.50 * h},
            {x + 0.50 * w, y - 0.12 * h},
            {x + 0.04 * w, y - 0.12 * h}
        };
        c.polygon(bolt, fill, null, 0);
    }

    private static void paintPrintedContainer(FieldCanvas c, double size, int cell,
                                              double cx, double cy) {
        double s = size * 0.24;
        double x = cx;
        double y = cy;
        double ang = 0;
        if (cell != 6) {
            ang = Math.toRadians(FieldGeometry.edgeAngle(cell));
            x = cx + size * 0.62 * Math.cos(ang);
            y = cy + size * 0.62 * Math.sin(ang);
        }
        // ФОРМУ (квадрат, повёрнутый по стороне) СЧИТАЕТ ГЕОМЕТРИЯ — тот же
        // квадрат нужен каталогу блоков, и держать вычисление в двух местах уже
        // однажды привело к расхождению (см. energyCellOutline).
        double[][] quad = FieldGeometry.containerCellQuad(cx, cy, size, cell, s);
        java.awt.image.BufferedImage tex = Textures.field("container");
        if (tex != null) {
            // Ячейка квадратная и повёрнута по своей стороне гекса — картинка
            // ложится тем же квадратом и тем же поворотом.
            c.image(tex, x, y, Math.toDegrees(ang), s / tex.getWidth(),
                tex.getWidth() / 2.0, tex.getHeight() / 2.0);
            return;
        }
        c.polygon(quad, CONTAINER_FILL, CONTAINER_EDGE, 1.4);
        c.text("?", x, y + s * 0.30, s * 0.82, true, CONTAINER_MARK);
    }

    private static void paintContainers(FieldCanvas c, double size, int count,
                                        double cx, double cy) {
        double s = size * 0.26;
        double off = count >= 2 ? size * 0.07 : 0;
        for (int i = Math.min(count, 2) - 1; i >= 0; i--) {
            double x = cx - s / 2 + (i == 0 ? -off : off);
            double y = cy - s / 2 + (i == 0 ? off : -off);
            c.roundRect(x, y, s, s, s * 0.28, CONTAINER_FILL, CONTAINER_EDGE, 1.6);
        }
    }

    // ==================== подписи ====================
    /** Короткий код здания на жетоне. */
    public static String buildingCode(String typeCode) {
        return switch (typeCode) {
            case "command_center" -> "ЦУ";
            case "factory" -> "Зв";
            case "airbase" -> "Ав";
            case "barracks" -> "Кз";
            case "miner" -> "Д";
            case "power_plant" -> "Э";
            default -> typeCode;
        };
    }

    /** Буква рода войск для значка укрытого жетона. */
    public static String unitLetter(String typeCode) {
        return switch (typeCode) {
            case "infantry" -> "П";
            case "vehicle" -> "Т";
            case "aircraft" -> "А";
            default -> "В";
        };
    }
}
