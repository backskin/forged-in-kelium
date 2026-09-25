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

    /** Куда прокрутить перед снимком (ряд карт ящика «Игрок»). */
    private static javax.swing.JComponent scrollTo;

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

        // НАСТОЯЩАЯ ЗАПИСЬ ПАРТИИ вместо расстановки: -Dshot.replay=<файл>,
        // -Dshot.frame=<шаг> (по умолчанию середина), -Dshot.drawer=<место>
        // (ящик «Игрок»), -Dshot.orders=<место> (панель приказов),
        // -Dshot.zoom=<id карты> (рядом снимок увеличения: <файл>-zoom.png).
        String replay = System.getProperty("shot.replay");
        if (replay != null) {
            kelium.report.ReplayRecord rec = kelium.report.ReplayRecord.load(
                java.nio.file.Path.of(replay));
            var sf = Replay2Gui.class.getDeclaredField("session");
            sf.setAccessible(true);
            Session session = (Session) sf.get(gui);
            Method rules = Replay2Gui.class.getDeclaredMethod("loadRules",
                kelium.report.ReplayRecord.class);
            rules.setAccessible(true);
            String fr = System.getProperty("shot.frame");
            SwingUtilities.invokeAndWait(() -> {
                try {
                    session.setRecord(rec);
                    rules.invoke(gui, rec);
                    session.seek(fr == null ? rec.frames.size() / 2 : Integer.parseInt(fr));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(300);
            String dr = System.getProperty("shot.drawer");
            if (dr != null) {
                var df = Replay2Gui.class.getDeclaredField("drawer");
                df.setAccessible(true);
                Drawer drawer = (Drawer) df.get(gui);
                Method set = Replay2Gui.class.getDeclaredMethod("setDrawer", boolean.class);
                set.setAccessible(true);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        drawer.showPlayer(Integer.parseInt(dr));
                        set.invoke(gui, true);
                        if (Boolean.getBoolean("shot.drawerCards")) {
                            var cf = Drawer.class.getDeclaredField("sheetCards");
                            cf.setAccessible(true);
                            javax.swing.JComponent cards = (javax.swing.JComponent) cf.get(drawer);
                            scrollTo = cards;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            String ord = System.getProperty("shot.orders");
            if (ord != null) {
                Method tog = Replay2Gui.class.getDeclaredMethod("toggleOrders", int.class);
                tog.setAccessible(true);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        tog.invoke(gui, Integer.parseInt(ord));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            Thread.sleep(900);
            // -Dshot.reader=<место>: читалка заданий этого места (<файл>-reader.png)
            String reader = System.getProperty("shot.reader");
            if (reader != null) {
                int st = Integer.parseInt(reader);
                java.awt.image.BufferedImage[] out = new java.awt.image.BufferedImage[1];
                SwingUtilities.invokeAndWait(() -> {
                    var p = session.frame().snapshot.players.get(st);
                    CardReader.show(null, session.content(), "objectives", "снимок",
                        p.objectiveHand, id -> Names.card(rec, id));
                    for (java.awt.Window win : java.awt.Window.getWindows()) {
                        if (win instanceof JFrame jf && "снимок".equals(jf.getTitle())) {
                            jf.validate();
                            out[0] = new BufferedImage(jf.getWidth(), jf.getHeight(),
                                BufferedImage.TYPE_INT_RGB);
                            Graphics2D rg = out[0].createGraphics();
                            jf.paint(rg);
                            rg.dispose();
                            jf.dispose();
                        }
                    }
                });
                if (out[0] != null) {
                    javax.imageio.ImageIO.write(out[0], "png",
                        new java.io.File(outFile.replace(".png", "-reader.png")));
                }
            }
            String zoom = System.getProperty("shot.zoom");
            if (zoom != null) {
                java.awt.image.BufferedImage face = kelium.gui.CardArt.face(zoom);
                if (face == null) {
                    face = kelium.gui.CardArt.order(zoom, null);
                }
                if (face != null) {
                    java.awt.image.BufferedImage f0 = face;
                    var ff0 = Replay2Gui.class.getDeclaredField("frame");
                    ff0.setAccessible(true);
                    JFrame fr0 = (JFrame) ff0.get(gui);
                    java.awt.image.BufferedImage[] out = new java.awt.image.BufferedImage[1];
                    SwingUtilities.invokeAndWait(() -> {
                        CardZoom.show(fr0.getContentPane(), f0,
                            new java.awt.Rectangle(10, 10, 20, 20));
                        java.awt.Window zw = java.awt.Window.getWindows()[0];
                        for (java.awt.Window win : java.awt.Window.getWindows()) {
                            if (win instanceof javax.swing.JWindow && win.isVisible()) {
                                zw = win;
                            }
                        }
                        out[0] = new BufferedImage(zw.getWidth(), zw.getHeight(),
                            BufferedImage.TYPE_INT_RGB);
                        Graphics2D zg = out[0].createGraphics();
                        zw.paint(zg);
                        zg.dispose();
                        CardZoom.hide();
                    });
                    javax.imageio.ImageIO.write(out[0], "png",
                        new java.io.File(outFile.replace(".png", "-zoom.png")));
                }
            }
        }

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

        if (scrollTo != null) {
            SwingUtilities.invokeAndWait(() -> scrollTo.scrollRectToVisible(
                new java.awt.Rectangle(0, 0, scrollTo.getWidth(), scrollTo.getHeight())));
            Thread.sleep(300);
        }

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        kelium.report.Сглаживание.включить(g);
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
