package kelium.gui;

import javax.swing.SwingUtilities;

/** Снимок собственно ЭКСПОРТИРУЕМОЙ картинки (не окна) — легенда/игроки/статистика. */
public final class ExportShot {

    private ExportShot() {
    }

    public static void main(String[] args) throws Exception {
        String out = args[0];

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
            m.get(4, 0).content = "player_start";
            m.get(4, 0).seat = 2;
            m.get(1, 1).content = "kelium_tile";
            m.get(1, 1).stack = 2;
            m.get(1, 1).keliumDelta = 1;
            m.get(3, 2).content = "spawn_start";
            m.get(2, 0).containers = 1;
            m.get(2, 2).containers = 2;
            m.get(3, 0).content = "forbidden";
            m.get(0, 2).neutrals.add(new LayoutEditor.Neutral(true, 1));
        });

        LayoutEditor.Canvas canvas = new LayoutEditor.Canvas();
        canvas.model = LayoutEditor.modelRef();
        canvas.fitToView();
        java.awt.image.BufferedImage field = canvas.render(1600, 1100);

        LayoutEditor.Model m = LayoutEditor.modelRef();
        PngExport.Content content = new PngExport.Content(
            java.util.List.of(
                PngExport.Item.hex(new java.awt.Color(0xEFEDE4), "обычный гекс — можно строить и ходить, ставить любые здания и передвигать войска без ограничений в любую сторону"),
                PngExport.Item.hex(LayoutEditor.Canvas.SPAWN_START, "малое зарождение: лицо 3 келемия, оборот 2"),
                PngExport.Item.hex(LayoutEditor.Canvas.SPAWN_NORMAL, "большое зарождение: лицо 4 келемия, оборот 3"),
                PngExport.Item.hex(new java.awt.Color(0x3A3A3A), "запретный гекс (✕) — дыра в поле, совершенно непроходимая ни для кого и никогда, ни при каких условиях"),
                PngExport.Item.square(LayoutEditor.Canvas.NEUTRAL_FILL, "нейтральное здание на стенке гекса"),
                PngExport.Item.square(LayoutEditor.Canvas.CONTAINER_FILL, "контейнер, напечатанный на гексе")),
            LayoutEditor.playerBlocks(m), LayoutEditor.mapStats(m));

        java.awt.image.BufferedImage img =
            PngExport.compose("Раскладка «тест-экспорт»", "Гексов: 20   ·   игроков: 3   ·   проверки: без ошибок",
                field, content);
        javax.imageio.ImageIO.write(img, "png", new java.io.File(out));
        System.out.println("экспортная картинка: " + out + "  " + img.getWidth() + "x" + img.getHeight());
        System.exit(0);
    }
}
