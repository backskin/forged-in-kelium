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
import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ПАРТИИ ПО ПОЛЯМ — подробный разбор, как играется игра на КАЖДОЙ раскладке.
 *
 * <p>Заказ дизайнера 19.09.2026: «прогнать кучу партий с новыми картами заданий
 * и на новых полях, и по каждому полю рассказать, что там», «очень подробно —
 * как партии играются, что боты делают».
 *
 * <p>Почему именно по полям, а не в среднем. Геометрия меняет эту игру сильнее
 * любых боевых правил: разброс уничтоженных жетонов между раскладками доходил
 * до 140%. Среднее по всем полям склеивает тесную раскладку с просторной и
 * прячет ровно то, ради чего поля и перерисовывались, — станет ли теснее
 * и будет ли больше столкновений.
 *
 * <p>Что собирается на каждом поле:
 * <ul>
 *   <li><b>ход партии</b> — длина, чем кончилась, сколько очков у победителя;</li>
 *   <li><b>что боты делают</b> — сколько раз за партию играется каждое из
 *       восьми действий и сколько из них впустую (Бой без единого залпа);</li>
 *   <li><b>задания</b> — сколько пришло, выполнено, сожжено, из них усиленно;
 *       какие награды-действия выпали и сколько удалось разыграть;</li>
 *   <li><b>война</b> — боёв, попаданий, снесённых жетонов, погибших ЦУ;</li>
 *   <li><b>хозяйство</b> — войск и боеприпасов произведено, зданий на поле,
 *       контейнеров взято, шагов на треках, модулей, денег и келемия;</li>
 *   <li><b>откуда очки</b> — разбивка итогового счёта по источникам.</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.ПартииПоПолям [партий на поле] [игроков] [уровень]}.
 */
public final class ПартииПоПолям {

    private ПартииПоПолям() {
    }

    /** Всё, что собрано по одному полю. */
    private static final class Поле {
        final String имя;
        int партий;
        double раундов;
        double раундовМин = 99;
        double раундовМакс;
        double очковПобедителя;
        double очковСредних;
        double разрывПобедителя;          // победитель минус второй
        final Map<String, Integer> концовки = new TreeMap<>();
        final Map<String, Integer> действия = new TreeMap<>();
        final Map<String, Integer> вхолостую = new TreeMap<>();
        double заданийПришло;
        double заданийВыполнено;
        double заданийУсиленно;
        double заданийСожжено;
        final Map<String, int[]> награды = new TreeMap<>();
        double боёв;
        double попаданий;
        double сносов;
        double цуСнесено;
        double войск;
        double боеприпасов;
        double зданийВКонце;
        double войскВКонце;
        double контейнеров;
        double шагов;
        // КОНТАКТ: как далеко армии друг от друга. Замер 19.09.2026 показал, что
        // 43% действий Бой уходят вхолостую, и в 99% этих случаев стрелять было
        // НЕКОГО — то есть дело не в решениях бота, а в расстоянии.
        double суммаРасстояний;
        int замеровРасстояния;
        double ходовСКонтактом;      // у игрока есть чужой жетон на соседнем гексе
        int замеровКонтакта;
        double модулейБоя;
        double модулейСборки;
        double монетВКонце;
        double келемияВКонце;
        double трофеевВКонце;
        final Map<String, Double> очкиПоИсточникам = new TreeMap<>();

        Поле(String имя) {
            this.имя = имя;
        }
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int наПоле = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int игроков = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int уровень = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        List<String> состав = Bots.ROSTER_4;
        List<LayoutLibrary.Entry> поля = LayoutLibrary.pool(игроков);

        out.printf("ПАРТИИ ПО ПОЛЯМ · свод %s · задания 1.18.0 · %d игроков · уровень %d%n",
            GameConfig.DEFAULT_RULESET, игроков, уровень);
        out.printf("раскладок %d, партий на каждой %d, всего %d%n%n",
            поля.size(), наПоле, поля.size() * наПоле);

        Map<String, Поле> итоги = new LinkedHashMap<>();
        for (LayoutLibrary.Entry e : поля) {
            Поле п = new Поле(e.id());
            итоги.put(e.id(), п);
            for (int g = 0; g < наПоле; g++) {
                сыграть(e, g, игроков, уровень, состав, п);
            }
        }

