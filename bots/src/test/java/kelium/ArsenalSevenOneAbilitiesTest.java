package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import kelium.core.Target;
import kelium.core.UnitToken;
import kelium.core.UnitType;
import kelium.dataio.GameConfig;
import kelium.engine.Actions;
import kelium.engine.CombatResolver;
import kelium.engine.GameEngine;
import kelium.engine.Objectives;
import kelium.engine.Passives;
import kelium.engine.Placement;
import kelium.engine.Setup;
import kelium.engine.TurnContext;
import kelium.engine.ability.Abilities;
import kelium.engine.ability.OptionSource;

/**
 * НИЗЫ АРСЕНАЛА 7.1.0 И СПОСОБНОСТИ СУПЕР-АРСЕНАЛА 4.0.0 (карты 25.09.2026) —
 * на собранном вручную столе, как {@code ArsenalSevenAbilitiesTest}.
 *
 * <p>Каждая способность проверяется в обе стороны: с картой срабатывает, без
 * карты — нет (иначе «работает» значило бы лишь «не падает»).
 */
class ArsenalSevenOneAbilitiesTest {

    /** Игрок, который от всего отказывается, если может. */
    private static final class Пасующий extends Agent {
        Пасующий(int seat) {
            super(seat, "пасующий");
        }

        @Override public Choice choose(GameState state, List<Choice> options,
                                       Map<String, Object> context) {
            for (Choice c : options) {
                if (c.payload() == null) {
                    return c;
                }
            }
            return options.get(0);
        }
    }

