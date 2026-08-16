package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import kelium.agents.Bots;
import kelium.agents.Genome;
import kelium.agents.SearchAgent;
import kelium.agents.ValueNet;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * СЕТЬ ПРОТИВ ЛИНЕЙНОЙ ФОРМУЛЫ — очная проверка за одним столом.
 *
 * <p>Заказ дизайнера 15.08.2026: включить обученную {@link ValueNet} и
 * переобучить. Точность предсказания на отложенных партиях — это не то же самое,
 * что сила игры: судья мог выучить корреляции, которые ничего не решают в
 * реальном выборе хода. Единственная честная проверка — посадить оба судьи за
 * стол.
 *
 * <p>Одинаковый характер (те же веса действий, тот же геном), различается ТОЛЬКО
 * судья позиции: {@link Genome#withJudge}. Места ротируются, чтобы преимущество
 * первого хода не исказило счёт.
 *
 * <p>ВАЖНО: сравнение идёт на {@link SearchAgent}, а НЕ на обычном
 * {@code StrategicAgent}. Судью позиции спрашивает ТОЛЬКО просчёт вперёд
 * (см. {@code Lookahead.horizonScore}) — обычный бот его не вызывает вовсе.
 * Первая версия этого стенда сравнивала двух ботов, которые вели себя
 * идентично, потому что судья, которым они отличались, не был задействован ни
 * разу; разница в счёте была чистым шумом.
 *
 * <p>Запуск: {@code kelium.NetVsLinear <путь-к-сети> [партий] [игроков] [характер]}.
 */
public final class NetVsLinear {

    private NetVsLinear() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Path netPath = Path.of(args.length > 0 ? args[0]
            : "data/genomes/value_4p.txt");
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 400;
        int players = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        String character = args.length > 3 ? args[3] : "balanced";

        ValueNet net = ValueNet.load(netPath);
        Genome baseLinear = Bots.genome(character, players);
        Genome baseNet = baseLinear.withJudge(net);

        double[] vp = new double[2];       // [сеть, линейная]
        int[] wins = new int[2];
        double rounds = 0;

        for (int g = 0; g < games; g++) {
            long seed = 11_000_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            // Половина стола — сеть, половина — линейная, места сдвигаются
            // каждую партию, чтобы преимущество первого хода не исказило счёт.
            int[] whoIsNet = new int[players];
            for (int i = 0; i < players; i++) {
                boolean useNet = ((i + g) % 2) == 0;
                whoIsNet[i] = useNet ? 1 : 0;
                Genome genome = useNet ? baseNet : baseLinear;
                agents.add(SearchAgent.deep(i, new Random(seed * 31 + i), genome,
                    character));
            }
            var res = GameEngine.playGame(s, agents, ev -> { });
            rounds += res.get("rounds") instanceof Number r ? r.intValue() : 0;
            int winner = res.get("winner") instanceof Number w ? w.intValue() : -1;
            for (int i = 0; i < players; i++) {
                int idx = whoIsNet[i];
                vp[idx] += Scoring.scorePlayer(s, i).getOrDefault("total", 0);
                if (winner == i) {
                    wins[idx]++;
                }
            }
        }

        double perSideGames = games * (players / 2.0);
        out.println("судья: сеть " + netPath + " против линейной формулы, тот же "
            + "характер (" + character + "), места ротируются");
        out.printf(Locale.ROOT, "раундов за партию: %.1f%n", rounds / games);
        out.printf(Locale.ROOT, "%-10s %8s %8s%n", "судья", "ПО/игрок", "побед");
        out.printf(Locale.ROOT, "%-10s %8.2f %7.1f%%%n", "сеть",
            vp[1] / perSideGames, 100 * wins[1] / perSideGames);
        out.printf(Locale.ROOT, "%-10s %8.2f %7.1f%%%n", "линейная",
            vp[0] / perSideGames, 100 * wins[0] / perSideGames);
    }
}
