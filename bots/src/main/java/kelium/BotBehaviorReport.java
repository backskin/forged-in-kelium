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

import kelium.agents.Genome;
import kelium.agents.Plan;
import kelium.agents.StrategicAgent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.dataio.Locations;
import kelium.core.Agent;
import kelium.engine.LayoutLibrary;

/**
 * BotBehaviorReport — «как боты на самом деле играют».
 *
 * <p>Отвечает на вопрос дизайнера: есть ли у ботов ПРОМЕЖУТОЧНЫЕ ЦЕЛИ или они
 * жадно берут сиюминутную выгоду. Меряется не результат, а поведение:
 *
 * <ul>
 *   <li><b>цели</b> — какие планы бот себе ставит и как часто (см. {@link Plan});</li>
 *   <li><b>цепочки</b> — доходит ли он от «построил добытчик» до «снял келемий»,
 *       и на каком звене рвётся;</li>
 *   <li><b>холостые ходы</b> — сколько действий не дали НИЧЕГО (ни ресурса, ни
 *       жетона, ни перемещения). Это и есть «бот сидит и тупит» в цифрах;</li>
 *   <li><b>цепочка келемий → маркет</b> — понимает ли он, что добытое можно
 *       продать.</li>
 * </ul>
 *
 * <p>Запуск: {@code java -cp ... kelium.BotBehaviorReport [players] [games]}
 * Отчёт: {@code reports/balance/поведение-ботов.md}
 */
public final class BotBehaviorReport {

    private BotBehaviorReport() {
    }

