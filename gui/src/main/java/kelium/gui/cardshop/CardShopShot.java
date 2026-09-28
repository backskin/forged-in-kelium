package kelium.gui.cardshop;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Снимок окна мастерской в PNG — для проверки вёрстки: {@code CardShopShot <out.png>}. */
public final class CardShopShot {

    private CardShopShot() {
    }

    public static void main(String[] args) throws Exception {
        CardShop.main(new String[0]);
        Thread.sleep(2500);
        JFrame[] f = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            for (java.awt.Frame fr : java.awt.Frame.getFrames()) {
                if (fr instanceof JFrame j && fr.isVisible()) {
                    f[0] = j;
                }
            }
        });
        Thread.sleep(1500);
        BufferedImage img = new BufferedImage(f[0].getWidth(), f[0].getHeight(),
            BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = img.createGraphics();
            f[0].getRootPane().paint(g);
            g.dispose();
        });
        ImageIO.write(img, "png", new File(args[0]));
        System.exit(0);
    }
}
