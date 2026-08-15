package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.HeuristicAgent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.report.GameLogger;
import kelium.core.Agent;

/**
 * Пакетный прогон множества партий (одна JVM, без живого вывода): разные составы
 * (2/3/4 игрока) и сиды, каждая партия пишет ПОЛНЫЙ лог в файл (ru + en).
 *
 * <p>Логи складываются в {@code reports/batch_logs/} (en) и
 * {@code reports/batch_logs_ru/} (ru), по одному файлу на партию. В конце —
 * краткая сводка в консоль.
 *
 * <p>Аргументы: {@code --games N} (всего партий, по умолчанию 99, делится поровну
 * между составами 2/3/4). Характеры ботов раздаются по кругу
 * (aggressor/defender/economist).
 */
public final class RunMany {

    private RunMany() {
    }

    private static final String[] CHARS = {"aggressor", "defender", "economist"};

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        int total = 99;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--games".equals(args[i])) {
                try { total = Integer.parseInt(args[i + 1]); } catch (NumberFormatException ignored) { }
            }
        }
        int perCount = total / 3;   // поровну на 2/3/4 игрока

        Path enDir = Path.of("reports", "batch_logs");
        Path ruDir = Path.of("reports", "batch_logs_ru");

        int played = 0;
        long totalCombat = 0;
        long totalDestroyed = 0;
        int[] winsByCount = new int[5];        // индекс = число игроков
        Map<String, Integer> winsByChar = new java.util.HashMap<>();
        Map<String, Integer> condCounts = new java.util.HashMap<>();
        // Средние ПО и занятость треков по числу игроков.
        double[] vpSum = new double[5];
        int[] vpPlayers = new int[5];
        int[] techStepsSum = new int[5];       // суммарно занятых шагов треков
        int[] techGamesWithSteps = new int[5]; // партий, где хоть кто-то шагнул на трек

        for (int players = 2; players <= 4; players++) {
            for (int g = 0; g < perCount; g++) {
                long seed = 1000L * players + g;   // разные сиды на состав
                GameConfig cfg = GameConfig.buildCached(players, seed);
                GameState state = Setup.buildGame(cfg);

                List<Agent> agents = new ArrayList<>();
                for (int s = 0; s < players; s++) {
                    agents.add(new HeuristicAgent(s, new Random(seed * 1000L + s),
                        CHARS[s % CHARS.length]));
                }

                GameLogger en = new GameLogger(state, GameLogger.defaultLogPath(state, enDir), "en");
                GameLogger ru = new GameLogger(state, GameLogger.defaultLogPath(state, ruDir), "ru");
                long[] hits = {0};
                long[] kills = {0};
                Map<String, Object> result = GameEngine.playGame(state, agents, event -> {
                    en.record(event);
                    ru.record(event);
                    String t = String.valueOf(event.get("type"));
                    if ("combat_hit".equals(t)) {
                        hits[0]++;
                        if (Boolean.TRUE.equals(event.get("destroyed"))) {
                            kills[0]++;
                        }
                    }
                });

                played++;
                totalCombat += hits[0];
                totalDestroyed += kills[0];
                int winner = (Integer) result.get("winner");
                winsByCount[players]++;
                String wchar = CHARS[winner % CHARS.length];
                winsByChar.merge(wchar, 1, Integer::sum);
                condCounts.merge(String.valueOf(result.get("condition")), 1, Integer::sum);

                // Средние ПО и занятость треков.
                @SuppressWarnings("unchecked")
                Map<Integer, Map<String, Integer>> scores =
                    (Map<Integer, Map<String, Integer>>) result.get("scores");
                for (var e : scores.entrySet()) {
                    vpSum[players] += e.getValue().get("total");
                    vpPlayers[players] += 1;
                }
                int stepsThisGame = 0;
                for (var p : state.players) {
                    for (int st : p.techSteps.values()) {
                        stepsThisGame += st;
                    }
                }
                techStepsSum[players] += stepsThisGame;
                if (stepsThisGame > 0) {
                    techGamesWithSteps[players] += 1;
                }
            }
        }

        out.println();
        out.println("=== СВОДКА ПАКЕТА (" + played + " партий) ===");
        out.println("составы: по " + perCount + " партий на 2, 3 и 4 игрока");
        out.println("боёв (попаданий): " + totalCombat + " | уничтожено жетонов: " + totalDestroyed);
        out.println("победы по характеру: " + winsByChar);
        out.println("условия победы: " + condCounts);
        for (int players = 2; players <= 4; players++) {
            if (vpPlayers[players] == 0) {
                continue;
            }
            double avgVp = vpSum[players] / vpPlayers[players];
            double avgSteps = (double) techStepsSum[players] / Math.max(1, perCount);
            out.println(String.format(java.util.Locale.ROOT,
                "  %dp: средние ПО/игрок=%.2f | шагов треков/партию=%.2f | партий с наукой=%d/%d",
                players, avgVp, avgSteps, techGamesWithSteps[players], perCount));
        }
        out.println("логи (en): " + enDir.toAbsolutePath());
        out.println("логи (ru): " + ruDir.toAbsolutePath());
    }
}
