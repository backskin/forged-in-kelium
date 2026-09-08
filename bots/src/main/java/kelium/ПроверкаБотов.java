package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Bots;
import kelium.agents.PlannerAgent;
import kelium.core.Agent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.LayoutLibrary;
import kelium.engine.Scoring;
import kelium.engine.Setup;

/**
 * ПРОВЕРКА БОТОВ — три требования дизайнера (07.09.2026) в цифрах.
 *
 * <ol>
 *   <li>выполняют ли задания (а не жгут их);</li>
 *   <li>ставят ли карты арсенала (низ), а не только жгут верх;</li>
 *   <li>производят ли войска и тратят ли боеприпасы на уничтожение чужих жетонов.</li>
 * </ol>
 *
 * <p>Плюс то, без чего числа выше не читаются: боёв за партию, попаданий,
 * произведённых боеприпасов против войск, очки и победы по характерам, время на
 * партию и сколько сценариев планировщик проиграл на копиях.
 *
 * <p>Запуск: {@code kelium.ПроверкаБотов [игроков] [партий] [уровень 1-4|legacyN]}.
 * {@code legacy3} сажает за стол прежних ботов третьего уровня — для сравнения.
 */
public final class ПроверкаБотов {

    private ПроверкаБотов() {
    }

    static final class Tally {
        int games;
        int wins;
        int vp;
        int objDone;
        int objBurn;
        int arsInstall;
        int arsBurn;
        int arsSpec;
        int unitsMade;
        int ammoMade;
        int battles;
        int hits;
        int kills;
        int cuKills;
        int moves;
        int plans;
        int sims;
        int misses;

        double per(int v) {
            return games == 0 ? 0 : (double) v / games;
        }
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        String levelArg = args.length > 2 ? args[2] : "2";
        boolean legacy = levelArg.startsWith("legacy");
        int level = Integer.parseInt(legacy ? levelArg.substring(6) : levelArg);

