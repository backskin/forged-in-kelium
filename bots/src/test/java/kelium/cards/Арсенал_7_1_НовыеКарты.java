package kelium.cards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import kelium.bench.CardBench;
import kelium.core.UnitType;

/**
 * НОВЫЕ КАРТЫ АРСЕНАЛА 7.1.0 (25.09.2026) — ВСЕМИ СПОСОБАМИ, КАКИМИ ИХ МОЖНО
 * РАЗЫГРАТЬ, в настоящей партии ботов (стенд {@link CardBench}).
 *
 * <ul>
 *   <li>обычная №33 «Боевое довольствие» (верх «Действие «Манёвр»», низ
 *       «Выполнив задание, получи 1 боеприпас»);</li>
 *   <li>начальная №5 (верх «добыча 1 добытчиком», низ «в конце Боя, если
 *       уничтожил хотя бы один жетон — 1 монета»);</li>
 *   <li>начальная №6 (верх «замени приказ с руки на другой из сброса», низ
 *       «СПЕЦ: 2 монеты — замени на поле один свой жетон наземного войска на
 *       другой наземный жетон из запаса»);</li>
 *   <li>начальная №7 «Сдача тары» (верх «манёвр 1 войском», низ «Вскрывая
 *       контейнер, получи ещё 1 монету»).</li>
 * </ul>
 */
class Арсенал_7_1_НовыеКарты {

    private static final String НАБОР = "arsenal";
    private static final List<String> КАРТЫ = List.of("a7_33", "bs7_5", "bs7_6", "bs7_7");

    /** Способы, покрытые тестами этого класса (у №6 есть ещё ПРИМЕНИТЬ). */
    private static final Set<CardBench.Способ> ПОКРЫТО = EnumSet.of(
        CardBench.Способ.СЖЕЧЬ, CardBench.Способ.УСТАНОВИТЬ, CardBench.Способ.ПРИМЕНИТЬ);

    @Test
    void всеСпособыПокрыты() {
        var b = CardBench.партия(4);
        for (String id : КАРТЫ) {
            var непокрыто = CardBench.способыБезПроверки(b.настройка(), НАБОР, id, ПОКРЫТО);
            assertTrue(непокрыто.isEmpty(), id + ": способы без теста " + непокрыто);
            assertFalse(CardBench.способы(b.настройка(), НАБОР, id).isEmpty(),
                id + " нет в действующем наборе");
        }
    }

    @Test
    void установить() {
        for (String id : КАРТЫ) {
            var b = CardBench.партия(4)
                .арсеналВРуку(0, id)
                .монеты(0, 6)
                .игратьДо(2, 0, CardBench.установить(id));
            assertTrue(b.состояние().player(0).arsenalInstalled.contains(id),
                id + " не установлена. " + b.сводка());
            assertTrue(b.было("arsenal", "mode", "install", "card", id),
                id + ": движок не сообщил об установке. " + b.сводка());
        }
    }

    @Test
    void сжечь() {
        for (String id : КАРТЫ) {
            var b = CardBench.партия(4)
                .арсеналВРуку(0, id)
                .монеты(0, 6)
                .боеприпасы(0, 3)
                .игратьДо(2, 0, CardBench.сжечьАрсенал(id));
            assertFalse(b.состояние().player(0).arsenalHand.contains(id),
                id + ": сожжённая карта осталась в руке. " + b.сводка());
            assertFalse(b.состояние().player(0).arsenalInstalled.contains(id),
                id + ": сожжённая карта оказалась установленной");
            assertTrue(b.было("arsenal", "mode", "burn", "card", id),
                id + ": движок не сообщил о сжигании. " + b.сводка());
        }
    }

    /** ПРИМЕНИТЬ: СПЕЦ начальной №6 — замена наземного жетона за 2 монеты. */
    @Test
    void применитьЗаменуЖетона() {
        var b = CardBench.партия(4)
            .арсеналУстановлен(0, "bs7_6")
            .войско(0, null, UnitType.INFANTRY)
            .монеты(0, 6);
        assertTrue(b.доступныеСпец(0).stream().anyMatch(v -> v.startsWith(
                "ability:spec_swap_ground_unit")),
            "СПЕЦ замены не предложен: " + b.доступныеСпец(0));
        b.игратьДо(2, 0, CardBench.вариант("spec", "СПЕЦ (2 МОН)"));
        assertTrue(b.событий("ability_spec") > 0,
            "движок не сообщил о применении СПЕЦ. " + b.сводка());
    }

    /** Без двух монет СПЕЦ начальной №6 не предлагается. */
    @Test
    void безДвухМонетЗаменаНеПредлагается() {
        var b = CardBench.партия(4)
            .арсеналУстановлен(0, "bs7_6")
            .войско(0, null, UnitType.INFANTRY)
            .монеты(0, 1);
        assertFalse(b.доступныеСпец(0).stream().anyMatch(v -> v.startsWith(
                "ability:spec_swap_ground_unit")),
            "СПЕЦ предложен при одной монете: " + b.доступныеСпец(0));
    }
}
