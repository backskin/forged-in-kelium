package kelium;

import java.util.List;
import java.util.Locale;

import kelium.agents.Fitness;
import kelium.agents.SelfPlayTrainer;

/**
 * ОБУЧЕНИЕ ЖНЕЦА ПО СТУПЕНЯМ (заказ дизайнера 2026-08-15).
 *
 * <p>Линию учат не всему сразу, а по очереди, и каждая ступень продолжает
 * предыдущую — начальная популяция берётся из чемпиона на диске:
 *
 * <ol>
 *   <li><b>жатва</b> — снести за партию как можно больше чужих жетонов и уцелеть;</li>
 *   <li><b>наука</b> — то же самое плюс максимум шагов по ВСЕМ трём трекам;</li>
 *   <li><b>задания</b> — то же самое плюс как можно больше УСИЛЕННЫХ выполнений.</li>
 * </ol>
 *
 * <p>Ступени накопительные: на второй жатва не отменяется, к ней добавляется
 * наука. Иначе третья ступень просто стёрла бы первую — отбор всегда вытачивает
 * ровно то, за что платят, и ничего сверх того.
 *
 * <p>Запуск: {@code kelium.TrainReaper <ступень 1|2|3> [партий] [популяция] [партий на геном]}.
 */
public final class TrainReaper {

    private TrainReaper() {
    }

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        int step = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        long per = args.length > 1 ? Long.parseLong(args[1]) : 200_000L;
        int population = args.length > 2 ? Integer.parseInt(args[2]) : 24;
        int perGenome = args.length > 3 ? Integer.parseInt(args[3]) : 6;

        Fitness.Goal goal = switch (step) {
            case 2 -> Fitness.Goal.ЖНЕЦ_НАУКА;
            case 3 -> Fitness.Goal.ЖНЕЦ_ЗАДАНИЯ;
            default -> Fitness.Goal.ЖНЕЦ;
        };
        System.out.printf(Locale.ROOT,
            "=== ЖНЕЦ, ступень %d (%s): %d партий, популяция %d, по %d партий на геном%n",
            step, goal, per, population, perGenome);

        SelfPlayTrainer t = new SelfPlayTrainer(population, perGenome,
            Fitness.Brain.ФОРМУЛА);
        // Учим ТОЛЬКО жнеца: соперниками остаются все линии (так устроен спарринг),
        // но переписывается один геном. Полный проход по девяти характерам ради
        // одной линии стоил бы в девять раз дороже.
        t.setOnly(List.of("reaper"));
        t.setGoal(goal);
        t.run(per, Math.max(500L, per / 10), 0L);
        System.out.println(t.logText().lines().filter(l -> l.contains("reaper")
            || l.contains("итог") || l.contains("ИТОГ")).limit(40)
            .reduce("", (a, b) -> a + b + System.lineSeparator()));
    }
}
