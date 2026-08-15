package kelium.rules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Загрузчик версионируемого, заменяемого НАБОРА ПРАВИЛ (ruleset).
 *
 * <p>Набор правил — совокупность значений правил и их вариантов
 * (см. {@code data/rulesets/*.yaml}). Незыблемое ядро читает свои настраиваемые
 * числа и переключатели через экземпляр {@code Ruleset} по точечным путям
 * ({@code get("actions.combat.surcharge_model")}), поэтому баланс меняется
 * заменой YAML-файла, но никогда кода.
 *
 * <p>Неизвестный ключ (без переданного значения по умолчанию) вызывает ошибку —
 * опечатка в файле правил падает громко, а не молча возвращает null.
 */
public final class Ruleset {

    /** Маркер «значение по умолчанию не задано» для {@link #get(String)}. */
    private static final Object MISSING = new Object();

    /** Ошибка: файл правил повреждён или отсутствует обязательный ключ. */
    public static final class RulesetError extends RuntimeException {
        public RulesetError(String msg) {
            super(msg);
        }
    }

    public final String id;
    public final Map<String, Object> raw;
    public final Path sourcePath;

    public Ruleset(String id, Map<String, Object> raw, Path sourcePath) {
        this.id = id;
        this.raw = raw;
        this.sourcePath = sourcePath;
    }

    /**
     * Получить значение по точечному пути, напр. "actions.combat.retaliation_enabled".
     * Без {@code hasDefault} отсутствующий ключ бросает {@link RulesetError}.
     */
    private Object getRaw(String dotted, boolean hasDefault, Object def) {
        Object node = raw;
        for (String part : dotted.split("\\.")) {
            if (!(node instanceof Map<?, ?> m) || !m.containsKey(part)) {
                if (hasDefault) {
                    return def;
                }
                throw new RulesetError("отсутствует ключ ruleset: " + dotted);
            }
            node = ((Map<?, ?>) m).get(part);
        }
        return node;
    }

    /** Значение по точечному пути; ошибка, если ключа нет. */
    public Object get(String dotted) {
        return getRaw(dotted, false, null);
    }

    /** Значение по точечному пути с запасным значением, если ключа нет. */
    public Object get(String dotted, Object def) {
        return getRaw(dotted, true, def);
    }

    /**
     * Переопределить значение по точечному пути (настройка партии/эксперимента,
     * например выключить супер-задания). Создаёт промежуточные словари.
     */
    @SuppressWarnings("unchecked")
    /**
     * ГЛУБОКАЯ КОПИЯ правил. Нужна потому, что {@code GameConfig.buildCached}
     * раздаёт разобранный YAML всем партиям процесса, а {@link #override}
     * правит его на месте: без копии один эксперимент («сыграем без
     * супер-заданий») молча отравил бы весь остальной батч.
     */
    public Ruleset copy() {
        return new Ruleset(id, deepCopy(raw), sourcePath);
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopy(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) m).entrySet()) {
                out.put(String.valueOf(e.getKey()), deepCopy(e.getValue()));
            }
            return out;
        }
        if (v instanceof java.util.List<?> l) {
            java.util.List<Object> out = new java.util.ArrayList<>(l.size());
            for (Object o : l) {
                out.add(deepCopy(o));
            }
            return out;
        }
        return v;                      // числа, строки, булевы — неизменяемые
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> m) {
        return (Map<String, Object>) deepCopy((Object) m);
    }

    public void override(String dotted, Object value) {
        String[] parts = dotted.split("\\.");
        java.util.Map<String, Object> node = raw;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = node.get(parts[i]);
            if (!(next instanceof java.util.Map)) {
                next = new java.util.HashMap<String, Object>();
                node.put(parts[i], next);
            }
            node = (java.util.Map<String, Object>) next;
        }
        node.put(parts[parts.length - 1], value);
    }

    /** Целое значение по точечному пути. */
    public int getInt(String dotted) {
        return ((Number) get(dotted)).intValue();
    }

    /** Целое значение по точечному пути с запасным значением. */
    public int getInt(String dotted, int def) {
        Object v = get(dotted, MISSING);
        return v == MISSING || v == null ? def : ((Number) v).intValue();
    }

    /** Булево значение по точечному пути с запасным значением. */
    public boolean getBool(String dotted, boolean def) {
        Object v = get(dotted, MISSING);
        return v == MISSING || v == null ? def : Boolean.TRUE.equals(v);
    }

    /** Строковое значение по точечному пути с запасным значением. */
    public String getStr(String dotted, String def) {
        Object v = get(dotted, MISSING);
        return v == MISSING || v == null ? def : v.toString();
    }

    /** Список целых по точечному пути (например, расписание наценок [0,1]). */
    @SuppressWarnings("unchecked")
    public List<Integer> getIntList(String dotted) {
        List<Object> list = (List<Object>) get(dotted);
        List<Integer> out = new ArrayList<>();
        for (Object o : list) {
            out.add(o == null ? null : ((Number) o).intValue());
        }
        return out;
    }

    /** Общий бонус к HP для всех жетонов (вариант «+1 HP всем»). */
    public int tokenHpBonusAll() {
        return getInt("asymmetry.token_hp_bonus_all", getInt("token_hp_bonus_all", 0));
    }

    /** Пространство имён economy как карта. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> economy() {
        return (Map<String, Object>) raw.get("economy");
    }

    /** Пространство имён asymmetry как карта. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> asymmetry() {
        return (Map<String, Object>) raw.get("asymmetry");
    }

    /**
     * Вместимость шагов науки для заданного числа игроков (null-элемент = без
     * лимита на этом шаге).
     */
    @SuppressWarnings("unchecked")
    public List<Integer> stepCapacity(int numPlayers) {
        // ТРЕКИ 2.1 (12.08.2026): ячейки шага описаны поимённо — для каждой задан
        // МИНИМАЛЬНЫЙ состав, при котором она открыта («4И», «3+И» на планшете).
        // Ёмкость шага для этого стола = сколько ячеек открыто.
        List<List<Integer>> cells = stepCells();
        if (!cells.isEmpty()) {
            List<Integer> out = new ArrayList<>();
            for (List<Integer> step : cells) {
                int open = 0;
                for (Integer min : step) {
                    if (min == null || numPlayers >= min) {
                        open++;
                    }
                }
                out.add(open);
            }
            return out;
        }
        Object by = get("tech.step_capacity_by_players", MISSING);
        if (by != MISSING && by instanceof Map<?, ?> byMap) {
            Object row = byMap.get(String.valueOf(numPlayers));
            if (row != null) {
                List<Integer> out = new ArrayList<>();
                for (Object o : (List<Object>) row) {
                    out.add(o == null ? null : ((Number) o).intValue());
                }
                return out;
            }
        }
        return getIntList("tech.step_capacity");
    }

    /**
     * ЯЧЕЙКИ ШАГОВ ТРЕКА: для каждого шага список ячеек, в каждой — минимальный
     * состав, при котором ячейка открыта. Пусто — в этой версии правил ячейки не
     * описаны, работает прежняя {@code tech.step_capacity}.
     */
    @SuppressWarnings("unchecked")
    public List<List<Integer>> stepCells() {
        Object raw = get("tech.step_cells", MISSING);
        List<List<Integer>> out = new ArrayList<>();
        if (raw == MISSING || !(raw instanceof List<?> steps)) {
            return out;
        }
        for (Object so : steps) {
            List<Integer> cells = new ArrayList<>();
            if (so instanceof List<?> list) {
                for (Object o : list) {
                    cells.add(o instanceof Number n ? n.intValue() : 2);
                }
            }
            out.add(cells);
        }
        return out;
    }

    /** Загрузить набор правил из YAML-файла по пути и провалидировать его. */
    @SuppressWarnings("unchecked")
    public static Ruleset load(Path p) {
        if (!Files.exists(p)) {
            throw new RulesetError("файл правил не найден: " + p);
        }
        Map<String, Object> data;
        try (InputStream in = Files.newInputStream(p)) {
            data = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RulesetError("ошибка чтения правил " + p + ": " + e.getMessage());
        }
        if (data == null) {
            throw new RulesetError("правила " + p + " не разобрались в отображение");
        }
        Map<String, Object> meta = (Map<String, Object>) data.getOrDefault("meta", Map.of());
        Object rid = meta.get("id");
        String id = rid != null ? rid.toString() : fileStem(p);
        Ruleset rs = new Ruleset(id, data, p);
        rs.validate();
        return rs;
    }

    /** Найти и загрузить набор правил по его id среди файлов *.yaml в каталоге. */
    @SuppressWarnings("unchecked")
    public static Ruleset loadById(String rulesetId, Path rulesetsDir) {
        Path candidate = rulesetsDir.resolve(rulesetId + ".yaml");
        if (Files.exists(candidate)) {
            return load(candidate);
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(rulesetsDir, "*.yaml")) {
            for (Path f : ds) {
                Map<String, Object> data;
                try (InputStream in = Files.newInputStream(f)) {
                    data = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
                if (data != null && data.get("meta") instanceof Map<?, ?> meta
                        && rulesetId.equals(((Map<String, Object>) meta).get("id"))) {
                    return load(f);
                }
            }
        } catch (IOException e) {
            throw new RulesetError("ошибка обхода каталога правил " + rulesetsDir + ": " + e.getMessage());
        }
        throw new RulesetError("нет набора правил с id " + rulesetId + " в " + rulesetsDir);
    }

    private static String fileStem(Path p) {
        String n = p.getFileName().toString();
        int dot = n.indexOf('.');
        return dot >= 0 ? n.substring(0, dot) : n;
    }

    /** Громко упасть при структурных проблемах, которые сломали бы движок. */
    public void validate() {
        String[] required = {"economy", "rounds", "actions", "tech", "return_step", "content_versions"};
        List<String> missing = new ArrayList<>();
        for (String k : required) {
            if (!raw.containsKey(k)) {
                missing.add(k);
            }
        }
        if (!missing.isEmpty()) {
            throw new RulesetError("в наборе правил " + id + " нет секций: " + missing);
        }
        int steps = getInt("tech.steps_per_track");
        for (String key : new String[]{"step_cost_trophy", "step_vp_cumulative"}) {
            List<Integer> seq = getIntList("tech." + key);
            if (seq.size() != steps) {
                throw new RulesetError("tech." + key + " имеет " + seq.size()
                        + " элементов, ожидалось steps_per_track=" + steps);
            }
        }
        Object by = get("tech.step_capacity_by_players", MISSING);
        if (by != MISSING && by instanceof Map<?, ?> rows) {
            for (Map.Entry<?, ?> e : rows.entrySet()) {
                List<?> seq = (List<?>) e.getValue();
                if (seq.size() != steps) {
                    throw new RulesetError("tech.step_capacity_by_players[" + e.getKey()
                            + "] имеет " + seq.size() + " элементов, ожидалось " + steps);
                }
            }
        }
    }

    /** Вернуть точечные пути, помеченные _needs_designer_update: true. */
    public List<String> designerTodo() {
        List<String> todos = new ArrayList<>();
        walk(raw, "", todos);
        return todos;
    }

    private static void walk(Object node, String prefix, List<String> todos) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                if (k.equals("_needs_designer_update") && Boolean.TRUE.equals(e.getValue())) {
                    todos.add(prefix.isEmpty() ? "<root>" : prefix);
                } else {
                    walk(e.getValue(), prefix.isEmpty() ? k : prefix + "." + k, todos);
                }
            }
        }
    }
}
