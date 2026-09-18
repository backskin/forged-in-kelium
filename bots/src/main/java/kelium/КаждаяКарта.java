package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КАЖДАЯ ЛИ КАРТА ИГРАЕТСЯ — поимённый отчёт по колодам заданий и арсенала.
 *
 * <p>Заказ дизайнера 19.09.2026: «научить играть ВСЕ карты заданий, проверить,
 * что все они могут быть сыграны и играются».
 *
 * <p>Средние числа на этот вопрос не отвечают. «Выполнено 13 заданий за партию»
 * может означать и то, что играются все сорок понемногу, и то, что играются
 * шесть лёгких, а тридцать четыре всегда уходят в утиль. Разница между этими
 * двумя мирами — вся колода, и видна она только поимённо.
 *
 * <p>По каждой карте задания считается:
 * <ul>
 *   <li><b>пришла</b> — сколько раз попадала в руку;</li>
 *   <li><b>выполнена</b> и <b>усиленно</b> — сколько раз доведена до награды;</li>
 *   <li><b>сожжена</b> — сколько раз ушла в утиль вместо выполнения;</li>
 *   <li><b>доля выполнения</b> — выполнена / пришла. Это и есть мера
 *       «играется ли карта»: ноль значит, что карта в игре не существует.</li>
 * </ul>
 *
 * <p>Для арсенала — пришла, поставлена, сожжена, сыграна спец-действием.
 *
 * <p>Запуск: {@code kelium.КаждаяКарта [партий] [игроков] [уровень]}.
 */
public final class КаждаяКарта {

    private КаждаяКарта() {
    }

    private static final class Счёт {
        int пришла;
        int выполнена;
        int усиленно;
        int сожжена;
        int поставлена;
        int спец;
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        List<String> состав = Bots.ROSTER_4;

        Map<String, Счёт> задания = new TreeMap<>();
        Map<String, Счёт> арсенал = new TreeMap<>();
        Map<String, String> имена = new LinkedHashMap<>();

        for (int g = 0; g < партий; g++) {
            long seed = 4_400_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < игроков; i++) {
                agents.add(Bots.create(состав.get((i + g) % состав.size()),
                    Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
            }
            if (g == 0) {
                собратьИмена(s, имена);
            }
            new GameEngine(s, agents, ev -> {
                String тип = String.valueOf(ev.get("type"));
                String карта = ev.get("card") == null ? null : String.valueOf(ev.get("card"));
                switch (тип) {
                    case "objective_drawn" -> {
                        if (карта != null) {
                            задания.computeIfAbsent(карта, k -> new Счёт()).пришла++;
                        }
                    }
                    case "objective" -> {
                        if (карта != null) {
                            Счёт c = задания.computeIfAbsent(карта, k -> new Счёт());
                            c.выполнена++;
                            if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                                c.усиленно++;
                            }
                        }
                    }
                    case "objective_burn" -> {
                        if (карта != null) {
                            задания.computeIfAbsent(карта, k -> new Счёт()).сожжена++;
                        }
                    }
                    case "arsenal" -> {
                        if (карта != null) {
                            Счёт c = арсенал.computeIfAbsent(карта, k -> new Счёт());
                            if ("install".equals(ev.get("mode"))) {
                                c.поставлена++;
                            } else if ("burn".equals(ev.get("mode"))) {
                                c.сожжена++;
                            } else {
                                c.пришла++;
                            }
                        }
                    }
                    case "arsenal_spec_use" -> {
                        if (карта != null) {
                            арсенал.computeIfAbsent(карта, k -> new Счёт()).спец++;
                        }
                    }
                    default -> { }
                }
            }).run();
        }

        out.printf("КАЖДАЯ КАРТА · свод %s · %d партий · %d игроков · уровень %d%n",
            GameConfig.DEFAULT_RULESET, партий, игроков, уровень);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.println();

        печатьЗаданий(out, задания, имена, партий);
        печатьАрсенала(out, арсенал, имена, партий);
    }

    private static void собратьИмена(GameState s, Map<String, String> имена) {
        for (String колода : new String[] {"objectives", "arsenal"}) {
            try {
                for (Map<String, Object> c : kelium.dataio.Ctx.cards(s, колода).entries) {
                    имена.put(String.valueOf(c.get("id")),
                        String.valueOf(c.getOrDefault("name", "")));
                }
            } catch (RuntimeException нет) {
                // колоды в этой партии нет — не беда
            }
        }
    }

    private static void печатьЗаданий(PrintStream out, Map<String, Счёт> задания,
                                      Map<String, String> имена, int партий) {
        out.println("ЗАДАНИЯ — поимённо (на 100 партий)");
        out.println("карта  имя                        пришла  выполн.  усил.  сожжена  доля");
        List<Map.Entry<String, Счёт>> строки = new ArrayList<>(задания.entrySet());
        строки.sort((a, b) -> Double.compare(доля(a.getValue()), доля(b.getValue())));
        int мёртвых = 0;
        int редких = 0;
        for (var e : строки) {
            Счёт c = e.getValue();
            double д = доля(c);
            if (c.выполнена == 0) {
                мёртвых++;
            } else if (д < 0.10) {
                редких++;
            }
            out.printf("%-6s %-26s %6.1f  %6.1f  %5.1f  %7.1f  %4.0f%%%n",
                e.getKey(), обрезать(имена.getOrDefault(e.getKey(), ""), 26),
                100.0 * c.пришла / партий, 100.0 * c.выполнена / партий,
                100.0 * c.усиленно / партий, 100.0 * c.сожжена / партий, 100 * д);
        }
        out.printf("%nВСЕГО карт заданий в игре: %d. НИ РАЗУ НЕ ВЫПОЛНЕНЫ: %d. "
            + "Выполняются реже чем в 10%% случаев: %d.%n%n", строки.size(), мёртвых, редких);
    }

    private static void печатьАрсенала(PrintStream out, Map<String, Счёт> арсенал,
                                       Map<String, String> имена, int партий) {
        out.println("АРСЕНАЛ — поимённо (на 100 партий)");
        out.println("карта  имя                        поставл.  сожжена  спец-действий");
        List<Map.Entry<String, Счёт>> строки = new ArrayList<>(арсенал.entrySet());
        строки.sort((a, b) -> a.getValue().поставлена - b.getValue().поставлена);
        int неставились = 0;
        for (var e : строки) {
            Счёт c = e.getValue();
            if (c.поставлена == 0) {
                неставились++;
            }
            out.printf("%-6s %-26s %8.1f  %7.1f  %10.1f%n",
                e.getKey(), обрезать(имена.getOrDefault(e.getKey(), ""), 26),
                100.0 * c.поставлена / партий, 100.0 * c.сожжена / партий,
                100.0 * c.спец / партий);
        }
        out.printf("%nВСЕГО карт арсенала в игре: %d. НИ РАЗУ НЕ ПОСТАВЛЕНЫ: %d.%n",
            строки.size(), неставились);
    }

    private static double доля(Счёт c) {
        return c.пришла == 0 ? 0 : (double) c.выполнена / c.пришла;
    }

    private static String обрезать(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }
}
