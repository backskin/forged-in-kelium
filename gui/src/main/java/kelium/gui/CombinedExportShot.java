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

        // ЛЕГЕНДА — НАСТОЯЩАЯ, а не выдуманная в снимке: иначе снимок показывает
        // не то, что уйдёт в печать, и правка легенды остаётся непроверенной.
        java.lang.reflect.Method legendM =
            LayoutEditor.class.getDeclaredMethod("layoutLegend");
        legendM.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<PngExport.Item> legend =
            (java.util.List<PngExport.Item>) legendM.invoke(null);
        PngExport.Content content = new PngExport.Content(
            legend, LayoutEditor.playerBlocks(m), LayoutEditor.mapStats(m));
        java.awt.image.BufferedImage layoutComposed =
            PngExport.compose("Раскладка «тест»", "Гексов: 20 · игроков: 2", layoutImg, content);

        PngExport.Layout mode = PngExport.Layout.valueOf(modeArg);
        java.awt.image.BufferedImage out;
        if (mode == PngExport.Layout.FUSION) {
            // Зовём РЕАЛЬНУЮ сборку слоёв, а не переизобретаем её в снимке:
            // иначе опечатка в снимке не поймает опечатку в продакшене.
            // Сборка слоёв работает от статических полей окна конструктора —
            // в снимке окна нет, поэтому подставляем те же холсты руками.
            java.lang.reflect.Field canvasField =
                LayoutEditor.class.getDeclaredField("canvas");
            canvasField.setAccessible(true);
            canvasField.set(null, canvas);
            java.lang.reflect.Field asmField =
                LayoutEditor.class.getDeclaredField("assemblyTab");
            asmField.setAccessible(true);
            asmField.set(null, asm);
            java.lang.reflect.Method fuse =
                LayoutEditor.class.getDeclaredMethod("fuseLayers",
                    int.class, int.class, PngExport.Options.class);
            fuse.setAccessible(true);
            PngExport.Options fusionOptions = new PngExport.Options(
                true, true, true, PngExport.Layout.FUSION, true, true);
            java.awt.image.BufferedImage fused =
                (java.awt.image.BufferedImage) fuse.invoke(null, 1600, 1100, fusionOptions);
            out = PngExport.compose("Раскладка «тест» — слияние", "Гексов: 20 · игроков: 2",
                fused, content);
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
