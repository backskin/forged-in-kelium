package kelium.gui;

import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.yaml.snakeyaml.Yaml;

import kelium.gui.LayoutEditor.Model;
import kelium.gui.replay2.Theme;

/**
 * KeliumBuilder — НОВЫЙ конструктор раскладок (заказ дизайнера 18.08.2026),
 * миграция {@link LayoutEditor} на движок рендера {@link BuilderScene} (тот
 * же {@code FieldGeometry}/{@code Theme}, что у разбора партии — не
 * отдельный ад-хок рисовальщик, как у старого {@code LayoutEditor.Canvas}).
 *
 * <p>СРЕЗ 2 (первый был только просмотром): базовое редактирование —
 * добавление/удаление гексов по сетке призраков, старты игроков, малое и
 * большое зарождение, запретный гекс. {@link LayoutEditor} не тронут ни
 * строкой и остаётся рабочим инструментом параллельно, пока сюда не
 * переедут отделка (правка келемия, контейнеры, нейтралы, стопки) и
 * PNG-экспорт (следующие срезы).
 */
public final class KeliumBuilder {

    private KeliumBuilder() {
    }

    public static void main(String[] args) {
        Theme.apply(true);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("KeliumBuilder — новый конструктор");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1200, 900);

            Path scenarioFile = args.length > 0 ? Path.of(args[0]) : null;
            boolean hasScenario = scenarioFile != null && Files.isReadable(scenarioFile);
            Model model = hasScenario ? loadFirstScenario(scenarioFile, frame) : new Model();

            BuilderScene scene = new BuilderScene(model);
            scene.setPan(0, 0);

            JLabel status = new JLabel();
            Runnable refreshStatus = () -> status.setText("  Гексов: " + model.hexes.size()
                + "  ·  игроков: " + model.players()
                + (hasScenario ? "  ·  " + scenarioFile.getFileName() : "  ·  пустое поле"));
            refreshStatus.run();
            scene.onChange(refreshStatus);

            frame.add(buildToolbar(scene), BorderLayout.WEST);
            frame.add(status, BorderLayout.NORTH);
            frame.add(scene, BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static JToolBar buildToolbar(BuilderScene scene) {
        JToolBar bar = new JToolBar(JToolBar.VERTICAL);
        bar.setFloatable(false);
        ButtonGroup group = new ButtonGroup();
        addTool(bar, group, "⬡ Гекс", BuilderScene.Tool.ADD_REMOVE, scene, true);
        addTool(bar, group, "🚩 Старт игрока", BuilderScene.Tool.PLAYER_START, scene, false);
        addTool(bar, group, "🟢 Малое зарождение", BuilderScene.Tool.SPAWN_SMALL, scene, false);
        addTool(bar, group, "🟩 Большое зарождение", BuilderScene.Tool.SPAWN_BIG, scene, false);
        addTool(bar, group, "⛔ Запретный гекс", BuilderScene.Tool.FORBIDDEN, scene, false);
        addTool(bar, group, "🧽 Очистить гекс", BuilderScene.Tool.CLEAR, scene, false);
        return bar;
    }

    private static void addTool(JToolBar bar, ButtonGroup group, String label,
                                BuilderScene.Tool tool, BuilderScene scene, boolean selected) {
        JToggleButton btn = new JToggleButton(label, selected);
        btn.setHorizontalAlignment(JToggleButton.LEFT);
        btn.addActionListener(e -> scene.setTool(tool));
        group.add(btn);
        bar.add(btn);
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
