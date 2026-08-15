package kelium;

import java.io.PrintWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.report.Commentator;
import kelium.report.GameArchive;
import kelium.report.NarrativeLog;
import kelium.dataio.Locations;
import kelium.core.Agent;

/**
 * NarrateGame — прогон ОДНОЙ партии с «живым» рассказом на русском:
 * <ul>
 *   <li>все игроки — стратегические боты ({@link StrategicAgent}), каждый
 *       озвучивает свои решения ОТ ПЕРВОГО ЛИЦА;
 *   <li>{@link Commentator} рассуждает, хвалит и критикует ходы;
 *   <li>рассказ пишется в файл reports/narrative/ и одновременно в консоль.
 * </ul>
 *
 * <p>CLI: {@code kelium.NarrateGame [players] [seed] [genomePath]}. Если геном не
 * указан — берётся data/genomes/strategic_<N>p.json, а при его отсутствии
 * дефолтный геном.
 */
public final class NarrateGame {

    private NarrateGame() {
    }

    public static void main(String[] args) {
        PrintStream stdout = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 42L;

        GameConfig cfg = GameConfig.buildCached(players, seed);
        GameState state = Setup.buildGame(cfg);

        Genome genome = loadGenome(args, players, cfg);

        // папка на партию: рядом рассказ .md и SVG-картинки полей по раундам
        Path gameDir = Paths.get("reports", "narrative",
            "partiya_p" + players + "_seed" + seed);
        Path narrPath = gameDir.resolve("rasskaz.md");
        NarrativeLog narrative = new NarrativeLog(state, narrPath)
            .withEcho(new PrintWriter(stdout, true));

        Path archPath = Paths.get("reports", "gamearchive",
            "game_p" + players + "_seed" + seed + ".jsonl");
        GameArchive archive = new GameArchive(archPath);

        List<Agent> agents = new ArrayList<>();
        String[] roles = {"стратег-1", "стратег-2", "стратег-3", "стратег-4"};
        for (int seat = 0; seat < players; seat++) {
            narrative.withRole(seat, roles[seat % roles.length]);
            StrategicAgent a = new StrategicAgent(seat, new Random(seed * 100L + seat), genome, archive)
                .withNarrative(narrative);
            agents.add(a);
        }

        Commentator commentator = new Commentator(state, narrative);

        stdout.println("Рассказ партии: игроков=" + players + " сид=" + seed
            + " -> " + narrPath.toAbsolutePath());
        stdout.println();

        // комментатор — наблюдатель потока событий; реплики ботов идут из choose
        Map<String, Object> result = GameEngine.playGame(state, agents, commentator);
        archive.close();

        stdout.println();
        stdout.println("Готово. Победитель: игрок " + result.get("winner")
            + " | рассказ: " + narrPath.toAbsolutePath());
    }

    private static Genome loadGenome(String[] args, int players, GameConfig cfg) {
        Path path;
        if (args.length > 2) {
            path = Paths.get(args[2]);
        } else {
            path = Locations.botMemory().resolve("strategic_" + players + "p.json");
        }
        try {
            Genome g = Genome.loadJson(path);
            System.out.println("геном загружен: " + path);
            return g;
        } catch (Exception e) {
            System.out.println("геном не найден (" + path + "), беру дефолтный.");
            return Genome.defaults();
        }
    }
}
