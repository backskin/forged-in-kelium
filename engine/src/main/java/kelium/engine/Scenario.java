package kelium.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import kelium.core.Field;
import kelium.core.Hex;
import kelium.core.HexKind;

/**
 * Загрузчик сценариев — построение настоящего гексового поля из версионного файла.
 *
 * <p>Разбирает {@code data/scenarios/scenario_<N>p.<version>.yaml} (осевые
 * координаты q,r с плоским верхом, содержимое гекса, заблокированные рёбра) в
 * {@link Field}. Соседство выводится из осевых координат (6 направлений
 * {@link Field#AXIAL_DIRS}); индекс i направления = номер стороны гекса.
 */
public final class Scenario {

    private Scenario() {
    }

    /** Ошибка загрузки/разбора файла сценария. */
    public static final class ScenarioError extends RuntimeException {
        public ScenarioError(String msg) {
            super(msg);
        }
    }

    private static String hexId(int q, int r) {
        return "h" + q + "_" + r;
    }

    /**
     * Развернуть компактную форму сценария (shape + special в координатах
     * «ряд/столбец») в явный список гексов с осевыми q,r. Если у сценария уже
     * есть явный список {@code hexes} (старый формат) — вернуть его как есть.
     *
     * <p>Модель координат дизайнера: ряды сверху вниз (1..N), колонки абсолютные
     * по общей сетке; НЕЧЁТНЫЕ (1-баз.) ряды смещены на пол-гекса вправо
     * (pointy-top, offset «even-r» в 0-базе). Перевод в axial:
     * {@code r = row0; q = col0 - (row0 + (row0 & 1)) / 2}, где row0=row-1,
     * col0=col-1. Чётность ПОДТВЕРЖДЕНА коррекцией дизайнера по 2p v1
     * (2026-08-10): с его отступами [2,1,0,1,2] стартовые грядки примыкают к
     * стартам обоих игроков симметрично.
     *
     * <p>Элемент {@code shape} — одно из двух:
     * <ul>
     *   <li>число W — ряд из W гексов, начинается с колонки 1 (старый формат);</li>
     *   <li>карта {@code {offset: N, count: M}} — ряд из M гексов с ОТСТУПОМ:
     *       занимает колонки N+1..N+M общей сетки. Колонки в {@code special}
     *       тогда АБСОЛЮТНЫЕ (по общей сетке, не от начала ряда).</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static List<Object> expandedHexes(Map<String, Object> scenario) {
        Object explicit = scenario.get("hexes");
        if (explicit instanceof List<?> list && !list.isEmpty()) {
            return (List<Object>) explicit;
        }
        Object shapeObj = scenario.get("shape");
        if (!(shapeObj instanceof List<?> shape)) {
            throw new ScenarioError("сценарий без 'hexes' и без 'shape'");
        }
        // особые гексы по ключу row*1000+col (col — абсолютная колонка сетки)
        Map<Integer, Map<String, Object>> special = new HashMap<>();
        Object specObj = scenario.get("special");
        if (specObj instanceof List<?> specList) {
            for (Object o : specList) {
                Map<String, Object> e = (Map<String, Object>) o;
                int row = ((Number) e.get("row")).intValue();
                int col = ((Number) e.get("col")).intValue();
                special.put(row * 1000 + col, e);
            }
        }
        // нейтральные здания — ОТДЕЛЬНЫЙ слой поверх любого гекса (в т.ч. с
        // контентом); на одном гексе их может быть несколько
        Map<Integer, List<Map<String, Object>>> neutrals = new HashMap<>();
        Object neuObj = scenario.get("neutrals");
        if (neuObj instanceof List<?> neuList) {
            for (Object o : neuList) {
                Map<String, Object> e = (Map<String, Object>) o;
                int row = ((Number) e.get("row")).intValue();
                int col = ((Number) e.get("col")).intValue();
                neutrals.computeIfAbsent(row * 1000 + col,
                    k -> new java.util.ArrayList<>()).add(e);
            }
        }
        Map<Integer, Map<String, Object>> unused = new HashMap<>(special);
        Map<Integer, List<Map<String, Object>>> unusedNeu = new HashMap<>(neutrals);
        List<Object> out = new java.util.ArrayList<>();
        for (int row = 1; row <= shape.size(); row++) {
            int offset = 0;
            int width;
            Object rowSpec = shape.get(row - 1);
            if (rowSpec instanceof Map<?, ?> m) {
                offset = intOr(m.get("offset"), 0);
                width = intOr(m.get("count"), -1);
                if (width < 0) {
                    throw new ScenarioError("shape ряд " + row + ": нет 'count'");
                }
            } else {
                width = ((Number) rowSpec).intValue();
            }
            for (int col = offset + 1; col <= offset + width; col++) {
                Map<String, Object> base = special.get(row * 1000 + col);
                unused.remove(row * 1000 + col);
                Map<String, Object> hx = base != null
                    ? new HashMap<>(base) : new HashMap<>();
                List<Map<String, Object>> neu = neutrals.get(row * 1000 + col);
                if (neu != null) {
                    unusedNeu.remove(row * 1000 + col);
                    hx.put("neutral_list", neu);
                }
                int row0 = row - 1;
                int col0 = col - 1;
                int q = col0 - (row0 + (row0 & 1)) / 2;   // even-r: нечётные ряды правее
                int r = row0;
                hx.put("q", q);
                hx.put("r", r);
                out.add(hx);
            }
        }
        // Особый гекс вне формы — раньше молча выпадал (терялись старты!),
        // теперь это громкая ошибка транскрипции.
        List<Map<String, Object>> outside = new java.util.ArrayList<>(unused.values());
        for (List<Map<String, Object>> lst : unusedNeu.values()) {
            outside.addAll(lst);
        }
        if (!outside.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> e : outside) {
                sb.append(" (ряд ").append(e.get("row")).append(", кол ")
                  .append(e.get("col")).append(": ")
                  .append(e.getOrDefault("content", "нейтрал")).append(')');
            }
            throw new ScenarioError("сценарий '" + scenario.get("id")
                + "': особые гексы вне границ формы —" + sb);
        }
        return out;
    }

    /** Результат сборки поля: граф + соответствие {seat -> id стартового гекса}. */
    public record FieldWithStarts(Field field, Map<Integer, String> starts) {
    }

