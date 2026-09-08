package kelium.gui.dev;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * СНИМОК КАТАЛОГА МЕШОЧКА МОДУЛЕЙ — без живого окна.
 *
 * <p>Проверять надо ровно одно: печатные жетоны сели в карточки, обычная и
 * золотая стороны РАЗНЫЕ, и описание рядом соответствует картинке. Мышью это
 * проверять нельзя (правило 30.08.2026), поэтому сетка собирается в памяти.
 *
 * <p>Запуск: {@code kelium.gui.dev.СнимокМешочка [папка] [красный|синий] [обычный|золото]}
 */
public final class СнимокМешочка {

    private СнимокМешочка() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : ".");
        boolean красный = args.length < 2 || !"синий".equals(args[1]);
        boolean золото = args.length > 2 && "золото".equals(args[2]);
        int w = args.length > 3 ? Integer.parseInt(args[3]) : 1000;
        int h = args.length > 4 ? Integer.parseInt(args[4]) : 1400;
        if (!dir.exists() && !dir.mkdirs()) {
            System.out.println("не смог создать папку " + dir);
            return;
        }
        kelium.dataio.Locations.applyDataFolder();
        final BufferedImage[] box = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            kelium.gui.replay2.Theme.apply(false);
            javax.swing.JComponent сетка =
                kelium.gui.replay2.ModuleBagWindow.панель(null, красный, золото);
            javax.swing.JPanel корень = new javax.swing.JPanel(new java.awt.BorderLayout());
            корень.add(сетка);
            корень.setSize(w, h);
            сетка.setSize(w, h);
            for (int i = 0; i < 3; i++) {
                разложить(корень);
            }
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            var g = img.createGraphics();
            // Тексты в снимке без сглаживания слипаются: буквы наезжают, и
            // «ряд атаки» читается как «рядатаки». Окно берёт эти настройки у
            // системы, а снимок обязан назвать их сам.
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(kelium.gui.replay2.Theme.bg());
            g.fillRect(0, 0, w, h);
            сетка.printAll(g);
            g.dispose();
            box[0] = img;
        });
        File out = new File(dir, "мешочек-" + (красный ? "красный" : "синий")
            + (золото ? "-золото" : "") + ".png");
        ImageIO.write(box[0], "png", out);
        System.out.println("снято: " + out.getAbsolutePath());
    }

    private static void разложить(java.awt.Component c) {
        c.doLayout();
        if (c instanceof java.awt.Container k) {
            for (java.awt.Component ch : k.getComponents()) {
                разложить(ch);
            }
        }
    }
}
