package kelium.gui;

import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import kelium.dataio.FieldFile;
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

            frame.add(buildToolbar(scene, frame, model), BorderLayout.WEST);
            frame.add(status, BorderLayout.NORTH);
            frame.add(scene, BorderLayout.CENTER);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static JToolBar buildToolbar(BuilderScene scene, JFrame frame, Model model) {
        JToolBar bar = new JToolBar(JToolBar.VERTICAL);
        bar.setFloatable(false);
        ButtonGroup group = new ButtonGroup();
        addTool(bar, group, "⬡ Гекс", BuilderScene.Tool.ADD_REMOVE, scene, true);
        addTool(bar, group, "🚩 Старт игрока", BuilderScene.Tool.PLAYER_START, scene, false);
        addTool(bar, group, "🟢 Малое зарождение", BuilderScene.Tool.SPAWN_SMALL, scene, false);
        addTool(bar, group, "🟩 Большое зарождение", BuilderScene.Tool.SPAWN_BIG, scene, false);
        addTool(bar, group, "🔂 Стопка ×2", BuilderScene.Tool.STACK, scene, false);
        addTool(bar, group, "💎 Келемий ±", BuilderScene.Tool.KELIUM_DELTA, scene, false);
        addTool(bar, group, "📦 Контейнер", BuilderScene.Tool.CONTAINER, scene, false);
        addTool(bar, group, "⛔ Запретный гекс", BuilderScene.Tool.FORBIDDEN, scene, false);
        addTool(bar, group, "🧽 Очистить гекс", BuilderScene.Tool.CLEAR, scene, false);

        bar.addSeparator();
        JButton undo = new JButton("↶ Отменить");
        undo.setHorizontalAlignment(JButton.LEFT);
        undo.addActionListener(e -> scene.undo());
        bar.add(undo);

        JButton save = new JButton("💾 Сохранить…");
        save.setHorizontalAlignment(JButton.LEFT);
        save.addActionListener(e -> saveLayout(frame, model));
        bar.add(save);
        return bar;
    }

    /**
     * СОХРАНЕНИЕ В ТОТ ЖЕ ФОРМАТ, что пишет {@link LayoutEditor} — один и тот
     * же {@code toScenarioMap} и то же расширение {@code .kmap}, чтобы файлы
     * двух конструкторов были взаимозаменяемы, а не «почти совместимы».
     */
    private static void saveLayout(JFrame frame, Model model) {
        Path start = kelium.dataio.GameConfig.resolveDataRoot(null)
            .resolve("scenarios").resolve(newFileName());
        Path path = PathDialog.choose(frame, "Сохранить раскладку", start, true,
            FieldFile.EXT, FieldFile.READ_EXTS);
        if (path == null) {
            return;
        }
        path = FieldFile.withExt(path);
        String id = FieldFile.baseName(path);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", "editor");
        doc.put("scenarios", List.of(LayoutEditor.toScenarioMap(model, id)));
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setAllowUnicode(true);
        try {
            Files.writeString(path, new Yaml(opts).dump(doc), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(frame, "Раскладка сохранена:\n" + path,
                "Сохранено", JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(frame,
                "Не удалось сохранить файл.\n\n" + e.getMessage(),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Дата в имени — В ОБРАТНОМ ПОРЯДКЕ (год-месяц-день): так сортировка по
     *  имени совпадает с сортировкой по времени (просьба дизайнера 19.08.2026). */
    private static String newFileName() {
        return "kmap_" + java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
            + FieldFile.DOT_EXT;
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
