package kelium.dataio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import kelium.rules.Ruleset;

/**
 * Конфигурация партии: связывает набор правил + его контент + число игроков.
 *
 * <p>Единственный объект, который получают движок и запускающий код. Разрешает
 * версионируемый файл правил, загружает весь запрошенный правилами контент и
 * фиксирует выборы конкретного запуска (число игроков, зерно RNG, стороны досок).
 *
 * <p>Данные лежат в каталоге {@code data/} В КОРНЕ ПРОЕКТА (rulesets, cards,
 * boards, scenarios) — путь не дублируется по коду, его считает
 * {@link #resolveDataRoot(Path)}: сначала системное свойство {@code kelium.data},
 * иначе перебор кандидатов относительно рабочего каталога.
 */
public final class GameConfig {

    public final Ruleset ruleset;
    public final ContentLibrary content;
    public final int numPlayers;
    public final Long seed;
    public final Path dataRoot;
    // Стороны досок по местам, напр. ["A","B1","B2"]; null = авто Б1..Бn.
    public final java.util.List<String> boardSides;
    /**
     * Какую именно раскладку взять (id варианта из файла сценария, напр.
     * {@code field_4p_v2}). null — вариант выбирается по сиду, как раньше.
     */
    public final String scenarioId;
    /**
     * Стартовый ПОВОРОТ ЦУ по местам: номер стороны гекса 0..5, с которой
     * начинается пара занятых стенок (ЦУ занимает f и f+1). Элемент null или
     * весь список null — поворот подбирается автоматически, как раньше.
     */
    public final java.util.List<Integer> cuFacing;
    /**
     * Файл, из которого брать раскладку. null — искать среди авторских
     * ({@code scenarios/scenario_<N>p.<версия>.yaml}). Задаётся, когда поле
     * нарисовано конструктором и лежит в своей папке.
     */
    public final Path scenarioFile;

    /**
     * ПРАВКА ЗАПИСИ О ЖЕТОНАХ на один прогон (прочность, ячейки энергии, цены,
     * трофейные обороты). {@code null} — брать печатную запись из контента.
     *
     * <p>Зачем. Балансовые опыты правят не только набор правил, но и печатные
     * характеристики: «а если у авиабазы две ячейки энергии вместо трёх». Править
     * файл ради замера нельзя — он источник правды, а запись контента общая на
     * процесс, и правка «на месте» протекла бы во все остальные замеры прогона.
     * Поэтому сюда кладётся КОПИЯ записи с изменениями, ровно как
     * {@code Ruleset.copy().override(...)} для правил.
     */
    public java.util.Map<String, Object> tokenStatsOverride;

    /**
     * ЧТО ВЫБРАНО ЗА СТОЛОМ НА КАЖДОЕ МЕСТО: сторона планшета войск, сторона
     * планшета хранилища и цвет игрока (он же — его колода приказов). Элемент
     * {@code null} и весь список {@code null} — как раньше: стороны берутся из
     * правил, цвет раздаётся по сиду.
     */
    public java.util.List<SeatPick> seatPicks;

    /**
     * Выбор на одно место. Любое поле {@code null} — «не выбрано, решай сам».
     *
     * @param troopSide   сторона планшета ВОЙСК: «A», «B1»…«B4»
     * @param storageSide сторона планшета ХРАНИЛИЩА: «A», «B1»…«B4»
     * @param orderColor  цвет игрока и его колода приказов: «red», «blue», …
     */
    /**
     * ЧТО ИГРОК ВЫБРАЛ ЗА СВОИМ МЕСТОМ.
     *
     * <p>Сторон планшетов здесь больше нет: планшет один на всех (решение
     * дизайнера 09.09.2026, подтверждено как окончательное). Выбирают две вещи:
     * КОЛОДУ ПРИКАЗОВ (узор нижних приказов — главная асимметрия партии) и
     * ЦВЕТ МЕСТА (краска жетонов и планшетов).
     *
     * @param orderColor код колоды приказов ({@code blue}…) или {@code null} —
     *                   колода по умолчанию, то есть цвета этого места
     * @param colorSlot  гнездо краски 0..3 или {@code null} — краска по номеру
     *                   места
     */
    public record SeatPick(String orderColor, Integer colorSlot) {
        public boolean isEmpty() {
            return orderColor == null && colorSlot == null;
        }
    }

