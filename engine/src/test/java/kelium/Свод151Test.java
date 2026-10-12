package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.engine.TurnContext;
import kelium.support.Fix;

/**
 * СВОД 1.51.0 (решения Влада 12.10.2026): асимметрия жетонов и планшетов,
 * здания без энергии, стройка без срабатывания, келемий в Науке не больше
 * трофеев.
 */
class Свод151Test {

    private static final String СВОД = "1.51.0";

    /** Место за столом с планшетом этого цвета. */
    private static PlayerState цвет(GameState s, String side) {
        for (PlayerState p : s.players) {
            if (side.equals(p.board.troop.side)) {
                return p;
            }
        }
        throw new AssertionError("нет цвета " + side);
    }

    @Test
    void добытчикиСтанцииИХранилищеПоЦвету() {
        GameState s = Fix.game(СВОД, 4, 3L);
        var t = s.tokenStats;
        // печать жетонов зданий 12.10.2026
        assertEquals(2, t.minerYield(2, цвет(s, "blue").seat), "синий добытчик ур.2 даёт 2");
        assertEquals(1, t.minerYield(2, цвет(s, "red").seat), "красный добытчик ур.2 даёт 1");
        assertEquals(1, t.minerYield(3, цвет(s, "green").seat), "зелёный добытчик ур.3 даёт 1");
        assertEquals(3, t.plantEnergyGives(3, цвет(s, "blue").seat), "синяя станция ур.3 даёт 3");
        assertEquals(2, t.plantEnergyGives(4, цвет(s, "red").seat), "красная станция ур.4 даёт 2");
        assertEquals(1, t.plantEnergyGives(2, цвет(s, "yellow").seat), "жёлтая станция ур.2 даёт 1");
        assertEquals(2, t.buildingHp(BuildingType.POWER_PLANT, 2, цвет(s, "red").seat));
        assertEquals(2, t.buildingEnergySlots(BuildingType.MINER, 2, цвет(s, "blue").seat));
        // планшеты хранилища
        assertEquals("UU", цвет(s, "red").board.storage.minerCells(2));
        assertEquals("U", цвет(s, "blue").board.storage.plantCells(3));
        assertEquals("UU", цвет(s, "yellow").board.storage.plantCells(2));
        // планшеты войск
        assertEquals(1, цвет(s, "blue").board.troop.printedOutput(BuildingType.COMMAND_CENTER, "units"));
        assertEquals(kelium.core.UnitType.INFANTRY.code,
            String.valueOf(цвет(s, "red").board.troop.raw.get("attacks") instanceof Map<?, ?> a ? a.get("aircraft") : null));
    }

