package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.agents.Поиск;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.step.Летопись;

/**
 * АРЕНА ПОИСКА (23.09.2026): один бот {@link Поиск} против трёх прежних
 * гроссмейстеров; место поиска крутится по кругу. Прежние боты здесь — мерка,
 * а не цель.
 *
 * <p>Запуск: {@code kelium.АренаПоиска [партий] [розыгрышей] [горизонт кругов]}.
 */
public final class АренаПоиска {

    private АренаПоиска() {
    }

    private static int ЯДЕР = 1;

    private record Итог(int очки, double соперники, boolean победа, double действий,
                        double действийСоп, long мс, long розыгрышей, long сорвалось,
                        long решений) {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        int розыгрышей = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        int горизонт = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int ядер = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        // Партии по очереди, а ядра — розыгрышам внутри решения поиска.
        ExecutorService пул = Executors.newFixedThreadPool(1);
        ЯДЕР = ядер;
        List<Future<Итог>> ff = new ArrayList<>();
        for (int g = 0; g < партий; g++) {
            final int n = g;
            ff.add(пул.submit(() -> партия(n, розыгрышей, горизонт)));
        }
        double о = 0, с = 0, д = 0, дс = 0, мс = 0;
        long роз = 0, сорв = 0, реш = 0;
        int побед = 0;
        for (Future<Итог> f : ff) {
            Итог и = f.get();
            о += и.очки();
            с += и.соперники();
            д += и.действий();
            дс += и.действийСоп();
            мс += и.мс();
            роз += и.розыгрышей();
            сорв += и.сорвалось();
            реш += и.решений();
            побед += и.победа() ? 1 : 0;
            System.err.printf("  поиск %d ПО против %.1f; действий %.1f/%.1f за раунд; %d с%n",
                и.очки(), и.соперники(), и.действий(), и.действийСоп(), и.мс() / 1000);
        }
        пул.shutdown();
        out.printf("ПОИСК (%d розыгрышей на решение, горизонт %d кругов) против трёх прежних"
            + " гроссмейстеров, %d партий%n", розыгрышей, горизонт, партий);
        out.printf("  побед: %d из %d (%.0f%%; ровно — 25%%)%n", побед, партий, 100.0 * побед / партий);
        out.printf("  ПО: поиск %.2f, прежний бот %.2f%n", о / партий, с / партий);
        out.printf("  действий за раунд: поиск %.2f, прежний бот %.2f%n", д / партий, дс / партий);
        out.printf("  решений поиска за партию %.0f, розыгрышей сорвалось %.1f%%, время партии %.0f с%n",
            (double) реш / партий, роз + сорв == 0 ? 0 : 100.0 * сорв / (роз + сорв),
            мс / партий / 1000);
    }

    private static Итог партия(int номер, int розыгрышей, int горизонт) {
        long seed = 9_400_000L + номер;
        int место = номер % 4;
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        Летопись летопись = new Летопись();
        List<Agent> agents = new ArrayList<>();
        Поиск поиск = new Поиск(место, летопись, seed);
        поиск.розыгрышей = розыгрышей;
        поиск.горизонтКругов = горизонт;
        поиск.потоков = ЯДЕР;
        for (int i = 0; i < 4; i++) {
            agents.add(i == место ? поиск
                : Bots.create(Bots.ROSTER_4.get(i), Bots.Level.ГРОССМЕЙСТЕР, i,
                    new Random(seed * 31 + i), 4));
        }
        List<Agent> записанные = летопись.подключить(s, agents);
        int[] действий = new int[4];
        long t0 = System.currentTimeMillis();
        GameEngine.playGame(s, записанные, ev -> {
            if ("action".equals(ev.get("type")) && Boolean.TRUE.equals(ev.get("ok"))
                    && ev.get("seat") instanceof Number n) {
                действий[n.intValue()]++;
            }
        });
        long t1 = System.currentTimeMillis();
        отчётКонцов(поиск);
        double соп = 0, дс = 0;
        for (int i = 0; i < 4; i++) {
            if (i != место) {
                соп += Scoring.scorePlayer(s, i).getOrDefault("total", 0) / 3.0;
                дс += действий[i] / 3.0;
            }
        }
        int раундов = Math.max(1, s.round);
        return new Итог(Scoring.scorePlayer(s, место).getOrDefault("total", 0), соп,
            s.winner != null && s.winner == место, действий[место] / (double) раундов,
            дс / раундов, t1 - t0, поиск.розыгрышейВсего, поиск.сорвалось, поиск.решений);
    }

    static synchronized void отчётКонцов(Поиск п) {
        System.err.printf("    концы розыгрышей: партия %d, горизонт %d, петля %d; решений на розыгрыш %.0f%n",
            п.концы[0], п.концы[1], п.концы[2],
            п.розыгрышейВсего == 0 ? 0 : (double) п.решенийВРозыгрышах / п.розыгрышейВсего);
    }
}
