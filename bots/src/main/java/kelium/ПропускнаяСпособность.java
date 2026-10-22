package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * ПРОПУСКНАЯ СПОСОБНОСТЬ ХОДА — сколько всего игрок УСПЕВАЕТ за один ход.
 *
 * <p>Зачем это отдельный стенд. Поимённый отчёт по картам (kelium.КаждаяКарта)
 * показал: три задания из сорока не выполняются НИ РАЗУ за двести партий, ещё
 * восемь — реже чем в одном случае из десяти. У всех мёртвых карт одна и та же
 * примета: условие просит ДВА-ТРИ события В ОДИН ХОД («забери 3 контейнера»,
 * «построй 2 здания», «потрать трофеи тремя разными способами»).
 *
 * <p>Гипотеза, которую надо доказать или опровергнуть: такие условия мертвы не
 * потому, что боты их не понимают, а потому, что за один ход в этой игре
 * физически не происходит двух-трёх однотипных событий. Если так, никакое
 * обучение их не откроет, и лечится это только правкой условий.
 *
 * <p>Здесь считается РАСПРЕДЕЛЕНИЕ по ходам: для каждого вида события — сколько
 * их случилось за ход, и в какой доле ходов их было 1, 2, 3 и больше. Карту
 * «сделай N за ход» можно выполнить не чаще, чем доля ходов с N событиями, —
 * это верхняя граница, ещё до всякой стратегии.
 *
 * <p>Ход здесь — один игрок в одном раунде: между событиями {@code turn_orders}
 * (или между «reveal» и концом раунда, если события хода не размечены).
 *
 * <p>Запуск: {@code kelium.ПропускнаяСпособность [партий] [игроков] [уровень]}.
 */
public final class ПропускнаяСпособность {

    private ПропускнаяСпособность() {
    }

    /** Распределение числа событий одного вида по ходам. */
    private static final class Разброс {
        long ходов;
        long всего;
        long максимум;
        final long[] сколькоРаз = new long[8];   // 0,1,2,...,6, 7+

        void добавить(int n) {
            ходов++;
            всего += n;
            максимум = Math.max(максимум, n);
            сколькоРаз[Math.min(7, n)]++;
        }

        double среднее() {
            return ходов == 0 ? 0 : (double) всего / ходов;
        }

        /** Доля ходов, где событий было не меньше n. */
        double неМенее(int n) {
            if (ходов == 0) {
                return 0;
            }
            long c = 0;
            for (int i = Math.min(7, n); i < сколькоРаз.length; i++) {
                c += сколькоРаз[i];
            }
            return (double) c / ходов;
        }
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партий = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        List<String> состав = Bots.ROSTER_4;

        Map<String, Разброс> виды = new LinkedHashMap<>();
        for (String в : new String[] {"контейнеров взято", "строй-операций",
            "жетонов снесено", "войск нанято", "боеприпасов сделано",
            "перемещений", "залпов", "трофеев в науку", "сделок на рынке",
            "шагов на треках", "заданий выполнено"}) {
            виды.put(в, new Разброс());
        }

