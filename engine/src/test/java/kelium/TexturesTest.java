package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import kelium.report.Textures;

/**
 * ТЕКСТУРЫ ЖЕТОНОВ: правило «нетронутая заготовка — это не текстура».
 *
 * <p>Заготовки лежат в той же папке и под теми же именами, что настоящие картинки
 * (иначе непонятно, по чему рисовать). Если бы приложение брало их как есть, на поле
 * вместо силуэтов появились бы плоские пятна — хуже, чем было. Поэтому при раскладке
 * заготовок пишется список отпечатков, и картинка с совпавшим отпечатком молча
 * пропускается.
 *
 * <p>Отпечаток считается ПО ПИКСЕЛЯМ: перезапись файла в редакторе (другая упаковка,
 * другие метаданные) не должна притворяться работой художника.
 */
class TexturesTest {

    @Test
    void untouchedStubIsIgnoredAndPaintedOneIsUsed() throws Exception {
        Path root = Files.createTempDirectory("kelium-textures");
        try {
            Path tokens = Files.createDirectories(root.resolve("token"));
            BufferedImage stub = flat(new Color(0x3B82D0));
            ImageIO.write(stub, "png", tokens.resolve("barracks_p1.png").toFile());

            BufferedImage painted = flat(new Color(0x3B82D0));
            Graphics2D g = painted.createGraphics();
            g.setColor(Color.YELLOW);
            g.fillRect(4, 4, 10, 10);          // «художник порисовал»
            g.dispose();
            ImageIO.write(painted, "png", tokens.resolve("barracks_p2.png").toFile());

            // список отпечатков: ОБА файла записаны как заготовки
            Files.write(root.resolve(Textures.MANIFEST), List.of(
                "# отпечатки заготовок",
                "barracks_p1.png " + Textures.fingerprint(stub),
                "barracks_p2.png " + Textures.fingerprint(stub)),
                StandardCharsets.UTF_8);

            Textures.useFolder(root);
            assertNull(Textures.building("barracks", null, 0),
                "нетронутая заготовка не должна подставляться вместо силуэта");
            assertNotNull(Textures.building("barracks", null, 1),
                "по изменённой картинке отпечаток не совпадает — это уже текстура");
            assertEquals(1, Textures.skippedStubs(), "пропущена ровно одна заготовка");
        } finally {
            Textures.useFolder(null);
        }
    }

    @Test
    void repackingTheSameImageDoesNotFakeWork() throws Exception {
        // Тот же рисунок, сохранённый заново: отпечаток обязан совпасть, иначе любое
        // открытие-закрытие файла в редакторе выдавало бы заготовку за текстуру.
        BufferedImage img = flat(new Color(0xE07038));
        Path tmp = Files.createTempFile("kelium-tex", ".png");
        try {
            ImageIO.write(img, "png", tmp.toFile());
            BufferedImage back = ImageIO.read(tmp.toFile());
            assertEquals(Textures.fingerprint(img), Textures.fingerprint(back),
                "отпечаток берётся с пикселей, а не с байтов файла");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void namesGoFromExactToGeneral() {
        List<String> names = Textures.names("miner", 3, 1);
        assertEquals("miner_l3_p2", names.get(0), "сначала уровень и место");
        assertTrue(names.contains("miner_p2"), "потом только место");
        assertTrue(names.contains("miner_l3"), "потом только уровень");
        assertEquals("miner", names.get(names.size() - 1), "в конце — общая на всех");
    }

    /** Однотонная картинка нужного цвета — заменитель заготовки в тесте. */
    private static BufferedImage flat(Color colour) {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(colour);
        g.fillRect(0, 0, 24, 24);
        g.dispose();
        return img;
    }
}
