package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.PlayerState;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.CellGraph;
import kelium.engine.DeployPattern;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.Symbols;

/**
 * СУПЕР ЗАДАНИЯ 2.0 (правила 1.6.0, решение дизайнера 12.08.2026):
 * выбор карты из двух, рисунок из конкретных объектов с НЕПРЕРЫВНОЙ связью по
 * ЯЧЕЙКАМ, символы под планшетом и проверка СПЕЦ-действием.
 */
class SuperObjectives2Test {

    private static final String RULES = GameConfig.DEFAULT_RULESET;

    private static GameState game(int players, long seed) {
        return Setup.buildGame(GameConfig.buildCached(RULES, players, seed, null, null));
    }

    // ==================== раздача и выбор ====================

    @Test
    void dealsTwoCardsAndPlayerKeepsOne() {
        GameState s = game(4, 11L);
        for (PlayerState p : s.players) {
            assertEquals(2, p.superObjectiveOffer.size(),
                "по правилам 2.0 раздаётся две карты супер задания");
            assertTrue(p.superObjective == null,
                "до выбора карта не назначена — выбирает игрок, а не подготовка");
        }
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(Bots.create("balanced", seat, new Random(seed(seat)), 4));
        }
        GameEngine.playGame(s, agents, null);
        for (PlayerState p : s.players) {
            assertTrue(p.superObjective != null, "после начала партии выбор сделан");
            assertTrue(p.superObjectiveOffer.contains(p.superObjective),
                "выбрана карта ИЗ предложенных, а не любая");
        }
    }

    private static long seed(int seat) {
        return 31L * (seat + 1);
    }

    // ==================== связность по ячейкам ====================

    @Test
    void oppositeCellsAreNotConnectedButAircraftBridgesThem() {
        GameState s = game(4, 12L);
        Hex h = null;
        for (Hex x : s.field.hexes.values()) {
            if (!x.hasNeutral() && !x.hasSpawnTile() && x.freeSectors() == 6) {
                h = x;
                break;
            }
        }
        assertTrue(h != null, "нужен пустой гекс");

        PlayerState me = s.player(0);
        UnitToken a = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 8001);
        a.hexId = h.id;
        me.units.add(a);
        h.occupySides(a.uid, List.of(0));

        UnitToken b = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 8002);
        b.hexId = h.id;
        me.units.add(b);
        h.occupySides(b.uid, List.of(3));           // РОВНО НАПРОТИВ

        assertFalse(CellGraph.linked(s, a, b),
            "жетоны напротив друг друга на одном гексе не связаны");

        UnitToken air = s.tokenStats.makeUnit(UnitType.AIRCRAFT, 0, 8003);
        air.hexId = h.id;
        me.units.add(air);
        assertTrue(CellGraph.linked(s, a, b),
            "авиация в центре гекса связывает всю его наземку");
    }

    @Test
    void neighbourCellsAndSharedEdgeAreConnected() {
        GameState s = game(4, 13L);
        Hex h = null;
        int side = -1;
        for (Hex x : s.field.hexes.values()) {
            if (x.hasNeutral() || x.hasSpawnTile() || x.freeSectors() < 6) {
                continue;
            }
            for (int i = 0; i < 6; i++) {
                Hex n = x.neighborBySide[i] == null ? null : s.field.get(x.neighborBySide[i]);
                if (n != null && !n.hasNeutral() && !n.hasSpawnTile() && n.freeSectors() == 6) {
                    h = x;
                    side = i;
                    break;
                }
            }
            if (h != null) {
                break;
            }
        }
        assertTrue(h != null, "нужны два пустых соседних гекса");
        PlayerState me = s.player(0);

        // рядом по кругу внутри гекса
        UnitToken a = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 8101);
        a.hexId = h.id;
        me.units.add(a);
        h.occupySides(a.uid, List.of(side));
        UnitToken b = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 8102);
        b.hexId = h.id;
        me.units.add(b);
        h.occupySides(b.uid, List.of((side + 1) % 6));
        assertTrue(CellGraph.linked(s, a, b), "соседние по кругу ячейки связаны");

        // общее ребро двух гексов
        Hex n = s.field.get(h.neighborBySide[side]);
        UnitToken c = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, 8103);
        c.hexId = n.id;
        me.units.add(c);
        n.occupySides(c.uid, List.of((side + 3) % 6));
        assertTrue(CellGraph.linked(s, a, c),
            "ячейки по общему ребру двух гексов связаны");
    }

    // ==================== рисунок развёртывания ====================

    @Test
    void deployNeedsAllObjectsInOneContinuousLink() {
        GameState s = game(4, 14L);
        Hex h = null;
        for (Hex x : s.field.hexes.values()) {
            if (!x.hasNeutral() && !x.hasSpawnTile() && x.freeSectors() == 6) {
                h = x;
                break;
            }
        }
        assertTrue(h != null, "нужен пустой гекс");
        PlayerState me = s.player(0);

        BuildingToken plant = s.tokenStats.makeBuilding(BuildingType.POWER_PLANT, 0, 8201, 1);
        plant.hexId = h.id;
        me.buildings.add(plant);
        h.occupySides(plant.uid, List.of(0));

        UnitToken veh = s.tokenStats.makeUnit(UnitType.VEHICLE, 0, 8202);
        veh.hexId = h.id;
        me.units.add(veh);
        h.occupySides(veh.uid, List.of(3, 4));      // НЕ рядом со станцией

        Map<String, Object> deploy = new HashMap<>();
        deploy.put("relation", "connected");
        deploy.put("objects", List.of(
            Map.of("what", "building:power_plant", "count", 1),
            Map.of("what", "unit:vehicle", "count", 1)));

        assertFalse(DeployPattern.satisfied(s, 0, deploy),
            "разорванная связка рисунком не считается");

        // сдвигаем технику вплотную: ячейки 1-2 примыкают к ячейке 0
        h.freeSidesByToken(veh.uid);
        h.occupySides(veh.uid, List.of(1, 2));
        assertTrue(DeployPattern.satisfied(s, 0, deploy),
            "непрерывная связка станции и техники — рисунок выполнен");
    }

    // ==================== символы ВКЛЮЧЕНЫ ====================

    /**
     * Сет-коллекшн символов ВКЛЮЧЁН решением дизайнера 13.08.2026 (версия правил
     * 1.7.0, отмена парковки из бэклога I1). Проверяем именно это: разметка
     * подключена, требование символов поднято, и вторая часть супер задания
     * зависит не только от рисунка на поле, но и от вскрытых символов.
     */
    @Test
    void symbolSetCollectionIsOnAndGatesTheObjective() {
        GameState s = game(4, 15L);
        Symbols.Marking m = Symbols.of(s);
        assertTrue(m.ofArsenal("a01") != null,
            "разметка символов подключена версией контента symbols 1.0.0");
        assertTrue(Boolean.TRUE.equals(kelium.dataio.Ctx.rules(s)
                .get("super_objectives.require_symbols", Boolean.FALSE)),
            "требование символов включено");

        PlayerState p = s.player(0);
        p.superObjective = "prizma";
        assertFalse(Symbols.satisfied(s, p, Symbols.required(s, "prizma"), 0),
            "без вскрытых символов задание не проходит");
    }

    // ==================== партия целиком ====================

    @Test
    void gamesOnNewRulesetFinishAndUseTheNewMechanics() {
        int tucks = 0;
        int reveals = 0;
        int picks = 0;
        for (long seed = 40; seed < 46; seed++) {
            GameState s = game(4, seed);
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < 4; seat++) {
                agents.add(Bots.create(Bots.CHARACTERS.get(seat % Bots.CHARACTERS.size()),
                    seat, new Random(seed * 31 + seat), 4));
            }
            int[] counts = new int[3];
            Map<String, Object> res = GameEngine.playGame(s, agents, ev -> {
                switch (String.valueOf(ev.get("type"))) {
                    case "tuck" -> counts[0]++;
                    case "symbol_reveal" -> counts[1]++;
                    case "super_pick" -> counts[2]++;
                    default -> { }
                }
            });
            assertTrue(res.containsKey("winner"), "партия обязана доиграть (сид " + seed + ")");
            tucks += counts[0];
            reveals += counts[1];
            picks += counts[2];
        }
        assertTrue(picks >= 6 * 4 - 2, "выбор супер задания делают все игроки: " + picks);
        // модуль символов включён (1.7.0) — карты уходят под планшет и вскрываются
        assertTrue(tucks > 0, "модуль символов включён: карты уходят под планшет, tucks=" + tucks);
        assertTrue(reveals > 0, "модуль символов включён: символы вскрываются, reveals=" + reveals);
        assertTrue(reveals <= tucks, "вскрыть можно только подложенное: "
            + reveals + " вскрытий при " + tucks + " подкладываниях");
    }
}
