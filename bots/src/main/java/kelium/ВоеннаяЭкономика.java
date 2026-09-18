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

/**
 * ВОЕННАЯ ЭКОНОМИКА — сколько в этой игре воюют и что с этого имеют.
 *
 * <p>Заказ дизайнера 19.09.2026: «сколько боёв в играх, сколько игроки получают
 * трофеев, за что и каким образом; сколько у одного игрока максимум уничтожили
 * жетонов за партию и каких; сколько зданий уничтожают в среднем; сколько
 * боеприпасов один игрок тратит за партию».
 *
 * <p>Почему это не выводится из прежних замеров. Они считали СНОСЫ одним числом
 * — «столько-то жетонов за партию». Из такого числа не видно ни того, кого
 * сносят (войско за один трофей или энергостанция четвёртого уровня), ни того,
 * достаётся ли это одному несчастному или размазано по всем, ни того, во что
 * обошлось нападающему. А решать по нему собираются именно это.
 *
 * <p>Что считается:
 * <ul>
 *   <li><b>бой</b> — действий Бой, из них холостых; залпов; попаданий;</li>
 *   <li><b>боеприпасы</b> — сделано за партию, потрачено в бою, остаток;</li>
 *   <li><b>потери</b> — по видам жетонов, отдельно войска и здания; и худшая
 *       доля: сколько потерял САМЫЙ ПОБИТЫЙ игрок партии;</li>
 *   <li><b>трофеи</b> — откуда взялись: с уничтоженных жетонов прямо в бою,
 *       со свалки в Возвращении, из прочих источников (карты, тайлы, награды).</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.ВоеннаяЭкономика [партий] [игроков] [уровень]}.
 */
public final class ВоеннаяЭкономика {

    private ВоеннаяЭкономика() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        List<String> состав = Bots.ROSTER_4;

        double действийБой = 0;
        double холостых = 0;
        double залпов = 0;
        double попаданий = 0;
        double сносов = 0;
        double боеприпасовСделано = 0;
        double боеприпасовПотрачено = 0;
        double боеприпасовОсталось = 0;
        double трофеевЗаУбийство = 0;
        double трофеевСоСвалки = 0;
        double трофеевВНауку = 0;
        double трофеевОсталось = 0;
        double раундов = 0;
        // Кого сносят: подпись жертвы -> сколько раз.
        Map<String, Integer> жертвы = new TreeMap<>();
        // Худшая доля: сколько жетонов потерял самый побитый игрок партии.
        double худшийСредний = 0;
        int худшийЗаВсё = 0;
        // Сколько партий, где игрок не потерял НИ ОДНОГО жетона.
        double безПотерь = 0;

        for (int g = 0; g < партий; g++) {
            long seed = 8_800_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < игроков; i++) {
                agents.add(Bots.create(состав.get((i + g) % состав.size()),
                    Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
            }
            long[] счёт = new long[5];   // попаданий, сносов, боёв, холостых, залпов
            double[] трофеиВНауку = new double[игроков];
            int[] потери = new int[игроков];
            double[] трофеиБой = new double[игроков];
            double[] трофеиСвалка = new double[игроков];
            double[] бпрПотрачено = new double[игроков];
            double[] бпрСделано = new double[игроков];

            new GameEngine(s, agents, ev -> {
                switch (String.valueOf(ev.get("type"))) {
                    case "combat_hit" -> {
                        счёт[0]++;                       // попаданий
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            счёт[1]++;                   // уничтожено жетонов
                        }
                        int место = чис(ev.get("seat"));
                        if (место >= 0 && место < игроков) {
                            бпрПотрачено[место] += чис(ev.get("ammo"));
                            трофеиБой[место] += чис(ev.get("trophy"));
                        }
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            int жертва = чис(ev.get("victim_owner"));
                            if (жертва >= 0 && жертва < игроков) {
                                потери[жертва]++;
                            }
                            жертвы.merge(String.valueOf(ev.get("victim")), 1, Integer::sum);
                        }
                    }
                    case "trophy_to_trophy" -> {
                        int место = чис(ev.get("seat"));
                        if (место >= 0 && место < игроков) {
                            трофеиСвалка[место] += чис(ev.get("gained"));
                        }
                    }
                    case "action" -> {
                        if (!(ev.get("telemetry") instanceof Map<?, ?> м)) {
                            return;
                        }
                        int место = чис(ev.get("seat"));
                        String имя = String.valueOf(ev.get("action"));
                        if ("assembly".equals(имя) && место >= 0 && место < игроков) {
                            бпрСделано[место] += чис(м.get("ammo"));
                        }
                        if ("science".equals(имя) && место >= 0 && место < игроков) {
                            трофеиВНауку[место] += чис(м.get("trophy_spent"));
                        }
                        if ("combat".equals(имя)) {
                            счёт[2]++;                   // действий Бой
                            if (чис(м.get("battle")) <= 0) {
                                счёт[3]++;               // из них холостых
                            }
                            счёт[4] += Math.max(0, чис(м.get("battle")));
                        }
                    }
                    default -> { }
                }
            }).run();

