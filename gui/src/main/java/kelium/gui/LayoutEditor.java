package kelium.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import kelium.core.Field;
import kelium.dataio.AppSettings;
import kelium.dataio.FieldFile;
import kelium.engine.Scenario;
import kelium.gui.replay2.ScaleDialog;
import kelium.gui.replay2.Theme;

/**
 * LayoutEditor — визуальный конструктор начальных раскладок поля.
 *
 * <p>Две зоны работы: «размещение гексов» (приклеивание гексов друг к другу по
 * пунктирным призракам) и «заселение гексов» (тайлы зарождения обычные и
 * стартовые, двойные стопки, правка келемия, старты игроков, запретные гексы,
 * нейтральные здания с поворотом). Сохранение/загрузка — YAML в
 * формате сценариев симулятора; живой журнал проверяет раскладку в реальном
 * времени и в конце прогоняет её через НАСТОЯЩИЙ загрузчик
 * {@link Scenario#buildFieldFromScenario} — что сохранено, то сим и прочитает.
 *
 * <p>Запуск: {@code java -cp <classpath> kelium.gui.LayoutEditor}
 */
public final class LayoutEditor {

    private LayoutEditor() {
    }

    // ==================== модель ====================

    /** Нейтральное здание на гексе: размер + «поворот» (стартовый угол 1..6). */
    public static final class Neutral {
        boolean big;
        int corner;   // 1..6; малый занимает углы [k, k+1], большой [k, k+1, k+2]

        public Neutral(boolean big, int corner) {
            this.big = big;
            this.corner = corner;
        }

        /** Углы гекса (1..6), которые занимает это здание. */
        public List<Integer> corners() {
            List<Integer> out = new ArrayList<>();
            int n = big ? 3 : 2;
            for (int i = 0; i < n; i++) {
                out.add((corner - 1 + i) % 6 + 1);
            }
            return out;
        }
    }

    /** Гекс раскладки (осевые координаты, как у симулятора). */
    public static final class LHex {
        public final int q;
        public final int r;
        /** normal | kelium_tile | spawn_start | player_start | forbidden */
        public String content = "normal";
        public int seat = -1;               // для player_start
        public int stack = 1;               // 1 или 2 — двойной тайл зарождения
        public int keliumDelta = 0;         // -4..+4 — правка келемия на лице тайла
        /**
         * СКОЛЬКО КОНТЕЙНЕРОВ НАРИСОВАНО на гексе (0, 1 или 2).
         *
         * <p>Это РАЗМЕТКА ПЕЧАТИ, а не игровая механика: движок контейнеры из
         * файла поля не читает — в основном режиме их печатает штамповка блоков
         * ({@code BlockStamp}), в откатном раскладывает {@code TokenContainers}.
         * Здесь они нужны дизайнеру, чтобы рисовать поле так, как оно уйдёт в
         * печать (просьба 13.08.2026, п. 12 заказа). Подключение к правилам —
         * отдельная работа, когда решится п. 8 заказа (правила получения).
         */
        public int containers = 0;
        public final List<Neutral> neutrals = new ArrayList<>();

        public LHex(int q, int r) {
            this.q = q;
            this.r = r;
        }

        boolean isSpawn() {
            return "kelium_tile".equals(content) || "spawn_start".equals(content);
        }

        /** Келемий на лице тайла с учётом правки (обычный 4, стартовый 3). */
        int faceKelium() {
            int base = "spawn_start".equals(content) ? 3 : 4;
            return Math.max(0, base + keliumDelta);
        }
    }

    /** Осевые направления — РОВНО как {@link Field#AXIAL_DIRS} (индекс = сторона). */
    private static final int[][] DIRS = Field.AXIAL_DIRS;

    public static final class Model {
        public final Map<Long, LHex> hexes = new LinkedHashMap<>();

        /**
         * СОСТАВ НЕ НАСТРАИВАЕТСЯ ОТДЕЛЬНО. Число игроков — это просто число
         * расставленных стартов (решение дизайнера 12.08.2026): поставил три
         * флажка — раскладка на троих. Отдельная настройка «Игроков:» убрана,
         * чтобы состав и поле не могли разойтись.
         */
        public int players() {
            int n = 0;
            for (LHex h : hexes.values()) {
                if ("player_start".equals(h.content)) {
                    n++;
                }
            }
            return n;
        }

        public static long key(int q, int r) {
            return (((long) q) << 32) ^ (r & 0xffffffffL);
        }

        public LHex get(int q, int r) {
            return hexes.get(key(q, r));
        }
    }

    // ==================== инструменты ====================
    private enum Tool {
        // --- зона РАЗМЕЩЕНИЯ ---
        ADD(true, "⬡", "Гекс: добавить / убрать",
            "Клик по <b>пунктирному призраку</b> — приклеить новый гекс.<br>"
            + "Клик по существующему гексу — убрать его.<br>"
            + "<i>Сетка призраков показывается только в этом режиме.</i>"),
        // --- зона ЗАСЕЛЕНИЯ ---
        CLEAR_HEX(false, "🧽", "Очистить гекс",
            "Сделать гекс обычным: снять тайл, старт, запрет и нейтралов."),
        // Названия тайлов зарождения — МАЛОЕ и БОЛЬШОЕ (решение дизайнера
        // 12.08.2026): «стартовый» путалось со стартом игрока. Малое стоит в
        // палитре ВЫШЕ большого.
        // Иконки зарождений — ЗЕЛЁНЫЕ ФИГУРЫ (круг и квадрат): прежний росток 🌱
        // путался с флажком старта игрока 🚩 (замечание дизайнера 12.08.2026).
        SPAWN_START(false, "🟢", "Малое зарождение",
            "Малое зарождение: лицо 3 келемия, оборот 2.<br>"
            + "Соседство со стартом игрока <b>не обязательно</b> — раскладки без "
            + "малых зарождений у стартов и вовсе без них законны.<br>"
            + "<b>ПКМ</b> — снять зарождение с гекса."),
        KELIUM(false, "🟩", "Большое зарождение",
            "Большое зарождение: лицо 4 келемия, оборот 3.<br>"
            + "Занимает <b>весь гекс</b> — ни строить, ни стоять там нельзя.<br>"
            + "<b>ПКМ</b> — снять зарождение с гекса."),
        STACK(false, "🔂", "Двойной тайл (стопка ×2)",
            "Переключает стопку: <b>один тайл ⇄ два тайла</b>.<br>"
            + "Двойной вырабатывается дважды — гекс освобождается позже.<br>"
            + "Работает и на больших, и на малых зарождениях."),
        KELIUM_DELTA(false, "💎", "Правка келемия ±",
            "<b>ЛКМ</b> — +1 келемий на лице тайла, <b>ПКМ</b> — −1.<br>"
            + "Диапазон правки: от −4 до +4."),
        PLAYER(false, "🚩", "Старт игрока",
            "Ставит старт следующего места (P1, P2 …).<br>"
            + "Клик по существующему старту — снять его (места перенумеруются).<br>"
            + "<b>Состав задаётся именно здесь:</b> сколько стартов поставил — "
            + "на столько игроков раскладка. От двух до четырёх."),
        CONTAINER(false, "📦", "Контейнер",
            "Печатный контейнер на гексе.<br>"
            + "<b>ЛКМ</b> — по кругу: нет → один → два → нет.<br>"
            + "<b>ПКМ</b> — в обратную сторону (тот же ± , что у зарождений).<br>"
            + "<i>Это разметка печати: правила пока берут контейнеры не отсюда.</i>"),
        FORBIDDEN(false, "⛔", "Запретный гекс",
            "Дыра в поле: непроходима и незастраиваема."),
        NEUTRAL_SMALL(false, "🏚", "Нейтрал малый",
            "Малое нейтральное здание: 2 угла = одна стенка.<br>"
            + "<b>ЛКМ по свободному краю</b> гекса — поставить именно туда.<br>"
            + "<b>ЛКМ по своему зданию</b> — повернуть на следующий свободный край.<br>"
            + "<b>ПКМ</b> — убрать здание под курсором.<br>"
            + "<i>На одном гексе помещается до трёх малых (углов всего шесть).</i>"),
        NEUTRAL_BIG(false, "🏭", "Нейтрал большой",
            "Большое нейтральное здание: 3 угла = две стенки.<br>"
            + "<b>ЛКМ по свободному краю</b> гекса — поставить именно туда.<br>"
            + "<b>ЛКМ по своему зданию</b> — повернуть на следующий свободный край.<br>"
            + "<b>ПКМ</b> — убрать здание под курсором.<br>"
            + "<i>На одном гексе помещается до двух больших.</i>");

        final boolean placement;
        /**
         * Символ инструмента. В ИНТЕРФЕЙСЕ НЕ ИСПОЛЬЗУЕТСЯ: эмодзи в Swing
         * выводились пустыми квадратами, иконки кнопок рисует {@link ToolIcons}
         * по имени инструмента. Символ оставлен только как памятка для текстовых
         * логов и документации.
         */
        final String icon;
        final String label;
        final String help;

        Tool(boolean placement, String icon, String label, String help) {
            this.placement = placement;
            this.icon = icon;
            this.label = label;
            this.help = help;
        }
    }

    // ==================== состояние приложения ====================
    /**
     * БОЛЬШЕ ЧЕТЫРЁХ МЕСТ В ИГРЕ НЕ БЫВАЕТ (просьба дизайнера 13.08.2026):
     * комплектов компонентов игрока в коробке четыре. Ограничение стоит и в
     * инструменте «Старт игрока», и в проверках журнала — чтобы поле с пятым
     * стартом не пришло из файла.
     */
    public static final int MAX_SEATS = 4;

    /**
     * ЕДИНСТВЕННАЯ модель на всё приложение. Её объект НИКОГДА не подменяется:
     * на неё держат ссылки полотно и вкладка сборки, и подмена приводила к тому,
     * что сборка из блоков считалась для прежнего поля («Новая раскладка» делала
     * {@code model = new Model()} — баг найден дизайнером 12.08.2026). Новое поле
     * = очистить содержимое этой же модели.
     */
    private static final Model model = new Model();

    /** Та самая единственная модель — для тестов и вкладки сборки. */
    static Model modelRef() {
        return model;
    }
    private static Canvas canvas;
    private static JFrame frame;
    private static JTextPane journal;
    /** Вкладки окна: «Конструктор» и «Сборка из блоков» (как в браузере). */
    private static javax.swing.JTabbedPane tabs;
    /** Панель сборки — живёт во второй вкладке, а не в отдельном окне. */
    private static AssemblyWindow assemblyTab;
    /** Панель инструментов конструктора: во вкладке сборки она не нужна. */
    private static javax.swing.JComponent toolPanel;
    /** Показ найденного состава (число расставленных стартов). */
    private static JLabel playersLabel;
    /** Галочка «Тёмная тема» — её надо держать в согласии с кнопкой в углу. */
    private static JCheckBoxMenuItem darkMenuItem;
    private static Path currentFile = null;
    private static boolean dirty = false;
    /**
     * Настройки приложения в файле {@code %APPDATA%\Kelium\kelium.cfg}. Раздел
     * общий с разбором партии — см. {@link #main(String[])}.
     */
    private static final AppSettings settings = AppSettings.of("replay2");

    // ==================== запуск ====================
    /**
     * ЗАПУСК С ФАЙЛОМ. Путь в аргументе открывается сразу — так работают и
     * командная строка, и двойной щелчок по {@code .kmap} в проводнике, и
     * «Открыть с помощью…»: во всех трёх случаях Windows передаёт путь первым
     * аргументом (просьба дизайнера 13.08.2026).
     */
    public static void main(String[] args) {
        // ОБЛИК — ОБЩИЙ С РАЗБОРОМ ПАРТИИ. Раздел настроек тот же («replay2»),
        // поэтому тема и масштаб у конструктора, проигрывателя и справочника
        // одни: дизайнер настраивает один раз (жалоба 14.08.2026 — «нет тёмной
        // темы, шрифты кривые, масштаб не настроить»). Пересилить тему на один
        // запуск можно ключом -Dkelium.theme=light|dark.
        Theme.loadScale(settings);
        String forced = System.getProperty("kelium.theme", "");
        Theme.apply(forced.isBlank()
            ? settings.getBoolean("dark", true) : !"light".equals(forced));
        Path open = null;
        for (String a : args) {
            if (a == null || a.isBlank() || a.startsWith("-")) {
                continue;
            }
            Path p = Path.of(a);
            if (Files.isReadable(p)) {
                open = p.toAbsolutePath().normalize();
                break;
            }
        }
        final Path startWith = open;
        SwingUtilities.invokeLater(() -> {
            createAndShow();
            if (startWith != null) {
                openLayoutFile(startWith);
            }
        });
    }

