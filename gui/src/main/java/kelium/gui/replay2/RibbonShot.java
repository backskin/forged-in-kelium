package kelium.gui.replay2;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Снимок окна разбора партии для ревью новой ленты настроек. */
public final class RibbonShot {

    private RibbonShot() {
    }

    public static void main(String[] args) throws Exception {
        String outFile = args[0];
        int w = Integer.parseInt(args[1]);
        int h = Integer.parseInt(args[2]);
        boolean dark = args.length < 4 || !"light".equals(args[3]);

        kelium.dataio.Locations.applyDataFolder();
        Theme.setUserScale(1.0);
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

        Method setOpen = Replay2Gui.class.getDeclaredMethod("setSetupOpen", boolean.class);
        setOpen.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                setOpen.invoke(gui, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(300);

        var ff = Replay2Gui.class.getDeclaredField("frame");
        ff.setAccessible(true);
        JFrame frame = (JFrame) ff.get(gui);
        SwingUtilities.invokeAndWait(() -> {
            frame.setSize(w, h);
            frame.validate();
        });
        Thread.sleep(400);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        SwingUtilities.invokeAndWait(() -> frame.paint(g));
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(outFile));
        System.out.println("saved: " + outFile);
        System.exit(0);
    }
}