    /**
     * КОЛОДА ПРИКАЗОВ ПО УМОЛЧАНИЮ — та, что цвета самого места.
     *
     * <p>Заказ дизайнера 11.09.2026: «колода приказов всегда должна
     * соответствовать цвету игрока и его планшетов по умолчанию». Гнёзда
     * краски идут в том же порядке, в каком раздаются планшеты и жетоны:
     * 0 синий, 1 красный, 2 зелёный, 3 песочный.
     */
    public static String orderDeckOfColour(int colorSlot) {
        return switch (Math.floorMod(colorSlot, 4)) {
            case 0 -> "blue";
            case 1 -> "red";
            case 2 -> "green";
            default -> "yellow";
        };
    }

    /**
     * ПОВОРОТ ЦУ «РЕШАЕТ БОТ» — не сторона света, а признак.
     *
     * <p>Обычный поворот это 0..5. Отрицательное число стороной быть не может,
     * поэтому им и помечено «пусть решает тот, кто играет за это место»:
     * подготовка ставит ЦУ автоматом, а движок в начале партии переспрашивает
     * агента (см. {@code GameEngine.offerCuFacing}).
     */
    public static final int CU_FACING_BOT = -1;

    /** Гнездо краски места: выбранное игроком или, если не выбирали, номер места. */
    public static int colourOfSeat(int seat) {
        java.util.List<SeatPick> all = seatPickAll();
        SeatPick p = seat >= 0 && seat < all.size() ? all.get(seat) : null;
        return p == null || p.colorSlot() == null ? Math.floorMod(seat, 4) : p.colorSlot();
    }

    /** Выбор на место {@code seat} — или пустой, если ничего не выбирали. */
    public SeatPick seatPick(int seat) {
        if (seatPicks == null || seat < 0 || seat >= seatPicks.size()
                || seatPicks.get(seat) == null) {
            return new SeatPick(null, null);
        }
        return seatPicks.get(seat);
    }

    public GameConfig(Ruleset ruleset, ContentLibrary content, int numPlayers,
                      Long seed, Path dataRoot, java.util.List<String> boardSides) {
        this(ruleset, content, numPlayers, seed, dataRoot, boardSides, null, null, null);
    }

    public GameConfig(Ruleset ruleset, ContentLibrary content, int numPlayers,
                      Long seed, Path dataRoot, java.util.List<String> boardSides,
                      String scenarioId, java.util.List<Integer> cuFacing) {
        this(ruleset, content, numPlayers, seed, dataRoot, boardSides,
            scenarioId, cuFacing, null);
    }

    public GameConfig(Ruleset ruleset, ContentLibrary content, int numPlayers,
                      Long seed, Path dataRoot, java.util.List<String> boardSides,
                      String scenarioId, java.util.List<Integer> cuFacing,
                      Path scenarioFile) {
        if (numPlayers < 2 || numPlayers > 4) {
            throw new IllegalArgumentException("numPlayers должно быть 2..4, получено " + numPlayers);
        }
        this.ruleset = ruleset;
        this.content = content;
        this.numPlayers = numPlayers;
        this.seed = seed;
        this.dataRoot = dataRoot;
        this.boardSides = boardSides;
        this.scenarioId = scenarioId;
        this.cuFacing = cuFacing;
        this.scenarioFile = scenarioFile;
    }

