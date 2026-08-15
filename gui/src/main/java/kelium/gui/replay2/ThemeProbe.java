package kelium.gui.replay2;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * ThemeProbe — сколько красок остаётся в ПРЕЖНЕЙ теме после переключения.
 *
 * <p>Считает компоненты, чей фон или текст совпадает с палитрой противоположной
 * темы: именно это дизайнер видит как «фон остался старым до перезапуска».
 */
public final class ThemeProbe {

    private ThemeProbe() {
    }

    public static void main(String[] args) throws Exception {
        boolean startDark = args.length == 0 || !"light".equals(args[0]);
        kelium.dataio.Locations.applyDataFolder();
        Theme.apply(startDark);

        Object gui = build();
        Thread.sleep(2500);

        System.out.println("до переключения (" + (startDark ? "тёмная" : "светлая")
            + "): " + report(frameOf(gui)));

        Method set = gui.getClass().getDeclaredMethod("setDarkTheme", boolean.class);
        set.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                set.invoke(gui, !startDark);     // переключаем на другую
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(800);

        System.out.println("после переключения (" + (startDark ? "светлая" : "тёмная")
            + "): " + report(frameOf(gui)));

        if (args.length > 1) {
            JFrame f = frameOf(gui);
            int w = 1366;
            int h = 768;
            SwingUtilities.invokeAndWait(() -> {
                f.setSize(w, h);
                f.validate();
            });
            Thread.sleep(600);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            SwingUtilities.invokeAndWait(() -> f.paint(g));
            g.dispose();
            javax.imageio.ImageIO.write(img, "png", new java.io.File(args[1]));
            System.out.println("снято: " + args[1]);
        }
        System.exit(0);
    }

    private static Object build() throws Exception {
        Object[] box = new Object[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                var ctor = Replay2Gui.class.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object g = ctor.newInstance();
                Method show = Replay2Gui.class.getDeclaredMethod("show");
                show.setAccessible(true);
                show.invoke(g);
                Method preview = Replay2Gui.class.getDeclaredMethod("preview");
                preview.setAccessible(true);
                preview.invoke(g);
                box[0] = g;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return box[0];
    }

    private static JFrame frameOf(Object gui) throws Exception {
        Field f = Replay2Gui.class.getDeclaredField("frame");
        f.setAccessible(true);
        return (JFrame) f.get(gui);
    }

    /** Сколько компонентов держат краску ПРОТИВОПОЛОЖНОЙ темы и какие именно. */
    private static String report(JFrame frame) {
        List<String> bad = new ArrayList<>();
        scan(frame.getContentPane(), bad);
        StringBuilder sb = new StringBuilder("чужих красок " + bad.size());
        for (int i = 0; i < Math.min(12, bad.size()); i++) {
            sb.append("\n    ").append(bad.get(i));
        }
        return sb.toString();
    }

    private static void scan(Component c, List<String> bad) {
        check(c, c.getBackground(), "фон", bad);
        check(c, c.getForeground(), "текст", bad);
        if (c instanceof Container box) {
            for (Component ch : box.getComponents()) {
                scan(ch, bad);
            }
        }
    }

    private static void check(Component c, Color col, String what, List<String> bad) {
        if (col instanceof javax.swing.plaf.UIResource) {
            return;
        }
        Color swap = Theme.counterpart(col);
        if (swap != null) {
            bad.add(c.getClass().getSimpleName() + " · " + what + " "
                + String.format("#%06X", col.getRGB() & 0xFFFFFF));
        }
    }
}
