package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Genome;
import kelium.agents.HeuristicAgent;
import kelium.agents.StrategicAgent;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.dataio.Locations;
import kelium.core.Agent;

/**
 * BalanceProbe — «балансовый зонд». Прогоняет большой батч партий и собирает
 * компактную сводку по четырём осям, которые важны дизайнеру:
 * <ul>
 *   <li>БАЛАНС — нет доминирующего места/стороны/цвета колоды, шансы близки;
 *   <li>ПОЛНОТЕЛОСТЬ — всё работает и часто используется (действия, механики,
 *       все пути победы случаются, нет мёртвых);
 *   <li>РЕИГРАБЕЛЬНОСТЬ — победы приходят РАЗНЫМИ путями (разброс профилей ПО);
 *   <li>СЛОМАННЫЕ МЕСТА — частые провалы действий, неиспользуемые механики.
 * </ul>
 *
 * <p>Вывод — короткий русский отчёт (Markdown в файл + консоль), пригодный для
 * того, чтобы кинуть его в чат Claude как аналитику. Зонд НЕ выносит вердиктов
 * сам — он даёт цифры; выводы делает человек/LLM.
 *
 * <p>CLI: {@code kelium.BalanceProbe [players] [games] [agent]}. agent =
 * heuristic | strategic (по умолчанию strategic — «умные» боты, чтобы
 * баланс мерился на осмысленной игре, а не на случайной).
 */
public final class BalanceProbe {

    private BalanceProbe() {
    }

    // --- накопители по батчу ---
    private final Map<Integer, Integer> winsBySeat = new TreeMap<>();
    private final Map<String, Integer> winsBySide = new TreeMap<>();
    private final Map<String, Integer> winsByColor = new TreeMap<>();
    private final Map<String, Integer> endConditions = new TreeMap<>();
    private final Map<String, Integer> actionOk = new TreeMap<>();
    private final Map<String, Integer> actionFail = new TreeMap<>();
    private final Map<String, Integer> mechanics = new TreeMap<>();      // arsenal_burn, install, container, maneuver, super_deploy...
    private final Map<String, Integer> vpSourceTotals = new TreeMap<>();
    private final Map<String, Integer> winnerTopSource = new TreeMap<>();  // за счёт чего чаще выигрывают
    private final List<Integer> winnerVps = new ArrayList<>();
    private final List<Integer> margins = new ArrayList<>();
    // --- задания: выполнение и верхний утиль-эффект по картам, эффектам и фазам ---
    private final Map<String, Integer> objectiveDoneByCard = new TreeMap<>();
    private final Map<String, Integer> burnByEffect = new TreeMap<>();       // какой верхний эффект жгут
    private final Map<String, Integer> burnByCard = new TreeMap<>();
    private final Map<String, Integer> donePhase = new TreeMap<>();          // фаза выполнения (начало/середина/конец)
    private final Map<String, Integer> burnPhase = new TreeMap<>();          // фаза сжигания верха
    private int objectiveDoneTotal = 0;
    private int objectiveEnhTotal = 0;
    private int burnTotal = 0;
    private int games = 0;
    private int rounds = 0;

    /** Фаза партии по номеру раунда (раундов ~7): начало 1-2, середина 3-5, конец 6+. */
    private static String phase(int round) {
        if (round <= 2) {
            return "начало";
        }
        if (round <= 5) {
            return "середина";
        }
        return "конец";
    }

    private final java.util.List<String> allObjectiveIds = new ArrayList<>();

    private void run(int players, int nGames, String agentSpec) {
        Genome genome = tryLoadGenome(players);
        // все id заданий (для поиска «мёртвых»)
        for (Map<String, Object> e : GameConfig.buildCached(players, 0L).content.get("objectives").entries) {
            allObjectiveIds.add(String.valueOf(e.get("id")));
        }

        for (long seed = 0; seed < nGames; seed++) {
            GameConfig cfg = GameConfig.buildCached(players, seed);
            GameState s = Setup.buildGame(cfg);
            int stratSeat = (int) (seed % players);
            List<Agent> agents = buildAgents(players, seed, agentSpec);

            final int[] roundMax = {0};
            GameEngine.playGame(s, agents, ev -> observe(ev, roundMax));
            games++;
            rounds += roundMax[0];

            // итог партии
            recordEnd(s);
        }
        writeReport(players, nGames, agentSpec);
    }

