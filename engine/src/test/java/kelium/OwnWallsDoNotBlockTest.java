package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;

import kelium.engine.Setup;

/**
 * СВОИ ЗДАНИЯ НЕ МЕШАЮТ СВОИМ ВОЙСКАМ.
 *
 * <p>СВОД («Действия — полный свод», движение): «Непроходимо: гексы зарождения,
 * ЧУЖИЕ здания, НЕЙТРАЛЬНЫЕ здания, запретные гексы. Свои здания не мешают».
 * Движок же закрывал ЛЮБУЮ занятую сторону, включая стенки собственных зданий и
 * стоящие войска — войска сидели запертыми в своей базе (найдено зондом
 * MovementProbe 12.08.2026: 58,7% закрытых рёбер приходилось на СВОИ стенки).
 */
class OwnWallsDoNotBlockTest {

    private static GameState game() {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 4242L, null, null));
    }

    /** Пара соседних гексов, оба свободны от тайлов и нейтралов. */
    private static String[] freePair(GameState s) {
        for (Hex h : s.field.hexes.values()) {
            if (h.hasNeutral() || h.hasSpawnTile()) {
                continue;
            }
            for (int side = 0; side < 6; side++) {
                String nb = h.neighborBySide[side];
                Hex n = nb == null ? null : s.field.get(nb);
                if (n != null && !n.hasNeutral() && !n.hasSpawnTile()) {
                    return new String[]{h.id, nb, String.valueOf(side)};
                }
            }
        }
        return null;
    }

    @Test
    void ownWallLetsOwnTroopThrough() {
        GameState s = game();
        String[] pair = freePair(s);
        assertTrue(pair != null, "нужны два свободных соседних гекса");
        Hex from = s.field.get(pair[0]);
        int side = Integer.parseInt(pair[2]);

        PlayerState me = s.player(0);
        UnitToken u = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 9001);
        u.hexId = from.id;
        me.units.add(u);

        // своё здание встаёт СТЕНКОЙ ровно на то ребро, куда идёт войско
        BuildingToken mine = s.tokenStats.makeBuilding(BuildingType.MINER, 0, 9002, 1);
        mine.hexId = from.id;
        from.occupySides(mine.uid, List.of(side));

        assertTrue(kelium.engine.Movement.canEnter(s, u, pair[1], 0),
            "своё здание не должно запирать своё же войско");
    }

    @Test
    void enemyWallStillBlocks() {
        GameState s = game();
        String[] pair = freePair(s);
        assertTrue(pair != null, "нужны два свободных соседних гекса");
        Hex from = s.field.get(pair[0]);
        int side = Integer.parseInt(pair[2]);

        UnitToken u = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 9101);
        u.hexId = from.id;
        s.player(0).units.add(u);

        // ЧУЖОЕ здание на том же ребре — проход закрыт
        BuildingToken foe = s.tokenStats.makeBuilding(BuildingType.MINER, 1, 9102, 1);
        foe.hexId = from.id;
        s.player(1).buildings.add(foe);
        from.occupySides(foe.uid, List.of(side));

        assertFalse(kelium.engine.Movement.canEnter(s, u, pair[1], 0),
            "чужое здание обязано закрывать проход");
    }

    @Test
    void ownTroopIsNotAWall() {
        GameState s = game();
        String[] pair = freePair(s);
        assertTrue(pair != null, "нужны два свободных соседних гекса");
        Hex from = s.field.get(pair[0]);
        int side = Integer.parseInt(pair[2]);

        UnitToken walker = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 9201);
        walker.hexId = from.id;
        s.player(0).units.add(walker);

        UnitToken sitting = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 9202);
        sitting.hexId = from.id;
        s.player(0).units.add(sitting);
        from.occupySides(sitting.uid, List.of(side));

        assertTrue(kelium.engine.Movement.canEnter(s, walker, pair[1], 0),
            "войска не образуют стенок: они переупаковываются внутри гекса");
    }
}
