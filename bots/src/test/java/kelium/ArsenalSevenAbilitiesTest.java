package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.Hex;
import kelium.core.Resource;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Placement;
import kelium.engine.Scoring;
import kelium.engine.Setup;
import kelium.engine.ability.Abilities;
import kelium.engine.ability.Hook;
import kelium.engine.ability.OptionSource;
import kelium.engine.ability.RuleQuery;

/**
 * НОВЫЕ И ПЕРЕПИСАННЫЕ НИЗЫ АРСЕНАЛА 7.0.0 И СУПЕР-АРСЕНАЛА 3.0.0 — работают
 * ли они на собранном вручную столе (как {@code ArsenalSixAbilitiesTest}).
 */
class ArsenalSevenAbilitiesTest {

    /** Игрок, который всегда берёт первый непустой вариант. */
    private static final class Решительный extends Agent {
        Решительный() {
            super(0, "решительный");
        }

        @Override public Choice choose(GameState state, List<Choice> options,
                                       Map<String, Object> context) {
            for (Choice c : options) {
                if (c.payload() != null) {
                    return c;
                }
            }
            return options.get(options.size() - 1);
        }
    }

    private static GameState стол(long seed) {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, seed, null, null));
    }

    private static Choice вариант(GameState s, int seat, String id) {
        for (Choice c : Abilities.options(s, seat, OptionSource.Slot.SPEC)) {
            if (String.valueOf(c.kind()).equals("ability:" + id)) {
                return c;
            }
        }
        return null;
    }

    @Test
    void звёздаУстановленнойКартыИдётВОчки() {
        GameState s = стол(71L);
        assertNull(Scoring.scorePlayer(s, 0).get("arsenal_stars"), "без карты звёзд нет");
        s.player(0).arsenalInstalled.add("a7_22");   // «Реактивные ранцы», звезда
        s.player(0).arsenalInstalled.add("a7_21");   // «Воздушная наводка», без звезды
        assertEquals(1, Scoring.scorePlayer(s, 0).get("arsenal_stars"));
    }

    @Test
    void оперативныйОтделДаётВтороеСпецТолькоСБезопасностью() {
        GameState s = стол(72L);
        s.player(0).arsenalInstalled.add("a7_11");
        s.player(0).orderPlayed.clear();
        s.player(0).orderPlayed.add("red_place");
        assertEquals(1, RuleQuery.of(s, 0, Hook.ORDER_SPEC_COUNT).base(1).ask(),
            "обычный приказ — одно спец-действие");
        s.player(0).orderPlayed.add("security_1");
        assertEquals(2, RuleQuery.of(s, 0, Hook.ORDER_SPEC_COUNT).base(1).ask(),
            "сыграна Безопасность — два");
    }

    @Test
    void келемиевыйДождьСтоитДваКелемияИУдаляетКарту() {
        GameState s = стол(73L);
        var p = s.player(0);
        p.arsenalInstalled.add("a7_01");
        p.resources.add(Resource.KELIUM, 2);
        int было = p.resources.get(Resource.KELIUM);
        Choice ход = вариант(s, 0, "spec_kelium_rain_burn");
        assertNotNull(ход, "СПЕЦ «Келемиевый дождь» предложен");
        assertTrue(Abilities.perform(s, 0, ход, new Решительный()));
        assertEquals(было - 2, p.resources.get(Resource.KELIUM), "плата — два келемия");
        assertFalse(p.arsenalInstalled.contains("a7_01"), "«Затем удали эту карту»");
    }

    @Test
    void геологоразведкаПереноситНаСоседнийГексЗаМонету() {
        GameState s = стол(74L);
        var p = s.player(0);
        p.arsenalInstalled.add("a7_07");
        // ставим свой добытчик из запаса рядом с ЦУ
        BuildingToken цу = null;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                цу = b;
            }
        }
        assertNotNull(цу);
        BuildingToken добытчик = s.tokenStats.makeBuilding(BuildingType.MINER, 0,
            Placement.nextUid(s), 1);
        p.buildings.add(добытчик);
        Hex дом = s.field.get(цу.hexId);
        int ячейка = дом.freeSideIndices().get(0);
        добытчик.hexId = дом.id;
        дом.sideOwner[ячейка] = добытчик.uid;

        p.resources.pay(Resource.COIN, p.resources.coin());
        assertNull(вариант(s, 0, "spec_move_economy_building_adjacent"), "без монеты — нет");
        p.resources.add(Resource.COIN, 1);
        Choice ход = вариант(s, 0, "spec_move_economy_building_adjacent");
        assertNotNull(ход, "с монетой — предложено");
        assertTrue(Abilities.perform(s, 0, ход, new Решительный()));
        assertEquals(0, p.resources.get(Resource.COIN), "плата — монета");
        assertTrue(s.field.neighborsView(дом.id).contains(добытчик.hexId),
            "добытчик на соседнем гексе");
    }

    @Test
    void суперВышкаБьётСоседейЗаОдинБоеприпас() {
        GameState s = стол(75L);
        // Движок партии заводит бой (state.combat) — урон кладёт он.
        List<Agent> игроки = List.of(new Решительный(), new Решительный(),
            new Решительный(), new Решительный());
        s.combat = new kelium.engine.CombatResolver(s, ev -> { }).bindAgents(игроки);
        var p = s.player(0);
        UnitToken вышка = s.tokenStats.makeUnit(UnitType.TOWER, 0, Placement.nextUid(s));
        вышка.superUnit = true;
        вышка.superCardId = "sa3_04";
        p.superArsenalCards.add("sa3_04");
        p.units.add(вышка);
        // гекс вышки — гекс своего ЦУ, враг — на соседнем
        String гекс = null;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                гекс = b.hexId;
            }
        }
        assertNotNull(гекс);
        вышка.hexId = гекс;
        UnitToken враг = s.player(1).unitsOnField().isEmpty() ? null
            : s.player(1).unitsOnField().get(0);
        if (враг == null) {
            враг = s.tokenStats.makeUnit(UnitType.INFANTRY, 1, Placement.nextUid(s));
            s.player(1).units.add(враг);
        }
        String сосед = s.field.neighborsView(гекс).get(0);
        враг.hexId = сосед;
        p.resources.add(Resource.AMMO, 1);
        int было = p.resources.get(Resource.AMMO);

        Choice ход = вариант(s, 0, "super_tower_ring");
        assertNotNull(ход, "залп предложен");
        assertTrue(Abilities.perform(s, 0, ход, new Решительный()));
        assertEquals(было - 1, p.resources.get(Resource.AMMO), "один боеприпас за весь залп");
        assertTrue(враг.damage > 0 || враг.hexId == null || !враг.alive(),
            "враг на соседнем гексе получил урон");
    }
}