    /**
     * Загрузить YAML-файл сценария для данного числа игроков и версии, вернув
     * первый сценарий из списка. Бросает {@link ScenarioError}, если файла нет.
     */
    public static Map<String, Object> loadScenario(int numPlayers, String version, Path dataRoot) {
        return loadScenario(numPlayers, version, dataRoot, null);
    }

    /**
     * Загрузить раскладку. Если в файле несколько вариантов (список scenarios),
     * выбирается один: по {@code variantSeed} (случайно, но воспроизводимо) или
     * первый, если сид не задан. Так один файл держит несколько раскладок на
     * данное число игроков — их случайный выбор поднимает реиграбельность.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadScenario(int numPlayers, String version, Path dataRoot,
                                                   Long variantSeed) {
        Path path = dataRoot.resolve("scenarios")
            .resolve("scenario_" + numPlayers + "p." + version + ".kmap");
        if (!Files.exists(path)) {
            throw new ScenarioError("файл сценария не найден: " + path);
        }
        Map<String, Object> data;
        try (InputStream in = Files.newInputStream(path)) {
            data = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScenarioError("ошибка чтения " + path + ": " + e.getMessage());
        }
        Object scns = data.get("scenarios");
        if (!(scns instanceof List<?> list) || list.isEmpty()) {
            throw new ScenarioError(path + " не содержит списка 'scenarios'");
        }
        int idx = 0;
        if (variantSeed != null && list.size() > 1) {
            idx = Math.floorMod(variantSeed, list.size());
        }
        return (Map<String, Object>) list.get(idx);
    }

    /** Вернуть ВСЕ варианты раскладки из файла (для батч-рендера/проверки). */
    public static List<Map<String, Object>> loadAllVariants(int numPlayers, String version,
                                                            Path dataRoot) {
        return loadVariantsFromFile(dataRoot.resolve("scenarios")
            .resolve("scenario_" + numPlayers + "p." + version + ".kmap"));
    }

