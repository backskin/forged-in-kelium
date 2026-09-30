package kelium;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;
import kelium.engine.cards.CardRegistry;
import kelium.engine.cards.EngineCardContext;
import kelium.engine.cards.ObjectiveCard;

/**
 * ПРОВЕРКА НАБОРА (Карты 2.0, 30.09.2026) — старые и новые колоды на одних
 * раздачах, играют стратеги с одной сетью.
 *
 * <p>Главное мерило — РАЗНООБРАЗИЕ СТРАТЕГИЙ: у каждого игрока его «дорога» —
 * развилка, к которой относится большинство выполненных им заданий (по
 * действию, которым задание закрывают). Для победителей считается доля каждой
 * дороги: хороший набор — когда побеждают разными дорогами и ни одна не
 * забирает больше порога. Рядом — сколько карт играется, связки за ход,
 * срабатывания арсенала.
 *
 * <p>Запуск: {@code kelium.ЗамерНабора [партий] [потоков] [свод …]}.
 */
public final class ЗамерНабора {

    private ЗамерНабора() {
    }

    record Итог(double заданий, double арсенала, double связок, double срабатываний,
                String дорогаПобедителя) {
    }

    public static void main(String[] args) throws Exception {
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        int потоков = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        List<String> своды = args.length > 2 ? List.of(args).subList(2, args.length)
            : List.of("1.46.0", "1.47.0");
        Сеть сеть = Files.exists(ОбученныйСтратег.файл()) ? Сеть.загрузить(ОбученныйСтратег.файл()) : null;
        ExecutorService пул = Executors.newFixedThreadPool(потоков);
        Map<String, List<Future<Итог>>> ff = new LinkedHashMap<>();
        for (String свод : своды) {
            List<Future<Итог>> список = new ArrayList<>();
            for (int g = 0; g < партий; g++) {
                final long seed = 8_700_000L + g;
                список.add(пул.submit(() -> партия(свод, seed, сеть)));
            }
            ff.put(свод, список);
        }
        StringBuilder sb = new StringBuilder("# Проверка набора — старые и новые колоды\n\n"
            + партий + " раздач на свод, стратеги с одной сетью. Дорога победителя — развилка"
            + " большинства выполненных им заданий.\n\n| свод | заданий на игрока | арсенала на игрока"
            + " | ходов со связкой на игрока | срабатываний на игрока | дороги победителей |\n"
            + "|---|---|---|---|---|---|\n");
        for (var e : ff.entrySet()) {
            double[] с = new double[4];
            Map<String, Integer> дороги = new java.util.TreeMap<>();
            int n = 0;
            for (Future<Итог> f : e.getValue()) {
                Итог и;
                try {
                    и = f.get();
                } catch (Exception ex) {
                    System.out.println("  партия сорвалась: " + ex.getCause());
                    continue;
                }
                с[0] += и.заданий();
                с[1] += и.арсенала();
                с[2] += и.связок();
                с[3] += и.срабатываний();
                дороги.merge(и.дорогаПобедителя(), 1, Integer::sum);
                n++;
            }
            n = Math.max(1, n);
            StringBuilder д = new StringBuilder();
            for (var x : дороги.entrySet()) {
                д.append(x.getKey()).append(' ').append(Math.round(100.0 * x.getValue() / n)).append("% ");
            }
            sb.append(String.format(java.util.Locale.ROOT, "| %s | %.2f | %.2f | %.2f | %.2f | %s |%n",
                e.getKey(), с[0] / n, с[1] / n, с[2] / n, с[3] / n, д.toString().trim()));
        }
        пул.shutdown();
        Files.writeString(Path.of("design-docs/фигуры/проверка набора.md"), sb.toString(),
            StandardCharsets.UTF_8);
        System.out.println(sb);
    }

    private static Итог партия(String свод, long seed, Сеть сеть) {
        GameConfig база = GameConfig.buildCached(свод, 4, seed, null, null);
        GameState s = Setup.buildGame(LayoutLibrary.configFor(база, 4, seed));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(ЦиклСтратега.стратег(Bots.ROSTER_4.get(i), i, new Random(seed * 31 + i), сеть,
                сеть == null ? 0 : 1.0));
        }
        int[] заданий = new int[1];
        int[] арсенала = new int[1];
        int[] срабатываний = new int[1];
        Map<String, Integer> карт = new HashMap<>();
        Map<Integer, Map<String, Integer>> дороги = new HashMap<>();
        GameEngine.playGame(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            Object место = ev.get("seat");
            String ход = s.round + ":" + s.circle + ":" + место;
            switch (тип) {
                case "objective" -> {
                    заданий[0]++;
                    карт.merge(ход, 1, Integer::sum);
                    if (место instanceof Integer m) {
                        ObjectiveCard oc = CardRegistry.objective(String.valueOf(ev.get("card")));
                        String действие = oc == null ? null : oc.suggestedAction(new EngineCardContext(s, m));
                        String р = действие == null ? "прочее"
                            : String.valueOf(kelium.engine.Срабатывания.развилка(действие));
                        дороги.computeIfAbsent(m, x -> new HashMap<>()).merge(р, 1, Integer::sum);
                    }
                }
                case "arsenal" -> {
                    if ("install".equals(ev.get("mode")) || "burn".equals(ev.get("mode"))) {
                        карт.merge(ход, 1, Integer::sum);
                        if ("install".equals(ev.get("mode"))) {
                            арсенала[0]++;
                        }
                    }
                }
                case "objective_burn" -> карт.merge(ход, 1, Integer::sum);
                case "card_trigger" -> срабатываний[0]++;
                default -> { }
            }
        });
        int связок = 0;
        for (int c : карт.values()) {
            if (c >= 2) {
                связок++;
            }
        }
        String дорога = "без заданий";
        if (s.winner != null && дороги.get(s.winner) != null) {
            дорога = дороги.get(s.winner).entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("без заданий");
        }
        return new Итог(заданий[0] / 4.0, арсенала[0] / 4.0, связок / 4.0, срабатываний[0] / 4.0, дорога);
    }
}
