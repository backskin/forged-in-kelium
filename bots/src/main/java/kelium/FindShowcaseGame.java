package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.GameState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.dataio.Locations;
import kelium.core.Agent;
import kelium.engine.LayoutLibrary;

/**
 * FindShowcaseGame — поиск ПОКАЗАТЕЛЬНОЙ партии: с боями, всеми родами войск и
 * реально выработанным келемием.
 *
 * <p>Нужен, чтобы картинка поля показывала живую игру, а не пустое поле с
 * нетронутыми тайлами зарождения. Перебирает сиды, считает по каждому: сколько
 * было боёв и уничтожений, какие рода войск появились, сколько добыто келемия и
 * сколько тайлов перевёрнуто/снято. Печатает лучшие сиды по сумме признаков.
 *
 * <p>Запуск: {@code java -cp ... kelium.FindShowcaseGame [players] [seedsToScan]}
 */
public final class FindShowcaseGame {

    private FindShowcaseGame() {
    }

    /** Что интересного случилось в партии. */
    public record Stats(long seed, int battles, int kills, int keliumMined, int tilesTouched,
                        boolean infantry, boolean vehicle, boolean aircraft, boolean tower,
                        int buildings, int rounds) {

        /** Насколько партия «показательная»: чем больше, тем лучше. */
        public int score() {
            int kinds = (infantry ? 1 : 0) + (vehicle ? 1 : 0) + (aircraft ? 1 : 0)
                + (tower ? 1 : 0);
            return battles * 3 + kills * 5 + keliumMined * 2 + tilesTouched * 8
                + kinds * 25 + buildings;
        }

        @Override public String toString() {
            return String.format(Locale.ROOT,
                "сид %-6d очки %-5d | боёв %-3d уничтожено %-3d | келемий %-3d тайлов тронуто %d "
                + "| пехота %s техника %s авиация %s вышка %s | зданий %d, раундов %d",
                seed, score(), battles, kills, keliumMined, tilesTouched,
                infantry ? "+" : "−", vehicle ? "+" : "−", aircraft ? "+" : "−",
                tower ? "+" : "−", buildings, rounds);
        }
    }

    /** Сыграть одну партию и собрать статистику. Агенты — обученные стратеги. */
    public static Stats play(int players, long seed) {
        GameConfig cfg = LayoutLibrary.configFor(players, seed);
        GameState s = Setup.buildGame(cfg);
        Genome genome = loadGenome(players);
        List<Agent> agents = new ArrayList<>();
        String[] profs = {"hawk", "opportunist", "hawk", "balanced"};
        for (int seat = 0; seat < players; seat++) {
            Random r = new Random(seed * 131 + seat + 1);
            // Ястребы и Исследователь: первые воюют, второй трогает все механики
            agents.add(switch (seat % 4) {
                case 2 -> kelium.agents.Bots.create("explorer", seat, r, players);
                case 3 -> kelium.agents.Bots.create("chaos", seat, r, players);
                default -> new StrategicAgent(seat, r, genome.withProfile(profs[seat % 4]));
            });
        }
        int[] battles = {0};
        int[] kills = {0};
        GameEngine.playGame(s, agents, ev -> {
            Object t = ev.get("type");
            if ("combat_hit".equals(t)) {
                battles[0]++;
                if (Boolean.TRUE.equals(ev.get("destroyed"))) {
                    kills[0]++;
                }
            }
        });

        int kelium = 0;
        int buildings = 0;
        boolean inf = false;
        boolean veh = false;
        boolean air = false;
        boolean tow = false;
        for (var p : s.players) {
            kelium += p.resources.kelium();
            buildings += p.buildingsOnField().size();
            for (UnitToken u : p.units) {
                if (u.hexId == null) {
                    continue;
                }
                switch (u.type) {
                    case INFANTRY -> inf = true;
                    case VEHICLE -> veh = true;
                    case AIRCRAFT -> air = true;
                    case TOWER -> tow = true;
                    default -> { }
                }
            }
        }
        int touched = 0;
        for (var h : s.field.hexes.values()) {
            if (h.spawnTile != null && (h.spawnTile.flipped
                    || h.spawnTile.kelium < h.spawnTile.faceKelium)) {
                touched++;
            }
        }
        return new Stats(seed, battles[0], kills[0], kelium, touched, inf, veh, air, tow,
            buildings, s.round);
    }

    private static Genome loadGenome(int players) {
        try {
            return Genome.loadJson(Locations.botMemory()
                .resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            return Genome.defaults();
        }
    }

    public static void main(String[] args) {
        java.io.PrintStream out = new java.io.PrintStream(System.out, true,
            java.nio.charset.StandardCharsets.UTF_8);
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int scan = args.length > 1 ? Integer.parseInt(args[1]) : 60;

        List<Stats> all = new ArrayList<>();
        for (long seed = 1; seed <= scan; seed++) {
            try {
                all.add(play(players, seed));
            } catch (RuntimeException e) {
                out.println("сид " + seed + ": сбой — " + e.getMessage());
            }
        }
        all.sort((a, b) -> b.score() - a.score());
        out.println("=== ЛУЧШИЕ ПАРТИИ (" + players + " игроков, просмотрено " + scan + ") ===");
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            out.println(all.get(i));
        }
        int withAir = 0;
        int withVeh = 0;
        int withBattle = 0;
        int withMining = 0;
        for (Stats st : all) {
            if (st.aircraft()) {
                withAir++;
            }
            if (st.vehicle()) {
                withVeh++;
            }
            if (st.battles() > 0) {
                withBattle++;
            }
            if (st.tilesTouched() > 0) {
                withMining++;
            }
        }
        out.printf(Locale.ROOT, "%nИЗ %d ПАРТИЙ: с боями %d · с техникой %d · с авиацией %d "
            + "· с добычей келемия %d%n", all.size(), withBattle, withVeh, withAir, withMining);
        Map<String, Object> ignored = Map.of();
        out.println(ignored.isEmpty() ? "" : "");
    }
}