    /**
     * Вернуть все варианты раскладки из ПРОИЗВОЛЬНОГО файла. Нужно, чтобы
     * подхватывать поля, нарисованные конструктором и сохранённые куда угодно,
     * а не только в файлы вида {@code scenario_<N>p.<версия>.yaml}.
     */
    /**
     * Кэш РАЗОБРАННЫХ файлов раскладок по пути и времени изменения. YAML читался
     * заново на каждую партию — на батче в тысячи партий это было заметно, а
     * файл между партиями не меняется. Правка файла на диске кэш сбрасывает.
     */
    private static final Map<String, List<Map<String, Object>>> FILE_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> loadVariantsFromFile(Path path) {
        if (!Files.exists(path)) {
            throw new ScenarioError("файл раскладки не найден: " + path);
        }
        String key;
        try {
            key = path.toAbsolutePath() + "@" + Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            key = path.toAbsolutePath().toString();
        }
        List<Map<String, Object>> cached = FILE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Map<String, Object> data;
        try (InputStream in = Files.newInputStream(path)) {
            data = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScenarioError("ошибка чтения " + path + ": " + e.getMessage());
        }
        if (data == null) {
            throw new ScenarioError(path + " пуст");
        }
        Object scns = data.get("scenarios");
        if (!(scns instanceof List<?> list) || list.isEmpty()) {
            throw new ScenarioError(path + " не содержит списка 'scenarios'");
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        if (out.isEmpty()) {
            throw new ScenarioError(path + ": в списке 'scenarios' нет ни одной раскладки");
        }
        FILE_CACHE.put(key, out);
        return out;
    }

    /**
     * Вернуть (Field, {seat: id стартового гекса}). Соседство выводится из
     * осевых координат; учитываются тайлы зарождения, контейнеры, нейтральные
     * здания, модификаторы грядок (+1/x2) и заблокированные рёбра.
     */
    @SuppressWarnings("unchecked")
    public static FieldWithStarts buildFieldFromScenario(Map<String, Object> scenario) {
        Field f = new Field();
        Map<Integer, String> starts = new HashMap<>();
        Map<Long, String> coords = new HashMap<>();
        int[] neutralUid = {-1000};   // отрицательные uid — не пересекаются с жетонами

        List<Object> hexes = expandedHexes(scenario);
        for (Object hxObj : hexes) {
            Map<String, Object> hx = (Map<String, Object>) hxObj;
            int q = ((Number) hx.get("q")).intValue();
            int r = ((Number) hx.get("r")).intValue();
            String hid = hexId(q, r);
            String contentType = (String) hx.getOrDefault("content", "normal");
            HexKind kind = HexKind.NORMAL;
            kelium.core.SpawnTile tile = null;      // жетон-тайл зарождения
            List<Hex.NeutralBuilding> neutralList = new java.util.ArrayList<>();
            // контейнеры на поле отменены (Контейнеры 2.0)

            switch (contentType) {
                case "forbidden" -> kind = HexKind.FORBIDDEN;
                case "player_start" -> {
                    kind = HexKind.START;
                    starts.put(((Number) hx.get("seat")).intValue(), hid);
                }
                case "spawn_start" -> tile = new kelium.core.SpawnTile(true,
                    intOr(hx.get("face_kelium"), 3), intOr(hx.get("back_kelium"), 2), 1);
                case "kelium_tile" -> tile = new kelium.core.SpawnTile(false,
                    intOr(hx.get("face_kelium"), 4), intOr(hx.get("back_kelium"), 3), 1);
                case "container" -> { }   // отменено: контейнеры печатные
                case "neutral_building" ->
                    // старый формат: нейтрал как content (size + corners в записи)
                    neutralList.add(new Hex.NeutralBuilding(neutralUid[0]--,
                        "big".equals(hx.get("size")), cornerList(hx.get("corners"))));
                default -> {
                    // normal — по умолчанию
                }
            }

            // Нейтралы отдельным слоем (neutrals:) — поверх любого контента,
            // на гексе их может быть несколько.
            Object neuListObj = hx.get("neutral_list");
            if (neuListObj instanceof List<?> nl) {
                for (Object o : nl) {
                    Map<String, Object> ne = (Map<String, Object>) o;
                    neutralList.add(new Hex.NeutralBuilding(neutralUid[0]--,
                        "big".equals(ne.getOrDefault("size", "small")),
                        cornerList(ne.get("corners"))));
                }
            }

            Object mod = hx.get("modifier");
            if (tile != null) {
                // СТАРЫЙ формат (одна строка): "+1" / "-1" / "x2". Правка лица
                // ложится туда же, куда и новая: на ЛИЦО ВЕРХНЕГО тайла.
                int legacy = 0;
                if ("+1".equals(mod)) {
                    legacy = 1;
                } else if ("-1".equals(mod)) {
                    legacy = -1;
                } else if ("x2".equals(mod)) {
                    tile.stack = 2;
                }
                if (legacy != 0) {
                    tile.applyTopFaceDelta(legacy);
                }
                // НОВЫЙ формат (независимые поля, пишет конструктор раскладок):
                // stack: 1|2 — сколько тайлов в стопке (двойной тайл зарождения);
                // kelium_delta: -4..+4 — правка келемия НА ЛИЦЕ ВЕРХНЕГО тайла.
                // Ни оборот этого тайла, ни второй тайл стопки она не трогает:
                // напечатанное на картоне правкой раскладки не меняется.
                int stack = intOr(hx.get("stack"), 0);
                if (stack > 0) {
                    tile.stack = stack;
                }
                tile.applyTopFaceDelta(intOr(hx.get("kelium_delta"), 0));
            }

            Hex h = new Hex(hid);
            h.kind = kind;
            h.sectors = intOr(hx.get("sectors"), 6);
            h.spawnTile = tile;
            h.neutrals.addAll(neutralList);
            // §12.3: стенки нейтралов ЗАНИМАЮТ стороны гекса — блокировка
            // прохода/застройки по-сторонняя, а не по-гексовая.
            for (Hex.NeutralBuilding nb : neutralList) {
                h.occupySides(nb.uid, nb.wallSides());
            }

            f.addHex(h);
            coords.put(key(q, r), hid);
        }

        // Соседство из осевых координат: индекс i = сторона гекса к соседу i.
        for (Object hxObj : hexes) {
            Map<String, Object> hx = (Map<String, Object>) hxObj;
            int q = ((Number) hx.get("q")).intValue();
            int r = ((Number) hx.get("r")).intValue();
            String hid = hexId(q, r);
            for (int side = 0; side < Field.AXIAL_DIRS.length; side++) {
                int dq = Field.AXIAL_DIRS[side][0];
                int dr = Field.AXIAL_DIRS[side][1];
                String nb = coords.get(key(q + dq, r + dr));
                if (nb != null) {
                    f.link(hid, nb, side);
                }
            }
        }

        // Явно заблокированные рёбра (стенки/ворота).
        Object beObj = scenario.get("blocked_edges");
        if (beObj instanceof List<?> beList) {
            for (Object beo : beList) {
                Map<String, Object> be = (Map<String, Object>) beo;
                String a = hexId(((Number) be.get("q")).intValue(), ((Number) be.get("r")).intValue());
                String b = hexId(((Number) be.get("to_q")).intValue(), ((Number) be.get("to_r")).intValue());
                if (f.hexes.containsKey(a) && f.hexes.containsKey(b)) {
                    f.hexes.get(a).neighbors.remove(b);
                    f.hexes.get(b).neighbors.remove(a);
                    // G2: разорвать и neighborBySide — иначе sidesFacing /
                    // «примыкание стенкой» видят соседа через разорванное ребро
                    Hex ha = f.hexes.get(a);
                    Hex hb = f.hexes.get(b);
                    for (int i = 0; i < 6; i++) {
                        if (b.equals(ha.neighborBySide[i])) {
                            ha.neighborBySide[i] = null;
                        }
                        if (a.equals(hb.neighborBySide[i])) {
                            hb.neighborBySide[i] = null;
                        }
                    }
                }
            }
        }

        return new FieldWithStarts(f, starts);
    }

    private static int intOr(Object o, int def) {
        return o == null ? def : ((Number) o).intValue();
    }

    // Список углов нейтрала из YAML (может быть null).
    private static List<Integer> cornerList(Object o) {
        if (!(o instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Integer> out = new java.util.ArrayList<>();
        for (Object c : list) {
            out.add(((Number) c).intValue());
        }
        return out;
    }

    // Упаковка координат (q,r) в long-ключ, устойчивую к отрицательным значениям.
    private static long key(int q, int r) {
        return (((long) q) << 32) ^ (r & 0xffffffffL);
    }
}