    private static void createAndShow() {
        // ЗЕРНО, К ЧЕМУ КЛЕИТЬ — только на пустой модели. Окно пересобирается
        // при смене масштаба, и без этой проверки каждая пересборка подсаживала
        // бы в готовую раскладку лишний гекс (0,0).
        if (model.hexes.isEmpty()) {
            model.hexes.put(Model.key(0, 0), new LHex(0, 0));
        }

        frame = new JFrame();
        Ui.setAppIcon(frame, "constructor");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (confirmDiscardChanges("Выйти из конструктора?")) {
                    frame.dispose();
                    System.exit(0);
                }
            }
        });

        journal = new JTextPane();
        journal.setEditable(false);
        // Журнал — обычный текст темы, а не моноширинный 12 кегль: колонок в нём
        // нет, а читать надо часто (жалоба дизайнера на шрифты 14.08.2026).
        journal.setFont(Theme.wideText(12));
        journal.setBackground(Theme.panel());
        JScrollPane journalScroll = new JScrollPane(journal);
        journalScroll.setPreferredSize(new Dimension(Theme.px(380), Theme.px(200)));
        journalScroll.setBorder(titled("Журнал проверок"));

        canvas = new Canvas();
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.setBorder(null);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasScroll, journalScroll);
        split.setResizeWeight(1.0);
        split.setOneTouchExpandable(true);
        split.setBorder(null);

        // ВКЛАДКИ. Раньше сборка из блоков открывалась отдельным окном; теперь
        // это вторая вкладка того же окна (просьба дизайнера 12.08.2026).
        // ПАЛИТРА В ПРОКРУТКЕ. На невысоком окне (ноутбук 15″, крупный масштаб)
        // нижние кнопки и подсказка просто срезались краем — теперь колонка
        // прокручивается, а не теряет содержимое.
        toolPanel = buildToolPanel();
        JScrollPane toolScroll = new JScrollPane(toolPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        toolScroll.setBorder(null);
        toolScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(16));
        JPanel editorTab = new JPanel(new BorderLayout());
        editorTab.add(toolScroll, BorderLayout.WEST);
        editorTab.add(split, BorderLayout.CENTER);

        assemblyTab = new AssemblyWindow(model);
        BlockCatalogPanel catalogTab = new BlockCatalogPanel(
            kelium.dataio.GameConfig.resolveDataRoot(null));

        tabs = new javax.swing.JTabbedPane();
        tabs.addTab("Конструктор", editorTab);
        tabs.addTab("Сборка из блоков", assemblyTab);
        tabs.addTab("Каталог блоков", catalogTab);
        tabs.setToolTipTextAt(0, "Рисование поля: гексы, тайлы, старты, нейтралы");
        tabs.setToolTipTextAt(1, "Как сложить это поле из физических блоков картона");
        tabs.setToolTipTextAt(2, "Все стороны всех блоков по версиям набора — контейнеры и жёлтые ячейки");
        // при переходе на вкладку сборки поле могло измениться — пересчитываем
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) {
                assemblyTab.refresh();
            }
        });

        frame.setLayout(new BorderLayout());
        frame.add(tabs, BorderLayout.CENTER);
        frame.setJMenuBar(buildMenu(journalScroll, split));

        JLabel status = new JLabel(" ");
        status.setBorder(BorderFactory.createEmptyBorder(
            Theme.px(3), Theme.px(8), Theme.px(3), Theme.px(8)));
        status.setFont(Theme.wideText(12));
        frame.add(status, BorderLayout.SOUTH);
        canvas.status = status;

        refreshTitle();
        refreshJournal();
        canvas.updateStatus();
        frame.setSize(Theme.px(1340), Theme.px(860));
        frame.setLocationRelativeTo(null);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.72));
        frame.setVisible(true);
    }

    /**
     * ПЕРЕСБОРКА ОКНА ПОД НОВЫЙ МАСШТАБ. Размеры запечены в компоненты при
     * создании ({@code Theme.px()} зовётся в конструкторах), менять их на лету
     * нечем — окно собирается заново. Раскладку терять при этом нельзя: модель
     * одна на приложение и не подменяется, а вид полотна (масштаб, сдвиг,
     * выбранный инструмент) переносится вручную.
     *
     * <p>Новое окно появляется РАНЬШЕ, чем исчезает старое: между ними не должно
     * быть мгновения без живых окон, иначе Swing завершит программу.
     */
    private static void rebuildWindow() {
        JFrame old = frame;
        Canvas oldCanvas = canvas;
        int tab = tabs == null ? 0 : tabs.getSelectedIndex();
        createAndShow();
        canvas.adoptFrom(oldCanvas);
        if (tab == 1) {
            tabs.setSelectedIndex(1);
        }
        refreshTitle();
        refreshJournal();
        canvas.updateStatus();
        canvas.repaint();
        if (old != null) {
            old.dispose();
        }
    }

    /**
     * СМЕНИТЬ ТЕМУ, НЕ ТРОГАЯ ОКНО. Пересобирать окно ради темы не нужно и
     * неприятно глазу — оно мигало, будто программа перезапустилась (замечание
     * дизайнера 14.08.2026). Размеры от темы не зависят, значит менять надо
     * только краски:
     *
     * <ul>
     *   <li>{@code FlatLaf.updateUI()} — всё, что рисует оформление, вместе со
     *       строкой заголовка;</li>
     *   <li>{@link Theme#restyleTree} — краски, выставленные нашим кодом и
     *       запечённые в компоненты;</li>
     *   <li>рамки с заголовком и журнал — у них цвет лежит внутри рамки и
     *       внутри документа, деревом до него не дотянуться.</li>
     * </ul>
     *
     * <p>Пересборка остаётся только у МАСШТАБА: там размеры действительно
     * запечены в каждый компонент при создании.
     */
    private static void applyTheme(boolean dark) {
        settings.putBoolean("dark", dark);
        Theme.apply(dark);
        com.formdev.flatlaf.FlatLaf.updateUI();
        Theme.restyleTree(frame.getContentPane());
        Theme.restyleTree(frame.getJMenuBar());
        // Подложка поля — явно: в светлой палитре она белая, как и панель, и по
        // одной краске их не различить (см. Theme.counterpart).
        canvas.setBackground(Theme.paper());
        restyleTitles(frame.getContentPane());
        refreshJournal();          // цвета строк журнала лежат в его документе
        if (darkMenuItem != null) {
            darkMenuItem.setSelected(dark);
        }
        canvas.repaint();
        frame.repaint();
    }

    /**
     * Рамки с заголовком («Журнал проверок», «Состав», группы инструментов):
     * их цвета заданы при сборке и деревом не правятся — рамка не компонент.
     */
    private static void restyleTitles(java.awt.Component c) {
        if (c instanceof JComponent jc && jc.getBorder() instanceof TitledBorder tb) {
            jc.setBorder(titled(tb.getTitle()));
        }
        if (c instanceof java.awt.Container box) {
            for (java.awt.Component ch : box.getComponents()) {
                restyleTitles(ch);
            }
        }
    }

    /** Сменить масштаб интерфейса (0 — «подобрать под экран») и пересобрать окно. */
    private static void applyScale(double value) {
        if (Math.abs(Theme.userScale() - value) < 0.005) {
            return;
        }
        settings.putDouble(Theme.SCALE_KEY, value);
        Theme.setUserScale(value);
        Theme.apply(Theme.isDark());     // шрифты и ширина полос лежат в UIManager
        rebuildWindow();
        canvas.hint("Масштаб интерфейса — "
            + Math.round(Theme.effectiveScale() * 100) + " %"
            + (Theme.userScale() == 0 ? " (подобран под экран)" : ""));
    }

    private static void askScale() {
        Double v = ScaleDialog.show(frame, Theme.effectiveScale());
        if (v != null) {
            applyScale(v);
        }
    }

    /** Показать вкладку сборки (и пересчитать её под текущее поле). */
    private static void showAssemblyTab() {
        tabs.setSelectedIndex(1);
        assemblyTab.refresh();
    }

    /**
     * Выгрузить РАСКЛАДКУ в PNG: поле рисуется тем же холстом, что и на экране
     * (значит картинка не может разойтись с программой), а под полем печатается
     * ЛЕГЕНДА всех обозначений — просьба дизайнера 12.08.2026.
     */
    /**
     * ЕДИНАЯ ТОЧКА ВХОДА ЭКСПОРТА — package-private: кнопка на вкладке «Сборка
     * из блоков» зовёт ровно ЭТОТ же метод (просьба дизайнера 14.08.2026 не
     * разводить два независимых экспорта на двух вкладках).
     */
    static void exportLayoutPng() {
        if (model.hexes.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Поле пустое — нечего выгружать.", 360),
                "Экспорт PNG", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!promptSaveBeforeExport()) {
            return;
        }
        try {
            exportLayoutPngUnsafe();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Не удалось выгрузить картинку.\n\n" + ex, 480),
                "Экспорт PNG", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * ПРЕДЛОЖИТЬ СОХРАНИТЬ ПЕРЕД ЭКСПОРТОМ (просьба дизайнера 14.08.2026): PNG
     * называется по имени файла проекта, а у никогда не сохранённого проекта
     * его нет — картинка получалась безликой «раскладка.png». Если проект уже
     * сохранён и правок с тех пор не было, спрашивать нечего — имя точно есть,
     * трогать его лишний раз не нужно.
     *
     * @return false — пользователь передумал (отмена или закрыл диалог крестиком),
     *     экспорт нужно прервать целиком, не только пропустить сохранение
     */
    static boolean promptSaveBeforeExport() {
        if (currentFile != null && !dirty) {
            return true;
        }
        int ans = JOptionPane.showConfirmDialog(frame,
            Ui.text(currentFile == null
                ? "Проект ещё не сохранён — картинка выйдет без имени. "
                    + "Сохранить проект перед экспортом?"
                : "В проекте есть несохранённые правки. Сохранить перед экспортом?", 380),
            "Сохранить перед экспортом?",
            JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans == JOptionPane.YES_OPTION) {
            return saveLayout(false);
        }
        return ans == JOptionPane.NO_OPTION;
    }

    /** Имя проекта для заголовков и файлов — по сохранённому файлу либо «раскладка». */
    private static String projectName() {
        return currentFile != null
            ? currentFile.getFileName().toString().replace(FieldFile.DOT_EXT, "")
            : "раскладка";
    }

    private static String layoutSubtitle() {
        int n = model.players();
        int errors = 0;
        int warns = 0;
        for (Issue i : validate(model)) {
            if (i.level() == 2) {
                errors++;
            } else if (i.level() == 1) {
                warns++;
            }
        }
        return "Гексов: " + model.hexes.size() + "   ·   игроков: " + n
            + "   ·   проверки: " + (errors == 0 ? "без ошибок" : errors + " ошибок")
            + (warns > 0 ? ", " + warns + " предупреждений" : "");
    }

    /**
     * ОБЩИЙ МАСШТАБ И СДВИГ для слоя блоков и слоя содержимого в СЛИЯНИИ — ТА ЖЕ
     * формула, что у {@link Canvas#render}. Каждый холст сам по себе умеет
     * подгонять размер под свою рамку, но подгоняет НЕЗАВИСИМО (у сборки свой
     * потолок масштаба, 90 против 110 у конструктора) — на СОВПАДАЮЩИХ картинках
     * это дало бы сдвиг в доли гекса. Здесь одна формула на двоих.
     *
     * <p>УЧИТЫВАЕТ ЧЁРНЫЕ НАКЛАДКИ СБОРКИ (баг дизайнера 14.08.2026: «картинка
     * не вписывается»). Физический блок — фигура из 5–6 гексов, и лишние её
     * ячейки, торчащие за пределы нарисованного поля, закрываются чёрной
     * накладкой «недоступно» — такая ячейка лежит ЗА пределами {@code m.hexes},
     * и без неё в рамке накладка могла оказаться за краем картинки.
     *
     * @return {size, panX, panY}
     */
    private static double[] sharedFit(Model m, int w, int h) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (LHex hx : m.hexes.values()) {
            double[] c = kelium.report.FieldGeometry.hexCenter(hx.q, hx.r, 1);
            minX = Math.min(minX, c[0] - 1);
            maxX = Math.max(maxX, c[0] + 1);
            minY = Math.min(minY, c[1] - 1);
            maxY = Math.max(maxY, c[1] + 1);
        }
        if (assemblyTab != null) {
            for (BlockAssembler.Cell c : assemblyTab.currentBlackCells()) {
                double[] p = kelium.report.FieldGeometry.hexCenter(c.q(), c.r(), 1);
                minX = Math.min(minX, p[0] - 1);
                maxX = Math.max(maxX, p[0] + 1);
                minY = Math.min(minY, p[1] - 1);
                maxY = Math.max(maxY, p[1] + 1);
            }
        }
        if (minX > maxX) {
            minX = -1;
            minY = -1;
            maxX = 1;
            maxY = 1;
        }
        double size = Math.max(18, Math.min(110,
            Math.min((w - 60) / (maxX - minX), (h - 60) / (maxY - minY))));
        double panX = w / 2.0 - size * (minX + maxX) / 2;
        double panY = h / 2.0 - size * (minY + maxY) / 2;
        return new double[]{size, panX, panY};
    }

    /**
     * СЛИЯНИЕ — СОБРАТЬ КАДР ПОЛЯ ПО СЛОЯМ.
     *
     * <p>Порядок слоёв назначен дизайнером 17.08.2026 и повторяет порядок, в
     * котором это кладут на стол:
     *
     * <ol>
     *   <li><b>блоки поля</b> — белая заливка, тёмная обводка; ЦВЕТА БЛОКОВ НЕТ
     *       (на слиянии он только мешает читать содержимое) и обычных гексов
     *       тоже нет: их роль играют сами блоки;</li>
     *   <li><b>гексовая сетка</b> — если включена; проходит по всей площади
     *       кадра и гаснет по мере удаления от поля;</li>
     *   <li><b>тайлы зарождения</b>;</li>
     *   <li><b>игроки</b>;</li>
     *   <li><b>стартовые здания</b> (нейтральные постройки раскладки);</li>
     *   <li><b>тайлы запретных гексов</b> — и помеченные в раскладке, и чёрные
     *       накладки сборки на ячейки блока, торчащие за край поля;</li>
     *   <li><b>контейнеры</b> — если включены.</li>
     * </ol>
     *
     * <p>Слоёв несколько, а геометрия ОДНА ({@link #sharedFit}): холсты разные,
     * и без общего масштаба со сдвигом они разъехались бы на доли гекса.
     */
    private static java.awt.image.BufferedImage fuseLayers(int fw, int fh,
                                                           PngExport.Options options) {
        double[] fit = sharedFit(model, fw, fh);
        java.awt.image.BufferedImage out =
            assemblyTab.renderBlocksLayer(fw, fh, fit[0], fit[1], fit[2], true);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        if (options.hexGrid()) {
            g.drawImage(canvas.renderHexGridLayer(fw, fh, fit[0], fit[1], fit[2]), 0, 0, null);
        }
        // Зарождения, игроки и стартовые здания — одним проходом: внутри метода
        // они уже разложены по слоям в нужном порядке. Контейнеры выключены
        // здесь: по порядку они идут ПОСЛЕ запретных гексов.
        g.drawImage(canvas.renderContentOnly(fw, fh, fit[0], fit[1], fit[2], false),
            0, 0, null);
        g.drawImage(canvas.renderForbiddenLayer(fw, fh, fit[0], fit[1], fit[2]), 0, 0, null);
        g.drawImage(assemblyTab.renderBlackOverlayLayer(fw, fh, fit[0], fit[1], fit[2]),
            0, 0, null);
        if (options.containers()) {
            g.drawImage(canvas.renderContainersLayer(fw, fh, fit[0], fit[1], fit[2]),
                0, 0, null);
        }
        g.dispose();
        return out;
    }

    private static PngExport.Content layoutContent() {
        return new PngExport.Content(layoutLegend(), playerBlocks(model), mapStats(model))
            .filtered(ExportOptionsDialog.current(settings));
    }

    /**
     * ЕДИНЫЙ ЭКСПОРТ ПОЛЯ И СБОРКИ (просьба дизайнера 14.08.2026): раньше поле
     * и сборка из блоков выгружались двумя независимыми действиями с разных
     * вкладок — незачем, за столом дизайнеру нужны обе картинки сразу. Форма
     * складывания — раздельные файлы, друг над другом, бок о бок или слияние —
     * выбирается один раз в окне настроек экспорта, а не при каждом сохранении.
     */
    private static void exportLayoutPngUnsafe() {
        if (assemblyTab == null || !assemblyTab.hasResult()) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Сборка из блоков ещё не подобрана. Открой вкладку "
                    + "«Сборка из блоков», дождись расчёта и попробуй снова.", 380),
                "Экспорт PNG", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = projectName();
        PngExport.Options options = ExportOptionsDialog.current(settings);
        java.awt.image.BufferedImage layoutImg =
            PngExport.compose("Раскладка «" + name + "»", layoutSubtitle(),
                canvas.render(1600, 1100), layoutContent());

        switch (options.layout()) {
            case FUSION -> {
                java.awt.image.BufferedImage fused = PngExport.compose(
                    "Раскладка «" + name + "» — слияние", layoutSubtitle(),
                    fuseLayers(1600, 1100, options), layoutContent());
                PngExport.done(frame, PngExport.save(frame, name + "-слияние.png", fused));
            }
            case VERTICAL, HORIZONTAL -> {
                java.awt.image.BufferedImage assemblyImg = assemblyTab.exportImage(options);
                java.awt.image.BufferedImage combined = PngExport.stack(
                    layoutImg, assemblyImg, options.layout() == PngExport.Layout.VERTICAL);
                PngExport.done(frame, PngExport.save(frame, name + ".png", combined));
            }
            default -> {
                java.awt.image.BufferedImage assemblyImg = assemblyTab.exportImage(options);
                PngExport.doneTwo(frame, PngExport.saveTwo(frame, name + ".png",
                    layoutImg, "-поле", assemblyImg, "-сборка"));
            }
        }
    }

    /** Старты по местам — тот же расклад, что использует живой журнал проверок. */
    private static Map<Integer, LHex> startsOf(Model m) {
        Map<Integer, LHex> starts = new LinkedHashMap<>();
        for (LHex h : m.hexes.values()) {
            if ("player_start".equals(h.content)) {
                starts.put(h.seat, h);
            }
        }
        return starts;
    }

    /**
     * ЛИЧНЫЕ БЛОКИ ИГРОКОВ для экспорта: цвет места и пара показателей на
     * каждого — сколько келемия лежит БЛИЖЕ к его старту, чем к чужому (по
     * прямому расстоянию, тот же счёт, что у «нет соседнего малого зарождения»
     * в журнале), и сколько у старта обычных соседей для разворота.
     */
    static List<PngExport.PlayerBlock> playerBlocks(Model m) {
        Map<Integer, LHex> starts = startsOf(m);
        if (starts.isEmpty()) {
            return List.of();
        }
        Map<Integer, Integer> kelium = new java.util.HashMap<>();
        for (LHex h : m.hexes.values()) {
            if (!h.isSpawn()) {
                continue;
            }
            int bestSeat = -1;
            int bestDist = Integer.MAX_VALUE;
            for (var e : starts.entrySet()) {
                int d = axialDist(h.q, h.r, e.getValue().q, e.getValue().r);
                if (d < bestDist || (d == bestDist && e.getKey() < bestSeat)) {
                    bestDist = d;
                    bestSeat = e.getKey();
                }
            }
            if (bestSeat >= 0) {
                kelium.merge(bestSeat, h.faceKelium() * h.stack, Integer::sum);
            }
        }
        List<PngExport.PlayerBlock> out = new ArrayList<>();
        List<Integer> seats = new ArrayList<>(starts.keySet());
        java.util.Collections.sort(seats);
        for (int seat : seats) {
            LHex st = starts.get(seat);
            int neighbors = 0;
            for (int[] d : DIRS) {
                LHex nb = m.get(st.q + d[0], st.r + d[1]);
                if (nb != null && !"forbidden".equals(nb.content) && !nb.isSpawn()) {
                    neighbors++;
                }
            }
            out.add(new PngExport.PlayerBlock(Canvas.SEAT[seat % 4], "Игрок " + (seat + 1),
                List.of("келемий поблизости: " + kelium.getOrDefault(seat, 0),
                    "обычных соседей у старта: " + neighbors)));
        }
        return out;
    }

    /** Общая статистика поля для экспорта — то, что дизайнер сверяет при балансировке. */
    static List<String> mapStats(Model m) {
        Map<Integer, LHex> starts = startsOf(m);
        int bigSpawns = 0;
        int smallSpawns = 0;
        int containers = 0;
        for (LHex h : m.hexes.values()) {
            if ("kelium_tile".equals(h.content)) {
                bigSpawns += h.stack;
            } else if ("spawn_start".equals(h.content)) {
                smallSpawns += h.stack;
            }
            containers += h.containers;
        }
        List<String> out = new ArrayList<>();
        out.add("гексов: " + m.hexes.size());
        out.add("игроков: " + m.players());
        out.add("больших зарождений: " + bigSpawns);
        out.add("малых зарождений: " + smallSpawns);
        out.add("контейнеров нарисовано: " + containers);
        List<Integer> seats = new ArrayList<>(starts.keySet());
        java.util.Collections.sort(seats);
        if (seats.size() >= 2) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int i = 0; i < seats.size(); i++) {
                for (int j = i + 1; j < seats.size(); j++) {
                    LHex a = starts.get(seats.get(i));
                    LHex b = starts.get(seats.get(j));
                    int d = axialDist(a.q, a.r, b.q, b.r);
                    min = Math.min(min, d);
                    max = Math.max(max, d);
                }
            }
            out.add("расстояние между стартами: " + min + "–" + max + " гекса(ов)");
        }
        return out;
    }

    /**
     * Строка состояния конструктора. Вынесена отдельно и БЕЗ Swing нарочно:
     * 12.08.2026 конструктор перестал открываться из-за того, что при чистке
     * эмодзи из подписи убрали один аргумент, а {@code %s} в шаблоне остался —
     * {@code String.format} падал MissingFormatArgumentException при первом же
     * обновлении статуса, то есть ещё до показа окна. Теперь это чистая функция,
     * и на неё есть тест по всем инструментам.
     */
    static String statusText(String toolLabel, int hexes, int starts, int players, int spawns) {
        // ЗНАМЕНАТЕЛЬ — ПРЕДЕЛ МЕСТ, А НЕ СОСТАВ. Раньше здесь стояло
        // starts/players, но с 12.08.2026 состав И ЕСТЬ число стартов
        // (Model.players() считает те же гексы), поэтому дробь всегда читалась
        // «N/N» и не сообщала ничего. Осмысленный знаменатель тут один — сколько
        // мест вообще бывает: комплектов компонентов в коробке MAX_SEATS.
        return String.format(
            "Инструмент: %s   ·   гексов: %d   ·   стартов: %d из %d   ·   зарождений: %d",
            toolLabel, hexes, starts, MAX_SEATS, spawns);
    }

    /**
     * МОЖНО ЛИ ПОСТАВИТЬ ЕЩЁ ОДИН СТАРТ. Мест в игре не больше
     * {@link #MAX_SEATS}: пятому игроку нечем играть — комплектов компонентов
     * в коробке четыре.
     */
    public static boolean canAddStart(Model m) {
        return m.players() < MAX_SEATS;
    }

    /**
     * СНЯТЬ ЗАРОЖДЕНИЕ с гекса — то, что делает ПКМ в режиме зарождений
     * (просьба дизайнера 12.08.2026: не переключаться ради этого на «Очистить
     * гекс»). Вместе с тайлом сбрасываются стопка и правка келемия, потому что
     * без тайла они бессмысленны. false — снимать было нечего.
     */
    public static boolean clearSpawn(LHex h) {
        if (h == null || !h.isSpawn()) {
            return false;
        }
        h.content = "normal";
        h.stack = 1;
        h.keliumDelta = 0;
        return true;
    }

    /** Легенда обозначений раскладки — печатается ПОД полем. */
    /**
     * ОБЩАЯ ЛЕГЕНДА РАСКЛАДКИ — на языке игры, а не конструктора (правка
     * дизайнера 17.08.2026).
     *
     * <p>Легенда объясняет, ЧТО ЭТО ЗНАЧИТ ЗА СТОЛОМ: не «зелёный шестиугольник»,
     * а «тайл зарождения, к которому добытчик примыкает стенкой». Каждый образец
     * назван термином свода — иначе легенда объясняет картинку сама себе.
     *
     * <p>МЕСТО КАЖДОГО ИГРОКА — отдельной строкой со своим цветом. Общего
     * образца «старт игрока» больше нет: игроков за столом четверо, и на поле
     * важно не то, что старт бывает, а чей он.
     */
    private static java.util.List<PngExport.Item> layoutLegend() {
        java.util.List<PngExport.Item> out = new ArrayList<>();
        out.add(PngExport.Item.hex(new Color(0xEFEDE4),
            "обычный гекс — здесь строят здания и ходят войска"));
        out.add(PngExport.Item.hex(Canvas.SPAWN_START,
            "малое зарождение (S): 3 келемия на лице, 2 на обороте. Жетонов на "
                + "тайле не бывает — добытчик к нему ПРИМЫКАЕТ стенкой"));
        out.add(PngExport.Item.hex(Canvas.SPAWN_NORMAL,
            "большое зарождение (K): 4 келемия на лице, 3 на обороте. Только его "
                + "оборот даёт победное очко тому, кто его исчерпал"));
        out.add(PngExport.Item.hex(Canvas.SPAWN_NORMAL,
            "«×2» на тайле зарождения — два тайла стопкой: исчерпав верхний, "
                + "открываешь нижний. «+1» / «−1» — правка келемия на лице"));
        out.add(PngExport.Item.hex(new Color(0x3A3A3A),
            "запретный гекс (✕) — непроходим ни для кого и никогда, строить нельзя"));
        out.add(PngExport.Item.square(Canvas.NEUTRAL_FILL,
            "нейтральное здание: закрывает стенку гекса с обеих сторон — через неё "
                + "не пройти наземкой и не расширить зону стройки. Малое занимает "
                + "одну стенку, большое две"));
        out.add(PngExport.Item.square(Canvas.CONTAINER_FILL,
            "печатный контейнер: накрой эту ячейку любым своим жетоном — возьми "
                + "карту контейнера. Ячейка срабатывает один раз за партию. Две "
                + "карточки на гексе — два контейнера"));
        // МЕСТО КАЖДОГО ИГРОКА — своей строкой и своим цветом (правка дизайнера
        // 17.08.2026). Строк ровно столько, сколько мест расставлено в раскладке.
        java.util.List<Integer> seats = new ArrayList<>(startsOf(model).keySet());
        java.util.Collections.sort(seats);
        for (int seat : seats) {
            out.add(PngExport.Item.seat(Canvas.SEAT[seat % 4], "P" + (seat + 1),
                "место игрока " + (seat + 1) + " — здесь стоит его ЦУ в начале партии"));
        }
        return out;
    }

    // ==================== палитра инструментов ====================
    private static ButtonGroup toolButtons = new ButtonGroup();
    private static final Map<Tool, JToggleButton> TOOL_BUTTONS = new LinkedHashMap<>();

    private static JComponent buildToolPanel() {
        // Пересборка окна (смена масштаба или темы) строит палитру заново —
        // старые кнопки надо забыть, иначе группа переключателей копила бы
        // мёртвые кнопки от прошлых окон.
        toolButtons = new ButtonGroup();
        TOOL_BUTTONS.clear();
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(
            Theme.px(8), Theme.px(8), Theme.px(8), Theme.px(8)));

        col.add(toolGroup("1. Размещение гексов",
            "Собери форму поля:|приклеивай гексы друг к другу.", Tool.ADD));
        col.add(Box.createVerticalStrut(Theme.px(10)));
        col.add(toolGroup("2. Заселение гексов",
            "Что лежит на гексах:|тайлы, старты, нейтралы.",
            // МАЛОЕ зарождение стоит ВЫШЕ большого (просьба дизайнера 12.08.2026)
            Tool.CLEAR_HEX, Tool.PLAYER, Tool.SPAWN_START, Tool.KELIUM, Tool.STACK,
            Tool.KELIUM_DELTA, Tool.CONTAINER, Tool.FORBIDDEN,
            Tool.NEUTRAL_SMALL, Tool.NEUTRAL_BIG));
        col.add(Box.createVerticalStrut(10));

        JPanel comp = new JPanel();
        comp.setLayout(new BoxLayout(comp, BoxLayout.Y_AXIS));
        comp.setBorder(titled("Состав"));
        // Состав НЕ выбирается — он определяется числом расставленных стартов.
        playersLabel = new JLabel();
        playersLabel.setFont(Theme.wideText(15));
        playersLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel("Игроков (по стартам):");
        lbl.setFont(Theme.wideText(12));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setToolTipText("<html><div style='width:280px'>Состав определяется тем, "
            + "сколько стартов игроков ты поставил инструментом «Старт игрока». "
            + "Отдельной настройки нет: поставил три флажка — раскладка на троих. "
            + "Минимум — два.</div></html>");
        comp.add(lbl);
        comp.add(Box.createVerticalStrut(3));
        comp.add(playersLabel);
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setMaximumSize(new Dimension(Theme.px(240), Theme.px(90)));
        col.add(comp);
        col.add(Box.createVerticalStrut(10));

        // Заметная кнопка — дубликат пункта «Вид → Сборка из блоков…» (F2)
        JButton assembly = new JButton("Собрать из блоков", ToolIcons.of("BLOCKS"));
        assembly.setIconTextGap(8);
        assembly.setToolTipText("<html><div style='width:300px'><b>Сборка поля из блоков</b><br>"
            + "Показывает, как сложить нарисованное поле из физических кусков картона:<br>"
            + "больших блоков (6 гексов), малых (5 гексов) и чёрных накладок.<br>"
            + "Программа ищет вариант с <b>наименьшим числом накладок</b>.<br>"
            + "Откроется во <b>второй вкладке</b> этого же окна. Клавиша F2.</div></html>");
        assembly.setAlignmentX(Component.LEFT_ALIGNMENT);
        assembly.setMaximumSize(new Dimension(Theme.px(240), Theme.px(40)));
        assembly.setPreferredSize(new Dimension(Theme.px(226), Theme.px(38)));
        // ШИРОКОЕ ОБЫЧНОЕ, А НЕ ЖИРНОЕ. Жирное начертание Tektur на кнопке
        // читается тяжело и слипается (жалоба дизайнера 14.08.2026); широкое
        // обычное — то самое, что в теме заведено под надписи на кнопках.
        assembly.setFont(Theme.wideText(13));
        assembly.setFocusPainted(false);
        assembly.addActionListener(e -> showAssemblyTab());
        col.add(assembly);
        col.add(Box.createVerticalStrut(6));

        // Экспорт поля картинкой — прямо в панели инструментов конструктора:
        // это ДРУГОЙ экспорт, не тот, что на вкладке сборки (просьба дизайнера).
        JButton exportPng = new JButton("Экспорт поля в PNG", ToolIcons.of("PNG"));
        exportPng.setIconTextGap(8);
        exportPng.setToolTipText("<html><div style='width:300px'><b>Картинка раскладки</b><br>"
            + "Поле в полном цвете, как в конструкторе, со всеми жетонами,<br>"
            + "а под ним — <b>легенда всех обозначений</b>.<br>"
            + "Сборку из блоков выгружает своя кнопка на второй вкладке. Ctrl+P.</div></html>");
        exportPng.setMaximumSize(new Dimension(Theme.px(188), Theme.px(40)));
        exportPng.setPreferredSize(new Dimension(Theme.px(184), Theme.px(38)));
        exportPng.setFont(Theme.wideText(13));
        exportPng.setFocusPainted(false);
        exportPng.addActionListener(e -> exportLayoutPng());

        // ШЕСТЕРЁНКА РЯДОМ — окно «Что класть в экспорт» (просьба дизайнера
        // 14.08.2026): какие разделы легенды и статистики печатать под полем.
        // Само поле не выключается — это не чекбокс, а сама причина экспорта.
        JButton exportSettings = new JButton(ToolIcons.of("SETTINGS"));
        exportSettings.setToolTipText("<html><div style='width:280px'>"
            + "<b>Настройки экспорта</b><br>Какие разделы легенды и статистики "
            + "печатать под полем. Само изображение поля выключить нельзя.</div></html>");
        exportSettings.setFocusPainted(false);
        exportSettings.setMargin(new java.awt.Insets(4, 8, 4, 8));
        exportSettings.setPreferredSize(new Dimension(Theme.px(38), Theme.px(38)));
        exportSettings.setMaximumSize(new Dimension(Theme.px(38), Theme.px(38)));
        exportSettings.addActionListener(e -> ExportOptionsDialog.show(frame, settings));

        JPanel exportRow = new JPanel();
        exportRow.setLayout(new BoxLayout(exportRow, BoxLayout.X_AXIS));
        exportRow.setOpaque(false);
        exportRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportRow.setMaximumSize(new Dimension(Theme.px(240), Theme.px(40)));
        exportPng.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportRow.add(exportPng);
        exportRow.add(Box.createHorizontalStrut(Theme.px(6)));
        exportRow.add(exportSettings);
        col.add(exportRow);

        col.add(Box.createVerticalGlue());
        col.add(hintLines("колесо — масштаб", "правая кнопка — перетащить поле"));
        return col;
    }

    private static TitledBorder titled(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Theme.border()), title);
        b.setTitleFont(Theme.wideText(12));
        b.setTitleColor(Theme.ink2());
        return b;
    }

    /**
     * ПОЯСНЕНИЕ ПОД ЗАГОЛОВКОМ — по строчке на подпись, БЕЗ HTML.
     *
     * <p>Почему не одна html-подпись с переносом: разметка HTML в Swing теряет
     * РАЗРЯДКУ шрифта (межбуквенное расстояние задано атрибутом шрифта, а
     * html-раскладка его не переносит), и буквы слипаются — жалоба дизайнера
     * 14.08.2026. Обычная подпись разрядку держит, поэтому строки разбиты
     * вручную по символу «|».
     */
    private static JComponent hintLines(String... lines) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setBorder(BorderFactory.createEmptyBorder(
            0, Theme.px(2), Theme.px(5), Theme.px(2)));
        for (String line : lines) {
            JLabel l = new JLabel(line.trim());
            l.setFont(Theme.note(11));
            l.setForeground(Theme.ink3());
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(l);
        }
        return box;
    }

    private static JComponent toolGroup(String title, String hint, Tool... tools) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(titled(title));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(hintLines(hint.split("\\|")));
        for (Tool t : tools) {
            // ИКОНКА РИСУЕТСЯ, а не берётся из шрифта: эмодзи в Swing выводились
            // пустыми квадратами (скриншот дизайнера 12.08.2026). Все иконки одной
            // коробки 20×20, поэтому подписи стоят ровным столбцом.
            JToggleButton b = new JToggleButton(t.label, ToolIcons.of(t.name()));
            b.setIconTextGap(8);
            b.setToolTipText("<html><div style='width:260px'><b>" + t.label + "</b><br>"
                + t.help + "</div></html>");
            b.setHorizontalAlignment(JToggleButton.LEFT);
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setFont(Theme.wideText(12));
            b.setMaximumSize(new Dimension(Theme.px(240), Theme.px(32)));
            b.setPreferredSize(new Dimension(Theme.px(226), Theme.px(30)));
            b.setFocusPainted(false);
            b.addActionListener(e -> selectTool(t));
            toolButtons.add(b);
            TOOL_BUTTONS.put(t, b);
            p.add(b);
            p.add(Box.createVerticalStrut(Theme.px(3)));
        }
        if (tools.length > 0 && tools[0] == Tool.ADD) {
            TOOL_BUTTONS.get(Tool.ADD).setSelected(true);
        }
        p.setMaximumSize(new Dimension(Theme.px(240), Theme.px(40 + tools.length * 34)));
        return p;
    }

    private static void selectTool(Tool t) {
        canvas.tool = t;
        // сетка призраков видна ТОЛЬКО в режиме размещения — переключаем сразу,
        // не дожидаясь клика по полю
        canvas.repaint();
        canvas.updateStatus();
    }

    // ==================== меню ====================
    private static JMenuBar buildMenu(JScrollPane journalScroll, JSplitPane split) {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Файл");
        file.setMnemonic(KeyEvent.VK_F);
        file.add(item("Новая раскладка", "control N",
            "Пустое поле (текущее будет закрыто)", e -> newLayout()));
        file.add(item("Открыть…", "control O",
            "Загрузить раскладку из YAML", e -> openLayout()));
        file.addSeparator();
        file.add(item("Сохранить", "control S",
            "Сохранить в текущий файл", e -> saveLayout(false)));
        file.add(item("Сохранить как…", "control shift S",
            "Сохранить в новый файл", e -> saveLayout(true)));
        file.addSeparator();
        file.add(item("Очистить поле", "control DELETE",
            "Стереть всё содержимое и форму поля", e -> eraseField()));
        file.addSeparator();
        file.add(item("Выход", null, null, e -> {
            if (confirmDiscardChanges("Выйти из конструктора?")) {
                System.exit(0);
            }
        }));
        bar.add(file);

        JMenu edit = new JMenu("Правка");
        edit.setMnemonic(KeyEvent.VK_P);
        edit.add(item("Отменить", "control Z", "Откатить последнее действие",
            e -> canvas.undo()));
        edit.add(item("Снять все нейтралы", null, null, e -> {
            canvas.pushUndo();
            model.hexes.values().forEach(h -> h.neutrals.clear());
            afterChange();
        }));
        bar.add(edit);

        JMenu view = new JMenu("Вид");
        JCheckBoxMenuItem showJournal = new JCheckBoxMenuItem("Журнал проверок", true);
        showJournal.addActionListener(e -> {
            journalScroll.setVisible(showJournal.isSelected());
            split.setDividerLocation(showJournal.isSelected() ? 0.72 : 1.0);
            split.revalidate();
        });
        view.add(showJournal);
        view.add(item("Вписать в окно", "control 0", null, e -> canvas.fitToView()));
        view.add(item("Поле крупнее", "control PLUS", null, e -> canvas.zoom(1.15)));
        view.add(item("Поле мельче", "control MINUS", null, e -> canvas.zoom(1 / 1.15)));
        view.addSeparator();
        darkMenuItem = new JCheckBoxMenuItem("Тёмная тема", Theme.isDark());
        darkMenuItem.setToolTipText("Тема общая с разбором партии и справочником");
        darkMenuItem.addActionListener(e -> applyTheme(darkMenuItem.isSelected()));
        view.add(darkMenuItem);
        view.add(scaleMenu());
        view.addSeparator();
        view.add(item("Сборка из блоков (вкладка)", "F2",
            "Как сложить это поле из физических блоков гексов",
            e -> showAssemblyTab()));
        view.add(item("Конструктор (вкладка)", "F3",
            "Вернуться к рисованию поля",
            e -> tabs.setSelectedIndex(0)));
        view.addSeparator();
        view.add(item("Экспорт раскладки в PNG…", "control P",
            "Картинка поля со всеми обозначениями и легендой под ним",
            e -> exportLayoutPng()));
        bar.add(view);

        JMenu help = new JMenu("Справка");
        help.add(item("Как пользоваться", "F1", null, e -> showGuide()));
        help.add(item("О программе", null, null, e -> JOptionPane.showMessageDialog(frame,
            Ui.text("Конструктор раскладок «Кристаллы Раздора».\n"
                + "Сохраняет YAML, который читает симулятор.\n\n"
                + "Настройки хранятся в файле: " + AppSettings.location(), 520),
            "О программе", JOptionPane.INFORMATION_MESSAGE)));
        bar.add(help);

        // КНОПКА ТЕМЫ В УГЛУ — как в разборе партии (просьба дизайнера
        // 14.08.2026): значок-полукруг день/ночь, единственный цветной в строке.
        // Стоит в строке меню за распоркой, поэтому оказывается ровно в углу.
        bar.add(Box.createHorizontalGlue());
        bar.add(kelium.gui.replay2.Ui2.iconButton(
            TransportIcons.of("THEME", Theme.px(18)),
            "Переключить тёмную и светлую темы. Тема общая с разбором партии.",
            26, () -> applyTheme(!Theme.isDark())));
        return bar;
    }

    /**
     * МАСШТАБ ИНТЕРФЕЙСА — шаги словами, как в разборе партии. «Авто» подбирает
     * размер под рабочий стол: на 15″ при 125 % системного масштаба вёрстка
     * ужимается, чтобы палитра и журнал влезли целиком.
     *
     * <p>Сочетания взяты с Shift: {@code control PLUS/MINUS} уже заняты
     * масштабом ПОЛЯ, и одна клавиша не должна делать два разных дела.
     */
    private static final double[] SCALE_STEPS = {0, 0.75, 0.85, 1.00, 1.15, 1.30};
    private static final String[] SCALE_WORDS = {
        "Авто — под экран", "Очень мелкий", "Мелкий", "Обычный", "Крупный", "Очень крупный",
    };

    private static JMenu scaleMenu() {
        JMenu m = new JMenu("Масштаб интерфейса");
        ButtonGroup g = new ButtonGroup();
        double now = Theme.userScale();
        for (int i = 0; i < SCALE_STEPS.length; i++) {
            double v = SCALE_STEPS[i];
            String pct = v == 0
                ? " (" + Math.round(Theme.autoScaleValue() * 100) + " %)"
                : "  ·  " + Math.round(v * 100) + " %";
            javax.swing.JRadioButtonMenuItem mi =
                new javax.swing.JRadioButtonMenuItem(SCALE_WORDS[i] + pct);
            mi.setSelected(Math.abs(now - v) < 0.005);
            mi.addActionListener(e -> applyScale(v));
            g.add(mi);
            m.add(mi);
        }
        m.addSeparator();
        m.add(item("Свой…", null, "Ползунок с точным числом", e -> askScale()));
        m.addSeparator();
        m.add(item("Крупнее интерфейс", "control shift EQUALS", null,
            e -> applyScale(nudge(+1))));
        m.add(item("Мельче интерфейс", "control shift MINUS", null,
            e -> applyScale(nudge(-1))));
        return m;
    }

    /** Шаг «крупнее/мельче» — 5 % от того, что действует сейчас. */
    private static double nudge(int dir) {
        return Math.max(0.6, Math.min(2.0,
            Math.round((Theme.effectiveScale() + dir * 0.05) * 100) / 100.0));
    }

    private static JMenuItem item(String text, String accel, String tip,
                                  java.util.function.Consumer<ActionEvent> action) {
        JMenuItem mi = new JMenuItem(new AbstractAction(text) {
            @Override public void actionPerformed(ActionEvent e) {
                action.accept(e);
            }
        });
        if (accel != null) {
            mi.setAccelerator(KeyStroke.getKeyStroke(accel));
        }
        if (tip != null) {
            mi.setToolTipText(tip);
        }
        return mi;
    }

    // ==================== файловые операции ====================
    private static void markDirty() {
        dirty = true;
        refreshTitle();
    }

    private static void afterChange() {
        markDirty();
        canvas.repaint();
        refreshJournal();
        canvas.updateStatus();
    }

    private static void refreshTitle() {
        frame.setTitle("Конструктор раскладок — "
            + (currentFile == null ? "без имени" : currentFile.getFileName())
            + (dirty ? " *" : ""));
    }

    /** true = можно продолжать (сохранено / не нужно / пользователь согласен). */
    private static boolean confirmDiscardChanges(String title) {
        if (!dirty) {
            return true;
        }
        int r = JOptionPane.showConfirmDialog(frame,
            Ui.text("Раскладка изменена и не сохранена.\nСохранить перед продолжением?"),
            title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) {
            return false;
        }
        if (r == JOptionPane.YES_OPTION) {
            return saveLayout(false);
        }
        return true;
    }

    private static void newLayout() {
        if (!confirmDiscardChanges("Новая раскладка")) {
            return;
        }
        // ОБЪЕКТ МОДЕЛИ НЕ ПОДМЕНЯЕМ, а очищаем на месте. Раньше здесь стояло
        // model = new Model(), и вкладка «Сборка из блоков» продолжала держать
        // ссылку на СТАРУЮ модель: сборка считалась для прежнего поля и не
        // совпадала с нарисованным (баг найден дизайнером 12.08.2026).
        model.hexes.clear();
        model.hexes.put(Model.key(0, 0), new LHex(0, 0));
        canvas.model = model;
        currentFile = null;
        dirty = false;
        canvas.fitToView();
        refreshTitle();
        refreshJournal();
        canvas.updateStatus();
    }

    private static void eraseField() {
        if (model.hexes.size() <= 1) {
            return;
        }
        int r = JOptionPane.showConfirmDialog(frame,
            Ui.text("Стереть ВСЁ поле (" + model.hexes.size() + " гексов)?\n"
                + "Действие можно откатить через «Правка → Отменить»."),
            "Очистить поле", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        canvas.pushUndo();
        model.hexes.clear();
        model.hexes.put(Model.key(0, 0), new LHex(0, 0));
        afterChange();
    }

    /**
     * Сохранение раскладки. Если в журнале есть КРАСНЫЕ замечания, раскладка
     * не годится для игры — предупреждаем и даём отказаться (решение дизайнера
     * 12.08.2026). Сохранить всё равно можно: черновик тоже имеет право на жизнь.
     */
    private static boolean saveLayout(boolean askPath) {
        List<Issue> issues = validate(model);
        List<String> errors = new ArrayList<>();
        for (Issue i : issues) {
            if (i.level() == 2) {
                errors.add("•  " + i.text());
            }
        }
        if (!errors.isEmpty()) {
            String list = String.join("\n", errors.subList(0, Math.min(8, errors.size())));
            if (errors.size() > 8) {
                list += "\n…и ещё " + (errors.size() - 8);
            }
            int ans = JOptionPane.showConfirmDialog(frame,
                Ui.text("Эта раскладка НЕ ГОДИТСЯ ДЛЯ ИГРЫ — в журнале "
                    + errors.size() + " красных замечаний:\n\n" + list
                    + "\n\nСохранить её всё равно (как черновик)?", 480),
                "Раскладка не готова", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ans != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        Path path = currentFile;
        if (askPath || path == null) {
            // ИМЯ ДЛЯ НОВОГО, ЕЩЁ НИ РАЗУ НЕ СОХРАНЁННОГО ПРОЕКТА — случайное, а не
            // одно и то же "scenario_Np.custom" для всех (просьба дизайнера
            // 14.08.2026: файлы с одинаковым именем путались и перезаписывали друг
            // друга). Уже сохранённый проект («Сохранить как…») по-прежнему
            // предлагает СВОЁ прежнее имя — суффикс тут не добавляется.
            Path start = path != null ? path
                : defaultScenarioDir().resolve(randomNewFileName());
            path = PathDialog.choose(frame, "Сохранить раскладку", start, true,
                FieldFile.EXT, FieldFile.READ_EXTS);
            if (path == null) {
                return false;
            }
        }
        // СОХРАНЯЕМ ВСЕГДА В СВОЙ ФОРМАТ: открыли старый .yaml — запишется .kmap
        path = FieldFile.withExt(path);
        String id = FieldFile.baseName(path);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", "editor");
        doc.put("scenarios", List.of(toScenarioMap(model, id)));
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setAllowUnicode(true);
        try {
            Files.writeString(path, new Yaml(opts).dump(doc), StandardCharsets.UTF_8);
            currentFile = path;
            dirty = false;
            refreshTitle();
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Не удалось сохранить файл.\n\n" + e.getMessage()),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void openLayout() {
        if (!confirmDiscardChanges("Открыть раскладку")) {
            return;
        }
        Path path = PathDialog.choose(frame, "Открыть раскладку",
            currentFile != null ? currentFile : defaultScenarioDir(), false,
            FieldFile.EXT, FieldFile.READ_EXTS);
        if (path == null) {
            return;
        }
        openLayoutFile(path);
    }

    /**
     * Открыть КОНКРЕТНЫЙ файл раскладки. Отдельным методом — потому что путь
     * приходит не только из диалога: конструктор запускают и двойным щелчком по
     * файлу, и командой с путём в аргументе (просьба дизайнера 13.08.2026).
     */
    @SuppressWarnings("unchecked")
    static void openLayoutFile(Path path) {
        try {
            Map<String, Object> data = new Yaml().load(
                Files.readString(path, StandardCharsets.UTF_8));
            List<Object> scns = (List<Object>) data.get("scenarios");
            List<Map<String, Object>> variants = new ArrayList<>();
            for (Object o : scns) {
                variants.add((Map<String, Object>) o);
            }
            Map<String, Object> scn = variants.get(0);
            if (variants.size() > 1) {
                String[] names = new String[variants.size()];
                for (int i = 0; i < variants.size(); i++) {
                    names[i] = String.valueOf(variants.get(i).getOrDefault("id", "вариант " + (i + 1)));
                }
                Object pick = JOptionPane.showInputDialog(frame,
                    Ui.text("В файле несколько раскладок — какую открыть?"), "Выбор раскладки",
                    JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
                if (pick == null) {
                    return;
                }
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(pick)) {
                        scn = variants.get(i);
                    }
                }
            }
            loadScenarioIntoModel(scn);
            currentFile = path;
            dirty = false;
            canvas.fitToView();
            refreshTitle();
            refreshJournal();
            canvas.updateStatus();
            // Если открыли файл, СИДЯ на вкладке сборки, обработчик смены вкладок
            // не сработает — пересчитываем сборку сами, иначе она показывала бы
            // прежнее поле (та же жалоба дизайнера 12.08.2026).
            if (tabs != null && tabs.getSelectedIndex() == 1 && assemblyTab != null) {
                assemblyTab.refresh();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Не удалось открыть раскладку.\n\n" + e.getMessage()),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Разобрать карту сценария в модель редактора (понимает и старый формат). */
    @SuppressWarnings("unchecked")
    static void loadScenarioIntoModel(Map<String, Object> scn) {
        List<Object> hexes = Scenario.expandedHexes(scn);
        model.hexes.clear();
        // players из файла НЕ читаем: состав считается по расставленным стартам.
        for (Object o : hexes) {
            Map<String, Object> e = (Map<String, Object>) o;
            LHex h = new LHex(((Number) e.get("q")).intValue(),
                ((Number) e.get("r")).intValue());
            h.content = String.valueOf(e.getOrDefault("content", "normal"));
            if (e.get("seat") instanceof Number sn) {
                h.seat = sn.intValue();
            }
            // СТАРЫЙ ФОРМАТ: content: container. Контейнер больше не «содержимое
            // гекса», а разметка поверх него — переносим в счётчик, а сам гекс
            // делаем обычным.
            if ("container".equals(h.content)) {
                h.content = "normal";
                h.containers = 1;
            }
            if (e.get("containers") instanceof Number cn) {
                h.containers = Math.max(0, Math.min(2, cn.intValue()));
            }
            if (e.get("stack") instanceof Number stn) {
                h.stack = Math.max(1, stn.intValue());
            }
            if (e.get("kelium_delta") instanceof Number kn) {
                h.keliumDelta = kn.intValue();
            }
            Object mod = e.get("modifier");   // старый формат
            if ("x2".equals(mod)) {
                h.stack = 2;
            } else if ("+1".equals(mod)) {
                h.keliumDelta += 1;
            } else if ("-1".equals(mod)) {
                h.keliumDelta -= 1;
            }
            Object nl = e.get("neutral_list");
            if (nl instanceof List<?> list) {
                for (Object no : list) {
                    Map<String, Object> ne = (Map<String, Object>) no;
                    List<Integer> corners = (List<Integer>) ne.get("corners");
                    int corner = corners != null && !corners.isEmpty() ? corners.get(0) : 1;
                    h.neutrals.add(new Neutral("big".equals(ne.get("size")), corner));
                }
            } else if ("neutral_building".equals(h.content)) {
                List<Integer> corners = (List<Integer>) e.get("corners");
                h.neutrals.add(new Neutral("big".equals(e.get("size")),
                    corners != null && !corners.isEmpty() ? corners.get(0) : 1));
                h.content = "normal";
            }
            model.hexes.put(Model.key(h.q, h.r), h);
        }
        // Модель одна на приложение и не подменяется, так что полотну ничего
        // передавать не надо — оно и так смотрит в неё. Присваивание оставлено
        // только для случая, когда полотна ещё нет (загрузка из тестов).
        if (canvas != null) {
            canvas.model = model;
        }
    }

    /**
     * Папка с раскладками — стартовая точка диалогов файла. Каталог данных ищет
     * {@link kelium.dataio.GameConfig#resolveDataRoot}, а не свой перебор путей:
     * иначе редактор и движок расходятся в том, где лежат данные, стоит папке
     * переехать.
     */
    private static Path defaultScenarioDir() {
        Path dir = kelium.dataio.GameConfig.resolveDataRoot(null).resolve("scenarios");
        return Files.isDirectory(dir) ? dir : Paths.get(".");
    }

    /**
     * СЛУЧАЙНОЕ ИМЯ для нового проекта: {@code scenario_<N>p_<ддммгггг>_<6 цифр>}
     * — дата и случайный отпечаток через подчёркивание (просьба дизайнера
     * 14.08.2026). Отпечаток спасает от совпадения имён при сохранении
     * нескольких черновиков в один день; дата помогает найти файл потом руками.
     */
    private static String randomNewFileName() {
        String date = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy"));
        int suffix = new java.util.Random().nextInt(1_000_000);
        return "scenario_" + Math.max(2, model.players()) + "p_" + date
            + "_" + String.format("%06d", suffix) + FieldFile.DOT_EXT;
    }

    // ==================== справка ====================
    private static void showGuide() {
        // Кегль и ширина текста считаются от масштаба интерфейса, семейство и
        // цвет — от темы: иначе справка оставалась мелкой и чёрной по чёрному.
        String head = "<html><body style='font-family:" + Theme.uiFamily()
            + "; font-size:" + Theme.px(12) + "px; width:" + Theme.px(560) + "px; color:#"
            + String.format("%06X", Theme.ink().getRGB() & 0xFFFFFF) + "'>";
        String html = head + """
            <h2>Конструктор раскладок — как пользоваться</h2>

            <h3>1. Размещение гексов</h3>
            <p>Выбери инструмент <b>⬡ Гекс</b>. Вокруг поля появятся пунктирные
            «призраки» — клик по призраку приклеивает новый гекс, клик по готовому
            гексу убирает его. Сетка призраков видна <b>только</b> в этом режиме.</p>
            <p>Ориентир по размеру: <b>7–10 гексов на игрока</b> (журнал следит).</p>

            <h3>2. Заселение гексов</h3>
            <ul>
              <li><b>Малое зарождение</b> — лицо 3 / оборот 2. Рядом со стартом
                  <i>не обязательно</i>: это лишь лёгкое замечание в журнале.
                  <b>ПКМ снимает зарождение.</b></li>
              <li><b>Большое зарождение</b> — лицо 4 / оборот 3.
                  <b>ПКМ снимает зарождение.</b></li>
              <li><b>Двойной тайл</b> — стопка из двух тайлов: вырабатывается дважды.</li>
              <li><b>Правка келемия</b> — ЛКМ +1, ПКМ −1 (в пределах ±4).</li>
              <li><b>Старт игрока</b> — ставит P1, P2… по порядку; повторный клик
                  снимает. Больше <b>четырёх</b> стартов поставить нельзя.</li>
              <li><b>Запретный гекс</b> — дыра в поле.</li>
              <li><b>Контейнер</b> — ЛКМ по кругу: нет → один → два → нет, ПКМ
                  в обратную сторону. Это <i>разметка печати</i>: правила берут
                  контейнеры не из файла поля.</li>
              <li><b>Нейтралы</b> — повторный клик поворачивает здание, после полного круга снимает.</li>
              <li><b>Очистить гекс</b> — вернуть гекс в обычное состояние.</li>
            </ul>

            <h3>3. Журнал проверок</h3>
            <p>Обновляется на каждое действие. Красное ✘ — <b>ошибка</b> (симулятор
            такую раскладку не примет или партия будет сломана), жёлтое ⚠ —
            предупреждение (играбельно, но выбивается из ориентиров), зелёное ✔ — норма.
            Последняя строка — прогон через настоящий загрузчик симулятора.</p>

            <h3>4. Сохранение</h3>
            <p><b>Файл → Сохранить</b> пишет YAML в формате симулятора. Сохранять можно
            в любую папку: <b>проигрыватель партий</b> подхватит раскладку, если эта папка
            добавлена у него в «Настройки → Папки с раскладками…» (каталог
            <code>simulator/data/scenarios/</code> учитывается всегда). В списке полей она
            появится под своим именем, и на ней можно играть.</p>
            <p>Чтобы раскладку брали ещё и пакетные прогоны, её нужно положить в
            <code>simulator/data/scenarios/</code> под именем
            <code>scenario_&lt;N&gt;p.&lt;версия&gt;.yaml</code> — там движок ищет
            авторские поля.</p>
            <p><b>Не забудь расставить стартовые гексы игроков</b> — без них раскладку
            не примет ни проигрыватель, ни прогон.</p>

            <h3>Управление мышью</h3>
            <p>Колесо — масштаб · правая кнопка (перетаскивание) — двигать поле ·
            Ctrl+Z — отменить · Ctrl+0 — вписать в окно.</p>
            </body></html>
            """;
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        // Справка — не белый лист поверх тёмного окна: цвета берутся у темы.
        pane.setBackground(Theme.panel());
        pane.setForeground(Theme.ink());
        JScrollPane sp = new JScrollPane(pane);
        sp.setPreferredSize(new Dimension(Theme.px(620), Theme.px(560)));
        JDialog d = new JDialog(frame, "Справка конструктора", false);
        d.add(sp);
        d.pack();
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
    }

    // ==================== холст ====================
    static final class Canvas extends JPanel {
        static final Color[] SEAT = {new Color(0x3b82d0), new Color(0xe07038),
            new Color(0x3f9e60), new Color(0xb04a96)};
        static final Color SPAWN_NORMAL = new Color(0x2E7D32);   // тёмно-зелёный
        static final Color SPAWN_START = new Color(0xA5D6A7);    // светло-зелёный
        static final Color NEUTRAL_FILL = new Color(0x9AA0A6);   // серебристый
        private static final Color NEUTRAL_EDGE = new Color(0x33383E);
        static final Color CONTAINER_FILL = new Color(0xE8C77B);
        private static final Color CONTAINER_EDGE = new Color(0x6E4E13);

        Model model = LayoutEditor.model;
        Tool tool = Tool.ADD;
        JLabel status;
        double size = 46;
        double panX = 560;
        double panY = 360;
        private int lastMx;
        private int lastMy;
        private int pressMx;
        private int pressMy;
        private final Deque<Map<Long, LHex>> undoStack = new ArrayDeque<>();

        Canvas() {
            setPreferredSize(new Dimension(Theme.px(2400), Theme.px(1700)));
            // ПОДЛОЖКА ПОЛЯ берётся из темы: на светлой это бумага, на тёмной —
            // тёмный стол, но всё равно светлее фона окна, чтобы поле читалось
            // отдельным предметом (тот же приём, что в разборе партии).
            setBackground(Theme.paper());
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    lastMx = e.getX();
                    lastMy = e.getY();
                    pressMx = e.getX();
                    pressMy = e.getY();
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        click(e.getX(), e.getY(), false);
                    }
                }

                @Override public void mouseReleased(MouseEvent e) {
                    // ПКМ без перетаскивания — «обратное» действие инструмента:
                    // убавить келемий, убрать нейтральное здание.
                    boolean hasSecondary = tool == Tool.KELIUM_DELTA
                        || tool == Tool.NEUTRAL_SMALL || tool == Tool.NEUTRAL_BIG
                        // в режиме зарождений ПКМ снимает тайл (12.08.2026)
                        || tool == Tool.KELIUM || tool == Tool.SPAWN_START;
                    if (SwingUtilities.isRightMouseButton(e) && hasSecondary
                            && Math.hypot(e.getX() - pressMx, e.getY() - pressMy) < 5) {
                        click(e.getX(), e.getY(), true);
                    }
                }

                @Override public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)
                            || SwingUtilities.isMiddleMouseButton(e)) {
                        panX += e.getX() - lastMx;
                        panY += e.getY() - lastMy;
                        lastMx = e.getX();
                        lastMy = e.getY();
                        repaint();
                    }
                }

                @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                    zoom(e.getPreciseWheelRotation() < 0 ? 1.12 : 1 / 1.12);
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("control Z"), "undo");
            getActionMap().put("undo", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    undo();
                }
            });
        }

        void zoom(double f) {
            size = Math.max(16, Math.min(120, size * f));
            repaint();
        }

        /**
         * ПЕРЕНЯТЬ ВИД У ПРЕЖНЕГО ПОЛОТНА при пересборке окна: масштаб поля,
         * сдвиг, выбранный инструмент и историю отмен. Раскладка не переносится
         * — модель одна на приложение и не подменяется.
         */
        void adoptFrom(Canvas old) {
            if (old == null) {
                return;
            }
            size = old.size;
            panX = old.panX;
            panY = old.panY;
            tool = old.tool;
            undoStack.clear();
            undoStack.addAll(old.undoStack);
            JToggleButton b = TOOL_BUTTONS.get(tool);
            if (b != null) {
                b.setSelected(true);
            }
        }

        void fitToView() {
            if (model.hexes.isEmpty()) {
                return;
            }
            int minQ = Integer.MAX_VALUE;
            int maxQ = Integer.MIN_VALUE;
            int minR = Integer.MAX_VALUE;
            int maxR = Integer.MIN_VALUE;
            for (LHex h : model.hexes.values()) {
                minQ = Math.min(minQ, h.q);
                maxQ = Math.max(maxQ, h.q);
                minR = Math.min(minR, h.r);
                maxR = Math.max(maxR, h.r);
            }
            double w = Math.max(600, getVisibleRect().width);
            double h = Math.max(400, getVisibleRect().height);
            // РАЗМЕР И СДВИГ — ОТ НАСТОЯЩЕЙ РАМКИ ПОЛЯ, а не от числа строк и
            // столбцов: поле развёрнуто на 30° (см. FieldGeometry.TILT), и старая
            // прикидка «столбцы × √3, строки × 1,5» после разворота врёт.
            double[] b = unitBounds();
            size = Math.max(18, Math.min(90,
                Math.min(w / (b[2] - b[0] + 1), h / (b[3] - b[1] + 1))));
            panX = w / 2 - size * (b[0] + b[2]) / 2;
            panY = h / 2 - size * (b[1] + b[3]) / 2;
            repaint();
        }

        /**
         * Нарисовать поле в картинку заданного размера: масштаб и сдвиг
         * подбираются так, чтобы поле поместилось целиком. Сетка призраков в
         * картинку не попадает — она нужна только при рисовании.
         */
        java.awt.image.BufferedImage render(int w, int h) {
            double savedSize = size;
            double savedX = panX;
            double savedY = panY;
            Tool savedTool = tool;
            tool = Tool.CLEAR_HEX;   // не режим размещения — призраков не будет
            double[] b = unitBounds();
            size = Math.max(18, Math.min(110,
                Math.min((w - 60) / (b[2] - b[0]), (h - 60) / (b[3] - b[1]))));
            panX = w / 2.0 - size * (b[0] + b[2]) / 2;
            panY = h / 2.0 - size * (b[1] + b[3]) / 2;
            java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            // ФОН КАДРА ЧУТЬ ТЕМНЕЕ БУМАГИ (просьба дизайнера 17.08.2026): так
            // поле читается как отдельный объект на листе, а белые гексы не
            // сливаются с полями страницы. Фон холста (Theme.paper()) здесь не
            // годится вовсе — в тёмной теме он чёрный.
            g.setColor(ExportPaint.FIELD_BG);
            g.fillRect(0, 0, w, h);
            int savedW = getWidth();
            int savedH = getHeight();
            // ФОН КОМПОНЕНТА ТОЖЕ ПОДМЕНЯЕТСЯ: paintComponent начинается с
            // super.paintComponent, а тот заливает всё цветом холста (в тёмной
            // теме — чёрным) и затирает уже положенную заливку кадра. Именно
            // здесь и протекала тёмная тема в выгруженный PNG.
            java.awt.Color savedBg = getBackground();
            setBackground(ExportPaint.FIELD_BG);
            setSize(w, h);
            ExportPaint.with(() -> paintComponent(g));
            setBackground(savedBg);
            setSize(savedW, savedH);
            g.dispose();
            size = savedSize;
            panX = savedX;
            panY = savedY;
            tool = savedTool;
            return img;
        }

        /**
         * СОДЕРЖИМОЕ БЕЗ ОБЫЧНЫХ ГЕКСОВ, ПРОЗРАЧНЫЙ ФОН — для СЛИЯНИЯ с сборкой
         * блоков (поправка дизайнера 14.08.2026: «блоки — на фоне позади всего,
         * обычные гексы вообще не рисовать»). Пустой гекс без тайла, старта,
         * нейтрала и контейнера пропускается целиком — сквозь него будет виден
         * цвет блока картона. {@code size}/{@code panX}/{@code panY} задаются
         * СНАРУЖИ, а не подбираются автоматически: слой блоков и слой содержимого
         * рисуются РАЗНЫМИ холстами и обязаны совпасть пиксель в пиксель, а свой
         * автоподбор (clamp 18–90 у сборки, 18–110 здесь) их развёл бы.
         */
        java.awt.image.BufferedImage renderContentOnly(int w, int h,
                                                        double fitSize, double fitPanX,
                                                        double fitPanY) {
            return renderContentOnly(w, h, fitSize, fitPanX, fitPanY, true);
        }

        /**
         * То же, но с явным выключателем слоя контейнеров (чекбокс в настройках
         * экспорта). Слои рисуются в порядке, назначенном дизайнером 17.08.2026:
         * тайлы зарождения, игроки, стартовые здания, потом контейнеры. Порядок
         * общий для всей картинки, а не для каждого гекса по отдельности —
         * иначе контейнер одного гекса ложился бы под тайл соседнего.
         */
        java.awt.image.BufferedImage renderContentOnly(int w, int h,
                                                        double fitSize, double fitPanX,
                                                        double fitPanY, boolean containers) {
            double savedSize = size;
            double savedX = panX;
            double savedY = panY;
            size = fitSize;
            panX = fitPanX;
            panY = fitPanY;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            int savedW = getWidth();
            int savedH = getHeight();
            setSize(w, h);
            ExportPaint.with(() -> drawContentLayers(g, containers));
            setSize(savedW, savedH);
            g.dispose();
            size = savedSize;
            panX = savedX;
            panY = savedY;
            return img;
        }

        /**
         * СОДЕРЖИМОЕ ПОЛЯ ПО СЛОЯМ — порядок задан дизайнером и повторяет
         * порядок, в котором это кладут на стол:
         *
         * <ol>
         *   <li>тайлы зарождения — их выкладывают на собранное поле первыми;</li>
         *   <li>игроки (стартовые гексы);</li>
         *   <li>стартовые здания — в конструкторе это нейтральные постройки,
         *       единственные здания, которые расставляет сама раскладка;</li>
         *   <li>контейнеры — отдельным слоем и с отдельным выключателем.</li>
         * </ol>
         *
         * <p>Слой блоков рисуется ДО этого метода (он фон), а тайлы запретных
         * гексов — ПОСЛЕ: физически это картонка поверх всего.
         *
         * <p>ОБЫЧНЫЕ ГЕКСЫ ЗДЕСЬ НЕ РИСУЮТСЯ ВОВСЕ: в слиянии их роль играют
         * блоки картона, и вторая сетка поверх первой только мешает.
         */
        private void drawContentLayers(java.awt.Graphics2D g, boolean containers) {
            for (LHex hx : model.hexes.values()) {
                if (hx.isSpawn()) {
                    double[] c = center(hx.q, hx.r);
                    drawSpawn(g, hx, c[0], c[1]);
                }
            }
            for (LHex hx : model.hexes.values()) {
                if ("player_start".equals(hx.content)) {
                    double[] c = center(hx.q, hx.r);
                    int seat = Math.max(0, hx.seat);
                    g.setColor(SEAT[seat % 4]);
                    double rr = size * 0.5;
                    g.fillOval((int) (c[0] - rr), (int) (c[1] - rr),
                        (int) (2 * rr), (int) (2 * rr));
                    g.setColor(Color.WHITE);
                    g.setFont(getFont().deriveFont(Font.BOLD, (float) (size * 0.40)));
                    drawCentered(g, "P" + (seat + 1), c[0], c[1]);
                }
            }
            for (LHex hx : model.hexes.values()) {
                double[] c = center(hx.q, hx.r);
                for (Neutral n : hx.neutrals) {
                    drawNeutral(g, c[0], c[1], n);
                }
            }
            if (containers) {
                for (LHex hx : model.hexes.values()) {
                    if (hx.containers > 0) {
                        double[] c = center(hx.q, hx.r);
                        drawContainers(g, hx, c[0], c[1]);
                    }
                }
            }
        }

        /**
         * КОНТЕЙНЕРЫ — САМЫЙ ВЕРХНИЙ СЛОЙ (порядок дизайнера 17.08.2026: шестым,
         * после тайлов запретных гексов). На печатном поле это метка ячейки, и
         * закрывать её не должно ничто. Гасится своим чекбоксом в настройках.
         */
        java.awt.image.BufferedImage renderContainersLayer(int w, int h, double fitSize,
                                                            double fitPanX, double fitPanY) {
            double savedSize = size;
            double savedX = panX;
            double savedY = panY;
            size = fitSize;
            panX = fitPanX;
            panY = fitPanY;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            int savedW = getWidth();
            int savedH = getHeight();
            setSize(w, h);
            for (LHex hx : model.hexes.values()) {
                if (hx.containers > 0) {
                    double[] c = center(hx.q, hx.r);
                    drawContainers(g, hx, c[0], c[1]);
                }
            }
            setSize(savedW, savedH);
            g.dispose();
            size = savedSize;
            panX = savedX;
            panY = savedY;
            return img;
        }

        /**
         * ТАЙЛЫ ЗАПРЕТНЫХ ГЕКСОВ, отмеченные в самой раскладке, — отдельным слоем
         * поверх содержимого. Чёрные накладки сборки (ячейки блока, торчащие за
         * край поля) рисует вид сборки; здесь только то, что дизайнер пометил
         * запретным сам.
         */
        java.awt.image.BufferedImage renderForbiddenLayer(int w, int h, double fitSize,
                                                           double fitPanX, double fitPanY) {
            double savedSize = size;
            double savedX = panX;
            double savedY = panY;
            size = fitSize;
            panX = fitPanX;
            panY = fitPanY;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            int savedW = getWidth();
            int savedH = getHeight();
            setSize(w, h);
            for (LHex hx : model.hexes.values()) {
                if (!"forbidden".equals(hx.content)) {
                    continue;
                }
                double[] c = center(hx.q, hx.r);
                Polygon poly = hexPoly(c[0], c[1], size * 0.88);
                g.setColor(ExportPaint.FORBIDDEN_FILL);
                g.fillPolygon(poly);
                g.setColor(new Color(0x5A6068));
                g.setStroke(new BasicStroke(1.6f));
                g.drawPolygon(poly);
                g.setColor(new Color(0xE0E0E0));
                g.setFont(getFont().deriveFont(Font.BOLD, (float) (size * 0.46)));
                drawCentered(g, "\u2715", c[0], c[1]);
            }
            setSize(savedW, savedH);
            g.dispose();
            size = savedSize;
            panX = savedX;
            panY = savedY;
            return img;
        }

        /**
         * ТОНКАЯ ГЕКСОВАЯ СЕТКА ПО ВСЕЙ ПЛОЩАДИ КАДРА, угасающая по мере удаления
         * от поля (просьба дизайнера 17.08.2026).
         *
         * <p>Зачем: в слиянии обычные гексы не рисуются вовсе, и поле висит в
         * пустоте — непонятно, где кончается картон и начинается пустое место.
         * Сетка показывает продолжение сетки поля и гаснет, не споря с ним.
         *
         * <p>Гексы, которые есть в раскладке, пропускаются: там уже нарисован
         * блок картона, и обводить его второй раз незачем.
         */
        java.awt.image.BufferedImage renderHexGridLayer(int w, int h, double fitSize,
                                                         double fitPanX, double fitPanY) {
            double savedSize = size;
            double savedX = panX;
            double savedY = panY;
            size = fitSize;
            panX = fitPanX;
            panY = fitPanY;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1f));

            // Насколько далеко от поля сетка гаснет полностью — в гексах.
            final int fade = 5;
            java.util.List<int[]> real = new java.util.ArrayList<>();
            int minQ = Integer.MAX_VALUE;
            int maxQ = Integer.MIN_VALUE;
            int minR = Integer.MAX_VALUE;
            int maxR = Integer.MIN_VALUE;
            for (LHex hx : model.hexes.values()) {
                real.add(new int[]{hx.q, hx.r});
                minQ = Math.min(minQ, hx.q);
                maxQ = Math.max(maxQ, hx.q);
                minR = Math.min(minR, hx.r);
                maxR = Math.max(maxR, hx.r);
            }
            if (real.isEmpty()) {
                g.dispose();
                size = savedSize;
                panX = savedX;
                panY = savedY;
                return img;
            }
            // Перебираем с запасом: кадр шире поля, и сетка обязана дойти до краёв.
            int span = (int) Math.ceil(Math.max(w, h) / Math.max(1.0, size)) + fade + 2;
            java.util.Set<Long> occupied = new java.util.HashSet<>();
            for (int[] qr : real) {
                occupied.add(Model.key(qr[0], qr[1]));
            }
            for (int q = minQ - span; q <= maxQ + span; q++) {
                for (int r = minR - span; r <= maxR + span; r++) {
                    if (occupied.contains(Model.key(q, r))) {
                        continue;   // здесь уже лежит блок картона
                    }
                    double[] c = center(q, r);
                    if (c[0] < -size || c[0] > w + size || c[1] < -size || c[1] > h + size) {
                        continue;   // за пределами кадра
                    }
                    int dist = Integer.MAX_VALUE;
                    for (int[] qr : real) {
                        dist = Math.min(dist, axialDist(q, r, qr[0], qr[1]));
                        if (dist <= 1) {
                            break;
                        }
                    }
                    if (dist > fade) {
                        continue;   // угасла совсем
                    }
                    // Гаснет ПО КВАДРАТУ, а не линейно: линейное угасание на глаз
                    // читается как ровная сетка до самого края кадра.
                    double t = 1.0 - (dist - 1) / (double) fade;
                    int alpha = (int) Math.round(95.0 * t * t);
                    if (alpha <= 2) {
                        continue;
                    }
                    g.setColor(new Color(ExportPaint.GRID.getRed(),
                        ExportPaint.GRID.getGreen(), ExportPaint.GRID.getBlue(),
                        Math.min(255, Math.max(0, alpha))));
                    g.drawPolygon(hexPoly(c[0], c[1], size * 0.99));
                }
            }
            g.dispose();
            size = savedSize;
            panX = savedX;
            panY = savedY;
            return img;
        }

        void updateStatus() {
            if (status == null) {
                return;
            }
            int starts = 0;
            int spawns = 0;
            for (LHex h : model.hexes.values()) {
                if ("player_start".equals(h.content)) {
                    starts++;
                }
                if (h.isSpawn()) {
                    spawns += h.stack;
                }
            }
            status.setText(statusText(tool.label, model.hexes.size(), starts,
                model.players(), spawns));
        }

        void pushUndo() {
            Map<Long, LHex> snap = new LinkedHashMap<>();
            for (var e : model.hexes.entrySet()) {
                LHex src = e.getValue();
                LHex c = new LHex(src.q, src.r);
                c.content = src.content;
                c.seat = src.seat;
                c.stack = src.stack;
                c.keliumDelta = src.keliumDelta;
                for (Neutral n : src.neutrals) {
                    c.neutrals.add(new Neutral(n.big, n.corner));
                }
                snap.put(e.getKey(), c);
            }
            undoStack.push(snap);
            if (undoStack.size() > 100) {
                undoStack.removeLast();
            }
        }

        void undo() {
            if (undoStack.isEmpty()) {
                return;
            }
            model.hexes.clear();
            model.hexes.putAll(undoStack.pop());
            afterChange();
        }

        private double[] center(int q, int r) {
            double[] c = kelium.report.FieldGeometry.hexCenter(q, r, size);
            return new double[]{panX + c[0], panY + c[1]};
        }

        /** Рамка поля в единицах радиуса гекса: {minX, minY, maxX, maxY}. */
        private double[] unitBounds() {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (LHex h : model.hexes.values()) {
                double[] c = kelium.report.FieldGeometry.hexCenter(h.q, h.r, 1);
                minX = Math.min(minX, c[0] - 1);
                maxX = Math.max(maxX, c[0] + 1);
                minY = Math.min(minY, c[1] - 1);
                maxY = Math.max(maxY, c[1] + 1);
            }
            return minX > maxX ? new double[]{-1, -1, 1, 1}
                : new double[]{minX, minY, maxX, maxY};
        }

        // ---------- клик ----------
        private void click(int mx, int my, boolean secondary) {
            long bestKey = 0;
            boolean bestGhost = false;
            double bestD = Double.MAX_VALUE;
            int bq = 0;
            int br = 0;
            for (LHex h : model.hexes.values()) {
                double[] c = center(h.q, h.r);
                double d = Math.hypot(mx - c[0], my - c[1]);
                if (d < bestD) {
                    bestD = d;
                    bestKey = Model.key(h.q, h.r);
                    bestGhost = false;
                    bq = h.q;
                    br = h.r;
                }
            }
            if (tool.placement) {
                for (long g : ghostKeys()) {
                    int q = (int) (g >> 32);
                    int r = (int) g;
                    double[] c = center(q, r);
                    double d = Math.hypot(mx - c[0], my - c[1]);
                    if (d < bestD) {
                        bestD = d;
                        bestKey = g;
                        bestGhost = true;
                        bq = q;
                        br = r;
                    }
                }
            }
            if (bestD > size * 0.95) {
                return;
            }
            pushUndo();
            boolean changed;
            if (bestGhost) {
                model.hexes.put(bestKey, new LHex(bq, br));
                changed = true;
            } else {
                // к какому КРАЮ гекса относится клик — по нему ставится нейтрал
                double[] c = center(bq, br);
                double ang = Math.toDegrees(Math.atan2(my - c[1], mx - c[0]));
                int edge = Math.floorMod((int) Math.round((ang + 60) / 60.0), 6);
                changed = applyTool(model.hexes.get(bestKey), bestKey, secondary, edge + 1);
            }
            if (!changed) {
                undoStack.pop();   // ничего не поменяли — не засоряем историю
                return;
            }
            afterChange();
        }

        private boolean applyTool(LHex h, long key, boolean secondary, int clickedCorner) {
            switch (tool) {
                case ADD -> {
                    if (model.hexes.size() <= 1) {
                        return false;
                    }
                    model.hexes.remove(key);
                }
                case CLEAR_HEX -> {
                    h.content = "normal";
                    h.seat = -1;
                    h.stack = 1;
                    h.keliumDelta = 0;
                    h.containers = 0;
                    h.neutrals.clear();
                }
                // КОНТЕЙНЕРЫ — по кругу 0 → 1 → 2 → 0, ПКМ в обратную сторону.
                // На запретном гексе контейнера не бывает: он вне поля.
                case CONTAINER -> {
                    if ("forbidden".equals(h.content)) {
                        hint("Запретный гекс вне поля — контейнеру там не на чем стоять");
                        return false;
                    }
                    h.containers = Math.floorMod(h.containers + (secondary ? -1 : 1), 3);
                }
                // ПКМ в режиме зарождений СНИМАЕТ тайл (просьба дизайнера
                // 12.08.2026): не нужно переключаться на «Очистить гекс».
                // Снимается любое зарождение, а не только того же размера.
                case KELIUM -> {
                    if (secondary) {
                        return LayoutEditor.clearSpawn(h);
                    }
                    h.content = "kelium_tile";
                    h.seat = -1;
                }
                case SPAWN_START -> {
                    if (secondary) {
                        return LayoutEditor.clearSpawn(h);
                    }
                    h.content = "spawn_start";
                    h.seat = -1;
                }
                case STACK -> {
                    if (!h.isSpawn()) {
                        return false;   // стопка бывает только у тайлов зарождения
                    }
                    h.stack = h.stack >= 2 ? 1 : 2;
                }
                case KELIUM_DELTA -> {
                    if (!h.isSpawn()) {
                        return false;
                    }
                    int d = secondary ? -1 : 1;
                    int nv = h.keliumDelta + d;
                    if (nv < -4 || nv > 4) {
                        return false;
                    }
                    h.keliumDelta = nv;
                }
                case PLAYER -> {
                    if ("player_start".equals(h.content)) {
                        h.content = "normal";
                        h.seat = -1;
                        // СОСТАВ = ЧИСЛО СТАРТОВ, поэтому места обязаны идти
                        // подряд: сняли P2 из трёх — бывший P3 становится P2,
                        // иначе в файле окажется дыра в нумерации мест.
                        renumberSeats();
                    } else {
                        // ПОТОЛОК СОСТАВА — ЧЕТЫРЕ (просьба дизайнера 13.08.2026,
                        // п. 12 заказа). Компонентов игрока в коробке четыре
                        // комплекта, пятому месту взять нечего.
                        if (!canAddStart(model)) {
                            hint("Больше " + MAX_SEATS + " стартов не бывает: в игре "
                                + "не больше четырёх мест. Сними лишний старт, "
                                + "чтобы поставить его в другом месте");
                            return false;
                        }
                        Set<Integer> used = new HashSet<>();
                        for (LHex x : model.hexes.values()) {
                            if ("player_start".equals(x.content)) {
                                used.add(x.seat);
                            }
                        }
                        int seat = 0;
                        while (used.contains(seat)) {
                            seat++;
                        }
                        h.content = "player_start";
                        h.seat = seat;
                        h.stack = 1;
                        h.keliumDelta = 0;
                    }
                }
                case FORBIDDEN -> {
                    h.content = "forbidden";
                    h.seat = -1;
                    h.stack = 1;
                    h.keliumDelta = 0;
                    h.containers = 0;
                    h.neutrals.clear();
                }
                case NEUTRAL_SMALL, NEUTRAL_BIG -> {
                    boolean big = tool == Tool.NEUTRAL_BIG;
                    Neutral under = neutralAtCorner(h, clickedCorner);
                    if (secondary) {
                        // ПКМ — снять здание под курсором
                        if (under == null) {
                            return false;
                        }
                        h.neutrals.remove(under);
                        return true;
                    }
                    if (under != null) {
                        if (under.big != big) {
                            hint("Тут стоит " + (under.big ? "большое" : "малое")
                                + " здание — возьми соответствующий инструмент или сними его ПКМ");
                            return false;
                        }
                        // повернуть на следующий СВОБОДНЫЙ край
                        Integer next = freeCornerFrom(h, under, under.corner);
                        if (next == null) {
                            hint("Повернуть некуда: соседние края заняты");
                            return false;
                        }
                        under.corner = next;
                        return true;
                    }
                    // поставить новое: сначала пробуем туда, куда кликнули
                    if (spanFree(h, clickedCorner, big, null)) {
                        h.neutrals.add(new Neutral(big, clickedCorner));
                        return true;
                    }
                    Integer spot = freeCornerFrom(h, null, clickedCorner);
                    if (spot == null) {
                        hint("На этом гексе больше нет свободных краёв под "
                            + (big ? "большое" : "малое") + " здание");
                        return false;
                    }
                    h.neutrals.add(new Neutral(big, spot));
                }
                default -> {
                    return false;
                }
            }
            return true;
        }

        // ---- нейтральные здания: несколько на гекс, по свободным краям ----

        /** Здание, чья СТЕНКА приходится на ребро, начинающееся в этом углу. */
        private static Neutral neutralAtCorner(LHex h, int corner) {
            int wall = Math.floorMod(2 - corner, 6);
            for (Neutral n : h.neutrals) {
                if (wallsOf(n.big, n.corner).contains(wall)) {
                    return n;
                }
            }
            return null;
        }

        /**
         * Стенки гекса, которые занимает здание: ребро между углами c и c+1 —
         * это сторона (2−c) mod 6 (та же формула, что в движке,
         * {@code Hex.NeutralBuilding.wallSides()}). Малое здание закрывает одну
         * стенку, большое — две.
         */
        private static Set<Integer> wallsOf(boolean big, int startCorner) {
            Set<Integer> out = new HashSet<>();
            int walls = big ? 2 : 1;
            for (int i = 0; i < walls; i++) {
                int c = (startCorner - 1 + i) % 6 + 1;
                out.add(Math.floorMod(2 - c, 6));
            }
            return out;
        }

        /**
         * Свободны ли СТЕНКИ, которые занял бы блок с началом в startCorner.
         * Раньше проверялись углы — из-за этого два здания на соседних рёбрах
         * считались конфликтующими, хотя делят они только угловую точку и
         * физически стоят вплотную (замечание дизайнера).
         */
        private static boolean spanFree(LHex h, int startCorner, boolean big, Neutral except) {
            Set<Integer> want = wallsOf(big, startCorner);
            for (Neutral n : h.neutrals) {
                if (n == except) {
                    continue;
                }
                for (int w : wallsOf(n.big, n.corner)) {
                    if (want.contains(w)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Следующий свободный угол по часовой, начиная со следующего за from.
         * moving — здание, которое двигаем (его собственные углы не мешают);
         * null, если ставим новое.
         */
        private static Integer freeCornerFrom(LHex h, Neutral moving, int from) {
            boolean big = moving != null ? moving.big
                : LayoutEditor.canvas.tool == Tool.NEUTRAL_BIG;
            for (int step = 1; step <= 6; step++) {
                int c = (from - 1 + step) % 6 + 1;
                if (spanFree(h, c, big, moving)) {
                    return c;
                }
            }
            return null;
        }

        /** Подсказка в статусной строке (почему действие не сработало). */
        private void hint(String text) {
            if (status != null) {
                status.setText("⚠  " + text);
            }
        }

        private Set<Long> ghostKeys() {
            Set<Long> out = new HashSet<>();
            for (LHex h : model.hexes.values()) {
                for (int[] d : DIRS) {
                    long k = Model.key(h.q + d[0], h.r + d[1]);
                    if (!model.hexes.containsKey(k)) {
                        out.add(k);
                    }
                }
            }
            return out;
        }

        // ---------- отрисовка ----------
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            // сетка призраков — ТОЛЬКО в режиме размещения гексов
            if (tool.placement) {
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_ROUND, 0, new float[]{5, 5}, 0));
                g.setColor(Theme.ink3());
                for (long k : ghostKeys()) {
                    double[] c = center((int) (k >> 32), (int) k);
                    g.draw(hexPoly(c[0], c[1], size * 0.94));
                }
            }

            for (LHex h : model.hexes.values()) {
                double[] c = center(h.q, h.r);
                drawHex(g, h, c[0], c[1]);
            }
        }

        private void drawHex(Graphics2D g, LHex h, double cx, double cy) {
            drawHexBody(g, h, cx, cy);
            // Кегль подписей на поле считается от РАЗМЕРА ГЕКСА, а не от масштаба
            // интерфейса: Theme.font() домножил бы его на масштаб второй раз.
            g.setFont(getFont().deriveFont((float) (size * 0.17)));
            g.setColor(ExportPaint.active() ? ExportPaint.LABEL : Theme.ink3());
            drawCentered(g, h.q + "," + h.r, cx, cy - size * 0.70);
        }

        /** Пустой обычный гекс — ни тайла, ни старта, ни нейтрала, ни контейнера. */
        private static boolean isPlainEmpty(LHex h) {
            return "normal".equals(h.content) && h.neutrals.isEmpty() && h.containers == 0;
        }

        /**
         * ТЕЛО ГЕКСА без подписи координат — общее для обычного полотна и для
         * СЛИЯНИЯ (просьба дизайнера 14.08.2026): гекс с заливкой и рамкой плюс
         * всё, что на нём стоит.
         */
        private void drawHexBody(Graphics2D g, LHex h, double cx, double cy) {
            Polygon poly = hexPoly(cx, cy, size);
            g.setColor(baseFill(h));
            g.fillPolygon(poly);
            g.setStroke(new BasicStroke(2f));
            g.setColor(hexEdge());
            g.drawPolygon(poly);

            switch (h.content) {
                case "kelium_tile", "spawn_start" -> drawSpawn(g, h, cx, cy);
                case "player_start" -> {
                    int seat = Math.max(0, h.seat);
                    g.setColor(SEAT[seat % 4]);
                    double rr = size * 0.5;
                    g.fillOval((int) (cx - rr), (int) (cy - rr), (int) (2 * rr), (int) (2 * rr));
                    g.setColor(Color.WHITE);
                    g.setFont(getFont().deriveFont(Font.BOLD, (float) (size * 0.40)));
                    drawCentered(g, "P" + (seat + 1), cx, cy);
                }
                case "forbidden" -> {
                    g.setColor(new Color(0xE0E0E0));
                    g.setFont(getFont().deriveFont(Font.BOLD, (float) (size * 0.46)));
                    drawCentered(g, "✕", cx, cy);
                }
                default -> { }
            }

            // Контейнеры рисуются ПОВЕРХ тайла зарождения и подписи старта:
            // на печатном поле это отдельная метка ячейки, её не должно
            // закрывать содержимое гекса.
            if (h.containers > 0) {
                drawContainers(g, h, cx, cy);
            }
            for (Neutral n : h.neutrals) {
                drawNeutral(g, cx, cy, n);
            }
        }

        /** Тайл зарождения: тёмно-зелёный (обычный) / светло-зелёный (стартовый). */
        private void drawSpawn(Graphics2D g, LHex h, double cx, double cy) {
            boolean start = "spawn_start".equals(h.content);
            // двойной тайл — вторая «карточка» видна из-под первой
            if (h.stack >= 2) {
                Polygon back = hexPoly(cx + size * 0.09, cy + size * 0.09, size * 0.80);
                g.setColor(start ? SPAWN_START.darker() : SPAWN_NORMAL.darker());
                g.fillPolygon(back);
                g.setColor(new Color(0x24501f));
                g.setStroke(new BasicStroke(1.6f));
                g.drawPolygon(back);
            }
            Polygon tile = hexPoly(cx, cy, size * 0.80);
            g.setColor(start ? SPAWN_START : SPAWN_NORMAL);
            g.fillPolygon(tile);
            g.setColor(new Color(0x1B5E20));
            g.setStroke(new BasicStroke(2f));
            g.drawPolygon(tile);

            g.setColor(start ? new Color(0x1B5E20) : Color.WHITE);
            g.setFont(getFont().deriveFont(Font.BOLD, (float) (size * 0.50)));
            drawCentered(g, start ? "S" : "K", cx, cy + size * 0.02);

            String note = (h.stack >= 2 ? "×2" : "")
                + (h.keliumDelta != 0
                   ? (h.keliumDelta > 0 ? " +" + h.keliumDelta : " " + h.keliumDelta) : "");
            if (!note.isEmpty()) {
                g.setFont(getFont().deriveFont(Font.BOLD, (float) (size * 0.24)));
                drawCentered(g, note.trim(), cx, cy + size * 0.42);
            }
            // итоговый келемий на лице — мелко сверху
            g.setFont(getFont().deriveFont((float) (size * 0.20)));
            drawCentered(g, h.faceKelium() + "кел", cx, cy - size * 0.34);
        }

        /**
         * КОНТЕЙНЕРЫ на гексе — карточки со скруглением, двойной сдвинут по
         * диагонали, чтобы было видно обе. Стоят в нижней части гекса: сверху
         * подпись координат, в середине — тайл или номер места.
         */
        private void drawContainers(Graphics2D g, LHex h, double cx, double cy) {
            double w = size * 0.34;
            double hh = size * 0.26;
            double arc = size * 0.10;
            double baseY = cy + size * 0.44;
            for (int i = h.containers - 1; i >= 0; i--) {
                double x = cx - w / 2 + i * size * 0.10;
                double y = baseY - hh / 2 - i * size * 0.10;
                g.setColor(CONTAINER_FILL);
                g.fillRoundRect((int) Math.round(x), (int) Math.round(y),
                    (int) Math.round(w), (int) Math.round(hh),
                    (int) Math.round(arc), (int) Math.round(arc));
                g.setColor(CONTAINER_EDGE);
                g.setStroke(new BasicStroke(1.8f));
                g.drawRoundRect((int) Math.round(x), (int) Math.round(y),
                    (int) Math.round(w), (int) Math.round(hh),
                    (int) Math.round(arc), (int) Math.round(arc));
                // защёлка посередине — чтобы карточка читалась как ящик
                g.drawLine((int) Math.round(x), (int) Math.round(y + hh / 2),
                    (int) Math.round(x + w), (int) Math.round(y + hh / 2));
            }
        }

        /** Нейтральное здание — полоса по краю гекса: малое 2 угла, большое 3. */
        private void drawNeutral(Graphics2D g, double cx, double cy, Neutral n) {
            List<Integer> corners = n.corners();
            double outer = 0.90;
            double inner = 0.52;
            Polygon p = new Polygon();
            for (int corner : corners) {
                double a = Math.toRadians(60 * (corner - 1) - 90
                    + kelium.report.FieldGeometry.TILT);
                p.addPoint((int) (cx + size * outer * Math.cos(a)),
                    (int) (cy + size * outer * Math.sin(a)));
            }
            for (int i = corners.size() - 1; i >= 0; i--) {
                double a = Math.toRadians(60 * (corners.get(i) - 1) - 90
                    + kelium.report.FieldGeometry.TILT);
                p.addPoint((int) (cx + size * inner * Math.cos(a)),
                    (int) (cy + size * inner * Math.sin(a)));
            }
            g.setColor(n.big ? NEUTRAL_FILL.darker() : NEUTRAL_FILL);
            g.fillPolygon(p);
            g.setColor(NEUTRAL_EDGE);
            g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(p);
        }

        /**
         * ЦВЕТ ГЕКСА ПОД ТЕМУ. На светлой теме это белый картон, каким поле
         * уходит в печать; на тёмной — тёмная плитка светлее подложки, иначе
         * поле било бы белым листом посреди тёмного окна.
         */
        private Color baseFill(LHex h) {
            // ЭКСПОРТ НИКОГДА НЕ БЫВАЕТ ТЁМНЫМ: картинка уходит в печать, и тема
            // интерфейса к ней отношения не имеет (баг дизайнера 17.08.2026 —
            // выгрузка темнела вместе с окном).
            if (ExportPaint.active()) {
                return switch (h.content) {
                    case "forbidden" -> ExportPaint.FORBIDDEN_FILL;
                    default -> ExportPaint.HEX_FILL;
                };
            }
            boolean dark = Theme.isDark();
            return switch (h.content) {
                case "forbidden" -> dark ? new Color(0x0E1116) : new Color(0x4a4844);
                case "player_start" -> dark ? new Color(0x333A44) : new Color(0xFFFDF5);
                default -> dark ? new Color(0x2A313B) : Color.WHITE;
            };
        }

        /** Обводка гекса — по теме, иначе на тёмном фоне её не видно. */
        private static Color hexEdge() {
            if (ExportPaint.active()) {
                return ExportPaint.HEX_EDGE;
            }
            return Theme.isDark() ? new Color(0x79838F) : new Color(0x6d6a5e);
        }

        private Polygon hexPoly(double cx, double cy, double s) {
            Polygon p = new Polygon();
            for (int k = 0; k < 6; k++) {
                double a = Math.toRadians(60 * k - 90
                    + kelium.report.FieldGeometry.TILT);
                p.addPoint((int) Math.round(cx + s * Math.cos(a)),
                    (int) Math.round(cy + s * Math.sin(a)));
            }
            return p;
        }

        private void drawCentered(Graphics2D g, String s, double x, double y) {
            var fm = g.getFontMetrics();
            g.drawString(s, (float) (x - fm.stringWidth(s) / 2.0),
                (float) (y + fm.getAscent() / 2.5));
        }
    }

    // ==================== сериализация ====================

    /** Модель → карта сценария (формат «hexes», который читает симулятор). */
    /**
     * На сколько сдвинуть координаты, чтобы (0,0) достался САМОМУ ЦЕНТРАЛЬНОМУ
     * гексу карты. Центральный — это ближайший к геометрическому центру всех
     * гексов, а не к середине диапазона q/r: гексовая сетка косая, и «средний
     * номер» в ней запросто указывает за пределы поля.
     *
     * <p>Ничьей быть не может: при равном расстоянии берём меньший (q, r) —
     * иначе одна и та же карта сохранялась бы с разным нулём.
     */
    private static int[] centreShift(Model m) {
        if (m.hexes.isEmpty()) {
            return new int[]{0, 0};
        }
        double sx = 0;
        double sy = 0;
        for (LHex h : m.hexes.values()) {
            double[] c = kelium.report.FieldGeometry.hexCenter(h.q, h.r, 1.0);
            sx += c[0];
            sy += c[1];
        }
        sx /= m.hexes.size();
        sy /= m.hexes.size();
        LHex best = null;
        double bestD = Double.MAX_VALUE;
        for (LHex h : m.hexes.values()) {
            double[] c = kelium.report.FieldGeometry.hexCenter(h.q, h.r, 1.0);
            double d = (c[0] - sx) * (c[0] - sx) + (c[1] - sy) * (c[1] - sy);
            if (d < bestD - 1e-9
                    || (Math.abs(d - bestD) <= 1e-9 && best != null
                        && (h.q < best.q || (h.q == best.q && h.r < best.r)))) {
                bestD = d;
                best = h;
            }
        }
        return new int[]{best.q, best.r};
    }

    public static Map<String, Object> toScenarioMap(Model m, String id) {
        // НАЧАЛО КООРДИНАТ — В ГЕОМЕТРИЧЕСКОМ ЦЕНТРЕ ПОЛЯ (просьба дизайнера
        // 16.08.2026). Рисовать раскладку начинают с любого места, и у готовой
        // карты (0,0) оказывался где придётся — иногда у самого края. Сдвиг
        // считаем один раз здесь, при записи: пока карту правят, номера гексов
        // не должны прыгать под руками.
        int[] shift = centreShift(m);
        List<Map<String, Object>> hexes = new ArrayList<>();
        for (LHex h : m.hexes.values()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("q", h.q - shift[0]);
            e.put("r", h.r - shift[1]);
            String content = h.content;
            if (!"normal".equals(content)) {
                e.put("content", content);
            }
            if ("player_start".equals(h.content)) {
                e.put("seat", h.seat);
            }
            if (h.isSpawn()) {
                if (h.stack > 1) {
                    e.put("stack", h.stack);
                }
                if (h.keliumDelta != 0) {
                    e.put("kelium_delta", h.keliumDelta);
                }
            }
            // РАЗМЕТКА ПЕЧАТИ: отдельным ключом, а не через content — гекс с
            // контейнером остаётся обычным (на нём строят и ходят). Старый
            // формат писал content: container и тем самым терял всё остальное.
            if (h.containers > 0) {
                e.put("containers", h.containers);
            }
            if (!h.neutrals.isEmpty()) {
                List<Map<String, Object>> nl = new ArrayList<>();
                for (Neutral n : h.neutrals) {
                    Map<String, Object> ne = new LinkedHashMap<>();
                    ne.put("size", n.big ? "big" : "small");
                    ne.put("corners", n.corners());
                    nl.add(ne);
                }
                e.put("neutral_list", nl);
            }
            hexes.add(e);
        }
        Map<String, Object> scn = new LinkedHashMap<>();
        scn.put("id", id);
        scn.put("players", Math.max(2, m.players()));
        scn.put("_made_with", "LayoutEditor");
        scn.put("hexes", hexes);
        return scn;
    }

    // ==================== живой журнал валидации ====================

    public record Issue(int level, String text) { }    // 0 ok / 1 warn / 2 error

    private static void refreshJournal() {
        List<Issue> issues = validate(model);
        if (playersLabel != null) {
            int n = model.players();
            playersLabel.setText(n == 0 ? "— (поставь старты)" : String.valueOf(n));
            playersLabel.setForeground(n >= 2 && n <= MAX_SEATS
                ? Theme.good() : Theme.bad());
        }
        StyledDocument doc = journal.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            int errors = 0;
            int warns = 0;
            for (Issue i : issues) {
                if (i.level() == 2) {
                    errors++;
                }
                if (i.level() == 1) {
                    warns++;
                }
            }
            SimpleAttributeSet head = new SimpleAttributeSet();
            StyleConstants.setBold(head, true);
            StyleConstants.setForeground(head, errors > 0 ? Theme.bad() : Theme.good());
            doc.insertString(doc.getLength(),
                (errors > 0 ? "РАСКЛАДКА НЕ ГОТОВА" : "РАСКЛАДКА В ПОРЯДКЕ")
                + String.format("%nошибок %d · предупреждений %d%n%n", errors, warns), head);
            for (Issue i : issues) {
                SimpleAttributeSet a = new SimpleAttributeSet();
                String mark;
                switch (i.level()) {
                    case 2 -> {
                        StyleConstants.setForeground(a, Theme.bad());
                        StyleConstants.setBold(a, true);
                        mark = "✘ ";
                    }
                    case 1 -> {
                        // Жёлтый берётся от темы: на тёмной 0x9a6a00 читался как
                        // грязное пятно, а не как предупреждение.
                        StyleConstants.setForeground(a, Theme.isDark()
                            ? new Color(0xE0A93B) : new Color(0x9a6a00));
                        mark = "⚠ ";
                    }
                    default -> {
                        StyleConstants.setForeground(a, Theme.good());
                        mark = "✔ ";
                    }
                }
                doc.insertString(doc.getLength(), mark + i.text() + "\n", a);
            }
            journal.setCaretPosition(0);
        } catch (BadLocationException ignored) {
            // пишем в конец — не бывает
        }
    }

    /** Все проверки раскладки; уровни: 0 ок, 1 предупреждение, 2 ошибка. */
    public static List<Issue> validate(Model m) {
        List<Issue> out = new ArrayList<>();
        int n = m.players();
        Map<Integer, LHex> starts = new HashMap<>();
        int spawnStarts = 0;
        int grids = 0;
        for (LHex h : m.hexes.values()) {
            switch (h.content) {
                case "player_start" -> {
                    if (starts.put(h.seat, h) != null) {
                        out.add(new Issue(2, "Два старта на место " + (h.seat + 1)));
                    }
                }
                case "spawn_start" -> spawnStarts++;
                case "kelium_tile" -> grids += h.stack;
                default -> { }
            }
        }

        // --- размер поля: 7–10 гексов на игрока ---
        // При n < 2 состав ещё не задан — ориентиры считать не от чего.
        int lo = 7 * n;
        int hi = 10 * n;
        if (n < 2) {
            lo = 0;
            hi = Integer.MAX_VALUE;
        }
        if (m.hexes.size() < lo) {
            out.add(new Issue(1, "Мало гексов: " + m.hexes.size()
                + " (ориентир " + lo + "–" + hi + " на " + n + " игроков)"));
        } else if (m.hexes.size() > hi) {
            out.add(new Issue(1, "Много гексов: " + m.hexes.size()
                + " (ориентир " + lo + "–" + hi + ")"));
        } else if (n >= 2) {
            out.add(new Issue(0, "Размер поля в норме: " + m.hexes.size()
                + " (" + lo + "–" + hi + ")"));
        }

        // --- старты игроков ---
        // СОСТАВ = ЧИСЛО СТАРТОВ. Отдельной настройки нет, поэтому проверяем
        // не «все ли места заняты», а «хватает ли игроков вообще».
        if (n < 2) {
            out.add(new Issue(2, "Стартов игроков " + n
                + " — раскладка не игровая, нужно минимум два"));
        } else if (n > MAX_SEATS) {
            // Инструмент столько не даст поставить, но такое поле могло прийти
            // из файла — тогда об этом надо сказать вслух.
            out.add(new Issue(2, "Стартов игроков " + n + " — больше " + MAX_SEATS
                + " мест в игре не бывает, лишние старты надо снять"));
        } else {
            out.add(new Issue(0, "Состав: " + n + " игрока(ов) — по числу стартов"));
        }

        // --- контейнеры (разметка печати) ---
        int containers = 0;
        int hiddenContainers = 0;
        for (LHex h : m.hexes.values()) {
            containers += h.containers;
            if (h.containers > 0 && (h.isSpawn() || !h.neutrals.isEmpty())) {
                hiddenContainers++;
            }
        }
        if (containers > 0) {
            out.add(new Issue(0, "Контейнеров нарисовано: " + containers
                + " (разметка печати; правила берут контейнеры не из файла поля)"));
        }
        if (hiddenContainers > 0) {
            out.add(new Issue(1, "Контейнеров под тайлом зарождения или нейтралом: "
                + hiddenContainers + " — на печатном поле такую ячейку не видно"));
        }

        // --- цельность карты ---
        int comps = components(m);
        if (comps > 1) {
            out.add(new Issue(2, "Карта развалилась на " + comps
                + " куска — должна быть одним целым"));
        } else {
            out.add(new Issue(0, "Карта цельная (одна компонента)"));
        }

        // --- наземная связность стартов ---
        // НАЗЕМНЫЕ ПУТИ: у КАЖДОГО игрока должен быть путь до КАЖДОГО другого
        // (правило дизайнера 12.08.2026). Путь идёт по стенкам гексов и рвётся
        // там, где стенку занимает нейтральное здание, — а также на тайлах
        // зарождения и запретных гексах. Это ПРЕДУПРЕЖДЕНИЕ (жёлтое), а не
        // ошибка: играть на такой карте можно, но она разрезана.
        if (starts.size() >= 2) {
            List<Integer> seats = new ArrayList<>(starts.keySet());
            java.util.Collections.sort(seats);
            boolean allConnected = true;
            for (int i = 0; i < seats.size(); i++) {
                for (int j = i + 1; j < seats.size(); j++) {
                    if (!groundConnected(m, starts.get(seats.get(i)), starts.get(seats.get(j)))) {
                        allConnected = false;
                        out.add(new Issue(1, "P" + (seats.get(i) + 1) + " и P" + (seats.get(j) + 1)
                            + " не связаны НАЗЕМНЫМ путём: дорогу перекрывают нейтралы, "
                            + "тайлы зарождения или запретные гексы"));
                    }
                }
            }
            if (allConnected) {
                out.add(new Issue(0, "У каждого игрока есть наземный путь до каждого другого"));
            }
        }

        // --- МАЛЫЕ ЗАРОЖДЕНИЯ: ничего не обязательно (решение дизайнера
        // 12.08.2026). Раскладка без малых зарождений у стартов — законна, и
        // раскладка вообще без малых зарождений (только большие) — тоже. Поэтому
        // здесь только ЛЁГКИЕ замечания, ошибок нет.
        if (n >= 2 && !m.hexes.isEmpty() && spawnStarts == 0) {
            out.add(new Issue(1, "Малых зарождений нет вовсе — законно: партия пойдёт "
                + "на одних больших, экономика стартует медленнее"));
        } else if (n >= 2 && spawnStarts != n && !m.hexes.isEmpty()) {
            out.add(new Issue(1, "Малых зарождений " + spawnStarts + ", а игроков " + n
                + " — не поровну (это допустимо, но старты неравны)"));
        }
        for (var e : starts.entrySet()) {
            LHex st = e.getValue();
            boolean adjStartSpawn = false;
            int neighbors = 0;
            int plainNeighbors = 0;   // обычные проходимые соседи (не тайл, не запрет)
            for (int[] d : DIRS) {
                LHex nb = m.get(st.q + d[0], st.r + d[1]);
                if (nb == null) {
                    continue;
                }
                neighbors++;
                if ("spawn_start".equals(nb.content)) {
                    adjStartSpawn = true;
                }
                if (!"forbidden".equals(nb.content) && !nb.isSpawn()) {
                    plainNeighbors++;
                }
            }
            String who = "P" + (e.getKey() + 1);
            if (!adjStartSpawn) {
                // ЛЁГКОЕ замечание, а не ошибка: дизайнер 12.08.2026 снял
                // требование соседнего малого зарождения — такие раскладки
                // осмысленны, старт просто разгоняется медленнее.
                out.add(new Issue(1, "У старта " + who
                    + " нет соседнего малого зарождения — допустимо, но добыча "
                    + "начнётся позже"));
            }
            // Порог свободных соседей у старта — ДВА (решение дизайнера
            // 12.08.2026; раньше требовалось три). Двух хватает, чтобы было
            // куда развернуться на первых ходах.
            if (plainNeighbors < 2) {
                out.add(new Issue(2, "У старта " + who + " только " + plainNeighbors
                    + " обычных соседних гексов (нужно ≥2: иначе негде разворачиваться)"));
            } else if (neighbors < 4) {
                out.add(new Issue(1, "У старта " + who + " всего " + neighbors
                    + " соседей — тесновато"));
            }
        }

        // --- большие зарождения ---
        int gLo = n < 2 ? 0 : (n + 1) / 2;
        if (n >= 2 && grids < gLo) {
            out.add(new Issue(1, "Мало больших зарождений: " + grids
                + " (ориентир " + gLo + "–" + n + "; двойные считаются за два)"));
        } else if (n >= 2 && grids > n + 1) {
            out.add(new Issue(1, "Много больших зарождений: " + grids
                + " (ориентир " + gLo + "–" + n + ") — келемий зальёт партию"));
        } else if (n >= 2) {
            out.add(new Issue(0, "Больших зарождений " + grids + " — в норме"));
        }

        // --- дистанция между стартами ---
        List<Integer> seatList = new ArrayList<>(starts.keySet());
        java.util.Collections.sort(seatList);
        int okDist = 0;
        int pairs = seatList.size() * (seatList.size() - 1) / 2;
        for (int i = 0; i < seatList.size(); i++) {
            for (int j = i + 1; j < seatList.size(); j++) {
                LHex a = starts.get(seatList.get(i));
                LHex b = starts.get(seatList.get(j));
                // ПРАВИЛО (дизайнер 12.08.2026): между стартами не меньше ТРЁХ
                // гексов расстояния, то есть минимум два гекса между ними.
                int dist = axialDist(a.q, a.r, b.q, b.r);
                if (dist < 3) {
                    out.add(new Issue(2, "Старты P" + (seatList.get(i) + 1) + " и P"
                        + (seatList.get(j) + 1) + " стоят вплотную: расстояние " + dist
                        + ", а нужно не меньше 3 (между ними два гекса)"));
                } else {
                    okDist++;
                }
            }
        }

        if (pairs > 0 && okDist == pairs) {
            out.add(new Issue(0, "Все старты разнесены на 3+ гекса"));
        }

        // --- финальная проверка НАСТОЯЩИМ загрузчиком симулятора ---
        try {
            Scenario.FieldWithStarts fw =
                Scenario.buildFieldFromScenario(toScenarioMap(m, "editor_check"));
            out.add(new Issue(0, "Загрузчик симулятора принял раскладку: "
                + fw.field().size() + " гексов, стартов " + fw.starts().size()));
        } catch (RuntimeException e) {
            out.add(new Issue(2, "Загрузчик симулятора НЕ принял раскладку: " + e.getMessage()));
        }
        return out;
    }

    private static int components(Model m) {
        Set<Long> seen = new HashSet<>();
        int comps = 0;
        for (LHex h : m.hexes.values()) {
            if (seen.contains(Model.key(h.q, h.r))) {
                continue;
            }
            comps++;
            Deque<LHex> stack = new ArrayDeque<>();
            stack.push(h);
            seen.add(Model.key(h.q, h.r));
            while (!stack.isEmpty()) {
                LHex cur = stack.pop();
                for (int[] d : DIRS) {
                    LHex nb = m.get(cur.q + d[0], cur.r + d[1]);
                    if (nb != null && seen.add(Model.key(nb.q, nb.r))) {
                        stack.push(nb);
                    }
                }
            }
        }
        return comps;
    }

    /**
     * Занята ли СТЕНКА {@code wall} гекса нейтральным зданием.
     * Ребро между углами c и c+1 — это сторона (2−c) mod 6 (та же формула,
     * что в движке, {@code Hex.NeutralBuilding.wallSides()}).
     */
    private static boolean wallBlocked(LHex h, int wall) {
        for (Neutral n : h.neutrals) {
            int walls = n.big ? 2 : 1;
            for (int i = 0; i < walls; i++) {
                int c = (n.corner - 1 + i) % 6 + 1;
                if (Math.floorMod(2 - c, 6) == wall) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Наземная связность двух гексов. Непроходимы тайлы зарождения и запретные
     * гексы (§4.1); кроме того, переход между соседями закрыт, если общую
     * стенку занимает нейтральное здание с любой из сторон.
     */
    private static boolean groundConnected(Model m, LHex a, LHex b) {
        java.util.function.Predicate<LHex> pass = h ->
            !"forbidden".equals(h.content) && !h.isSpawn();
        if (!pass.test(a) || !pass.test(b)) {
            return false;
        }
        Set<Long> seen = new HashSet<>();
        Deque<LHex> stack = new ArrayDeque<>();
        stack.push(a);
        seen.add(Model.key(a.q, a.r));
        while (!stack.isEmpty()) {
            LHex cur = stack.pop();
            if (cur == b) {
                return true;
            }
            for (int side = 0; side < DIRS.length; side++) {
                int[] d = DIRS[side];
                LHex nb = m.get(cur.q + d[0], cur.r + d[1]);
                if (nb == null || !pass.test(nb)) {
                    continue;
                }
                // общую стенку может закрывать нейтрал с ЛЮБОЙ из двух сторон
                if (wallBlocked(cur, side) || wallBlocked(nb, (side + 3) % 6)) {
                    continue;
                }
                if (seen.add(Model.key(nb.q, nb.r))) {
                    stack.push(nb);
                }
            }
        }
        return false;
    }

    /** Перенумеровать места стартов подряд с нуля, сохранив их порядок. */
    private static void renumberSeats() {
        List<LHex> starts = new ArrayList<>();
        for (LHex x : model.hexes.values()) {
            if ("player_start".equals(x.content)) {
                starts.add(x);
            }
        }
        starts.sort(java.util.Comparator.comparingInt(x -> x.seat));
        for (int i = 0; i < starts.size(); i++) {
            starts.get(i).seat = i;
        }
    }

    private static int axialDist(int q1, int r1, int q2, int r2) {
        int dq = q1 - q2;
        int dr = r1 - r2;
        return (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr)) / 2;
    }
}