        List<String> roster = Bots.ROSTER_4;
        Map<String, Tally> by = new LinkedHashMap<>();
        for (String c : roster) {
            by.put(c, new Tally());
        }
        Map<String, Integer> endBy = new LinkedHashMap<>();
        Map<String, Integer> missByKind = new java.util.TreeMap<>();
        Map<String, Integer> actionsByName = new java.util.TreeMap<>();
        long t0 = System.nanoTime();
        int roundsTotal = 0;
        for (int g = 0; g < games; g++) {
            long seed = 7000L + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            List<Agent> agents = new ArrayList<>();
            List<String> chars = new ArrayList<>();
            int shift = (int) (seed % players);
            for (int i = 0; i < players; i++) {
                String ch = roster.get((i + shift) % roster.size());
                chars.add(ch);
                Random rng = new Random(seed * 31 + i);
                agents.add(legacy
                    ? Bots.createLegacy(ch, Bots.Level.of(level), i, rng, players)
                    : Bots.create(ch, Bots.Level.of(level), i, rng, players));
            }
            Map<String, Object> result = new GameEngine(s, agents, ev -> {
                Object seatObj = ev.get("seat");
                if (!(seatObj instanceof Number sn)) {
                    return;
                }
                Tally t = by.get(chars.get(sn.intValue()));
                switch (String.valueOf(ev.get("type"))) {
                    case "objective" -> t.objDone++;
                    case "objective_burn" -> t.objBurn++;
                    case "arsenal_spec_use", "ability_spec" -> {
                        if (!Boolean.FALSE.equals(ev.get("did"))) {
                            t.arsSpec++;
                        }
                    }
                    case "arsenal" -> {
                        if ("install".equals(ev.get("mode"))) {
                            t.arsInstall++;
                        } else if ("burn".equals(ev.get("mode"))) {
                            t.arsBurn++;
                        }
                    }
                    case "combat_hit" -> {
                        t.hits++;
                        if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                            t.kills++;
                        }
                    }
                    case "cu_destroyed" -> {
                        if (ev.get("by") instanceof Number by0) {
                            by.get(chars.get(by0.intValue())).cuKills++;
                        }
                    }
                    case "action" -> {
                        actionsByName.merge(String.valueOf(ev.get("action")), 1, Integer::sum);
                        Object tel = ev.get("telemetry");
                        if (!(tel instanceof Map<?, ?> m)) {
                            return;
                        }
                        if ("combat".equals(ev.get("action")) && num(m.get("battle")) == 0) {
                            actionsByName.merge(Boolean.TRUE.equals(m.get("could_fight"))
                                ? "combat:отказался" : "combat:некого", 1, Integer::sum);
                        }
                        switch (String.valueOf(ev.get("action"))) {
                            case "assembly" -> {
                                t.unitsMade += num(m.get("units"));
                                t.ammoMade += num(m.get("ammo"));
                            }
                            case "combat" -> t.battles += num(m.get("battle"));
                            case "movement" -> t.moves += num(m.get("moves"));
                            default -> { }
                        }
                    }
                    default -> { }
                }
            }).run();
            roundsTotal += s.round;
            String cond = String.valueOf(s.winCondition);
            endBy.merge(cond, 1, Integer::sum);
            for (int i = 0; i < players; i++) {
                Tally t = by.get(chars.get(i));
                t.games++;
                t.vp += Scoring.scorePlayer(s, i).getOrDefault("total", 0);
                if (s.winner != null && s.winner == i) {
                    t.wins++;
                }
                if (agents.get(i) instanceof PlannerAgent pa) {
                    t.plans += pa.plansMade;
                    t.sims += pa.simulations;
                    t.misses += pa.scriptMisses;
                    for (var e : pa.missByKind.entrySet()) {
                        missByKind.merge(e.getKey(), e.getValue(), Integer::sum);
                    }
                }
            }
            out.printf(Locale.ROOT, "партия %d: раундов %d, конец %s, победил %s%n",
                g + 1, s.round, cond, s.winner == null ? "—" : chars.get(s.winner));
        }
        double sec = (System.nanoTime() - t0) / 1e9;
        out.println();
        out.printf(Locale.ROOT, "Игроков %d, партий %d, боты %s уровня %d; %.1f с на партию; "
            + "раундов в среднем %.1f%n", players, games, legacy ? "прежние" : "планировщик",
            level, sec / games, (double) roundsTotal / games);
        out.println("Условия конца: " + endBy);
        if (!missByKind.isEmpty()) {
            out.println("Промахи сценария по видам решений: " + missByKind);
        }
        StringBuilder acts = new StringBuilder("Действий на партию: ");
        for (var e : actionsByName.entrySet()) {
            acts.append(e.getKey()).append(' ')
                .append(String.format(Locale.ROOT, "%.1f", (double) e.getValue() / games)).append("; ");
        }
        out.println(acts);
        out.println();
        out.printf(Locale.ROOT, "%-10s %5s %5s | %6s %6s %5s | %6s %6s %6s | %6s %6s | %5s %5s %5s %5s | %5s %6s %5s%n",
            "характер", "ПО", "побед", "задВып", "задСож", "доля", "арсУст", "арсСож", "арсСПЕЦ",
            "войск", "БПР", "боёв", "попад", "убито", "ЦУ", "план", "симул", "промах");
        Tally all = new Tally();
        for (var e : by.entrySet()) {
            Tally t = e.getValue();
            print(out, e.getKey(), t);
            all.games += t.games;
            all.wins += t.wins;
            all.vp += t.vp;
            all.objDone += t.objDone;
            all.objBurn += t.objBurn;
            all.arsInstall += t.arsInstall;
            all.arsBurn += t.arsBurn;
            all.arsSpec += t.arsSpec;
            all.unitsMade += t.unitsMade;
            all.ammoMade += t.ammoMade;
            all.battles += t.battles;
            all.hits += t.hits;
            all.kills += t.kills;
            all.cuKills += t.cuKills;
            all.moves += t.moves;
            all.plans += t.plans;
            all.sims += t.sims;
            all.misses += t.misses;
        }
        print(out, "ВСЕГО", all);
    }

    private static void print(PrintStream out, String name, Tally t) {
        int acted = t.objDone + t.objBurn;
        double share = acted == 0 ? 0 : 100.0 * t.objDone / acted;
        out.printf(Locale.ROOT, "%-10s %5.1f %5d | %6.2f %6.2f %4.0f%% | %6.2f %6.2f %6.2f | %6.2f %6.2f | %5.2f %5.2f %5.2f %5.2f | %5.1f %6.0f %5.1f%n",
            name, t.per(t.vp), t.wins, t.per(t.objDone), t.per(t.objBurn), share,
            t.per(t.arsInstall), t.per(t.arsBurn), t.per(t.arsSpec),
            t.per(t.unitsMade), t.per(t.ammoMade),
            t.per(t.battles), t.per(t.hits), t.per(t.kills), t.per(t.cuKills),
            t.per(t.plans), t.per(t.sims), t.per(t.misses));
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