            раундов += s.round;
            int худший = 0;
            for (int i = 0; i < игроков; i++) {
                худший = Math.max(худший, потери[i]);
                if (потери[i] == 0) {
                    безПотерь++;
                }
                трофеевЗаУбийство += трофеиБой[i];
                трофеевСоСвалки += трофеиСвалка[i];
                трофеевВНауку += трофеиВНауку[i];
                боеприпасовПотрачено += бпрПотрачено[i];
                боеприпасовСделано += бпрСделано[i];
            }
            худшийСредний += худший;
            худшийЗаВсё = Math.max(худшийЗаВсё, худший);
            for (PlayerState p : s.players) {
                боеприпасовОсталось += p.resources.ammo();
                трофеевОсталось += p.resources.trophy();
            }
            попаданий += счёт[0];
            сносов += счёт[1];
            действийБой += счёт[2];
            холостых += счёт[3];
            залпов += счёт[4];
        }

        double мест = (double) партий * игроков;
        out.printf("ВОЕННАЯ ЭКОНОМИКА · свод %s · %d партий · %d игроков · уровень %d%n",
            GameConfig.DEFAULT_RULESET, партий, игроков, уровень);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.printf("партия: %.1f раунда%n%n", раундов / партий);

        out.println("БОЙ (на игрока за партию)");
        out.printf("  действий Бой %.2f, из них холостых (ни одного залпа) %.2f (%.0f%%)%n",
            действийБой / мест, холостых / мест,
            действийБой == 0 ? 0 : 100 * холостых / действийБой);
        out.printf("  залпов     %.2f%n", залпов / мест);
        out.printf("  попаданий  %.2f%n", попаданий / мест);
        out.printf("  из них уничтожили жетон  %.2f  (%.0f%% попаданий добивают)%n",
            сносов / мест, попаданий == 0 ? 0 : 100 * сносов / попаданий);

        out.println("\nБОЕПРИПАСЫ (на игрока за партию)");
        out.printf("  сделано в Снаряжении  %.2f%n", боеприпасовСделано / мест);
        out.printf("  потрачено в бою       %.2f%n", боеприпасовПотрачено / мест);
        out.printf("  осталось в конце      %.2f%n", боеприпасовОсталось / мест);
        out.printf("  цена одного сноса: %.2f боеприпаса%n",
            сносов == 0 ? 0 : боеприпасовПотрачено / сносов);

        out.println("\nПОТЕРИ");
        out.printf("  жетонов теряет игрок за партию: %.2f%n", сносов / мест);
        out.printf("  самый побитый игрок партии теряет: %.2f (рекорд за все партии: %d)%n",
            худшийСредний / партий, худшийЗаВсё);
        out.printf("  партий-мест, где игрок не потерял НИЧЕГО: %.0f%%%n",
            100 * безПотерь / мест);

        out.println("\n  КОГО СНОСЯТ (доля всех уничтоженных жетонов)");
        int всегоЖертв = жертвы.values().stream().mapToInt(Integer::intValue).sum();
        double зданий = 0;
        жертвы.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> out.printf("    %-10s %6.2f за партию  (%4.1f%%)%n",
                e.getKey(), (double) e.getValue() / партий,
                100.0 * e.getValue() / Math.max(1, всегоЖертв)));
        for (var e : жертвы.entrySet()) {
            if (зданиеЛи(e.getKey())) {
                зданий += e.getValue();
            }
        }
        out.printf("    ИТОГО зданий уничтожено за партию: %.2f (%.0f%% всех потерь)%n",
            зданий / партий, 100 * зданий / Math.max(1, всегоЖертв));

        out.println("\nТРОФЕИ (на игрока за партию)");
        // «Всего получено» намеренно НЕ печатается: движок такого счётчика не
        // ведёт, и складывать его из видимых источников значило бы выдать
        // догадку за замер. Печатается то, что видно событиями, плюс расход.
        out.printf("  пришло с уничтоженных жетонов (в бою)  %.2f%n",
            трофеевЗаУбийство / мест);
        out.printf("  пришло со свалки в Возвращении         %.2f%n",
            трофеевСоСвалки / мест);
        out.printf("  потрачено в Науке                      %.2f%n",
            трофеевВНауку / мест);
        out.printf("  осталось неистраченными                %.2f%n",
            трофеевОсталось / мест);
    }

    private static boolean зданиеЛи(String подпись) {
        // Подпись жертвы: у зданий это код типа плюс уровень («brk L2»), у войск —
        // просто код рода. Войск ровно четыре вида, всё остальное — здания.
        String к = подпись == null ? "" : подпись.trim();
        return !(к.equals("inf") || к.equals("veh") || к.equals("air") || к.equals("twr"));
    }

    private static int чис(Object o) {
        return o instanceof Number n ? n.intValue() : -1;
    }
}
