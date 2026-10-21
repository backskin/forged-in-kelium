package kelium.gui.dev;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import kelium.gui.HotSeatWindow;
import kelium.gui.replay2.Theme;

/**
 * СНИМОК ЛОББИ СЕТЕВОГО СТОЛА — без живого окна (правило 30.08.2026:
 * прогонщики не показывают окно и не забирают фокус, экран рисуется в память).
 *
 * <p>Стол на время снимка поднимается по-настоящему, на свободном порту, и
 * сразу закрывается: лобби показывает рассадку, а её неоткуда взять, кроме
 * настоящего стола.
 *
 * <p>Запуск: {@code kelium.gui.dev.СнимокЛобби [папка]}
 */
public final class СнимокЛобби {

    private СнимокЛобби() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        if (!dir.exists() && !dir.mkdirs()) {
            System.out.println("папка не создалась: " + dir);
            return;
        }
        Theme.apply(false);

        // ВСЁ ДЕЛАЕТСЯ НА ПОТОКЕ СОБЫТИЙ. Лобби живое: рассадка приходит из
        // сети и перерисовывается через очередь событий. Раскладывать те же
        // панели из main параллельно нельзя — главный поток и поток событий
        // берут замки раскладки в разном порядке и встают намертво (это
        // случилось на первом же прогоне).
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                // Стол на четверых: я, бот и два места по сети — так видно все
                // три вида строк рассадки разом.
                HotSeatWindow.Options настройки = HotSeatWindow.Options.simple(4, 4242L,
                    List.of("human", "builder:2", "human", "human"));
                снять(kelium.gui.net.ЛоббиДляСнимка.хозяин(настройки, 0),
                    new File(dir, "лобби-хозяин.png"));
                снять(kelium.gui.net.ЛоббиДляСнимка.адрес(),
                    new File(dir, "лобби-подключение.png"));
            } catch (Exception e) {
                throw new IllegalStateException("снимок лобби не вышел", e);
            }
        });
        System.out.println("снимки лобби: " + dir.getAbsolutePath());
        System.exit(0);
    }

    private static void снять(JPanel панель, File файл) throws Exception {
        Dimension d = панель.getPreferredSize();
        панель.setSize(d);
        панель.doLayout();
        разложить(панель);
        BufferedImage img = new BufferedImage(Math.max(1, d.width), Math.max(1, d.height),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
        панель.paint(g);
        g.dispose();
        ImageIO.write(img, "png", файл);
    }

    /** Разложить дерево целиком: без этого вложенные панели нулевого размера. */
    private static void разложить(java.awt.Container c) {
        c.doLayout();
        for (java.awt.Component ch : c.getComponents()) {
            if (ch instanceof java.awt.Container cc) {
                разложить(cc);
            }
        }
    }
}