        for (Поле п : итоги.values()) {
            печать(out, п, игроков);
        }
        сводка(out, итоги, игроков);
    }

    private static void сыграть(LayoutLibrary.Entry поле, int номер, int игроков,
                                int уровень, List<String> состав, Поле п) {
        long seed = 3_300_000L + номер;
        // КОНКРЕТНАЯ РАСКЛАДКА, а не выбранная по зерну: нам нужно сравнить поля
        // между собой, поэтому поле задаётся, а меняется только раздача.
        GameConfig база = GameConfig.buildCached(GameConfig.DEFAULT_RULESET,
            игроков, seed, null, null);
        GameConfig cfg = new GameConfig(база.ruleset, база.content, игроков, seed,
            база.dataRoot, база.boardSides, поле.id(), база.cuFacing, поле.file());
        GameState s = Setup.buildGame(cfg);
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < игроков; i++) {
            agents.add(Bots.create(состав.get((i + номер) % состав.size()),
                Bots.Level.of(уровень), i, new Random(seed * 31 + i), игроков));
        }

        new GameEngine(s, agents, ev -> {
            String тип = String.valueOf(ev.get("type"));
            switch (тип) {
                case "action" -> {
                    String имя = String.valueOf(ev.get("action"));
                    п.действия.merge(имя, 1, Integer::sum);
                    if (ev.get("telemetry") instanceof Map<?, ?> м) {
                        switch (имя) {
                            case "assembly" -> {
                                п.войск += чис(м.get("units"));
                                п.боеприпасов += чис(м.get("ammo"));
                            }
                            case "combat" -> {
                                double залпов = чис(м.get("battle"));
                                п.боёв += залпов;
                                if (залпов == 0) {
                                    п.вхолостую.merge("combat", 1, Integer::sum);
                                }
                            }
                            default -> { }
                        }
                    }
                }
                case "objective_drawn" -> п.заданийПришло++;
                case "objective" -> {
                    п.заданийВыполнено++;
                    if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                        п.заданийУсиленно++;
                    }
                    if (ev.get("granted") instanceof Map<?, ?> дано
                            && дано.get("base") instanceof Map<?, ?> база2) {
                        String имя = база2.get("action") != null
                            ? String.valueOf(база2.get("action"))
                            : (база2.containsKey("gild") ? "позолота" : "—");
                        int[] пара = п.награды.computeIfAbsent(имя, k -> new int[2]);
                        пара[0]++;
                        boolean сыграно = база2.get("action") != null
                            ? !Boolean.FALSE.equals(база2.get("action_ran"))
                            : (база2.get("gild") instanceof Number gn && gn.intValue() > 0);
                        if (сыграно) {
                            пара[1]++;
                        }
                    }
                }
                case "objective_burn" -> п.заданийСожжено++;
                case "combat_hit" -> {
                    п.попаданий++;
                    if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                        п.сносов++;
                    }
                }
                case "cu_destroyed" -> п.цуСнесено++;
                case "container" -> п.контейнеров++;
                // КОНЕЦ РАУНДА — снимок расстояний между армиями. Прежде этот
                // замер был написан, но НЕ ВЫЗЫВАЛСЯ, и отчёт печатал нули.
                case "return" -> замерКонтакта(s, п);
                default -> { }
            }
        }).run();

        п.партий++;
        п.раундов += s.round;
        п.раундовМин = Math.min(п.раундовМин, s.round);
        п.раундовМакс = Math.max(п.раундовМакс, s.round);
        п.концовки.merge(String.valueOf(s.winCondition), 1, Integer::sum);

        List<Integer> очки = new ArrayList<>();
        for (PlayerState p : s.players) {
            Map<String, Integer> разбор = Scoring.scorePlayer(s, p.seat);
            int всего = разбор.getOrDefault("total", 0);
            очки.add(всего);
            п.очковСредних += всего;
            for (var e : разбор.entrySet()) {
                if (!"total".equals(e.getKey())) {
                    п.очкиПоИсточникам.merge(e.getKey(), (double) e.getValue(), Double::sum);
                }
            }
            for (BuildingToken b : p.buildingsOnField()) {
                if (b != null) {
                    п.зданийВКонце++;
                }
            }
            п.войскВКонце += p.unitsOnField().size();
            п.монетВКонце += p.resources.coin();
            п.келемияВКонце += p.resources.kelium();
            п.трофеевВКонце += p.resources.trophy();
            п.модулейБоя += p.redPlacements.size();
            п.модулейСборки += p.bluePlacements.size();
            for (String трек : s.tech.tracks) {
                for (int шаг = 0; шаг < s.tech.steps; шаг++) {
                    if (s.tech.occupancy.get(трек).get(шаг).contains(p.seat)) {
                        п.шагов++;
                    }
                }
            }
        }
        очки.sort((a, b) -> b - a);
        п.очковПобедителя += очки.get(0);
        if (очки.size() > 1) {
            п.разрывПобедителя += очки.get(0) - очки.get(1);
        }
    }

    /**
     * КАК ДАЛЕКО АРМИИ. Для каждого игрока берётся ближайшее расстояние от его
     * жетона до чужого — в гексах, поиском в ширину по полю. Ноль означает, что
     * жетоны стоят на одном гексе, единица — на соседних (то есть в досягаемости
     * наземного удара).
     */
    private static void замерКонтакта(GameState s, Поле п) {
        for (PlayerState p : s.players) {
            List<String> мои = new ArrayList<>();
            for (kelium.core.Token t : p.unitsOnField()) {
                if (t.hexId() != null) {
                    мои.add(t.hexId());
                }
            }
            if (мои.isEmpty()) {
                continue;
            }
            java.util.Set<String> чужие = new java.util.HashSet<>();
            for (PlayerState o : s.players) {
                if (o.seat == p.seat) {
                    continue;
                }
                for (kelium.core.Token t : o.unitsOnField()) {
                    if (t.hexId() != null) {
                        чужие.add(t.hexId());
                    }
                }
                for (kelium.core.BuildingToken b : o.buildingsOnField()) {
                    if (b.hexId != null) {
                        чужие.add(b.hexId);
                    }
                }
            }
            if (чужие.isEmpty()) {
                continue;
            }
            int лучшее = Integer.MAX_VALUE;
            for (String от : мои) {
                лучшее = Math.min(лучшее, вШирину(s, от, чужие, лучшее));
            }
            if (лучшее == Integer.MAX_VALUE) {
                continue;
            }
            п.суммаРасстояний += лучшее;
            п.замеровРасстояния++;
            п.замеровКонтакта++;
            if (лучшее <= 1) {
                п.ходовСКонтактом++;
            }
        }
    }

    /** Кратчайший путь по гексам от {@code от} до ближайшего из {@code цели}. */
    private static int вШирину(GameState s, String от, java.util.Set<String> цели,
                               int неХужеЧем) {
        if (цели.contains(от)) {
            return 0;
        }
        java.util.Deque<String> очередь = new java.util.ArrayDeque<>();
        java.util.Map<String, Integer> глубина = new java.util.HashMap<>();
        очередь.add(от);
        глубина.put(от, 0);
        while (!очередь.isEmpty()) {
            String h = очередь.poll();
            int d = глубина.get(h);
            if (d >= неХужеЧем) {
                continue;             // дальше уже не улучшим
            }
            for (String nb : s.field.neighbors(h)) {
                if (глубина.containsKey(nb)) {
                    continue;
                }
                if (цели.contains(nb)) {
                    return d + 1;
                }
                глубина.put(nb, d + 1);
                очередь.add(nb);
            }
        }
        return Integer.MAX_VALUE;
    }

    private static void печать(PrintStream out, Поле п, int игроков) {
        double n = Math.max(1, п.партий);
        double мест = n * игроков;
        out.println("=".repeat(78));
        out.printf("ПОЛЕ %s — %d партий%n", п.имя, п.партий);
        out.println("=".repeat(78));
        out.printf("  длина партии: %.1f раунда (от %.0f до %.0f)%n",
            п.раундов / n, п.раундовМин, п.раундовМакс);
        out.print("  чем кончалась:");
        for (var e : п.концовки.entrySet()) {
            out.printf(" %s %.0f%%;", e.getKey(), 100.0 * e.getValue() / n);
        }
        out.println();
        out.printf("  очки: у победителя %.1f, в среднем %.1f, отрыв от второго %.1f%n",
            п.очковПобедителя / n, п.очковСредних / мест, п.разрывПобедителя / n);

        out.println("\n  ЧТО БОТЫ ДЕЛАЮТ (действий за партию, на всех игроков)");
        for (var e : п.действия.entrySet()) {
            int пусто = п.вхолостую.getOrDefault(e.getKey(), 0);
            out.printf("    %-12s %6.2f%s%n", e.getKey(), e.getValue() / n,
                пусто == 0 ? "" : String.format("   из них впустую %.2f (%.0f%%)",
                    пусто / n, 100.0 * пусто / e.getValue()));
        }

        out.println("\n  ЗАДАНИЯ (за партию, на всех игроков)");
        out.printf("    пришло в руки %.2f, выполнено %.2f (из них усиленно %.2f), "
                + "сожжено в утиль %.2f%n",
            п.заданийПришло / n, п.заданийВыполнено / n, п.заданийУсиленно / n,
            п.заданийСожжено / n);
        out.println("    награды-действия: выпало / сыграно");
        int выпало = 0;
        int сыграло = 0;
        for (var e : п.награды.entrySet()) {
            выпало += e.getValue()[0];
            сыграло += e.getValue()[1];
            out.printf("      %-12s %5.2f / %5.2f (%3.0f%%)%n", e.getKey(),
                e.getValue()[0] / n, e.getValue()[1] / n,
                e.getValue()[0] == 0 ? 0 : 100.0 * e.getValue()[1] / e.getValue()[0]);
        }
        out.printf("      ИТОГО      %5.2f / %5.2f (%3.0f%% наград удалось разыграть)%n",
            выпало / n, сыграло / n, выпало == 0 ? 0 : 100.0 * сыграло / выпало);

        out.println("\n  ВОЙНА");
        out.printf("    залпов %.2f, попаданий %.2f, снесено жетонов %.2f, ЦУ %.2f%n",
            п.боёв / n, п.попаданий / n, п.сносов / n, п.цуСнесено / n);
        out.printf("    снесено за раунд: %.2f%n", п.сносов / п.раундов);

        out.println("\n  ХОЗЯЙСТВО (на игрока)");
        out.printf("    произведено войск %.2f, боеприпасов %.2f, взято контейнеров %.2f%n",
            п.войск / мест, п.боеприпасов / мест, п.контейнеров / мест);
        out.printf("    к концу: зданий %.2f, войск на поле %.2f, шагов на треках %.2f%n",
            п.зданийВКонце / мест, п.войскВКонце / мест, п.шагов / мест);
        out.printf("    модулей: боя %.2f, сборки %.2f%n",
            п.модулейБоя / мест, п.модулейСборки / мест);
        out.printf("    осталось: монет %.2f, келемия %.2f, трофеев %.2f%n",
            п.монетВКонце / мест, п.келемияВКонце / мест, п.трофеевВКонце / мест);

        out.println("\n  ОТКУДА ОЧКИ (на игрока)");
        п.очкиПоИсточникам.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(10)
            .forEach(e -> out.printf("    %-22s %5.2f%n", e.getKey(), e.getValue() / мест));
        out.println();
    }

    private static void сводка(PrintStream out, Map<String, Поле> итоги, int игроков) {
        out.println("=".repeat(78));
        out.println("СВОДКА: ЧЕМ ПОЛЯ ОТЛИЧАЮТСЯ ДРУГ ОТ ДРУГА");
        out.println("=".repeat(78));
        out.println("поле                         | раундов | снесено | залпов | войск "
            + "| заданий | очки поб. | дистанция | контакт");
        for (Поле п : итоги.values()) {
            double n = Math.max(1, п.партий);
            out.printf("%-28s |  %5.1f  |  %5.2f  | %6.2f | %5.2f |  %5.2f  |  %5.1f    "
                    + "|   %5.2f   |  %3.0f%%%n",
                п.имя.length() > 28 ? п.имя.substring(0, 28) : п.имя,
                п.раундов / n, п.сносов / n, п.боёв / n,
                п.войск / (n * игроков), п.заданийВыполнено / n, п.очковПобедителя / n,
                п.замеровРасстояния == 0 ? 0 : п.суммаРасстояний / п.замеровРасстояния,
                п.замеровКонтакта == 0 ? 0 : 100 * п.ходовСКонтактом / п.замеровКонтакта);
        }
    }

    private static double чис(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
