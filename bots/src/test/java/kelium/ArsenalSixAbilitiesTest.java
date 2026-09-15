package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.engine.ability.Abilities;
import kelium.engine.ability.OptionSource;

/**
 * НОВЫЕ НИЗЫ АРСЕНАЛА 6.0.0 — работают ли они на самом деле.
 *
 * <p>ЗАЧЕМ ОТДЕЛЬНЫЙ ТЕСТ, КОГДА ЕСТЬ ПРОГОН ПАРТИЙ. Прогон показывает, что
 * способность СРАБОТАЛА ХОТЬ РАЗ, и этого мало: способность, которой боты не
 * пользуются, выглядит в прогоне точно так же, как способность, которую движок
 * не умеет предложить. Разница видна только на собранном вручную столе — здесь
 * он и собирается: карта установлена, условие выполнено, монета есть.
 *
 * <p>«Комендатура» проверяется особенно подробно: в прогоне сорока партий она не
 * сработала ни разу, и надо было отличить «боты не берут» от «нечего брать».
 */
class ArsenalSixAbilitiesTest {

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

    /** Номер карты действующей колоды, несущей эту установку. */
    private static String карта(GameState s, String пассивка) {
        for (var card : kelium.dataio.Ctx.cards(s, "arsenal").entries) {
            if (card.get("bottom") instanceof Map<?, ?> bm
                    && пассивка.equals(String.valueOf(bm.get("passive")))) {
                return String.valueOf(card.get("id"));
            }
        }
        throw new AssertionError("в колоде нет карты со способностью " + пассивка);
    }

    private static void установить(GameState s, int seat, String пассивка) {
        s.player(seat).arsenalInstalled.add(карта(s, пассивка));
    }

    private static List<Choice> спец(GameState s, int seat) {
        return Abilities.options(s, seat, OptionSource.Slot.SPEC);
    }

    private static Choice вариант(List<Choice> опции, String id) {
        for (Choice c : опции) {
            if (String.valueOf(c.kind()).contains(id)) {
                return c;
            }
        }
        return null;
    }

    // ==================================================================
    //  КОМЕНДАТУРА
    // ==================================================================

    @Test
    void комендатураВыпускаетГарнизонЗаСтенку() {
        GameState s = стол(41L);
        var p = s.player(0);
        установить(s, 0, "spec_release_garrison");
        p.resources.add(Resource.COIN, 3);

        BuildingToken казарма = военноеЗдание(s, 0);
        assertNotNull(казарма, "у игрока есть военное здание на поле");
        UnitToken жетон = свободныйЖетон(s, 0);
        assertNotNull(жетон, "нашёлся жетон войск, который можно посадить внутрь");

        // САЖАЕМ ГАРНИЗОН РУКАМИ: в Снаряжении жетон встаёт «на гекс здания или
        // ВНУТРЬ него» (глава 8), и здесь нас интересует именно второй случай.
        жетон.setHexId(казарма.hexId);
        жетон.insideBuildingUid = казарма.uid;

        Choice ход = вариант(спец(s, 0), "spec_release_garrison");
        assertNotNull(ход, "СПЕЦ-действие «выпустить гарнизон» предложено");

        int монетБыло = p.resources.get(Resource.COIN);
        assertTrue(Abilities.perform(s, 0, ход, new Решительный()),
            "выпуск гарнизона состоялся");
        assertEquals(монетБыло - 1, p.resources.get(Resource.COIN),
            "выпуск стоит ровно одну монету");
        assertFalse(жетон.inside(), "жетон вышел из здания");
        assertFalse(казарма.hexId.equals(жетон.hexId),
            "жетон вышел НА СОСЕДНИЙ гекс, а не остался на гексе здания");
    }

    @Test
    void комендатураМолчитБезГарнизона() {
        GameState s = стол(42L);
        установить(s, 0, "spec_release_garrison");
        s.player(0).resources.add(Resource.COIN, 3);
        assertNotNull(военноеЗдание(s, 0), "здание есть");
        // Внутри никого — предлагать нечего.
        assertEquals(null, вариант(спец(s, 0), "spec_release_garrison"),
            "пустое здание гарнизон не выпускает");
    }