    @SuppressWarnings("unchecked")
    private void observe(Map<String, Object> ev, int[] roundMax) {
        String t = String.valueOf(ev.get("type"));
        switch (t) {
            case "refresh" -> {
                Object r = ev.get("round");
                if (r instanceof Number n) {
                    roundMax[0] = Math.max(roundMax[0], n.intValue());
                }
            }
            case "action" -> {
                String name = String.valueOf(ev.get("action"));
                if (Boolean.TRUE.equals(ev.get("ok"))) {
                    actionOk.merge(name, 1, Integer::sum);
                } else {
                    actionFail.merge(name, 1, Integer::sum);
                }
            }
            case "arsenal" -> mechanics.merge("arsenal_" + ev.get("mode"), 1, Integer::sum);
            case "container" -> mechanics.merge("container_open", 1, Integer::sum);
            case "maneuver" -> mechanics.merge("maneuver", 1, Integer::sum);
            case "super_deploy" -> mechanics.merge("super_deploy", 1, Integer::sum);
            case "objective" -> {
                mechanics.merge("objective_done", 1, Integer::sum);
                objectiveDoneTotal++;
                objectiveDoneByCard.merge(String.valueOf(ev.get("card")), 1, Integer::sum);
                if (Boolean.TRUE.equals(ev.get("enhanced"))) {
                    objectiveEnhTotal++;
                }
                int r = ev.get("round") instanceof Number n ? n.intValue() : 0;
                donePhase.merge(phase(r), 1, Integer::sum);
            }
            case "objective_burn" -> {
                mechanics.merge("objective_burn", 1, Integer::sum);
                burnTotal++;
                burnByEffect.merge(String.valueOf(ev.get("effect")), 1, Integer::sum);
                burnByCard.merge(String.valueOf(ev.get("card")), 1, Integer::sum);
                int r = ev.get("round") instanceof Number n ? n.intValue() : 0;
                burnPhase.merge(phase(r), 1, Integer::sum);
            }
            case "combat_hit" -> {
                mechanics.merge("combat_hit", 1, Integer::sum);
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    mechanics.merge("token_destroyed", 1, Integer::sum);
                }
            }
            default -> { }
        }
    }

    private void recordEnd(GameState s) {
        endConditions.merge(String.valueOf(s.winCondition), 1, Integer::sum);
        Integer winner = s.winner;

        // разброс ПО + маржа
        int best = Integer.MIN_VALUE;
        int second = Integer.MIN_VALUE;
        for (PlayerState p : s.players) {
            int vp = Scoring.scorePlayer(s, p.seat).getOrDefault("total", 0);
            for (var e : Scoring.scorePlayer(s, p.seat).entrySet()) {
                if (!"total".equals(e.getKey()) && e.getValue() != 0) {
                    vpSourceTotals.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
            if (vp > best) {
                second = best;
                best = vp;
            } else if (vp > second) {
                second = vp;
            }
        }
        if (second > Integer.MIN_VALUE) {
            margins.add(best - second);
        }

        if (winner != null && winner >= 0) {
            PlayerState w = s.player(winner);
            winsBySeat.merge(winner, 1, Integer::sum);
            winsBySide.merge(w.board.troop.side, 1, Integer::sum);
            winsByColor.merge(String.valueOf(w.orderColor), 1, Integer::sum);
            Map<String, Integer> bd = Scoring.scorePlayer(s, winner);
            winnerVps.add(bd.getOrDefault("total", 0));
            // главный источник очков победителя
            String top = null;
            int topVal = 0;
            for (var e : bd.entrySet()) {
                if (!"total".equals(e.getKey()) && e.getValue() > topVal) {
                    topVal = e.getValue();
                    top = e.getKey();
                }
            }
            if (top != null) {
                winnerTopSource.merge(top, 1, Integer::sum);
            }
        }
    }

    // ================= отчёт =============================================
    private void writeReport(int players, int nGames, String agentSpec) {
        List<String> L = new ArrayList<>();
        L.add("# Балансовый зонд — Кристаллы Раздора");
        L.add("");
        L.add(String.format(Locale.ROOT, "Игроков: **%d**, партий: **%d**, боты: **%s**, ср. раундов: **%.1f**",
            players, nGames, agentSpec, games > 0 ? (double) rounds / games : 0));
        L.add("");

        // 1. БАЛАНС
        L.add("## 1. Баланс");
        L.add(pctLine("Победы по МЕСТУ (ротация: разброс = дисбаланс старта)", winsBySeat, nGames, true));
        L.add(pctLine("Победы по СТОРОНЕ планшета", winsBySide, nGames, false));
        L.add(pctLine("Победы по ЦВЕТУ колоды приказов", winsByColor, nGames, false));
        L.add(String.format(Locale.ROOT, "- Средняя маржа победы (ПО): **%.2f** (меньше = напряжённее)",
            avg(margins)));
        L.add("");

        // 2. ПУТИ ПОБЕДЫ (полнотелость + реиграбельность)
        L.add("## 2. Пути победы (условия окончания)");
        L.add(pctLine("Как заканчивались партии", endConditions, nGames, false));
        L.add("");
        L.add("## 3. За счёт чего выигрывают (главный источник ПО победителя)");
        L.add(pctLine("Доминирующий источник очков у победителя", winnerTopSource, sum(winnerTopSource), false));
        L.add("- ПО победителя: " + statLine(winnerVps));
        L.add("");
        L.add("## 4. Вклад источников ПО (суммарно по всем игрокам)");
        L.add(shareLine(vpSourceTotals));
        L.add("");

        // 5. ПОЛНОТЕЛОСТЬ действий
        L.add("## 5. Действия — частота и провалы");
        for (String a : new String[]{"assembly", "mining", "build", "energy_swap",
                "movement", "combat", "market", "science"}) {
            int ok = actionOk.getOrDefault(a, 0);
            int fail = actionFail.getOrDefault(a, 0);
            String flag = ok == 0 ? "  ⚠МЁРТВОЕ" : (fail > ok ? "  ⚠чаще проваливается" : "");
            L.add(String.format(Locale.ROOT, "- %-12s успешно=%d, провал=%d%s", a, ok, fail, flag));
        }
        L.add("");

        // 6. МЕХАНИКИ
        L.add("## 6. Использование механик (сколько раз за батч)");
        for (String m : new String[]{"objective_done", "combat_hit", "token_destroyed",
                "arsenal_burn", "arsenal_install", "container_open", "maneuver", "super_deploy"}) {
            int v = mechanics.getOrDefault(m, 0);
            String flag = v == 0 ? "  ⚠НЕ ИСПОЛЬЗУЕТСЯ" : "";
            L.add(String.format(Locale.ROOT, "- %-16s %d%s", m, v, flag));
        }
        L.add("");

        // 7. ЗАДАНИЯ — выполнение (низ) и верхний утиль-эффект (burn)
        L.add("## 7. Задания: выполнение низа");
        L.add(String.format(Locale.ROOT,
            "- выполнено всего: **%d**, из них усилено: **%d** (%.0f%%)",
            objectiveDoneTotal, objectiveEnhTotal,
            objectiveDoneTotal > 0 ? 100.0 * objectiveEnhTotal / objectiveDoneTotal : 0));
        L.add("- фаза выполнения: " + phaseLine(donePhase, objectiveDoneTotal));
        L.add("- НИКОГДА не выполнялись (мёртвые низы): " + deadCards());
        L.add(topCards("- самые частые низы: ", objectiveDoneByCard, 8));
        L.add("");
        L.add("## 8. Задания: верхний утиль-эффект (сжигание)");
        L.add(String.format(Locale.ROOT, "- сожжено верхов всего: **%d**", burnTotal));
        L.add("- фаза сжигания: " + phaseLine(burnPhase, burnTotal));
        L.add(mapLine("- по ЭФФЕКТУ верха: ", burnByEffect));
        L.add(topCards("- самые жгомые карты: ", burnByCard, 8));
        L.add("");
        L.add("_Зонд даёт цифры без вердиктов — анализ и советы по балансу/полнотелости/"
            + "реиграбельности делает Claude по этому отчёту._");

        String text = String.join("\n", L);
        // консоль
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println(text);
        // файл
        try {
            Path p = Paths.get("reports", "balance",
                "probe_p" + players + "_" + agentSpec + "_" + nGames + "g.md");
            Files.createDirectories(p.getParent());
            Files.writeString(p, text, StandardCharsets.UTF_8);
            out.println("\n(сохранено: " + p.toAbsolutePath() + ")");
        } catch (Exception e) {
            out.println("не удалось сохранить отчёт: " + e.getMessage());
        }
    }

    // ================= помощники форматирования ==========================
    private static String pctLine(String title, Map<?, Integer> counts, int denom, boolean seat) {
        StringBuilder sb = new StringBuilder("- " + title + ": ");
        boolean first = true;
        for (var e : counts.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            double pct = denom > 0 ? 100.0 * e.getValue() / denom : 0;
            sb.append(seat ? "место " : "").append(e.getKey()).append("=")
              .append(String.format(Locale.ROOT, "%.0f%%", pct));
        }
        return sb.toString();
    }

    private static String shareLine(Map<String, Integer> totals) {
        int total = sum(totals);
        List<Map.Entry<String, Integer>> es = new ArrayList<>(totals.entrySet());
        es.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        for (var e : es) {
            double pct = total > 0 ? 100.0 * e.getValue() / total : 0;
            sb.append(String.format(Locale.ROOT, "- %-20s %.0f%% (%d)%n", e.getKey(), pct, e.getValue()));
        }
        return sb.toString().stripTrailing();
    }

    private String phaseLine(Map<String, Integer> ph, int total) {
        if (total == 0) {
            return "нет данных";
        }
        StringBuilder sb = new StringBuilder();
        for (String k : new String[]{"начало", "середина", "конец"}) {
            int v = ph.getOrDefault(k, 0);
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(k).append("=").append(String.format(Locale.ROOT, "%.0f%%", 100.0 * v / total));
        }
        return sb.toString();
    }

    private String deadCards() {
        List<String> dead = new ArrayList<>();
        for (String id : allObjectiveIds) {
            if (objectiveDoneByCard.getOrDefault(id, 0) == 0) {
                dead.add(id);
            }
        }
        return dead.isEmpty() ? "нет (все хоть раз выполнялись)"
            : dead.size() + " из " + allObjectiveIds.size() + ": " + dead;
    }

    private static String topCards(String prefix, Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> es = new ArrayList<>(m.entrySet());
        es.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < Math.min(n, es.size()); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(es.get(i).getKey()).append("=").append(es.get(i).getValue());
        }
        return sb.toString();
    }

    private static String mapLine(String prefix, Map<String, Integer> m) {
        if (m.isEmpty()) {
            return prefix + "нет данных";
        }
        List<Map.Entry<String, Integer>> es = new ArrayList<>(m.entrySet());
        es.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder(prefix);
        boolean first = true;
        for (var e : es) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    private static String statLine(List<Integer> xs) {
        if (xs.isEmpty()) {
            return "нет данных";
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x : xs) {
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        return String.format(Locale.ROOT, "мин=%d, среднее=%.1f, макс=%d (разброс = реиграбельность)",
            min, avg(xs), max);
    }

    private static double avg(List<Integer> xs) {
        if (xs.isEmpty()) {
            return 0;
        }
        double s = 0;
        for (int x : xs) {
            s += x;
        }
        return s / xs.size();
    }

    private static int sum(Map<?, Integer> m) {
        int s = 0;
        for (int v : m.values()) {
            s += v;
        }
        return s;
    }

    // ================= сборка ботов ======================================
    private static List<Agent> buildAgents(int players, long seed, String spec) {
        List<Agent> agents = new ArrayList<>();
        String[] chars = {"aggressor", "defender", "economist", "aggressor"};
        for (int seat = 0; seat < players; seat++) {
            Random r = new Random(seed * 131 + seat + 1);
            switch (spec) {
                case "strategic" -> {
                    // разные характеры за столом (непредсказуемость); ротация по
                    // сиду, чтобы играли ВСЕ. С 12.08.2026 все — обученные линии
                    // геномов, прошитых характеров нет.
                    //
                    // ТОЛЬКО ИГРОКИ, БЕЗ ПРИБОРОВ: аксиома и жнец обучены без очков
                    // в цели, и их присутствие за столом портит замер соседей — а
                    // это балансовый стенд, замер тут единственный смысл.
                    List<String> profs = kelium.agents.Bots.PLAYERS;
                    String prof = profs.get((seat + (int) Math.floorMod(seed, profs.size()))
                        % profs.size());
                    agents.add(kelium.agents.Bots.create(prof, seat, r, players));
                }
                default -> agents.add(new HeuristicAgent(seat, r, chars[seat % chars.length]));
            }
        }
        return agents;
    }

    private static Genome tryLoadGenome(int players) {
        try {
            return Genome.loadJson(Locations.botMemory().resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            return Genome.defaults();
        }
    }

    public static void main(String[] args) {
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int nGames = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        String agentSpec = args.length > 2 ? args[2] : "strategic";
        new BalanceProbe().run(players, nGames, agentSpec);
    }
}
