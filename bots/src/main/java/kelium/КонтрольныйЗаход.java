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
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Setup;

/**
 * КОНТРОЛЬНЫЙ ЗАХОД — партии ГРОССМЕЙСТЕРОВ на действующем своде и новых полях.
 *
 * <p>Заказан дизайнером 19.09.2026 после ревизии заданий: «обучи гроссмейстеров,
 * и только по ним проведи партии сравнения, 100 партий, и дай отчёт подробный».
 * За стол садится действующий состав ({@link Bots#ROSTER_4}) на четвёртом
 * уровне умения — тот самый, чей геном переобучался.
 *
 * <p>Чем отличается от прежних стендов. {@code ПроверкаБотов} меряет, НЕ ЛОМАЕТСЯ
 * ли бот (жжёт ли карты, ставит ли арсенал), а {@code ТемпПоРаундам} — темп
 * партии. Здесь собрано то, что нужно решить именно про эту ревизию:
 * <ul>
 *   <li><b>награды-действия</b> — сколько раз каждое досталось и сколько раз
 *       его удалось РАЗЫГРАТЬ. Награда, которую нечем сыграть, — мёртвая, и
 *       это главный риск всей затеи;</li>
 *   <li><b>усиление вместе с базой</b> — доля заданий, выполненных усиленно;</li>
 *   <li><b>утиль</b> — что сжигают вместо выполнения;</li>
 *   <li>война, производство, модули и деньги — по характерам и по раундам.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.КонтрольныйЗаход [партий] [игроков] [уровень]}.
 */
public final class КонтрольныйЗаход {

    private КонтрольныйЗаход() {
    }

    /** Итоги одного характера по всем партиям. */
    private static final class Счёт {
        int партий;
        int побед;
        double очки;
        double заданийВыполнено;
        double заданийУсиленно;
        double заданийСожжено;
        double боёв;
        double попаданий;
        double сносов;
        double войскПроизведено;
        double боеприпасов;
        double модулейБоя;
        double модулейСборки;
        double позолоты;
        double монетВКонце;
        double келемияВКонце;
        double шаговНаТреках;
    }

    /** Состояние поля на конец раунда — для строки темпа. */
    private static final class Раунд {
        int партий;
        double войска;
        double модулиСборки;
        double монеты;
        double келемий;
        double сносовЗаРаунд;
        double заданийЗаРаунд;
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int партийВсего = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        List<String> состав = Bots.ROSTER_4;

        Map<String, Счёт> по = new LinkedHashMap<>();
        for (String ch : состав) {
            по.put(ch, new Счёт());
        }
        final int МАКС = 14;
        Раунд[] темп = new Раунд[МАКС + 1];
        for (int i = 0; i <= МАКС; i++) {
            темп[i] = new Раунд();
        }
        // Награда-действие: сколько раз выпала и сколько раз реально сыграла.
        Map<String, int[]> награды = new TreeMap<>();
        Map<String, Integer> утиль = new TreeMap<>();
        Map<String, Integer> концовки = new TreeMap<>();
        double раундовВсего = 0;
        double длинаМин = Double.MAX_VALUE;
        double длинаМакс = 0;