    private static GameState стол(long seed) {
        return Setup.buildGame(
            GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, seed, null, null));
    }

    private static String гексЦУ(GameState s, int seat) {
        for (BuildingToken b : s.player(seat).buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                return b.hexId;
            }
        }
        throw new AssertionError("нет ЦУ у места " + seat);
    }

    // ==================================================================
    //  НАБОР НА ПОДГОТОВКЕ
    // ==================================================================

    @Test
    void начальныхПоОднойНеРозданныеВКолодуНеИдут() {
        GameState s = стол(81L);
        List<String> начальные = new ArrayList<>();
        for (Map<String, Object> e : kelium.dataio.Ctx.cards(s, "arsenal").entries) {
            if ("starting".equals(e.get("kind"))) {
                начальные.add(String.valueOf(e.get("id")));
            }
        }
        assertEquals(7, начальные.size());
        int розданных = 0;
        for (PlayerState p : s.players) {
            for (String c : p.arsenalHand) {
                if (начальные.contains(c)) {
                    розданных++;
                }
            }
        }
        assertEquals(4, розданных, "по одной начальной каждому");
        // колоду вытягиваем до конца — ни одной начальной в ней нет
        var колода = s.decks.get("arsenal");
        String c;
        while ((c = колода.draw(s.rng)) != null) {
            assertFalse(начальные.contains(c), "начальная " + c + " в колоде арсенала");
        }
    }

    // ==================================================================
    //  НАЧАЛЬНЫЙ АРСЕНАЛ 7.1.0
    // ==================================================================

    @Test
    void переформированиеСтоитДвеМонеты() {
        GameState s = стол(82L);
        PlayerState p = s.player(0);
        p.arsenalInstalled.add("bs7_6");
        UnitToken пехота = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, Placement.nextUid(s),
            p.unitsOfKind(UnitType.INFANTRY));
        p.units.add(пехота);
        пехота.hexId = гексЦУ(s, 0);
        p.resources.pay(Resource.COIN, p.resources.coin());
        p.resources.add(Resource.COIN, 1);
        assertTrue(варианты(s, 0, "spec_swap_ground_unit").isEmpty(), "одной монеты мало");
        p.resources.add(Resource.COIN, 1);
        assertFalse(варианты(s, 0, "spec_swap_ground_unit").isEmpty(), "две монеты — предложено");
    }

    @Test
    void сдачаТарыДаётМонетуЗаВскрытыйКонтейнер() {
        for (boolean сКартой : new boolean[]{false, true}) {
            GameState s = стол(83L);
            PlayerState p = s.player(0);
            p.arsenalHand.clear();
            p.arsenalInstalled.clear();
            if (сКартой) {
                p.arsenalInstalled.add("bs7_7");
            }
            p.containers = 1;
            List<Map<String, Object>> события = new ArrayList<>();
            GameEngine e = new GameEngine(s, агенты(), события::add);
            int монетДо = p.resources.coin();
            вскрыть(e, p);
            Map<String, Object> контейнер = null;
            for (Map<String, Object> ev : события) {
                if ("container".equals(ev.get("type"))) {
                    контейнер = ev;
                }
            }
            assertNotNull(контейнер, "контейнер не вскрылся");
            Map<?, ?> got = (Map<?, ?>) контейнер.get("got");
            int монетНаКарте = got.get("coin") instanceof Number n ? n.intValue() : 0;
            assertEquals(монетДо + монетНаКарте + (сКартой ? 1 : 0), p.resources.coin(),
                сКартой ? "с «Сдачей тары» — монета сверх контейнера"
                        : "без карты — ровно то, что на контейнере");
        }
    }

    /** Вскрыть один контейнер закрытым методом движка (он и есть правило). */
    private static void вскрыть(GameEngine e, PlayerState p) {
        try {
            var m = GameEngine.class.getDeclaredMethod("offerOpenContainer", PlayerState.class);
            m.setAccessible(true);
            m.invoke(e, p);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    // ==================================================================
    //  ОБЫЧНЫЙ №33
    // ==================================================================

    @Test
    void боевоеДовольствиеДаётБоеприпасЗаВыполненноеЗадание() {
        for (boolean сКартой : new boolean[]{false, true}) {
            GameState s = стол(84L);
            PlayerState p = s.player(0);
            if (сКартой) {
                p.arsenalInstalled.add("a7_33");
            }
            p.resources.pay(Resource.AMMO, p.resources.ammo());
            String задание = s.decks.get("objectives").draw(s.rng);
            p.objectiveHand.add(задание);
            Map<String, Object> выдано = Objectives.playObjective(s, 0, s.journal, задание, ev -> { });
            if (сКартой) {
                assertEquals(1, выдано.get("arsenal_bonus_ammo"), "боеприпас за задание");
            } else {
                assertFalse(выдано.containsKey("arsenal_bonus_ammo"), "без карты — нет");
            }
        }
    }

    // ==================================================================
    //  СУПЕР-АРСЕНАЛ 4.0.0
    // ==================================================================

    @Test
    void штабнаяДирективаИгнорируетСрезИДаётСпецЗаНижний() {
        for (boolean сКартой : new boolean[]{false, true}) {
            GameState s = стол(85L);
            if (сКартой) {
                s.player(0).superArsenalCards.add("sa4_05");
            }
            List<Map<String, Object>> события = new ArrayList<>();
            GameEngine e = new GameEngine(s, агенты(), события::add);
            // совпал верхний приказ, нижний открыт
            e.simulateTurn(0, "red_place", true, true);
            Map<String, Object> приказ = последнее(события, "turn_orders");
            assertNotNull(приказ);
            assertEquals(сКартой ? 2 : 1, ((Number) приказ.get("top_allowed")).intValue(),
                сКартой ? "с директивой — оба действия" : "без неё совпадение режет до одного");
            assertEquals(сКартой, последнее(события, "spec_bonus") != null,
                "спец-действие за открытый нижний приказ — только с картой");
        }
    }

    @Test
    void директиваБезОткрытогоНижнегоСпецНеДаёт() {
        GameState s = стол(86L);
        s.player(0).superArsenalCards.add("sa4_05");
        List<Map<String, Object>> события = new ArrayList<>();
        GameEngine e = new GameEngine(s, агенты(), события::add);
        e.simulateTurn(0, "red_place", false, false);
        assertEquals(null, последнее(события, "spec_bonus"));
    }

    @Test
    void келемиевыйРудникПлатитЗаКаждуюДобычу() {
        for (boolean сКартой : new boolean[]{false, true}) {
            GameState s = стол(87L);
            PlayerState p = s.player(0);
            if (сКартой) {
                p.superArsenalCards.add("sa4_06");
            }
            p.resources.pay(Resource.KELIUM, p.resources.kelium());
            Actions.create("mining", s).perform(p, new TurnContext(0, 0), new Пасующий(0));
            assertEquals(сКартой ? 1 : 0, p.resources.kelium(),
                "Добыча без добытчиков: келемий только от рудника");
            // срабатывание добытчика при постройке — не действие «Добыча»
            TurnContext приПостройке = new TurnContext(0, 0);
            приПостройке.толькоЗдание = 999_999;
            Actions.create("mining", s).perform(p, приПостройке, new Пасующий(0));
            assertEquals(сКартой ? 1 : 0, p.resources.kelium(), "при постройке рудник молчит");
        }
    }

    @Test
    void оружейныйКонвейерПлатитЗаКаждоеСнаряжение() {
        for (boolean сКартой : new boolean[]{false, true}) {
            GameState s = стол(88L);
            PlayerState p = s.player(0);
            if (сКартой) {
                p.superArsenalCards.add("sa4_07");
            }
            p.resources.pay(Resource.AMMO, p.resources.ammo());
            // все пасуют: здания ничего не делают — боеприпас только от карты
            Actions.create("assembly", s).perform(p, new TurnContext(0, 0), new Пасующий(0));
            assertEquals(сКартой ? 1 : 0, p.resources.ammo());
        }
    }

    @Test
    void бронированныеЦехаПрибавляютПрочностьСвоимЗданиям() {
        GameState s = стол(89L);
        BuildingToken своё = s.player(0).buildingsOnField().get(0);
        BuildingToken чужое = s.player(1).buildingsOnField().get(0);
        int своёБыло = Passives.effectiveHp(s, своё);
        int чужоеБыло = Passives.effectiveHp(s, чужое);
        s.player(0).superArsenalCards.add("sa4_08");
        assertEquals(своёБыло + 1, Passives.effectiveHp(s, своё), "своё здание +1");
        assertEquals(чужоеБыло, Passives.effectiveHp(s, чужое), "чужое — как было");
    }

    @Test
    void баллистическийРасчётУниверсальнаяЗаОдинБоеприпас() {
        for (boolean сКартой : new boolean[]{false, true}) {
            GameState s = стол(90L);
            PlayerState p = s.player(0);
            // очищаем поле от войск, чтобы на соседнем гексе был ровно наш враг
            for (PlayerState o : s.players) {
                for (UnitToken u : new ArrayList<>(o.unitsOnField())) {
                    u.setHexId(null);
                }
            }
            String дом = гексЦУ(s, 0);
            // атакует пехота; цель — род, который НЕ её специальная цель
            UnitToken стрелок = s.tokenStats.makeUnit(UnitType.INFANTRY, 0, Placement.nextUid(s),
                p.unitsOfKind(UnitType.INFANTRY));
            p.units.add(стрелок);
            стрелок.hexId = дом;
            Target спец = p.board.troop.specializedTarget(UnitType.INFANTRY);
            UnitType родЦели = спец == Target.INFANTRY ? UnitType.VEHICLE : UnitType.INFANTRY;
            String сосед = null;
            for (String nb : s.field.neighborsView(дом)) {
                if (kelium.engine.Movement.passable(s, nb) && !s.field.get(nb).hasSpawnTile()
                        && s.field.get(nb).freeSideIndices().size() >= 2) {
                    сосед = nb;
                    break;
                }
            }
            assertNotNull(сосед, "нет свободного соседнего гекса");
            UnitToken враг = s.tokenStats.makeUnit(родЦели, 1, Placement.nextUid(s),
                s.player(1).unitsOfKind(родЦели));
            s.player(1).units.add(враг);
            враг.hexId = сосед;
            p.resources.pay(Resource.AMMO, p.resources.ammo());
            p.resources.add(Resource.AMMO, 1);
            if (сКартой) {
                p.superArsenalCards.add("sa4_09");
            }
            CombatResolver бой = new CombatResolver(s, ev -> { });
            boolean можно = бой.canAttack(0, дом, сосед);
            assertEquals(сКартой, можно, сКартой
                ? "с «Баллистическим расчётом» универсальная атака по 1 боеприпасу"
                : "без него на один боеприпас бьётся только специальная цель");
        }
    }

    // ==================================================================
    //  ВСПОМОГАТЕЛЬНОЕ
    // ==================================================================

    private static List<Agent> агенты() {
        return List.of(new Пасующий(0), new Пасующий(1), new Пасующий(2), new Пасующий(3));
    }

    private static List<Choice> варианты(GameState s, int seat, String id) {
        List<Choice> out = new ArrayList<>();
        for (Choice c : Abilities.options(s, seat, OptionSource.Slot.SPEC)) {
            if (String.valueOf(c.kind()).equals("ability:" + id)) {
                out.add(c);
            }
        }
        return out;
    }

    private static Map<String, Object> последнее(List<Map<String, Object>> события, String тип) {
        Map<String, Object> out = null;
        for (Map<String, Object> ev : события) {
            if (тип.equals(ev.get("type"))) {
                out = ev;
            }
        }
        return out;
    }

}