    /** Агент сборки: у завода записывает варианты и берёт вариант {@code брать}, прочие пропускает. */
    private static Agent сборщик(int seat, List<String> варианты, String брать) {
        return new Agent(seat, "сборщик") {
            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                boolean этоЗавод = "factory".equals(ctx.get("building_type"));
                for (Choice c : options) {
                    if (этоЗавод && c.payload() instanceof Map<?, ?> m) {
                        варианты.add(String.valueOf(m.get("kind")));
                    }
                }
                for (Choice c : options) {
                    if (этоЗавод ? c.payload() instanceof Map<?, ?> m && брать.equals(m.get("kind"))
                            : "pass".equals(c.kind())) {
                        return c;
                    }
                }
                return options.get(options.size() - 1);
            }
        };
    }

    private static BuildingToken завод(GameState s, PlayerState p) {
        String гекс = null;
        for (BuildingToken b : p.buildingsOnField()) {
            гекс = b.hexId;
        }
        assertTrue(гекс != null, "у игрока есть здание на поле");
        BuildingToken завод = s.tokenStats.makeBuilding(BuildingType.FACTORY, p.seat, 9300, null);
        завод.hexId = гекс;
        p.buildings.add(завод);
        return завод;
    }

    @Test
    void военноеЗданиеБезЭнергииДаётОднуШтуку() {
        GameState s = Fix.game(СВОД, 4, 5L);
        PlayerState p = s.player(0);
        завод(s, p);
        List<String> варианты = new ArrayList<>();
        int было = p.resources.ammo();
        Actions.create("assembly", s).perform(p, new TurnContext(p.seat, 1), сборщик(p.seat, варианты, "ammo"));
        assertEquals(List.of("unit", "ammo"), варианты, "без энергии — 1 войско или 1 боеприпас");
        assertEquals(было + 1, p.resources.ammo(), "ровно 1 боеприпас");
    }

    @Test
    void запитанноеЗданиеДаётВсёСразу() {
        GameState s = Fix.game(СВОД, 4, 5L);
        PlayerState p = s.player(0);
        завод(s, p);
        List<String> варианты = new ArrayList<>();
        int боеприпасов = p.resources.ammo();
        int войск = p.units.size();
        TurnContext ctx = new TurnContext(p.seat, 1);
        ctx.allPowered = true;
        Actions.create("assembly", s).perform(p, ctx, сборщик(p.seat, варианты, "both"));
        assertEquals(List.of("both"), варианты, "запитанное — только «всё сразу»");
        int печатьБП = p.board.troop.printedOutput(BuildingType.FACTORY, "ammo");
        int печатьВойск = p.board.troop.printedOutput(BuildingType.FACTORY, "units");
        assertEquals(боеприпасов + печатьБП, p.resources.ammo(), "боеприпасы по печати");
        assertEquals(войск + печатьВойск, p.units.size(), "и войска по печати");
    }

    @Test
    void наукаКелемийНеБольшеТрофеев() {
        GameState s = Fix.game(СВОД, 4, 9L);
        PlayerState p = s.player(0);
        p.resources.add(Resource.KELIUM, 6);
        p.resources.add(Resource.TROPHY, 3);
        List<Integer> келемия = new ArrayList<>();
        Agent агент = new Agent(p.seat, "учёный") {
            @Override
            public Choice choose(GameState st, List<Choice> options, Map<String, Object> ctx) {
                if ("sci_pay_kelium".equals(ctx.get("kind"))) {
                    int цена = ((Number) ctx.get("cost")).intValue();
                    for (Choice c : options) {
                        int k = (Integer) c.payload();
                        келемия.add(k);
                        assertTrue(k <= цена - k, "келемия " + k + " при цене " + цена);
                    }
                    return options.get(options.size() - 1);
                }
                for (Choice c : options) {
                    if (c.payload() != null && !"pass".equals(c.kind())) {
                        return c;
                    }
                }
                return options.get(0);
            }
        };
        Actions.create("science", s).perform(p, new TurnContext(p.seat, 1), агент);
        assertFalse(келемия.isEmpty(), "игроку предложили платить келемием");
    }

    @Test
    void партииДоигрываются() {
        for (long сид = 1; сид <= 3; сид++) {
            GameState s = Setup.buildGame(GameConfig.buildCached(СВОД, 4, сид, null, null));
            List<Agent> agents = new ArrayList<>();
            for (int seat = 0; seat < 4; seat++) {
                agents.add(new Fix.FirstChoiceAgent(seat));
            }
            new GameEngine(s, agents, ev -> { }).run();
            assertTrue(s.round > 1, "сид " + сид + ": партия идёт по раундам");
        }
    }

    @Test
    void стартовоеВойскоРодаСМодуляБлокировки() {
        GameState s = Setup.buildGame(GameConfig.buildCached(СВОД, 4, 11L, null, null));
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            agents.add(new Fix.FirstChoiceAgent(seat));
        }
        List<Map<String, Object>> выставлено = new ArrayList<>();
        new GameEngine(s, agents, ev -> {
            if ("start_unit".equals(ev.get("type"))) {
                выставлено.add(ev);
            }
        }).run();
        assertEquals(4, выставлено.size(), "у каждого из четырёх — стартовый жетон: " + выставлено);
        java.util.Set<Object> рода = new java.util.HashSet<>();
        for (Map<String, Object> ev : выставлено) {
            рода.add(ev.get("unit"));
        }
        assertEquals(4, рода.size(), "модули блокировки разных родов — и жетоны разных родов");
    }

    @Test
    void наградаЗаданияДаётСуперЗадание() {
        GameState s = Fix.game(СВОД, 4, 21L);
        PlayerState p = s.player(0);
        Setup.refillSuperDisplay(s, kelium.dataio.Ctx.rules(s));
        int было = p.superObjectives.size();
        int наСтоле = s.superDisplay.size();
        assertTrue(наСтоле > 0, "супер-задания лежат открытыми на столе");
        Map<String, Object> итог = kelium.engine.Effects.apply("take_super_objective", s, p.seat, Map.of());
        assertEquals(1, итог.get("super_objective"));
        assertEquals(было + 1, p.superObjectives.size(), "карта пришла игроку");
        assertEquals(наСтоле, s.superDisplay.size(), "на её место выложена новая");
    }

    @Test
    void большихТайловМеньшеИгроковИКелемияБольше() {
        for (int n = 2; n <= 4; n++) {
            for (long сид = 1; сид <= 5; сид++) {
                GameState s = Setup.buildGame(GameConfig.buildCached(СВОД, n, сид, null, null));
                int больших = 0;
                for (kelium.core.Hex h : s.field.hexes.values()) {
                    if (h.spawnTile == null) {
                        continue;
                    }
                    if (h.spawnTile.isStart) {
                        assertTrue(h.spawnTile.kelium >= 4, "стартовый тайл: 3 + 1 келемия");
                    } else {
                        больших++;
                    }
                }
                assertTrue(больших <= n - 1, n + " игрока, сид " + сид + ": больших тайлов " + больших);
            }
        }
    }
}
