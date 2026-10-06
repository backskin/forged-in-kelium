package kelium.gui.cardshop;

import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Снимок окна мастерской в PNG: {@code CardShopShot <out.png> [picker]} — с
 * {@code picker} ещё и снимок окна выбора иконок ({@code out-picker.png}).
 */
public final class CardShopShot {

    private CardShopShot() {
    }

    public static void main(String[] args) throws Exception {
        CardShop.main(new String[0]);
        Thread.sleep(3000);
        if (System.getProperty("shot.type") != null) {
            CardShop.debugType(System.getProperty("shot.type"));
            Thread.sleep(2500);
        }
        if (System.getProperty("shot.element") != null) {
            CardShop.debugSelect(System.getProperty("shot.element"));
            Thread.sleep(1500);
        }
        JFrame f = null;
        for (java.awt.Frame fr : java.awt.Frame.getFrames()) {
            if (fr instanceof JFrame j && fr.isVisible()) {
                f = j;
            }
        }
        shoot(f, new File(args[0]));
        if (args.length > 1) {
            JFrame ff = f;
            SwingUtilities.invokeAndWait(() -> IconPicker.open(ff, new CardAssets(), "Выбрать иконку",
                k -> { }));
            Thread.sleep(2500);
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isVisible()) {
                    shoot(d, new File(args[0].replace(".png", "-picker.png")));
                }
            }
        }
        System.exit(0);
    }

    private static void shoot(Window w, File out) throws Exception {
        BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = img.createGraphics();
            w.paint(g);
            g.dispose();
        });
        ImageIO.write(img, "png", out);
    }
}
