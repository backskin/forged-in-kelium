package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import kelium.agents.Genome;
import kelium.agents.HeuristicAgent;
import kelium.agents.RandomAgent;
import kelium.agents.StrategicAgent;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.GameEngine;
import kelium.engine.Setup;
import kelium.report.BatchResult;
import kelium.report.GameLogger;
import kelium.report.SvgFieldRenderer;
import kelium.report.TelemetryCollector;
import kelium.dataio.Locations;
import kelium.core.Agent;

/**
 * RunnerGui — приложение прогонов ботов и нейросетей.
 *
 * <p>Три режима: пакетный прогон партий (отчёт + логи всех партий + покадровая
 * SVG-отрисовка случайной партии), балансовый зонд и оценка полезности заданий.
 * Состав стола задаётся по местам, модели подбираются из реально существующих
 * файлов (несуществующее выбрать физически нельзя), перед стартом идёт проверка
 * готовности, а недостающие модели можно обучить прямо из меню «Инструменты».
 *
 * <p>Запуск: {@code java -cp <classpath> kelium.gui.RunnerGui}
 * (или {@code --nogui [players games seed]} для безголового прогона).
 */
public final class RunnerGui {

    // ==================== агенты по местам ====================

    /** Вариант бота для одного места. */
    private record SeatOption(String id, String label, String tip) {
        @Override public String toString() {
            return label;
        }
    }

    /**
     * СОСТАВ БОТОВ — из единого справочника {@link kelium.agents.BotCatalog}.
     *
     * <p>Раньше список был выписан здесь, второй раз в записи партии и третий раз в
     * лиге, и все три расходились: разные имена, разные наборы, а в выборе висели
     * две строки «Нейросеть» и «Нейросеть ONNX», разницу между которыми нельзя было
     * объяснить игроку — она была в формате файла модели. Обе ветки удалены
     * 13.08.2026: ни одна не проверялась в лиге и обе слабее обученного генома.
     */
    private static List<SeatOption> catalogSeats() {
        List<SeatOption> out = new ArrayList<>();
        for (var e : kelium.agents.BotCatalog.ALL) {
            out.add(new SeatOption(e.id(), e.label(), e.tip()));
        }
        return out;
    }

    // ==================== состояние окна ====================
    private JFrame frame;
    private final JSpinner players = new JSpinner(new SpinnerNumberModel(4, 2, 4, 1));
    private final JSpinner games = new JSpinner(new SpinnerNumberModel(200, 1, 100000, 50));
    private final JTextField seed = new JTextField("1000", 8);
    private final JComboBox<String> mode = new JComboBox<>(new String[]{
        "Прогон партий — отчёт, логи, кадры",
        "Балансовый зонд — сводка по балансу",
        "Оценка заданий — таблица полезности K",
        "Характеры ботов — кто и откуда берёт очки",
        // Три новых режима: единая шкала силы, карта стратегий и извлечённое из
        // партий понимание игры. «Партий» в этих режимах означает объём работы:
        // кругов лиги / кандидатов атласа / самоигр для модели.
        "Лига ботов — рейтинг Эло (единая шкала силы)",
        "Атлас стратегий — какие стили игры рабочие",
        "Что приносит победу — разбор по признакам",
        // Два стенда 14.08.2026. Раньше они были только из командной строки, то
        // есть недоступны без разработчика — а именно ими и проверяют, как
        // правка правил или карты сказалась на игре.
        "Составы ботов — воители, вредители и мирные за столом",
        "Карты рынка по одной — берут ли предложения",
        "Карты заданий по одной — выполняют или сжигают"});
    private final JComboBox<String> ruleset = new JComboBox<>();
    /** Раскладка поля: авторские плюс всё, что лежит в папках библиотеки. */
    private final JComboBox<FieldOption> fieldBox = new JComboBox<>();
    @SuppressWarnings("unchecked")
    private final JComboBox<SeatOption>[] seatBoxes = new JComboBox[4];
    private final JComboBox<SeatOption> allSeats = new JComboBox<>();
    /** Порядок ровно как в {@link #searchBox} — индекс списка и есть уровень. */
    private static final kelium.agents.Bots.Search[] SEARCH_LEVELS = {
        kelium.agents.Bots.Search.НЕТ, kelium.agents.Bots.Search.ОТСЕВ,
        kelium.agents.Bots.Search.СРЕДНИЙ, kelium.agents.Bots.Search.ГЛУБОКИЙ};
    private final JComboBox<String> searchBox = new JComboBox<>(new String[]{
        "без просчёта (быстро)", "отсев холостых ходов", "средний",
        "глубокий (сильнее всего)"});
    private final JCheckBox ruLogs = new JCheckBox("русская копия логов", true);
    private final JCheckBox vizGame = new JCheckBox("рисовать поле по раундам (случайная партия)", true);
    private final JTextField outDir = new JTextField(26);
    private final JButton start = new JButton("▶  Запустить");
    private final JButton stop = new JButton("■  Остановить");
    private final JButton openReport = new JButton("📄 Открыть отчёт");
    private final JButton openFolder = new JButton("📂 Папка результатов");
    private final JProgressBar bar = new JProgressBar(0, 100);
    private final JLabel stage = new JLabel(" ");
    private final JTextPane log = new JTextPane();
    private JMenuItem testsItem;
    private JMenuItem trainGenomeItem;

    private SwingWorker<?, ?> worker;
    private Path lastReport;
    private Path lastOutDir;

    // ==================== запуск ====================
    public static void main(String[] args) {
        for (String a : args) {
            if ("--nogui".equals(a)) {
                runHeadless(args);
                return;
            }
        }
        Ui.init();
        SwingUtilities.invokeLater(() -> new RunnerGui().createAndShow());
    }

