package kelium.gui.dev;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import kelium.gui.replay2.BoardSheet;
import kelium.gui.replay2.Session;
import kelium.report.ReplayRecord;

/**
 * СНИМОК ЛИЧНОЙ ЗОНЫ ИГРОКА — без живого окна (правило 30.08.2026: прогонщики
 * не забирают фокус, окно не показываем, а рисуем в память).
 *
 * <p>Нужно, чтобы смотреть на печатные планшеты с живым поверх: жетоны
 * добытчиков и энергостанций лежат прямо на картинке планшета хранилища,
 * повёрнутые, как на столе, и отдельных дублирующих групп на листе больше нет.
 *
 * <p>Запуск: {@code kelium.gui.dev.СнимокЛичнойЗоны <файл записи> [папка] [место] [ширина]}
 */
public final class СнимокЛичнойЗоны {

    private СнимокЛичнойЗоны() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("нужен файл записи партии");
            return;
        }
        ReplayRecord rec = ReplayRecord.load(java.nio.file.Path.of(args[0]));
        File dir = new File(args.length > 1 ? args[1] : ".");
        int seat = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        int w = args.length > 3 ? Integer.parseInt(args[3]) : 980;

        Session session = new Session();
        session.setRecord(rec);
        // Середина партии: часть зданий уже на поле, часть ещё лежит на
        // планшете — видно и жетоны поверх ячеек, и открытые ячейки с ресурсами.
        session.seek(rec.frames.size() / 2);

        BoardSheet sheet = new BoardSheet(session, seat);
        sheet.setSize(w, 3000);
        sheet.doLayout();
        // ПЕРВЫЙ ПРОХОД ВХОЛОСТУЮ: лист считает свою высоту по содержимому во
        // время рисования, и до первой отрисовки предпочтительный размер ещё
        // не знает, что на нём поместилось.
        BufferedImage warm = new BufferedImage(w, 3000, BufferedImage.TYPE_INT_RGB);
        Graphics2D wg = warm.createGraphics();
        sheet.paint(wg);
        wg.dispose();
        int h = Math.max(600, sheet.getPreferredSize().height);
        sheet.setSize(w, h);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        sheet.paint(g);
        g.dispose();
        File out = new File(dir, "личная-зона-место" + seat + ".png");
        ImageIO.write(im, "png", out);
        System.out.println("снято: " + out.getAbsolutePath() + " " + w + "x" + h);
    }
}
