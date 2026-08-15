package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import kelium.dataio.GameConfig;
import kelium.report.ReplayRecord;

/**
 * ReplayGui — ПРОИГРЫВАТЕЛЬ ПАРТИЙ: показывает сыгранную партию ход за ходом,
 * как запись матча.
 *
 * <p>Сверху панель управления (параметры партии и «пульт»: играть, пауза, шаг
 * вперёд и назад, скорость, полоса перемотки), в середине поле и зоны игроков,
 * снизу лог ходов и мыслей ботов. Шаг = одно событие движка.
 *
 * <p>Движок играет партию целиком и синхронно, поэтому сначала партия
 * ЗАПИСЫВАЕТСЯ ({@link GameRecorder}), а потом проигрыватель листает готовые
 * кадры — отсюда и шаг назад, и перемотка, и сохранение записи в файл.
 *
 * <p>Запуск: {@code java -cp <classpath> kelium.gui.ReplayGui}.
 */
public final class ReplayGui {

    // ==================== состояние ====================
    private JFrame frame;
    private ReplayRecord record;
    private int cursor;                       // текущий шаг

    // параметры партии
    private final JComboBox<Integer> players = new JComboBox<>(new Integer[]{2, 3, 4});
    private final JTextField seed = new JTextField("777", 8);
    private final JComboBox<String> ruleset = new JComboBox<>();
    /** Раскладки, доступные для выбранного числа игроков (первый пункт — «по сиду»). */
    private final JComboBox<FieldOption> fieldBox = new JComboBox<>();
    @SuppressWarnings("unchecked")
    private final JComboBox<GameRecorder.SeatOption>[] seats = new JComboBox[4];
    /** Стартовый поворот ЦУ по местам: «авто» или грань 1..6. */
    @SuppressWarnings("unchecked")
    private final JComboBox<String>[] cuFacing = new JComboBox[4];
    private final JLabel[] seatLabels = new JLabel[4];
    private final JButton playGame = new JButton("Сыграть и показать");

    /**
     * Вариант раскладки в списке «поле». {@code file} не null — это поле из
     * библиотеки (нарисовано конструктором и лежит в своей папке).
     */
    /**
     * МЕШОК СЛУЧАЙНЫХ ПОЛЕЙ: перетасованные номера позиций списка. Кнопка
     * «случайно» достаёт из него по одному, и пока мешок не опустеет, поле не
     * повторится; опустел — тасуем заново.
     */
    private final List<Integer> fieldBag = new ArrayList<>();
    private final java.util.Random fieldRng = new java.util.Random();

    /** Выбрать случайную раскладку из мешка (см. {@link #fieldBag}). */
    private void pickRandomField() {
        int count = fieldBox.getItemCount();
        if (count <= 1) {
            say("Выбирать не из чего: в списке только «любая (по сиду)».");
            return;
        }
        if (fieldBag.isEmpty()) {
            for (int i = 1; i < count; i++) {     // 0 — «любая (по сиду)», её пропускаем
                fieldBag.add(i);
            }
            java.util.Collections.shuffle(fieldBag, fieldRng);
        }
        int idx = fieldBag.remove(fieldBag.size() - 1);
        if (idx >= count) {
            fieldBag.clear();                     // список успел поменяться
            pickRandomField();
            return;
        }
        fieldBox.setSelectedIndex(idx);
        FieldOption fo = fieldBox.getItemAt(idx);
        say("Случайная раскладка: " + (fo == null ? "?" : fo.label())
            + "   (в мешке осталось " + fieldBag.size() + ")");
    }

    private record FieldOption(String id, String label, Path file) {
        FieldOption(String id, String label) {
            this(id, label, null);
        }

        @Override public String toString() {
            return label;
        }
    }

    // пульт
    // Подписи только треугольниками и словами: экзотические значки в системном
    // шрифте Windows рисуются пустыми квадратами.
    private final JButton toStart = new JButton("|◀ в начало");
    private final JButton stepBack = new JButton("◀ шаг");
    private final JButton playPause = new JButton("▶ играть");
    private final JButton stepFwd = new JButton("шаг ▶");
    private final JButton toEnd = new JButton("в конец ▶|");
    private final JButton prevRound = new JButton("◀ раунд");
    private final JButton nextRound = new JButton("раунд ▶");
    private final JButton prevBattle = new JButton("◀ бой");
    private final JButton nextBattle = new JButton("бой ▶");
    private final JComboBox<String> speed = new JComboBox<>(new String[]{
        "0,25×", "0,5×", "1×", "2×", "4×", "8×", "как можно быстрее"});
    private final JSlider scrub = new JSlider(0, 0, 0);
    private final JLabel position = new JLabel(" ");
    private final JLabel status = new JLabel(" ");

    // вид
    private final FieldView field = new FieldView();
    private final BoardsPanel boards = new BoardsPanel();
    private final SuperObjectivesPanel supers = new SuperObjectivesPanel();
    private JTabbedPane tabs;
    // ПУЛЬТ ВОСПРОИЗВЕДЕНИЯ: рамка снизу и её части — нужны, чтобы переключать
    // обычный вид (две строки) и компактный (одна полоса).
    private Collapsible transport;
    private JPanel transportStack;
    private JPanel transportButtons;
    private JPanel transportScrubLine;
    private JPanel[] transportGroups;
    // РАМКИ И РАЗДЕЛИТЕЛИ ЭКРАНА держим в поле: разделители надо расставлять после
    // появления окна (до этого его размер нулевой), а рамки — сворачивать по
    // требованию («только поле», сворачивание настроек после старта партии).
    private Collapsible settingsBox;
    private Collapsible logBox;
    private Collapsible zonesBox;
    private JSplitPane logSplit;
    private JSplitPane centreSplit;
    private JSplitPane leftSplit;
    private JSplitPane rightSplit;
    /** На сколько игроков собрана раскладка сейчас; −1 — ещё не собрана. */
    private int laidOutFor = -1;

    /** Сколько отдаём логу, зонам и боковой колонке, когда окно только открылось. */
    private static final int LOG_H = 148;
    private static final int ZONES_H = 168;
    private static final int SIDE_W = 250;
    /** Меньше этого поле не сжимаем — сначала уступают лог и зоны. */
    private static final int FIELD_MIN_H = 340;

    private final PlayerZone[] zones = new PlayerZone[4];
    /**
     * Полоски РАЗЫГРАННЫХ ПРИКАЗОВ — по одной на игрока, вплотную к его зоне
     * статистики. Свежая карта впереди, прежние отодвинуты; видно, что сыграно,
     * что осталось, сработало ли совпадение и открылась ли нижняя половина.
     */
    private final OrderStrip[] strips = new OrderStrip[4];
    private final JPanel board = new JPanel(new BorderLayout(6, 6));

    // лог
    private final DefaultListModel<LogEntry> logModel = new DefaultListModel<>();
    private final JList<LogEntry> logList = new JList<>(logModel);
    private final JCheckBox onlyBattles = new JCheckBox("только бои");
    private final JCheckBox onlyThoughts = new JCheckBox("только мысли");
    private final JCheckBox onlyMine = new JCheckBox("только игрок:");
    private final JComboBox<String> mineSeat = new JComboBox<>();
    private boolean syncingSelection;
    /** Строка лога, выбранная кликом: её нельзя перебивать автоподсветкой. */
    private int keepLogRow = -1;
    private final JCheckBox pauseOnBattle = new JCheckBox("пауза на боях");
    /**
     * СИД РАСКЛАДКИ БЛОКОВ — отдельный от сида партии. Поле собирается из
     * картонных блоков с напечатанными контейнерами, и за столом их каждый раз
     * кладут иначе. Кнопка «другая сборка» крутит только этот сид: поле,
     * колоды и боты остаются те же, меняется расположение контейнеров.
     * null — брать общий сид партии.
     */
    private Long blockSeed;
    private final JButton reshuffleBlocks = new JButton("🎲 другая сборка");
    /** Пока true — форму заполняем мы сами, перестраивать расстановку не надо. */
    private boolean suppressPreview;

    private final Timer ticker = new Timer(700, e -> tick());
    private Path lastFile;

    // ==================== запуск ====================
    /**
     * Точка входа. Необязательный аргумент — путь к сохранённой записи партии:
     * она откроется сразу (удобно, если связать .json с приложением).
     */
    public static void main(String[] args) {
        Ui.init();
        Path preload = args.length > 0 && !args[0].isBlank() ? Paths.get(args[0]) : null;
        SwingUtilities.invokeLater(() -> {
            ReplayGui gui = new ReplayGui();
            gui.show();
            if (preload != null) {
                gui.openFile(preload);
            }
        });
    }

