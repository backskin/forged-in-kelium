package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import kelium.agents.Bots;
import kelium.agents.ОбученныйСтратег;
import kelium.agents.сеть.Сеть;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.Вариант;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ЭКОНОМИКА КАРТ (Карты 2.0, 30.09.2026) — сколько карт реально играется за
 * партию и что меняют ручки правил, от которых зависит поток карт.
 *
 * <p>Замер прогона бульона: карт арсенала ставят меньше одной на игрока за
 * партию. С таким потоком карты не могут быть главной частью игры, как бы
 * хороши они ни были. Этот стенд играет ОДНИ И ТЕ ЖЕ раздачи при нескольких
 * вариантах правил (стратеги, одна и та же сеть) и меряет: выполненные
 * задания и установленный арсенал на игрока, ходы, где сыграно две карты и
 * больше (связка за ход), срабатывания, длину партии и разброс очков.
 *
 * <p>Запуск: {@code kelium.ЗамерЭкономикиКарт [партий на вариант] [потоков]}.
 */
public final class ЗамерЭкономикиКарт {

    private ЗамерЭкономикиКарт() {
    }

    private static final List<Вариант> ВАРИАНТЫ = List.of(
        Вариант.обычный("как сейчас"),
        Вариант.разобрать("задание без спец-действия;правило=objectives.play_is_free_action=true"),
        Вариант.разобрать("два спец-действия за ход;правило=actions.spec_per_turn=2"),
        Вариант.разобрать("рука заданий 3;правило=rounds.objective_hand_limit=3"),
        Вариант.разобрать("задание без спец-действия и рука 3;"
            + "правило=objectives.play_is_free_action=true;правило=rounds.objective_hand_limit=3"));

    /** Итог одной партии одного варианта. */
    record Итог(double заданий, double арсенала, double связокЗаХод, double срабатываний,
                double спец, int раундов, double разброс) {
    }

    public static void main(String[] args) throws Exception {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int потоков = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        Сеть сеть = Files.exists(ОбученныйСтратег.файл()) ? Сеть.загрузить(ОбученныйСтратег.файл()) : null;
        System.out.printf("экономика карт: %d партий на вариант, %d потоков, стратег %s%n", партий,
            потоков, сеть != null ? "обученный" : "без сети");
        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        Map<String, List<Future<Итог>>> ff = new LinkedHashMap<>();
        for (Вариант в : ВАРИАНТЫ) {
            List<Future<Итог>> список = new ArrayList<>();
            for (int g = 0; g < партий; g++) {
                final long seed = 8_300_000L + g;
                список.add(пул.submit(() -> в.применить(() -> партия(seed, сеть))));
            }
            ff.put(в.имя(), список);
        }
        StringBuilder sb = new StringBuilder("# Экономика карт — варианты правил на одних раздачах\n\n"
            + "Стратеги (одна сеть), " + партий + " раздач на вариант. Всё — средние на игрока за"
            + " партию, кроме раундов и разброса очков (лучший минус худший).\n\n"
            + "| вариант | заданий выполнено | арсенала установлено | ходов со связкой (2+ карты)"
            + " | срабатываний | спец-действий | раундов | разброс очков |\n"
            + "|---|---|---|---|---|---|---|---|\n");
        for (var e : ff.entrySet()) {
            double[] сумма = new double[7];
            int n = 0;
            for (Future<Итог> f : e.getValue()) {
                Итог и;
                try {
                    и = f.get();
                } catch (Exception ex) {
                    System.out.println("  партия сорвалась: " + ex.getCause());
                    continue;
                }
                сумма[0] += и.заданий();
                сумма[1] += и.арсенала();
                сумма[2] += и.связокЗаХод();
                сумма[3] += и.срабатываний();
                сумма[4] += и.спец();
                сумма[5] += и.раундов();
                сумма[6] += и.разброс();
                n++;
            }
            n = Math.max(1, n);
            sb.append(String.format(java.util.Locale.ROOT,
                "| %s | %.2f | %.2f | %.2f | %.2f | %.1f | %.1f | %.1f |%n", e.getKey(), сумма[0] / n,
                сумма[1] / n, сумма[2] / n, сумма[3] / n, сумма[4] / n, сумма[5] / n, сумма[6] / n));
        }
        пул.shutdown();
        Path out = Path.of("design-docs/фигуры/экономика карт.md");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println(sb);
    }

    private static Итог партия(long seed, Сеть сеть) {
        GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(ЦиклСтратега.стратег(Bots.ROSTER_4.get(i), i, new Random(seed * 31 + i), сеть,
                сеть == null ? 0 : 1.0));
        }
        int[] заданий = new int[1];
        int[] арсенала = new int[1];
        int[] срабатываний = new int[1];
        int[] связок = new int[1];
        int[] спец = new int[1];
        Map<String, Integer> карт = new LinkedHashMap<>();
        GameEngine.playGame(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            Object место = ev.get("seat");
            switch (тип) {
                case "objective" -> {
                    заданий[0]++;
                    карт.merge(s.round + ":" + s.circle + ":" + место, 1, Integer::sum);
                }
                case "arsenal" -> {
                    if ("install".equals(ev.get("mode"))) {
                        арсенала[0]++;
                        карт.merge(s.round + ":" + s.circle + ":" + место, 1, Integer::sum);
                    } else if ("burn".equals(ev.get("mode"))) {
                        карт.merge(s.round + ":" + s.circle + ":" + место, 1, Integer::sum);
                    }
                }
                case "objective_burn" -> карт.merge(s.round + ":" + s.circle + ":" + место, 1,
                    Integer::sum);
                case "card_trigger" -> срабатываний[0]++;
                case "turn_end" -> {
                    if (место instanceof Integer m && s.journal != null) {
                        спец[0] += s.journal.of(m).спецИспользовано;
                    }
                }
                default -> { }
            }
        });
        for (int c : карт.values()) {
            if (c >= 2) {
                связок[0]++;
            }
        }
        int мин = Integer.MAX_VALUE;
        int макс = Integer.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            int v = Scoring.scorePlayer(s, i).getOrDefault("total", 0);
            мин = Math.min(мин, v);
            макс = Math.max(макс, v);
        }
        return new Итог(заданий[0] / 4.0, арсенала[0] / 4.0, связок[0] / 4.0, срабатываний[0] / 4.0,
            спец[0] / 4.0, s.round, макс - мин);
    }
}