    /** Копилка наблюдений по всем партиям. */
    static final class Tally {
        int games;
        int turns;
        int actions;
        int actionsIdle;                 // действие сыграно, но ничего не изменило
        final Map<String, Integer> byAction = new LinkedHashMap<>();
        final Map<String, Integer> idleByAction = new LinkedHashMap<>();
        final Map<String, Integer> goals = new LinkedHashMap<>();
        int keliumMined;
        int keliumSold;
        int marketPlays;
        int objectivesDone;
        int techSteps;
        int unitsMade;
        int battles;
        // цепочка добычи по партиям
        int gMiner;
        int gMinerAdj;
        int gMinerPowered;
        int gMined;
        int gSoldAfterMining;
        double vpAvg;
        // ОБОРОТ РЕСУРСОВ: на что уходят деньги и понимают ли боты выбор уровней
        int coinsSpentOnBuild;
        int coinsSpentOnSurcharge;
        int coinsPaidForPower;
        int payPowerOffers;
        int payPowerAccepted;
        final Map<String, Integer> minerLevels = new LinkedHashMap<>();
        final Map<String, Integer> plantLevels = new LinkedHashMap<>();
        int objectivesDoneEv;
        int storageTokensTaken;
        int minersEnd;
        int plantsEnd;
        // бой: сколько раз он был ВОЗМОЖЕН, сколько раз при этом не состоялся,
        // и сколько раз бить было просто некого
        int combatChances;
        int combatMissed;
        int combatNoTargets;
        // КОНТЕЙНЕРЫ 2.0: сколько карт контейнеров прошло через руки за партию
        int containersOpened;
        int containersLeft;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, "UTF-8"));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 1000;

        Genome genome = loadGenome(players);
        Tally t = new Tally();
        List<String> sampleThoughts = new ArrayList<>();

        for (int g = 0; g < games; g++) {
            long seed = 5000 + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            List<StrategicAgent> strategic = new ArrayList<>();
            final int sample = g;
            for (int seat = 0; seat < players; seat++) {
                StrategicAgent a = new StrategicAgent(seat, new Random(seed * 131 + seat), genome);
                if (sample == 0) {
                    a.withThoughts((who, phrase) -> {
                        if (sampleThoughts.size() < 400) {
                            sampleThoughts.add("игрок " + (who + 1) + ": " + phrase);
                        }
                    });
                }
                strategic.add(a);
                agents.add(a);
            }

            final boolean[] mined = {false};
            final boolean[] soldAfter = {false};
            GameEngine.playGame(s, agents, ev -> observe(t, ev, mined, soldAfter));

            // цели, которые боты успели себе поставить (последний план каждого)
            for (StrategicAgent a : strategic) {
                Plan p = a.currentPlan();
                if (p != null) {
                    t.goals.merge(p.goal.ru, 1, Integer::sum);
                }
            }

            boolean anyMiner = false;
            boolean anyAdj = false;
            boolean anyPowered = false;
            var live = Plan.liveTileHexes(s);
            for (int seat = 0; seat < players; seat++) {
                PlayerState p = s.player(seat);
                for (BuildingToken b : p.buildingsOnField()) {
                    if (b.type != BuildingType.MINER) {
                        continue;
                    }
                    anyMiner = true;
                    boolean adj = Plan.touchesLiveTile(s, b.hexId, live);
                    if (adj) {
                        anyAdj = true;
                        if (b.powered()) {
                            anyPowered = true;
                        }
                    }
                }
                t.techSteps += p.techSteps.values().stream().mapToInt(Integer::intValue).sum();
                t.vpAvg += Scoring.scorePlayer(s, seat).getOrDefault("total", 0);
            }
            for (int seat = 0; seat < players; seat++) {
                PlayerState p = s.player(seat);
                t.storageTokensTaken += p.storageTokens.size();
                t.containersLeft += p.containers;
                for (BuildingToken b : p.buildingsOnField()) {
                    if (b.type == BuildingType.MINER) {
                        t.minersEnd++;
                        t.minerLevels.merge("№" + (b.level == null ? "?" : b.level), 1,
                            Integer::sum);
                    } else if (b.type == BuildingType.POWER_PLANT) {
                        t.plantsEnd++;
                        t.plantLevels.merge("№" + (b.level == null ? "?" : b.level), 1,
                            Integer::sum);
                    }
                }
            }
            t.games++;
            if (anyMiner) {
                t.gMiner++;
            }
            if (anyAdj) {
                t.gMinerAdj++;
            }
            if (anyPowered) {
                t.gMinerPowered++;
            }
            if (mined[0]) {
                t.gMined++;
            }
            if (soldAfter[0]) {
                t.gSoldAfterMining++;
            }
        }

        String md = render(t, players, games, sampleThoughts);
        Path out = Paths.get("reports", "balance", "поведение-ботов.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md, StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("сохранено: " + out.toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static void observe(Tally t, Map<String, Object> ev,
                                boolean[] mined, boolean[] soldAfter) {
        Object type = ev.get("type");
        if ("turn_end".equals(type)) {
            t.turns++;
            return;
        }
        if ("objective".equals(type)) {
            t.objectivesDoneEv++;
            return;
        }
        if ("container".equals(type)) {
            t.containersOpened++;
            return;
        }
        if (!"action".equals(type) || !Boolean.TRUE.equals(ev.get("ok"))) {
            return;
        }
        String name = String.valueOf(ev.get("action"));
        t.actions++;
        t.byAction.merge(name, 1, Integer::sum);
        Map<String, Object> tel = ev.get("telemetry") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        int kel = num(tel, "kelium");
        int cont = num(tel, "containers");
        int ops = num(tel, "ops");
        int units = num(tel, "units");
        int ammo = num(tel, "ammo");
        int placed = num(tel, "energy_placed");
        int steps = num(tel, "steps");
        // ВАЖНО: ключи телеметрии у каждого действия свои (см. Actions.tel.put).
        // Если проверять не те ключи, «холостыми» окажутся все Движения и Бои —
        // и отчёт соврёт, будто бот ничего не делает.
        int moves = num(tel, "moves");
        int battles = num(tel, "battle");
        // ЧЕСТНЫЙ СЧЁТ ХОЛОСТОГО БОЯ (замечание дизайнера 12.08.2026): Операцию
        // часто вскрывают ради Движения — подвинуться, выждать, собрать
        // контейнеры, — и бить при этом просто некого. Такой розыгрыш холостым
        // не считается. Холостой бой — это когда цели БЫЛИ, а боя не случилось.
        boolean couldFight = Boolean.TRUE.equals(tel.get("could_fight"));
        if ("combat".equals(name)) {
            if (couldFight) {
                t.combatChances++;
                if (battles == 0) {
                    t.combatMissed++;
                }
            } else {
                t.combatNoTargets++;
            }
        }
        int sold = num(tel, "kelium_spent");
        int trophySpent = num(tel, "trophy_spent");
        int hits = battles;
        int total = kel + cont + ops + units + ammo + placed + moves + steps
            + battles + sold + trophySpent;
        boolean idle = "combat".equals(name) ? couldFight && battles == 0 : total == 0;
        if (idle) {
            t.actionsIdle++;
            t.idleByAction.merge(name, 1, Integer::sum);
        }
        if ("mining".equals(name)) {
            t.keliumMined += kel;
            if (kel > 0) {
                mined[0] = true;
            }
        }
        if ("market".equals(name)) {
            t.marketPlays++;
            if (mined[0]) {
                soldAfter[0] = true;
            }
            t.keliumSold += num(tel, "kelium_spent");
        }
        if ("assembly".equals(name)) {
            t.unitsMade += units;
        }
        if ("build".equals(name)) {
            t.coinsSpentOnBuild += num(tel, "coin_spent");
        }
        int powerCoins = num(tel, "power_coins");
        int powerOffers = num(tel, "power_offers");
        t.coinsPaidForPower += powerCoins;
        t.payPowerOffers += powerOffers;
        if (powerCoins > 0) {
            t.payPowerAccepted++;
        }
        if ("combat".equals(name) && hits > 0) {
            t.battles++;
        }
        if ("objective".equals(type)) {
            t.objectivesDone++;
        }
    }

    private static int num(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.intValue() : 0;
    }

    private static String render(Tally t, int players, int games, List<String> sample) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Поведение ботов: цели, цепочки, холостые ходы\n\n");
        sb.append(String.format(Locale.ROOT,
            "Партий: **%d**, игроков: %d. Все места — стратегический бот с планировщиком.%n%n",
            games, players));

        sb.append("## 1. Цепочка добычи — где рвётся\n\n");
        sb.append("| звено | партий | доля |\n|---|---:|---:|\n");
        row(sb, "построен добытчик", t.gMiner, games);
        row(sb, "добытчик примыкает к живой жиле", t.gMinerAdj, games);
        row(sb, "примыкающий добытчик запитан", t.gMinerPowered, games);
        row(sb, "келемий реально снят", t.gMined, games);
        row(sb, "добытое пущено в Маркет", t.gSoldAfterMining, games);
        sb.append('\n');

        sb.append("## 2. Холостые ходы\n\n");
        sb.append(String.format(Locale.ROOT,
            "Действий сыграно: **%d** за %d ходов (%.2f за ход). "
            + "Из них **%d (%.1f%%)** не дали НИЧЕГО.%n%n",
            t.actions, t.turns, t.turns == 0 ? 0.0 : (double) t.actions / t.turns,
            t.actionsIdle, t.actions == 0 ? 0.0 : 100.0 * t.actionsIdle / t.actions));
        sb.append("| действие | сыграно | вхолостую | доля холостых |\n|---|---:|---:|---:|\n");
        for (var e : t.byAction.entrySet()) {
            int idle = t.idleByAction.getOrDefault(e.getKey(), 0);
            sb.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f%% |%n",
                e.getKey(), e.getValue(), idle,
                e.getValue() == 0 ? 0.0 : 100.0 * idle / e.getValue()));
        }
        sb.append('\n');

        sb.append("### Бой — честный счёт\n\n");
        sb.append("Приказ Операция часто вскрывают ради ДВИЖЕНИЯ, и бить при этом некого.\n");
        sb.append("Холостым считается только тот бой, где цели БЫЛИ, а удара не было.\n\n");
        sb.append(String.format(Locale.ROOT,
            "| розыгрышей Боя | бить было некого | бой был возможен | из них упущено |%n"
            + "|---:|---:|---:|---:|%n| %d | %d (%.0f%%) | %d | **%d (%.1f%%)** |%n%n",
            t.combatChances + t.combatNoTargets, t.combatNoTargets,
            pct(t.combatNoTargets, t.combatChances + t.combatNoTargets),
            t.combatChances, t.combatMissed, pct(t.combatMissed, t.combatChances)));

        sb.append("## 3. Какие цели ставят боты\n\n");
        sb.append("| цель | раз |\n|---|---:|\n");
        for (var e : t.goals.entrySet()) {
            sb.append(String.format("| %s | %d |%n", e.getKey(), e.getValue()));
        }
        sb.append('\n');

        sb.append("## 4. Оборот ресурсов\n\n");
        sb.append("| куда уходят монеты | за партию (все игроки) |\n|---|---:|\n");
        avg(sb, "потрачено на стройку", t.coinsSpentOnBuild, games);
        avg(sb, "уплачено за компенсацию энергии", t.coinsPaidForPower, games);
        sb.append(String.format(Locale.ROOT,
            "%n Компенсацию предлагали **%d** раз, согласились **%d** (%.0f%%).%n%n",
            t.payPowerOffers, t.payPowerAccepted,
            t.payPowerOffers == 0 ? 0.0 : 100.0 * t.payPowerAccepted / t.payPowerOffers));
        sb.append("Понимают ли боты выбор УРОВНЯ здания (можно строить любой №1-№4):\n\n");
        sb.append("| добытчики на поле | шт |\n|---|---:|\n");
        for (var e : new java.util.TreeMap<>(t.minerLevels).entrySet()) {
            sb.append(String.format("| %s | %d |%n", e.getKey(), e.getValue()));
        }
        sb.append("\n| энергостанции на поле | шт |\n|---|---:|\n");
        for (var e : new java.util.TreeMap<>(t.plantLevels).entrySet()) {
            sb.append(String.format("| %s | %d |%n", e.getKey(), e.getValue()));
        }
        sb.append('\n');
        avgLine(sb, "добытчиков на игрока к концу", t.minersEnd, games * 4);
        avgLine(sb, "энергостанций на игрока к концу", t.plantsEnd, games * 4);
        avgLine(sb, "жетонов хранилища на игрока", t.storageTokensTaken, games * 4);
        avgLine(sb, "заданий выполнено за партию", t.objectivesDoneEv, games);
        avgLine(sb, "КОНТЕЙНЕРОВ вскрыто за партию (все игроки)", t.containersOpened, games);
        avgLine(sb, "контейнеров осталось нераскрытыми на игрока", t.containersLeft, games * 4);
        sb.append('\n');

        sb.append("## 5. Итоги за партию\n\n");
        sb.append("| показатель | за партию |\n|---|---:|\n");
        avg(sb, "келемия добыто", t.keliumMined, games);
        avg(sb, "келемия продано на маркете", t.keliumSold, games);
        avg(sb, "розыгрышей Маркета", t.marketPlays, games);
        avg(sb, "войск произведено", t.unitsMade, games);
        avg(sb, "шагов науки (сумма по игрокам)", t.techSteps, games);
        avg(sb, "победных очков (сумма по игрокам)", (int) t.vpAvg, games);
        sb.append('\n');

        if (!sample.isEmpty()) {
            sb.append("## 6. Мысли ботов из показательной партии\n\n");
            sb.append("Строки «ЦЕЛЬ: …» — это и есть промежуточные цели: цепочка шагов,\n");
            sb.append("которую бот держит в голове, и то, что ему сейчас мешает.\n\n```\n");
            for (String line : sample) {
                sb.append(line).append('\n');
            }
            sb.append("```\n");
        }
        return sb.toString();
    }

    private static double pct(int part, int whole) {
        return whole == 0 ? 0.0 : 100.0 * part / whole;
    }

    private static void row(StringBuilder sb, String what, int n, int games) {
        sb.append(String.format(Locale.ROOT, "| %s | %d | %.0f%% |%n",
            what, n, games == 0 ? 0.0 : 100.0 * n / games));
    }

    private static void avgLine(StringBuilder sb, String what, int n, int denom) {
        sb.append(String.format(Locale.ROOT, "- %s: **%.2f**%n",
            what, denom == 0 ? 0.0 : (double) n / denom));
    }

    private static void avg(StringBuilder sb, String what, int n, int games) {
        sb.append(String.format(Locale.ROOT, "| %s | %.2f |%n",
            what, games == 0 ? 0.0 : (double) n / games));
    }

    private static Genome loadGenome(int players) {
        try {
            return Genome.loadJson(Locations.botMemory()
                .resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            return Genome.defaults();
        }
    }
}
