package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.Storage;

/**
 * КУДА ДЕВАЮТСЯ ТРОФЕИ — приход поимённо, расход поимённо, и сколько СРЕЗАЛО
 * хранилище.
 *
 * <p><b>Зачем.</b> Утверждение «трофеев не хватает на третью ступень трека»
 * я вывел из двух строк прошлого отчёта, где считались только бой и Возврат.
 * Дизайнер возразил: трофеи идут ещё с контейнеров, с заданий, с нейтральных
 * зданий, с рынка, и на начальном задании один уже лежит. Возражение
 * справедливое: спор о том, сколько чего приходит, решается перечислением, а
 * не мнением.
 *
 * <p>Здесь включается учёт {@link Storage#УЧЁТ_ТРОФЕЕВ} — единственная точка,
 * через которую проходит ВЕСЬ приход трофеев, — и к нему добавлены две вещи,
 * которых в споре не было вовсе:
 *
 * <ul>
 *   <li><b>сколько срезала вместимость.</b> Трофей кладётся в общий склад, и
 *       если места нет, лишнее просто не приходит. Это не «мало трофеев», это
 *       «некуда класть», и лечится совсем другим;</li>
 *   <li><b>докуда доезжают треки.</b> Вопрос ведь не про трофеи как таковые, а
 *       про то, доходит ли кто-нибудь до третьей ступени.</li>
 * </ul>
 *
 * <p>Стенд ОДНОПОТОЧНЫЙ: учёт в {@code Storage} — одно статическое поле на
 * процесс, и считать его из нескольких партий сразу значит сложить их в кашу.
 *
 * <p>Запуск: {@code kelium.КудаДеваютсяТрофеи [партий] [игроков] [уровень]}.
 */
public final class КудаДеваютсяТрофеи {

    private КудаДеваютсяТрофеи() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 4;

        Storage.УЧЁТ_ТРОФЕЕВ = new java.util.concurrent.ConcurrentHashMap<>();
        kelium.engine.CombatResolver.УЧЁТ_ЯЧЕЕК =
            new java.util.concurrent.ConcurrentHashMap<>();
        List<String> состав = Bots.ROSTER_4;

        double сносов = 0;
        double потраченоВНауку = 0;
        double осталось = 0;
        // Сколько игроков-мест дошло до ступени N хотя бы на одном треке.
        int[] дошлиДоСтупени = new int[5];
        Map<Integer, Integer> шаговВсего = new TreeMap<>();

