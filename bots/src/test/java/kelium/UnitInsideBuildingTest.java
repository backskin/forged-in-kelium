package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Genome;
import kelium.agents.StrategicAgent;
import kelium.agents.WorldView;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Token;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.engine.GameEngine;
import kelium.engine.Placement;
import kelium.support.Fix;

/**
 * ВОЙСКО ВНУТРИ ЗДАНИЯ (правило дизайнера 12.08.2026).
 *
 * <p>Найм идёт на гекс со зданием; места нет — войско можно вставить ПРЯМО В
 * ЗДАНИЕ, но не больше одного войска в здание и не больше одного такого здания у
 * игрока; места нет совсем — жетон не нанимается. Вставленное войско не занимает
 * ячейку гекса и НЕ АТАКУЕМО, пока здание живо.
 *
 * <p>Тесты сторожат именно то, что было сломано: раньше укрытие ВЫЧИСЛЯЛОСЬ по
 * совпадению рода войск и типа здания на гексе, из-за чего неуязвимыми становились
 * ВСЕ войска у своих зданий, а вышка на гексе ЦУ — тоже, хотя ей прятаться
 * запрещено прямым правилом.
 */
class UnitInsideBuildingTest {

    /** Казарма игрока 0 на свободном гексе рядом со стартом (на старте стоит ЦУ). */
    private static BuildingToken barracksNextToStart(GameState s) {
        String hex = Fix.freeNeighbour(s, s.player(0).startHex);
        return Fix.building(s, 0, BuildingType.BARRACKS, hex, null);
    }

    /** Просто стоять на гексе своего здания — НЕ укрытие. */
    @Test
    void standingNextToOwnBuildingIsNotHiding() {
        GameState s = Fix.game(4, 41L);
        BuildingToken barracks = barracksNextToStart(s);
        UnitToken inf = Fix.unit(s, 0, UnitType.INFANTRY, barracks.hexId);

        assertFalse(inf.inside(), "жетон не вставлен в здание — значит не укрыт");
        WorldView wv = new WorldView(s, 1);
        boolean visible = false;
        for (Token t : wv.enemyTokens) {
            if (t.uid() == inf.uid) {
                visible = true;
            }
        }
        assertTrue(visible, "войско у своего здания обязано быть обычной целью");
    }

    /** Вставленное войско не видно как цель, но само здание видно. */
    @Test
    void unitInsideBuildingIsNotATargetButTheBuildingIs() {
        GameState s = Fix.game(4, 42L);
        BuildingToken barracks = barracksNextToStart(s);
        UnitToken inf = Fix.unit(s, 0, UnitType.INFANTRY, barracks.hexId);
        inf.insideBuildingUid = barracks.uid;

        WorldView wv = new WorldView(s, 1);
        boolean unitSeen = false;
        boolean buildingSeen = false;
        for (Token t : wv.enemyTokens) {
            if (t.uid() == inf.uid) {
                unitSeen = true;
            }
            if (t.uid() == barracks.uid) {
                buildingSeen = true;
            }
        }
        assertFalse(unitSeen, "войско внутри здания не должно быть целью");
        assertTrue(buildingSeen, "здание целью остаётся — через него и добираются");
    }

    /** Внутри здания жетон не занимает ячейку гекса. */
    @Test
    void unitInsideDoesNotOccupyAHexCell() {
        GameState s = Fix.game(4, 43L);
        BuildingToken barracks = barracksNextToStart(s);
        UnitToken inf = Fix.unit(s, 0, UnitType.INFANTRY, barracks.hexId);

        int[] withUnit = Placement.groundLoad(s, barracks.hexId, -1);
        inf.insideBuildingUid = barracks.uid;
        int[] whenInside = Placement.groundLoad(s, barracks.hexId, -1);
        assertEquals(withUnit[1] - 1, whenInside[1],
            "войско внутри здания перестаёт занимать ячейку на гексе");
    }

    /** Уход с гекса выводит войско из здания. */
    @Test
    void leavingTheHexEvictsTheUnit() {
        GameState s = Fix.game(4, 44L);
        BuildingToken barracks = barracksNextToStart(s);
        UnitToken inf = Fix.unit(s, 0, UnitType.INFANTRY, barracks.hexId);
        inf.insideBuildingUid = barracks.uid;

        String nb = Fix.freeNeighbour(s, barracks.hexId);
        inf.setHexId(nb);
        assertNull(inf.insideBuildingUid,
            "сменил гекс — вышел из здания (внутри можно стоять только на его гексе)");
    }

    /** Возврат в запас тоже выводит из здания. */
    @Test
    void goingToReserveEvictsTheUnit() {
        GameState s = Fix.game(4, 45L);
        BuildingToken barracks = barracksNextToStart(s);
        UnitToken inf = Fix.unit(s, 0, UnitType.INFANTRY, barracks.hexId);
        inf.insideBuildingUid = barracks.uid;
        inf.setHexId(null);
        assertNull(inf.insideBuildingUid);
    }

    /** Копия состояния переносит признак «внутри здания». */
    @Test
    void copyKeepsTheInsideFlag() {
        GameState s = Fix.game(4, 46L);
        BuildingToken barracks = barracksNextToStart(s);
        UnitToken inf = Fix.unit(s, 0, UnitType.INFANTRY, barracks.hexId);
        inf.insideBuildingUid = barracks.uid;

        GameState c = s.deepCopy(7L);
        UnitToken copy = null;
        for (UnitToken u : c.player(0).units) {
            if (u.uid == inf.uid) {
                copy = u;
            }
        }
        assertEquals(Integer.valueOf(barracks.uid), copy.insideBuildingUid);
        // и правка копии не задевает оригинал
        copy.insideBuildingUid = null;
        assertTrue(inf.inside());
    }

