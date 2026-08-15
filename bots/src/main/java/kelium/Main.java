package kelium;

import java.io.PrintWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import kelium.agents.HeuristicAgent;
import kelium.agents.RandomAgent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.report.GameLogger;
import kelium.core.Agent;

/**
 * Консольная точка входа: гоняет ОДНУ партию и в реальном времени пишет её лог
 * ОДНОВРЕМЕННО в консоль и в файл, затем завершается. Главный артефакт — файл
 * лога партии.
 *
 * <p>Использование:
 * <pre>
 *   java -cp target/classes kelium.Main [опции]
 *   опции:
 *     --players N        число игроков 2..4 (по умолчанию 4)
 *     --seed S           сид ГСЧ (по умолчанию 42)
 *     --agent A          тип ботов: random | heuristic | explorer | chaos |
 *                        personality:aggressor,defender,economist,... (по умолч. heuristic)
 *     --lang ru|en       язык лога (по умолчанию ru)
 *     --out PATH         путь к файлу лога (по умолчанию reports/gamelogs[_ru]/game_...log)
 *     --quiet            не дублировать лог в консоль (только в файл)
 * </pre>
 *
 * <p>Лог пишется потоково: каждая строка сразу уходит и на диск, и в консоль —
 * видно, как партия идёт «вживую».
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Windows-консоль часто cp1251 — печатаем через UTF-8, чтобы кириллица
        // отображалась корректно (файл и так пишется в UTF-8).
        PrintStream stdout = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        // --- разбор аргументов ---
        Map<String, String> opt = parseArgs(args);
        int players = clampPlayers(intOpt(opt, "players", 4));
        long seed = longOpt(opt, "seed", 42L);
        String agentSpec = opt.getOrDefault("agent", "heuristic");
        String lang = opt.getOrDefault("lang", "ru");
        boolean quiet = opt.containsKey("quiet");

        // --- сборка партии ---
        // Версия правил задаётся ключом --ruleset (раньше ключ молча игнорировался
        // и партия всегда шла на версии по умолчанию).
        String rulesetId = opt.getOrDefault("ruleset", GameConfig.DEFAULT_RULESET);
        GameConfig config = GameConfig.buildCached(rulesetId, players, seed, null, null);
        GameState state = Setup.buildGame(config);
        List<Agent> agents = makeAgents(agentSpec, players, seed);

        // --- логгер: файл + живое зеркало в консоль ---
        Path logDir = Path.of("reports", "ru".equals(lang) ? "gamelogs_ru" : "gamelogs");
        Path logPath = opt.containsKey("out")
            ? Path.of(opt.get("out"))
            : GameLogger.defaultLogPath(state, logDir);

        GameLogger logger = new GameLogger(state, logPath, lang);
        if (!quiet) {
            logger.withEcho(new PrintWriter(stdout, true));
        }
        Consumer<Map<String, Object>> onEvent = logger::record;

        // краткая шапка запуска в консоль (в файле своя шапка)
        stdout.println("Forged in Kelium — партия: игроков=" + players
            + " сид=" + seed + " боты=" + agentSpec + " лог=" + logPath);

        // --- прогон партии (лог льётся в реальном времени) ---
        Map<String, Object> result = GameEngine.playGame(state, agents, onEvent);

        // --- финальная строка в консоль ---
        int winner = (Integer) result.get("winner");
        stdout.println();
        stdout.println("Готово. Победитель: игрок " + winner
            + " | условие: " + result.get("condition")
            + " | лог сохранён: " + logPath.toAbsolutePath());
    }

    // ---------------------------------------------------------------------
    //  Вспомогательные
    // ---------------------------------------------------------------------

    /**
     * Собрать ботов по спецификации: random | heuristic | personality:a,b,c |
     * strategic[:genomePath]. Нейросетевая ветка удалена 13.08.2026: она не
     * проверялась в лиге и играла слабее обученного генома.
     */
    private static List<Agent> makeAgents(String spec, int players, long seed) {
        List<Agent> agents = new ArrayList<>();
        if (spec.startsWith("strategic")) {
            kelium.agents.Genome genome;
            int colon = spec.indexOf(':');
            if (colon >= 0) {
                try {
                    genome = kelium.agents.Genome.loadJson(java.nio.file.Paths.get(spec.substring(colon + 1)));
                } catch (Exception e) {
                    System.err.println("не удалось загрузить геном, беру дефолтный: " + e.getMessage());
                    genome = kelium.agents.Genome.defaults();
                }
            } else {
                genome = kelium.agents.Genome.defaults();
            }
            // Все места — стратеги с РАЗНЫМИ характерами (непредсказуемый стол:
            // соперники не знают, чего ждать). Профили накладываются на геном.
            String[] profiles = {"hawk", "opportunist", "dove", "balanced"};
            for (int s = 0; s < players; s++) {
                kelium.agents.Genome g = genome.withProfile(profiles[s % profiles.length]);
                agents.add(new kelium.agents.StrategicAgent(s, new Random(seed * 1000L + s), g));
            }
            return agents;
        }
        // Характерные боты «Исследователь» и «Хаос»: занимают место 0, остальные —
        // эвристики. Explorer пробует максимум механик за партию; Chaos вредит всем.
        if ("explorer".equals(spec) || "chaos".equals(spec)) {
            agents.add(makeCharacter(spec, 0, new Random(seed * 1000L + 1)));
            String[] chars = {"aggressor", "defender", "economist", "aggressor"};
            for (int s = 1; s < players; s++) {
                agents.add(new HeuristicAgent(s, new Random(seed * 1000L + s), chars[s % chars.length]));
            }
            return agents;
        }
        if (spec.startsWith("personality:")) {
            String[] names = spec.substring("personality:".length()).split(",");
            for (int s = 0; s < players; s++) {
                agents.add(new HeuristicAgent(s, new Random(seed * 1000L + s),
                    names[s % names.length].trim()));
            }
        } else if ("random".equals(spec)) {
            for (int s = 0; s < players; s++) {
                agents.add(new RandomAgent(s, new Random(seed * 1000L + s)));
            }
        } else { // heuristic (по умолчанию) — раздаём разные характеры по кругу
            String[] chars = {"aggressor", "defender", "economist", "aggressor"};
            for (int s = 0; s < players; s++) {
                agents.add(new HeuristicAgent(s, new Random(seed * 1000L + s),
                    chars[s % chars.length]));
            }
        }
        return agents;
    }

    /**
     * Мини-фабрика характерных ботов по строке: {@code explorer} | {@code chaos}.
     * Прочее — сбалансированный стратег.
     */
    public static Agent makeCharacter(String kind, int seat, Random rng) {
        // характер = линия генома; прошитых характеров нет с 12.08.2026
        return kelium.agents.Bots.CHARACTERS.contains(kind)
            ? kelium.agents.Bots.create(kind, seat, rng, 4)
            : new kelium.agents.StrategicAgent(seat, rng, kelium.agents.Genome.defaults());
    }

    /** Разобрать аргументы вида {@code --key value} и флаги {@code --flag}. */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    m.put(key, args[++i]);
                } else {
                    m.put(key, "true");   // флаг без значения
                }
            }
        }
        return m;
    }

    private static int intOpt(Map<String, String> o, String k, int def) {
        try { return o.containsKey(k) ? Integer.parseInt(o.get(k)) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private static long longOpt(Map<String, String> o, String k, long def) {
        try { return o.containsKey(k) ? Long.parseLong(o.get(k)) : def; }
        catch (NumberFormatException e) { return def; }
    }

    private static int clampPlayers(int n) {
        return Math.max(2, Math.min(4, n));
    }
}
