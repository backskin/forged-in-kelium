package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Token;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.Storage;
import kelium.engine.LayoutLibrary;

/**
 * РЕВИЗИЯ ПАРТИИ — один прогон, который отвечает на вопросы дизайнера числами.
 *
 * <p>Вопросы (13.08.2026), под которые он и написан:
 * <ul>
 *   <li>доходят ли трофейные очки уничтоженных жетонов до карты приказа
 *       победителя, и приходит ли контейнер-компенсация тому, кому снесли
 *       здание — то есть работают ли эти механики ТОЧНО;</li>
 *   <li>сколько карт заданий проходит через руку игрока, как часто задания
 *       выполняются, какие именно (война/экспансия/экономика) и бывает ли, что
 *       рука переполняется;</li>
 *   <li>покупают ли боты пару карт заданий за келемий на рынке;</li>
 *   <li>почему боты не воюют, не добивают треки науки, не сносят второе ЦУ и
 *       медленно копают — по разложенным этапам, а не «на глаз».</li>
 * </ul>
 *
 * <p>Запуск: {@code kelium.RevisionProbe [игроков] [партий]}.
 */
public final class RevisionProbe {

    private RevisionProbe() {
    }

    /** Раздел каталога заданий по номеру карты: o01-o10 A, o11-o20 B и так далее. */
    private static String section(String cardId) {
        if (cardId == null || cardId.length() < 3 || cardId.charAt(0) != 'o') {
            return "начальные";
        }
        int n;
        try {
            n = Integer.parseInt(cardId.substring(1, 3));
        } catch (NumberFormatException e) {
            return "начальные";
        }
        if (n <= 10) {
            return "A разработка";
        }
        if (n <= 20) {
            return "B инфраструктура";
        }
        if (n <= 32) {
            return "C операция (война)";
        }
        if (n <= 40) {
            return "D приобретения";
        }
        return "начальные";
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 200;

        // ---- счётчики
        double rounds = 0;
        double drawn = 0;              // карт заданий пришло в руку
        double done = 0;               // заданий выполнено
        double burned = 0;             // заданий сожжено/сброшено
        double handEnd = 0;            // карт в руке к концу партии
        int handOverLimit = 0;         // партий, где рука доходила до 4+ карт
        double maxHandSeen = 0;
        Map<String, Integer> doneBy = new TreeMap<>();
        Map<String, Integer> drawnBy = new TreeMap<>();
        double trophyPointsEnd = 0;    // ТО на картах приказа к концу партии
        double trophyPointsEarned = 0; // ТО, реально положенные на карту за партию
        double killsUnits = 0;
        double killsBuildings = 0;
        double compContainers = 0;     // контейнеров-компенсаций выдано
        double cuKills = 0;
        double cuDouble = 0;
        double peaks = 0;              // очки за науку к концу
        double techSteps = 0;          // шагов по трекам сделано
        double allPeaks = 0;
        double keliumMined = 0;
        double keliumEnd = 0;
        double coinsEnd = 0;
        double marketObjectivePairs = 0;
        double sciCoinExchanges = 0;
        Map<String, Integer> ends = new TreeMap<>();

        Storage.resetContainerStats();
        for (int g = 0; g < games; g++) {
            long seed = 3_300_000L + g;
            GameState s = Setup.buildGame(
                LayoutLibrary.configFor(players, seed));
            List<Agent> agents = new ArrayList<>();
            List<String> lineup = List.of("hawk", "dove", "balanced", "opportunist");
            for (int i = 0; i < players; i++) {
                agents.add(Bots.create(lineup.get(i % lineup.size()), i,
                    new Random(seed * 31 + i), players));
            }
            double[] acc = new double[9];
            int[] maxHand = {0};
            GameEngine.playGame(s, agents, ev -> {
                String type = String.valueOf(ev.get("type"));
                switch (type) {
                    case "objective_drawn" -> {
                        acc[0]++;
                        drawnBy.merge(section(String.valueOf(ev.get("card"))), 1, Integer::sum);
                        if (ev.get("hand") instanceof Number n) {
                            maxHand[0] = Math.max(maxHand[0], n.intValue());
                        }
                    }
                    case "objective" -> {
                        acc[1]++;
                        doneBy.merge(section(String.valueOf(ev.get("card"))), 1, Integer::sum);
                        // Награда задания часто ВКЛЮЧАЕТ новую карту задания —
                        // это тоже приход в руку, и без него счёт карт врёт.
                        if (ev.get("granted") instanceof Map<?, ?> gr
                                && gr.get("objective_card") instanceof Number gn) {
                            acc[0] += gn.intValue();
                        }
                    }
                    // Сожжение задания (spec-действие) — это НЕ слепой сброс карты
                    // приказа: их надо считать порознь, иначе выходит, что сбросов
                    // больше, чем всех карт заданий вместе.
                    case "objective_burn" -> acc[2]++;
                    case "container" -> {
                        if (ev.get("got") instanceof Map<?, ?> got
                                && got.get("objective_cards") instanceof Number cn) {
                            acc[0] += cn.intValue();
                        }
                    }
                    case "combat_hit" -> {
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            if (ev.get("trophy") instanceof Number tn) {
                                acc[8] += tn.intValue();   // ТО, положенные на карту
                            }
                            String victim = String.valueOf(ev.get("victim"));
                            if (victim.contains("_") || victim.contains("plant")
                                    || victim.contains("barracks") || victim.contains("factory")
                                    || victim.contains("airbase") || victim.contains("miner")
                                    || victim.contains("command")) {
                                acc[3]++;
                            } else {
                                acc[4]++;
                            }
                        }
                    }
                    case "action" -> {
                        Object tel = ev.get("telemetry");
                        if (tel instanceof Map<?, ?> m) {
                            if (m.get("kelium") instanceof Number n) {
                                acc[5] += n.doubleValue();
                            }
                            // Пара карт заданий за келемий на рынке и обмен трофеев
                            // на монеты в науке — считаем по помеченным сделкам.
                            // Рынок: сколько раз взяли сделку «1 келемий -> 2 карты
                            // задания» (ключ deal_kelium_to_objective).
                            if (m.get("deal_objective_cards") instanceof Number n2) {
                                acc[6] += n2.intValue();
                            }
                            if (m.get("objective_cards") instanceof Number n3) {
                                acc[0] += n3.intValue();   // карты, купленные на рынке
                            }
                            // Наука: телеметрия обмена — СТРОКА из id использованных
                            // обменов, поэтому считаем вхождения, а не число.
                            Object ex = m.get("exchange");
                            if (ex != null && String.valueOf(ex).contains("coin")) {
                                acc[7]++;
                            }
                        }
                    }
                    default -> { }
                }
            });

            rounds += s.round;
            drawn += acc[0];
            done += acc[1];
            burned += acc[2];
            killsBuildings += acc[3];
            killsUnits += acc[4];
            keliumMined += acc[5];
            marketObjectivePairs += acc[6];
            trophyPointsEarned += acc[8];
            sciCoinExchanges += acc[7];
            maxHandSeen = Math.max(maxHandSeen, maxHand[0]);
            if (maxHand[0] > 3) {
                handOverLimit++;
            }
            ends.merge(String.valueOf(s.winCondition), 1, Integer::sum);

            for (PlayerState p : s.players) {
                handEnd += p.objectiveHand.size();
                trophyPointsEnd += p.trophySpacePoints();
                keliumEnd += p.resources.kelium();
                coinsEnd += p.resources.coin();
                cuKills += p.cuKills;
                if (p.cuDestructionTokens >= 2) {
                    cuDouble++;
                }
                peaks += Scoring.scorePlayer(s, p.seat).getOrDefault("tech", 0);
                for (int v : p.techSteps.values()) {
                    techSteps += v;
                }
            }
            if (s.tech.allPeaksOccupied()) {
                allPeaks++;
            }
        }
        Map<String, Long> containerSources = Storage.containerStats();
        for (var e : containerSources.entrySet()) {
            if (e.getKey().contains("компенсация")) {
                compContainers += e.getValue();
            }
        }

