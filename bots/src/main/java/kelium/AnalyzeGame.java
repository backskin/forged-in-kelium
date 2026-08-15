package kelium;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.agents.Genome;
import kelium.agents.SearchAgent;
import kelium.agents.StrategicAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.report.analysis.Analyst;
import kelium.report.Commentator;
import kelium.report.NarrativeLog;

/**
 * AnalyzeGame — ПАРТИЯ С РАЗБОРОМ: боты играют, а рядом считается цена каждого
 * их решения.
 *
 * <p>Чем отличается от {@link NarrateGame}. Рассказ объясняет, ЧТО бот сделал и
 * почему он так считает. Разбор отвечает на другой вопрос — НАДО ЛИ БЫЛО так: для
 * каждого варианта копия партии доигрывается заново, и разница итогов даёт цену
 * решения в победных очках. Именно это превращает партию ботов в урок для
 * человека: видно не только ходы, но и чего стоили альтернативы.
 *
 * <p>Дорого по времени (на каждое решение — несколько доигранных партий), поэтому
 * это инструмент для ОДНОЙ показательной партии, а не для батчей.
 *
 * <p>CLI: {@code kelium.AnalyzeGame [игроков] [сид] [характер] [прогонов] [горизонт]}.
 */
public final class AnalyzeGame {

    private AnalyzeGame() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream stdout = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        System.setOut(stdout);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42L;
        String character = args.length > 2 ? args[2] : "balanced";
        int rollouts = args.length > 3 ? Integer.parseInt(args[3]) : 2;
        int horizon = args.length > 4 ? Integer.parseInt(args[4]) : 2;

        GameConfig cfg = GameConfig.buildCached(players, seed);
        GameState state = Setup.buildGame(cfg);
        Genome genome = Bots.genome(character, players);

        Path dir = Paths.get("reports", "narrative",
            "razbor_p" + players + "_seed" + seed);
        Path narrPath = dir.resolve("rasskaz.md");
        NarrativeLog narrative = new NarrativeLog(state, narrPath)
            .withEcho(new PrintWriter(stdout, true));

        List<Agent> agents = new ArrayList<>();
        List<Analyst> analysts = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            narrative.withRole(seat, "стратег-" + (seat + 1));
            // Играет ПРОСЧИТЫВАЮЩИЙ бот — разбирать имеет смысл сильную игру:
            // у слабой ошибок столько, что важные среди них не видны.
            StrategicAgent inner = SearchAgent.fast(seat,
                new Random(seed * 100L + seat), genome, character);
            inner.withNarrative(narrative);
            Analyst a = new Analyst(inner, genome, rollouts, horizon, narrative,
                seed * 7919L + seat);
            analysts.add(a);
            agents.add(a);
        }

        Commentator commentator = new Commentator(state, narrative);
        stdout.println("Разбор партии: игроков=" + players + " сид=" + seed
            + " характер=" + character + " (прогонов на вариант " + rollouts
            + ", горизонт " + horizon + " раунда)");
        long t0 = System.nanoTime();
        Map<String, Object> result = GameEngine.playGame(state, agents, commentator);
        double mins = (System.nanoTime() - t0) / 6e10;

        String report = Analyst.report(analysts, 25);
        Path out = dir.resolve("разбор.md");
        Files.createDirectories(dir);
        Files.writeString(out, report, StandardCharsets.UTF_8);

        stdout.println();
        stdout.println(report);
        stdout.println("Победитель: игрок " + result.get("winner")
            + " | условие: " + result.get("condition"));
        stdout.printf(java.util.Locale.ROOT, "время разбора: %.1f мин%n", mins);
        stdout.println("рассказ: " + narrPath.toAbsolutePath());
        stdout.println("разбор:  " + out.toAbsolutePath());
    }
}
