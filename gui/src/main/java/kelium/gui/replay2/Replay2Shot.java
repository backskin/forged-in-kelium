package kelium.gui.replay2;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Replay2Shot — снимки разбора партии для ревью читаемости шрифтов.
 *
 * Args: outDir stageId scale winW winH [dark|light]
 */
public final class Replay2Shot {

    private Replay2Shot() {
    }

    public static void main(String[] args) throws Exception {
        String outFile = args[0];
        String stage = args[1];
        double scale = Double.parseDouble(args[2]);
        int w = Integer.parseInt(args[3]);
        int h = Integer.parseInt(args[4]);
        boolean dark = args.length < 6 || !"light".equals(args[5]);

        kelium.dataio.Locations.applyDataFolder();
        Theme.setUserScale(scale);
        Theme.apply(dark);

        Replay2Gui gui = new Replay2Gui();
        Method show = Replay2Gui.class.getDeclaredMethod("show");
        show.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                show.invoke(gui);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Method preview = Replay2Gui.class.getDeclaredMethod("preview");
        preview.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                preview.invoke(gui);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(400);

        if (!"field".equals(stage)) {
            Method showStage = Replay2Gui.class.getDeclaredMethod("showStage", String.class);
            showStage.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    showStage.invoke(gui, stage);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        var ff = Replay2Gui.class.getDeclaredField("frame");
        ff.setAccessible(true);
        JFrame frame = (JFrame) ff.get(gui);

        SwingUtilities.invokeAndWait(() -> {
            frame.setSize(w, h);
            frame.validate();
        });
        Thread.sleep(400);

        if (args.length > 6 && "debug".equals(args[6])) {
            var srf = Replay2Gui.class.getDeclaredField("stripsRow");
            srf.setAccessible(true);
            var ssf = Replay2Gui.class.getDeclaredField("stripsScroll");
            ssf.setAccessible(true);
            javax.swing.JPanel stripsRow = (javax.swing.JPanel) srf.get(gui);
            javax.swing.JScrollPane stripsScroll = (javax.swing.JScrollPane) ssf.get(gui);
            System.out.println("stripsRow.getSize()=" + stripsRow.getSize());
            System.out.println("stripsRow.getPreferredSize()=" + stripsRow.getPreferredSize());
            System.out.println("stripsScroll.getSize()=" + stripsScroll.getSize());
            System.out.println("stripsScroll.getViewport().getExtentSize()="
                + stripsScroll.getViewport().getExtentSize());
            System.out.println("stripsScroll.getViewport().getViewSize()="
                + stripsScroll.getViewport().getViewSize());
            System.out.println("hScrollBar.isVisible()="
                + stripsScroll.getHorizontalScrollBar().isVisible());
            System.out.println("hScrollBar.getVisibleAmount()/Max="
                + stripsScroll.getHorizontalScrollBar().getVisibleAmount() + "/"
                + stripsScroll.getHorizontalScrollBar().getMaximum());
            for (java.awt.Component c : stripsRow.getComponents()) {
                System.out.println("  strip: pref=" + c.getPreferredSize()
                    + " min=" + c.getMinimumSize() + " actual=" + c.getSize());
            }
            System.out.println("frame.getSize()=" + frame.getSize());
            System.out.println("frame.getMinimumSize()=" + frame.getMinimumSize());
            System.out.println("frame.getPreferredSize()=" + frame.getPreferredSize());
            var setupF = Replay2Gui.class.getDeclaredField("setup");
            setupF.setAccessible(true);
            java.awt.Component setup = (java.awt.Component) setupF.get(gui);
            System.out.println("setup.getSize()=" + setup.getSize());
            System.out.println("setup.getPreferredSize()=" + setup.getPreferredSize());
            System.out.println("setup.getMinimumSize()=" + setup.getMinimumSize());
            printWide(frame.getContentPane(), 0);
        }

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        SwingUtilities.invokeAndWait(() -> frame.paint(g));
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(outFile));
        System.out.println("saved: " + outFile);
        System.exit(0);
    }

    /** Найти всё, чей минимум шире 1300px — подозреваемые на распирание окна. */
    private static void printWide(java.awt.Component c, int depth) {
        if (c.getMinimumSize().width > 1300) {
            System.out.println("  ".repeat(depth) + "WIDE: " + c.getClass().getName()
                + " name=" + c.getName() + " min=" + c.getMinimumSize()
                + " pref=" + c.getPreferredSize());
        }
        if (c instanceof java.awt.Container ct) {
            for (java.awt.Component ch : ct.getComponents()) {
                printWide(ch, depth + 1);
            }
        }
    }
}
