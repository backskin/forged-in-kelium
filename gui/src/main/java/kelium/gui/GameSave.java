package kelium.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kelium.dataio.GameConfig;
import kelium.report.Json;

/**
 * СОХРАНЁННАЯ ПАРТИЯ — настройки стола и лента принятых решений.
 *
 * <p>Слепка состояния здесь нет намеренно. Движок воспроизводим: тот же сид, та
 * же раскладка и те же решения дают ту же партию. Значит достаточно хранить
 * НАСТРОЙКИ и НОМЕРА ВЫБРАННЫХ ОПЦИЙ по порядку — файл выходит крошечным, не
 * ржавеет от новых полей состояния и заодно даёт полный журнал: загруженная
 * партия проигрывается с первого хода.
 *
 * <p><b>Цена решения, о которой надо знать.</b> Сохранение привязано к ВЕРСИИ
 * ПРАВИЛ и наборов карт. Поменялись правила — прежняя лента решений означает
 * уже другую игру, и такое сохранение {@link #checkCompatible} отвергает вслух,
 * а не доигрывает молча не ту партию.
 */
public final class GameSave {

    /** Куда кладутся сохранения. */
    public static final Path FOLDER = Path.of("reports", "saves");

    private static final String FORMAT = "kelium-save-1";

    public final String name;
    public final HotSeatWindow.Options options;
    public final List<Integer> moves;
    public final String rulesetId;
    public final Map<String, String> contentVersions;
    /** Когда сохранено — человеческой строкой, для списка в меню. */
    public final String saved;
    /** Раунд и круг на момент сохранения — чтобы список говорил, докуда доиграли. */
    public final int round;
    public final int circle;

    public GameSave(String name, HotSeatWindow.Options options, List<Integer> moves,
                     String rulesetId, Map<String, String> contentVersions,
                     String saved, int round, int circle) {
        this.name = name;
        this.options = options;
        this.moves = List.copyOf(moves);
        this.rulesetId = rulesetId;
        this.contentVersions = Map.copyOf(contentVersions);
        this.saved = saved;
        this.round = round;
        this.circle = circle;
    }

    /** Наборы карт этой партии — по ним сверяется пригодность сохранения. */
    public static Map<String, String> contentVersionsOf(GameConfig cfg) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : List.of("orders", "objectives", "arsenal", "market", "containers",
                "boards", "scenarios", "modules", "super_objectives", "super_arsenal")) {
            String v = cfg.ruleset.getStr("content_versions." + key, null);
            if (v != null) {
                out.put(key, v);
            }
        }
        return out;
    }

    /**
     * Годится ли сохранение к нынешним правилам. Возвращает {@code null}, если
     * годится, иначе — человеческую причину, почему нет.
     */
    public String checkCompatible() {
        GameConfig cfg;
        try {
            cfg = GameConfig.buildCached(rulesetId, options.players(), options.seed(), null, null);
        } catch (RuntimeException e) {
            return "свод правил " + rulesetId + " больше не читается: " + e.getMessage();
        }
        Map<String, String> now = contentVersionsOf(cfg);
        for (Map.Entry<String, String> e : contentVersions.entrySet()) {
            String v = now.get(e.getKey());
            if (v != null && !v.equals(e.getValue())) {
                return "набор «" + e.getKey() + "» был версии " + e.getValue()
                    + ", а сейчас " + v + " — по такому сохранению партия будет уже другой";
            }
        }
        return null;
    }

    // ==================== файл ====================

    public void save(Path file) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("name", name);
        m.put("saved", saved);
        m.put("round", round);
        m.put("circle", circle);
        m.put("ruleset", rulesetId);
        m.put("contentVersions", contentVersions);
        m.put("players", options.players());
        m.put("seed", options.seed());
        m.put("seatSpecs", options.seatSpecs());
        m.put("scenarioId", options.scenarioId());
        m.put("scenarioFile", options.scenarioFile() == null ? null
            : options.scenarioFile().toString());
        m.put("cuFacing", options.cuFacing());
        m.put("seatColors", options.seatColors());
        m.put("startCoins", options.startCoins());
        m.put("startKelium", options.startKelium());
        m.put("startAmmo", options.startAmmo());
        m.put("moves", moves);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, Json.write(m), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    public static GameSave load(Path file) throws IOException {
        Object parsed = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> raw)) {
            throw new IOException("не сохранение партии: " + file.getFileName());
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        if (!FORMAT.equals(String.valueOf(m.get("format")))) {
            throw new IOException("сохранение другого формата: " + m.get("format"));
        }
        List<Integer> moves = new ArrayList<>();
        for (Object o : Json.list(m, "moves")) {
            moves.add(o instanceof Number n ? n.intValue() : 0);
        }
        Map<String, String> versions = new LinkedHashMap<>();
        if (m.get("contentVersions") instanceof Map<?, ?> cv) {
            for (Map.Entry<?, ?> e : cv.entrySet()) {
                versions.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        String scenarioFile = m.get("scenarioFile") == null ? null
            : String.valueOf(m.get("scenarioFile"));
        HotSeatWindow.Options opts = new HotSeatWindow.Options(
            String.valueOf(m.get("ruleset")),
            Json.i(m, "players"),
            m.get("seed") instanceof Number sn ? sn.longValue() : 0L,
            Json.strings(m, "seatSpecs"),
            m.get("scenarioId") == null ? null : String.valueOf(m.get("scenarioId")),
            scenarioFile == null ? null : Path.of(scenarioFile),
            nums(m, "cuFacing"), nums(m, "seatColors"),
            num(m, "startCoins"), num(m, "startKelium"), num(m, "startAmmo"));
        return new GameSave(String.valueOf(m.get("name")), opts, moves,
            String.valueOf(m.get("ruleset")), versions,
            String.valueOf(m.get("saved")), Json.i(m, "round"), Json.i(m, "circle"));
    }

    private static List<Integer> nums(Map<String, Object> m, String key) {
        List<Object> raw = Json.list(m, key);
        if (raw.isEmpty()) {
            return null;
        }
        List<Integer> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            out.add(o instanceof Number n ? n.intValue() : null);
        }
        return out;
    }

    private static Integer num(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.intValue() : null;
    }

    /** Все сохранения на диске, свежие сверху. */
    public static List<GameSave> all() {
        List<GameSave> out = new ArrayList<>();
        if (!Files.isDirectory(FOLDER)) {
            return out;
        }
        try (var files = Files.list(FOLDER)) {
            for (Path f : files.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .toList()) {
                try {
                    out.add(load(f));
                } catch (IOException | RuntimeException e) {
                    // Испорченное сохранение не должно ронять меню — просто не покажем
                }
            }
        } catch (IOException e) {
            return out;
        }
        out.sort((a, b) -> b.saved.compareTo(a.saved));
        return out;
    }

    /** Имя файла для сохранения с таким названием. */
    public static Path fileFor(String name) {
        String safe = name.replaceAll("[^\\p{L}\\p{N} _-]", "_").trim();
        return FOLDER.resolve((safe.isEmpty() ? "партия" : safe) + ".kelium-save.json");
    }

    /** Строка для списка в меню: докуда доиграли и когда сохранено. */
    public String describe() {
        return "раунд " + round + " · круг " + circle + " · " + saved;
    }
}
