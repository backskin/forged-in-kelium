package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import kelium.dataio.GameConfig;
import kelium.engine.Scenario;
import kelium.report.ScenarioSvgRenderer;

/**
 * CLI: отрисовать ВСЕ варианты раскладок 2p/3p/4p в SVG для визуальной проверки.
 *
 * <p>Запуск: {@code mvn exec:java -Dexec.mainClass=kelium.RenderScenarioSvg}
 * (опционально аргумент — версия, по умолчанию 1.0.0). Пишет файлы в
 * {@code reports/layouts/scenario_<N>p_<id>.svg} с легендой обозначений (emoji).
 */
public final class RenderScenarioSvg {

    private RenderScenarioSvg() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String version = args.length > 0 ? args[0] : "1.0.0";
        Path dataRoot = GameConfig.resolveDataRoot(null);
        Path outDir = Path.of("reports", "layouts");
        Files.createDirectories(outDir);

        int total = 0;
        for (int players = 2; players <= 4; players++) {
            List<Map<String, Object>> variants =
                Scenario.loadAllVariants(players, version, dataRoot);
            for (int v = 0; v < variants.size(); v++) {
                Map<String, Object> scn = variants.get(v);
                String id = String.valueOf(scn.getOrDefault("id", players + "p_v" + (v + 1)));
                String title = players + " игрока — вариант " + (v + 1) + "  (" + id + ")";
                String svg = ScenarioSvgRenderer.render(scn, title);
                Path outFile = outDir.resolve("scenario_" + players + "p_" + id + ".svg");
                Files.writeString(outFile, svg, StandardCharsets.UTF_8);
                out.println("нарисовано: " + outFile);
                total++;
            }
        }
        out.println("ИТОГО раскладок: " + total + " → " + outDir.toAbsolutePath());
    }
}
