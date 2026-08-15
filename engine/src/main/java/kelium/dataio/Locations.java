package kelium.dataio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Locations — ЕДИНОЕ место, где все приложения проекта договариваются, что где
 * лежит: раскладки полей и память ботов (геномы и модели нейросетей).
 *
 * <p>Раньше каждое приложение считало пути само (везде было
 * {@code dataRoot.resolve("genomes")}), а раскладки вообще искались только
 * среди авторских файлов. Из-за этого поле, нарисованное конструктором, видел
 * один проигрыватель, а прогоны — нет. Теперь путь один и настраивается один
 * раз: настройки хранятся в {@link Preferences} пользователя, поэтому переживают
 * перезапуск и не требуют файлов рядом с exe.
 *
 * <p>Значения по умолчанию — как было: {@code <данные>/scenarios} и
 * {@code <данные>/genomes}. Каталог данных по-прежнему определяет
 * {@link GameConfig#resolveDataRoot(Path)}.
 */
public final class Locations {

    private Locations() {
    }

    private static final String KEY_LAYOUTS = "layout-folders";
    private static final String KEY_MEMORY = "bot-memory-folder";
    /** Папка данных игры, выбранная человеком в окне настроек. */
    private static final String KEY_DATA = "data-folder";
    // РАЗДЕЛИТЕЛЬ СПИСКА ПАПОК. Раньше был перевод строки, но настройки переехали
    // в текстовый файл, где перевод строки заканчивает запись, — список из двух
    // папок читался как одна с мусором. Точка с запятой в путях Windows не
    // встречается (это разделитель PATH), поэтому подходит.
    private static final String SEP = ";";

    private static final AppSettings SET = AppSettings.of("paths");

    /**
     * ПЕРЕНОС ИЗ РЕЕСТРА. Прежде пути лежали в {@link Preferences}; настройки
     * переехали в {@code kelium.cfg}, но у человека на машине уже подключены свои
     * папки раскладок — терять их нельзя. Переносим один раз, при первом чтении.
     */
    static {
        migrateOnce();
    }

    private static void migrateOnce() {
        try {
            if (!SET.get(KEY_LAYOUTS, "").isBlank() || !SET.get(KEY_MEMORY, "").isBlank()) {
                return;                       // уже переносили или уже настроено
            }
            Preferences p = Preferences.userNodeForPackage(Locations.class);
            String old = p.get(KEY_LAYOUTS, "");
            if (!old.isBlank()) {
                SET.put(KEY_LAYOUTS, String.join(SEP, old.split("\n")));
            }
            String mem = p.get(KEY_MEMORY, "");
            if (!mem.isBlank()) {
                SET.put(KEY_MEMORY, mem);
            }
        } catch (RuntimeException e) {
            // реестр недоступен — начинаем с настроек по умолчанию
        }
    }

    // ==================== папка данных игры ====================

    /**
     * ПАПКА ДАННЫХ, ВЫБРАННАЯ РУКАМИ (правила, карты, раскладки, текстуры). Пусто —
     * ищется как раньше: {@code -Dkelium.data}, затем рядом с программой.
     *
     * <p>Ради этой настройки всё и затевалось: когда приложение «поехало» и
     * перестало находить данные, показать ему нужное место должно быть можно
     * ПРЯМО В ОКНЕ, а не пересборкой exe (жалоба дизайнера 14.08.2026).
     */
    public static Path dataFolder() {
        String s = SET.get(KEY_DATA, "");
        return s.isBlank() ? null : Paths.get(s);
    }

    /** Задать папку данных; null — вернуться к поиску по умолчанию. */
    public static void setDataFolder(Path dir) {
        SET.put(KEY_DATA, dir == null ? null : dir.toAbsolutePath().normalize().toString());
        applyDataFolder();
        GameConfig.clearCache();
    }

    /**
     * Применить выбранную папку данных к текущему запуску.
     *
     * <p>{@link GameConfig#resolveDataRoot} читает системное свойство
     * {@code kelium.data} — через него настройка и доходит до всех, кто грузит
     * контент, не требуя протаскивать путь через каждый вызов. Вызывать в самом
     * начале {@code main}, ДО первой загрузки правил.
     */
    public static void applyDataFolder() {
        Path p = dataFolder();
        if (p != null && Files.isDirectory(p)) {
            System.setProperty("kelium.data", p.toString());
        }
    }

    private static AppSettings prefs() {
        return SET;
    }

    // ==================== раскладки полей ====================

    /** Папка авторских раскладок — она в списке всегда и не убирается. */
    public static Path builtinLayoutFolder() {
        return GameConfig.resolveDataRoot(null).resolve("scenarios");
    }

    /** Все папки, где ищутся раскладки: сначала авторская, затем добавленные. */
    public static List<Path> layoutFolders() {
        List<Path> out = new ArrayList<>();
        out.add(builtinLayoutFolder());
        for (String s : prefs().get(KEY_LAYOUTS, "").split(SEP)) {
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            // ОДНА ПОРЧЕННАЯ ЗАПИСЬ НЕ ДОЛЖНА ВАЛИТЬ ВСЁ ПРИЛОЖЕНИЕ. Список папок
            // хранится в текстовом файле настроек, который могут одновременно
            // писать несколько запущенных приложений (гонка при записи), и мусор
            // в одной строке раньше приводил к InvalidPathException прямо при
            // старте окна — приложение падало без единого шанса открыться и
            // починить список самому (баг найден дизайнером 14.08.2026: exe не
            // открывался вовсе). Плохая запись просто пропускается.
            Path p;
            try {
                p = Paths.get(t);
            } catch (java.nio.file.InvalidPathException e) {
                continue;
            }
            if (!out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Папки, добавленные пользователем (их можно убрать). */
    public static List<Path> userLayoutFolders() {
        List<Path> all = new ArrayList<>(layoutFolders());
        all.remove(builtinLayoutFolder());
        return all;
    }

    /** Добавить папку с раскладками (повтор игнорируется). */
    public static void addLayoutFolder(Path dir) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Path p : userLayoutFolders()) {
            set.add(p.toString());
        }
        if (dir != null && !dir.equals(builtinLayoutFolder())) {
            set.add(dir.toAbsolutePath().normalize().toString());
        }
        prefs().put(KEY_LAYOUTS, String.join(SEP, set));
    }

    /** Убрать папку с раскладками из списка (сами файлы не трогаются). */
    public static void removeLayoutFolder(Path dir) {
        List<String> keep = new ArrayList<>();
        for (Path p : userLayoutFolders()) {
            if (!p.equals(dir)) {
                keep.add(p.toString());
            }
        }
        prefs().put(KEY_LAYOUTS, String.join(SEP, keep));
    }

    // ==================== память ботов ====================

    /** Где по умолчанию лежит память ботов. */
    public static Path defaultBotMemory() {
        return GameConfig.resolveDataRoot(null).resolve("genomes");
    }

    /**
     * ПАМЯТЬ БОТОВ — папка с геномами стратегов и моделями нейросетей
     * ({@code strategic_<N>p.json}, {@code neural_<N>p.txt},
     * {@code policy_<N>p.onnx}). Отсюда читают и прогоны, и проигрыватель, и
     * обучение — место одно.
     */
    public static Path botMemory() {
        // ПЕРЕОПРЕДЕЛЕНИЕ НА ОДИН ЗАПУСК: -Dkelium.botmemory=путь. Нужно, чтобы
        // сравнивать два обучения между собой — каждое пишет чемпионов в свою
        // папку и они не затирают друг друга. В отличие от настройки в реестре
        // это НЕ переживает перезапуск и потому не может тихо испортить будущие
        // прогоны (а такое уже случалось).
        String prop = System.getProperty("kelium.botmemory", "");
        if (!prop.isBlank()) {
            Path p = Paths.get(prop);
            try {
                Files.createDirectories(p);
            } catch (java.io.IOException e) {
                System.out.println("[ПАМЯТЬ БОТОВ] не создал папку " + p + ": "
                    + e.getMessage());
            }
            if (Files.isDirectory(p)) {
                warnCustomOnce(p);
                return p;
            }
        }
        String custom = prefs().get(KEY_MEMORY, "");
        if (!custom.isBlank()) {
            Path p = Paths.get(custom);
            if (Files.isDirectory(p)) {
                warnCustomOnce(p);
                return p;
            }
        }
        return defaultBotMemory();
    }

    private static boolean warned = false;

    /**
     * ОДИН РАЗ громко сказать, что память ботов взята не из обычного места.
     *
     * <p>Зачем. Настройка живёт в реестре пользователя и переживает перезапуск,
     * поэтому она может быть выставлена когда-то давно и молча ломать всё: если
     * она указывает в папку-АРХИВ, то обучение читает старые геномы и пишет новые
     * в бэкап, а прогоны играют не тем, чем кажется. Один раз это уже случилось
     * (память была направлена в {@code archive-pre-selfplay}), и заметить это было
     * нельзя ничем, кроме случайного взгляда на путь в логе.
     */
    private static void warnCustomOnce(Path p) {
        if (warned) {
            return;
        }
        warned = true;
        System.out.println("[ПАМЯТЬ БОТОВ] используется НЕ обычная папка: " + p);
        System.out.println("[ПАМЯТЬ БОТОВ] обычная: " + defaultBotMemory()
            + " — сбросить: java kelium.dataio.Locations reset");
    }

    /**
     * CLI: {@code kelium.dataio.Locations [reset]} — показать, где сейчас лежит
     * память ботов, и при желании вернуть путь к обычному.
     */
    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        // add <папка> — подключить папку с раскладками к библиотеке. Нужно из
        // командной строки, а не только из окна: обучение и замеры берут поля из
        // библиотеки, и папку со свежими полями надо уметь подключить одной строкой.
        if (args.length > 1 && "add".equals(args[0])) {
            Path dir = Paths.get(args[1]).toAbsolutePath().normalize();
            addLayoutFolder(dir);
            System.out.println("подключена папка раскладок: " + dir);
            for (Path p : layoutFolders()) {
                System.out.println("  в библиотеке: " + p);
            }
            return;
        }
        boolean reset = args.length > 0 && "reset".equals(args[0]);
        String custom = prefs().get(KEY_MEMORY, "");
        System.out.println("обычная папка памяти ботов: " + defaultBotMemory());
        System.out.println("настроенная вручную:        "
            + (custom.isBlank() ? "(нет)" : custom));
        if (reset) {
            setBotMemory(null);
            System.out.println("СБРОШЕНО: память ботов снова берётся из обычной папки.");
        }
        System.out.println("действует сейчас:           " + botMemory());
    }

    /** Задать свою папку памяти ботов; null или пустой путь — вернуть к обычной. */
    public static void setBotMemory(Path dir) {
        if (dir == null) {
            prefs().remove(KEY_MEMORY);
        } else {
            prefs().put(KEY_MEMORY, dir.toAbsolutePath().normalize().toString());
        }
    }

    /** Файл памяти ботов по имени, например {@code strategic_4p.json}. */
    public static Path botMemoryFile(String name) {
        return botMemory().resolve(name);
    }
}
