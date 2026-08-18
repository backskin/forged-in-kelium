package kelium.gui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.yaml.snakeyaml.Yaml;

import kelium.gui.LayoutEditor.Model;
import kelium.gui.replay2.Theme;

/**
 * KeliumBuilder — НОВЫЙ конструктор раскладок (заказ дизайнера 18.08.2026),
 * первый срез миграции {@link LayoutEditor} на движок рендера
 * {@link BuilderScene} (тот же {@code FieldGeometry}/{@code Theme}, что у
 * разбора партии — не отдельный ад-хок рисовальщик, как у старого
 * {@code LayoutEditor.Canvas}).
 *
 * <p>ЭТОТ СРЕЗ: открыть готовый сценарий и показать его новым рендером —
 * только просмотр, редактирование ещё не перенесено. {@link LayoutEditor}
 * не тронут ни строкой и остаётся рабочим инструментом параллельно, пока
 * сюда не переедут инструменты редактирования, ghost-сетка и PNG-экспорт
 * (следующие срезы по трёхфазному плану миграции).
 */
public final class KeliumBuilder {

    private KeliumBuilder() {
    }

    public static void main(String[] args) {
        Theme.apply(true);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("KeliumBuilder — новый конструктор (превью рендера)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 900);

            Model model = new Model();
            Path scenarioFile = args.length > 0 ? Path.of(args[0]) : null;
            if (scenarioFile != null && Files.isReadable(scenarioFile)) {
                model = loadFirstScenario(scenarioFile, frame);
            }

            BuilderScene scene = new BuilderScene(model);
            scene.setPan(0, 0);
            frame.add(new JLabel("  Гексов: " + model.hexes.size()
                + (scenarioFile != null ? "  ·  " + scenarioFile.getFileName() : "  ·  пустое поле")),
                java.awt.BorderLayout.NORTH);
            frame.add(scene, java.awt.BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    @SuppressWarnings("unchecked")
    private static Model loadFirstScenario(Path file, JFrame owner) {
        try {
            Map<String, Object> data = new Yaml().load(Files.readString(file, StandardCharsets.UTF_8));
            List<Object> scns = (List<Object>) data.get("scenarios");
            Map<String, Object> first = (Map<String, Object>) scns.get(0);
            return BuilderScene.loadScenario(first);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(owner,
                "Не удалось открыть сценарий.\n\n" + e.getMessage(),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
            return new Model();
        }
    }
}
