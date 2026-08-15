package kelium;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.SpawnTile;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.dataio.Locations;
import kelium.core.Agent;
import kelium.engine.LayoutLibrary;

/**
 * MiningProbe — почему тайлы зарождения остаются целыми.
 *
 * <p>Жалоба дизайнера: за 8 раундов НИ ОДИН тайл не выработан. Зонд разбирает
 * причину по шагам цепочки, которая должна привести к добыче:
 *
 * <ol>
 *   <li>построен ли вообще добытчик;</li>
 *   <li>стоит ли он рядом с тайлом, где ещё есть келемий (примыкание стенкой);</li>
 *   <li>запитан ли он;</li>
 *   <li>сыграна ли Добыча;</li>
 *   <li>хватило ли места в хранилище.</li>
 * </ol>
 *
 * <p>Печатает, на каком шаге цепочка рвётся чаще всего — это и есть ответ,
 * баг это или поведение ботов.
 *
 * <p>Запуск: {@code java -cp ... kelium.MiningProbe [players] [games]}
 */
public final class MiningProbe {

    private MiningProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(
            java.io.FileDescriptor.out), true, "UTF-8"));
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 20;

        int gamesWithMiner = 0;
        int gamesWithAdjacentMiner = 0;
        int gamesWithPoweredAdjacentMiner = 0;
        int gamesWithMiningPlayed = 0;
        int gamesWithKeliumTaken = 0;
        int totalKeliumOnTiles = 0;
        int totalKeliumLeft = 0;
        int tilesTouched = 0;
        int tilesTotal = 0;
        int minersBuilt = 0;
        int minersPowered = 0;
        int minersAdjacent = 0;

        Genome genome;
        try {
            genome = Genome.loadJson(Locations.botMemory()
                .resolve("strategic_" + players + "p.json"));
        } catch (Exception e) {
            genome = Genome.defaults();
        }

        for (int g = 0; g < games; g++) {
            long seed = 1000 + g;
            GameConfig cfg = LayoutLibrary.configFor(players, seed);
            GameState s = Setup.buildGame(cfg);
            int printed = 0;
            for (Hex h : s.field.hexes.values()) {
                if (h.spawnTile != null) {
                    printed += h.spawnTile.kelium;
                    tilesTotal++;
                }
            }
            totalKeliumOnTiles += printed;

            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < players; seat++) {
                agents.add(new StrategicAgent(seat, new Random(seed * 131 + seat), genome));
            }
            final boolean[] minedFlag = {false};
            final boolean[] miningPlayed = {false};
            GameEngine.playGame(s, agents, ev -> {
                if ("action".equals(ev.get("type")) && "mining".equals(ev.get("action"))) {
                    miningPlayed[0] = true;
                    if (ev.get("telemetry") instanceof java.util.Map<?, ?> tel
                            && tel.get("kelium") instanceof Number k && k.intValue() > 0) {
                        minedFlag[0] = true;
                    }
                }
            });

            boolean anyMiner = false;
            boolean anyAdjacent = false;
            boolean anyPoweredAdjacent = false;
            for (int seat = 0; seat < players; seat++) {
                PlayerState p = s.player(seat);
                for (BuildingToken b : p.buildingsOnField()) {
                    if (b.type != BuildingType.MINER) {
                        continue;
                    }
                    anyMiner = true;
                    minersBuilt++;
                    boolean adj = touchesTile(s, b.hexId);
                    if (adj) {
                        anyAdjacent = true;
                        minersAdjacent++;
                    }
                    if (b.powered()) {
                        minersPowered++;
                        if (adj) {
                            anyPoweredAdjacent = true;
                        }
                    }
                }
            }
            for (Hex h : s.field.hexes.values()) {
                SpawnTile t = h.spawnTile;
                if (t != null) {
                    totalKeliumLeft += t.kelium;
                    if (t.flipped || t.kelium < t.faceKelium) {
                        tilesTouched++;
                    }
                }
            }

            if (anyMiner) {
                gamesWithMiner++;
            }
            if (anyAdjacent) {
                gamesWithAdjacentMiner++;
            }
            if (anyPoweredAdjacent) {
                gamesWithPoweredAdjacentMiner++;
            }
            if (miningPlayed[0]) {
                gamesWithMiningPlayed++;
            }
            if (minedFlag[0]) {
                gamesWithKeliumTaken++;
            }
        }

        System.out.println("=== Зонд добычи: " + games + " партий, " + players + " игроков ===");
        System.out.println();
        System.out.println("Цепочка «чтобы тайл выработался» — где она рвётся:");
        System.out.printf("  1. построен добытчик ................ %d/%d партий%n",
            gamesWithMiner, games);
        System.out.printf("  2. добытчик ПРИМЫКАЕТ к тайлу ....... %d/%d партий%n",
            gamesWithAdjacentMiner, games);
        System.out.printf("  3. примыкающий добытчик ЗАПИТАН ..... %d/%d партий%n",
            gamesWithPoweredAdjacentMiner, games);
        System.out.printf("  4. Добыча вообще сыграна ............ %d/%d партий%n",
            gamesWithMiningPlayed, games);
        System.out.printf("  5. келемий реально СНЯТ с тайла ..... %d/%d партий%n",
            gamesWithKeliumTaken, games);
        System.out.println();
        System.out.printf("Добытчиков на поле к концу: %d (запитанных %d, примыкающих %d)%n",
            minersBuilt, minersPowered, minersAdjacent);
        System.out.printf("Келемий на тайлах: было %d, осталось %d — выработано %d (%.1f%%)%n",
            totalKeliumOnTiles, totalKeliumLeft, totalKeliumOnTiles - totalKeliumLeft,
            totalKeliumOnTiles == 0 ? 0.0
                : 100.0 * (totalKeliumOnTiles - totalKeliumLeft) / totalKeliumOnTiles);
        System.out.printf("Тайлов тронуто: %d из %d%n", tilesTouched, tilesTotal);
    }

    /** Примыкает ли гекс {@code hexId} к тайлу с келемием (или сам стоит на нём). */
    private static boolean touchesTile(GameState s, String hexId) {
        Hex self = s.field.get(hexId);
        if (self != null && self.spawnTile != null) {
            return true;
        }
        for (String nb : s.field.neighbors(hexId)) {
            Hex h = s.field.get(nb);
            if (h != null && h.spawnTile != null) {
                return true;
            }
        }
        return false;
    }
}
