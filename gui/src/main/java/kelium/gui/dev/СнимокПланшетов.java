package kelium.gui.dev;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import kelium.dataio.GameConfig;
import kelium.gui.BoardsPanel;
import kelium.report.ReplayRecord;

/**
 * СНИМОК ПЛАНШЕТОВ — проверка планшета науки и прочих досок БЕЗ живого окна.
 *
 * <p>Правило 30.08.2026: прогонщики не забирают фокус, окно не показываем, а
 * рисуем в память и складываем в PNG. Здесь это нужно, чтобы посмотреть на
 * перерисованный трек науки: кубики теперь занимают ячейки навсегда, а под
 * треками вместо «старта» стоит личный запас кубиков каждого игрока.
 *
 * <p>Запуск: {@code kelium.gui.dev.СнимокПланшетов <файл записи> [папка] [ширина] [высота]}
 */
public final class СнимокПланшетов {

    private СнимокПланшетов() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("нужен файл записи партии");
            return;
        }
        ReplayRecord rec = ReplayRecord.load(java.nio.file.Path.of(args[0]));
        File dir = new File(args.length > 1 ? args[1] : ".");
        int w = args.length > 2 ? Integer.parseInt(args[2]) : 1100;
        int h = args.length > 3 ? Integer.parseInt(args[3]) : 700;

        GameConfig cfg = GameConfig.buildCached(rec.ruleset, 4, 0L, null, null);
        BoardsPanel boards = new BoardsPanel();
        boards.setRules(cfg.ruleset, cfg.content);
        boards.setSize(w, h);

        // Последний кадр: к концу партии кубиков на треках больше всего, и
        // накопление видно лучше всего.
        boards.show(rec, rec.frames.get(rec.frames.size() - 1).snapshot);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        boards.paint(g);
        g.dispose();
        File out = new File(dir, "планшеты-конец.png");
        ImageIO.write(im, "png", out);
        System.out.println("снято: " + out.getAbsolutePath() + " (свод " + rec.ruleset + ")");
    }
}
