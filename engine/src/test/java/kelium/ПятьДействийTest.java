package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.core.Agent;
import kelium.core.BuildingToken;
import kelium.core.BuildingType;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.PlayerState;
import kelium.core.Resource;
import kelium.dataio.Ctx;
import kelium.engine.Actions;
import kelium.engine.GameEngine;
import kelium.engine.Order;
import kelium.engine.TurnContext;
import kelium.support.Fix;

/**
 * ПЯТЬ ДЕЙСТВИЙ-РАЗВИЛОК (решение дизайнера 27.09.2026, свод 1.46.0, приказы
 * 5.0.0): каждое действие — «одно из двух», ветка выбирается в начале
 * действия; «построить» — Стройка только своих типов; своё ЦУ не сносится;
 * срабатывания при постройке нет; у цвета пять карт без джокера.
 */
class ПятьДействийTest {

    private static final String СВОД = "1.46.0";

    /** Берёт заданную ветку; в Стройке — строит и сносит, что предложат; запоминает всё. */
    private static final class Ветвящий extends Agent {
        final String ветка;
        final List<String> виды = new ArrayList<>();
        final List<Choice> стройка = new ArrayList<>();
        final List<Choice> снос = new ArrayList<>();
        int стройкиВыбрано = 0;

        Ветвящий(int seat, String ветка) {
            super(seat, "ветвящий");
            this.ветка = ветка;
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            String вид = String.valueOf(ctx.get("kind"));
            виды.add(вид);
            if ("action_branch".equals(вид)) {
                for (Choice c : options) {
                    if (ветка.equals(c.payload())) {
                        return c;
                    }
                }
                throw new AssertionError("нет ветки " + ветка + " среди " + options);
            }
            if ("build_pick".equals(вид)) {
                for (Choice c : options) {
                    if ("build_pick".equals(c.kind())) {
                        стройка.add(c);
                    } else if ("demolish_pick".equals(c.kind())) {
                        снос.add(c);
                    }
                }
                // одна постройка — и хватит, иначе Стройка крутится до денег
                if (стройкиВыбрано++ > 0) {
                    for (Choice c : options) {
                        if (c.payload() == null) {
                            return c;
                        }
                    }
                }
                for (Choice c : options) {
                    if ("build_pick".equals(c.kind())) {
                        return c;
                    }
                }
            }
            for (Choice c : options) {
                if (!"pass".equals(c.kind()) && c.payload() != null) {
                    return c;
                }
            }
            return options.get(0);
        }
    }

    private static GameState игра() {
        return Fix.game(СВОД, 4, 7L);
    }

    @Test
    void сводПоУмолчаниюНовый() {
        assertEquals(СВОД, kelium.dataio.GameConfig.DEFAULT_RULESET);
    }

    @Test
    void колодаПятьКартУЦветаБезДжокера() {
        GameState s = игра();
        var колода = Ctx.cards(s, "orders");
        Map<String, Integer> поЦвету = new HashMap<>();
        Map<String, String> низ = Map.of(
            "settle", "advance", "mobilize", "secure", "advance", "research",
            "secure", "settle", "research", "mobilize");
        for (Map<String, Object> c : колода.entries) {
            assertFalse(Boolean.TRUE.equals(c.get("joker")), "джокера нет: " + c.get("id"));
            поЦвету.merge(String.valueOf(c.get("deck")), 1, Integer::sum);
            String верх = String.valueOf(c.get("top"));
            assertEquals(низ.get(верх), String.valueOf(c.get("bottom")),
                "низ карты " + c.get("id"));
            // на НАСТУПАТЬ плашки нет ни у одного цвета (комплект «пять развилок», 27.09)
            if ("advance".equals(верх)) {
                assertNull(c.get("spec"), "на НАСТУПАТЬ плашки нет: " + c.get("id"));
            } else {
                assertNotNull(c.get("spec"), "плашка у " + c.get("id"));
            }
        }
        assertEquals(Map.of("blue", 5, "red", 5, "green", 5, "yellow", 5), поЦвету);
        for (String цвет : поЦвету.keySet()) {
            Set<String> верхи = new java.util.HashSet<>();
            for (Map<String, Object> c : колода.entries) {
                if (цвет.equals(c.get("deck"))) {
                    верхи.add(String.valueOf(c.get("top")));
                }
            }
            assertEquals(Set.of("settle", "mobilize", "advance", "secure", "research"), верхи,
                "у цвета " + цвет + " все пять приказов");
        }
        assertEquals(5, Ctx.rules(s).getInt("rounds.order_hand_size"));
    }

