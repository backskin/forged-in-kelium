package kelium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Predicates;
import kelium.engine.Setup;
import kelium.core.TurnJournal;

/**
 * «Живая» проверка геометрии заданий: каждый гео-предикат должен ЛОВИТЬ нужную
 * расстановку (позитив) И ОТВЕРГАТЬ неподходящую (негатив). Проверяем соседство
 * гексов, размещение на одном гексе, НЕсоседство (расстояние) и общую стенку.
 *
 * <p>Стенд строит контролируемую расстановку прямо на сценарном поле 4p
 * (реальные гексы + стороны), вручную размещая жетоны на конкретные гексы.
 */
class GeometryLiveTest {

    private GameState fresh() {
        return Setup.buildGame(GameConfig.build(4, 0L));
    }

    private TurnJournal j(GameState s) {
        TurnJournal tj = new TurnJournal(s.numPlayers());
        tj.startTurn(0);
        return tj;
    }

    /** Найти гекс с >=3 соседями — для узоров «кольцо/веер». */
    private String hubWith(GameState s, int minNeigh) {
        for (String id : s.field.hexes.keySet()) {
            if (s.field.neighbors(id).size() >= minNeigh) {
                return id;
            }
        }
        throw new IllegalStateException("нет гекса с " + minNeigh + " соседями");
    }

    private void clearSeat0(GameState s) {
        s.player(0).buildings.clear();
        s.player(0).units.clear();
    }

    private BuildingToken putBld(GameState s, BuildingType t, String hex, int uid) {
        BuildingToken b = s.tokenStats.makeBuilding(t, 0, uid, t == BuildingType.MINER
                || t == BuildingType.POWER_PLANT ? 1 : null);
        b.hexId = hex;
        s.player(0).buildings.add(b);
        return b;
    }

    private boolean chk(GameState s, String pid, Map<String, Object> params) {
        return Predicates.check(pid, s, 0, j(s), params);
    }

    // ---- Соседство гексов: кольцо зданий вокруг центра ----
    @Test
    void ringNeighbourPositiveAndNegative() {
        GameState s = fresh();
        clearSeat0(s);
        String hub = hubWith(s, 3);
        List<String> nbs = s.field.neighbors(hub);
        // позитив: 3 здания на трёх соседях центра
        putBld(s, BuildingType.BARRACKS, nbs.get(0), 901);
        putBld(s, BuildingType.BARRACKS, nbs.get(1), 902);
        putBld(s, BuildingType.BARRACKS, nbs.get(2), 903);
        assertTrue(chk(s, "buildings_ring_around_hex", Map.of("count", 3)),
            "3 здания вокруг центра должны давать ring>=3");
        // негатив: те же 3 здания, но требуем 4 вокруг общего центра
        assertFalse(chk(s, "buildings_ring_around_hex", Map.of("count", 4)),
            "4 вокруг центра быть не должно (зданий только 3)");
    }

    // ---- Один гекс: несколько зданий на одном гексе ----
    @Test
    void sameHexPositiveAndNegative() {
        GameState s = fresh();
        clearSeat0(s);
        String hub = hubWith(s, 1);
        putBld(s, BuildingType.BARRACKS, hub, 911);
        putBld(s, BuildingType.FACTORY, hub, 912);
        putBld(s, BuildingType.AIRBASE, hub, 913);
        assertTrue(chk(s, "sp_three_buildings_one_hex_touching", Map.of()),
            "3 здания на ОДНОМ гексе должны ловиться");
        // негатив: раскидать по разным гексам
        clearSeat0(s);
        List<String> nbs = s.field.neighbors(hub);
        putBld(s, BuildingType.BARRACKS, hub, 921);
        putBld(s, BuildingType.FACTORY, nbs.get(0), 922);
        putBld(s, BuildingType.AIRBASE, nbs.get(1), 923);
        assertFalse(chk(s, "sp_three_buildings_one_hex_touching", Map.of()),
            "здания на РАЗНЫХ гексах не должны считаться 'на одном'");
    }

