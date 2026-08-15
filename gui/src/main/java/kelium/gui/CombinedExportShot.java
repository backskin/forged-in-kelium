package kelium.gui;

import javax.swing.SwingUtilities;

/** Снимок ОБЪЕДИНЁННОГО экспорта (поле+сборка) во всех режимах Layout. */
public final class CombinedExportShot {

    private CombinedExportShot() {
    }

    public static void main(String[] args) throws Exception {
        String outDir = args[0];
        String modeArg = args[1];   // VERTICAL | HORIZONTAL | FUSION | SEPARATE

        SwingUtilities.invokeAndWait(() -> {
            LayoutEditor.Model m = LayoutEditor.modelRef();
            m.hexes.clear();
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
            m.get(3, 2).content = "spawn_start";
            m.get(2, 0).containers = 1;
            m.get(3, 0).content = "forbidden";
            m.get(0, 2).neutrals.add(new LayoutEditor.Neutral(true, 1));
        });

        AssemblyWindow asm = new AssemblyWindow(LayoutEditor.modelRef());
        asm.refresh();
        // подождать, пока фоновый подбор сборки (SwingWorker) закончит работу
        for (int i = 0; i < 100 && !asm.hasResult(); i++) {
            Thread.sleep(100);
        }
        if (!asm.hasResult()) {
            System.out.println("сборка не подобралась за отведённое время");
            System.exit(1);
        }

        LayoutEditor.Model m = LayoutEditor.modelRef();
        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = m;
        canvas.fitToView();
        java.awt.image.BufferedImage layoutImg = canvas.render(1600, 1100);

        PngExport.Content content = new PngExport.Content(
            java.util.List.of(
                PngExport.Item.hex(new java.awt.Color(0xEFEDE4), "обычный гекс"),
                PngExport.Item.hex(LayoutEditor.Canvas.SPAWN_NORMAL, "большое зарождение")),
            LayoutEditor.playerBlocks(m), LayoutEditor.mapStats(m));
        java.awt.image.BufferedImage layoutComposed =
            PngExport.compose("Раскладка «тест»", "Гексов: 20 · игроков: 2", layoutImg, content);

        PngExport.Layout mode = PngExport.Layout.valueOf(modeArg);
        java.awt.image.BufferedImage out;
        if (mode == PngExport.Layout.FUSION) {
            // Зовём РЕАЛЬНУЮ приватную формулу и реальные методы слоёв — не
            // переизобретаем расчёт в тесте, иначе опечатка в тесте не поймает
            // опечатку в продакшене.
            java.lang.reflect.Method sharedFit =
                LayoutEditor.class.getDeclaredMethod("sharedFit",
                    LayoutEditor.Model.class, int.class, int.class);
            sharedFit.setAccessible(true);
            double[] fit = (double[]) sharedFit.invoke(null, m, 1600, 1100);
            java.awt.image.BufferedImage blocksLayer =
                asm.renderBlocksLayer(1600, 1100, fit[0], fit[1], fit[2]);
            java.awt.image.BufferedImage contentLayer =
                canvas.renderContentOnly(1600, 1100, fit[0], fit[1], fit[2]);
            java.awt.Graphics2D fg = blocksLayer.createGraphics();
            fg.drawImage(contentLayer, 0, 0, null);
            fg.dispose();
            out = PngExport.compose("Раскладка «тест» — слияние", "Гексов: 20 · игроков: 2",
                blocksLayer, content);
        } else if (mode == PngExport.Layout.SEPARATE) {
            out = asm.exportImage(new PngExport.Options(true, true, true, mode));
        } else {
            java.awt.image.BufferedImage assemblyImg =
                asm.exportImage(new PngExport.Options(true, true, true, mode));
            out = PngExport.stack(layoutComposed, assemblyImg, mode == PngExport.Layout.VERTICAL);
        }
        javax.imageio.ImageIO.write(out, "png", new java.io.File(outDir));
        System.out.println("saved: " + outDir + "  " + out.getWidth() + "x" + out.getHeight());
        System.exit(0);
    }
}