    @Test
    void приказыДаютСоседниеДействия() {
        assertEquals(List.of("extract", "power"), List.of(Order.ORDER_ACTIONS.get(Order.SETTLE)));
        assertEquals(List.of("power", "supply"), List.of(Order.ORDER_ACTIONS.get(Order.MOBILIZE)));
        assertEquals(List.of("supply", "command"), List.of(Order.ORDER_ACTIONS.get(Order.ADVANCE)));
        assertEquals(List.of("command", "develop"), List.of(Order.ORDER_ACTIONS.get(Order.SECURE)));
        assertEquals(List.of("develop", "extract"), List.of(Order.ORDER_ACTIONS.get(Order.RESEARCH)));
        assertEquals("ОСВОИТЬ", Order.SETTLE.имя());
        assertEquals("КОНТРОЛИРОВАТЬ", Order.SECURE.имя());
    }

    @Test
    void развилкаВыполняетВыбраннуюВетку() {
        for (String развилка : Actions.FORK_NAMES) {
            for (String ветка : Actions.FORKS.get(развилка)) {
                GameState s = игра();
                PlayerState p = s.player(0);
                p.resources.add(Resource.COIN, 10);
                Ветвящий агент = new Ветвящий(0, ветка);
                TurnContext ход = new TurnContext(0, 1);
                Actions.ForkAction a = (Actions.ForkAction) Actions.create(развилка, s);
                a.perform(p, ход, агент);
                assertEquals(ветка, a.branch, развилка + ": выполнена выбранная ветка");
                assertEquals("action_branch", агент.виды.get(0),
                    развилка + ": первым шагом — выбор ветки");
                assertTrue(ход.actionsPlayed.contains(развилка),
                    развилка + ": слот хода занят развилкой");
                assertFalse(ход.actionsPlayed.contains(ветка),
                    развилка + ": ветка слота не занимает");
                assertEquals(Set.copyOf(Actions.FORKS.get(развилка)).size() > 1, true);
            }
        }
    }