    // ==================================================================
    //  РЕМОНТНАЯ БАЗА
    // ==================================================================

    @Test
    void ремонтнаяБазаСнимаетУронВОбновление() {
        GameState s = стол(43L);
        установить(s, 0, "repair_all_in_refresh");
        var p = s.player(0);
        int ранено = 0;
        for (UnitToken u : p.unitsOnField()) {
            u.damage = 1;
            ранено++;
        }
        for (BuildingToken b : p.buildingsOnField()) {
            b.damage = 1;
            ранено++;
        }
        assertTrue(ранено > 0, "было кого чинить");

        // Чужой урон не трогается: карта чинит СВОИ жетоны.
        var сосед = s.player(1);
        for (UnitToken u : сосед.unitsOnField()) {
            u.damage = 1;
        }

        kelium.engine.GameEngine eng = new kelium.engine.GameEngine(s,
            List.of(new Решительный(), new Решительный(), new Решительный(),
                new Решительный()), ev -> { });
        eng.refreshForTest(2);

        for (UnitToken u : p.unitsOnField()) {
            assertEquals(0, u.damage, "урон со своего войска снят");
        }
        for (BuildingToken b : p.buildingsOnField()) {
            assertEquals(0, b.damage, "урон со своего здания снят");
        }
        boolean чужойУронОстался = false;
        for (UnitToken u : сосед.unitsOnField()) {
            чужойУронОстался |= u.damage > 0;
        }
        assertTrue(чужойУронОстался, "чужие жетоны карта не чинит");
    }

    // ==================================================================
    //  ПОДРЯДЧИК
    // ==================================================================

    @Test
    void подрядчикСтавитНейтралаЗаМонетуИТолькоУСвоихЗданий() {
        GameState s = стол(44L);
        установить(s, 0, "spec_neutral_near_own_building");
        s.player(0).resources.add(Resource.COIN, 3);

        java.util.Set<Integer> былиНейтралы = номераНейтралов(s);
        int былоНейтралов = былиНейтралы.size();
        Choice ход = вариант(спец(s, 0), "spec_neutral_near_own_building");
        assertNotNull(ход, "СПЕЦ-действие «нейтральная постройка» предложено");

        int монетБыло = s.player(0).resources.get(Resource.COIN);
        assertTrue(Abilities.perform(s, 0, ход, new Решительный()), "постройка встала");
        assertEquals(монетБыло - 1, s.player(0).resources.get(Resource.COIN),
            "постройка стоит ровно одну монету");
        assertEquals(былоНейтралов + 1, нейтралов(s), "нейтралов стало на одного больше");

        // ОДИНАРНАЯ: карта говорит про «свободный сектор», а не про два.
        Hex.NeutralBuilding новый = новыйНейтрал(s, былиНейтралы);
        assertNotNull(новый, "новая постройка нашлась");
        assertFalse(новый.big, "постройка одинарная, на один сектор");
    }

    @Test
    void подрядчикМолчитБезМонеты() {
        GameState s = стол(45L);
        установить(s, 0, "spec_neutral_near_own_building");
        s.player(0).resources.pay(Resource.COIN,
            s.player(0).resources.get(Resource.COIN));
        assertEquals(null, вариант(спец(s, 0), "spec_neutral_near_own_building"),
            "без монеты предложения нет");
    }

    // ==================================================================
    //  РАЗРЯДНИК
    // ==================================================================

