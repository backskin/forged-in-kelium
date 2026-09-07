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

    /**
     * ТРИ РОДА ИСТОЧНИКОВ, а не два. Первая редакция стенда делила их надвое и
     * ошибалась (поймал дизайнер 06.09.2026): треки науки покупаются ТРОФЕЯМИ, а
     * трофей по замыслу — продукт войны, значит помечать треки «одиночным»
     * источником было неверно.
     *
     * <p>ПРЯМОЙ — набрать нельзя, не отняв у другого (жетон уничтоженного ЦУ).
     * <p>ТРОФЕЙНЫЙ — оплачивается трофеями, то есть ПО ЗАМЫСЛУ требует войны.
     * Сколько его требует НА ДЕЛЕ, зависит от того, откуда приходят трофеи, и
     * это считается отдельно: если трофей капает даром, источник трофейный лишь
     * на бумаге.
     * <p>ОДИНОЧНЫЙ — набирается на своей половине стола.
     */
    private static final java.util.Set<String> ПРЯМЫЕ = java.util.Set.of("cu_tokens");
    private static final java.util.Set<String> ТРОФЕЙНЫЕ = java.util.Set.of("tech");

    private static String род(String источник) {
        if (ПРЯМЫЕ.contains(источник)) {
            return "ПРЯМОЙ";
        }
        return ТРОФЕЙНЫЕ.contains(источник) ? "трофейный" : "одиночный";
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
        long прямых = 0;
        long трофейных = 0;
        for (Map.Entry<String, Long> e : сумма.entrySet()) {
            double наИгрока = e.getValue() / (double) (партий * игроков);
            System.out.printf("%-24s %-12s %8.2f %7.0f%%%n",
                e.getKey(), род(e.getKey()), наИгрока, 100.0 * e.getValue() / всего);
            if (ПРЯМЫЕ.contains(e.getKey())) {
                прямых += e.getValue();
            }
            if (ТРОФЕЙНЫЕ.contains(e.getKey())) {
                трофейных += e.getValue();
            }
        }
        double долиПрямых = 100.0 * прямых / всего;
        double долиТрофейных = 100.0 * трофейных / всего;
        System.out.printf("%n  ПРЯМЫХ очков (нельзя набрать без противника):   %.0f%%%n",
            долиПрямых);
        System.out.printf("  ТРОФЕЙНЫХ очков (по замыслу требуют войны):     %.0f%%%n",
            долиТрофейных);
        System.out.printf("  ИТОГО по замыслу через войну:                   %.0f%% "
            + "из %.1f ПО на игрока%n",
            долиПрямых + долиТрофейных, всего / (double) (партий * игроков));
        System.out.printf("%n  Сколько из этого требует войны НА ДЕЛЕ — смотреть по доле%n"
            + "  трофеев, добытых боем: трофейная доля умножается на неё.%n");
    }
}
