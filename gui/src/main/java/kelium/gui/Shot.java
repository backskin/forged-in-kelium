package kelium.gui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Shot — снимок окна конструктора для ревью интерфейса агентом.
 *
 * <p>Живёт в пакете kelium.gui, чтобы дотянуться до пакетных modelRef() и
 * статического поля frame. Аргументы: путь к PNG, ширина, высота.
 */
public final class Shot {

    private Shot() {
    }

    public static void main(String[] args) throws Exception {
        String out = args[0];
        int w = Integer.parseInt(args[1]);
        int h = Integer.parseInt(args[2]);

        LayoutEditor.main(new String[0]);
        Thread.sleep(2500);

        SwingUtilities.invokeAndWait(() -> {
            LayoutEditor.Model m = LayoutEditor.modelRef();
            m.hexes.clear();
            // Поле-ромб 5×4, чтобы было видно всё разом.
            for (int q = 0; q < 5; q++) {
                for (int r = 0; r < 4; r++) {
                    m.hexes.put(LayoutEditor.Model.key(q, r), new LayoutEditor.LHex(q, r));
                }
            }
            m.get(0, 0).content = "player_start";
            m.get(0, 0).seat = 0;
            m.get(4, 3).content = "player_start";
            m.get(4, 3).seat = 1;
            m.get(1, 1).content = "kelium_tile";
            m.get(1, 1).stack = 2;
            m.get(1, 1).keliumDelta = 1;
            m.get(3, 2).content = "spawn_start";
            m.get(2, 0).containers = 1;
            m.get(2, 2).containers = 2;
            m.get(3, 0).content = "forbidden";
            m.get(0, 2).neutrals.add(new LayoutEditor.Neutral(true, 1));
        });
        Thread.sleep(300);

        // Четвёртый аргумент «toggle» — переключить тему НА МЕСТЕ и снять после.
        if (args.length > 3 && "toggle".equals(args[3])) {
            java.lang.reflect.Method m =
                LayoutEditor.class.getDeclaredMethod("applyTheme", boolean.class);
            m.setAccessible(true);
            boolean now = kelium.gui.replay2.Theme.isDark();
            SwingUtilities.invokeAndWait(() -> {
                try {
                    m.invoke(null, !now);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(600);
            System.out.println("тема переключена на "
                + (kelium.gui.replay2.Theme.isDark() ? "тёмную" : "светлую"));
        }

        Field ff = LayoutEditor.class.getDeclaredField("frame");
        ff.setAccessible(true);
        JFrame frame = (JFrame) ff.get(null);

        Field cf = LayoutEditor.class.getDeclaredField("canvas");
        cf.setAccessible(true);
        Object canvas = cf.get(null);

        SwingUtilities.invokeAndWait(() -> {
            frame.setSize(w, h);
            frame.validate();
            try {
                canvas.getClass().getDeclaredMethod("fitToView").setAccessible(true);
                java.lang.reflect.Method fit =
                    canvas.getClass().getDeclaredMethod("fitToView");
                fit.setAccessible(true);
                fit.invoke(canvas);
            } catch (Exception ignored) {
                // не вписалось — снимем как есть
            }
        });
        Thread.sleep(700);

        java.awt.Window shootWindow = frame;
        if (args.length > 3 && "export-dialog".equals(args[3])) {
            SwingUtilities.invokeAndWait(() ->
                kelium.gui.ExportOptionsDialog.show(frame,
                    kelium.dataio.AppSettings.of("replay2")));
            Thread.sleep(400);
            for (java.awt.Window ow : frame.getOwnedWindows()) {
                if (ow.isVisible()) {
                    shootWindow = ow;
                }
            }
        }

        java.awt.Window fw = shootWindow;
        java.awt.Dimension sz = fw.getSize();
        BufferedImage img = new BufferedImage(
            Math.max(1, sz.width), Math.max(1, sz.height), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        SwingUtilities.invokeAndWait(() -> fw.paint(g));
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", new java.io.File(out));
        System.out.println("снято: " + out);
        System.exit(0);
    }
}