    // ---- Несоседство / расстояние: юнит далеко от своих зданий ----
    @Test
    void distancePositiveAndNegative() {
        GameState s = fresh();
        clearSeat0(s);
        String home = hubWith(s, 1);
        putBld(s, BuildingType.BARRACKS, home, 931);
        // юнит рядом со зданием -> НЕ далеко
        UnitToken near = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 941);
        near.hexId = s.field.neighbors(home).get(0);
        s.player(0).units.add(near);
        assertFalse(chk(s, "unit_far_from_own_buildings", Map.of("distance", 3)),
            "юнит вплотную к зданию не должен считаться далёким (>=3)");
        // юнит на расстоянии: найдём гекс с bfs-дистанцией >=3 от home
        String far = null;
        for (String id : s.field.hexes.keySet()) {
            Integer d = bfs(s, id, home);
            if (d != null && d >= 3) {
                far = id;
                break;
            }
        }
        if (far != null) {
            near.hexId = far;
            assertTrue(chk(s, "unit_far_from_own_buildings", Map.of("distance", 3)),
                "юнит на расстоянии>=3 должен считаться далёким");
        }
    }

    // ---- Общая СТЕНКА: два здания на соседних гексах, занимающие общую границу ----
    @Test
    void sharedWallModelSupported() {
        GameState s = fresh();
        clearSeat0(s);
        // строим СОБСТВЕННУЮ пару гексов, связанную по стороне (детерминированно,
        // не зависим от того, как сценарий заполнил neighborBySide)
        String a = "wallA";
        String b = "wallB";
        int sideA = 0;
        s.field.addHex(new kelium.core.Hex(a));
        s.field.addHex(new kelium.core.Hex(b));
        s.field.link(a, b, sideA);   // A.side0 <-> B.side3
        var ha = s.field.get(a);
        var hb = s.field.get(b);
        BuildingToken bA = putBld(s, BuildingType.BARRACKS, a, 951);
        BuildingToken bB = putBld(s, BuildingType.BARRACKS, b, 952);
        ha.occupySides(bA.uid, List.of(sideA));
        hb.occupySides(bB.uid, List.of((sideA + 3) % 6));
        // МОДЕЛЬ должна позволять определить общую стенку:
        // A занимает сторону, смотрящую на B, И B занимает противоположную.
        boolean aFacesB = ha.sideOwner[sideA] != null && ha.sideOwner[sideA] == bA.uid;
        boolean bFacesA = hb.sideOwner[(sideA + 3) % 6] != null
            && hb.sideOwner[(sideA + 3) % 6] == bB.uid;
        assertTrue(aFacesB && bFacesA,
            "модель сторон поддерживает общую стенку: A и B занимают общую границу");
        // ПОЗИТИВ предиката: оба здания имеют общую стенку
        assertTrue(chk(s, "buildings_share_wall", Map.of("count", 2)),
            "два здания с общей стенкой должны ловиться предикатом");

        // НЕГАТИВ: те же соседние гексы, но стороны НЕ по общей грани
        // (A занимает другую свою сторону, B — другую свою) → общей стенки нет
        clearSeat0(s);
        var ha2 = s.field.get(a);
        var hb2 = s.field.get(b);
        for (int i = 0; i < 6; i++) {
            ha2.sideOwner[i] = null;
            hb2.sideOwner[i] = null;
        }
        BuildingToken cA = putBld(s, BuildingType.BARRACKS, a, 961);
        BuildingToken cB = putBld(s, BuildingType.BARRACKS, b, 962);
        int otherA = (sideA + 1) % 6;                 // не грань к B
        int otherB = ((sideA + 3) % 6 + 1) % 6;        // не грань к A
        ha2.occupySides(cA.uid, List.of(otherA));
        hb2.occupySides(cB.uid, List.of(otherB));
        assertFalse(chk(s, "buildings_share_wall", Map.of("count", 1)),
            "здания на соседних гексах, но занявшие НЕ общую грань, не должны считаться примыкающими стенкой");
    }

    // ---- Общая стенка ВНУТРИ одного гекса (соседние ячейки) ----
    @Test
    void sharedWallSameHex() {
        GameState s = fresh();
        clearSeat0(s);
        String hub = hubWith(s, 1);
        var h = s.field.get(hub);
        for (int i = 0; i < 6; i++) {
            h.sideOwner[i] = null;
        }
        // ПОЗИТИВ: два здания на ОДНОМ гексе, СМЕЖНЫЕ стороны (0 и 1) → касаются
        BuildingToken b1 = putBld(s, BuildingType.BARRACKS, hub, 971);
        BuildingToken b2 = putBld(s, BuildingType.FACTORY, hub, 972);
        h.occupySides(b1.uid, List.of(0));
        h.occupySides(b2.uid, List.of(1));
        assertTrue(chk(s, "buildings_share_wall", Map.of("count", 2)),
            "два здания на одном гексе на СМЕЖНЫХ сторонах должны касаться");

        // НЕГАТИВ: те же два здания, но стороны НЕсмежные (0 и 3) → НЕ касаются
        for (int i = 0; i < 6; i++) {
            h.sideOwner[i] = null;
        }
        h.occupySides(b1.uid, List.of(0));
        h.occupySides(b2.uid, List.of(3));
        assertFalse(chk(s, "buildings_share_wall", Map.of("count", 1)),
            "здания на одном гексе на НЕсмежных сторонах (0 и 3) не касаются");
    }

    private Integer bfs(GameState s, String from, String target) {
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        seen.put(from, 0);
        java.util.Deque<String> q = new java.util.ArrayDeque<>();
        q.add(from);
        while (!q.isEmpty()) {
            String x = q.poll();
            if (x.equals(target)) {
                return seen.get(x);
            }
            for (String nb : s.field.neighbors(x)) {
                if (!seen.containsKey(nb)) {
                    seen.put(nb, seen.get(x) + 1);
                    q.add(nb);
                }
            }
        }
        return null;
    }
}