    /** Определить каталог данных: свойство kelium.data или первый существующий кандидат. */
    public static Path resolveDataRoot(Path override) {
        if (override != null) {
            return override;
        }
        String prop = System.getProperty("kelium.data");
        if (prop != null && !prop.isEmpty()) {
            Path p = Paths.get(prop);
            if (Files.isDirectory(p)) {
                return p;
            }
            // ПАПКИ НЕТ — ЭТО НЕ ПОВОД ОСТАТЬСЯ БЕЗ ДАННЫХ. Сборщик exe
            // прибивает в свойство путь той машины, на которой собирали; на
            // чужом компьютере такой папки не существует, и приложение молча
            // оставалось без правил и карт. Своя копия лежит рядом с программой.
        }
        // ДАННЫЕ РЯДОМ С ПРОГРАММОЙ. В собранном приложении data лежит там же,
        // где его jar-ы, и это единственное надёжное место: рабочий каталог у
        // ярлыка может быть любым. При запуске из classes (разработка) здесь
        // ничего не находится, и всё идёт прежним путём — по кандидатам ниже.
        Path рядом = данныеРядомСПрограммой();
        if (рядом != null) {
            return рядом;
        }
        // КАНДИДАТЫ ОТНОСИТЕЛЬНО РАБОЧЕГО КАТАЛОГА. Данные лежат В КОРНЕ проекта
        // (`data/`), поэтому корневые варианты идут первыми: из корня это `data`,
        // из модуля при `mvn -pl engine` — `../data`.
        //
        // Хвост списка — прежняя раскладка `simulator/data` СНАРУЖИ проекта. Она
        // оставлена рабочей намеренно: тот же код запускается и из старой папки,
        // и ломать это на ровном месте незачем.
        String[] candidates = {
            "data", "../data", "../../data",
            "../simulator/data", "simulator/data", "../../simulator/data"
        };
        for (String c : candidates) {
            Path p = Paths.get(c);
            if (Files.isDirectory(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        // Ничего не нашлось — называем КОРНЕВОЙ вариант, чтобы сообщение об ошибке
        // указывало туда, где данные и должны лежать.
        return Paths.get("data").toAbsolutePath().normalize();
    }

    /**
     * ПАПКА {@code data} РЯДОМ С САМОЙ ПРОГРАММОЙ — либо {@code null}.
     *
     * <p>Ищется от того файла, из которого загружен этот класс: в собранном
     * приложении это jar в папке {@code app/}, и данные лежат там же
     * ({@code app/data}) либо на уровень выше. Так exe работает на ЛЮБОЙ машине,
     * а не только на той, где его собрали.
     */
    private static Path данныеРядомСПрограммой() {
        try {
            var src = GameConfig.class.getProtectionDomain().getCodeSource();
            if (src == null || src.getLocation() == null) {
                return null;
            }
            Path сам = Paths.get(src.getLocation().toURI());
            if (!сам.toString().endsWith(".jar")) {
                return null;         // запуск из classes — это разработка
            }
            for (Path base : new Path[]{сам.getParent(), сам.getParent().getParent()}) {
                if (base == null) {
                    continue;
                }
                Path d = base.resolve("data");
                if (Files.isDirectory(d)) {
                    return d.toAbsolutePath().normalize();
                }
            }
        } catch (RuntimeException | java.net.URISyntaxException e) {
            // Не смогли выяснить, где лежим — не беда, идём по кандидатам.
        }
        return null;
    }

    /** Собрать конфигурацию: загрузить правила по id и весь их контент из dataRoot. */
    public static GameConfig build(String rulesetId, int numPlayers, Long seed,
                                   Path dataRoot, java.util.List<String> boardSides) {
        Path root = resolveDataRoot(dataRoot);
        Ruleset ruleset = Ruleset.loadById(rulesetId, root.resolve("rulesets"));
        ContentLibrary content = ContentLibrary.forRuleset(ruleset, root);
        return new GameConfig(ruleset, content, numPlayers, seed, root, boardSides);
    }

    /** Собрать конфигурацию с настройками по умолчанию. */
    public static GameConfig build(int numPlayers, Long seed) {
        return build(DEFAULT_RULESET, numPlayers, seed, null, null);
    }

    /** То же с настройками по умолчанию, но правила и контент переиспользуются. */
    public static GameConfig buildCached(int numPlayers, Long seed) {
        return buildCached(DEFAULT_RULESET, numPlayers, seed, null, null);
    }

    /** Версия правил по умолчанию (её берут GUI и утилиты, если не сказано иное). */
    /**
     * СВОД ПРАВИЛ ПО УМОЛЧАНИЮ.
     *
     * <p>Переопределяется настройкой запуска {@code -Dkelium.ruleset=<id>}. Без
     * этого любой стенд играл ТОЛЬКО сводом по умолчанию, и сравнить две версии
     * правил можно было лишь пересборкой — из-за чего я один раз уже получил три
     * одинаковых замера для трёх разных сводов и чуть не сделал из этого вывод.
     */
    public static final String DEFAULT_RULESET =
        System.getProperty("kelium.ruleset", "1.40.0");

    // Кэш «правила + контент» по (версия, каталог данных): YAML читается один раз,
    // а не на каждую партию (на батче в тысячи партий это была главная статья
    // времени). Содержимое только читается: колоды строит Setup отдельным объектом.
    private static final java.util.Map<String, Object[]> CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * ВЫБРАННЫЕ НА ПАРТИЮ ВЕРСИИ КАРТОЧНЫХ НАБОРОВ: тип набора → версия.
     *
     * <p>Обычно версию колоды называют правила ({@code content_versions}), и это
     * правильно: набор карт — часть редакции правил. Но дизайнеру нужно сыграть те
     * же правила НА ДРУГИХ ЗАДАНИЯХ или другом арсенале, чтобы сравнить колоды
     * между собой (просьба 13.08.2026). Здесь и лежит этот выбор: пусто — всё как
     * в правилах, иначе названный тип берётся указанной версией.
     *
     * <p>Выбор общий на процесс, а не параметр каждого вызова: он задаётся один раз
     * перед партией и не должен протаскиваться через десяток перегрузок play().
     * Прогоны из командной строки его не трогают и получают версии из правил.
     */
    private static final java.util.Map<String, String> CONTENT_PICK =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Выбрать версию карточного набора на следующие партии (null — как в правилах). */
    public static void pickContentVersion(String contentType, String version) {
        if (version == null || version.isBlank()) {
            CONTENT_PICK.remove(contentType);
        } else {
            CONTENT_PICK.put(contentType, version);
        }
    }

    /** Что сейчас выбрано вручную (пусто — всё по правилам). */
    public static java.util.Map<String, String> contentPick() {
        return java.util.Map.copyOf(CONTENT_PICK);
    }

    private static String contentKey() {
        return new java.util.TreeMap<>(CONTENT_PICK).toString();
    }

    /**
     * ВЫБОР ЗА СТОЛОМ — так же, как выбор колод: задаётся один раз перед партией
     * и не тащится через десяток перегрузок play(). Прогоны из командной строки
     * его не трогают и играют как раньше.
     */
    private static final java.util.List<SeatPick> SEAT_PICK =
        java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /** Выбрать место: стороны планшетов и цвет (любой аргумент null — «сам»). */
    public static void pickSeat(int seat, String orderColor, Integer colorSlot) {
        synchronized (SEAT_PICK) {
            while (SEAT_PICK.size() <= seat) {
                SEAT_PICK.add(null);
            }
            SeatPick p = new SeatPick(orderColor, colorSlot);
            SEAT_PICK.set(seat, p.isEmpty() ? null : p);
        }
    }

    /** Что сейчас выбрано за столом (копия). */
    public static java.util.List<SeatPick> seatPickAll() {
        synchronized (SEAT_PICK) {
            return new java.util.ArrayList<>(SEAT_PICK);
        }
    }

    /** Как {@link #build}, но правила и контент переиспользуются между партиями. */
    public static GameConfig buildCached(String rulesetId, int numPlayers, Long seed,
                                         Path dataRoot, java.util.List<String> boardSides) {
        return buildCached(rulesetId, numPlayers, seed, dataRoot, boardSides, null, null);
    }

    /** То же, но с явным выбором раскладки и стартового поворота ЦУ по местам. */
    public static GameConfig buildCached(String rulesetId, int numPlayers, Long seed,
                                         Path dataRoot, java.util.List<String> boardSides,
                                         String scenarioId, java.util.List<Integer> cuFacing) {
        return buildCached(rulesetId, numPlayers, seed, dataRoot, boardSides,
            scenarioId, cuFacing, null);
    }

    /** То же плюс ФАЙЛ раскладки (поле, нарисованное конструктором). */
    public static GameConfig buildCached(String rulesetId, int numPlayers, Long seed,
                                         Path dataRoot, java.util.List<String> boardSides,
                                         String scenarioId, java.util.List<Integer> cuFacing,
                                         Path scenarioFile) {
        Path root = resolveDataRoot(dataRoot);
        // ВЫБРАННЫЕ ВРУЧНУЮ КОЛОДЫ входят в ключ кэша: иначе партия на других
        // заданиях получила бы набор, загруженный для прошлой (см. contentPick).
        String key = rulesetId + "@" + root + "#" + contentKey();
        Object[] rc = CACHE.computeIfAbsent(key, k -> {
            Ruleset rs = Ruleset.loadById(rulesetId, root.resolve("rulesets"));
            for (java.util.Map.Entry<String, String> e : CONTENT_PICK.entrySet()) {
                rs.override("content_versions." + e.getKey(), e.getValue());
            }
            return new Object[]{rs, ContentLibrary.forRuleset(rs, root)};
        });
        // Правила отдаём КОПИЕЙ: разобранный YAML общий на процесс, а
        // Ruleset.override правит его на месте — без копии эксперимент в одной
        // партии протёк бы во все остальные.
        GameConfig cfg = new GameConfig(((Ruleset) rc[0]).copy(), (ContentLibrary) rc[1],
            numPlayers, seed, root, boardSides, scenarioId, cuFacing, scenarioFile);
        cfg.seatPicks = seatPickAll();
        return cfg;
    }

    /**
     * ЗАБЫТЬ ЗАГРУЖЕННЫЕ ПРАВИЛА И КОНТЕНТ.
     *
     * <p>Нужно ровно в одном случае: человек поменял ПАПКУ ДАННЫХ в окне настроек.
     * Ключ кэша содержит путь, поэтому новая папка и так попала бы в новую запись,
     * но старая осталась бы висеть в памяти вместе с прочитанными файлами — а
     * главное, при возврате на прежний путь отдалась бы копия, прочитанная ДО
     * того, как человек починил данные. Поэтому после смены папки кэш чистится.
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /** Список доступных версий правил (файлы {@code rulesets/*.yaml}). */
    public static java.util.List<String> availableRulesets(Path dataRoot) {
        Path dir = resolveDataRoot(dataRoot).resolve("rulesets");
        java.util.List<String> out = new java.util.ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".yaml"))
                 .map(p -> p.getFileName().toString().replace(".yaml", ""))
                 .sorted(VersionOrder.ASC)
                 .forEach(out::add);
            } catch (java.io.IOException ignored) {
                // каталог нечитаем — вернём пустой список, вызывающий подставит дефолт
            }
        }
        return out;
    }

    /** Человекочитаемое описание конфигурации (правила, игроки, контент, TODO дизайнера). */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("ruleset:   ").append(ruleset.id).append('\n');
        sb.append("players:   ").append(numPlayers).append('\n');
        sb.append("seed:      ").append(seed).append('\n');
        sb.append("content:\n");
        for (var e : content.summary().entrySet()) {
            sb.append("  ").append(String.format("%-18s", e.getKey())).append(e.getValue()).append('\n');
        }
        var todos = ruleset.designerTodo();
        if (!todos.isEmpty()) {
            sb.append("designer TODO (правила финализирует только дизайнер):\n");
            for (String t : todos) {
                sb.append("  - ").append(t).append('\n');
            }
        }
        return sb.toString();
    }
}