    /**
     * НАЙМ: в полной партии правило соблюдается — у каждого игрока не больше
     * ОДНОГО войска внутри здания, и вышка внутри не оказывается никогда.
     */
    @Test
    void hiringNeverBreaksTheOnePerPlayerLimit() {
        for (long seed : new long[]{7L, 21L, 55L, 101L}) {
            GameState s = Fix.game(4, seed);
            List<Agent> agents = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                agents.add(new StrategicAgent(i, new Random(seed + i), Genome.defaults()));
            }
            GameEngine.playGame(s, agents, null);
            for (int seat = 0; seat < 4; seat++) {
                int inside = 0;
                for (UnitToken u : s.player(seat).units) {
                    if (!u.inside()) {
                        continue;
                    }
                    inside++;
                    assertFalse(u.type == UnitType.TOWER,
                        "вышка внутрь здания не вставляется (сид " + seed + ")");
                    // жетон внутри обязан ссылаться на ЖИВОЕ своё здание на поле
                    boolean host = false;
                    for (BuildingToken b : s.player(seat).buildings) {
                        if (b.uid == u.insideBuildingUid && b.alive() && b.hexId != null) {
                            host = true;
                        }
                    }
                    assertTrue(host, "войско внутри снесённого здания — рассинхрон "
                        + "(сид " + seed + ", место " + seat + ")");
                }
                assertTrue(inside <= 1,
                    "у игрока не больше одного войска внутри здания, найдено " + inside
                        + " (сид " + seed + ", место " + seat + ")");
            }
        }
    }

    /**
     * Вышка нанимается на гекс, где стоит ЛЮБОЕ здание игрока.
     *
     * <p>Проверяем В МОМЕНТ НАЙМА, а не в конце партии: к концу здание, на гексе
     * которого встала вышка, может быть уже снесено соперником — и вышка законно
     * останется на гексе без здания. Первая версия теста этого не учитывала и
     * падала на сиде 33 именно по такой честной причине.
     */
    @Test
    void towerGoesToAHexWithAnyOwnBuilding() {
        GameState s = Fix.game(2, 91L);
        List<Agent> agents = new ArrayList<>();
        agents.add(new Fix.FirstChoiceAgent(0));
        agents.add(new Fix.FirstChoiceAgent(1));
        GameEngine.bind(s, agents, null);
        Fix.turn(s, 0);

        // ЦУ у игрока есть с подготовки — из него и выходит вышка. Запитываем его
        // и добавляем ВТОРОЕ здание на соседнем гексе: если на гексе ЦУ места не
        // останется, вышка обязана уйти именно к нему, а не куда попало.
        BuildingToken cu = null;
        for (BuildingToken b : s.player(0).buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                cu = b;
            }
        }
        assertTrue(cu != null, "ЦУ на подготовке должно быть");
        Fix.power(cu);
        String other = placeableHex(s, cu.hexId);
        assertTrue(other != null, "на поле должен найтись гекс под второе здание");
        BuildingToken barracks = Fix.building(s, 0, BuildingType.BARRACKS, other, null);
        Fix.power(barracks);

        int towersBefore = countTowers(s);
        kelium.engine.Actions.create("assembly", s)
            .perform(s.player(0), new kelium.engine.TurnContext(0, 1), agents.get(0));
        assertTrue(countTowers(s) > towersBefore, "Сборка из ЦУ должна дать вышку");

        for (UnitToken u : s.player(0).unitsOnField()) {
            if (u.type != UnitType.TOWER) {
                continue;
            }
            boolean ownBuildingHere = false;
            for (BuildingToken b : s.player(0).buildingsOnField()) {
                if (u.hexId.equals(b.hexId)) {
                    ownBuildingHere = true;
                }
            }
            assertTrue(ownBuildingHere,
                "вышка встала на гекс " + u.hexId + " без своего здания");
            assertFalse(u.inside(), "вышка внутрь здания не вставляется");
        }
    }

    /**
     * Гекс, куда точно влезет казарма: сперва пробуем соседей заданного гекса,
     * потом всё поле. Через {@code Fix.freeNeighbour} нельзя — на тесных полях он
     * возвращает null, и тест падал не по делу.
     */
    private static String placeableHex(GameState s, String near) {
        List<String> candidates = new ArrayList<>(s.field.neighbors(near));
        candidates.addAll(s.field.hexes.keySet());
        for (String id : candidates) {
            if (id.equals(near)) {
                continue;
            }
            kelium.core.Hex h = s.field.get(id);
            if (h == null || h.kind != kelium.core.HexKind.NORMAL || h.hasSpawnTile()
                    || h.hasNeutral()) {
                continue;
            }
            if (h.chooseFootprint(kelium.engine.Placement.footprint(BuildingType.BARRACKS),
                    0, 0) != null) {
                return id;
            }
        }
        return null;
    }

    private static int countTowers(GameState s) {
        int n = 0;
        for (UnitToken u : s.player(0).unitsOnField()) {
            if (u.type == UnitType.TOWER) {
                n++;
            }
        }
        return n;
    }
}
