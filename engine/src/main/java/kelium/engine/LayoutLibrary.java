package kelium.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kelium.dataio.Locations;

/**
 * LayoutLibrary — БИБЛИОТЕКА РАСКЛАДОК: папки, в которых проигрыватель ищет
 * поля, нарисованные конструктором.
 *
 * <p>Зачем. Движок сам по себе читает только авторские файлы вида
 * {@code scenarios/scenario_<N>p.<версия>.yaml}. Поле, сохранённое конструктором
 * под своим именем, в прогон не попадало. Библиотека обходит указанные папки,
 * читает каждый YAML и отдаёт список раскладок с числом игроков — проигрыватель
 * показывает их в списке «поле» наравне с авторскими.
 *
 * <p>Список папок — общий для всех приложений проекта, он живёт в
 * {@link Locations}. Каталог {@code simulator/data/scenarios} в списке всегда:
 * там лежат авторские раскладки.
 */
public final class LayoutLibrary {

    private LayoutLibrary() {
    }

    /** Одна найденная раскладка. */
    public record Entry(String id, int players, Path file, String folder) {
        /** Подпись для выпадающего списка. */
        public String label() {
            return id + "  ·  " + file.getFileName();
        }
    }

    /** Папка авторских раскладок (см. {@link Locations}). */
    public static Path builtinFolder() {
        return Locations.builtinLayoutFolder();
    }

    /** Папки библиотеки: авторская плюс добавленные пользователем. */
    public static List<Path> folders() {
        return Locations.layoutFolders();
    }

    /** Папки, добавленные пользователем (их можно убрать). */
    public static List<Path> userFolders() {
        return Locations.userLayoutFolders();
    }

    /** Добавить папку в библиотеку. */
    public static void addFolder(Path dir) {
        Locations.addLayoutFolder(dir);
    }

    /** Убрать папку из библиотеки. */
    public static void removeFolder(Path dir) {
        Locations.removeLayoutFolder(dir);
    }

    // ==================================================================
    //  НАБОР ПОЛЕЙ ДЛЯ ЗАМЕРОВ И ОБУЧЕНИЯ
    // ==================================================================

    /**
     * ПАПКИ, НА КОТОРЫХ МЕРЯЕМ И УЧИМ. Решение дизайнера 13.08.2026: только его
     * нарисованные раскладки — то есть добавленные папки (обычно
     * {@code scenarios/new}), а встроенные авторские поля в замеры не идут.
     *
     * <p>Почему это важно, а не вкусовщина: геометрия меняет игру сильнее любых
     * боевых правил (разброс уничтоженных жетонов между полями — 140%). Мерить на
     * полях, на которых не собираются играть, значит настраивать баланс не той
     * игры. До этой правки половина стендов молча играла на встроенном поле.
     *
     * <p>Если добавленных папок нет — берём всё, что есть, иначе стенды просто
     * перестали бы работать на свежей машине. Настройкой запуска
     * {@code -Dkelium.layouts=все} можно вернуть прежнее поведение.
     */
    public static List<Path> poolFolders() {
        if ("все".equalsIgnoreCase(System.getProperty("kelium.layouts", ""))) {
            return folders();
        }
        List<Path> own = userFolders();
        return own.isEmpty() ? folders() : own;
    }

    // Список читается ОДИН раз на процесс: обучение делает сотни тысяч партий, и
    // лезть на диск в каждой — чистая потеря времени.
    private static final java.util.Map<Integer, List<Entry>> POOL =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Раскладки для замеров и обучения на {@code players} игроков. */
    public static List<Entry> pool(int players) {
        return POOL.computeIfAbsent(players, n -> {
            List<Entry> out = new ArrayList<>();
            for (Path dir : poolFolders()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                List<Path> files = new ArrayList<>();
                try (var s = Files.list(dir)) {
                    // Тот же отбор, что в scan: только файлы поля (.kmap и старые
                    // .yaml), иначе в набор полезет README и прочее соседнее.
                    files = s.filter(p -> Files.isRegularFile(p)
                            && kelium.dataio.FieldFile.isField(p))
                        .sorted().toList();
                } catch (java.io.IOException ignored) {
                    continue;
                }
                for (Path f : files) {
                    readFile(f, dir, n, out, null);
                }
            }
            return out;
        });
    }