        double perGame = 1.0 / games;
        double perPlayer = 1.0 / (games * (double) players);
        StringBuilder md = new StringBuilder();
        md.append("# Ревизия партий — ответы числами\n\n");
        md.append("Партий: ").append(games).append(", игроков: ").append(players)
          .append(", боты: ").append(Bots.describe()).append(".\n\n");
        md.append(String.format(Locale.ROOT, "Средняя длина партии: **%.2f раунда**.%n%n",
            rounds * perGame));

        md.append("## Карты заданий\n\n");
        md.append(String.format(Locale.ROOT,
            "| показатель | значение |%n|---|---:|%n"
            + "| карт пришло в руку за партию | %.2f |%n"
            + "| на игрока | %.2f |%n"
            + "| заданий ВЫПОЛНЕНО за партию | %.2f |%n"
            + "| выполнено на игрока | %.2f |%n"
            + "| доля выполненных от пришедших | %.0f%% |%n"
            + "| сожжено/сброшено за партию | %.2f |%n"
            + "| осталось в руке к концу (на игрока) | %.2f |%n"
            + "| партий, где рука доходила до 4+ карт | %d из %d |%n"
            + "| наибольшая рука за все партии | %.0f |%n",
            drawn * perGame, drawn * perPlayer, done * perGame, done * perPlayer,
            drawn > 0 ? 100.0 * done / drawn : 0.0, burned * perGame,
            handEnd * perPlayer, handOverLimit, games, maxHandSeen));

