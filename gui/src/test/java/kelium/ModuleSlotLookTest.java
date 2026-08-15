package kelium;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import kelium.report.ReplayRecord;

/**
 * ВЫКЛАДКА ВСЕХ СОСТОЯНИЙ МЕСТА ПОД МОДУЛЬ. Партия показывает лишь то, что в ней
 * случайно выпало: позолочённого жетона может не быть вовсе, и тогда золотая
 * сторона остаётся непроверенной. Здесь состояния выставлены руками — пустое
 * место, обычная сторона, позолочённая, обе стороны жетона хранилища, — и обе
 * темы рядом.
 *
 * <p>Главное, что проверяется глазами: <b>золотой синий и золотой красный не
 * должны выглядеть одинаково</b>. Ради этого золото и рисуется каймой поверх
 * своего цвета, а не заливкой.
 */
class ModuleSlotLookTest {

    @Test
    void everyModuleStateIsDistinguishable() throws Exception {
        Path dir = Path.of("target", "sheet-shots");
        Files.createDirectories(dir);
        kelium.gui.replay2.Theme.apply(true);
        ImageIO.write(sheet(), "png", dir.resolve("module-states-dark.png").toFile());
        kelium.gui.replay2.Theme.apply(false);
        ImageIO.write(sheet(), "png", dir.resolve("module-states-light.png").toFile());
        kelium.gui.replay2.Theme.apply(true);
        System.out.println("[states] " + dir.toAbsolutePath());
    }

    private static BufferedImage sheet() throws Exception {
        int w = 700;
        int h = 260;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg());
        g.fillRect(0, 0, w, h);

        String[] captions = {"пусто", "обычная", "ПОЗОЛОТА"};
        int side = 34;
        int y = 60;
        row(g, "красный (атака рода)", y, side, false);
        row(g, "синий (сборка здания)", y + 70, side, true);
        storeRow(g, y + 140, side);

        g.setFont(new Font("Dialog", Font.BOLD, 12));
        g.setColor(ink());
        for (int i = 0; i < captions.length; i++) {
            g.drawString(captions[i], 240 + i * 90, 40);
        }
        g.dispose();
        return img;
    }

    private static void row(Graphics2D g, String title, int y, int side, boolean blue)
            throws Exception {
        g.setFont(new Font("Dialog", Font.PLAIN, 13));
        g.setColor(ink());
        g.drawString(title, 20, y + side / 2 + 5);
        Color colour = blue ? call("blue") : call("red");
        paint(g, null, colour, 240, y, side);
        paint(g, mod(blue ? "C3" : "R1-2", false), colour, 330, y, side);
        paint(g, mod(blue ? "C3" : "R1-2", true), colour, 420, y, side);
    }

    private static void storeRow(Graphics2D g, int y, int side) throws Exception {
        g.setFont(new Font("Dialog", Font.PLAIN, 13));
        g.setColor(ink());
        g.drawString("жетон хранилища", 20, y + side / 2 + 5);
        var m = Class.forName("kelium.gui.replay2.ModuleSlot")
            .getDeclaredMethod("paintStorageToken", Graphics2D.class, String.class,
                double.class, double.class, double.class);
        m.setAccessible(true);
        m.invoke(null, g, null, 240.0, (double) y, (double) side);
        m.invoke(null, g, "+1_universal_cell", 330.0, (double) y, (double) side);
        m.invoke(null, g, "+1_energy", 420.0, (double) y, (double) side);
        g.setFont(new Font("Dialog", Font.PLAIN, 11));
        g.setColor(ink());
        g.drawString("склад", 330, y + side + 14);
        g.drawString("энергия", 420, y + side + 14);
    }

    private static ReplayRecord.Module mod(String id, boolean gold) {
        ReplayRecord.Module m = new ReplayRecord.Module();
        m.id = id;
        m.gold = gold;
        return m;
    }

    private static void paint(Graphics2D g, ReplayRecord.Module m, Color c,
                              double x, double y, double side) throws Exception {
        var pm = Class.forName("kelium.gui.replay2.ModuleSlot")
            .getDeclaredMethod("paint", Graphics2D.class, ReplayRecord.Module.class,
                Color.class, double.class, double.class, double.class);
        pm.setAccessible(true);
        pm.invoke(null, g, m, c, x, y, side);
    }

    private static Color call(String name) throws Exception {
        var m = Class.forName("kelium.gui.replay2.ModuleSlot").getDeclaredMethod(name);
        m.setAccessible(true);
        return (Color) m.invoke(null);
    }

    private static Color bg() {
        return kelium.gui.replay2.Theme.isDark() ? new Color(0x1B1E24)
            : new Color(0xF2F3F5);
    }

    private static Color ink() {
        return kelium.gui.replay2.Theme.isDark() ? new Color(0xD8DCE3)
            : new Color(0x2A2D33);
    }
}
