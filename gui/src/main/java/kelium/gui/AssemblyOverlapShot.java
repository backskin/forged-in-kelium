package kelium.gui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Снимок реальной большой раскладки, где была замечена перекрывающаяся подпись. */
public final class AssemblyOverlapShot {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        String outFile = args[0];
        Path file = kelium.dataio.GameConfig.resolveDataRoot(null)
            .resolve("scenarios").resolve("new").resolve("сценарий 4 игрока 4.yaml");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Object> doc = new org.yaml.snakeyaml.Yaml().load(text);
        List<Map<String, Object>> scns = (List<Map<String, Object>>) doc.get("scenarios");
        LayoutEditor.loadScenarioIntoModel(scns.get(0));

        AssemblyWindow asm = new AssemblyWindow(LayoutEditor.modelRef());
        asm.refresh();
        for (int i = 0; i < 100 && !asm.hasResult(); i++) {
            Thread.sleep(100);
        }
        if (!asm.hasResult()) {
            System.out.println("сборка не подобралась");
            System.exit(1);
        }
        java.awt.image.BufferedImage img = asm.exportImage(
            new PngExport.Options(true, true, true, PngExport.Layout.SEPARATE));
        javax.imageio.ImageIO.write(img, "png", new java.io.File(outFile));
        System.out.println("saved: " + outFile + "  " + img.getWidth() + "x" + img.getHeight());
        System.exit(0);
    }
}