        for (int g = 0; g < партий; g++) {
            long seed = 9_300_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
            // Настоящий стол объявляется ДО расстановки агентов: всё, что
            // начислят копии стола внутри планировщика, в учёт не попадёт.
            Storage.НАСТОЯЩАЯ_ПАРТИЯ = s;
            List<Agent> agents = new ArrayList<>();
            int сдвиг = g % состав.size();
            for (int i = 0; i < игроков; i++) {
                agents.add(Bots.create(состав.get((i + сдвиг) % состав.size()),
                    Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
            }
            double[] вНауку = new double[игроков];
            long[] снос = new long[1];
            new GameEngine(s, agents, ev -> {
                String тип = String.valueOf(ev.get("type"));
                if ("combat_hit".equals(тип) && Boolean.TRUE.equals(ev.get("destroyed"))) {
                    снос[0]++;
                } else if ("action".equals(тип) && "science".equals(ev.get("action"))
                        && ev.get("telemetry") instanceof Map<?, ?> м
                        && м.get("trophy_spent") instanceof Number n
                        && ev.get("seat") instanceof Number st) {
                    вНауку[st.intValue()] += n.doubleValue();
                }
            }).run();
            сносов += снос[0];
            for (int i = 0; i < игроков; i++) {
                потраченоВНауку += вНауку[i];
            }
            for (PlayerState p : s.players) {
                осталось += p.resources.trophy();
                int лучший = 0;
                int сумма = 0;
                for (int v : p.techSteps.values()) {
                    лучший = Math.max(лучший, v);
                    сумма += v;
                }
                for (int ст = 0; ст <= Math.min(4, лучший); ст++) {
                    дошлиДоСтупени[ст]++;
                }
                шаговВсего.merge(сумма, 1, Integer::sum);
            }
        }

        double места = партий * (double) игроков;
        out.printf("КУДА ДЕВАЮТСЯ ТРОФЕИ · свод %s · %d партий · %d игроков · уровень %d%n",
            GameConfig.DEFAULT_RULESET, партий, игроков, уровень);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.println("всё — НА ИГРОКА ЗА ПАРТИЮ\n");

        out.println("ПРИХОД ПО ИСТОЧНИКАМ (кто именно начислил)");
        long всего = 0;
        long потеряно = 0;
        List<Map.Entry<String, Long>> строки =
            new ArrayList<>(Storage.УЧЁТ_ТРОФЕЕВ.entrySet());
        строки.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Long> e : строки) {
            if (e.getKey().startsWith("!")) {
                потеряно = e.getValue();
                continue;
            }
            всего += e.getValue();
        }
        for (Map.Entry<String, Long> e : строки) {
            if (e.getKey().startsWith("!")) {
                continue;
            }
            out.printf("  %-46s %6.2f  (%2.0f%%)%n", e.getKey(), e.getValue() / места,
                100.0 * e.getValue() / Math.max(1, всего));
        }
        out.printf("  %-46s %6.2f%n", "ИТОГО ПРИШЛО", всего / места);
        out.printf("  %-46s %6.2f  <- срезала вместимость склада%n",
            "НЕ ВЛЕЗЛО И ПРОПАЛО", потеряно / места);

        out.println("\nРАСХОД");
        out.printf("  потрачено в Науке                              %6.2f%n",
            потраченоВНауку / места);
        out.printf("  осталось на руках в конце                      %6.2f%n", осталось / места);
        out.printf("  ушло на прочее (арсенал, рынок, карты)         %6.2f%n",
            (всего / места) - (потраченоВНауку / места) - (осталось / места));

        out.println("\nДОКУДА ДОЕЗЖАЮТ ТРЕКИ (доля партий-мест)");
        for (int ст = 1; ст <= 4; ст++) {
            out.printf("  дошли до ступени %d хотя бы на одном треке: %3.0f%%%n",
                ст, 100.0 * дошлиДоСтупени[ст] / места);
        }
        out.println("  шагов суммарно по трём трекам — сколько мест:");
        for (Map.Entry<Integer, Integer> e : шаговВсего.entrySet()) {
            out.printf("    %d шагов: %3.0f%%%n", e.getKey(), 100.0 * e.getValue() / места);
        }

        out.println("\nБЫЛ ЛИ ВЫБОР ЯЧЕЙКИ В БОЮ");
        out.println("(на каждый жетон, которому было куда стрелять)");
        java.util.Map<String, Long> я = kelium.engine.CombatResolver.УЧЁТ_ЯЧЕЕК;
        long всегоЯ = я.values().stream().mapToLong(Long::longValue).sum();
        я.entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(e -> out.printf("  %-34s %7d  (%2.0f%%)%n", e.getKey(), e.getValue(),
                100.0 * e.getValue() / Math.max(1, всегоЯ)));
        long выбор = я.entrySet().stream()
            .filter(e -> e.getKey().contains("ВЫБОР"))
            .mapToLong(java.util.Map.Entry::getValue).sum();
        out.printf("  %-34s %7d  (%2.0f%%)%n", "ИТОГО с выбором", выбор,
            100.0 * выбор / Math.max(1, всегоЯ));

        out.printf("%nдля сверки: уничтожено жетонов всеми за партию %.2f, "
            + "на игрока %.2f%n", сносов / партий, сносов / места);
    }
}
