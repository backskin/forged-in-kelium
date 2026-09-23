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
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КУДА ДЕВАЮТСЯ ДЕЙСТВИЯ (23.09.2026). Замер дуги показал 5,3 удачных действия
 * за раунд на игрока — меньше, чем дают карты приказов. Здесь — каждое
 * действие по имени: сколько сыграно, сколько вышло пустым и с какой причиной.
 *
 * <p>Запуск: {@code kelium.ПровалыДействий [партий] [уровень]}.
 */
public final class ПровалыДействий {

    private ПровалыДействий() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 6;
        int уровень = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        Map<String, int[]> поДействию = new TreeMap<>();      // [удачно, пусто]
        Map<String, Integer> причины = new TreeMap<>();
        Map<String, Integer> типы = new TreeMap<>();
        int[] раундов = {0};
        for (int g = 0; g < партий; g++) {
            long seed = 9_100_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(4, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                agents.add(Bots.create(Bots.ROSTER_4.get(i), Bots.Level.of(уровень), i,
                    new Random(seed * 31 + i), 4));
            }
            GameEngine.playGame(s, agents, ev -> {
                String тип = String.valueOf(ev.get("type"));
                типы.merge(тип, 1, Integer::sum);
                if ("turn_orders".equals(тип)) {
                    int можно = ev.get("top_allowed") instanceof Number n ? n.intValue() : 0;
                    типы.merge("~ходов", 1, Integer::sum);
                    типы.merge("~разрешено сверху", можно, Integer::sum);
                    if (Boolean.TRUE.equals(ev.get("coincided"))) {
                        типы.merge("~совпал приказ", 1, Integer::sum);
                    }
                    if (Boolean.TRUE.equals(ev.get("bottom_open"))) {
                        типы.merge("~нижний открыт", 1, Integer::sum);
                    }
                }
                if ("action".equals(тип)) {
                    String имя = String.valueOf(ev.get("action"));
                    boolean ok = Boolean.TRUE.equals(ev.get("ok"));
                    поДействию.computeIfAbsent(имя, k -> new int[2])[ok ? 0 : 1]++;
                    if (!ok) {
                        String д = String.valueOf(ev.get("detail"));
                        причины.merge(имя + ": " + (д.length() > 70 ? д.substring(0, 70) : д),
                            1, Integer::sum);
                    }
                }
            });
            раундов[0] += s.round;
        }
        double игрокоРаундов = 4.0 * раундов[0];
        out.printf("свод %s · партий %d · уровень %d · раундов %d%n%n",
            GameConfig.DEFAULT_RULESET, партий, уровень, раундов[0]);
        out.println("действие          удачно/иг.раунд  пусто/иг.раунд");
        поДействию.forEach((k, v) -> out.printf("  %-16s %6.2f          %6.2f%n", k,
            v[0] / игрокоРаундов, v[1] / игрокоРаундов));
        out.println("\nпричины пустых (раз за все партии):");
        причины.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(25)
            .forEach(e -> out.printf("  %5d  %s%n", e.getValue(), e.getKey()));
        out.println("\nсобытия на игрока за раунд:");
        типы.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(30)
            .forEach(e -> out.printf("  %6.2f  %s%n", e.getValue() / игрокоРаундов, e.getKey()));
    }
}
