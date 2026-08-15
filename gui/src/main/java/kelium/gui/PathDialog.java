package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/**
 * PathDialog — собственный диалог выбора файла.
 *
 * <p>Сделан вместо стандартного {@code JFileChooser}, который на машине
 * дизайнера показывал пустой список. Здесь список папок и файлов строится
 * своими руками (обычный {@link File#listFiles()}), поэтому показывать ему
 * нечего кроме правды.
 *
 * <p>Порядок работы такой, как просил дизайнер: сверху строка АДРЕСА — путь
 * можно просто вписать или вставить; кнопка рядом раскрывает содержимое папки
 * списком, по которому можно ходить двойным кликом.
 */
public final class PathDialog {

    private PathDialog() {
    }

    /**
     * Показать диалог и вернуть выбранный файл (или null, если отменили).
     *
     * @param save true — режим сохранения (можно назвать несуществующий файл)
     * @param ext  расширение без точки для фильтра и автодобавления (может быть null)
     */
    public static Path choose(Component parent, String title, Path start,
                              boolean save, String ext) {
        return choose(parent, title, start, save, ext,
            ext == null ? List.of() : List.of(ext));
    }

    /**
     * То же, но список показываемых расширений ШИРЕ, чем то, которое дописывается
     * при сохранении. Нужно раскладкам: сохраняем всегда в свой {@code .kfield},
     * а открывать даём и старые {@code .yaml} (решение дизайнера 13.08.2026).
     *
     * @param ext     расширение для автодобавления при сохранении (может быть null)
     * @param visible какие расширения показывать в списке (пустой — показывать все)
     */
    public static Path choose(Component parent, String title, Path start,
                              boolean save, String ext, List<String> visible) {
        Path startDir = start;
        String startName = "";
        if (startDir != null && !Files.isDirectory(startDir)) {
            startName = startDir.getFileName() == null ? "" : startDir.getFileName().toString();
            startDir = startDir.getParent();
        }
        if (startDir == null || !Files.isDirectory(startDir)) {
            startDir = Paths.get(System.getProperty("user.home"));
        }

        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent), title,
            JDialog.ModalityType.APPLICATION_MODAL);
        Path[] cur = {startDir.toAbsolutePath().normalize()};
        Path[] result = {null};

        // ---- строка адреса ----
        JTextField pathField = new JTextField(cur[0].toString(), 46);
        pathField.setToolTipText("<html>Впиши или вставь путь к папке либо к файлу.<br>"
            + "Enter или кнопка «Перейти» — открыть содержимое.</html>");
        JButton go = new JButton("Перейти ▸");
        go.setToolTipText("Открыть папку, указанную в строке адреса");

        // ---- список содержимого ----
        DefaultListModel<Entry> model = new DefaultListModel<>();
        JList<Entry> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Шрифт и цвета — от темы: диалог общий для всех приложений, и в тёмной
        // теме моноширинный чёрный по белому выбивался из окна.
        list.setFont(kelium.gui.replay2.Theme.body());
        list.setVisibleRowCount(16);

        JLabel where = new JLabel(" ");
        where.setFont(kelium.gui.replay2.Theme.note(11));
        where.setForeground(kelium.gui.replay2.Theme.ink3());

        JTextField nameField = new JTextField(startName, 30);
        nameField.setToolTipText(save ? "Имя сохраняемого файла" : "Имя открываемого файла");

        Runnable reload = () -> {
            model.clear();
            Path p = cur[0];
            pathField.setText(p.toString());
            List<Entry> dirs = new ArrayList<>();
            List<Entry> files = new ArrayList<>();
            File[] kids = p.toFile().listFiles();
            if (kids != null) {
                for (File f : kids) {
                    if (f.isHidden()) {
                        continue;
                    }
                    if (f.isDirectory()) {
                        dirs.add(new Entry(f, true));
                    } else if (matches(f.getName(), visible)) {
                        files.add(new Entry(f, false));
                    }
                }
            }
            dirs.sort(Comparator.comparing(e -> e.file.getName().toLowerCase(Locale.ROOT)));
            files.sort(Comparator.comparing(e -> e.file.getName().toLowerCase(Locale.ROOT)));
            if (p.getParent() != null) {
                model.addElement(new Entry(p.getParent().toFile(), true, ".. (на уровень вверх)"));
            }
            dirs.forEach(model::addElement);
            files.forEach(model::addElement);
            StringBuilder mask = new StringBuilder();
            for (String v : visible) {
                mask.append(mask.length() == 0 ? " " : ", ").append("*.").append(v);
            }
            where.setText("папок: " + dirs.size() + " · файлов" + mask + ": " + files.size()
                + (kids == null ? "   ⚠ папка недоступна для чтения" : ""));
        };

        Runnable openSelected = () -> {
            Entry e = list.getSelectedValue();
            if (e == null) {
                return;
            }
            if (e.dir) {
                cur[0] = e.file.toPath().toAbsolutePath().normalize();
                reload.run();
            } else {
                nameField.setText(e.file.getName());
            }
        };

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent ev) {
                if (ev.getClickCount() == 2) {
                    openSelected.run();
                } else {
                    Entry e = list.getSelectedValue();
                    if (e != null && !e.dir) {
                        nameField.setText(e.file.getName());
                    }
                }
            }
        });

        go.addActionListener(a -> {
            Path typed = Paths.get(pathField.getText().trim());
            if (Files.isDirectory(typed)) {
                cur[0] = typed.toAbsolutePath().normalize();
                reload.run();
            } else if (typed.getParent() != null && Files.isDirectory(typed.getParent())) {
                cur[0] = typed.getParent().toAbsolutePath().normalize();
                nameField.setText(typed.getFileName().toString());
                reload.run();
            } else {
                JOptionPane.showMessageDialog(d, Ui.text("Такой папки нет:\n" + typed),
                    "Путь не найден", JOptionPane.WARNING_MESSAGE);
            }
        });
        pathField.addActionListener(a -> go.doClick());

        // ---- быстрые переходы ----
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        quick.add(new JLabel("Быстрый переход:"));
        quick.add(quickButton("▲ Вверх", () -> {
            if (cur[0].getParent() != null) {
                cur[0] = cur[0].getParent();
                reload.run();
            }
        }));
        Path scenarios = scenariosDir();
        if (scenarios != null) {
            quick.add(quickButton("Раскладки", () -> {
                cur[0] = scenarios;
                reload.run();
            }));
        }
        quick.add(quickButton("Рабочий стол", () -> {
            Path desk = Paths.get(System.getProperty("user.home"), "Desktop");
            if (Files.isDirectory(desk)) {
                cur[0] = desk;
                reload.run();
            }
        }));
        quick.add(quickButton("Документы", () -> {
            Path docs = Paths.get(System.getProperty("user.home"), "Documents");
            if (Files.isDirectory(docs)) {
                cur[0] = docs;
                reload.run();
            }
        }));

        // ---- кнопки ----
        JButton ok = new JButton(save ? "Сохранить" : "Открыть");
        JButton cancel = new JButton("Отмена");
        ok.addActionListener(a -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                Entry e = list.getSelectedValue();
                if (e != null && !e.dir) {
                    name = e.file.getName();
                }
            }
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(d, Ui.text("Укажи имя файла."),
                    "Не выбран файл", JOptionPane.WARNING_MESSAGE);
                return;
            }
            name = withExtIfSaving(name, save, ext);
            Path chosen = cur[0].resolve(name);
            if (!save && !Files.exists(chosen)) {
                JOptionPane.showMessageDialog(d, Ui.text("Файла нет:\n" + chosen),
                    "Файл не найден", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (save && Files.exists(chosen)) {
                int r = JOptionPane.showConfirmDialog(d,
                    Ui.text("Файл уже есть:\n" + chosen + "\n\nПерезаписать?"),
                    "Подтверждение", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            result[0] = chosen;
            d.dispose();
        });
        cancel.addActionListener(a -> d.dispose());

        // ---- компоновка ----
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        JPanel addr = new JPanel(new BorderLayout(6, 0));
        addr.add(new JLabel("Путь: "), BorderLayout.WEST);
        addr.add(pathField, BorderLayout.CENTER);
        addr.add(go, BorderLayout.EAST);
        top.add(addr);
        top.add(Box.createVerticalStrut(4));
        top.add(quick);

        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.add(new JLabel("Имя файла: "), BorderLayout.WEST);
        nameRow.add(nameField, BorderLayout.CENTER);
        bottom.add(where);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(nameRow);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(ok);
        buttons.add(cancel);
        bottom.add(buttons);

        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createTitledBorder("Содержимое папки"));

        d.setLayout(new BorderLayout());
        d.add(top, BorderLayout.NORTH);
        d.add(sp, BorderLayout.CENTER);
        d.add(bottom, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(ok);
        d.getRootPane().registerKeyboardAction(a -> d.dispose(),
            KeyStroke.getKeyStroke("ESCAPE"), JPanel.WHEN_IN_FOCUSED_WINDOW);
        reload.run();
        d.setPreferredSize(new Dimension(kelium.gui.replay2.Theme.px(680),
            kelium.gui.replay2.Theme.px(520)));
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
        return result[0];
    }

    /**
     * ДОПИСАТЬ РАСШИРЕНИЕ — ТОЛЬКО ПРИ СОХРАНЕНИИ (баг найден дизайнером
     * 14.08.2026). При открытии имя уже взято из списка файлов как есть
     * (например «…6.yaml»); дописывание «.kmap» поверх готового имени давало
     * несуществующий путь «…6.yaml.kmap» и «Файл не найден» на файле, который
     * на экране только что был выбран. Вынесено отдельной чистой функцией —
     * не тестировать же логику имени через модальный диалог целиком.
     */
    static String withExtIfSaving(String name, boolean save, String ext) {
        if (save && ext != null && !name.toLowerCase(Locale.ROOT).endsWith("." + ext)) {
            return name + "." + ext;
        }
        return name;
    }

    /** Показывать ли файл: пустой список расширений — показываем всё. */
    private static boolean matches(String name, List<String> visible) {
        if (visible == null || visible.isEmpty()) {
            return true;
        }
        String low = name.toLowerCase(Locale.ROOT);
        for (String v : visible) {
            if (low.endsWith("." + v.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static JButton quickButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setMargin(new java.awt.Insets(1, 6, 1, 6));
        b.addActionListener(a -> action.run());
        return b;
    }

    /** Где лежат раскладки — спрашиваем у общего искателя данных, не у себя. */
    private static Path scenariosDir() {
        Path p = kelium.dataio.GameConfig.resolveDataRoot(null).resolve("scenarios");
        return Files.isDirectory(p) ? p.toAbsolutePath() : null;
    }

    /** Строка списка: папка или файл. */
    private static final class Entry {
        final File file;
        final boolean dir;
        final String label;

        Entry(File file, boolean dir) {
            this(file, dir, null);
        }

        Entry(File file, boolean dir, String label) {
            this.file = file;
            this.dir = dir;
            this.label = label;
        }

        @Override public String toString() {
            if (label != null) {
                return label;
            }
            if (dir) {
                return "[ " + file.getName() + " ]";
            }
            long kb = 0;
            try {
                kb = Math.max(1, Files.size(file.toPath()) / 1024);
            } catch (IOException ignored) {
                // размер не важен настолько, чтобы падать
            }
            return String.format("%-42s %6d КБ", file.getName(), kb);
        }
    }
}
