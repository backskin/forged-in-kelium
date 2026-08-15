package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import kelium.dataio.Locations;

/**
 * PlacesDialog — ОДИН на все приложения диалог «где что лежит»: папки с
 * раскладками полей и папка памяти ботов.
 *
 * <p>Настройки общие ({@link Locations}), поэтому добавленную здесь папку тут же
 * видят и проигрыватель партий, и раннер прогонов, и обучение — искать
 * раскладку или геном в разных местах больше не приходится.
 */
public final class PlacesDialog {

    private PlacesDialog() {
    }

    /** Показать диалог. Возвращает true, если что-то изменилось. */
    public static boolean show(Component parent) {
        String beforeLayouts = String.valueOf(Locations.userLayoutFolders());
        String beforeMemory = Locations.botMemory().toString();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Раскладки полей", layoutsTab(parent));
        tabs.addTab("Память ботов", memoryTab(parent));
        tabs.setPreferredSize(new Dimension(640, 300));
        JOptionPane.showMessageDialog(parent, tabs, "Где что лежит",
            JOptionPane.PLAIN_MESSAGE);

        return !beforeLayouts.equals(String.valueOf(Locations.userLayoutFolders()))
            || !beforeMemory.equals(Locations.botMemory().toString());
    }

    // ==================== раскладки ====================
    private static JPanel layoutsTab(Component parent) {
        DefaultListModel<String> model = new DefaultListModel<>();
        Runnable refill = () -> {
            model.clear();
            model.addElement(Locations.builtinLayoutFolder()
                + "   (авторские, не убрать)");
            for (Path p : Locations.userLayoutFolders()) {
                model.addElement(p.toString());
            }
        };
        refill.run();
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JLabel(Ui.text("Во всех этих папках приложения ищут раскладки полей. "
            + "Всё, что нарисуешь конструктором и сохранишь сюда, появится в списке "
            + "«поле» и в проигрывателе партий, и в раннере прогонов. Читаются файлы "
            + ".yaml и .yml; число игроков определяется по стартам на самой раскладке.",
            560)), BorderLayout.NORTH);
        JScrollPane sc = new JScrollPane(list);
        sc.setPreferredSize(new Dimension(600, 150));
        p.add(sc, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton add = new JButton("Добавить папку…");
        add.setToolTipText(Ui.text("Ещё одна папка, в которой лежат раскладки."));
        add.addActionListener(e -> {
            Path dir = askFolder(parent, "Добавить папку с раскладками",
                Locations.builtinLayoutFolder(), "yaml");
            if (dir != null) {
                Locations.addLayoutFolder(dir);
                refill.run();
            }
        });
        JButton drop = new JButton("Убрать выбранную");
        drop.setToolTipText(Ui.text("Убрать папку из списка. Сами файлы не трогаются."));
        drop.addActionListener(e -> {
            int i = list.getSelectedIndex();
            if (i > 0) {
                Locations.removeLayoutFolder(Locations.userLayoutFolders().get(i - 1));
                refill.run();
            }
        });
        JButton open = new JButton("Открыть в проводнике");
        open.addActionListener(e -> {
            int i = list.getSelectedIndex();
            openFolder(parent, i <= 0 ? Locations.builtinLayoutFolder()
                : Locations.userLayoutFolders().get(i - 1));
        });
        buttons.add(add);
        buttons.add(drop);
        buttons.add(open);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    // ==================== память ботов ====================
    private static JPanel memoryTab(Component parent) {
        JTextField path = new JTextField(Locations.botMemory().toString(), 46);
        path.setEditable(false);
        path.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JLabel(Ui.text("ПАМЯТЬ БОТОВ — папка, где лежат обученные геномы "
            + "стратегов (strategic_<N>p.json), сети (neural_<N>p.txt) и модели ONNX "
            + "(policy_<N>p.onnx). Отсюда читают и прогоны, и проигрыватель, и "
            + "обучение — место одно на все приложения.", 560)), BorderLayout.NORTH);
        p.add(path, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton change = new JButton("Выбрать другую папку…");
        change.addActionListener(e -> {
            Path dir = askFolder(parent, "Папка памяти ботов",
                Locations.botMemory(), "json");
            if (dir != null) {
                Locations.setBotMemory(dir);
                path.setText(Locations.botMemory().toString());
            }
        });
        JButton reset = new JButton("Вернуть обычную");
        reset.setToolTipText(Ui.text("Вернуться к папке "
            + Locations.defaultBotMemory() + "."));
        reset.addActionListener(e -> {
            Locations.setBotMemory(null);
            path.setText(Locations.botMemory().toString());
        });
        JButton open = new JButton("Открыть в проводнике");
        open.addActionListener(e -> openFolder(parent, Locations.botMemory()));
        buttons.add(change);
        buttons.add(reset);
        buttons.add(open);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    // ==================== мелочи ====================
    private static void openFolder(Component parent, Path dir) {
        try {
            java.awt.Desktop.getDesktop().open(dir.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                Ui.text("Не удалось открыть папку:\n" + dir), "Не вышло",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Спросить папку: путь можно вписать или вставить, а можно ткнуть в любой
     * файл внутри неё — возьмём его папку. Свой диалог выбора файла умеет
     * отдавать только ФАЙЛ, поэтому папку выбираем так.
     */
    private static Path askFolder(Component parent, String title, Path start, String ext) {
        JTextField pathField = new JTextField(start.toString(), 44);
        JButton browse = new JButton("Указать по файлу…");
        browse.setToolTipText(Ui.text("Выбери любой файл в нужной папке — "
            + "в настройки попадёт сама папка."));
        browse.addActionListener(e -> {
            Path f = PathDialog.choose(parent, "Любой файл в нужной папке",
                Paths.get(pathField.getText().trim()), false, ext);
            if (f != null && f.getParent() != null) {
                pathField.setText(f.getParent().toString());
            }
        });
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.add(new JLabel(Ui.text("Впиши или вставь путь к папке:", 420)),
            BorderLayout.NORTH);
        p.add(pathField, BorderLayout.CENTER);
        p.add(browse, BorderLayout.EAST);
        int r = JOptionPane.showConfirmDialog(parent, p, title,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return null;
        }
        String text = pathField.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        Path dir = Paths.get(text);
        if (!Files.isDirectory(dir)) {
            dir = dir.getParent();
        }
        if (dir == null || !Files.isDirectory(dir)) {
            JOptionPane.showMessageDialog(parent, Ui.text("Такой папки нет:\n" + text),
                "Папка не найдена", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return dir;
    }
}