    private static void runHeadless(String[] args) {
        List<String> pos = new ArrayList<>();
        for (String a : args) {
            if (!a.startsWith("--")) {
                pos.add(a);
            }
        }
        int p = pos.size() > 0 ? Integer.parseInt(pos.get(0)) : 4;
        int g = pos.size() > 1 ? Integer.parseInt(pos.get(1)) : 3;
        long s = pos.size() > 2 ? Long.parseLong(pos.get(2)) : 777L;
        List<String> seats = new ArrayList<>();
        String[] def = {"trained:hawk", "trained:opportunist", "trained:dove", "trained:balanced"};
        for (int i = 0; i < p; i++) {
            seats.add(def[i % def.length]);
        }
        RunParams rp = new RunParams(p, g, s, seats, GameConfig.DEFAULT_RULESET,
            freshOutDir(), true, true, null, null);
        try {
            Path r = new RunnerGui().runBatch(rp, System.out::println, () -> false, x -> { }, x -> { });
            System.out.println("ОТЧЁТ: " + (r == null ? "—" : r.toAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ==================== окно ====================
    private void createAndShow() {
        frame = new JFrame("Кристаллы Раздора — прогоны ботов и нейросетей");
        Ui.setAppIcon(frame, "runner");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (worker != null && !worker.isDone()) {
                    int r = JOptionPane.showConfirmDialog(frame,
                        Ui.text("Идёт прогон партий. Прервать его и выйти?"), "Выход",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (r != JOptionPane.YES_OPTION) {
                        return;
                    }
                    worker.cancel(true);
                }
                frame.dispose();
                System.exit(0);
            }
        });

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createTitledBorder("Ход работы"));

        frame.setLayout(new BorderLayout(0, 0));
        frame.add(buildForm(), BorderLayout.NORTH);
        frame.add(logScroll, BorderLayout.CENTER);
        frame.add(buildBottom(), BorderLayout.SOUTH);
        frame.setJMenuBar(buildMenu());

        refreshSeatOptions();
        outDir.setText(freshOutDir().toString());
        say("Готово к работе. Выбери режим и нажми «Запустить».", 0);
        sayModelsSummary();

        frame.setSize(1120, 760);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JComponent buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.X_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        form.add(buildWhatPanel());
        form.add(Box.createHorizontalStrut(8));
        form.add(buildSeatsPanel());
        form.add(Box.createHorizontalStrut(8));
        form.add(buildOutputPanel());
        return form;
    }

    private static TitledBorder titled(String t) {
        TitledBorder b = BorderFactory.createTitledBorder(t);
        b.setTitleFont(b.getTitleFont().deriveFont(Font.BOLD, 12f));
        return b;
    }

    private JComponent buildWhatPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(titled("Что и сколько"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        mode.setToolTipText("<html><b>Прогон партий</b> — полный батч: отчёт, логи всех партий,"
            + " SVG-кадры.<br><b>Балансовый зонд</b> — сводка по балансу: мёртвые задания,"
            + " неиспользуемые механики, перекос по местам.<br>"
            + "<b>Оценка заданий</b> — таблица K = награда/сложность по каждой карте.</html>");
        mode.addActionListener(e -> onModeChanged());
        c.gridx = 0;
        c.gridy = 0;
        p.add(new JLabel("Режим:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        p.add(mode, c);
        c.gridwidth = 1;

        players.setToolTipText("Число игроков за столом (2–4)");
        players.addChangeListener(e -> refreshSeatOptions());
        c.gridx = 0;
        c.gridy = 1;
        p.add(new JLabel("Игроков:"), c);
        c.gridx = 1;
        p.add(players, c);

        games.setToolTipText("<html>Сколько партий отыграть.<br>"
            + "50 — быстрая проверка · 200–500 — баланс · 2000+ — точные цифры</html>");
        c.gridx = 2;
        p.add(new JLabel("Партий:"), c);
        c.gridx = 3;
        p.add(games, c);

        c.gridx = 0;
        c.gridy = 2;
        p.add(new JLabel("Сид:"), c);
        c.gridx = 1;
        JPanel seedRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        seed.setToolTipText("Зерно случайности: одинаковый сид = точно повторяемый прогон");
        seedRow.add(seed);
        JButton dice = new JButton("🎲");
        dice.setToolTipText("Случайный сид");
        dice.setMargin(new Insets(1, 4, 1, 4));
        dice.addActionListener(e -> seed.setText(String.valueOf(new Random().nextInt(900000) + 1000)));
        seedRow.add(dice);
        c.gridwidth = 3;
        p.add(seedRow, c);
        c.gridwidth = 1;

        List<String> rs = GameConfig.availableRulesets(null);
        ruleset.setModel(new DefaultComboBoxModel<>(rs.isEmpty()
            ? new String[]{GameConfig.DEFAULT_RULESET} : rs.toArray(new String[0])));
        ruleset.setSelectedItem(GameConfig.DEFAULT_RULESET);
        ruleset.setToolTipText("Версия правил: набор значений и карт, по которым играют боты");
        c.gridx = 0;
        c.gridy = 3;
        p.add(new JLabel("Правила:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        p.add(ruleset, c);
        c.gridwidth = 1;

        fieldBox.setToolTipText("<html>Раскладка поля. Кроме авторских здесь всё, что "
            + "нарисовано конструктором<br>и лежит в папках библиотеки "
            + "(Инструменты → Папки с раскладками…).<br>"
            + "«любая (по сиду)» — как раньше: движок выбирает вариант сам.</html>");
        c.gridx = 0;
        c.gridy = 4;
        p.add(new JLabel("Поле:"), c);
        c.gridx = 1;
        c.gridwidth = 3;
        p.add(fieldBox, c);
        c.gridwidth = 1;
        reloadFields();
        players.addChangeListener(e -> reloadFields());
        ruleset.addActionListener(e -> reloadFields());

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        presets.add(new JLabel("Быстро:"));
        presets.add(preset("проверка 50", 50));
        presets.add(preset("баланс 300", 300));
        presets.add(preset("аудит 2000", 2000));
        presets.add(Box.createHorizontalStrut(12));
        presets.add(new JLabel("Просчёт:"));
        presets.add(searchBox);
        // ПРОСЧЁТ ВПЕРЁД раньше включался только настройкой запуска
        // -Dkelium.bots=просчёт, то есть из окна был недоступен вовсе. А это
        // самый сильный рычаг: перевес 69% по очкам против бота без просчёта.
        searchBox.setToolTipText(Ui.text("<html>Насколько дорого бот думает.<br>"
            + "<b>без просчёта</b> — только формула по весам, быстро: этим гоняют "
            + "стенды на десятки тысяч партий.<br>"
            + "<b>отсев холостых</b> — ходы проверяются на копии.<br>"
            + "<b>средний</b> — плюс доигрывание на раунд вперёд: отличает вложение "
            + "от растраты.<br>"
            + "<b>глубокий</b> — плюс доигрывание партии на выборе приказа. Сильнее "
            + "всего и медленнее всего.<br><br>"
            + "Действует и на прогоны, и на замеры, и на обучение.</html>"));
        // Список показывает то, что ДЕЙСТВУЕТ: если программу запустили с
        // -Dkelium.bots=просчёт, окно должно это отражать, а не врать «без просчёта».
        for (int i = 0; i < SEARCH_LEVELS.length; i++) {
            if (SEARCH_LEVELS[i] == kelium.agents.Bots.search()) {
                searchBox.setSelectedIndex(i);
            }
        }
        searchBox.addActionListener(e -> {
            kelium.agents.Bots.setSearch(SEARCH_LEVELS[searchBox.getSelectedIndex()]);
            say("Боты теперь играют: " + kelium.agents.Bots.describe()
                + ". Чем глубже просчёт, тем дольше прогон.", 0);
        });
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 4;
        p.add(presets, c);
        return p;
    }

    /**
     * Перечитать список раскладок под выбранное число игроков: авторские плюс
     * всё из ОБЩЕЙ библиотеки папок ({@link Locations}) — того же списка, что
     * видит проигрыватель партий.
     */
    private void reloadFields() {
        int n = playersValue();
        String version;
        try {
            version = GameConfig.buildCached(String.valueOf(ruleset.getSelectedItem()),
                n, 0L, null, null).ruleset.getStr("content_versions.scenarios", "1.0.0");
        } catch (RuntimeException e) {
            version = "1.0.0";
        }
        Object keep = fieldBox.getSelectedItem();
        fieldBox.removeAllItems();
        fieldBox.addItem(new FieldOption(null, "любая (по сиду)"));
        java.util.Set<String> authors = new java.util.LinkedHashSet<>();
        try {
            for (Map<String, Object> v : kelium.engine.Scenario.loadAllVariants(
                    n, version, GameConfig.resolveDataRoot(null))) {
                String id = String.valueOf(v.get("id"));
                authors.add(id);
                fieldBox.addItem(new FieldOption(id, "авторская · " + id));
            }
        } catch (RuntimeException ignored) {
            // список авторских не прочитан — останется «любая (по сиду)»
        }
        for (kelium.engine.LayoutLibrary.Entry e
                : kelium.engine.LayoutLibrary.scan(n, null)) {
            if (!authors.contains(e.id())) {
                fieldBox.addItem(new FieldOption(e.id(),
                    "своя · " + e.id() + "  (" + e.file().getFileName() + ")", e.file()));
            }
        }
        if (keep instanceof FieldOption fo && fo.id() != null) {
            for (int i = 0; i < fieldBox.getItemCount(); i++) {
                if (fo.id().equals(fieldBox.getItemAt(i).id())) {
                    fieldBox.setSelectedIndex(i);
                    return;
                }
            }
        }
        fieldBox.setSelectedIndex(0);
    }

    private JButton preset(String label, int n) {
        JButton b = new JButton(label);
        b.setMargin(new Insets(1, 6, 1, 6));
        b.addActionListener(e -> games.setValue(n));
        return b;
    }

    private JComponent buildSeatsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(titled("Состав стола"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        allSeats.setToolTipText("Поставить одного и того же бота на все места");
        allSeats.addActionListener(e -> {
            SeatOption s = (SeatOption) allSeats.getSelectedItem();
            if (s != null && allSeats.hasFocus()) {
                for (JComboBox<SeatOption> b : seatBoxes) {
                    b.setSelectedItem(s);
                }
            }
        });
        c.gridx = 0;
        c.gridy = 0;
        p.add(new JLabel("Всем:"), c);
        c.gridx = 1;
        p.add(allSeats, c);

        for (int i = 0; i < 4; i++) {
            seatBoxes[i] = new JComboBox<>();
            seatBoxes[i].setToolTipText("Кто играет на этом месте");
            c.gridx = 0;
            c.gridy = i + 1;
            p.add(new JLabel("Место " + (i + 1) + ":"), c);
            c.gridx = 1;
            p.add(seatBoxes[i], c);
        }
        return p;
    }

    private JComponent buildOutputPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(titled("Что сохранить"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        ruLogs.setToolTipText("Кроме английского лога каждой партии писать русскую копию");
        vizGame.setToolTipText("<html>Выбрать одну случайную партию прогона и сохранить"
            + " картинку поля<br>на каждый раунд (SVG с настоящими формами жетонов).</html>");
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        p.add(ruLogs, c);
        c.gridy = 1;
        p.add(vizGame, c);

        c.gridy = 2;
        c.gridwidth = 1;
        p.add(new JLabel("Папка:"), c);
        c.gridx = 1;
        outDir.setToolTipText("Куда сложить отчёт, логи и кадры (для каждого прогона своя папка)");
        p.add(outDir, c);
        c.gridx = 2;
        JButton pick = new JButton("…");
        pick.setMargin(new Insets(1, 5, 1, 5));
        pick.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(outDir.getText());
            fc.setDialogTitle("Куда сохранять результаты");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                outDir.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        p.add(pick, c);
        return p;
    }

    private JComponent buildBottom() {
        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));

        bar.setStringPainted(true);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        stage.setAlignmentX(Component.LEFT_ALIGNMENT);
        stage.setForeground(new Color(0x505050));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        start.setFont(start.getFont().deriveFont(Font.BOLD));
        start.addActionListener(e -> onStart());
        stop.setEnabled(false);
        stop.addActionListener(e -> {
            if (worker != null) {
                worker.cancel(true);
            }
        });
        openReport.setEnabled(false);
        openReport.addActionListener(e -> openPath(lastReport));
        openFolder.setEnabled(false);
        openFolder.addActionListener(e -> openPath(lastOutDir));
        buttons.add(start);
        buttons.add(stop);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(openReport);
        buttons.add(openFolder);

        south.add(stage);
        south.add(bar);
        south.add(buttons);
        return south;
    }

    private JMenuBar buildMenu() {
        JMenuBar bar2 = new JMenuBar();
        JMenu file = new JMenu("Файл");
        file.add(mi("Открыть папку результатов", e -> openPath(lastOutDir)));
        file.add(mi("Открыть последний отчёт", e -> openPath(lastReport)));
        file.addSeparator();
        file.add(mi("Очистить окно", e -> log.setText("")));
        file.addSeparator();
        file.add(mi("Выход", e -> {
            frame.dispatchEvent(new java.awt.event.WindowEvent(
                frame, java.awt.event.WindowEvent.WINDOW_CLOSING));
        }));
        bar2.add(file);

        JMenu tools = new JMenu("Инструменты");
        trainGenomeItem = mi("Обучить геном стратега (эволюция)…", e -> trainGenome());
        testsItem = mi("Проверить сборку (тесты)", e -> runTests());
        tools.add(trainGenomeItem);
        tools.addSeparator();
        tools.add(mi("Папки с раскладками и память ботов…", e -> {
            if (PlacesDialog.show(frame)) {
                reloadFields();
                say("Места хранения обновлены — список полей перечитан.", 0);
            }
        }));
        tools.addSeparator();
        // Обучение ВСЕХ характеров самоигрой друг против друга: у каждой линии
        // свой зал славы, чемпион обновляется только по отложенной проверке.
        tools.add(mi("Обучить ВСЕ характеры (самоигра)…", e -> trainAllCharacters()));
        tools.addSeparator();
        tools.add(mi("Показать найденные модели", e -> sayModelsSummary()));
        tools.addSeparator();
        tools.add(testsItem);
        bar2.add(tools);

        JMenu help = new JMenu("Справка");
        help.add(mi("Как пользоваться", e -> showGuide()));
        bar2.add(help);
        return bar2;
    }

    private JMenuItem mi(String text, Consumer<java.awt.event.ActionEvent> action) {
        return new JMenuItem(new AbstractAction(text) {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                action.accept(e);
            }
        });
    }

    // ==================== модели ====================

    /** Найденный файл модели. */
    private record ModelFile(Path path, String kind, int players) { }

    private static Path genomesDir() {
        return Locations.botMemory();
    }

    /** Просканировать каталог genomes: какие модели реально есть. */
    private static List<ModelFile> scanModels() {
        List<ModelFile> out = new ArrayList<>();
        Path dir = genomesDir();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var s = Files.list(dir)) {
            for (Path p : s.toList()) {
                String n = p.getFileName().toString();
                // Ищем только ГЕНОМЫ: нейросетевые ветки удалены 13.08.2026.
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^strategic_(\\d)p(_[a-z]+)?\\.json$").matcher(n);
                if (!m.matches()) {
                    continue;
                }
                out.add(new ModelFile(p, "genome", Integer.parseInt(m.group(1))));
            }
        } catch (IOException ignored) {
            // каталог нечитаем — вернём то, что успели
        }
        return out;
    }

    private static Path findModel(String kind, int players) {
        for (ModelFile f : scanModels()) {
            if (f.kind().equals(kind) && f.players() == players
                    && !f.path().getFileName().toString().contains("_")) {
                return f.path();
            }
        }
        for (ModelFile f : scanModels()) {
            if (f.kind().equals(kind) && f.players() == players) {
                return f.path();
            }
        }
        return null;
    }

    /** Перестроить списки мест под текущее число игроков. */
    private void refreshSeatOptions() {
        int n = playersValue();
        List<SeatOption> opts = catalogSeats();
        // По умолчанию за столом четыре РАЗНЫХ характера: так партия интереснее, и
        // сразу видно, чем характеры отличаются.
        String[] def = {"trained:hawk", "trained:opportunist", "trained:dove",
            "trained:balanced"};
        for (int i = 0; i < 4; i++) {
            Object prev = seatBoxes[i].getSelectedItem();
            seatBoxes[i].setModel(new DefaultComboBoxModel<>(opts.toArray(new SeatOption[0])));
            SeatOption keep = prev instanceof SeatOption so ? find(opts, so.id()) : null;
            seatBoxes[i].setSelectedItem(keep != null ? keep : find(opts, def[i % def.length]));
            seatBoxes[i].setEnabled(i < n && modeIndex() == 0);
        }
        allSeats.setModel(new DefaultComboBoxModel<>(opts.toArray(new SeatOption[0])));
        allSeats.setEnabled(modeIndex() == 0);
        allSeats.setToolTipText("Сверху сильнейшие: просчёт вперёд обходит "
            + "обученного бота по очкам примерно в 7 партиях из 10");
    }

    private static SeatOption find(List<SeatOption> opts, String id) {
        for (SeatOption o : opts) {
            if (o.id().equals(id)) {
                return o;
            }
        }
        return opts.isEmpty() ? null : opts.get(0);
    }

    private void sayModelsSummary() {
        List<ModelFile> all = scanModels();
        if (all.isEmpty()) {
            say("Каталог моделей пуст: " + genomesDir().toAbsolutePath(), 2);
            return;
        }
        StringBuilder sb = new StringBuilder("Найденные модели в " + genomesDir().toAbsolutePath() + ":");
        Map<Integer, List<String>> byPlayers = new java.util.TreeMap<>();
        for (ModelFile f : all) {
            byPlayers.computeIfAbsent(f.players(), k -> new ArrayList<>())
                .add(f.path().getFileName().toString());
        }
        for (var e : byPlayers.entrySet()) {
            sb.append("\n   ").append(e.getKey()).append("p: ").append(String.join(", ", e.getValue()));
        }
        say(sb.toString(), 0);
    }

    // ==================== параметры прогона ====================
    private record RunParams(int players, int games, long seed, List<String> seats,
                             String ruleset, Path outDir, boolean ruLogs, boolean vizGame,
                             String fieldId, Path fieldFile) { }

    /** Вариант раскладки в списке «поле» (общая библиотека, см. Locations). */
    private record FieldOption(String id, String label, Path file) {
        FieldOption(String id, String label) {
            this(id, label, null);
        }

        @Override public String toString() {
            return label;
        }
    }

    private static Path freshOutDir() {
        return Paths.get("reports", "gui-runs", "run-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
            .toAbsolutePath();
    }

    private int playersValue() {
        commit(players);
        return (Integer) players.getValue();
    }

    private int modeIndex() {
        return mode.getSelectedIndex();
    }

    private void onModeChanged() {
        boolean batch = modeIndex() == 0;
        for (int i = 0; i < 4; i++) {
            seatBoxes[i].setEnabled(batch && i < playersValue());
        }
        allSeats.setEnabled(batch);
        ruLogs.setEnabled(batch);
        vizGame.setEnabled(batch);
        outDir.setEnabled(batch);
        if (!batch) {
            String hint = switch (modeIndex()) {
                case 4 -> "«Партий» здесь означает ОБЪЁМ ЛИГИ: чем больше, тем "
                    + "больше кругов и точнее Эло. Участники — от случайного бота "
                    + "до просчитывающего, все на одной шкале.";
                case 5 -> "«Партий» здесь означает СКОЛЬКО СТРАТЕГИЙ перебрать. "
                    + "На выходе карта: какие стили игры рабочие и равны ли они.";
                case 6 -> "«Партий» здесь означает СКОЛЬКО САМОИГР собрать. "
                    + "На выходе таблица: какие признаки позиции реально "
                    + "предсказывают победу — прямой материал для баланса.";
                case 7 -> "«Партий» здесь — СКОЛЬКО ПАРТИЙ НА КАЖДЫЙ СОСТАВ, а "
                    + "составов двенадцать. Отвечает на вопрос, воюет ли бот сам "
                    + "или только когда его вынуждает сосед. Свод правил берётся "
                    + "выбранный выше — так и сравнивают две версии правил.";
                case 8 -> "«Партий» здесь — СКОЛЬКО ПАРТИЙ НА КАЖДУЮ КАРТУ. Колода "
                    + "рынка целиком составляется из одной карты, поэтому она "
                    + "действует все раунды: если её предложения всё равно не "
                    + "берут — дело в карте, а не в редкости.";
                case 9 -> "«Партий» здесь — СКОЛЬКО ПАРТИЙ НА КАЖДОЕ ЗАДАНИЕ. Копии "
                    + "карты по числу игроков кладутся в колоду, чтобы она дошла до "
                    + "руки. Видно, выполняют её или сжигают ради верхнего эффекта.";
                default -> "играет обученный стратег (6 характеров по кругу), "
                    + "результат — отчёт .md в reports/balance/.";
            };
            say("Режим «" + mode.getSelectedItem() + "»: " + hint, 0);
        }
    }

    private static void commit(JSpinner sp) {
        try {
            sp.commitEdit();
        } catch (java.text.ParseException e) {
            sp.setValue(sp.getValue());   // откатить недобранный текст
        }
    }

    // ==================== старт ====================
    private void onStart() {
        commit(players);
        commit(games);
        long seedVal;
        try {
            seedVal = Long.parseLong(seed.getText().trim().replace(" ", ""));
        } catch (NumberFormatException e) {
            problem("Сид должен быть целым числом.\nСейчас там: «" + seed.getText() + "»");
            seed.requestFocus();
            seed.selectAll();
            return;
        }
        int n = (Integer) players.getValue();
        int g = (Integer) games.getValue();
        String rs = String.valueOf(ruleset.getSelectedItem());

        if (modeIndex() != 0) {
            runProbe(modeIndex(), n, g);
            return;
        }

        List<String> seats = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            SeatOption o = (SeatOption) seatBoxes[i].getSelectedItem();
            seats.add(o == null ? "trained:balanced" : o.id());
        }
        Path out = freshOutDir();
        outDir.setText(out.toString());
        FieldOption fo = (FieldOption) fieldBox.getSelectedItem();
        RunParams p = new RunParams(n, g, seedVal, seats, rs, out, ruLogs.isSelected(),
            vizGame.isSelected(), fo == null ? null : fo.id(),
            fo == null ? null : fo.file());

        List<String> problems = preflight(p);
        if (!problems.isEmpty()) {
            problem("Прогон не запущен:\n\n• " + String.join("\n• ", problems));
            return;
        }

        setRunning(true);
        bar.setIndeterminate(false);
        bar.setValue(0);
        long t0 = System.currentTimeMillis();
        SwingWorker<Path, Object[]> w = new SwingWorker<>() {
            @Override protected Path doInBackground() throws Exception {
                return runBatch(p, m -> publish(new Object[]{"log", m, 0}),
                    this::isCancelled,
                    pct -> publish(new Object[]{"pct", null, pct}),
                    st -> publish(new Object[]{"stage", st, 0}));
            }

            @Override protected void process(List<Object[]> chunks) {
                for (Object[] c : chunks) {
                    switch ((String) c[0]) {
                        case "log" -> say((String) c[1], 0);
                        case "stage" -> stage.setText((String) c[1]);
                        case "pct" -> {
                            int pct = (Integer) c[2];
                            bar.setValue(pct);
                            long el = System.currentTimeMillis() - t0;
                            if (pct > 3) {
                                long left = (long) (el * (100.0 - pct) / pct);
                                bar.setString(pct + "%  ·  осталось ≈ " + human(left));
                            }
                        }
                        default -> { }
                    }
                }
            }

            @Override protected void done() {
                setRunning(false);
                try {
                    lastReport = get();
                    lastOutDir = p.outDir();
                    openFolder.setEnabled(Files.isDirectory(lastOutDir));
                    openReport.setEnabled(lastReport != null && Files.exists(lastReport));
                    if (lastReport != null) {
                        say("ГОТОВО. Отчёт: " + lastReport, 3);
                        stage.setText("Готово: " + p.games() + " партий за "
                            + human(System.currentTimeMillis() - t0));
                    }
                } catch (java.util.concurrent.CancellationException ce) {
                    say("Прогон остановлен пользователем.", 1);
                    lastOutDir = p.outDir();
                    openFolder.setEnabled(Files.isDirectory(lastOutDir));
                } catch (Exception ex) {
                    reportFailure(ex);
                }
            }
        };
        worker = w;
        w.execute();
    }

    /** Проверки готовности ДО первой партии: понятные проблемы вместо стектрейса. */
    private List<String> preflight(RunParams p) {
        List<String> out = new ArrayList<>();
        if (p.games() < 1) {
            out.add("Число партий должно быть ≥ 1.");
        }
        try {
            Files.createDirectories(p.outDir());
            Path probe = p.outDir().resolve(".writetest");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            out.add("Папка результатов недоступна для записи: " + p.outDir());
        }
        try {
            GameConfig.buildCached(p.ruleset(), p.players(), 0L, null, null);
        } catch (RuntimeException e) {
            out.add("Не читаются данные игры (" + GameConfig.resolveDataRoot(null)
                + "): " + e.getMessage());
        }
        if (findModel("genome", p.players()) == null) {
            out.add("Нет обученного генома strategic_" + p.players()
                + "p.json (обучить: Инструменты → Обучить геном).");
        }
        return out;
    }

    private void setRunning(boolean on) {
        start.setEnabled(!on);
        stop.setEnabled(on);
        testsItem.setEnabled(!on);
        trainGenomeItem.setEnabled(!on);
        mode.setEnabled(!on);
        players.setEnabled(!on);
        games.setEnabled(!on);
    }

    // ==================== сам прогон ====================
    @SuppressWarnings("unchecked")
    private Path runBatch(RunParams p, Consumer<String> say,
                          java.util.function.BooleanSupplier cancelled,
                          java.util.function.IntConsumer progress,
                          Consumer<String> stageMsg) throws IOException {
        Files.createDirectories(p.outDir());
        Path enDir = p.outDir().resolve("gamelogs");
        Path ruDir = p.outDir().resolve("gamelogs_ru");
        Files.createDirectories(enDir);
        if (p.ruLogs()) {
            Files.createDirectories(ruDir);
        }

        int vizIdx = p.vizGame() ? new Random(p.seed() * 7919L).nextInt(p.games()) : -1;

        stageMsg.accept("Загружаю правила и модели…");
        Genome genome = loadGenome(p.players(), say);

        BatchResult br = new BatchResult(p.ruleset(), p.players(), p.games());
        Map<Integer, List<Integer>> vpAccum = new HashMap<>();
        for (int seat = 0; seat < p.players(); seat++) {
            vpAccum.put(seat, new ArrayList<>());
        }
        int[] winsBySeat = new int[p.players()];
        int played = 0;

        say.accept(String.format("Прогон: %d игроков × %d партий, правила %s, сид %d",
            p.players(), p.games(), p.ruleset(), p.seed()));
        for (int i = 0; i < p.players(); i++) {
            say.accept("   место " + (i + 1) + ": " + labelOf(p.seats().get(i)));
        }
        if (vizIdx >= 0) {
            say.accept("Кадры поля будут сохранены для партии №" + (vizIdx + 1));
        }

        for (int g = 0; g < p.games(); g++) {
            if (cancelled.getAsBoolean()) {
                say.accept("Остановлено на партии " + (g + 1) + " из " + p.games() + ".");
                break;
            }
            long gseed = p.seed() + g;
            GameConfig cfg = GameConfig.buildCached(p.ruleset(), p.players(), gseed, null, null,
                p.fieldId(), null, p.fieldFile());
            GameState state = Setup.buildGame(cfg);
            List<Agent> agents = buildAgents(p, gseed, genome);

            String[] sides = new String[p.players()];
            for (int seat = 0; seat < p.players(); seat++) {
                sides[seat] = state.player(seat).board.troop.side;
            }

            GameLogger en = new GameLogger(state, GameLogger.defaultLogPath(state, enDir), "en");
            GameLogger ru = p.ruLogs()
                ? new GameLogger(state, GameLogger.defaultLogPath(state, ruDir), "ru") : null;
            TelemetryCollector col = new TelemetryCollector();
            final Path vizDir;
            if (g == vizIdx) {
                vizDir = p.outDir().resolve("kadry-partii-" + (g + 1) + "-seed" + gseed);
                Files.createDirectories(vizDir);
            } else {
                vizDir = null;
            }
            Consumer<Map<String, Object>> broadcast = event -> {
                en.record(event);
                if (ru != null) {
                    ru.record(event);
                }
                col.record(event);
                // Кадр рисуем в КОНЦЕ раунда (событие «return»), а не в начале:
                // Обновление восстанавливает келемий на тайлах и снимает урон,
                // поэтому кадр из начала раунда всегда выглядел «нетронутым».
                if (vizDir != null && "return".equals(event.get("type"))) {
                    int rnd = event.get("round") instanceof Number nn ? nn.intValue() : 0;
                    writeSvg(vizDir.resolve(String.format("round%02d.svg", rnd)),
                        SvgFieldRenderer.render(state, rnd), say);
                }
            };

            Map<String, Object> result;
            try {
                result = GameEngine.playGame(state, agents, broadcast);
            } finally {
                en.close();
                if (ru != null) {
                    ru.close();
                }
            }
            if (vizDir != null) {
                writeSvg(vizDir.resolve("final.svg"),
                    SvgFieldRenderer.render(state, state.round), say);
                say.accept("Кадры партии №" + (g + 1) + ": " + vizDir);
            }

            TelemetryCollector.GameReport rep = col.report();
            int winner = (Integer) result.get("winner");
            winsBySeat[winner]++;
            br.seatWins.merge(winner, 1, Integer::sum);
            br.sideWins.merge(sides[winner], 1, Integer::sum);
            br.conditionCounts.merge(String.valueOf(result.get("condition")), 1, Integer::sum);
            br.margins.add(rep.margin());
            for (var e : rep.scores.entrySet()) {
                vpAccum.get(e.getKey()).add(e.getValue().getOrDefault("total", 0));
                for (var se : e.getValue().entrySet()) {
                    if (!"total".equals(se.getKey())) {
                        br.vpSourceTotals.merge(se.getKey(), se.getValue(), Integer::sum);
                    }
                }
            }
            for (var e : rep.actionCounts.entrySet()) {
                br.actionTotals.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            for (var e : rep.failedActions.entrySet()) {
                br.failedActionTotals.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            played++;

            progress.accept((int) Math.round(100.0 * (g + 1) / p.games()));
            StringBuilder w = new StringBuilder();
            for (int s = 0; s < p.players(); s++) {
                w.append(s > 0 ? " / " : "").append(Math.round(100.0 * winsBySeat[s] / played))
                 .append('%');
            }
            stageMsg.accept("Партия " + (g + 1) + " из " + p.games()
                + "   ·   победы по местам: " + w);
        }

        if (played == 0) {
            return null;
        }
        stageMsg.accept("Пишу отчёт…");
        for (int seat = 0; seat < p.players(); seat++) {
            List<Integer> xs = vpAccum.get(seat);
            br.avgVpBySeat.put(seat, xs.isEmpty() ? 0
                : xs.stream().mapToInt(Integer::intValue).average().orElse(0));
        }
        Path report = p.outDir().resolve("report.md");
        StringBuilder md = new StringBuilder();
        md.append("# Прогон ботов\n\n");
        md.append("- игроков: **").append(p.players()).append("**\n");
        md.append("- партий отыграно: **").append(played).append("** из ").append(p.games())
          .append(played < p.games() ? " (прогон остановлен)" : "").append('\n');
        md.append("- сид: `").append(p.seed()).append("`, правила: `").append(p.ruleset())
          .append("`\n");
        md.append("- состав стола:\n");
        for (int i = 0; i < p.players(); i++) {
            md.append("  - место ").append(i + 1).append(": ")
              .append(labelOf(p.seats().get(i))).append('\n');
        }
        md.append("\n").append(br.renderMarkdown());
        Files.writeString(report, md.toString(), StandardCharsets.UTF_8);
        say.accept("Логи партий: " + enDir + (p.ruLogs() ? "  (+ русская копия)" : ""));
        return report;
    }

    private Genome loadGenome(int players, Consumer<String> say) {
        Path gp = genomesDir().resolve("strategic_" + players + "p.json");
        try {
            return Genome.loadJson(gp);
        } catch (Exception e) {
            say.accept("ВНИМАНИЕ: геном " + gp.getFileName()
                + " не загрузился — боты играют настройками по умолчанию (цифры хуже реальных).");
            return Genome.defaults();
        }
    }

    private List<Agent> buildAgents(RunParams p, long gseed, Genome genome) {
        List<Agent> agents = new ArrayList<>();
        for (int seat = 0; seat < p.players(); seat++) {
            // Кого посадить — решает единый справочник ботов: и окно, и лига, и
            // запись партии обязаны собирать одного и того же бота по одному имени.
            agents.add(kelium.agents.BotCatalog.create(p.seats().get(seat), seat,
                new Random(gseed * 131 + seat + 1), p.players()));
        }
        return agents;
    }

    private static String labelOf(String id) {
        return kelium.agents.BotCatalog.label(id);
    }

    private void writeSvg(Path path, String svg, Consumer<String> say) {
        try {
            Files.writeString(path, svg, StandardCharsets.UTF_8);
        } catch (IOException e) {
            say.accept("не записан кадр " + path.getFileName() + ": " + e.getMessage());
        }
    }

    // ==================== зонды (баланс / задания) ====================
    private void runProbe(int modeIdx, int n, int g) {
        String what = switch (modeIdx) {
            case 1 -> "Балансовый зонд";
            case 3 -> "Отчёт по характерам";
            case 4 -> "Лига ботов (Эло)";
            case 5 -> "Атлас стратегий";
            case 6 -> "Что приносит победу";
            case 7 -> "Составы ботов";
            case 8 -> "Карты рынка по одной";
            case 9 -> "Карты заданий по одной";
            default -> "Оценка заданий";
        };
        setRunning(true);
        bar.setIndeterminate(true);
        bar.setString(what + " идёт…");
        stage.setText(what + ": " + n + " игроков, " + g + " партий. Это может занять время.");
        say.accept(what + " запущен: " + n + "p × " + g + " партий…");
        SwingWorker<Path, String> w = new SwingWorker<>() {
            // throws Exception нарочно: новые режимы (лига, атлас, модель) пишут
            // отчёты на диск и могут упасть по вводу-выводу. Ошибка доедет до
            // done() через get() и покажется человеку, а не потеряется молча.
            @Override protected Path doInBackground() throws Exception {
                if (modeIdx == 1) {
                    kelium.BalanceProbe.main(new String[]{String.valueOf(n),
                        String.valueOf(g), "strategic"});
                    return Paths.get("reports", "balance",
                        "probe_p" + n + "_strategic_" + g + "g.md").toAbsolutePath();
                }
                if (modeIdx == 3) {
                    // Каждый характер по очереди садится на место 1: что делает,
                    // что строит и ОТКУДА берёт победные очки.
                    kelium.CharacterReport.main(new String[]{String.valueOf(n),
                        String.valueOf(g)});
                    return Paths.get("reports", "balance", "characters.md").toAbsolutePath();
                }
                if (modeIdx == 4) {
                    // ЛИГА: «партий» здесь = кругов. За круг каждый участник
                    // играет по разу на каждом месте за каждым столом, поэтому
                    // партий выходит намного больше — 6-24 кругов достаточно.
                    int rounds = Math.max(2, Math.min(60, g / 20 + 2));
                    kelium.agents.Arena.main(new String[]{String.valueOf(n),
                        String.valueOf(rounds),
                        "random,heuristic:economist,default,balanced,hawk,dove,"
                            + "search:balanced,deep:balanced"});
                    return Paths.get("reports", "balance", "лига-" + n + "p.md")
                        .toAbsolutePath();
                }
                if (modeIdx == 5) {
                    // АТЛАС: «партий» = сколько кандидатов перебрать.
                    kelium.agents.MapElites.main(new String[]{String.valueOf(n),
                        String.valueOf(Math.max(40, g)), "4", "12"});
                    return Paths.get("reports", "balance",
                        "атлас-стратегий-" + n + "p.md").toAbsolutePath();
                }
                if (modeIdx == 6) {
                    // РАЗБОР ПО ПРИЗНАКАМ: «партий» = сколько самоигр собрать.
                    kelium.agents.ValueTrainer.main(new String[]{String.valueOf(n),
                        String.valueOf(Math.max(60, g)), "300", "24"});
                    return Paths.get("reports", "balance",
                        "что-приносит-победу-" + n + "p.md").toAbsolutePath();
                }
                if (modeIdx == 7) {
                    // СОСТАВЫ: двенадцать столов — только воители, только
                    // вредители, только мирные, пополам и «один среди чужих».
                    // Отвечает на вопрос «бот воюет сам или только когда его
                    // вынуждает сосед», на который средние по всем прогонам не
                    // отвечают никогда.
                    String rs = String.valueOf(ruleset.getSelectedItem());
                    kelium.MatchLab.main(new String[]{rs, String.valueOf(n),
                        String.valueOf(g)});
                    return Paths.get("reports", "balance",
                        "лаборатория-составов-" + rs + ".md").toAbsolutePath();
                }
                if (modeIdx == 8) {
                    kelium.CardLab.main(new String[]{"рынок", String.valueOf(n),
                        String.valueOf(g)});
                    return Paths.get("reports", "balance",
                        "лаборатория-карт-рынка.md").toAbsolutePath();
                }
                if (modeIdx == 9) {
                    kelium.CardLab.main(new String[]{"задания", String.valueOf(n),
                        String.valueOf(g)});
                    return Paths.get("reports", "balance",
                        "лаборатория-карт-заданий.md").toAbsolutePath();
                }
                kelium.ObjectiveValue.main(new String[]{String.valueOf(n), String.valueOf(g)});
                return Paths.get("reports", "balance", "objective_value.md").toAbsolutePath();
            }

            @Override protected void done() {
                setRunning(false);
                bar.setIndeterminate(false);
                bar.setValue(100);
                try {
                    Path r = get();
                    lastReport = Files.exists(r) ? r : null;
                    lastOutDir = r.getParent();
                    openReport.setEnabled(lastReport != null);
                    openFolder.setEnabled(Files.isDirectory(lastOutDir));
                    say(lastReport != null ? "ГОТОВО. Отчёт: " + lastReport
                        : "Готово, но отчёт не найден — смотри reports/balance/", 3);
                    stage.setText("Готово.");
                } catch (Exception ex) {
                    reportFailure(ex);
                }
            }
        };
        worker = w;
        w.execute();
    }

    // ==================== обучение моделей ====================

    /**
     * Обучить ВСЕ характеры самоигрой друг против друга. Долгая операция: она
     * переписывает геномы всех линий, поэтому спрашиваем подтверждение и говорим,
     * сколько это займёт.
     */
    private void trainAllCharacters() {
        // ЧТО НАСТРАИВАЕТСЯ. Раньше окно спрашивало только число партий: какие
        // линии учить и с какой целью — было зашито в коде, то есть менялось
        // только пересборкой. Теперь и то и другое здесь.
        JTextField games = new JTextField("5000", 8);
        java.util.List<String> lines = kelium.agents.Bots.CHARACTERS;
        JComboBox<String> whom = new JComboBox<>();
        whom.addItem("все характеры");
        for (String ch : lines) {
            whom.addItem("только " + kelium.agents.BotCatalog.labelOfCharacter(ch));
        }
        JComboBox<String> goal = new JComboBox<>(new String[]{
            "как заведено (воитель учится войне, остальные победе)",
            "все учатся ПОБЕДЕ (отрыв по очкам)",
            "все учатся ВОЙНЕ (отрыв через агрессию и найм)"});

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints fc = new GridBagConstraints();
        fc.insets = new java.awt.Insets(4, 4, 4, 4);
        fc.anchor = GridBagConstraints.WEST;
        fc.gridx = 0;
        fc.gridy = 0;
        fc.gridwidth = 2;
        form.add(new JLabel("<html><div style='width:430px'>"
            + "Обучение самоигрой друг против друга. Оно <b>продолжает</b> с того "
            + "места, где остановилось: начальная популяция берётся из чемпионов на "
            + "диске. Геном линии переписывается, только если новая версия окажется "
            + "сильнее прежней на партиях, которых отбор не видел."
            + "</div></html>"), fc);
        fc.gridwidth = 1;
        fc.gridy = 1;
        form.add(new JLabel("Партий на характер:"), fc);
        fc.gridx = 1;
        form.add(games, fc);
        fc.gridx = 0;
        fc.gridy = 2;
        form.add(new JLabel("Кого учить:"), fc);
        fc.gridx = 1;
        form.add(whom, fc);
        fc.gridx = 0;
        fc.gridy = 3;
        form.add(new JLabel("Цель отбора:"), fc);
        fc.gridx = 1;
        form.add(goal, fc);
        fc.gridx = 0;
        fc.gridy = 4;
        fc.gridwidth = 2;
        form.add(new JLabel("<html><div style='width:430px'><i>"
            + "5000 партий — примерно четверть часа, 20000 — час с лишним. Одна "
            + "линия учится во столько раз быстрее, во сколько их меньше. "
            + "Соперниками всегда остаются ВСЕ линии, иначе доучивание одной "
            + "превратилось бы в игру с самим собой.</i></div></html>"), fc);

        if (JOptionPane.showConfirmDialog(frame, form, "Обучение характеров",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }
        long per;
        try {
            per = Long.parseLong(games.getText().trim());
        } catch (NumberFormatException e) {
            problem("Нужно целое число партий.");
            return;
        }
        java.util.List<String> only = whom.getSelectedIndex() == 0
            ? java.util.List.of() : java.util.List.of(lines.get(whom.getSelectedIndex() - 1));
        kelium.agents.Fitness.Goal goalPick = switch (goal.getSelectedIndex()) {
            case 1 -> kelium.agents.Fitness.Goal.ПОБЕДА;
            case 2 -> kelium.agents.Fitness.Goal.ВОЙНА;
            default -> null;
        };
        setRunning(true);
        bar.setIndeterminate(true);
        stage.setText("Самоигра всех характеров: по " + per + " партий на характер…");
        say("Самоигра запущена. Окно останется отзывчивым; итог — "
            + "reports/balance/самоигра.log", 0);
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                kelium.agents.SelfPlayTrainer.trainFromGui(per, only, goalPick);
                return null;
            }

            @Override protected void done() {
                setRunning(false);
                bar.setIndeterminate(false);
                try {
                    get();
                    kelium.agents.Bots.forgetCache();
                    say("Характеры обучены. Геномы линий обновлены в памяти ботов.", 3);
                    refreshSeatOptions();
                } catch (Exception ex) {
                    reportFailure(ex);
                }
                stage.setText("Готово.");
            }
        };
        worker = w;
        w.execute();
    }

    private void trainGenome() {
        int n = playersValue();
        String s = JOptionPane.showInputDialog(frame,
            Ui.text("Обучение генома стратега для " + n + " игроков.\n"
                + "Сколько поколений эволюции? 8 — быстро и грубо, 20 — заметно лучше."),
            "12");
        if (s == null) {
            return;
        }
        int gens;
        try {
            gens = Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            problem("Нужно целое число поколений.");
            return;
        }
        setRunning(true);
        bar.setIndeterminate(true);
        stage.setText("Обучение генома: " + gens + " поколений…");
        say("Обучение генома стратега (" + n + "p, " + gens + " поколений). Это надолго — "
            + "окно останется отзывчивым.", 0);
        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                kelium.agents.EvoTrainer.main(new String[]{String.valueOf(n),
                    String.valueOf(gens), "8", "12", "-"});
                return null;
            }

            @Override protected void done() {
                setRunning(false);
                bar.setIndeterminate(false);
                try {
                    get();
                    say("Геном обучён и сохранён: strategic_" + n + "p.json", 3);
                    refreshSeatOptions();
                } catch (Exception ex) {
                    reportFailure(ex);
                }
                stage.setText("Готово.");
            }
        };
        worker = w;
        w.execute();
    }

    // ==================== тесты сборки ====================
    private void runTests() {
        setRunning(true);
        bar.setIndeterminate(true);
        stage.setText("Идут тесты сборки…");
        say("Запуск тестов (mvn test)…", 0);
        SwingWorker<Integer, String> w = new SwingWorker<>() {
            @Override protected Integer doInBackground() throws Exception {
                String mvn = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                    .contains("win") ? "mvn.cmd" : "mvn";
                ProcessBuilder pb = new ProcessBuilder(mvn, "-q", "test");
                Path wd = findProjectDir();
                if (wd != null) {
                    pb.directory(wd.toFile());
                }
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader rd = new BufferedReader(new InputStreamReader(
                        proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = rd.readLine()) != null) {
                        if (line.contains("Tests run") || line.contains("ERROR")
                                || line.contains("BUILD")) {
                            publish(line.trim());
                        }
                    }
                }
                return proc.waitFor();
            }

            @Override protected void process(List<String> chunks) {
                for (String m : chunks) {
                    say("   " + m, 0);
                }
            }

            @Override protected void done() {
                setRunning(false);
                bar.setIndeterminate(false);
                try {
                    int code = get();
                    say(code == 0 ? "ТЕСТЫ ЗЕЛЁНЫЕ — сборка исправна."
                        : "ТЕСТЫ УПАЛИ (код " + code + ") — смотри строки выше.",
                        code == 0 ? 3 : 2);
                } catch (Exception ex) {
                    say("Не удалось запустить mvn: " + rootCause(ex).getMessage()
                        + ". Тесты доступны только из исходников проекта.", 2);
                }
                stage.setText("Готово.");
            }
        };
        worker = w;
        w.execute();
    }

    private static Path findProjectDir() {
        Path p = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && p != null; i++) {
            if (Files.exists(p.resolve("pom.xml"))) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }

    // ==================== служебное ====================
    private final Consumer<String> say = m -> say(m, 0);

    /** level: 0 обычный, 1 предупреждение, 2 ошибка, 3 успех. */
    private void say(String msg, int level) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> say(msg, level));
            return;
        }
        StyledDocument doc = log.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        switch (level) {
            case 1 -> StyleConstants.setForeground(a, new Color(0x9a6a00));
            case 2 -> {
                StyleConstants.setForeground(a, new Color(0xb00020));
                StyleConstants.setBold(a, true);
            }
            case 3 -> {
                StyleConstants.setForeground(a, new Color(0x1b6b2a));
                StyleConstants.setBold(a, true);
            }
            default -> StyleConstants.setForeground(a, new Color(0x222222));
        }
        try {
            doc.insertString(doc.getLength(), msg + "\n", a);
            if (doc.getLength() > 400_000) {
                doc.remove(0, 100_000);   // не растим окно бесконечно
            }
        } catch (javax.swing.text.BadLocationException ignored) {
            // пишем в конец — не бывает
        }
        log.setCaretPosition(doc.getLength());
    }

    private void problem(String text) {
        say(text.replace("\n", " "), 2);
        JOptionPane.showMessageDialog(frame, Ui.text(text, 420), "Проверь настройки",
            JOptionPane.WARNING_MESSAGE);
    }

    private void reportFailure(Exception ex) {
        Throwable c = rootCause(ex);
        String human = humanize(c);
        say("ОШИБКА: " + human, 2);
        stage.setText("Прогон прерван ошибкой.");
        JTextPane details = new JTextPane();
        details.setText(c.getClass().getSimpleName() + ": " + c.getMessage());
        details.setEditable(false);
        JPanel pane = new JPanel(new BorderLayout(0, 6));
        pane.add(new JLabel("<html><div style='width:420px'>" + human + "</div></html>"),
            BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(details);
        sp.setPreferredSize(new Dimension(440, 90));
        sp.setBorder(BorderFactory.createTitledBorder("Подробности"));
        pane.add(sp, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(frame, pane, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }

    private static String humanize(Throwable c) {
        String m = String.valueOf(c.getMessage());
        if (m.contains("ONNX")) {
            return "Не загрузилась ONNX-модель. Её нет для выбранного числа игроков — "
                + "выбери другого бота на это место (ONNX готовится отдельно, в Python).";
        }
        if (m.contains("нейросет")) {
            return "Не загрузилась нейросеть. Обучи её: меню «Инструменты → Обучить нейросеть».";
        }
        if (c instanceof java.nio.file.AccessDeniedException) {
            return "Нет прав на запись в папку результатов. Выбери другую папку.";
        }
        if (m.contains("сценари") || m.contains("scenario")) {
            return "Не читается раскладка поля для этого числа игроков. "
                + "Проверь файлы в simulator/data/scenarios/.";
        }
        return m;
    }

    private static String human(long ms) {
        long s = ms / 1000;
        if (s < 60) {
            return s + " с";
        }
        return (s / 60) + " мин " + (s % 60) + " с";
    }

    private void openPath(Path p) {
        if (p == null || !Files.exists(p)) {
            say("Открывать нечего — сначала выполни прогон.", 1);
            return;
        }
        try {
            Desktop.getDesktop().open(p.toFile());
        } catch (Exception e) {
            say("Не открылось: " + p, 1);
        }
    }

    private void showGuide() {
        String html = """
            <html><body style='font-family:sans-serif; font-size:12px; width:600px'>
            <h1>Прогоны ботов — полная справка</h1>
            <p>Программа многократно играет партии сама с собой и собирает статистику.
            Это инструмент <b>балансировки</b>: любое изменение правил или карт можно
            проверить не на живых людях, а на сотнях машинных партий.</p>

            <h2>1. Три режима</h2>
            <h3>Прогон партий</h3>
            <p>Основной режим. Играет заданное число партий выбранным составом и пишет:</p>
            <ul>
              <li><b>report.md</b> — сводный отчёт: победы по местам и по сторонам планшетов,
                  средние победные очки, из чего эти очки набраны, частота действий;</li>
              <li><b>gamelogs/</b> — подробный лог <i>каждой</i> партии, ход за ходом
                  (плюс русская копия, если стоит галочка);</li>
              <li><b>kadry-partii-N/</b> — если включена галочка кадров, программа сама
                  выбирает одну случайную партию прогона и сохраняет картинку поля
                  <b>на каждый раунд</b>: настоящие силуэты жетонов, цвета игроков,
                  здания на своих сторонах гекса, урон, энергия, панели игроков.</li>
            </ul>

            <h3>Балансовый зонд</h3>
            <p>Не про «кто выиграл», а про <b>здоровье игры</b>. Показывает: перекос побед
            по местам за столом (насколько важен порядок хода), какими путями вообще
            выигрывают, какие механики никто не использует, какие задания <i>ни разу</i>
            не были выполнены, на какой фазе партии что происходит. Если механика годами
            не используется — её либо чинят, либо убирают.</p>

            <h3>Оценка заданий</h3>
            <p>Таблица по каждой карте задания с коэффициентом <b>K = награда / сложность</b>:</p>
            <ul>
              <li><b>K ≈ 1</b> — карта сбалансирована;</li>
              <li><b>K ≫ 1</b> — «имба»: слишком легко за слишком много (боты фармят её);</li>
              <li><b>K ≪ 1</b> — переоценена: трудно и невыгодно, никто не берёт;</li>
              <li><b>вып. = 0</b> — мёртвая карта: за весь прогон не выполнена ни разу.</li>
            </ul>

            <h2>2. Состав стола — кто за кого играет</h2>
            <p>Каждому месту назначается свой бот. Это главный инструмент сравнения:
            посади нового бота на место 1, старых — на остальные, и смотри его долю побед.
            Кнопка «Всем» ставит одного бота на все места (тогда сравниваются
            <i>места</i>, а не боты — так проверяют честность порядка хода).</p>

            <h3>Стратеги (обученные эволюцией)</h3>
            <p>Все четверо используют один обученный «геном» — набор весов, найденный
            самообучением, — но с разным <b>характером</b>: характер сдвигает веса
            в свою сторону.</p>
            <ul>
              <li><b>Ястреб</b> — агрессор: охотно воюет, строит военные здания,
                  тратит боеприпасы, штурмует ЦУ.</li>
              <li><b>Голубь</b> — мирный: экономика, добыча келемия, треки науки;
                  в драку лезет только по необходимости.</li>
              <li><b>Оппортунист</b> — бьёт того, кто вырвался вперёд, и ловит выгодный
                  момент; хорош для проверки «а не слишком ли безнаказанно лидировать».</li>
              <li><b>Сбалансированный</b> — без перекосов, эталон для сравнения.</li>
            </ul>

            <h3>Особые характеры</h3>
            <ul>
              <li><b>Исследователь</b> — играет не на победу, а на <i>охват</i>: старается
                  за партию потрогать как можно больше разных механик (все треки, все
                  курсы рынка, атаки всеми родами, контейнеры, смена энергии).
                  Незаменим, когда проверяешь, «а работает ли вообще эта механика».</li>
              <li><b>Хаос</b> — вредитель: цель не свои очки, а чужие потери. Показывает,
                  насколько игра устойчива к целенаправленной агрессии.</li>
            </ul>

            <h3>Простые боты</h3>
            <ul>
              <li><b>Эвристики</b> (агрессор / защитник / эконом) — боты без обучения,
                  на жёстких правилах. Полезны как стабильная «линейка»: их сила не
                  меняется от переобучения, поэтому по ним удобно мерить прогресс.</li>
              <li><b>Случайный</b> — ходит наугад. Нижняя планка: если правка не бьёт
                  случайного бота, что-то очень не так.</li>
            </ul>

            <p><b>Нейросетевые боты убраны 13.08.2026.</b> Их было два вида, и разницу
            между ними — формат файла модели — нельзя было объяснить за столом. Ни один
            не проверялся в лиге, и оба слабее обученного генома. Сильнейший бот в
            системе — «Просчёт вперёд».</p>

            <h2>3. Остальные настройки</h2>
            <ul>
              <li><b>Игроков</b> — состав стола 2/3/4. Поле, колоды и часть карт зависят
                  от числа игроков, поэтому баланс проверяют на всех составах.</li>
              <li><b>Партий</b> — чем больше, тем меньше случайности в цифрах.
                  50 — «жив ли прогон», 300 — рабочая оценка, 2000+ — надёжные проценты.</li>
              <li><b>Сид</b> — зерно случайности. Один и тот же сид даёт <i>точно</i> тот
                  же прогон: удобно, когда хочешь сравнить две версии правил на
                  одинаковых раскладах. Кнопка 🎲 берёт новый сид.</li>
              <li><b>Правила</b> — версия набора правил и карт. Старые версии не
                  удаляются, поэтому можно прогнать 1.4.0 и 1.5.0 и сравнить отчёты.</li>
              <li><b>Быстро</b> — пресеты числа партий.</li>
            </ul>

            <h2>4. Если модели нет</h2>
            <p>Меню <b>Инструменты</b>:</p>
            <ul>
              <li><b>Обучить геном стратега</b> — эволюционное самообучение. Геном нужен
                  <i>всем</i> ботам-стратегам, поэтому без него прогон честно ругается.
                  8 поколений — быстро и грубо, 20 — заметно лучше.</li>
              <li><b>Обучить нейросеть</b> — обучение сети для выбранного числа игроков.</li>
              <li><b>Показать найденные модели</b> — что вообще лежит в каталоге моделей.</li>
              <li><b>Проверить сборку (тесты)</b> — техническая кнопка: гоняет тесты
                  движка. Нужна, только если правился код.</li>
            </ul>

            <h2>5. Как читать результаты</h2>
            <ul>
              <li><b>Победы по местам</b> — при одинаковых ботах должны быть близки к
                  равным. Перекос = преимущество порядка хода.</li>
              <li><b>Средняя маржа</b> — разрыв между первым и вторым. Маленькая маржа =
                  напряжённые партии.</li>
              <li><b>Источники очков</b> — за счёт чего выигрывают. Если один источник
                  даёт больше половины очков, остальные пути победы декоративны.</li>
              <li><b>Действия</b> — какие действия вообще играются. Ноль у действия —
                  повод разбираться.</li>
            </ul>
            <p>Кнопки внизу открывают отчёт и папку прогона. У каждого прогона своя папка
            со временем в имени, поэтому прошлые результаты не затираются.</p>

            <h2>6. Честность цифр</h2>
            <p>Программа предупреждает в логе, если что-то пошло не по плану: не нашёлся
            геном (боты играют настройками по умолчанию — цифры хуже реальных), не
            загрузилась модель и так далее. Такие строки нельзя игнорировать: отчёт
            в этом случае описывает не тех ботов, которых ты выбрал.</p>
            </body></html>
            """;
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(pane);
        sp.setPreferredSize(new Dimension(680, 640));
        JDialog d = new JDialog(frame, "Справка", false);
        d.add(sp);
        d.pack();
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
    }
}
