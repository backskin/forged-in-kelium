package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import kelium.agents.HeuristicAgent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.report.BatchResult;
import kelium.report.GameLogger;
import kelium.report.TelemetryCollector;
import kelium.core.Agent;

/**
 * Пакетный прогон партий — играет N партий на 4 игроков смешанными характерами
 * (aggressor/defender/economist), пишет два лога на партию (английский в
 * reports/gamelogs/, русскую копию в reports/gamelogs_ru/), собирает телеметрию
 * и рендерит Markdown-отчёт баланса в reports/ (порт forge/report/batch.py).
 *
 * <p>Запуск: {@code mvn exec:java -Dexec.mainClass=kelium.RunBatch
 * [-Dexec.args="<games> <seed> <out.md>"]}. По умолчанию отчёт пишется в
 * {@code reports/java-batch-4p.md}.
 */
public final class RunBatch {

    private RunBatch() {
    }

    // Смешанные характеры по местам (детерминированно, повторяется по кругу).
    private static final String[] PERSONALITIES = {"aggressor", "defender", "economist", "aggressor"};

    /**
     * CLI: {@code kelium.RunBatch [games] [seed] [report.md] [rulesetId] [numPlayers] [writeLogs]}.
     * {@code rulesetId} "-" = {@link GameConfig#DEFAULT_RULESET}. {@code writeLogs}
     * "1" пишет полный текстовый лог (en+ru) на КАЖДУЮ партию — для больших
     * прогонов (сотни-тысячи партий) по умолчанию выключено (0), иначе
     * получаются тысячи мелких файлов и синхронизация Яндекс.Диска начинает
     * тормозить (баг найден дизайнером 2026-08-15).
     */
    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int games = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        long baseSeed = args.length > 1 ? Long.parseLong(args[1]) : 1000L;
        Path reportPath = Paths.get(args.length > 2 ? args[2] : "reports/java-batch-4p.md");
        String rulesetId = args.length > 3 && !"-".equals(args[3]) ? args[3] : GameConfig.DEFAULT_RULESET;
        int numPlayers = args.length > 4 ? Integer.parseInt(args[4]) : 4;
        boolean writeLogs = args.length > 5 && "1".equals(args[5]);

        Path enDir = Paths.get("reports/gamelogs");
        Path ruDir = Paths.get("reports/gamelogs_ru");

        BatchResult br = new BatchResult(rulesetId, numPlayers, games);
        Map<Integer, List<Integer>> vpAccum = new HashMap<>();
        for (int seat = 0; seat < numPlayers; seat++) {
            vpAccum.put(seat, new ArrayList<>());
        }

        long totalCombatHits = 0;
        long totalDestroyed = 0;
        double[] sumVpByPersonality = new double[PERSONALITIES.length];
        Map<String, Integer> winsByCharName = new HashMap<>();
        double sumVpAll = 0;
        int totalPlayerScores = 0;

        for (int g = 0; g < games; g++) {
            long seed = baseSeed + g;
            GameConfig cfg = GameConfig.buildCached(rulesetId, numPlayers, seed, null, null);
            GameState state = Setup.buildGame(cfg);

            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < numPlayers; seat++) {
                agents.add(new HeuristicAgent(seat, new Random(seed * 1000L + seat),
                    PERSONALITIES[seat % PERSONALITIES.length]));
            }

            // Стороны планшетов по местам (для доли побед по СТОРОНЕ).
            String[] sides = new String[numPlayers];
            for (int seat = 0; seat < numPlayers; seat++) {
                sides[seat] = state.player(seat).board.troop.side;
            }

            GameLogger en = writeLogs
                ? new GameLogger(state, GameLogger.defaultLogPath(state, enDir), "en") : null;
            GameLogger ru = writeLogs
                ? new GameLogger(state, GameLogger.defaultLogPath(state, ruDir), "ru") : null;
            TelemetryCollector col = new TelemetryCollector();
            long[] hits = {0};
            long[] kills = {0};
            Consumer<Map<String, Object>> broadcast = event -> {
                if (writeLogs) {
                    en.record(event);
                    ru.record(event);
                }
                col.record(event);
                if ("combat_hit".equals(event.get("type"))) {
                    hits[0]++;
                    if (Boolean.TRUE.equals(event.get("destroyed"))) {
                        kills[0]++;
                    }
                }
            };

