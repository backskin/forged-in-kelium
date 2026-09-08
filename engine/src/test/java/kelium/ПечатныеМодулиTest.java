package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.engine.BlockStamp;
import kelium.report.BlockArt;

/**
 * ПЕЧАТНЫЕ МОДУЛИ ПОЛЯ — картинки настоящего картона и якорь к ним.
 *
 * <p>Что проверяется машинно: нарисованы ВСЕ двадцать сторон (потерять одну
 * легко — поле тогда наполовину останется схемой), якорь прочитался, и печать
 * нарисована ПОД ТУ ЖЕ версию набора блоков, которую играет свод. Последнее
 * важнее всего: положить арт одной версии на набор другой — значит показывать
 * одни контейнеры, а отдавать другие.
 *
 * <p>Сходится ли арт с набором ГЕКС В ГЕКС — проверяет генератор
 * {@code tools/gen_block_art.py}: он смотрит на сами пиксели.
 */
class ПечатныеМодулиTest {

    @Test
    void нарисованыВсеДвадцатьСторон() {
        assertNotNull(BlockArt.art(), "якорь печатных модулей не прочитался");
        for (int n = 1; n <= 5; n++) {
            for (String side : new String[]{"A", "B"}) {
                assertNotNull(BlockArt.face("Б" + n, side),
                    "нет картинки большого блока Б" + n + "-" + side);
                assertNotNull(BlockArt.face("М" + n, side),
                    "нет картинки малого блока М" + n + "-" + side);
            }
        }
    }

    @Test
    void якорьРазумный() {
        BlockArt.Anchor a = BlockArt.art();
        assertNotNull(a);
        assertTrue(a.artW() > 200 && a.artH() > 200, "картинка не крошечная");
        assertTrue(a.hex() > 20 && a.hex() < a.artW(),
            "радиус гекса на картинке: " + a.hex());
        assertTrue(a.ox() > 0 && a.ox() < a.artW() * 1.5
                && a.oy() > 0 && a.oy() < a.artH() * 1.5,
            "точка привязки где-то на картинке, а не за тридевять земель");
        assertTrue(a.rot() >= 0 && a.rot() < 6, "поворот печати 0..5");
    }

    @Test
    void артПодТуЖеВерсиюНабора() {
        GameConfig cfg = GameConfig.buildCached(GameConfig.DEFAULT_RULESET, 4, 1L, null, null);
        String играем = String.valueOf(cfg.ruleset.get(
            "content_versions.blocks", BlockStamp.ВЕРСИЯ_ПО_УМОЛЧАНИЮ));
        assertEquals(играем, BlockArt.art().blocks(),
            "печатные модули нарисованы под набор " + BlockArt.art().blocks()
                + ", а свод играет " + играем + ": на поле будут одни контейнеры, "
                + "а отдаваться другие. Либо перерисовать арт, либо сменить набор в своде.");
        assertTrue(BlockArt.matches(играем), "и matches() того же мнения");
    }

    @Test
    void поворотКартинкиСчитаетсяОтЯкоря() {
        int свой = BlockArt.art().rot();
        assertEquals(0.0, BlockArt.rotationDeg(свой), 1e-9,
            "картонка, положенная тем же поворотом, что напечатана, не крутится");
        assertEquals(60.0, BlockArt.rotationDeg(свой + 1), 1e-9,
            "шаг укладки — 60°");
    }
}
