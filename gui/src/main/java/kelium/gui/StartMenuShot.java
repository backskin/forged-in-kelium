package kelium.gui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * СНИМОК СТАРТОВОГО МЕНЮ В PNG — для проверки вёрстки без рук.
 *
 * <p>Запуск: {@code StartMenuShot <out.png> [ширина] [высота]}.
 */
public final class StartMenuShot {

    private StartMenuShot() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("kelium.gui.offscreen", "true");
        String out = args[0];
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 1500;
        int h = args.length > 2 ? Integer.parseInt(args[2]) : 950;
        StartMenuWindow[] win = new StartMenuWindow[1];
        SwingUtilities.invokeAndWait(() -> {
            win[0] = new StartMenuWindow();
            win[0].start();
            win[0].frame.setSize(w, h);
        });
        Thread.sleep(2500);
        // -Dshot.seat=N — сесть на место N (поворот ЦУ ещё не выбран)
        if (System.getProperty("shot.seat") != null) {
            SwingUtilities.invokeAndWait(() -> {
                win[0].mySeat = Integer.parseInt(System.getProperty("shot.seat"));
                win[0].rebuildPreview();
            });
            Thread.sleep(500);
        }
        // -Dshot.gallery=1 — снять открытую галерею полей, дождавшись картинок
        if (System.getProperty("shot.gallery") != null) {
            SwingUtilities.invokeAndWait(() -> win[0].openGallery());
            for (int i = 0; i < 600 && win[0].gallery.readyCount() < win[0].gallery.tileCount(); i++) {
                Thread.sleep(100);
            }
            Thread.sleep(300);
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            win[0].frame.getRootPane().validate();
            Graphics2D g = img.createGraphics();
            win[0].frame.getRootPane().paint(g);
            g.dispose();
        });
        ImageIO.write(img, "png", new File(out));
        System.exit(0);
    }
}