    /** Одной строкой: на чём меряем — для шапки любого отчёта. */
    public static String describePool(int players) {
        List<Entry> p = pool(players);
        if (p.isEmpty()) {
            return "встроенное поле (нарисованных раскладок не найдено)";
        }
        java.util.Set<String> folders = new java.util.LinkedHashSet<>();
        for (Entry e : p) {
            folders.add(e.folder());
        }
        return "раскладок " + p.size() + " из " + folders;
    }

    /**
     * Настройка партии с раскладкой ИЗ НАБОРА, выбранной по сиду. Один и тот же
     * сид всегда даёт одно и то же поле — иначе замеры нельзя повторить.
     */
    public static kelium.dataio.GameConfig configFor(int players, long seed) {
        kelium.dataio.GameConfig base = kelium.dataio.GameConfig.buildCached(
            kelium.dataio.GameConfig.DEFAULT_RULESET, players, seed, null, null);
        return configFor(base, players, seed);
    }

    /** То же, но на основе готовой настройки (когда в ней уже правлены правила). */
    public static kelium.dataio.GameConfig configFor(kelium.dataio.GameConfig base,
                                                     int players, long seed) {
        List<Entry> all = pool(players);
        if (all.isEmpty()) {
            return base;
        }
        Entry e = all.get((int) Math.floorMod(seed, all.size()));
        kelium.dataio.GameConfig cfg = new kelium.dataio.GameConfig(base.ruleset,
            base.content, players, seed, base.dataRoot, base.boardSides,
            e.id(), base.cuFacing, e.file());
        cfg.tokenStatsOverride = base.tokenStatsOverride;
        return cfg;
    }

    /**
     * Найти все раскладки на {@code players} игроков во всех папках библиотеки.
     * Файлы, которые не читаются или не являются раскладкой, ПРОПУСКАЮТСЯ, а
     * причина уходит в {@code problems} — молча терять их нельзя, но и падать
     * из-за чужого YAML в папке тоже незачем.
     *
     * @param problems куда складывать сообщения о непрочитанных файлах (может быть null)
     */
    public static List<Entry> scan(int players, List<String> problems) {
        List<Entry> out = new ArrayList<>();
        for (Path dir : folders()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files = new ArrayList<>();
            try (var s = Files.list(dir)) {
                // свой формат .kmap и старые .yaml — см. kelium.dataio.FieldFile
                s.filter(p -> Files.isRegularFile(p)
                        && kelium.dataio.FieldFile.isField(p))
                    .sorted().forEach(files::add);
            } catch (java.io.IOException e) {
                if (problems != null) {
                    problems.add("папка не читается: " + dir);
                }
                continue;
            }
            for (Path f : files) {
                readFile(f, dir, players, out, problems);
            }
        }
        return out;
    }

    private static void readFile(Path file, Path dir, int players,
                                 List<Entry> out, List<String> problems) {
        List<Map<String, Object>> variants;
        try {
            variants = Scenario.loadVariantsFromFile(file);
        } catch (RuntimeException e) {
            if (problems != null) {
                problems.add(file.getFileName() + ": " + e.getMessage());
            }
            return;
        }
        for (Map<String, Object> v : variants) {
            String id = String.valueOf(v.getOrDefault("id",
                file.getFileName().toString().replaceAll("\\.ya?ml$", "")));
            int seats;
            try {
                seats = Scenario.buildFieldFromScenario(v).starts().size();
            } catch (RuntimeException e) {
                if (problems != null) {
                    problems.add(file.getFileName() + " / " + id + ": " + e.getMessage());
                }
                continue;
            }
            if (seats == 0) {
                // Частая беда: поле нарисовано, а стартовые гексы игроков не
                // расставлены — движок такую раскладку принять не может.
                if (problems != null) {
                    problems.add(file.getFileName() + " / " + id
                        + ": не расставлены стартовые гексы игроков");
                }
                continue;
            }
            if (seats != players) {
                continue;                    // раскладка на другое число игроков
            }
            out.add(new Entry(id, seats, file, dir.getFileName() == null
                ? dir.toString() : dir.getFileName().toString()));
        }
    }
}
