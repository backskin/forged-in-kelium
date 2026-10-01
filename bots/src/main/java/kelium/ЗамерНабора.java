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
                String дорогаПобедителя, double урона, double уничтожено, boolean воинПобедил,
                double сухихБоёв, double сухихБезБоеприпасов, double сухихСКарты,
                double уронаЗолотом, double уронаСупер) {
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
            + " | ходов со связкой на игрока | срабатываний на игрока | урона нанесено на игрока (золотым модулем / супер-войском)"
            + " | уничтожено жетонов на игрока | победил самый воинственный"
            + " | сухих боёв на игрока (без боеприпасов / с карты) | дороги победителей |\n"
            + "|---|---|---|---|---|---|---|---|---|---|\n");
        for (var e : ff.entrySet()) {
            double[] с = new double[12];
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
                с[4] += и.урона();
                с[5] += и.уничтожено();
                с[6] += и.воинПобедил() ? 1 : 0;
                с[7] += и.сухихБоёв();
                с[8] += и.сухихБезБоеприпасов();
                с[9] += и.сухихСКарты();
                с[10] += и.уронаЗолотом();
                с[11] += и.уронаСупер();
                дороги.merge(и.дорогаПобедителя(), 1, Integer::sum);
                n++;
            }
            n = Math.max(1, n);
            StringBuilder д = new StringBuilder();
            for (var x : дороги.entrySet()) {
                д.append(x.getKey()).append(' ').append(Math.round(100.0 * x.getValue() / n)).append("% ");
            }
            sb.append(String.format(java.util.Locale.ROOT,
                "| %s | %.2f | %.2f | %.2f | %.2f | %.2f (%.2f / %.2f) | %.2f | %.0f%% | %.2f (%.2f / %.2f) | %s |%n",
                e.getKey(), с[0] / n, с[1] / n, с[2] / n, с[3] / n, с[4] / n, с[10] / n, с[11] / n, с[5] / n,
                100 * с[6] / n, с[7] / n, с[8] / n, с[9] / n, д.toString().trim()));
        }
        пул.shutdown();
        // у каждого набора сводов свой файл: два замера разом не затирают друг друга
        String файл = своды.equals(List.of("1.46.0", "1.47.0")) ? "проверка набора.md"
            : "проверка набора (" + String.join(" ", своды) + ").md";
        Files.writeString(Path.of("design-docs/фигуры").resolve(файл), sb.toString(),
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
        int[] урона = new int[1];
        int[] уничтожено = new int[1];
        int[] убийств = new int[4];
        int[] сухих = new int[3];      // боёв без попадания; из них без боеприпасов; из всех — с карты
        boolean[] сухойЖдёт = new boolean[4];
        int[] особых = new int[2];     // урон с золотого модуля боя; урон супер-войска
        GameEngine.playGame(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            Object место = ev.get("seat");
            String ход = s.round + ":" + s.circle + ":" + место;
            switch (тип) {
                case "combat_dry" -> {
                    сухих[0]++;
                    if ("нет боеприпасов".equals(ev.get("reason"))) {
                        сухих[1]++;
                    }
                    if (место instanceof Integer m && m >= 0 && m < 4) {
                        сухойЖдёт[m] = true;
                    }
                }
                // событие ветки приходит ПОСЛЕ боя: тогда и видно, чей был сухой бой —
                // с карты (free) или выбранный игроком
                case "action" -> {
                    if (место instanceof Integer m && m >= 0 && m < 4 && "combat".equals(ev.get("action"))
                            && сухойЖдёт[m]) {
                        if (Boolean.TRUE.equals(ev.get("free"))) {
                            сухих[2]++;
                        }
                        сухойЖдёт[m] = false;
                    }
                }
                case "combat_hit" -> {
                    урона[0]++;
                    boolean супер = String.valueOf(ev.get("attacker")).contains(".super");
                    if (супер) {
                        особых[1]++;
                    } else if (Boolean.TRUE.equals(ev.get("gold"))) {
                        особых[0]++;
                    }
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        уничтожено[0]++;
                        if (место instanceof Integer m && m >= 0 && m < 4) {
                            убийств[m]++;
                        }
                    }
                }
                case "objective" -> {
                    заданий[0]++;
                    карт.merge(ход, 1, Integer::sum);
                    if (место instanceof Integer m) {
                        ObjectiveCard oc = CardRegistry.objective(String.valueOf(ev.get("card")));
                        // ДОРОГА — ЗНАЧОК РАЗВИЛКИ НА КАРТЕ. У карт языка подсказки
                        // действия для состояний нет (90% уходило в «прочее»), а у
                        // старых она почти всегда «Добыть» — мерило врало в обе стороны.
                        String р;
                        if (oc instanceof kelium.cards.objectives.ЗаданиеИзЯзыка з && з.значок() != null) {
                            р = з.значок();
                        } else {
                            String действие = oc == null ? null : oc.suggestedAction(new EngineCardContext(s, m));
                            р = действие == null ? "прочее"
                                : String.valueOf(kelium.engine.Срабатывания.развилка(действие));
                        }
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
        // самый воинственный — больше всех уничтожил; при равенстве — никто
        int воин = -1;
        for (int i = 0; i < 4; i++) {
            if (убийств[i] > 0 && (воин < 0 || убийств[i] > убийств[воин])) {
                воин = i;
            }
        }
        for (int i = 0; i < 4 && воин >= 0; i++) {
            if (i != воин && убийств[i] == убийств[воин]) {
                воин = -1;
            }
        }
        boolean воинПобедил = воин >= 0 && s.winner != null && s.winner == воин;
        return new Итог(заданий[0] / 4.0, арсенала[0] / 4.0, связок / 4.0, срабатываний[0] / 4.0, дорога,
            урона[0] / 4.0, уничтожено[0] / 4.0, воинПобедил, сухих[0] / 4.0, сухих[1] / 4.0,
            сухих[2] / 4.0, особых[0] / 4.0, особых[1] / 4.0);
    }
}