        for (int g = 0; g < партийВсего; g++) {
            long seed = 9_100_000L + g;
            GameState s = Setup.buildGame(LayoutLibrary.configFor(игроков, seed));
            List<Agent> agents = new ArrayList<>();
            List<String> кто = new ArrayList<>();
            for (int i = 0; i < игроков; i++) {
                // Характеры СДВИГАЮТСЯ по партиям: иначе каратель всегда сидел бы
                // на одном и том же месте, а место у стола само по себе стоит
                // очков (порядок хода, угол поля).
                String ch = состав.get((i + g) % состав.size());
                кто.add(ch);
                agents.add(Bots.create(ch, Bots.Level.of(уровень), i,
                    new Random(seed * 31 + i), игроков));
            }
            int[] сносыЗаРаунд = {0};
            int[] заданияЗаРаунд = {0};

            new GameEngine(s, agents, ev -> {
                String тип = String.valueOf(ev.get("type"));
                Object место = ev.get("seat");
                Счёт t = место instanceof Number n && n.intValue() < кто.size()
                    ? по.get(кто.get(n.intValue())) : null;
                switch (тип) {
                    case "objective" -> {
                        if (t != null) {
                            t.заданийВыполнено++;
                            if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                                t.заданийУсиленно++;
                            }
                        }
                        заданияЗаРаунд[0]++;
                        if (ev.get("granted") instanceof Map<?, ?> дано
                                && дано.get("base") instanceof Map<?, ?> база) {
                            String имя = база.get("action") != null
                                ? String.valueOf(база.get("action"))
                                : (база.containsKey("gild") ? "позолота" : "—");
                            int[] пара = награды.computeIfAbsent(имя, k -> new int[2]);
                            пара[0]++;
                            // «Сыграно» для действия — признак из freeAction; для
                            // позолоты — единица, если модуль нашёлся.
                            boolean сыграно = база.get("action") != null
                                ? !Boolean.FALSE.equals(база.get("action_ran"))
                                : (база.get("gild") instanceof Number gn && gn.intValue() > 0);
                            if (сыграно) {
                                пара[1]++;
                            }
                            if (t != null && "позолота".equals(имя) && сыграно) {
                                t.позолоты++;
                            }
                        }
                    }
                    case "objective_burn" -> {
                        if (t != null) {
                            t.заданийСожжено++;
                        }
                        Object эф = ev.get("effect");
                        утиль.merge(эф == null ? "—" : String.valueOf(эф), 1, Integer::sum);
                    }
                    case "combat_hit" -> {
                        if (t != null) {
                            t.попаданий++;
                            if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                                t.сносов++;
                            }
                        }
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            сносыЗаРаунд[0]++;
                        }
                    }
                    case "action" -> {
                        if (t != null && ev.get("telemetry") instanceof Map<?, ?> м) {
                            switch (String.valueOf(ev.get("action"))) {
                                case "assembly" -> {
                                    t.войскПроизведено += чис(м.get("units"));
                                    t.боеприпасов += чис(м.get("ammo"));
                                }
                                case "combat" -> t.боёв += чис(м.get("battle"));
                                default -> { }
                            }
                        }
                    }
                    case "return" -> {
                        int r = ev.get("round") instanceof Number n ? n.intValue() : 0;
                        if (r < 1 || r > МАКС) {
                            return;
                        }
                        Раунд a = темп[r];
                        a.партий++;
                        a.сносовЗаРаунд += сносыЗаРаунд[0];
                        a.заданийЗаРаунд += заданияЗаРаунд[0];
                        сносыЗаРаунд[0] = 0;
                        заданияЗаРаунд[0] = 0;
                        for (PlayerState p : s.players) {
                            a.войска += p.unitsOnField().size();
                            a.модулиСборки += p.bluePlacements.size();
                            a.монеты += p.resources.coin();
                            a.келемий += p.resources.kelium();
                        }
                    }
                    default -> { }
                }
            }).run();

            раундовВсего += s.round;
            длинаМин = Math.min(длинаМин, s.round);
            длинаМакс = Math.max(длинаМакс, s.round);
            концовки.merge(String.valueOf(s.winCondition), 1, Integer::sum);
            int лучшие = -1;
            List<Integer> победители = new ArrayList<>();
            for (PlayerState p : s.players) {
                int оч = kelium.engine.Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0);
                Счёт t = по.get(кто.get(p.seat));
                t.партий++;
                t.очки += оч;
                t.монетВКонце += p.resources.coin();
                t.келемияВКонце += p.resources.kelium();
                t.модулейБоя += p.redPlacements.size();
                t.модулейСборки += p.bluePlacements.size();
                for (String трек : s.tech.tracks) {
                    for (int шаг = 0; шаг < s.tech.steps; шаг++) {
                        if (s.tech.occupancy.get(трек).get(шаг).contains(p.seat)) {
                            t.шаговНаТреках++;
                        }
                    }
                }
                if (оч > лучшие) {
                    лучшие = оч;
                    победители.clear();
                    победители.add(p.seat);
                } else if (оч == лучшие) {
                    победители.add(p.seat);
                }
            }
            for (int seat : победители) {
                по.get(кто.get(seat)).побед++;   // ничья засчитывается всем поровну
            }
        }

        // ================== ОТЧЁТ ==================
        out.printf("КОНТРОЛЬНЫЙ ЗАХОД · свод %s · %d партий · %d игроков · уровень %d "
                + "(%s)%n", GameConfig.DEFAULT_RULESET, партийВсего, игроков, уровень,
            уровень == 4 ? "ГРОССМЕЙСТЕР" : "уровень " + уровень);
        out.println("поля: " + LayoutLibrary.describePool(игроков));
        out.printf("партия: в среднем %.1f раунда (от %.0f до %.0f)%n%n",
            раундовВсего / партийВсего, длинаМин, длинаМакс);

        out.println("ХАРАКТЕРЫ");
        out.println("характер   | побед | ПО   | заданий | из них | сожж. | боёв | сносов"
            + " | войск | БПР  | ⚔    | ⚙    | позол| шагов | монет | кел.");
        out.println("           |       |      |         | усил.  |       |      |       "
            + " |       |      |      |      |      |       | в кон.| в кон.");
        for (var e : по.entrySet()) {
            Счёт t = e.getValue();
            double n = Math.max(1, t.партий);
            out.printf("%-10s | %4.0f%% | %4.1f |  %5.2f  | %5.2f  | %5.2f | %4.2f | %5.2f "
                    + " | %5.2f | %4.2f | %4.2f | %4.2f | %4.2f | %5.2f | %5.2f | %4.2f%n",
                e.getKey(), 100.0 * t.побед / n, t.очки / n,
                t.заданийВыполнено / n, t.заданийУсиленно / n, t.заданийСожжено / n,
                t.боёв / n, t.сносов / n, t.войскПроизведено / n, t.боеприпасов / n,
                t.модулейБоя / n, t.модулейСборки / n, t.позолоты / n,
                t.шаговНаТреках / n, t.монетВКонце / n, t.келемияВКонце / n);
        }

        out.println("\nНАГРАДЫ-ДЕЙСТВИЯ: сколько раз выпали и сколько раз сыграли");
        int выпало = 0;
        int сыграло = 0;
        for (var e : награды.entrySet()) {
            int[] пара = e.getValue();
            выпало += пара[0];
            сыграло += пара[1];
            out.printf("  %-12s выпало %4d, сыграно %4d (%3.0f%%)%n",
                e.getKey(), пара[0], пара[1],
                пара[0] == 0 ? 0 : 100.0 * пара[1] / пара[0]);
        }
        out.printf("  ИТОГО        выпало %4d, сыграно %4d (%3.0f%%) — "
                + "мёртвых наград %d%n", выпало, сыграло,
            выпало == 0 ? 0 : 100.0 * сыграло / выпало, выпало - сыграло);

        out.println("\nУТИЛЬ: что сжигают вместо выполнения");
        int всегоУтиля = утиль.values().stream().mapToInt(Integer::intValue).sum();
        for (var e : утиль.entrySet()) {
            out.printf("  %-28s %4d (%3.0f%%)%n", e.getKey(), e.getValue(),
                всегоУтиля == 0 ? 0 : 100.0 * e.getValue() / всегоУтиля);
        }

        out.println("\nТЕМП ПО РАУНДАМ (среднее на игрока, кроме двух правых колонок)");
        out.println("раунд | войск | мод.сборки | монет | келемия | сносов за раунд "
            + "| заданий за раунд");
        for (int r = 1; r <= МАКС; r++) {
            Раунд a = темп[r];
            if (a.партий == 0) {
                continue;
            }
            int мест = a.партий * игроков;
            out.printf("  %2d  | %5.2f |    %5.2f   | %5.2f |  %5.2f  |      %5.2f      "
                    + "|     %5.2f%n",
                r, a.войска / мест, a.модулиСборки / мест, a.монеты / мест,
                a.келемий / мест, a.сносовЗаРаунд / a.партий, a.заданийЗаРаунд / a.партий);
        }

        out.println("\nЧЕМ КОНЧАЛИСЬ ПАРТИИ");
        for (var e : концовки.entrySet()) {
            out.printf("  %-28s %4d (%3.0f%%)%n", e.getKey(), e.getValue(),
                100.0 * e.getValue() / партийВсего);
        }
    }

    private static double чис(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
