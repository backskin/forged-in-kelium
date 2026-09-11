package kelium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import kelium.report.Zones;

/**
 * РАЗМЕТКА ЗОН НА ТЕКСТУРЕ: художник рисует плоскими цветами, программа сама
 * достаёт из этого места, размеры и УГЛЫ.
 *
 * <p>Главное, что здесь проверяется: повёрнутый квадрат ячейки читается вместе со
 * своим углом. Дизайнер просил именно этого — «квадраты могут быть повернуты», и
 * заставлять его указывать градусы отдельно было бы издевательством.
 */
class ZonesTest {

    @Test
    void rotatedSlotsKeepTheirAngle() {
        BufferedImage mask = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        // два квадрата: один ровный, второй повёрнут на 30°
        g.setColor(Color.RED);
        g.fillRect(20, 20, 40, 40);
        AffineTransform old = g.getTransform();
        g.translate(140, 60);
        g.rotate(Math.toRadians(30));
        g.fillRect(-20, -20, 40, 40);
        g.setTransform(old);
        g.dispose();

        Zones z = Zones.parse(mask);
        assertEquals(2, z.slots().size(), "две ячейки — два красных пятна");

        Zones.Slot flat = z.slots().get(0);
        Zones.Slot tilted = z.slots().get(1);
        assertTrue(Math.abs(flat.cx() - 40) < 3 && Math.abs(flat.cy() - 40) < 3,
            "центр первой ячейки там, где нарисован квадрат: " + flat);
        assertTrue(Math.abs(tilted.cx() - 140) < 4 && Math.abs(tilted.cy() - 60) < 4,
            "центр второй ячейки: " + tilted);
        double angle = Math.abs(((tilted.angleDeg() % 90) + 90) % 90);
        assertTrue(Math.abs(angle - 30) < 6 || Math.abs(angle - 60) < 6,
            "угол повёрнутой ячейки должен читаться как 30° (с точностью до "
                + "поворота квадрата на 90°), а получилось " + tilted.angleDeg());
    }

    @Test
    void areaAndLabelAreFound() {
        BufferedImage mask = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(0x00, 0x80, 0xFF));
        g.fillRect(10, 10, 60, 20);          // площадь появления энергии
        g.setColor(new Color(0x00, 0xC0, 0x00));
        g.fillOval(90, 55, 16, 16);          // место под подпись
        g.dispose();

        Zones z = Zones.parse(mask);
        assertNotNull(z.energyArea(), "синее пятно — площадь появления энергии");
        assertTrue(z.energyArea().w() > z.energyArea().h(),
            "площадь вытянута вдоль, значит главная ось найдена верно");
        double[] label = z.labelSpot();
        assertNotNull(label, "зелёное пятно — место под подпись");
        assertTrue(Math.abs(label[0] - 98) < 4 && Math.abs(label[1] - 63) < 4,
            "подпись встаёт в центр зелёного пятна, а получилось "
                + java.util.Arrays.toString(label));
    }

    /**
     * ПАЛИТРА ДИЗАЙНЕРА (11.09.2026). Он размечает своими цветами: жёлтым —
     * ячейки энергии, белым — гнездо гарнизона, оранжевым — площадь свободной
     * энергии. Прежние маски рисовала программа, и их палитра осталась в силе,
     * поэтому читаться обязаны обе.
     */
    @Test
    void designerPaletteIsUnderstood() {
        BufferedImage mask = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(0xEE, 0xEB, 0x00));
        g.fillRect(10, 10, 30, 30);          // ячейка под энергию
        g.setColor(new Color(0xFE, 0xFE, 0xFE));
        g.fillRect(100, 20, 50, 50);         // гнездо гарнизона
        g.setColor(new Color(0xF5, 0xC2, 0x55));
        g.fillRect(10, 80, 60, 20);          // площадь свободной энергии
        g.dispose();

        Zones z = Zones.parse(mask);
        assertEquals(1, z.slots().size(), "жёлтый квадрат — это ячейка под энергию");
        assertNotNull(z.energyArea(), "оранжевое пятно — площадь появления энергии");
        Zones.Area гнездо = z.garrison();
        assertNotNull(гнездо, "белое пятно — гнездо гарнизона");
        assertTrue(Math.abs(гнездо.cx() - 125) < 3 && Math.abs(гнездо.cy() - 45) < 3,
            "жетон войска ложится в середину нарисованного гнезда, а получилось "
                + гнездо);
        assertTrue(Math.abs(гнездо.w() - 50) < 3,
            "ширина гнезда берётся с маски: по ней жетон и подгоняется, а вышло "
                + гнездо.w());
    }

    /**
     * НАСТОЯЩИЕ МАСКИ ЖЕТОНОВ. Гнездо гарнизона напечатано у казарм, завода и
     * авиабазы — войска сидят только в них. У ЦУ его нет: внутрь ЦУ войска не
     * заходят, и лишнее гнездо увело бы жетон не туда.
     */
    @Test
    void printedGarrisonNestIsMarkedOnMilitaryBuildings() {
        java.nio.file.Path корень = java.nio.file.Path.of("..", "data", "textures");
        if (!java.nio.file.Files.isDirectory(корень.resolve("token"))) {
            return;                          // сборка без папки данных — не наше дело
        }
        Zones.forget();
        for (String ключ : new String[]{"barracks_p1", "factory_p2", "airbase_p3"}) {
            assertNotNull(Zones.of(ключ, корень).garrison(),
                "у жетона " + ключ + " должно быть размечено гнездо гарнизона");
        }
        assertEquals(null, Zones.of("command_center_p1", корень).garrison(),
            "внутрь ЦУ войска не заходят — гнезда гарнизона у него нет");
        assertEquals(2, Zones.of("factory_p2", корень).slots().size(),
            "у завода две ячейки энергии — столько же, сколько в boards");
        assertEquals(1, Zones.of("barracks_p1", корень).slots().size(),
            "у казарм одна ячейка энергии");
        Zones.forget();
    }

    @Test
    void emptyMaskMeansNoMarkup() {
        BufferedImage mask = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Zones z = Zones.parse(mask);
        assertTrue(z.isEmpty(), "пустая маска — это отсутствие разметки, а не ноль зон");
    }
}
