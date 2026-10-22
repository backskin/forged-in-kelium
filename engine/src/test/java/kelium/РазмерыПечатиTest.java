package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.report.РазмерыПечати;

/**
 * СТОРОЖ ТАБЛИЦЫ РАЗМЕРОВ: миллиметры в {@code data/components/sizes.yaml}
 * обязаны сходиться с самими картинками жетонов.
 *
 * <p>Таблица нужна затем, чтобы рисунки книги клали жетоны и карты в одном
 * масштабе. Ошибка в ней не падает и не видна на глаз — жетон просто выходит
 * не того размера, и заметит это только дизайнер. Поэтому проверка
 * механическая: размер картинки, делённый на единый масштаб экспорта, обязан
 * совпасть с записанным числом.
 *
 * <p>Три размера названы дизайнером прямо (пехота 18×18, техника 39×28,5,
 * добытчик и энергостанция 39×20) — они проверяются отдельно: на них
 * держится весь масштаб.
 */
class РазмерыПечатиTest {

    private static final double ДОПУСК_ММ = 0.2;

    @Test
    void таблицаЕстьИМасштабОбъявлен() {
        assertTrue(РазмерыПечати.есть(), "таблицы размеров нет в data/components");
        assertTrue(РазмерыПечати.точекНаМм() > 1, "масштаб экспорта не объявлен");
    }

    @Test
    void размерыДизайнераНаМесте() {
        проверить("infantry", 18.0, 18.0);
        проверить("vehicle", 39.0, 28.5);
        проверить("miner", 39.0, 20.0);
        проверить("power_plant", 39.0, 20.0);
    }

    private void проверить(String код, double ш, double в) {
        РазмерыПечати.Размер р = РазмерыПечати.жетон(код);
        assertNotNull(р, "нет размера жетона " + код);
        assertEquals(ш, р.ширина(), 0.01, код + ": ширина");
        assertEquals(в, р.высота(), 0.01, код + ": высота");
    }

    @Test
    void картинкиЖетоновСходятсяСТаблицей() throws Exception {
        Path токены = GameConfig.resolveDataRoot(null).resolve("textures").resolve("token");
        if (!Files.isDirectory(токены)) {
            return;                       // набора текстур нет — проверять нечего
        }
        double мм = РазмерыПечати.точекНаМм();
        List<String> расхождения = new ArrayList<>();
        try (var поток = Files.list(токены)) {
            for (Path f : поток.toList()) {
                String имя = f.getFileName().toString();
                if (!имя.endsWith(".png") || имя.contains(".zones")) {
                    continue;
                }
                String код = РазмерыПечати.корень(имя);
                РазмерыПечати.Размер р = РазмерыПечати.жетон(код);
                if (р == null) {
                    continue;             // не жетон игрока (модули, заглушки)
                }
                BufferedImage im = ImageIO.read(f.toFile());
                if (im == null) {
                    continue;
                }
                double ш = im.getWidth() / мм;
                double в = im.getHeight() / мм;
                if (Math.abs(ш - р.ширина()) > ДОПУСК_ММ
                        || Math.abs(в - р.высота()) > ДОПУСК_ММ) {
                    расхождения.add(String.format(
                        "%s: картинка %.2f×%.2f мм, таблица %.2f×%.2f",
                        имя, ш, в, р.ширина(), р.высота()));
                }
            }
        }
        assertTrue(расхождения.isEmpty(),
            "таблица размеров разошлась с картинками: " + расхождения);
    }
}