        for (int g = 0; g < партий; g++) {
            long seed = 6_600_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < игроков; i++) {
                agents.add(Bots.create(состав.get((i + g) % состав.size()),
                    Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
            }
            // Счётчики ТЕКУЩЕГО хода по каждому месту. Ход закрывается, когда
            // место получает следующий приказ или когда кончается раунд.
            int[][] за = new int[игроков][виды.size()];
            List<String> ключи = new ArrayList<>(виды.keySet());

            new GameEngine(s, agents, ev -> {
                String тип = String.valueOf(ev.get("type"));
                Object местоОб = ev.get("seat");
                int место = местоОб instanceof Number n ? n.intValue() : -1;
                if ("turn_end".equals(тип) || "return".equals(тип)) {
                    // Закрыть ходы: на «turn_end» — одного места, на «return» — всех.
                    for (int m = 0; m < игроков; m++) {
                        if (место >= 0 && m != место && "turn_end".equals(тип)) {
                            continue;
                        }
                        boolean былиСобытия = false;
                        for (int k = 0; k < ключи.size(); k++) {
                            былиСобытия |= за[m][k] > 0;
                        }
                        if (былиСобытия || "turn_end".equals(тип)) {
                            for (int k = 0; k < ключи.size(); k++) {
                                виды.get(ключи.get(k)).добавить(за[m][k]);
                                за[m][k] = 0;
                            }
                        }
                    }
                    return;
                }
                if (место < 0 || место >= игроков) {
                    return;
                }
                switch (тип) {
                    // КОНТЕЙНЕР ПРИХОДИТ ДВУМЯ ПУТЯМИ, и первый замер знал только
                    // один. Телеметрия Добычи считает контейнеры, взятые
                    // ДОБЫТЧИКОМ; печатные ячейки поля дают контейнер войску,
                    // вошедшему на гекс, и это отдельное событие. Из-за пропуска
                    // второго пути стенд показал «0.02 контейнера за ход» и я
                    // объявил карту o08 невозможной. На деле контейнеры берут
                    // около 3.5 раза за партию на игрока — почти все движением.
                    case "container" -> за[место][ключи.indexOf("контейнеров взято")]++;
                    case "objective" -> за[место][ключи.indexOf("заданий выполнено")]++;
                    case "combat_hit" -> {
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            за[место][ключи.indexOf("жетонов снесено")]++;
                        }
                    }
                    case "action" -> {
                        if (!(ev.get("telemetry") instanceof Map<?, ?> м)) {
                            return;
                        }
                        switch (String.valueOf(ev.get("action"))) {
                            // В телеметрии Стройки одно число — «ops»: сколько
                            // строительных операций сделано за действие (и
                            // постановка, и снос считаются операцией). Раздельных
                            // счётчиков там нет, поэтому раздельно их здесь и не
                            // выдумываем: карта «построй 2 здания за ход» упирается
                            // ровно в число операций.
                            case "build" -> за[место][ключи.indexOf("строй-операций")]
                                += ц(м.get("ops"));
                            case "assembly" -> {
                                за[место][ключи.indexOf("войск нанято")] += ц(м.get("units"));
                                за[место][ключи.indexOf("боеприпасов сделано")] += ц(м.get("ammo"));
                            }
                            // Добыча уже учтена событием «container» выше —
                            // здесь считать второй раз нельзя.
                            case "mining" -> { }
                            case "movement" -> за[место][ключи.indexOf("перемещений")]
                                += ц(м.get("moves"));
                            case "combat" -> за[место][ключи.indexOf("залпов")]
                                += ц(м.get("battle"));
                            case "science" -> {
                                за[место][ключи.indexOf("трофеев в науку")]
                                    += ц(м.get("trophy_spent"));
                                за[место][ключи.indexOf("шагов на треках")]
                                    += ц(м.get("steps"));
                            }
                            case "market" -> за[место][ключи.indexOf("сделок на рынке")]
                                += ц(м.get("deals"));
                            default -> { }
                        }
                    }
                    default -> { }
                }
            }).run();
        }

        out.printf("ПРОПУСКНАЯ СПОСОБНОСТЬ ХОДА · свод %s · %d партий · %d игроков%n",
            GameConfig.DEFAULT_RULESET, партий, игроков);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.println("\nСколько событий каждого вида случается за ОДИН ход одного игрока.");
        out.println("«доля ходов с N» — верхняя граница выполнимости задания «сделай N за ход».\n");
        out.printf("%-22s %8s %6s %9s %9s %9s%n", "вид события", "среднее", "макс",
            "ходов с 1", "ходов с 2", "ходов с 3");
        out.println("-".repeat(70));
        for (var e : виды.entrySet()) {
            Разброс р = e.getValue();
            out.printf("%-22s %8.2f %6d %8.1f%% %8.1f%% %8.1f%%%n", e.getKey(),
                р.среднее(), р.максимум, 100 * р.неМенее(1), 100 * р.неМенее(2),
                100 * р.неМенее(3));
        }
    }

    private static int ц(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
