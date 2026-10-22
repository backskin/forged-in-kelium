package kelium.gui.replay2;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * СНИМОК СПРАВОЧНИКА ПРАВИЛ — прогонщик для разработки интерфейса.
 *
 * <p>Открывает справочник правил за краем экрана (как из светлого «Штаба»: тема
 * до открытия светлая — окно всё равно обязано быть в палитре стола) и снимает
 * два кадра: оглавление с главой и поиск по слову с подсветкой.
 *
 * <p>Запуск: {@code RulesBookShot <префикс> [слово] [строка дерева] [номер места]
 * [ширина] [высота]} — пишет {@code <префикс>-глава.png} и {@code <префикс>-поиск.png}.
 */
public final class RulesBookShot {

    private RulesBookShot() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("kelium.gui.offscreen", "true");
        String prefix = args.length > 0 ? args[0] : "rules";
        String word = args.length > 1 ? args[1] : "трофей";
        int row = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        int pick = args.length > 3 ? Integer.parseInt(args[3]) : 2;
        int w = args.length > 4 ? Integer.parseInt(args[4]) : 1400;
        int h = args.length > 5 ? Integer.parseInt(args[5]) : 900;

        SwingUtilities.invokeAndWait(() -> {
            Theme.apply(false);
            HelpWindow.showRules(null);
            HelpWindow.rulesFrameForTest().setSize(w, h);
            HelpWindow.rulesFrameForTest().validate();
        });
        settle();
        SwingUtilities.invokeAndWait(() -> HelpWindow.rulesForTest().selectRowForTest(row));
        settle();
        shoot(prefix + "-глава.png", w, h);

        SwingUtilities.invokeAndWait(() -> HelpWindow.rulesForTest().searchForTest(word));
        settle();
        SwingUtilities.invokeAndWait(() -> HelpWindow.rulesForTest().pickHitForTest(pick));
        settle();
        shoot(prefix + "-поиск.png", w, h);
        HelpWindow rw = HelpWindow.rulesForTest();
        System.out.println("найдено мест: " + rw.hitCountForTest()
            + ", подсвечено в статье: " + rw.highlightCountForTest());
        System.exit(0);
    }

    /** Дать Swing разложить окно и догрузить картинки статьи. */
    private static void settle() throws Exception {
        for (int i = 0; i < 6; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(250);
        }
    }

    private static void shoot(String out, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = HelpWindow.rulesFrameForTest();
            f.getRootPane().validate();
            Graphics2D g = img.createGraphics();
            f.getRootPane().paint(g);
            g.dispose();
        });
        ImageIO.write(img, "png", new File(out));
        System.out.println("снимок: " + out);
    }
}
