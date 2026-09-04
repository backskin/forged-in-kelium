package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kelium.agents.Genome;
import kelium.dataio.GameConfig;
import kelium.rules.Ruleset;

/**
 * СТОРОЖ ЯКОРЕЙ ОЦЕНКИ: веса бота обязаны соответствовать курсу очков СВОДА.
 *
 * <p>ЗАЧЕМ ЭТОТ ТЕСТ СУЩЕСТВУЕТ. 15.08.2026 свод поставил {@code kelium_per_vp:
 * 0} — келемий в хранилище перестал давать победные очки. Веса оценки при этом
 * остались от версии, где он давал очко, и девять дней боты копили то, что не
 * считается: келемий (0 ПО) стоял в оценке дороже трофея (0.5 ПО). Поймано это
 * было не тестом, а разбором с дизайнером.
 *
 * <p>ЧТО ПРОВЕРЯЕТСЯ. Не сами веса — их вправе менять отбор, — а КУРСЫ СВОДА, из
 * которых веса выведены. Если курс изменился, тест падает и требует пересчитать
 * якоря (вывод расписан в {@link Genome#defaults()}). Тест намеренно не считает
 * веса заново из курсов: тогда он проверял бы сам себя.
 */
class ЯкоряОценкиTest {

    /** Столько «стоит» одно победное очко в линейной оценке (eval.vp). */
    private static final double ЕДИНИЦА = 6.0;

    private static Ruleset свод() {
        return GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null).ruleset;
    }

    @Test
    @DisplayName("курсы очков свода — те, из которых выведены якоря оценки")
    void курсыСводаНеИзменились() {
        Ruleset rs = свод();
        assertEquals(0, rs.getInt("economy.kelium_per_vp", -1),
            "КЕЛЕМИЙ снова даёт победные очки — пересчитай eval.kelium и "
            + "plan.value.kelium: сейчас они выведены из нулевого курса");
        assertEquals(5, rs.getInt("economy.coins_per_vp", -1),
            "ИЗМЕНИЛСЯ курс монет — пересчитай eval.coin");
        assertEquals(4, rs.getInt("economy.buildings_per_vp", -1),
            "ИЗМЕНИЛСЯ курс зданий на поле — пересчитай eval.buildings");
        assertEquals(4, rs.getInt("economy.units_per_vp", -1),
            "ИЗМЕНИЛСЯ курс войск на поле — пересчитай eval.units");
        assertEquals(java.util.List.of(1, 1, 2, 3), rs.getIntList("tech.step_vp_cumulative"),
            "ИЗМЕНИЛАСЬ лестница трека науки — пересчитай eval.tech_steps. "
            + "Первый шаг ОБЯЗАН давать очко: обнуление входа убивает треки "
            + "целиком (замер 24.08.2026: доля треков в счёте 10% против 40%)");
        assertEquals(0.5,
            ((Number) rs.get("economy.trophy_storage_vp_per_unit", 0)).doubleValue(), 1e-9,
            "ИЗМЕНИЛСЯ курс трофея — пересчитай eval.trophy_pool");
    }

    @Test
    @DisplayName("якоря оценки равны курсу очков, умноженному на единицу")
    void якоряСоответствуютКурсам() {
        Genome g = Genome.defaults();
        // Считается как ПО-за-единицу × ЕДИНИЦА. Скидки на конвертацию (келемий,
        // боеприпас) здесь не проверяются: это оценка риска, а не курс.
        assertEquals(0.2 * ЕДИНИЦА, g.get("eval.coin", 0), 1e-9,
            "монета: 1/5 ПО × " + ЕДИНИЦА);
        assertEquals(0.5 * ЕДИНИЦА, g.get("eval.trophy_pool", 0), 1e-9,
            "трофей: 0.5 ПО × " + ЕДИНИЦА);
        assertEquals(0.25 * ЕДИНИЦА, g.get("eval.units", 0), 1e-9,
            "войско на поле: 1/4 ПО × " + ЕДИНИЦА);
        assertEquals(0.25 * ЕДИНИЦА, g.get("eval.buildings", 0), 1e-9,
            "здание на поле: 1/4 ПО × " + ЕДИНИЦА);
        assertEquals(3.0 * ЕДИНИЦА, g.get("eval.cu_tokens", 0), 1e-9,
            "жетон уничтожения ЦУ: 3 ПО × " + ЕДИНИЦА);
    }

    @Test
    @DisplayName("то, что считается на подсчёте, ценится выше того, что не считается")
    void считаемоеВышеНесчитаемого() {
        Genome g = Genome.defaults();
        // ГЛАВНАЯ ПРОВЕРКА. Трофей даёт пол-очка сам по себе, келемий — ноль;
        // значит трофей обязан стоить дороже. Именно это соотношение и было
        // перевёрнуто (0.7 против 0.9), и именно оно уводило ботов от войны.
        double трофей = g.get("eval.trophy_pool", 0);
        double келемий = g.get("eval.kelium", 0);
        org.junit.jupiter.api.Assertions.assertTrue(трофей > келемий,
            "трофей (0.5 ПО) должен цениться выше келемия (0 ПО), а сейчас "
            + трофей + " против " + келемий);
        org.junit.jupiter.api.Assertions.assertTrue(
            g.get("plan.value.army", 0) > g.get("plan.value.kelium", 0),
            "цель «армия» должна стоять выше цели «копить келемий»");
    }
}