    private void show() {
        frame = new JFrame("Кристаллы Раздора — проигрыватель партий");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setJMenuBar(menuBar());
        frame.setLayout(new BorderLayout(6, 6));

        // НАСТРОЙКА ПАРТИИ — сверху, ПУЛЬТ ВОСПРОИЗВЕДЕНИЯ — снизу, отдельными
        // панелями (просьба дизайнера 12.08.2026): настройки трогают один раз
        // перед партией, а пульт нужен всё время и место ему под полем, как у
        // любого плеера. Обе панели сворачиваются до полоски.
        settingsBox = new Collapsible("настройка партии", paramsRow());
        frame.add(settingsBox, BorderLayout.NORTH);

        logBox = new Collapsible("лог партии: ходы и мысли ботов", logPanel());
        // Вес 1.0 — ВЕСЬ прирост окна достаётся полю и зонам, а не логу: раньше лог
        // рос вместе с окном, и поле оставалось полоской.
        logSplit = splitter(JSplitPane.VERTICAL_SPLIT, board, logBox, 1.0);
        logBox.bindTo(logSplit, false);
        frame.add(logSplit, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        transport = new Collapsible("показ", transportRow());
        // ВТОРАЯ, «ЧУТЬ-ЧУТЬ» степень сворачивания: пульт сжимается в одну
        // полосу — кнопки мельче, ползунок и счётчик шага рядом с ними.
        transport.addExtraToggle("сжать", "развернуть", "уместить пульт в одну полосу",
            this::setTransportCompact);
        bottom.add(transport, BorderLayout.NORTH);
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        status.setForeground(new Color(0x44, 0x44, 0x44));
        bottom.add(status, BorderLayout.SOUTH);
        frame.add(bottom, BorderLayout.SOUTH);

        for (int i = 0; i < 4; i++) {
            zones[i] = new PlayerZone(i);
            strips[i] = new OrderStrip(i);
        }
        layoutBoard(4);
        wire();
        updateControls();

        // РАЗМЕР ОКНА по месту на экране, а не «1360×900 всегда»: на ноутбуке
        // 1366×768 прежнее окно было выше экрана и пульт уезжал под панель задач.
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment
            .getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(1460, screen.width - 40), Math.min(960, screen.height - 40));
        // Честный минимум: приложению нужно место. Прежние 900×620 были обещанием,
        // которое интерфейс не выполнял — на таком окне поля не было видно вовсе.
        frame.setMinimumSize(new Dimension(1100, 700));
        frame.setLocationRelativeTo(null);
        // Окно потянули за угол — следим, чтобы поле не сжали в полоску.
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                clampFieldSize();
            }
        });
        frame.setVisible(true);
        // Разделители расставляем ПОСЛЕ появления окна: до этого высота и ширина
        // ещё нулевые, и любые доли (0.62, 0.80) считаются от пустоты.
        SwingUtilities.invokeLater(this::applyLayoutSizes);
        // Сразу показываем стартовую расстановку с параметрами по умолчанию —
        // окно не встречает пустотой, и видно, что меняют настройки.
        refreshPreview();
        say("Показана стартовая расстановка. Меняй настройки — поле обновляется "
            + "сразу; нажми «Сыграть и показать», чтобы увидеть партию.");
    }

    // ==================== меню ====================
    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Файл");
        file.add(item("Открыть запись…", "Ctrl+O", this::openRecord));
        file.add(item("Сохранить запись…", "Ctrl+S", this::saveRecord));
        file.addSeparator();
        file.add(item("Сохранить текущий кадр картинкой…", null, this::saveShot));
        file.add(item("Переиграть эту партию заново", null, this::startGame));
        file.addSeparator();
        file.add(item("Выход", null, () -> frame.dispose()));
        bar.add(file);

        JMenu view = new JMenu("Вид");
        view.add(item("Крупнее", "Ctrl+=", () -> field.zoomBy(1.2)));
        view.add(item("Мельче", "Ctrl+-", () -> field.zoomBy(1 / 1.2)));
        view.add(item("Вписать поле в окно", "Ctrl+0", field::fitToWindow));
        view.addSeparator();
        // ПОЛЕ — ГЛАВНОЕ. Одним нажатием убираем всё остальное в полоски.
        view.add(item("Только поле", "F11",
            () -> showFieldOnly(settingsBox != null && settingsBox.isOpen())));
        view.add(item("Вернуть панели на место", null, () -> {
            showFieldOnly(false);
            applyLayoutSizes();
        }));
        view.addSeparator();
        // ЧТО ПОКАЗЫВАТЬ НА ПОЛЕ — только второстепенные пометки: цифры,
        // подписи, подкраска. Сами жетоны, тайлы и контейнеры не выключаются:
        // без них картинка перестаёт быть правдой (просьба дизайнера 12.08.2026).
        JMenu layers = new JMenu("Что показывать на поле");
        layers.add(check("Координаты гексов", true, field::setShowIds));
        layers.add(check("Подсветку последнего действия", true, field::setShowHighlights));
        layers.add(check("Подпись «чей ход»", true, field::setShowTurnCaption));
        layers.addSeparator();
        layers.add(check("Кубики урона на жетонах", true, field::setShowDamage));
        layers.add(check("Остаток келемия на тайлах", true, field::setShowKelium));
        layers.add(check("Ячейки и кубики энергии на жетонах", true, field::setShowEnergy));
        layers.add(check("Подкраску гексов и зон стройки", true, field::setShowOwnership));
        view.add(layers);
        bar.add(view);

        JMenu settings = new JMenu("Настройки");
        settings.add(item("Папки с раскладками…", null, this::editLibrary));
        bar.add(settings);

        JMenu help = new JMenu("Справка");
        help.add(item("Как пользоваться", "F1", this::showHelp));
        help.add(item("О приложении", null, this::showAbout));
        bar.add(help);
        return bar;
    }

    private JMenuItem item(String text, String accel, Runnable action) {
        JMenuItem mi = new JMenuItem(text);
        if (accel != null) {
            mi.setAccelerator(KeyStroke.getKeyStroke(accel.replace("Ctrl+", "control ")
                .replace("=", "EQUALS").replace("-", "MINUS")));
        }
        mi.addActionListener(e -> action.run());
        return mi;
    }

    private JCheckBoxMenuItem check(String text, boolean on,
                                    java.util.function.Consumer<Boolean> action) {
        JCheckBoxMenuItem mi = new JCheckBoxMenuItem(text, on);
        mi.addActionListener(e -> action.accept(mi.isSelected()));
        return mi;
    }

    // ==================== верхняя панель ====================
    /**
     * Строка кнопок, которая ЧЕСТНО сообщает свою высоту после переноса.
     *
     * <p>Обычный {@link FlowLayout} внутри {@link BoxLayout} — известная ловушка:
     * при узком окне он переносит элементы на вторую строку, но высоту панели
     * по-прежнему считает по одной строке, и нижний ряд просто обрезается
     * (жалоба дизайнера «целая линия не въезжает»). Здесь высота считается по
     * фактической ширине.
     */
    private static final class WrapRow extends JPanel {
        private static final long serialVersionUID = 1L;
        private int lastHeight = -1;

        WrapRow(int hgap, int vgap) {
            super(new FlowLayout(FlowLayout.LEFT, hgap, vgap));
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    int h = getPreferredSize().height;
                    if (h != lastHeight) {
                        lastHeight = h;
                        revalidate();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getWidth();
            if (width <= 0) {
                return super.getPreferredSize();
            }
            FlowLayout fl = (FlowLayout) getLayout();
            java.awt.Insets in = getInsets();
            int avail = Math.max(1, width - in.left - in.right);
            int x = 0;
            int rowH = 0;
            int total = in.top + in.bottom + fl.getVgap();
            for (Component c : getComponents()) {
                if (!c.isVisible()) {
                    continue;
                }
                Dimension d = c.getPreferredSize();
                if (x > 0 && x + d.width > avail) {
                    total += rowH + fl.getVgap();
                    x = 0;
                    rowH = 0;
                }
                x += d.width + fl.getHgap();
                rowH = Math.max(rowH, d.height);
            }
            return new Dimension(avail, total + rowH + fl.getVgap());
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * НАСТРОЙКА ПАРТИИ — форма, а не поток кнопок.
     *
     * <p>Была {@code WrapRow} с переносом строки, и на окне 1360×900 (а это
     * размер по умолчанию!) вторая строка сжималась в полоску 6 пикселей: главная
     * кнопка «Сыграть и показать» становилась невидимой и ненажимаемой, а обрезки
     * наезжали на рамки «Место N». Причина неизлечима подпорками: {@code FlowLayout}
     * с переносом сообщает высоту, зависящую от ширины, а {@code BorderLayout.NORTH}
     * спрашивает высоту раньше, чем ширина известна.
     *
     * <p>Теперь: {@link java.awt.GridBagLayout} с ДВУМЯ ЯВНЫМИ строками, ничего
     * никуда не переносится, а главная кнопка стоит отдельной колонкой справа —
     * она физически не может уехать. Кнопки «случайный сид», «случайное поле» и
     * «другая сборка» стали значками (рисованными, не символами шрифта): освободили
     * ~250 пикселей ширины, и строка целиком влезает даже в окно 1000 точек.
     */
    private JPanel paramsRow() {
        JPanel outer = new JPanel(new BorderLayout(10, 0));
        // Рамки с подписью «Партия» здесь больше нет: полоска Collapsible над ней
        // уже говорит «настройка партии» — два заголовка об одном и том же.
        outer.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridy = 0;
        c.gridx = 0;
        c.insets = new java.awt.Insets(0, 0, 4, 6);
        c.anchor = java.awt.GridBagConstraints.WEST;

        // ---- первая строка: стол и поле ----
        players.setSelectedItem(4);
        players.setToolTipText(Ui.text("Сколько игроков за столом. Список раскладок "
            + "справа сразу перестроится под это число — доступны только авторские "
            + "поля на столько игроков."));
        cell(form, c, big(new JLabel("игроков:")), 0);
        cell(form, c, big(players), 0);

        seed.setToolTipText(Ui.text("Зерно случайности. Одинаковый сид, одинаковая "
            + "раскладка и одинаковый состав ботов дают ровно ту же партию."));
        cell(form, c, big(new JLabel("сид:")), 0);
        // Поле сида держим своей ширины: сетка иначе сжимает именно его (у текстового
        // поля самый маленький допустимый размер), и от сида остаётся щель.
        Dimension seedSize = new Dimension(76, big(seed).getPreferredSize().height);
        seed.setPreferredSize(seedSize);
        seed.setMinimumSize(seedSize);
        cell(form, c, seed, 0);
        cell(form, c, iconButton("DICE", "Взять случайный сид.",
            () -> seed.setText(String.valueOf(Math.abs(new Random().nextInt(1_000_000))))), 0);

        for (String r : GameConfig.availableRulesets(null)) {
            ruleset.addItem(r);
        }
        if (ruleset.getItemCount() == 0) {
            ruleset.addItem(GameConfig.DEFAULT_RULESET);
        }
        ruleset.setSelectedItem(GameConfig.DEFAULT_RULESET);
        ruleset.setToolTipText(Ui.text("Версия правил: по ней берутся значения и карты."));
        cell(form, c, big(new JLabel("правила:")), 0);
        cell(form, c, big(ruleset), 0);

        fieldBox.setToolTipText(Ui.text("Раскладка поля. Список — только авторские "
            + "варианты на выбранное число игроков; «любая (по сиду)» оставляет выбор "
            + "движку, как в обычном прогоне."));
        cell(form, c, big(new JLabel("поле:")), 0);
        // Список поля — единственный, кто тянется: он забирает весь запас ширины и
        // он же первым уступает, когда окно узкое.
        fieldBox.setMinimumSize(new Dimension(120, fieldBox.getPreferredSize().height));
        cell(form, c, big(fieldBox), 1);

        // СЛУЧАЙНОЕ ПОЛЕ ИЗ МЕШКА (просьба дизайнера 12.08.2026): раскладки идут
        // перетасованным списком и не повторяются, пока список не кончится —
        // тогда он тасуется заново. Простой rnd.nextInt выдавал одно и то же
        // поле два-три раза подряд.
        cell(form, c, iconButton("SHUFFLE", "Случайная раскладка из списка. Идём "
            + "перетасованным мешком: пока все поля не покажутся, повторов не будет.",
            this::pickRandomField), 0);

        // ДРУГАЯ СБОРКА БЛОКОВ: поле, колоды и боты те же — меняется только то,
        // какими блоками и какой стороной выложено поле, а значит и где лежат
        // ПЕЧАТНЫЕ контейнеры (просьба дизайнера 12.08.2026).
        // Значок вместо «🎲»: цветные эмодзи Java2D не рисует, в системном шрифте
        // Windows на их месте пустой квадрат или чужой глиф.
        reshuffleBlocks.setText("другая сборка");
        reshuffleBlocks.setIcon(ToolIcons.of("BLOCKS"));
        reshuffleBlocks.setFocusable(false);
        reshuffleBlocks.setToolTipText(Ui.text("Разложить поле ДРУГИМИ блоками: "
            + "контейнеры окажутся на других ячейках. Сид партии, раскладка гексов "
            + "и боты не меняются — только то, как поле собрано из картона."));
        reshuffleBlocks.addActionListener(e -> {
            blockSeed = (long) Math.abs(new Random().nextInt(1_000_000));
            refreshPreview();
            say("Поле собрано другими блоками (сборка " + blockSeed
                + "). Контейнеры переехали; сама партия та же.");
        });
        cell(form, c, big(reshuffleBlocks), 0);
        reloadFields();

        // ---- вторая строка: кто на местах и поворот ЦУ ----
        JPanel q = new JPanel(new GridLayout(1, 4, 10, 0));
        for (int i = 0; i < 4; i++) {
            seats[i] = new JComboBox<>();
            reloadSeatOptions(seats[i], i);
            final int seat = i;
            seats[i].addActionListener(e -> {
                GameRecorder.SeatOption o =
                    (GameRecorder.SeatOption) seats[seat].getSelectedItem();
                seats[seat].setToolTipText(o == null ? null : Ui.text(o.tip()));
            });

            cuFacing[i] = new JComboBox<>(new String[]{"авто", "1", "2", "3", "4", "5", "6"});
            cuFacing[i].setToolTipText(Ui.text("Стартовый ПОВОРОТ ЦУ этого места: с какой "
                + "грани гекса стоит центр управления (он занимает эту грань и следующую "
                + "по часовой). От поворота зависит, какие стенки закрыты и куда открыт "
                + "выход. «Авто» — как ставит движок сам."));

            JPanel seatCell = new JPanel(new BorderLayout(6, 0));
            seatCell.setBorder(BorderFactory.createTitledBorder(null, "Место " + (i + 1),
                javax.swing.border.TitledBorder.LEADING,
                javax.swing.border.TitledBorder.TOP, bold(12f),
                FieldView.seatStroke(i)));
            seatCell.add(big(seats[i]), BorderLayout.CENTER);
            JPanel cu = new JPanel(new BorderLayout(4, 0));
            cu.setOpaque(false);
            seatLabels[i] = big(new JLabel("ЦУ гранью"));
            cu.add(seatLabels[i], BorderLayout.WEST);
            cu.add(big(cuFacing[i]), BorderLayout.CENTER);
            seatCell.add(cu, BorderLayout.EAST);
            q.add(seatCell);
        }
        // Вторая строка формы — во всю ширину первой.
        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        c.weightx = 1;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        form.add(q, c);

        outer.add(form, BorderLayout.CENTER);

        // ГЛАВНАЯ КНОПКА — отдельной колонкой справа, во всю высоту панели. Она не
        // участвует в переносе строк и потому не может исчезнуть на узком окне.
        playGame.setToolTipText(Ui.text("Сыграть партию с этими параметрами и показать её "
            + "по шагам. Партия играется целиком и записывается — потом её можно листать "
            + "вперёд и назад."));
        playGame.setFont(bold(15f));
        playGame.setIcon(TransportIcons.of("PLAY", 18));
        playGame.setMargin(new java.awt.Insets(6, 14, 6, 14));
        JPanel cta = new JPanel(new BorderLayout());
        cta.setOpaque(false);
        cta.add(playGame, BorderLayout.CENTER);
        outer.add(cta, BorderLayout.EAST);
        return outer;
    }

    /** Ячейка формы настроек: ставит компонент и переходит к следующей колонке. */
    private static void cell(JPanel form, java.awt.GridBagConstraints c,
                            Component comp, double weight) {
        c.weightx = weight;
        c.fill = weight > 0 ? java.awt.GridBagConstraints.HORIZONTAL
                            : java.awt.GridBagConstraints.NONE;
        form.add(comp, c);
        c.gridx++;
    }

    /**
     * Маленькая кнопка-значок для формы настроек: рисованная пиктограмма и
     * подсказка словами. Текстом такие кнопки съедали четверть строки.
     */
    private JButton iconButton(String code, String tip, Runnable action) {
        JButton b = new JButton(TransportIcons.of(code, 18));
        b.setToolTipText(Ui.text(tip));
        b.setFocusable(false);
        b.setMargin(new java.awt.Insets(2, 5, 2, 5));
        b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * Заполнить список ботов для места. Нейросети появляются в списке, только
     * если их файл реально лежит на диске (выбрать несуществующее нельзя).
     */
    private void reloadSeatOptions(JComboBox<GameRecorder.SeatOption> box, int seat) {
        int n = players.getSelectedItem() instanceof Integer i ? i : 4;
        Object keep = box.getSelectedItem();
        box.removeAllItems();
        List<GameRecorder.SeatOption> options = GameRecorder.seatOptions(n);
        for (GameRecorder.SeatOption o : options) {
            box.addItem(o);
        }
        int pick = Math.min(seat, options.size() - 1);
        if (keep instanceof GameRecorder.SeatOption prev) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).id().equals(prev.id())) {
                    pick = i;
                    break;
                }
            }
        }
        box.setSelectedIndex(Math.max(0, pick));
        GameRecorder.SeatOption o = (GameRecorder.SeatOption) box.getSelectedItem();
        box.setToolTipText(o == null ? null : Ui.text(o.tip()));
    }

    /** Перечитать список раскладок под выбранное число игроков. */
    private void reloadFields() {
        int n = players.getSelectedItem() instanceof Integer i ? i : 4;
        String version;
        try {
            version = GameConfig.buildCached(String.valueOf(ruleset.getSelectedItem()),
                n, 0L, null, null).ruleset.getStr("content_versions.scenarios", "1.0.0");
        } catch (RuntimeException e) {
            version = "1.0.0";
        }
        Object keep = fieldBox.getSelectedItem();
        fieldBag.clear();          // список полей меняется — мешок собираем заново
        fieldBox.removeAllItems();
        fieldBox.addItem(new FieldOption(null, "любая (по сиду)"));
        java.util.Set<String> authors = new java.util.LinkedHashSet<>();
        try {
            for (java.util.Map<String, Object> v : kelium.engine.Scenario.loadAllVariants(
                    n, version, GameConfig.resolveDataRoot(null))) {
                String id = String.valueOf(v.get("id"));
                authors.add(id);
                fieldBox.addItem(new FieldOption(id, "авторская · " + id));
            }
        } catch (RuntimeException e) {
            say("Список авторских раскладок не прочитан: " + human(e));
        }
        // Плюс всё, что нарисовано конструктором и лежит в папках библиотеки.
        List<String> problems = new ArrayList<>();
        for (kelium.engine.LayoutLibrary.Entry e : kelium.engine.LayoutLibrary.scan(n, problems)) {
            if (authors.contains(e.id())) {
                continue;                     // авторская уже в списке
            }
            fieldBox.addItem(new FieldOption(e.id(),
                "своя · " + e.id() + "  (" + e.file().getFileName() + ")", e.file()));
        }
        if (!problems.isEmpty()) {
            say("Часть файлов в папках раскладок не прочитана: "
                + String.join("; ", problems));
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

    private static Font bold(float size) {
        return new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size));
    }

    /** Увеличить шрифт элемента управления — мелкий текст дизайнеру не читается. */
    private static <T extends javax.swing.JComponent> T big(T c) {
        c.setFont(c.getFont().deriveFont(Font.PLAIN, 13.5f));
        return c;
    }

    /**
     * Кнопка-значок пульта: одинаковый компактный размер, без рамки фокуса.
     * Фокус кнопкам не нужен — иначе системная тема перехватывает ПРОБЕЛ на
     * последнюю нажатую кнопку, и «играть/пауза» работает через раз.
     */
    private static void icon(JButton b, int w, int h, float font) {
        b.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) font));
        b.setMargin(new java.awt.Insets(0, 0, 0, 0));
        b.setPreferredSize(new Dimension(w, h));
        b.setMinimumSize(new Dimension(w, h));
        b.setFocusable(false);
        b.setFocusPainted(false);
    }

    private JPanel transportRow() {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        // Рамка с подписью «Показ» убрана: полоска Collapsible над ней говорит то
        // же слово, а высота нужна полю.
        p.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));

        JPanel buttons = new WrapRow(6, 4);
        toStart.setToolTipText(Ui.text("В самое начало партии (Home)."));
        stepBack.setToolTipText(Ui.text("На одно действие назад: приказ, стройка, "
            + "удар в бою, розыгрыш задания, конец раунда (стрелка ←)."));
        playPause.setToolTipText(Ui.text("Играть или пауза (пробел)."));
        stepFwd.setToolTipText(Ui.text("На одно действие вперёд: приказ, стройка, "
            + "удар в бою, розыгрыш задания, конец раунда (стрелка →)."));
        toEnd.setToolTipText(Ui.text("В конец партии (End)."));
        prevRound.setToolTipText(Ui.text("К началу предыдущего раунда (Page Up)."));
        nextRound.setToolTipText(Ui.text("К началу следующего раунда (Page Down)."));
        prevBattle.setToolTipText(Ui.text("К ближайшему бою ДО текущего шага "
            + "(Shift+B)."));
        nextBattle.setToolTipText(Ui.text("К ближайшему бою ПОСЛЕ текущего шага (клавиша B)."));
        speed.setSelectedIndex(2);
        speed.setToolTipText(Ui.text("Скорость показа. «Как можно быстрее» — партия "
            + "прокручивается без пауз, но ни один шаг не пропускается."));
        // ПУЛЬТ КАК В ПЛЕЕРЕ, а не строка кнопок с подписями. Значки РИСОВАННЫЕ
        // (TransportIcons), а не символы шрифта: «⚔» в системном шрифте Windows
        // подменялся чужим глифом и кнопка «к бою» читалась как «X◀», а «играть» и
        // «шаг вперёд» были одним и тем же треугольником — два самых частых
        // действия неразличимы. Теперь у шага есть стойка, у раундов свой синий
        // цвет, у боёв — красная вспышка.
        setTransportIcons(18);

        JPanel mainGroup = new JPanel(new java.awt.GridLayout(1, 5, 1, 0));
        mainGroup.setOpaque(false);
        for (JButton b : new JButton[]{toStart, stepBack, playPause, stepFwd, toEnd}) {
            icon(b, 32, 28, 14f);
            mainGroup.add(b);
        }
        mainGroup.setMaximumSize(new Dimension(170, 30));
        buttons.add(mainGroup);

        buttons.add(Box.createHorizontalStrut(10));
        speed.setFocusable(false);
        speed.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        speed.setPreferredSize(new Dimension(112, 26));
        buttons.add(speed);

        buttons.add(Box.createHorizontalStrut(10));
        JPanel jumps = new JPanel(new java.awt.GridLayout(1, 4, 1, 0));
        jumps.setOpaque(false);
        for (JButton b : new JButton[]{prevRound, nextRound, prevBattle, nextBattle}) {
            icon(b, 32, 28, 12f);
            jumps.add(b);
        }
        jumps.setMaximumSize(new Dimension(136, 30));
        buttons.add(jumps);

        buttons.add(Box.createHorizontalStrut(10));
        pauseOnBattle.setToolTipText(Ui.text("Останавливать показ на каждом ударе в бою "
        + "— удобно разбирать, почему бот полез в драку."));
        pauseOnBattle.setFocusable(false);
        pauseOnBattle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        buttons.add(pauseOnBattle);

        // Ползунок перемотки — ОТДЕЛЬНОЙ строкой во всю ширину: так его удобно
        // тянуть, и кнопкам не приходится тесниться.
        JPanel line = new JPanel(new BorderLayout(10, 0));
        line.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        scrub.setToolTipText(Ui.text("Перемотка: тяни ползунок в любую точку партии."));
        line.add(scrub, BorderLayout.CENTER);
        position.setFont(bold(13f));
        position.setToolTipText(Ui.text("Номер текущего шага и всего шагов в партии."));
        line.add(position, BorderLayout.EAST);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(buttons);
        stack.add(line);
        p.add(stack, BorderLayout.CENTER);
        // запомним части пульта: компактный вид складывает их в одну полосу
        transportButtons = buttons;
        transportScrubLine = line;
        transportStack = stack;
        transportGroups = new JPanel[]{mainGroup, jumps};
        return p;
    }

    /**
     * КОМПАКТНЫЙ ПУЛЬТ — «свернуть чуть-чуть» (просьба дизайнера 12.08.2026).
     *
     * <p>Обычный вид: две строки — кнопки, под ними ползунок во всю ширину.
     * Компактный: одна полоса, кнопки мельче, ползунок и счётчик шага стоят
     * справа от кнопок. Полностью пульт по-прежнему сворачивается своей кнопкой
     * в полоску заголовка.
     */
    private void setTransportCompact(boolean compact) {
        if (transportStack == null) {
            return;
        }
        int side = compact ? 24 : 32;
        int high = compact ? 22 : 28;
        float font = compact ? 11f : 14f;
        for (JButton b : new JButton[]{toStart, stepBack, playPause, stepFwd, toEnd}) {
            icon(b, side, high, font);
        }
        for (JButton b : new JButton[]{prevRound, nextRound, prevBattle, nextBattle}) {
            icon(b, side, high, compact ? 10f : 12f);
        }
        setTransportIcons(compact ? 15 : 18);
        speed.setPreferredSize(new Dimension(compact ? 84 : 112, high));
        pauseOnBattle.setVisible(!compact);
        if (transportGroups != null) {
            transportGroups[0].setMaximumSize(new Dimension(side * 5 + 8, high + 2));
            transportGroups[1].setMaximumSize(new Dimension(side * 4 + 8, high + 2));
        }

        transportStack.removeAll();
        if (compact) {
            JPanel one = new JPanel(new BorderLayout(8, 0));
            one.setOpaque(false);
            one.add(transportButtons, BorderLayout.WEST);
            one.add(transportScrubLine, BorderLayout.CENTER);
            one.setAlignmentX(Component.LEFT_ALIGNMENT);
            transportStack.add(one);
        } else {
            transportStack.add(transportButtons);
            transportStack.add(transportScrubLine);
        }
        transportStack.revalidate();
        transportStack.repaint();
    }

    /** Размер значков пульта. Подписей у кнопок нет — только рисунок и подсказка. */
    private int iconPx = 18;

    private void setTransportIcons(int px) {
        iconPx = px;
        toStart.setText(null);
        stepBack.setText(null);
        playPause.setText(null);
        stepFwd.setText(null);
        toEnd.setText(null);
        prevRound.setText(null);
        nextRound.setText(null);
        prevBattle.setText(null);
        nextBattle.setText(null);
        toStart.setIcon(TransportIcons.of("TO_START", px));
        stepBack.setIcon(TransportIcons.of("STEP_BACK", px));
        stepFwd.setIcon(TransportIcons.of("STEP_FWD", px));
        toEnd.setIcon(TransportIcons.of("TO_END", px));
        prevRound.setIcon(TransportIcons.of("ROUND_PREV", px));
        nextRound.setIcon(TransportIcons.of("ROUND_NEXT", px));
        prevBattle.setIcon(TransportIcons.of("BATTLE_PREV", px));
        nextBattle.setIcon(TransportIcons.of("BATTLE_NEXT", px));
        // «Играть» крупнее остальных: главная кнопка пульта.
        playPause.setIcon(TransportIcons.of(playPauseCode(), px + 4));
    }

    /** Какой значок сейчас у главной кнопки: играть, пауза или «показать сначала». */
    private String playPauseCode() {
        if (ticker.isRunning()) {
            return "PAUSE";
        }
        boolean atEnd = record != null && record.frames.size() > 1
            && cursor >= record.frames.size() - 1;
        return atEnd ? "REPLAY" : "PLAY";
    }

    // ==================== лог ====================
    private JPanel logPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        // Заголовок уже написан на полоске Collapsible — второй раз не повторяем.
        p.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JPanel filters = new WrapRow(8, 2);
        onlyBattles.setToolTipText(Ui.text("Оставить в логе только строки боя."));
        onlyThoughts.setToolTipText(Ui.text("Оставить только мысли ботов "
            + "(эвристики и случайный бот мыслей не озвучивают)."));
        onlyMine.setToolTipText(Ui.text("Оставить только строки выбранного игрока."));
        mineSeat.setToolTipText(Ui.text("Чьи строки показывать."));
        for (JCheckBox c : new JCheckBox[]{onlyBattles, onlyThoughts, onlyMine}) {
            c.setFocusable(false);
            filters.add(big(c));
        }
        mineSeat.setFocusable(false);
        filters.add(big(mineSeat));
        JLabel hint = new JLabel("клик по строке — перемотка к этому моменту");
        hint.setForeground(new Color(0x77, 0x77, 0x77));
        filters.add(hint);
        p.add(filters, BorderLayout.NORTH);

        logList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logList.setCellRenderer(new LogRenderer());
        logList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        // Высота строки задана заранее: иначе JList обмеряет каждую из полутора
        // тысяч строк при любом обновлении и окно подвисает.
        logList.setFixedCellHeight(18);
        JScrollPane sc = new JScrollPane(logList);
        sc.getVerticalScrollBar().setUnitIncrement(16);
        p.add(sc, BorderLayout.CENTER);
        p.setPreferredSize(new Dimension(1200, LOG_H));
        p.setMinimumSize(new Dimension(400, 92));
        return p;
    }

    /** Строка лога: событие или мысль бота. */
    private record LogEntry(int frameIndex, int seat, String text, boolean thought,
                            boolean combat) {
    }

    private final class LogRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            LogEntry e = (LogEntry) value;
            setText((e.thought() ? "        · " : "") + e.text());
            Color fg = e.seat() >= 0 ? FieldView.seatStroke(e.seat()) : new Color(0x33, 0x33, 0x33);
            if (e.thought()) {
                setFont(getFont().deriveFont(Font.ITALIC));
            } else {
                setFont(getFont().deriveFont(e.combat() ? Font.BOLD : Font.PLAIN));
            }
            if (selected) {
                setBackground(new Color(0xFF, 0xF3, 0xC4));
                setForeground(fg.darker());
                setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0,
                    e.seat() >= 0 ? FieldView.seatColor(e.seat()) : Color.DARK_GRAY));
            } else {
                setBackground(Color.WHITE);
                setForeground(fg);
                setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
            }
            return this;
        }
    }

    /**
     * ЦЕНТР — ФРЕЙМ С ВКЛАДКАМИ (заказ дизайнера 12.08.2026), как в конструкторе:
     * поле, планшеты науки и рынка, супер-задания всех игроков. Вкладки строятся
     * один раз и переиспользуются: сами панели показывают текущий кадр.
     */
    private JTabbedPane centreTabs() {
        if (tabs == null) {
            tabs = new JTabbedPane();
            tabs.addTab("Поле", field);
            // ПЛАНШЕТЫ — В ПРОКРУТКЕ. Они нарисованы вёрсткой на 900×560, а в окне
            // им доставалось 1440×230: строки треков наезжали друг на друга, текст
            // рынка садился поверх заголовка. Теперь при нехватке места панель
            // прокручивается, а при избытке — растягивается (см. Scrollable в самих
            // панелях).
            tabs.addTab("Наука и рынок", scrolled(boards));
            tabs.addTab("Супер-задания", scrolled(supers));
            // Поле — главный вид, и меньше этого его сжимать нельзя: раньше
            // разделители могли сдавить вкладки до нуля, и поле пропадало вовсе.
            tabs.setMinimumSize(new Dimension(420, FIELD_MIN_H));
            tabs.addChangeListener(e -> {
                if (record != null) {
                    refreshTabs(record.frames.get(cursor));
                }
            });
        }
        return tabs;
    }

    /** Панель в прокрутке без рамки: планшеты рисуются своей вёрсткой. */
    private static JScrollPane scrolled(Component view) {
        JScrollPane sc = new JScrollPane(view);
        sc.setBorder(null);
        sc.getVerticalScrollBar().setUnitIncrement(24);
        sc.getHorizontalScrollBar().setUnitIncrement(24);
        return sc;
    }

    /** Обновить содержимое вкладок планшетов под выбранный кадр. */
    private void refreshTabs(ReplayRecord.Frame f) {
        boards.show(record, f.snapshot);
        supers.show(record, f.snapshot);
    }

    // ==================== раскладка зон игроков ====================
    private void layoutBoard(int playerCount) {
        board.removeAll();
        leftSplit = null;
        rightSplit = null;

        // Зоны игроков живут в РАЗДВИГАЕМЫХ рамках: границы тянутся мышкой, а
        // кнопка в полоске заголовка сворачивает рамку до одной строки
        // (просьба дизайнера 12.08.2026). Поле в центре — оно не сворачивается.
        int bottomCount = Math.min(2, playerCount);
        JPanel bottom = new JPanel(new GridLayout(1, bottomCount, 6, 0));
        for (int i = 0; i < bottomCount; i++) {
            bottom.add(withOrders(i, false));
        }
        zonesBox = new Collapsible("зоны игроков 1–" + bottomCount, bottom);

        // Вес 1.0: прирост окна достаётся ПОЛЮ, зоны остаются своей высоты.
        centreSplit = splitter(JSplitPane.VERTICAL_SPLIT, centreTabs(), zonesBox, 1.0);
        zonesBox.bindTo(centreSplit, false);

        Component core = centreSplit;
        if (playerCount >= 4) {
            rightSplit = splitter(JSplitPane.HORIZONTAL_SPLIT, core, withOrders(3, true), 1.0);
            core = rightSplit;
        }
        if (playerCount >= 3) {
            leftSplit = splitter(JSplitPane.HORIZONTAL_SPLIT, withOrders(2, true), core, 0.0);
            core = leftSplit;
        }
        board.add(core, BorderLayout.CENTER);
        board.revalidate();
        board.repaint();
        laidOutFor = playerCount;
        // Точные положения границ — когда раскладка уже получила размер.
        SwingUtilities.invokeLater(this::applyLayoutSizes);
    }

    /**
     * Зона игрока вместе с его полоской РАЗЫГРАННЫХ ПРИКАЗОВ. Полоска стоит
     * вплотную к статистике — на границе с ней, как просил дизайнер: карты и
     * ресурсы читаются одним взглядом.
     */
    private Component withOrders(int seat, boolean sideways) {
        // ДВА ОТДЕЛЬНЫХ ФРЕЙМА: статистика и карты приказов. Каждый сворачивается
        // своей кнопкой, а граница между ними тянется мышкой — картам нужно
        // много места, и сколько именно, решает пользователь (просьба дизайнера).
        Collapsible stats = new Collapsible("игрок " + (seat + 1) + " · статистика",
            zones[seat]);
        JPanel cards = new JPanel(new BorderLayout());
        cards.setBorder(sideways
            ? BorderFactory.createMatteBorder(2, 0, 0, 0, strips[seat].seatColor())
            : BorderFactory.createMatteBorder(0, 2, 0, 0, strips[seat].seatColor()));
        cards.add(new JScrollPane(strips[seat],
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        Collapsible orders = new Collapsible("игрок " + (seat + 1) + " · приказы", cards);

        // ВНИЗУ — РЯДОМ, ПО БОКАМ — ДРУГ ПОД ДРУГОМ. Нижние зоны широкие и низкие:
        // если ставить карты приказов ПОД показателями, на карты остаётся полоска
        // в десяток пикселей, и они не видны вовсе. Боковым колонкам, наоборот,
        // ширины мало, и там единственный разумный порядок — сверху вниз.
        int orientation = sideways ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
        JSplitPane sp = splitter(orientation, stats, orders, sideways ? 0.35 : 0.48);
        sp.setDividerLocation(sideways ? 112 : 220);
        stats.bindTo(sp, true);
        orders.bindTo(sp, false);
        return sp;
    }

    /**
     * РАССТАВИТЬ РАЗДЕЛИТЕЛИ так, чтобы главным на экране было ПОЛЕ.
     *
     * <p>Раньше доли задавались числами вроде {@code 0.62} и {@code 252.0/1200.0} —
     * то есть расчёт на окно ровно 1200 точек шириной, а доли считались до того,
     * как окно получило размер. Итог: поле получало 150 пикселей из 1080.
     *
     * <p>Теперь наоборот: логу, зонам игроков и боковым колонкам выдаётся столько,
     * сколько им нужно (в пикселях), а ВСЁ ОСТАЛЬНОЕ — полю. Если окно совсем
     * маленькое, уступают они, а не поле: {@code Math.max} держит полю нижнюю
     * границу.
     */
    private void applyLayoutSizes() {
        if (logSplit != null && logBox != null && logBox.isOpen()) {
            int h = logSplit.getHeight();
            if (h > 0) {
                logSplit.setDividerLocation(Math.max(FIELD_MIN_H + 40,
                    h - LOG_H - logSplit.getDividerSize()));
            }
        }
        if (centreSplit != null && zonesBox != null && zonesBox.isOpen()) {
            int h = centreSplit.getHeight();
            if (h > 0) {
                centreSplit.setDividerLocation(Math.max(FIELD_MIN_H,
                    h - ZONES_H - centreSplit.getDividerSize()));
            }
        }
        if (leftSplit != null) {
            leftSplit.setDividerLocation(SIDE_W);
        }
        if (rightSplit != null) {
            int w = rightSplit.getWidth();
            if (w > 0) {
                rightSplit.setDividerLocation(Math.max(360,
                    w - SIDE_W - rightSplit.getDividerSize()));
            }
        }
    }

    /**
     * ЗАЩИТА ПОЛЯ ПРИ УМЕНЬШЕНИИ ОКНА.
     *
     * <p>Разделителю можно сказать «весь прирост — полю», но обратная сторона того
     * же правила: при СЖАТИИ окна поле отдаёт всё первым, и, потянув окно за угол,
     * его снова можно было сжать в полоску. Поэтому на каждое изменение размера
     * проверяем нижнюю границу и, если поле ушло ниже, отодвигаем границу назад.
     * Границы, которые человек передвинул сам, при этом не трогаются — только если
     * поле совсем прижали.
     */
    private void clampFieldSize() {
        if (logSplit != null && logBox != null && logBox.isOpen()) {
            int h = logSplit.getHeight();
            int want = Math.min(FIELD_MIN_H + 40, Math.max(140, h - 92));
            if (h > 0 && logSplit.getDividerLocation() < want) {
                logSplit.setDividerLocation(want);
            }
        }
        if (centreSplit != null && zonesBox != null && zonesBox.isOpen()) {
            int h = centreSplit.getHeight();
            int want = Math.min(FIELD_MIN_H, Math.max(120, h - 96));
            if (h > 0 && centreSplit.getDividerLocation() < want) {
                centreSplit.setDividerLocation(want);
            }
        }
    }

    /**
     * РЕЖИМ «ТОЛЬКО ПОЛЕ» (F11): убирает настройки, лог и зоны игроков в полоски,
     * оставляя поле и пульт. Нужен для разбора и для показа с проектора.
     */
    private void showFieldOnly(boolean only) {
        if (settingsBox != null) {
            settingsBox.setOpen(!only);
        }
        if (logBox != null) {
            logBox.setOpen(!only);
        }
        if (zonesBox != null) {
            zonesBox.setOpen(!only);
        }
        if (!only) {
            SwingUtilities.invokeLater(this::applyLayoutSizes);
        }
        say(only ? "Только поле. F11 — вернуть лог, зоны и настройки."
                 : "Обычный вид: настройки, зоны игроков и лог на месте.");
    }

    /** Разделитель с живым перетаскиванием границы и без лишней рамки. */
    private static JSplitPane splitter(int orientation, Component first, Component second,
                                       double weight) {
        JSplitPane sp = new JSplitPane(orientation, first, second);
        sp.setResizeWeight(weight);
        sp.setContinuousLayout(true);
        sp.setOneTouchExpandable(true);
        sp.setDividerSize(8);
        sp.setBorder(null);
        return sp;
    }

    // ==================== связывание ====================
    private void wire() {
        playGame.addActionListener(e -> startGame());
        // Всё, что меняет расстановку, обновляет поле СРАЗУ — не дожидаясь партии.
        players.addActionListener(e -> {
            for (int i = 0; i < 4; i++) {
                reloadSeatOptions(seats[i], i);
            }
            reloadFields();
            enableSeatControls();
            refreshPreview();
        });
        ruleset.addActionListener(e -> {
            reloadFields();
            refreshPreview();
        });
        fieldBox.addActionListener(e -> refreshPreview());
        for (JComboBox<String> c : cuFacing) {
            c.addActionListener(e -> refreshPreview());
        }
        seed.addActionListener(e -> refreshPreview());
        seed.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                refreshPreview();
            }
        });
        enableSeatControls();
        toStart.addActionListener(e -> manualSeek(0));
        stepBack.addActionListener(e -> manualSeek(cursor - 1));
        stepFwd.addActionListener(e -> manualSeek(cursor + 1));
        toEnd.addActionListener(e -> manualSeek(Integer.MAX_VALUE));
        playPause.addActionListener(e -> togglePlay());
        prevRound.addActionListener(e -> jumpRound(-1));
        nextRound.addActionListener(e -> jumpRound(+1));
        prevBattle.addActionListener(e -> jumpBattle(-1));
        nextBattle.addActionListener(e -> jumpBattle(+1));
        speed.addActionListener(e -> applySpeed());

        scrub.addChangeListener(e -> {
            if (scrub.getValueIsAdjusting()) {
                stop();       // взялись за ползунок — таймер больше не вырывает его
            }
            seek(scrub.getValue());
        });

        logList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || syncingSelection) {
                return;
            }
            LogEntry sel = logList.getSelectedValue();
            if (sel != null && sel.frameIndex() != cursor) {
                keepLogRow = logList.getSelectedIndex();
                stop();
                seek(sel.frameIndex());
            }
        });
        onlyBattles.addActionListener(e -> rebuildLog());
        onlyThoughts.addActionListener(e -> rebuildLog());
        onlyMine.addActionListener(e -> rebuildLog());
        mineSeat.addActionListener(e -> {
            if (onlyMine.isSelected()) {
                rebuildLog();
            }
        });

        // ГОРЯЧИЕ КЛАВИШИ. Раньше подсказки обещали Page Up, Page Down и «B», но
        // привязаны они не были — кнопка говорила неправду. И шаг стрелкой шёл БЕЗ
        // остановки показа, поэтому таймер тут же уводил кадр дальше: теперь всё
        // ручное листание идёт через manualSeek (сначала стоп).
        JPanel root = (JPanel) frame.getContentPane();
        bindKey(root, "SPACE", this::togglePlay);
        bindKey(root, "LEFT", () -> manualSeek(cursor - 1));
        bindKey(root, "RIGHT", () -> manualSeek(cursor + 1));
        bindKey(root, "HOME", () -> manualSeek(0));
        bindKey(root, "END", () -> manualSeek(Integer.MAX_VALUE));
        bindKey(root, "PAGE_UP", () -> jumpRound(-1));
        bindKey(root, "PAGE_DOWN", () -> jumpRound(+1));
        bindKey(root, "B", () -> jumpBattle(+1));
        bindKey(root, "shift B", () -> jumpBattle(-1));
        bindKey(root, "F11", () -> showFieldOnly(settingsBox != null && settingsBox.isOpen()));
    }

    /** Лишние места гасим: при 2 игроках нет ни места 3, ни места 4. */
    private void enableSeatControls() {
        int n = players.getSelectedItem() instanceof Integer i ? i : 4;
        for (int seat = 0; seat < 4; seat++) {
            boolean on = seat < n;
            seats[seat].setEnabled(on);
            cuFacing[seat].setEnabled(on);
            seatLabels[seat].setEnabled(on);
        }
    }

    private void bindKey(javax.swing.JComponent root, String key, Runnable action) {
        root.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(key), key);
        root.getActionMap().put(key, new javax.swing.AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    /**
     * Показать СТАРТОВУЮ РАССТАНОВКУ с текущими параметрами прямо сейчас.
     * Партия при этом не играется — только подготовка, это быстро.
     */
    private void refreshPreview() {
        if (suppressPreview) {
            return;      // сейчас сами заполняем поля формы — это не выбор пользователя
        }
        try {
            suppressPreview = true;
            long s = Long.parseLong(seed.getText().trim());
            int n = (Integer) players.getSelectedItem();
            List<String> ids = new ArrayList<>();
            List<Integer> facing = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ids.add(((GameRecorder.SeatOption) seats[i].getSelectedItem()).id());
                int fi = cuFacing[i].getSelectedIndex();
                facing.add(fi <= 0 ? null : fi - 1);
            }
            FieldOption fo = (FieldOption) fieldBox.getSelectedItem();
            ReplayRecord rec = GameRecorder.preview(String.valueOf(ruleset.getSelectedItem()),
                n, s, ids, fo == null ? null : fo.id(), facing,
                fo == null ? null : fo.file(), blockSeed);
            load(rec);
            say("Расстановка показана. Меняй параметры — поле обновляется сразу; "
                + "нажми «Сыграть и показать», чтобы увидеть партию.");
        } catch (NumberFormatException e) {
            say("Сид должен быть целым числом — расстановка не перестроена.");
        } catch (RuntimeException e) {
            say("Расстановку не собрать: " + human(e));
        } finally {
            suppressPreview = false;
        }
    }

    // ==================== прогон партии ====================
    private void startGame() {
        long s;
        try {
            s = Long.parseLong(seed.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Сид должен быть целым числом. Сейчас там: «"
                    + seed.getText() + "»."), "Проверь сид", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int n = (Integer) players.getSelectedItem();
        String rs = String.valueOf(ruleset.getSelectedItem());
        List<String> ids = new ArrayList<>();
        List<Integer> facing = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(((GameRecorder.SeatOption) seats[i].getSelectedItem()).id());
            int fi = cuFacing[i].getSelectedIndex();
            facing.add(fi <= 0 ? null : fi - 1);      // «авто» = null, «1» = сторона 0
        }
        FieldOption fo = (FieldOption) fieldBox.getSelectedItem();
        String fieldId = fo == null ? null : fo.id();
        Path fieldFile = fo == null ? null : fo.file();
        // сборка блоков, которую игрок сейчас видит в расстановке
        final Long bs = blockSeed;
        stop();
        setBusy(true);
        say("Играю партию: " + n + " игроков, сид " + s + ", правила " + rs
            + ", поле " + (fieldId == null ? "по сиду" : fieldId) + "…");

        new SwingWorker<ReplayRecord, String>() {
            @Override
            protected ReplayRecord doInBackground() {
                return GameRecorder.play(rs, n, s, ids, fieldId, facing, fieldFile,
                    bs, this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String c : chunks) {
                    say(c);
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    load(get());
                    lastFile = null;
                    // Партия сыграна — настройки больше не нужны, и их полоса
                    // (полторы сотни пикселей) уходит ПОЛЮ. Разворачивается одним
                    // щелчком по заголовку, когда понадобится следующая партия.
                    if (settingsBox != null) {
                        settingsBox.setOpen(false);
                    }
                    SwingUtilities.invokeLater(ReplayGui.this::applyLayoutSizes);
                    say("Готово. Шагов: " + record.frames.size()
                        + ". Победил " + (record.winner == null ? "никто"
                            : record.playerName(record.winner))
                        + ". Жми «играть» или листай шагами. Настройки свернулись — "
                        + "место отдано полю.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    say("Партия не сыграна: " + human(cause));
                    JOptionPane.showMessageDialog(frame, Ui.text(
                        "Не получилось сыграть партию.\n\n" + human(cause)),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Условие конца партии по-русски. */
    private static String conditionRu(String condition) {
        return switch (condition == null ? "" : condition) {
            case "victory_points" -> "по победным очкам";
            case "super_objective" -> "супер-заданием";
            case "all_peaks_occupied" -> "заняты все вершины треков";
            case "last_spawn_tile" -> "кончился келемий на поле";
            case "military" -> "военная победа";
            default -> condition;
        };
    }

    private static String human(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank() ? t.getClass().getSimpleName() : m);
    }

    private void setBusy(boolean busy) {
        playGame.setEnabled(!busy);
        playGame.setText(busy ? "играю партию…" : "Сыграть и показать");
        players.setEnabled(!busy);
        seed.setEnabled(!busy);
        ruleset.setEnabled(!busy);
        fieldBox.setEnabled(!busy);
        if (busy) {
            for (int i = 0; i < 4; i++) {
                seats[i].setEnabled(false);
                cuFacing[i].setEnabled(false);
            }
        } else {
            enableSeatControls();
        }
    }

    /** Показать загруженную запись с самого начала. */
    private void load(ReplayRecord rec) {
        boolean was = suppressPreview;
        suppressPreview = true;
        try {
            loadInner(rec);
        } finally {
            suppressPreview = was;
        }
    }

    private void loadInner(ReplayRecord rec) {
        stop();
        this.record = rec;
        this.cursor = 0;
        // РАСКЛАДКУ ПЕРЕСОБИРАЕМ ТОЛЬКО ПРИ СМЕНЕ ЧИСЛА ИГРОКОВ. Раньше это делалось
        // на каждую загрузку, а расстановка обновляется при любой правке настроек —
        // и подобранные мышкой границы панелей, свёрнутые рамки и масштаб поля
        // слетали от нажатия «другая сборка».
        if (laidOutFor != rec.players) {
            layoutBoard(rec.players);
        }
        for (int i = 0; i < 4; i++) {
            zones[i].setRecord(rec);
        }
        field.setRecord(rec);
        // Вкладкам планшетов нужны САМИ ПРАВИЛА и карточные наборы той версии,
        // в которой сыграна партия: стоимости шагов, ёмкости, призы, тексты карт
        // рынка, супер-арсенала и супер-заданий. Не загрузилось — панели честно
        // покажут «не задано», а не выдуманные числа.
        try {
            var cfg = kelium.dataio.GameConfig.buildCached(
                rec.ruleset == null || rec.ruleset.isBlank()
                    ? kelium.dataio.GameConfig.DEFAULT_RULESET : rec.ruleset,
                Math.max(2, rec.players), 0L, null, null);
            boards.setRules(cfg.ruleset, cfg.content);
            supers.setContent(cfg.content);
        } catch (RuntimeException e) {
            boards.setRules(null, null);
            supers.setContent(null);
        }
        mineSeat.removeAllItems();
        for (int i = 0; i < rec.players; i++) {
            mineSeat.addItem(rec.playerName(i));
        }
        players.setSelectedItem(rec.players);
        seed.setText(String.valueOf(rec.seed));
        if (((javax.swing.DefaultComboBoxModel<String>) ruleset.getModel())
                .getIndexOf(rec.ruleset) < 0) {
            ruleset.addItem(rec.ruleset);
        }
        ruleset.setSelectedItem(rec.ruleset);
        for (int i = 0; i < rec.players && i < 4; i++) {
            String id = rec.seatIds.get(i);
            for (int k = 0; k < GameRecorder.SEAT_OPTIONS.size(); k++) {
                if (GameRecorder.SEAT_OPTIONS.get(k).id().equals(id)) {
                    seats[i].setSelectedIndex(k);
                    break;
                }
            }
            Integer f = i < rec.cuFacing.size() ? rec.cuFacing.get(i) : null;
            cuFacing[i].setSelectedIndex(f == null ? 0 : Math.floorMod(f, 6) + 1);
        }
        reloadFields();
        enableSeatControls();
        if (rec.scenarioId != null) {
            for (int i = 0; i < fieldBox.getItemCount(); i++) {
                FieldOption o = fieldBox.getItemAt(i);
                boolean sameFile = rec.scenarioFile == null
                    ? o.file() == null
                    : o.file() != null && rec.scenarioFile.equals(o.file().toString());
                if (rec.scenarioId.equals(o.id()) && sameFile) {
                    fieldBox.setSelectedIndex(i);
                    break;
                }
            }
        } else {
            fieldBox.setSelectedIndex(0);
        }
        scrub.setMinimum(0);
        scrub.setMaximum(Math.max(0, rec.frames.size() - 1));
        // Итог партии — в заголовок окна: одна строка статуса его затирает,
        // а это главный факт, ради которого партию и смотрят.
        frame.setTitle("Кристаллы Раздора — проигрыватель партий   ·   "
            + rec.players + " игрока, сид " + rec.seed
            + (rec.scenarioId == null ? "" : ", поле " + rec.scenarioId)
            + (rec.winner == null ? "   ·   расстановка"
                : "   ·   победил " + rec.playerName(rec.winner)
                  + " (" + conditionRu(rec.condition) + "), раундов " + rec.rounds));
        rebuildLog();
        showFrame(0);
        // Вписываем поле в окно, только если пользователь сам не крутил колесо и не
        // таскал поле: иначе каждая смена настроек сбрасывала его масштаб.
        if (field.isAutoFit()) {
            field.fitToWindow();
        }
        updateControls();
    }

    // ==================== управление показом ====================
    private void togglePlay() {
        if (record == null) {
            return;
        }
        if (ticker.isRunning()) {
            stop();
        } else {
            if (cursor >= record.frames.size() - 1) {
                seek(0);
            }
            applySpeed();
            ticker.start();
            playPause.setIcon(TransportIcons.of("PAUSE", iconPx + 4));
        }
    }

    private void stop() {
        ticker.stop();
        playPause.setIcon(TransportIcons.of(playPauseCode(), iconPx + 4));
    }

    private void applySpeed() {
        int idx = speed.getSelectedIndex();
        int delay = switch (idx) {
            case 0 -> 2800;
            case 1 -> 1400;
            case 2 -> 700;
            case 3 -> 350;
            case 4 -> 175;
            case 5 -> 90;
            default -> 1;           // «как можно быстрее» — без пауз, но БЕЗ пропусков
        };
        ticker.setDelay(delay);
        ticker.setInitialDelay(delay);
    }

    private void tick() {
        if (record == null) {
            stop();
            return;
        }
        int next = cursor + 1;
        if (next >= record.frames.size()) {
            seek(record.frames.size() - 1);
            stop();
            return;
        }
        seek(next);
        if (pauseOnBattle.isSelected() && record.frames.get(next).combat) {
            stop();
            say("Остановился на бою — так стоит галочка «пауза на боях».");
        }
    }

    /** Перемотка РУКОЙ: сначала стоп, чтобы таймер не уводил шаг дальше. */
    private void manualSeek(int index) {
        stop();
        seek(index);
    }

    private void seek(int index) {
        if (record == null || record.frames.isEmpty()) {
            return;
        }
        int i = Math.max(0, Math.min(record.frames.size() - 1, index));
        if (i == cursor && scrub.getValue() == i) {
            return;
        }
        cursor = i;
        showFrame(i);
    }

    /**
     * К началу соседнего раунда. Никакой скрытой логики «если ты почти в
     * начале»: назад — всегда предыдущий раунд, вперёд — всегда следующий.
     */
    private void jumpRound(int direction) {
        if (record == null || record.frames.isEmpty()) {
            return;
        }
        stop();          // прыжок — тоже ручное листание: показ останавливаем
        int round = record.frames.get(cursor).snapshot.round;
        if (direction < 0) {
            int start = firstFrameOfRound(round);
            seek(cursor > start ? start : firstFrameOfRound(round - 1));
        } else {
            int next = firstFrameOfRound(round + 1);
            if (next < 0) {
                say("Это последний раунд партии.");
                return;
            }
            seek(next);
        }
    }

    /** Первый кадр раунда; −1 — такого раунда нет. */
    private int firstFrameOfRound(int round) {
        if (round < 0) {
            return 0;
        }
        for (int i = 0; i < record.frames.size(); i++) {
            if (record.frames.get(i).snapshot != null
                    && record.frames.get(i).snapshot.round == round) {
                return i;
            }
        }
        return round <= 0 ? 0 : -1;
    }

    private void jumpBattle(int direction) {
        if (record == null) {
            return;
        }
        stop();
        for (int i = cursor + direction; i >= 0 && i < record.frames.size(); i += direction) {
            if (record.frames.get(i).combat) {
                seek(i);
                return;
            }
        }
        say(direction > 0 ? "Дальше боёв нет — до самого конца партии."
                          : "Раньше этого шага боёв не было.");
    }

    private void showFrame(int i) {
        ReplayRecord.Frame f = record.frames.get(i);
        field.setFrame(f);
        refreshTabs(f);
        for (int seat = 0; seat < record.players && seat < 4; seat++) {
            zones[seat].showFrame(f);
            if (strips[seat] != null) {
                strips[seat].update(record, i);
            }
        }
        scrub.setValue(i);
        position.setText("шаг " + (i + 1) + " из " + record.frames.size()
            + "   ·   раунд " + f.snapshot.round
            + (f.snapshot.circle > 0 ? ", круг " + f.snapshot.circle : ""));
        selectLogLineFor(i);
        updateControls();
    }

    private void updateControls() {
        boolean has = record != null && record.frames.size() > 1;
        boolean atStart = !has || cursor <= 0;
        boolean atEnd = !has || cursor >= record.frames.size() - 1;
        toStart.setEnabled(!atStart);
        stepBack.setEnabled(!atStart);
        prevRound.setEnabled(!atStart);
        prevBattle.setEnabled(!atStart);
        stepFwd.setEnabled(!atEnd);
        toEnd.setEnabled(!atEnd);
        nextRound.setEnabled(!atEnd);
        nextBattle.setEnabled(!atEnd);
        playPause.setEnabled(has);
        // на пульте только значки — что делает кнопка, говорит подсказка
        playPause.setIcon(TransportIcons.of(playPauseCode(), iconPx + 4));
        playPause.setToolTipText(Ui.text(ticker.isRunning() ? "Пауза (пробел)."
            : (atEnd && has ? "Партия закончена — показать сначала (пробел)."
                : "Играть (пробел).")));
        scrub.setEnabled(has);
        speed.setEnabled(has);
        pauseOnBattle.setEnabled(has);
        if (record == null || record.frames.isEmpty()) {
            position.setText("партия не загружена");
        }
    }

    // ==================== лог ====================
    private void rebuildLog() {
        if (record == null) {
            logModel.clear();
            return;
        }
        java.util.List<LogEntry> rows = new ArrayList<>();
        int mine = Math.max(0, mineSeat.getSelectedIndex());
        for (int i = 0; i < record.frames.size(); i++) {
            ReplayRecord.Frame f = record.frames.get(i);
            for (ReplayRecord.Thought t : f.thoughts) {
                LogEntry e = new LogEntry(i, t.seat,
                    record.playerName(t.seat) + ": «" + t.text + "»", true, f.combat);
                if (keep(e, mine)) {
                    rows.add(e);
                }
            }
            LogEntry e = new LogEntry(i, f.seat == null ? -1 : f.seat, f.log, false, f.combat);
            if (keep(e, mine)) {
                rows.add(e);
            }
        }
        if (rows.isEmpty()) {
            rows.add(new LogEntry(cursor, -1,
                "(под эти фильтры не попала ни одна строка)", false, false));
        }
        // Одним куском: поэлементное добавление тысячи строк заставляет JList
        // пересчитывать раскладку на каждую и подвешивает окно.
        logModel.clear();
        logModel.addAll(rows);
        selectLogLineFor(cursor);
    }

    /**
     * Отбор строки под фильтры. «Только бои» и «только мысли» складываются
     * ПО ИЛИ: отметив обе галочки, видишь и бои, и мысли, а не пустой список.
     * Фильтр по игроку независим и работает по И.
     */
    private boolean keep(LogEntry e, int mine) {
        if (onlyBattles.isSelected() || onlyThoughts.isSelected()) {
            boolean ok = (onlyBattles.isSelected() && e.combat() && !e.thought())
                || (onlyThoughts.isSelected() && e.thought());
            if (!ok) {
                return false;
            }
        }
        return !onlyMine.isSelected() || e.seat() == mine;
    }

    /** Подсветить и прокрутить лог к строке текущего шага. */
    private void selectLogLineFor(int frameIndex) {
        if (keepLogRow >= 0) {
            // Курсор двинул сам пользователь кликом по строке — оставляем
            // подсвеченной именно ту строку, по которой он попал.
            logList.ensureIndexIsVisible(keepLogRow);
            keepLogRow = -1;
            return;
        }
        int best = -1;
        for (int i = 0; i < logModel.size(); i++) {
            if (logModel.get(i).frameIndex() <= frameIndex) {
                best = i;
            } else {
                break;
            }
        }
        if (best < 0) {
            return;
        }
        syncingSelection = true;
        logList.setSelectedIndex(best);
        logList.ensureIndexIsVisible(best);
        syncingSelection = false;
    }

    // ==================== файл ====================
    private void openRecord() {
        Path start = lastFile != null ? lastFile : defaultDir();
        Path file = PathDialog.choose(frame, "Открыть запись партии", start, false, "json");
        if (file != null) {
            openFile(file);
        }
    }

    /**
     * Открыть файл записи. Чтение и разбор трёхмегабайтного JSON идут В ФОНЕ:
     * на потоке отрисовки окно на пару секунд переставало отвечать.
     */
    private void openFile(Path file) {
        stop();
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        say("Читаю запись " + file.getFileName() + "…");
        new SwingWorker<ReplayRecord, Void>() {
            @Override
            protected ReplayRecord doInBackground() throws Exception {
                return ReplayRecord.load(file);
            }

            @Override
            protected void done() {
                frame.setCursor(Cursor.getDefaultCursor());
                try {
                    ReplayRecord rec = get();
                    load(rec);
                    lastFile = file;
                    say("Открыта запись: " + file + " (шагов " + rec.frames.size() + ").");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(frame,
                        Ui.text("Не удалось открыть запись.\n\n" + human(cause)),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                    say("Запись не открыта: " + human(cause));
                }
            }
        }.execute();
    }

    private void saveRecord() {
        if (record == null) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Сохранять пока нечего: партия не сыграна и не открыта."),
                "Нет записи", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path start = lastFile != null ? lastFile
            : defaultDir().resolve("partiya-" + record.players + "p-seed" + record.seed + ".json");
        Path file = PathDialog.choose(frame, "Сохранить запись партии", start, true, "json");
        if (file == null) {
            return;
        }
        final ReplayRecord saving = record;
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        say("Пишу запись " + file.getFileName() + "…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                saving.save(file);          // несколько мегабайт — не на потоке окна
                return null;
            }

            @Override
            protected void done() {
                frame.setCursor(Cursor.getDefaultCursor());
                try {
                    get();
                    lastFile = file;
                    say("Запись сохранена: " + file);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(frame,
                        Ui.text("Не удалось сохранить запись.\n\n" + human(cause)),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Куда класть записи по умолчанию. НЕ рабочий каталог процесса: у exe,
     * установленного в Program Files, туда нет права записи.
     */
    /** Сохранить текущий кадр поля в PNG — для иллюстраций и разборов. */
    private void saveShot() {
        if (record == null || record.frames.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Сохранять нечего: партия не сыграна и не открыта."),
                "Нет партии", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Path start = defaultDir().resolve("kadr-" + (cursor + 1) + ".png");
        Path file = PathDialog.choose(frame, "Сохранить кадр картинкой", start, true, "png");
        if (file == null) {
            return;
        }
        int w = Math.max(600, field.getWidth());
        int h = Math.max(450, field.getHeight());
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        field.paint(g);
        g.dispose();
        try {
            javax.imageio.ImageIO.write(img, "png", file.toFile());
            say("Кадр сохранён: " + file);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                Ui.text("Не удалось сохранить картинку.\n\n" + human(e)),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Path defaultDir() {
        Path dir = Paths.get(System.getProperty("user.home"),
            "Кристаллы Раздора",
            "Записи партий");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (IOException e) {
            return Paths.get(System.getProperty("user.home"));
        }
        return dir;
    }

    // ==================== библиотека раскладок ====================
    /** Общий на все приложения диалог «где что лежит». */
    private void editLibrary() {
        if (PlacesDialog.show(frame)) {
            reloadFields();
            refreshPreview();
            say("Места хранения обновлены — список полей перечитан.");
        }
    }

    // ==================== справка ====================
    private void showHelp() {
        String html = """
            <html><body style='width:560px;font-family:sans-serif'>
            <h2>Проигрыватель партий</h2>
            <p>Приложение показывает партию ботов <b>ход за ходом</b>, как запись матча.
            Один шаг — одно событие движка: действие, удар в бою, розыгрыш задания,
            конец раунда.</p>

            <h3>Как посмотреть партию</h3>
            <ol>
              <li>Выбери число игроков, сид и версию правил.</li>
              <li>Посади ботов на места (у каждого свой характер — наведи мышь на список).</li>
              <li>Нажми <b>«Сыграть и показать»</b>. Партия играется целиком и записывается,
                  поэтому её можно листать и вперёд, и назад.</li>
            </ol>

            <h3>Пульт</h3>
            <p>Кнопки пульта — значками; что делает кнопка, всегда написано в подсказке
            при наведении. Значки говорят цветом и формой: <b>зелёный треугольник</b> —
            играть, <b>две полосы</b> — пауза, <b>треугольник со стойкой</b> — шаг
            (поэтому его не спутать с «играть»), <b>синие уголки</b> — прыжки по
            раундам, <b>красная вспышка</b> — прыжки по боям.</p>
            <ul>
              <li><b>играть / пауза</b> — пробел.</li>
              <li><b>шаг назад / шаг вперёд</b> — стрелки ← и →.</li>
              <li><b>в начало / в конец</b> — Home и End.</li>
              <li><b>раунд назад / раунд вперёд</b> — Page Up и Page Down.</li>
              <li><b>бой назад / бой вперёд</b> — Shift+B и B.</li>
              <li><b>Только поле</b> — F11: убирает настройки, зоны игроков и лог,
                  оставляя поле и пульт. Второе нажатие возвращает всё назад.</li>
              <li><b>Скорость</b> — от 0,25× до 8×; «как можно быстрее» прокручивает
                  партию без пауз, но ни одного шага не пропускает.</li>
              <li><b>Пауза на боях</b> — показ сам останавливается на каждом ударе.</li>
              <li><b>Полоса</b> под кнопками — перемотка в любую точку партии.</li>
            </ul>

            <h3>Поле</h3>
            <p>Колесо мыши — масштаб, перетаскивание — сдвиг, «Вид → Вписать поле в окно»
            вернёт всё на место. Наведи мышь на гекс — увидишь, что там стоит.</p>
            <p>Подсветки: пунктирная рамка — гексы того, чей сейчас ход; стрелка —
            перемещение; красная линия и вспышка — удар; кольцо с «+» — постройка;
            мигающее красное кольцо — урон; крест — уничтожение.</p>

            <h3>Зоны игроков</h3>
            <p>У каждого игрока свой планшет: ресурсы, склад, здания, войска на поле и в
            резерве, наука, модули, арсенал, задания, супер-задание и победные очки с
            разбивкой. Зона того, чей ход, обведена жирной рамкой. У каждого блока есть
            подсказка при наведении.</p>

            <h3>Лог</h3>
            <p>Внизу — ходы и <b>мысли ботов</b> от первого лица. Строки окрашены цветом
            игрока, текущая подсвечена. <b>Клик по строке перематывает партию</b> к этому
            моменту. Галочки сверху фильтруют: только бои, только мысли, только один игрок.</p>
            <p>Мысли озвучивают стратеги, Исследователь и Хаос. Простые эвристики и
            случайный бот молчат — им нечего объяснять.</p>

            <h3>Запись</h3>
            <p>«Файл → Сохранить запись» кладёт партию в файл JSON целиком (со всеми
            шагами), «Файл → Открыть запись» показывает её обратно ровно в том же виде.
            Тот же результат даёт повторный прогон с тем же сидом, той же раскладкой и
            тем же составом ботов — для этого есть «Файл → Переиграть эту партию заново».</p>
            <p>«Файл → Сохранить текущий кадр картинкой» кладёт видимое поле в PNG —
            удобно вставлять в документы по правилам.</p>

            <h3>Свои раскладки</h3>
            <p>Нарисуй поле <b>конструктором раскладок</b> и сохрани его YAML в любую
            папку. Затем здесь: <b>Настройки → Папки с раскладками…</b> — добавь эту
            папку. Все поля из неё появятся в списке «поле» с пометкой <b>«своя»</b>
            рядом с авторскими, и на них можно играть.</p>
            <p>Папка авторских раскладок (<code>simulator/data/scenarios</code>) в
            библиотеке всегда — туда конструктор и предлагает сохранять по умолчанию.
            Число игроков определяется по самой раскладке: сколько на ней стартовых
            гексов игроков, для стольких она и предлагается. Если стартов не
            расставить, поле в список не попадёт, а причина будет написана в строке
            состояния внизу.</p>

            <h3>Настройки партии</h3>
            <p><b>Поле</b> — список авторских раскладок; он перестраивается под выбранное
            число игроков, чужие поля туда не попадают. <b>ЦУ гранью</b> — с какой грани
            стартового гекса стоит центр управления; «авто» разворачивает его носом
            к центру поля, в сторону развития. Любая правка настроек сразу
            перерисовывает стартовую расстановку — партию для этого играть не нужно.</p>
            </body></html>
            """;
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        JScrollPane sc = new JScrollPane(pane);
        sc.setPreferredSize(new Dimension(640, 560));
        JDialog d = new JDialog(frame, "Как пользоваться", true);
        d.add(sc);
        d.pack();
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(frame, Ui.text(
            "Проигрыватель партий «Кристаллы Раздора».\n\n"
            + "Показывает партию ботов по шагам: поле, зоны игроков, лог ходов "
            + "и мыслей.\n"
            + "Правила и карты берутся из общего каталога данных симулятора, "
            + "движок вызывается напрямую как библиотека — сама игра не меняется.\n\n"
            + "Версия правил по умолчанию: " + GameConfig.DEFAULT_RULESET, 420),
            "О приложении", JOptionPane.INFORMATION_MESSAGE);
    }

    private void say(String text) {
        status.setText(text);
    }
}
