package kelium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;
import kelium.gui.replay2.PlayerStrip;
import kelium.gui.replay2.Session;
import kelium.gui.replay2.Theme;
import kelium.report.ReplayRecord;

/**
 * ПОБЕДНЫЕ ОЧКИ ВИДНЫ ПРИ ЛЮБОЙ ШИРИНЕ ПОЛОСЫ.
 *
 * <p>ЗАЧЕМ. Победные очки — главное число полосы: по нему смотрят, кто ведёт.
 * В верхнем ряду с ним соседствуют плашка с именем бота и две кнопки, и все они
 * растут от содержимого: длинное имя игрока сдвигает кнопки вправо, а те
 * затирают число. На широкой полосе этого не видно, поэтому проверка идёт на
 * УЗКИХ — там, где места действительно не хватает.
 *
 * <p>КАК ПРОВЕРЯЕТСЯ. Считаются пиксели цвета очков в правом верхнем углу и
 * сравниваются с тем же счётом на заведомо широкой полосе. Затёртое или
 * обрезанное число даёт заметно меньше краски. Это грубая мера — зато она не
 * ломается от смещения на пару точек и не требует знать раскладку.
 */
class PlayerStripTopRowTest {

    private static final int H = 116;

    private static int pointsPixels(Session session, int seat, int w) {
        PlayerStrip strip = new PlayerStrip(session, seat);
        strip.setSize(w, H);
        BufferedImage img = new BufferedImage(w, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, H);
        strip.paint(g);
        g.dispose();

        int want = Theme.points().getRGB();
        int n = 0;
        for (int y = 0; y < Math.min(H, Theme.px(26)); y++) {
            for (int x = (int) (w * 0.55); x < w; x++) {
                if (img.getRGB(x, y) == want) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void числоОчковНеЗатираетсяКнопками() {
        ReplayRecord rec = GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 4242,
            List.of("trained:hawk", "trained:dove", "trained:balanced", "search:balanced"),
            null);
        Session session = new Session();
        session.setRecord(rec);
        session.seek(rec.frames.size() * 3 / 4);

        for (int seat = 0; seat < 4; seat++) {
            int wide = pointsPixels(session, seat, 700);
            assertTrue(wide > 20,
                "на широкой полосе место " + seat + " не нарисовало очки вовсе: " + wide);
            for (int w : new int[]{320, 380, 460}) {
                int got = pointsPixels(session, seat, w);
                assertTrue(got >= wide * 0.6,
                    "место " + seat + ", ширина " + w + ": очки затёрты или обрезаны — "
                        + got + " точек краски против " + wide + " на широкой полосе");
            }
        }
    }
}