        md.append("\n### Какие задания приходят и какие выполняются\n\n");
        md.append("| раздел каталога | пришло | выполнено | доля |\n|---|---:|---:|---:|\n");
        Map<String, Integer> allSections = new LinkedHashMap<>();
        drawnBy.forEach((k, v) -> allSections.put(k, v));
        doneBy.forEach((k, v) -> allSections.putIfAbsent(k, 0));
        for (String sec : allSections.keySet()) {
            int d = drawnBy.getOrDefault(sec, 0);
            int c = doneBy.getOrDefault(sec, 0);
            md.append(String.format(Locale.ROOT, "| %s | %d | %d | %.0f%% |%n",
                sec, d, c, d > 0 ? 100.0 * c / d : 0.0));
        }

        md.append("\n## Трофеи и компенсации — работают ли механики точно\n\n");
        md.append(String.format(Locale.ROOT,
            "| показатель | значение |%n|---|---:|%n"
            + "| уничтожено ЖЕТОНОВ ВОЙСК за партию | %.2f |%n"
            + "| уничтожено ЗДАНИЙ за партию | %.2f |%n"
            + "| ТО ПОЛОЖЕНО на карты приказа за партию (всеми) | %.2f |%n"
            + "| ТО лежит на картах приказа к концу партии (на игрока) | %.2f |%n"
            + "| контейнеров-компенсаций выдано за партию | %.2f |%n"
            + "| сносов ЦУ за партию | %.2f |%n"
            + "| игроков, добравшихся до ДВУХ жетонов уничтожения ЦУ | %.0f из %d |%n",
            killsUnits * perGame, killsBuildings * perGame, trophyPointsEarned * perGame,
            trophyPointsEnd * perPlayer,
            compContainers * perGame, cuKills * perGame, cuDouble, games * players));
        md.append("\nВыдачи контейнеров по источникам за весь прогон: ")
          .append(containerSources).append("\n");

        md.append("\n## Экономика и наука\n\n");
        md.append(String.format(Locale.ROOT,
            "| показатель | значение |%n|---|---:|%n"
            + "| келемия добыто за партию (всеми) | %.2f |%n"
            + "| келемия осталось на складе (на игрока) | %.2f |%n"
            + "| монет на руках к концу (на игрока) | %.2f |%n"
            + "| очков за науку (на игрока) | %.2f |%n"
            + "| шагов по трекам сделано (на игрока) | %.2f |%n"
            + "| партий, где заняты ВСЕ вершины | %.0f из %d |%n"
            + "| покупок пары карт заданий на рынке за партию | %.2f |%n"
            + "| обменов трофеев на монеты в науке за партию | %.2f |%n",
            keliumMined * perGame, keliumEnd * perPlayer, coinsEnd * perPlayer,
            peaks * perPlayer, techSteps * perPlayer, allPeaks, games,
            marketObjectivePairs * perGame, sciCoinExchanges * perGame));
        md.append("\nЧем кончались партии: ").append(ends).append("\n");

        Path file = Path.of("reports/balance/ревизия-партий.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
        out.println(md);
        out.println("записано: " + file.toAbsolutePath());
    }
}
