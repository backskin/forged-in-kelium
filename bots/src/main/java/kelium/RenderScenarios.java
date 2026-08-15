package kelium;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import kelium.dataio.GameConfig;
import kelium.report.FieldRenderer;

/**
 * Печать ASCII-раскладок всех сценариев (2p/3p/4p) — чтобы дизайнер сверял
 * поле и из Java-версии. Порт назначения forge/report/render_field.render_all.
 *
 * <p>Запуск: {@code mvn exec:java -Dexec.mainClass=kelium.RenderScenarios}.
 * Каталог данных берётся тем же способом, что и в остальной части симулятора
 * ({@link GameConfig#resolveDataRoot}); можно переопределить {@code -Dkelium.data}.
 */
public final class RenderScenarios {

    private RenderScenarios() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        String version = args.length > 0 ? args[0] : "1.0.0";
        Path dataRoot = GameConfig.resolveDataRoot(null);
        Path scenarios = dataRoot.resolve("scenarios");
        out.println("Каталог сценариев: " + scenarios.toAbsolutePath());
        out.println();
        out.println(FieldRenderer.renderAll(scenarios, version));
    }
}