            Map<String, Object> result = GameEngine.playGame(state, agents, broadcast);
            totalCombatHits += hits[0];
            totalDestroyed += kills[0];

            TelemetryCollector.GameReport rep = col.report();

            @SuppressWarnings("unchecked")
            Map<Integer, Map<String, Integer>> scores =
                (Map<Integer, Map<String, Integer>>) result.get("scores");
            int winner = (Integer) result.get("winner");
            String cond = String.valueOf(result.get("condition"));

            // ---- агрегация в BatchResult (как Python run_batch) ----
            br.seatWins.merge(winner, 1, Integer::sum);
            br.sideWins.merge(sides[winner], 1, Integer::sum);
            br.conditionCounts.merge(cond, 1, Integer::sum);
            br.margins.add(rep.margin());
            for (var e : rep.scores.entrySet()) {
                int seat = e.getKey();
                Map<String, Integer> bd = e.getValue();
                vpAccum.get(seat).add(bd.getOrDefault("total", 0));
                for (var se : bd.entrySet()) {
                    if (!"total".equals(se.getKey())) {
                        br.vpSourceTotals.merge(se.getKey(), se.getValue(), Integer::sum);
                    }
                }
            }
            for (var e : rep.actionCounts.entrySet()) {
                br.actionTotals.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            for (var e : rep.failedActions.entrySet()) {
                br.failedActionTotals.merge(e.getKey(), e.getValue(), Integer::sum);
            }

            // ---- параллельный консольный сигнал (как прежде) ----
            for (int seat = 0; seat < numPlayers; seat++) {
                int vp = scores.get(seat).get("total");
                sumVpByPersonality[seat % PERSONALITIES.length] += vp;
                sumVpAll += vp;
                totalPlayerScores++;
            }
            String winPers = PERSONALITIES[winner % PERSONALITIES.length];
            winsByCharName.merge(winPers, 1, Integer::sum);
        }

        for (int seat = 0; seat < numPlayers; seat++) {
            List<Integer> xs = vpAccum.get(seat);
            double avg = 0;
            if (!xs.isEmpty()) {
                long s = 0;
                for (int x : xs) {
                    s += x;
                }
                avg = s / (double) xs.size();
            }
            br.avgVpBySeat.put(seat, avg);
        }

        // ---- записать Markdown-отчёт ----
        String md = br.renderMarkdown();
        try {
            if (reportPath.getParent() != null) {
                Files.createDirectories(reportPath.getParent());
            }
            Files.write(reportPath, md.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            out.println("НЕ УДАЛОСЬ записать отчёт: " + e.getMessage());
        }

        // ---- консольная сводка ----
        out.println("=".repeat(60));
        out.println("ПАКЕТНЫЙ ПРОГОН: партий=" + games + ", игроков=" + numPlayers
            + ", характеры по местам=" + java.util.Arrays.toString(PERSONALITIES));
        out.println("=".repeat(60));
        out.printf("Средние ПО на игрока: %.2f%n", sumVpAll / Math.max(1, totalPlayerScores));
        out.println("Средние ПО по месту/характеру:");
        for (int i = 0; i < PERSONALITIES.length; i++) {
            out.printf("  место %d (%s): %.2f ПО в среднем%n",
                i, PERSONALITIES[i], sumVpByPersonality[i] / Math.max(1, games));
        }
        out.println("Победы по характерам:");
        for (var e : winsByCharName.entrySet()) {
            out.printf("  %-10s : %d побед (%.1f%%)%n", e.getKey(), e.getValue(),
                100.0 * e.getValue() / games);
        }
        out.println("Условия победы: " + br.conditionCounts);
        out.println("Суммарно попаданий в бою (combat_hit): " + totalCombatHits);
        out.println("Суммарно уничтожено жетонов: " + totalDestroyed);
        out.println("Война идёт: " + (totalCombatHits > 0 ? "ДА" : "НЕТ"));
        out.println(writeLogs
            ? "Логи: " + enDir.toAbsolutePath() + " (en), " + ruDir.toAbsolutePath() + " (ru)"
            : "Логи по партиям не писались (writeLogs=0) — только агрегированный отчёт.");
        out.println("Markdown-отчёт баланса: " + reportPath.toAbsolutePath());
    }
}
