package kelium;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import kelium.dataio.GameConfig;
import kelium.gui.GameRecorder;
import kelium.gui.replay2.OrderCardsPanel;
import kelium.gui.replay2.Session;
import kelium.report.ReplayRecord;

/**
 * ПАНЕЛЬ КАРТ ПРИКАЗОВ — проверка на настоящей записи партии.
 *
 * <p>ЗАЧЕМ ИМЕННО ТАК. Панель целиком нарисованная: у неё нет дочерних
 * компонентов, которые можно опросить, и всё, что она делает, происходит в
 * {@code paintComponent}. Поэтому проверяем ровно то, что можно проверить
 * машинно: панель находит карты игрока в записи, рисует непустую картинку, и
 * подсказка под курсором действительно называет карту. Как это ВЫГЛЯДИТ, машина
 * судить не может — снимок кладётся в {@code target} для глаз дизайнера.
 */
class OrderCardsPanelTest {

    private static final int W = 420;
    private static final int H = 130;

    /** Кадр, где у игрока уже есть и разыгранные карты, и карты в руке. */
    private static int frameWithBoth(ReplayRecord rec, int seat) {
        for (int i = 0; i < rec.frames.size(); i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            if (f.snapshot == null || seat >= f.snapshot.players.size()) {
                continue;
            }
            ReplayRecord.Player p = f.snapshot.players.get(seat);
            if (!p.orderPlayed.isEmpty() && !p.orderHand.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static BufferedImage paint(OrderCardsPanel panel) {
        panel.setSize(W, H);
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x30, 0x80, 0x30));   // заведомо чужой фон
        g.fillRect(0, 0, W, H);
        panel.paint(g);
        g.dispose();
        return img;
    }

    /** Сколько пикселей отличается от подложенного фона — мера «нарисовано ли». */
    private static int painted(BufferedImage img) {
        int bg = new Color(0x30, 0x80, 0x30).getRGB();
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (img.getRGB(x, y) != bg) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void панельРисуетКартыИЗнаетЧтоПодКурсором() throws Exception {
        ReplayRecord rec = GameRecorder.play(GameConfig.DEFAULT_RULESET, 4, 4242,
            List.of("strat:hawk", "strat:dove", "explorer", "chaos"), null);

        int seat = -1;
        int frame = -1;
        for (int s = 0; s < 4 && frame < 0; s++) {
            frame = frameWithBoth(rec, s);
            seat = s;
        }
        assertTrue(frame >= 0,
            "в записи не нашлось кадра, где у игрока есть и стопка, и рука");

        Session session = new Session();
        session.setRecord(rec);
        session.seek(frame);
        OrderCardsPanel panel = new OrderCardsPanel(session, seat);

        BufferedImage open = paint(panel);
        int drawn = painted(open);
        assertTrue(drawn > W * H / 4,
            "раскрытая панель почти ничего не нарисовала: " + drawn + " пикселей");

        // ЗАКРЫТАЯ ПАНЕЛЬ НЕ РИСУЕТ НИЧЕГО. Это не косметика: панель лежит
        // ПОВЕРХ поля, и если в свёрнутом виде она продолжает закрашивать свой
        // прямоугольник, то прячется вместе с куском поля под ней.
        panel.setOpen(0);
        assertTrue(painted(paint(panel)) == 0,
            "свёрнутая панель всё равно закрасила поле под собой");
        // ПЕРЕРИСОВАТЬ ОБЯЗАТЕЛЬНО. Области карт для подсказок складываются В
        // МОМЕНТ РИСОВАНИЯ, а свёрнутая панель их стирает; вне окна repaint()
        // ничего не перерисовывает, поэтому без явного paint подсказок не будет.
        panel.setOpen(1);
        open = paint(panel);

        // ПОДСКАЗКА: хотя бы в одной точке панель называет карту. Перебор сеткой,
        // а не по известным координатам: раскладка карт — дело вида, и тест не
        // должен ломаться от того, что веер сдвинули на пару пикселей.
        String tip = null;
        for (int y = 8; y < H - 8 && tip == null; y += 6) {
            for (int x = 4; x < W - 4 && tip == null; x += 6) {
                tip = panel.getToolTipText(new MouseEvent(panel, MouseEvent.MOUSE_MOVED,
                    0L, 0, x, y, 0, false));
            }
        }
        assertNotNull(tip, "панель не смогла назвать ни одной карты под курсором");
        assertTrue(tip.contains("руке") || tip.contains("Разыграно") || tip.contains("Круг"),
            "подсказка не говорит, что это за карта: " + tip);

        Path dir = Path.of("target", "order-cards");
        Files.createDirectories(dir);
        ImageIO.write(open, "png", dir.resolve("panel-open.png").toFile());
    }
}
