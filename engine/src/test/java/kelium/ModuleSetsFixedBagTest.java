package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.engine.ModuleSets;

/**
 * ФИКСИРОВАННЫЕ НАБОРЫ МОДУЛЕЙ НЕ МАСШТАБИРУЮТСЯ ПО ЧИСЛУ ИГРОКОВ (решение
 * дизайнера 20.08.2026).
 *
 * <p>ЗАЧЕМ ЭТОТ ТЕСТ. Награда «модуль» тянется из ОБЩЕГО мешка, а не
 * раздаётся личным комплектом на игрока — раньше это делалось нижним слоем
 * (buildBag клал по копии набора на каждого игрока: 8/12/16 жетонов на
 * 2/3/4 игроков), хотя мешок общий и делить его не на кого. R30/C30 уже
 * полные сами по себе (все шесть пар целей; все четыре вида выхода Сборки),
 * поэтому их количество должно быть ОДНО И ТО ЖЕ при любом числе игроков —
 * иначе на четверых мешок раздуется до 48 жетонов вместо 12.
 */
class ModuleSetsFixedBagTest {

    private static ModuleSets.Library lib() {
        return ModuleSets.load(GameConfig.resolveDataRoot(null), "3.0.0");
    }

    @Test
    void красныйФиксированныйМешокОдинаковыйНезависимоОтЧислаИгроков() {
        ModuleSets.Library lib = lib();
        for (int players = 2; players <= 4; players++) {
            List<String> bag = ModuleSets.buildBag(lib, lib.redSets(), "bag_R30",
                players, new Random(1));
            assertEquals(12, bag.size(),
                "фиксированный набор R30 должен давать 12 жетонов при " + players
                + " игроках, а не масштабироваться");
        }
    }

    @Test
    void синийФиксированныйМешокОдинаковыйНезависимоОтЧислаИгроков() {
        ModuleSets.Library lib = lib();
        for (int players = 2; players <= 4; players++) {
            List<String> bag = ModuleSets.buildBag(lib, lib.blueSets(), "bag_C30",
                players, new Random(1));
            assertEquals(12, bag.size(),
                "фиксированный набор C30 должен давать 12 жетонов при " + players
                + " игроках, а не масштабироваться");
        }
    }

    @Test
    void старыйНемасштабируемыйНаборВсёЕщёМасштабируется() {
        // КОНТРОЛЬНАЯ ПРОВЕРКА: fixed влияет ТОЛЬКО на помеченные наборы,
        // прежнее поведение (R1 — личный комплект x число игроков) не тронуто.
        ModuleSets.Library lib = lib();
        List<String> bag2 = ModuleSets.buildBag(lib, lib.redSets(), "bag_R1", 2, new Random(1));
        List<String> bag4 = ModuleSets.buildBag(lib, lib.redSets(), "bag_R1", 4, new Random(1));
        assertEquals(8, bag2.size());
        assertEquals(16, bag4.size());
        assertTrue(bag4.size() > bag2.size());
    }

    @Test
    void всеШестьПарЦелейПредставленыВR30() {
        ModuleSets.Library lib = lib();
        var set = lib.redSets().get("R30");
        var pairs = new java.util.HashSet<String>();
        for (var t : set.tokens()) {
            var targets = new java.util.ArrayList<>(t.targets());
            java.util.Collections.sort(targets);
            pairs.add(String.join("+", targets));
        }
        assertEquals(6, pairs.size(), "должно быть ровно шесть разных пар целей: " + pairs);
        assertTrue(pairs.contains("aircraft+infantry"),
            "пара пехота/авиация отсутствовала во ВСЕХ прежних наборах — R30 обязана её закрыть");
    }
}