    @Test
    void веткаПостроитьСтроитТолькоСвоиТипы() {
        Map<String, Set<BuildingType>> можно = Map.of(
            "extract", Set.of(BuildingType.MINER),
            "power", Set.of(BuildingType.POWER_PLANT),
            "supply", Set.of(BuildingType.BARRACKS, BuildingType.FACTORY,
                BuildingType.AIRBASE, BuildingType.COMMAND_CENTER));
        for (var e : можно.entrySet()) {
            GameState s = игра();
            PlayerState p = s.player(0);
            p.resources.add(Resource.COIN, 20);
            Ветвящий агент = new Ветвящий(0, "build");
            Actions.create(e.getKey(), s).perform(p, new TurnContext(0, 1), агент);
            assertFalse(агент.стройка.isEmpty(), e.getKey() + ": есть что построить");
            for (Choice c : агент.стройка) {
                BuildingType t = (BuildingType) ((Map<?, ?>) c.payload()).get("btype");
                assertTrue(e.getValue().contains(t), e.getKey() + ": чужой тип " + t);
            }
            for (Choice c : агент.снос) {
                assertFalse(c.label().contains("command_center"),
                    e.getKey() + ": своё ЦУ не сносится");
            }
        }
        // А обычная Стройка (без ветки) предлагает все типы.
        GameState s = игра();
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 20);
        Ветвящий агент = new Ветвящий(0, "build");
        Actions.create("build", s).perform(p, new TurnContext(0, 1), агент);
        Set<BuildingType> типы = new java.util.HashSet<>();
        for (Choice c : агент.стройка) {
            типы.add((BuildingType) ((Map<?, ?>) c.payload()).get("btype"));
        }
        assertTrue(типы.contains(BuildingType.MINER) && типы.contains(BuildingType.POWER_PLANT)
            && типы.contains(BuildingType.BARRACKS), "обычная Стройка — все типы: " + типы);
    }

    @Test
    void сносТолькоСвоихТиповВетки() {
        GameState s = игра();
        PlayerState p = s.player(0);
        BuildingToken цу = null;
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                цу = b;
            }
        }
        assertNotNull(цу);
        String рядом = Fix.freeNeighbour(s, цу.hexId);
        Fix.building(s, 0, BuildingType.MINER, рядом, 1);
        Fix.building(s, 0, BuildingType.POWER_PLANT, рядом, 1);
        Ветвящий добыча = new Ветвящий(0, "build");
        Actions.create("extract", s).perform(p, new TurnContext(0, 1), добыча);
        assertFalse(добыча.снос.isEmpty(), "свой добытчик сносится");
        for (Choice c : добыча.снос) {
            assertTrue(c.label().contains("miner"), "Добыча сносит только добытчики: " + c.label());
        }
    }

    @Test
    void своёЦуНеСносится() {
        GameState s = игра();
        PlayerState p = s.player(0);
        assertTrue(p.ownCuTokenAvailable);
        Ветвящий агент = new Ветвящий(0, "build");
        Actions.create("build", s).perform(p, new TurnContext(0, 1), агент);
        Actions.create("supply", s).perform(p, new TurnContext(0, 1), агент);
        for (Choice c : агент.снос) {
            assertFalse(c.label().contains("command_center"), "снос ЦУ не предлагается");
        }
        assertFalse(Ctx.rules(s).getBool("actions.build.demolish_cu_allowed", true));
    }

    @Test
    void веткаПостроитьСтавитОдноЗданиеИОноСрабатывает() {
        // комплект «пять развилок» 27.09: ветка «построить» — ОДНО здание своего
        // вида, и новое здание сразу срабатывает без энергии
        GameState s = игра();
        assertTrue(Ctx.rules(s).getBool("actions.build.building_fires_on_build", false));
        assertFalse(Ctx.rules(s).getBool("actions.build.military_fires_only_on_container", true));
        PlayerState p = s.player(0);
        p.resources.add(Resource.COIN, 20);
        int зданий = p.buildingsOnField().size();
        Actions.create("extract", s).perform(p, new TurnContext(0, 1), new Ветвящий(0, "build"));
        assertTrue(p.buildingsOnField().size() <= зданий + 1, "ветка ставит не больше одного здания");
    }

    @Test
    void ходПишетВЖурналИмяВетки() {
        GameState s = игра();
        List<Agent> агенты = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            агенты.add(new Fix.FirstChoiceAgent(i));
        }
        List<Map<String, Object>> события = new ArrayList<>();
        GameEngine.bindResume(s, агенты, события::add);
        String карта = "blue_advance";
        assertEquals("advance", Ctx.cards(s, "orders").byId(карта).get("top"));
        new GameEngine(s, агенты, события::add).simulateTurn(0, карта, false, false);
        List<String> развилки = new ArrayList<>();
        for (Map<String, Object> e : события) {
            if ("action".equals(e.get("type"))) {
                String развилка = String.valueOf(e.get("fork"));
                развилки.add(развилка);
                assertTrue(Actions.FORKS.get(развилка).contains(String.valueOf(e.get("action"))),
                    "в событии — имя ветки: " + e);
            }
        }
        assertEquals(List.of("supply", "command"), развилки, "НАСТУПАТЬ: Снабжение и Командование");
    }

    /** Первый вариант во всём; запоминает руку приказов на сбросе. */
    private static final class Первый extends Agent {
        private final Fix.FirstChoiceAgent первый;
        final List<Integer> рукаНаСбросе = new ArrayList<>();
        final List<String> карты = new ArrayList<>();

        Первый(int seat) {
            super(seat, "первый");
            первый = new Fix.FirstChoiceAgent(seat);
        }

        @Override
        public Choice choose(GameState s, List<Choice> options, Map<String, Object> ctx) {
            if ("blind_discard".equals(String.valueOf(ctx.get("kind")))) {
                рукаНаСбросе.add(options.size());
                for (Choice c : options) {
                    карты.add(String.valueOf(c.payload()));
                }
            }
            return первый.choose(s, options, ctx);
        }
    }

    @Test
    void партияДоигрываетсяИРукаПятьКартБезДжокера() {
        for (int игроков = 2; игроков <= 4; игроков++) {
            GameState s = Fix.game(СВОД, игроков, 11L + игроков);
            List<Agent> агенты = new ArrayList<>();
            for (int i = 0; i < игроков; i++) {
                агенты.add(new Первый(i));
            }
            Map<String, Object> итог = new GameEngine(s, агенты, e -> { }).run();
            assertNotNull(итог.get("scores"), "партия на " + игроков + " доиграна");
            Первый первый = (Первый) агенты.get(0);
            assertFalse(первый.рукаНаСбросе.isEmpty(), "сброс спрашивали");
            assertEquals(5, первый.рукаНаСбросе.get(0), "в руке пять карт");
            for (String id : первый.карты) {
                assertFalse(Boolean.TRUE.equals(Ctx.cards(s, "orders").byId(id).get("joker")));
            }
        }
    }

    @Test
    void прежнийСводИграетсяПоСтарому() {
        GameState s = Fix.game("1.45.0", 4, 7L);
        boolean джокер = false;
        for (Map<String, Object> c : Ctx.cards(s, "orders").entries) {
            джокер |= Boolean.TRUE.equals(c.get("joker"));
        }
        assertTrue(джокер, "в 1.45.0 колода 4.0.0 с джокерами");
        assertTrue(Ctx.rules(s).getBool("actions.build.building_fires_on_build", false));
    }
}