    @Test
    void разрядникКопитЭнергиюИВозвращаетЕёНаИсточник() {
        GameState s = стол(46L);
        установить(s, 0, "spec_energy_on_card_free_attack");
        var p = s.player(0);

        // На подготовке у игрока один источник с одним кубиком, а карте нужны
        // два. В партии это обычное дело — энергостанции дают по 2-4 кубика, —
        // поэтому доложим кубики на ЦУ, а не будем доигрывать до стройки.
        for (BuildingToken b : p.buildingsOnField()) {
            if (b.type == BuildingType.COMMAND_CENTER) {
                b.energyIdle += 2;
            }
        }
        assertTrue(кубиковНаИсточниках(s, 0) >= 2, "есть что переносить на карту");
        int былоНаИсточниках = кубиковНаИсточниках(s, 0) + кубиковВЯчейках(s, 0);

        Choice зарядка = вариант(спец(s, 0), "spec_energy_on_card_free_attack");
        assertNotNull(зарядка, "зарядка предложена");
        assertTrue(Abilities.perform(s, 0, зарядка, new Решительный()), "первый кубик лёг на карту");
        assertEquals(1, p.arsenalCardEnergy.getOrDefault("разрядник", 0),
            "на карте один кубик");

        Choice вторая = вариант(спец(s, 0), "spec_energy_on_card_free_attack");
        assertNotNull(вторая, "вторая зарядка предложена");
        assertTrue(Abilities.perform(s, 0, вторая, new Решительный()), "второй кубик лёг на карту");
        assertEquals(2, p.arsenalCardEnergy.getOrDefault("разрядник", 0),
            "на карте оба кубика");
        assertEquals(былоНаИсточниках - 2, кубиковНаИсточниках(s, 0) + кубиковВЯчейках(s, 0),
            "кубики ушли со стола игрока, а не из воздуха");

        // Кубиков на карте два — теперь предлагается выстрел.
        List<Choice> опции = спец(s, 0);
        Choice выстрел = null;
        for (Choice c : опции) {
            if (String.valueOf(c.kind()).endsWith(":fire")) {
                выстрел = c;
            }
        }
        assertNotNull(выстрел, "разрядка предложена, когда обе ячейки заняты");
    }

    // ==================================================================
    //  вспомогательное
    // ==================================================================

    private static BuildingToken военноеЗдание(GameState s, int seat) {
        for (BuildingToken b : s.player(seat).buildingsOnField()) {
            if (b.type == BuildingType.BARRACKS || b.type == BuildingType.FACTORY
                    || b.type == BuildingType.AIRBASE
                    || b.type == BuildingType.COMMAND_CENTER) {
                Hex h = b.hexId == null ? null : s.field.get(b.hexId);
                if (h == null) {
                    continue;
                }
                for (int side = 0; side < 6; side++) {
                    if (h.sideOwner[side] != null && h.sideOwner[side] == b.uid
                            && h.neighborBySide[side] != null) {
                        return b;      // здание, обращённое стенкой к соседу
                    }
                }
            }
        }
        return null;
    }

    private static UnitToken свободныйЖетон(GameState s, int seat) {
        for (UnitToken u : s.player(seat).unitsOnField()) {
            if (!u.inside()) {
                return u;
            }
        }
        return null;
    }

    private static int нейтралов(GameState s) {
        int n = 0;
        for (Hex h : s.field.hexes.values()) {
            n += h.neutrals.size();
        }
        return n;
    }

    private static java.util.Set<Integer> номераНейтралов(GameState s) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (Hex h : s.field.hexes.values()) {
            for (Hex.NeutralBuilding nb : h.neutrals) {
                out.add(nb.uid);
            }
        }
        return out;
    }

    /** Нейтрал, которого не было до хода: сравнение по номерам, а не «последний». */
    private static Hex.NeutralBuilding новыйНейтрал(GameState s, java.util.Set<Integer> были) {
        for (Hex h : s.field.hexes.values()) {
            for (Hex.NeutralBuilding nb : h.neutrals) {
                if (!были.contains(nb.uid)) {
                    return nb;
                }
            }
        }
        return null;
    }

    /** Кубики, лежащие на источниках нерозданными. */
    private static int кубиковНаИсточниках(GameState s, int seat) {
        int n = 0;
        for (BuildingToken b : s.player(seat).buildingsOnField()) {
            n += b.energyIdle;
        }
        return n;
    }

    /** Кубики, уже разложенные по ячейкам энергии зданий. */
    private static int кубиковВЯчейках(GameState s, int seat) {
        int n = 0;
        for (BuildingToken b : s.player(seat).buildingsOnField()) {
            n += b.energyPlaced;
        }
        return n;
    }
}
