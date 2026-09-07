package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ОТКУДА БЕРУТСЯ ОЧКИ — и сколько из них требует ПРОТИВНИКА.
 *
 * <p>Вопрос дизайнера 06.09.2026: почему в игре не возникает неизбежного
 * столкновения, как в Blood Rage. Прежде чем чинить бой, надо посмотреть на
 * счёт: если победные очки можно набрать, ни разу не тронув соседа, то мирное
 * согласие — не случайность и не трусость ботов, а прямое следствие того, как
 * устроен счёт.
 *
 * <p>Источники делятся на ВСТРЕЧНЫЕ (набрать нельзя, не отняв у другого) и
 * ОДИНОЧНЫЕ (набираются на своей половине стола, соседи не нужны). Разметка
 * ниже — единственное место, где это решение записано.
 */
public final class ОткудаОчки {

    private ОткудаОчки() {
    }

    /** Источники, которых нельзя набрать без противника. */
    private static final java.util.Set<String> ВСТРЕЧНЫЕ = java.util.Set.of(
        "cu_tokens");

    private static String род(String источник) {
        return ВСТРЕЧНЫЕ.contains(источник) ? "ВСТРЕЧНЫЙ" : "одиночный";
    }

    public static void main(String[] args) {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 60;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String характер = args.length > 2 ? args[2] : "balanced";

        Map<String, Long> сумма = new TreeMap<>();
        long всего = 0;
        for (int i = 0; i < партий; i++) {
            long seed = 11000L + i;
            GameState s = Setup.buildGame(
                GameConfig.buildCached(GameConfig.DEFAULT_RULESET, игроков, seed, null, null));
            List<Agent> боты = new ArrayList<>();
            for (int seat = 0; seat < игроков; seat++) {
                боты.add(kelium.agents.Bots.create(характер, seat,
                    new Random(seed * 31 + seat), игроков));
            }
            new GameEngine(s, боты, ev -> { }).run();
            for (int seat = 0; seat < игроков; seat++) {
                for (Map.Entry<String, Integer> e : Scoring.scorePlayer(s, seat).entrySet()) {
                    if ("total".equals(e.getKey()) || e.getValue() == 0) {
                        continue;
                    }
                    сумма.merge(e.getKey(), (long) e.getValue(), Long::sum);
                    всего += e.getValue();
                }
            }
        }
        System.out.printf("свод %s, игроков %d, партий %d, за столом «%s»%n%n",
            GameConfig.DEFAULT_RULESET, игроков, партий, характер);
        System.out.printf("%-24s %-12s %8s %8s%n", "источник", "род", "ПО/игрок", "доля");
        long встречных = 0;
        for (Map.Entry<String, Long> e : сумма.entrySet()) {
            double наИгрока = e.getValue() / (double) (партий * игроков);
            System.out.printf("%-24s %-12s %8.2f %7.0f%%%n",
                e.getKey(), род(e.getKey()), наИгрока, 100.0 * e.getValue() / всего);
            if (ВСТРЕЧНЫЕ.contains(e.getKey())) {
                встречных += e.getValue();
            }
        }
        System.out.printf("%n  ВСТРЕЧНЫХ очков (нельзя набрать без противника): %.0f%% "
            + "из %.1f ПО на игрока%n",
            100.0 * встречных / всего, всего / (double) (партий * игроков));
    }
}
