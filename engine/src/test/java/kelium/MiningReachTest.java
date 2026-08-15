package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.Setup;
import kelium.engine.TurnContext;

/**
 * ДОБЫЧА ДОТЯГИВАЕТСЯ ТОЛЬКО СВОЕЙ СТЕНКОЙ (баг найден дизайнером 12.08.2026:
 * добытчик копал жилу, к которой стоит спиной) и только у ЗАПИТАННОГО либо
 * оплаченного монетами добытчика.
 */
class MiningReachTest {

    /** Агент, который всегда берёт первую не-пасующую опцию. */
    private static final class Greedy extends Agent {
        Greedy() {
            super(0, "greedy");
        }

        @Override public Choice choose(GameState s, List<Choice> options,
                                       Map<String, Object> ctx) {
            for (Choice o : options) {
                if (!"pass".equals(o.kind()) && o.payload() != null) {
                    return o;
                }
            }
            return options.get(options.size() - 1);
        }
    }

    private static GameState game(long seed) {
        GameState s = Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, seed, null, null));
        List<Agent> agents = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            agents.add(new Greedy());
        }
        s.agents = agents;
        return s;
    }

    /** Гекс с живой жилой и его сосед, где можно поставить добытчика. */
    private static String[] veinAndNeighbour(GameState s) {
        for (Hex h : s.field.hexes.values()) {
            if (h.spawnTile == null || h.spawnTile.kelium <= 0) {
                continue;
            }
            for (String nb : s.field.neighbors(h.id)) {
                Hex n = s.field.get(nb);
                if (n != null && n.spawnTile == null
                        && n.kind != kelium.core.HexKind.FORBIDDEN) {
                    return new String[]{h.id, nb};
                }
            }
        }
        return null;
    }

    /** Поставить добытчика на гекс {@code at} стенкой в сторону {@code facing}. */
    private static BuildingToken miner(GameState s, PlayerState p, String at,
                                       String facing, boolean powered) {
        Hex hex = s.field.get(at);
        BuildingToken m = s.tokenStats.makeBuilding(BuildingType.MINER, p.seat, 9100, 2);
        m.hexId = at;
        p.buildings.add(m);
        int side = -1;
        for (int i = 0; i < 6; i++) {
            if (facing.equals(hex.neighborBySide[i]) && hex.sideOwner[i] == null) {
                side = i;
                break;
            }
        }
        assertTrue(side >= 0, "нашлась свободная стенка в нужную сторону");
        hex.occupySides(m.uid, List.of(side));
        if (powered) {
            m.addEnergyFrom(m.uid, m.energySlots);
        }
        return m;
    }

    /** Прогнать одно действие Добыча и вернуть, сколько келемия добыто. */
    private static int mine(GameState s, PlayerState p) {
        int before = p.resources.kelium();
        Actions.create("mining", s).perform(p, new TurnContext(p.seat, 1), new Greedy());
        return p.resources.kelium() - before;
    }

    @Test
    void minerReachesTheVeinItFacesWithItsOwnWall() {
        GameState s = game(41L);
        String[] pair = veinAndNeighbour(s);
        assertTrue(pair != null, "на поле есть жила с обычным соседом");
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 20);

        miner(s, p, pair[1], pair[0], true);
        assertTrue(mine(s, p) > 0, "добытчик стоит стенкой к жиле — копает");
    }

    @Test
    void minerFacingAwayFromTheVeinDiggsNothing() {
        GameState s = game(42L);
        String[] pair = veinAndNeighbour(s);
        assertTrue(pair != null);
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 20);

        // ставим стенкой в ЛЮБУЮ другую сторону, кроме жилы
        Hex hex = s.field.get(pair[1]);
        BuildingToken m = s.tokenStats.makeBuilding(BuildingType.MINER, 0, 9101, 2);
        m.hexId = pair[1];
        p.buildings.add(m);
        int side = -1;
        for (int i = 0; i < 6; i++) {
            if (hex.sideOwner[i] == null && !pair[0].equals(hex.neighborBySide[i])) {
                side = i;
                break;
            }
        }
        assertTrue(side >= 0);
        hex.occupySides(m.uid, List.of(side));
        m.addEnergyFrom(m.uid, m.energySlots);

        assertEquals(0, mine(s, p),
            "добытчик смотрит в другую сторону — до жилы он не дотягивается");
    }

    @Test
    void unpoweredMinerWithoutMoneyDiggsNothing() {
        GameState s = game(43L);
        String[] pair = veinAndNeighbour(s);
        assertTrue(pair != null);
        PlayerState p = s.player(0);
        // забираем все монеты: компенсацию энергии купить нечем
        p.resources.pay(Resource.COIN, p.resources.coin());

        miner(s, p, pair[1], pair[0], false);
        assertEquals(0, mine(s, p),
            "незапитанный добытчик без денег не копает вообще");
    }

    @Test
    void unpoweredMinerWithMoneyPaysForEnergyAndDiggs() {
        GameState s = game(44L);
        String[] pair = veinAndNeighbour(s);
        assertTrue(pair != null);
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 20);
        int coins = p.resources.coin();

        miner(s, p, pair[1], pair[0], false);
        assertTrue(mine(s, p) > 0, "в Разработке энергию можно докупить монетами");
        assertTrue(p.resources.coin() < coins,
            "и монеты за это действительно списываются");
    }
}
