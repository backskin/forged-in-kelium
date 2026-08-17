package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.bench.CardBench;
import kelium.core.BuildingType;

/**
 * n1 «Подъём» — начальное задание. ВСЕМИ СПОСОБАМИ, КАКИМИ ЕЁ МОЖНО РАЗЫГРАТЬ.
 *
 * <p>Требование СОСТОЯНИЯ: на поле не меньше трёх своих зданий, считая ЦУ.
 * Усиления у начальных заданий нет ни у одного, поэтому способов два: ВЫПОЛНИТЬ
 * и СЖЕЧЬ.
 *
 * <p>ПОРОГ ЗДЕСЬ — ГЛАВНОЕ. Дизайнер поднял его с двух зданий до трёх именно
 * потому, что ЦУ у игрока стоит с подготовки: порог «2» закрывался одной
 * стройкой. Поэтому проверка на двух зданиях обязана давать ОТКАЗ — иначе карта
 * вернулась к прежнему виду, а по данным этого не увидеть.
 */
class Задание_n1_Подъём {

    private static final String ID = "n1";
    private static final String НАБОР = "objectives";

    private static final Set<CardBench.Способ> ПОКРЫТО = EnumSet.of(
        CardBench.Способ.ВЫПОЛНИТЬ,
        CardBench.Способ.СЖЕЧЬ);

    /** НИ ОДИН СПОСОБ НЕ ЗАБЫТ — список считается по записи карты в каталоге. */
    @Test
    void всеСпособыПокрыты() {
        var b = CardBench.партия(4);
        var непокрыто = CardBench.способыБезПроверки(b.настройка(), НАБОР, ID, ПОКРЫТО);
        assertTrue(непокрыто.isEmpty(),
            "у карты " + ID + " есть способы разыграть её, на которые нет теста: " + непокрыто);
    }

    /** ВЫПОЛНИТЬ: на трёх зданиях условие видно движку. */
    @Test
    void триЗданияУсловиеВыполнено() {
        var b = CardBench.партия(4)
            .заданиеВРуку(0, ID)
            .здание(0, BuildingType.BARRACKS, 0)
            .здание(0, BuildingType.MINER, 0);
        assertTrue(b.готово(0, ID),
            "ЦУ и два здания — три штуки, условие обязано быть выполнено");
    }

    /** ОТКАЗ НА ДВУХ ЗДАНИЯХ: порог поднят до трёх ради этого. */
    @Test
    void двухЗданийНеХватает() {
        var b = CardBench.партия(4)
            .заданиеВРуку(0, ID)
            .здание(0, BuildingType.BARRACKS, 0);
        assertFalse(b.готово(0, ID),
            "ЦУ и одно здание — условие не должно считаться выполненным");
    }

    /** ОТКАЗ НА ОДНОМ ЦУ: карта не выполняется даром с подготовки. */
    @Test
    void однимЦУНеВыполняется() {
        var b = CardBench.партия(4).заданиеВРуку(0, ID);
        assertFalse(b.готово(0, ID),
            "на подготовке у игрока стоит только ЦУ — задание не должно быть готово");
    }

    /** У НАЧАЛЬНОЙ КАРТЫ НЕТ УСИЛЕНИЯ — ни в данных, ни в индикаторе. */
    @Test
    void усиленияНет() {
        var b = CardBench.партия(4)
            .заданиеВРуку(0, ID)
            .здание(0, BuildingType.BARRACKS, 0)
            .здание(0, BuildingType.MINER, 0);
        assertFalse(b.усилено(0, ID), "у начального задания не может быть усиления");
    }

    /** ВЫПОЛНИТЬ В ПАРТИИ: награда — обломок и карта задания. */
    @Test
    void выполнитьВПартии() {
        var b = CardBench.партия(4)
            .заданиеВРуку(0, ID)
            .здание(0, BuildingType.BARRACKS, 0)
            .здание(0, BuildingType.MINER, 0)
            .обломки(0, 0);
        b.игратьДо(1, 0, CardBench.выполнитьЗадание(ID));
        assertTrue(b.было("objective", "card", ID),
            "движок не сообщил о выполнении задания. " + b.сводка());
        assertFalse(b.состояние().player(0).objectiveHand.contains(ID),
            "выполненное задание осталось в руке");
        assertEquals(1, b.наградаБазовая(ID, "debris"),
            "награда обломком не выдана: " + b.награда(ID));
        assertEquals(1, b.наградаБазовая(ID, "objective_card"),
            "награда картой задания не выдана: " + b.награда(ID));
    }

    /** СЖЕЧЬ: карта уходит из руки, верх даёт монету. */
    @Test
    void сжечь() {
        var b = CardBench.партия(4)
            .заданиеВРуку(0, ID)
            .монеты(0, 0);
        b.игратьДо(1, 0, CardBench.сжечьЗадание(ID));
        assertTrue(b.было("objective_burn", "card", ID),
            "движок не сообщил о сжигании задания. " + b.сводка());
        assertFalse(b.состояние().player(0).objectiveHand.contains(ID),
            "сожжённое задание осталось в руке");
    }

    /** НАГРАДА ТАКАЯ ЖЕ, КАК У ВСЕХ НАЧАЛЬНЫХ: обломок и карта задания. */
    @Test
    void наградаКакУВсехНачальных() {
        var b = CardBench.партия(4);
        var карта = b.настройка().content.get(НАБОР).byId(ID);
        assertEquals(java.util.Map.of("debris", 1, "objective_card", 1), карта.get("base_reward"),
            "у начального задания награда обязана быть ровно 1 обломок и 1 карта задания");
        assertFalse(карта.containsKey("enhanced"), "у начального задания не бывает усиления");
    }
}
