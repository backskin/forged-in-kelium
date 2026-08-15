package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

import kelium.gui.BoardsPanel;
import kelium.gui.GameRecorder;
import kelium.gui.PathDialog;
import kelium.gui.SuperObjectivesPanel;
import kelium.report.ReplayRecord;

/**
 * Replay2Gui — ПРОИГРЫВАТЕЛЬ ПАРТИЙ 2.0: «судейская комната».
 *
 * <p>Устройство экрана — «Сцена»: поле занимает всё, что не занято немногими
 * постоянными жильцами (строка контекста, четыре полосы игроков, лента времени,
 * пульт, строка состояния). Лог, планшет игрока, биография гекса, графики и журналы
 * живут в выдвижном ящике справа и приходят по вызову. Планшеты науки и рынка и
 * экран итогов открываются на месте поля — как «дай посмотреть планшет» за столом.
 *
 * <p>Версия 1.0 ({@link kelium.gui.ReplayGui}) остаётся на месте и продолжает
 * работать: это отдельное приложение, а не замена. Общими остаются движок, запись
 * партии, единый отрисовщик поля и панели планшетов.
 *
 * <p>Запуск: {@code java -cp <classpath> kelium.gui.replay2.Replay2Gui [файл.json]}.
 */
public final class Replay2Gui {

    /** Раздел файла настроек, в котором живёт это окно. */
    private static final String SECTION = "replay2";

    private final Session session = new Session();
    private final kelium.dataio.AppSettings prefs = kelium.dataio.AppSettings.of(SECTION);

    private JFrame frame;
    private SceneField field;
    private Timeline timeline;
    private TransportBar transport;
    private Drawer drawer;
    private SetupPanel setup;
    private JSplitPane drawerSplit;
    private JPanel stage;
    private CardLayout stageCards;
    private JPanel stripsRow;
    private JScrollPane stripsScroll;
    private final PlayerStrip[] strips = new PlayerStrip[4];
    private final JLabel context = new JLabel();
    private final JLabel thought = new JLabel();
    private final JLabel status = new JLabel();
    private final BoardsPanel boards = new BoardsPanel();
    private final SuperObjectivesPanel supers = new SuperObjectivesPanel();
    private ResultsPanel results;
    private JButton setupButton;

    /** Подложки, чей цвет задан кодом: при смене темы их надо перекрасить. */
    private final List<JComponent> panelSurfaces = new ArrayList<>();
    private final List<JComponent> bgSurfaces = new ArrayList<>();
    private JPanel topBarPanel;
    private JPanel deckPanel;

    private boolean drawerOpen;
    private boolean fieldOnly;
    private boolean spoilerFree;
    private Path lastFile;

    // ==================== запуск ====================
    public static void main(String[] args) {
        Path preload = args.length > 0 && !args[0].isBlank() ? Paths.get(args[0]) : null;
        kelium.dataio.AppSettings p = kelium.dataio.AppSettings.of(SECTION);
        // ПАПКА ДАННЫХ — САМЫМ ПЕРВЫМ ДЕЛОМ, до всего остального: правила и карты
        // грузятся отсюда, и путь, выбранный человеком в окне настроек, должен
        // действовать уже на первой загрузке, а не со второго запуска.
        kelium.dataio.Locations.applyDataFolder();
        Theme.loadScale(p);                   // масштаб — ДО сборки окна
        // Тему можно задать запуском: -Dkelium.theme=light|dark. Нужно, чтобы
        // проверять оба вида, не трогая запомненную настройку.
        String forced = System.getProperty("kelium.theme", "");
        Theme.apply(forced.isBlank() ? p.getBoolean("dark", true) : !"light".equals(forced));
        SwingUtilities.invokeLater(() -> {
            Replay2Gui gui = new Replay2Gui();
            gui.show();
            if (preload != null) {
                gui.openFile(preload);
            } else {
                gui.preview();
            }
        });
    }

    private void show() {
        frame = new JFrame("Кристаллы Раздора — разбор партии");
        kelium.gui.Ui.setAppIcon(frame, "replay2");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Theme.bg());

        // Сначала СОБИРАЕМ части, и только потом меню: его пункты ссылаются на поле,
        // ящик и пульт, и до их создания ставить меню нельзя.
        field = new SceneField(session);
        timeline = new Timeline(session);
        transport = new TransportBar(session, field);
        transport.setOnSay(this::say);
        drawer = new Drawer(session);
        drawer.setOnClose(() -> setDrawer(false));
        results = new ResultsPanel(session);
        setup = new SetupPanel(session, this::say);
        setup.setOnPlay(this::startGame);
        setup.setOnPreview(this::preview);

        frame.add(topBar(), BorderLayout.NORTH);
        frame.add(centre(), BorderLayout.CENTER);
        frame.add(bottom(), BorderLayout.SOUTH);
        frame.setJMenuBar(menuBar());

        field.setOnHexClick(id -> {
            if (id != null) {
                drawer.showHex(id);
                setDrawer(true);
            }
        });
        session.whenFrameChanged(s -> refreshContext());
        session.whenRecordChanged(s -> {
            rebuildStrips();
            drawer.refreshAll();
            refreshContext();
            refreshTitle();
        });

        bindKeys();
        restoreWindow();
        frame.setVisible(true);
        SwingUtilities.invokeLater(this::layoutStage);
        say("Готово к разбору. Настрой партию сверху и нажми «Сыграть и показать» — "
            + "или открой сохранённую запись (Ctrl+O).");
        SwingUtilities.invokeLater(this::checkDataOnStart);
    }

    /** Про сломанные данные говорим ОДИН раз за запуск, а не на каждую пересборку окна. */
    private static boolean dataWarned;

    /**
     * ПРОВЕРКА ДАННЫХ ПРИ ЗАПУСКЕ — и ГРОМКИЙ разговор, если их нет.
     *
     * <p>Повод (14.08.2026). Из папки данных пропал один файл, и приложение
     * открылось пустым: ни версий правил, ни полей, ни описаний карт в
     * справочнике. Никакого сообщения при этом не было — окно просто молчало, и
     * дизайнер решил, что «поехали пути». Хуже такого поведения только его
     * повторение, поэтому теперь приложение при запуске проверяет, собирается ли
     * хоть одна версия правил, и если нет — говорит, ЧЕГО именно не хватает, и
     * сразу предлагает окно настроек.
     */
    private void checkDataOnStart() {
        if (dataWarned) {
            return;
        }
        String trouble = null;
        List<String> rulesets = kelium.dataio.GameConfig.availableRulesets(null);
        if (rulesets.isEmpty()) {
            trouble = "В папке данных не найдено ни одной версии правил.";
        } else {
            boolean anyOk = false;
            String first = null;
            for (String rid : rulesets) {
                try {
                    kelium.dataio.GameConfig.buildCached(rid, 4, 0L, null, null);
                    anyOk = true;
                    break;
                } catch (RuntimeException e) {
                    if (first == null) {
                        first = human(e);
                    }
                }
            }
            if (!anyOk) {
                trouble = "Ни одна версия правил не собирается.\n\n" + first;
            }
        }
        if (trouble == null) {
            return;
        }
        dataWarned = true;
        say("ДАННЫЕ НЕ СОБИРАЮТСЯ. Файл → Настройки приложения (Ctrl+P) → «Проверка данных».");
        int a = JOptionPane.showOptionDialog(frame, Ui2.tip(
            "Данные игры не читаются, поэтому окно пустое: ни полей, ни карт, ни "
            + "описаний в справочнике.\n\n" + trouble
            + "\n\nПапка данных сейчас:\n"
            + kelium.dataio.GameConfig.resolveDataRoot(null)
            + "\n\nВ настройках можно указать другую папку и посмотреть подробную "
            + "проверку — там перечислено, какого файла не хватает.", 460),
            "Данные не читаются", JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE, null,
            new Object[]{"Открыть настройки", "Потом"}, "Открыть настройки");
        if (a == 0) {
            showSettings();
        }
    }

    // ==================== верх ====================
    private JPanel topBar() {
        JPanel bar = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(4) + " " + Theme.px(10) + " " + Theme.px(4) + " "
                + Theme.px(10) + ", gapx " + Theme.px(8) + ", novisualpadding",
            "[]" + Theme.px(12) + "[]push[]", "[]"));
        panelSurface(bar);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        topBarPanel = bar;

        // НАСТРОЙКИ НЕ ЖИВУТ НА ЭКРАНЕ: их трогают один раз перед прогоном, поэтому
        // здесь только строка-кнопка с их сводкой, а сама форма раскрывается по ней.
        setupButton = Ui2.textButton("настроить партию…",
            "Число игроков, сид, версия правил, поле и кто сидит на местах.",
            () -> setSetupOpen(!setup.isVisible()));
        bar.add(setupButton);

        context.setFont(Theme.subtitle());
        context.setForeground(Theme.ink());
        bar.add(context);
        thought.setFont(Theme.italic());
        thought.setForeground(Theme.ink2());
        bar.add(thought, "gapx " + Theme.px(12));

        JPanel right = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(4)));
        right.setOpaque(false);
        // Тема — единственный ЦВЕТНОЙ значок в строке: половина солнечная,
        // половина ночная. Рисованный, а не символ шрифта (◐ рисуется квадратом).
        right.add(Ui2.iconButton(kelium.gui.TransportIcons.of("THEME", Theme.px(18)),
            "Переключить тёмную и светлую темы.", 26, this::toggleTheme));
        right.add(Ui2.textButton("лог", "Открыть или закрыть ящик с логом (L).",
            () -> openDrawer(Drawer.View.LOG)));
        // НАЗВАНИЕ ПО ФАКТУ, а не по первому замыслу. Кнопка убирает полосы
        // игроков и ленту времени на ЛЮБОЙ вкладке — и когда открыты планшеты
        // или итоги, «только поле» прямо врало (замечание дизайнера 14.08.2026).
        // Осталась «сцена» — то, что сейчас открыто, чем бы оно ни было.
        right.add(Ui2.textButton("только сцена",
            "Убрать полосы игроков и ленту времени — останется только то, что "
            + "открыто сейчас: поле, планшеты или итоги (F11).",
            () -> setFieldOnly(!fieldOnly)));
        bar.add(right);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(bar, BorderLayout.NORTH);
        setup.setVisible(false);
        wrap.add(setup, BorderLayout.CENTER);
        return wrap;
    }

    private void setSetupOpen(boolean open) {
        setup.setVisible(open);
        setupButton.setText(open ? "свернуть настройки" : setup.summary());
        frame.getContentPane().revalidate();
        SwingUtilities.invokeLater(this::layoutStage);
    }

    // ==================== центр ====================
    private JComponent centre() {
        stageCards = new CardLayout();
        stage = new JPanel(stageCards);
        bgSurface(stage);
        stage.add(field, "field");
        stage.add(scrolled(boards), "boards");
        stage.add(scrolled(supers), "supers");
        stage.add(scrolled(results), "results");

        // ВКЛАДКИ СЦЕНЫ ВСЕГДА НА ВИДУ. Раньше планшеты и итоги открывались только
        // из меню, а обратно к полю вело лишь Esc — человек оставался на экране
        // итогов без видимого выхода (замечание дизайнера 13.08.2026).
        JPanel stageBox = new JPanel(new BorderLayout());
        bgSurface(stageBox);
        stageBox.add(stageTabs(), BorderLayout.NORTH);
        stageBox.add(stage, BorderLayout.CENTER);

        drawerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, stageBox, drawer);
        drawerSplit.setResizeWeight(1.0);      // весь прирост окна — полю
        drawerSplit.setContinuousLayout(true);
        drawerSplit.setBorder(null);
        drawerSplit.setDividerSize(Theme.px(6));
        drawer.setVisible(false);
        drawerSplit.setDividerSize(0);
        drawerSplit.setDividerLocation(1.0);
        return drawerSplit;
    }

    /** Названия вкладок сцены и их карточки. */
    private static final String[][] STAGES = {
        {"field", "Поле"},
        {"boards", "Наука и рынок"},
        {"supers", "Супер-задания"},
        {"results", "Итоги партии"},
    };

    private final java.util.Map<String, javax.swing.JToggleButton> stageButtons =
        new java.util.HashMap<>();

    private JPanel stageTabs() {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(4) + " " + Theme.px(8) + " " + Theme.px(4) + " "
                + Theme.px(8) + ", gapx " + Theme.px(3)));
        panelSurface(p);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (String[] s : STAGES) {
            javax.swing.JToggleButton b = new javax.swing.JToggleButton(s[1]);
            // Вкладки сцены — крупнее прочего мелкого текста: это главная
            // навигация окна, и её читают мельком (замечание 14.08.2026).
            b.setFont(Theme.font(14, Font.PLAIN));
            b.setFocusable(false);
            b.setSelected("field".equals(s[0]));
            b.addActionListener(e -> showStage(s[0]));
            group.add(b);
            stageButtons.put(s[0], b);
            p.add(b);
        }
        return p;
    }

    private static JScrollPane scrolled(JComponent view) {
        JScrollPane sc = new JScrollPane(view);
        sc.setBorder(null);
        // Прокрутка на СЦЕНЕ — прозрачная: фон здесь принадлежит сцене, а не панели
        sc.setOpaque(false);
        sc.getViewport().setOpaque(false);
        sc.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        sc.getHorizontalScrollBar().setUnitIncrement(Theme.px(24));
        return sc;
    }

    // ==================== низ ====================
    private JPanel bottom() {
        JPanel p = new JPanel(new BorderLayout());
        bgSurface(p);

        stripsRow = new JPanel(new java.awt.GridLayout(1, 4, Theme.px(6), 0));
        stripsRow.setOpaque(false);
        stripsRow.setBorder(BorderFactory.createEmptyBorder(Theme.px(4), Theme.px(6),
            Theme.px(4), Theme.px(6)));
        for (int i = 0; i < 4; i++) {
            strips[i] = new PlayerStrip(session, i);
            strips[i].setOnTile(this::onTile);
            stripsRow.add(strips[i]);
        }
        // ГОРИЗОНТАЛЬНАЯ ПРОКРУТКА ВМЕСТО ОБРЕЗАНИЯ. У полосы игрока есть свой
        // минимум ширины (PlayerStrip.getMinimumSize) — с ним текст не мельчает
        // до нечитаемого. На узком окне ПРИ КРУПНОМ масштабе интерфейса (130 %
        // и уже на обычном ноутбучном 1366×768) четыре полосы в GridLayout не
        // помещались, и последняя просто обрывалась за краем окна без единого
        // способа её увидеть — ни прокрутки, ни сжатия (найдено ревью читаемости
        // 14.08.2026). GridLayout сам никогда не сжимается ниже суммы минимумов,
        // поэтому лишнее теперь уезжает в прокрутку, а не пропадает.
        stripsScroll = new JScrollPane(stripsRow,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        stripsScroll.setBorder(null);
        stripsScroll.setOpaque(false);
        stripsScroll.getViewport().setOpaque(false);
        // ТОНКАЯ ПОЛОСА, КАК У ЛЕНТЫ НАСТРОЕК: конвейер полос — не орган
        // управления, а способ дотянуться до полосы, которая не влезла.
        Ui2.thinHorizontalBar(stripsScroll, 6);
        // ОКНО ДОЛЖНО СЖИМАТЬСЯ УЖЕ ОДНОЙ ПОЛОСЫ (просьба дизайнера 15.08.2026).
        // JScrollPane по умолчанию берёт минимум у своего содержимого, а у ряда
        // из GridLayout минимум — это сумма минимумов всех полос. Из-за этого
        // конвейер держал всё окно широким: прокрутка была, но воспользоваться
        // ею было нельзя, окно просто не давало себя сузить до её появления.
        // Свой минимум разрывает эту связь: ряд внутри остаётся какой есть и
        // уезжает в прокрутку, а окно сжимается дальше.
        stripsScroll.setMinimumSize(new Dimension(Theme.px(120),
            Theme.px(Theme.H_STRIP_TIGHT) + Theme.px(14)));
        p.add(stripsScroll, BorderLayout.NORTH);

        JPanel deck = panelSurface(new JPanel(new BorderLayout()));
        deck.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()));
        deck.add(timeline, BorderLayout.CENTER);
        deck.add(transport, BorderLayout.SOUTH);
        p.add(deck, BorderLayout.CENTER);
        deckPanel = deck;

        status.setFont(Theme.font(11, Font.PLAIN));
        status.setForeground(Theme.ink3());
        status.setBorder(BorderFactory.createEmptyBorder(Theme.px(3), Theme.px(10),
            Theme.px(3), Theme.px(10)));
        p.add(status, BorderLayout.SOUTH);
        return p;
    }

    /** Щелчок по плитке показателя или по полосе игрока. */
    private void onTile(int seat, String metric) {
        if (metric == null) {
            drawer.showPlayer(seat);
            setDrawer(true);
            return;
        }
        Chart.Metric m = switch (metric) {
            case "kelium" -> Chart.Metric.KELIUM;
            case "coin" -> Chart.Metric.COIN;
            case "debris" -> Chart.Metric.DEBRIS;
            case "vp" -> Chart.Metric.VP;
            default -> null;
        };
        if (m == null) {
            drawer.showPlayer(seat);
        } else {
            drawer.show(Drawer.View.CHART);
            SwingUtilities.invokeLater(() -> drawer.repaint());
        }
        setDrawer(true);
    }

    // ==================== меню ====================
    private JMenuBar menuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("Файл");
        file.add(item("Открыть запись…", "control O", this::openRecord));
        file.add(item("Сохранить запись…", "control S", this::saveRecord));
        file.addSeparator();
        file.add(item("Сохранить картинку поля (2000 точек)…", null, this::savePng));
        file.add(item("Переиграть эту партию заново", null, this::startGame));
        file.addSeparator();
        // НАСТРОЙКИ ПРИЛОЖЕНИЯ — здесь, а не в «Виде»: это про папки и данные, а
        // не про облик. Туда же ведёт проверка данных, когда что-то не нашлось.
        file.add(item("Настройки приложения…", "control P", this::showSettings));
        file.add(item("Пересобрать заготовки текстур…", null, this::rebuildTextureStubs));
        file.addSeparator();
        file.add(item("Выход", null, () -> frame.dispose()));
        bar.add(file);

        JMenu view = new JMenu("Вид");
        view.add(item("Крупнее", "control EQUALS", () -> field.zoomBy(1.2)));
        view.add(item("Мельче", "control MINUS", () -> field.zoomBy(1 / 1.2)));
        view.add(item("Вписать поле", "F", field::fitToWindow));
        view.addSeparator();
        view.add(scaleMenu());
        view.addSeparator();
        view.add(item("Только сцена — без полос и ленты", "F11",
            () -> setFieldOnly(!fieldOnly)));
        view.add(item("Светлая или тёмная тема", null, this::toggleTheme));
        view.addSeparator();
        JMenu layers = new JMenu("Слои поля");
        int key = 1;
        for (SceneField.Layer l : SceneField.Layer.values()) {
            JCheckBoxMenuItem mi = new JCheckBoxMenuItem("Показывать " + l.label,
                l.byDefault);
            mi.setAccelerator(KeyStroke.getKeyStroke(String.valueOf(key++)));
            mi.addActionListener(e -> field.setLayer(l, mi.isSelected()));
            layers.add(mi);
        }
        layers.addSeparator();
        JCheckBoxMenuItem focus = new JCheckBoxMenuItem(
            "Пригашать всё, кроме участников события", true);
        focus.addActionListener(e -> field.setFocusMode(focus.isSelected()));
        layers.add(focus);
        JCheckBoxMenuItem title = new JCheckBoxMenuItem("Титр события в углу поля", true);
        title.addActionListener(e -> field.setShowTitle(title.isSelected()));
        layers.add(title);
        view.add(layers);
        bar.add(view);

        JMenu study = new JMenu("Разбор");
        study.add(item("Лог", "L", () -> openDrawer(Drawer.View.LOG)));
        study.add(item("График партии", "G", () -> openDrawer(Drawer.View.CHART)));
        study.add(item("Планшеты науки и рынка", "S", () -> showStage("boards")));
        study.add(item("Супер-задания", null, () -> showStage("supers")));
        study.add(item("Итоги партии", "T", () -> showStage("results")));
        study.addSeparator();
        study.add(item("Журнал странностей", "control J",
            () -> openDrawer(Drawer.View.ODD)));
        study.add(item("Поворотные моменты", null, () -> openDrawer(Drawer.View.MOMENTS)));
        study.add(item("Что было на этом гексе", null, () -> openDrawer(Drawer.View.HEX)));
        study.addSeparator();
        study.add(item("Поставить или снять закладку", "M", this::toggleBookmark));
        JCheckBoxMenuItem spoiler = new JCheckBoxMenuItem("Не показывать итог заранее",
            prefs.getBoolean("spoilerFree", false));
        spoiler.addActionListener(e -> {
            spoilerFree = spoiler.isSelected();
            prefs.putBoolean("spoilerFree", spoilerFree);
            refreshTitle();
        });
        spoilerFree = spoiler.isSelected();
        study.add(spoiler);
        bar.add(study);

        JMenu help = new JMenu("Справка");
        // СПРАВОЧНИК — на F1: это главный вход в справку. «Как пользоваться»
        // остаётся коротким листком про клавиши и никуда не девается.
        help.add(item("Справочник", "F1", this::showBook));
        help.add(item("Как пользоваться", null, this::showHelp));
        help.add(item("О приложении", null, this::showAbout));
        bar.add(help);
        return bar;
    }

    // ==================== масштаб интерфейса ====================
    /**
     * Ступени масштаба словами. 0 — «как подойдёт экрану»: приложение смотрит на
     * размер рабочего стола и подбирает само.
     */
    private static final double[] SCALE_STEPS = {0, 0.75, 0.85, 1.00, 1.15, 1.30};
    private static final String[] SCALE_WORDS = {
        "Авто — под экран", "Очень мелкий", "Мелкий", "Обычный", "Крупный",
        "Очень крупный",
    };

    private JMenu scaleMenu() {
        JMenu m = new JMenu("Масштаб интерфейса");
        javax.swing.ButtonGroup g = new javax.swing.ButtonGroup();
        double now = Theme.userScale();
        boolean known = false;
        for (int i = 0; i < SCALE_STEPS.length; i++) {
            double v = SCALE_STEPS[i];
            String pct = v == 0
                ? "  ·  сейчас " + Math.round(Theme.autoScaleValue() * 100) + " %"
                : "  ·  " + Math.round(v * 100) + " %";
            javax.swing.JRadioButtonMenuItem mi =
                new javax.swing.JRadioButtonMenuItem(SCALE_WORDS[i] + pct);
            if (Math.abs(now - v) < 0.005) {
                mi.setSelected(true);
                known = true;
            }
            mi.addActionListener(e -> applyScale(v));
            g.add(mi);
            m.add(mi);
        }
        m.addSeparator();
        // Свой процент — отдельным пунктом, и он же отмечен, когда выбранное число
        // не совпало ни с одной ступенью.
        javax.swing.JRadioButtonMenuItem own = new javax.swing.JRadioButtonMenuItem(
            "Свой…" + (known ? "" : "  ·  " + Math.round(now * 100) + " %"));
        own.setSelected(!known);
        own.addActionListener(e -> askScale());
        g.add(own);
        m.add(own);
        m.addSeparator();
        // ВАЖНО: Ctrl+= и Ctrl+− уже заняты масштабом ПОЛЯ. Интерфейсу — Ctrl+Shift,
        // иначе одно сочетание делало бы два разных дела.
        m.add(item("Крупнее интерфейс", "control shift EQUALS", () -> applyScale(nudge(+1))));
        m.add(item("Мельче интерфейс", "control shift MINUS", () -> applyScale(nudge(-1))));
        return m;
    }

    /** Шаг «крупнее/мельче» — 5 % от того, что действует сейчас. */
    private double nudge(int dir) {
        return Math.max(0.6, Math.min(2.0,
            Math.round((Theme.effectiveScale() + dir * 0.05) * 100) / 100.0));
    }

    private void askScale() {
        Double v = ScaleDialog.show(frame, Theme.effectiveScale());
        if (v != null) {
            applyScale(v);
        }
    }

    private void applyScale(double value) {
        if (Math.abs(Theme.userScale() - value) < 0.005) {
            return;
        }
        prefs.putDouble(Theme.SCALE_KEY, value);
        Theme.setUserScale(value);
        rebuildForScale();
    }

    /**
     * ПЕРЕСОБРАТЬ ОКНО ПОД НОВЫЙ МАСШТАБ.
     *
     * <p>Размеры запечены в компоненты при создании: {@code Theme.px()} зовут
     * конструкторы, строки раскладки MigLayout и {@code setPreferredSize}. Менять
     * их на лету нечем — окно собирается заново. Партию и место в ней при этом
     * терять нельзя, поэтому запись переносится в новое окно.
     *
     * <p>Новое окно создаётся ДО закрытия старого: между ними не должно быть
     * мгновения без живых окон.
     */
    private void rebuildForScale() {
        ReplayRecord rec = session.record();
        int cursor = session.cursor();
        // Запомненный размер окна — в экранных пикселях, а масштаб только что
        // изменился: без пересчёта окно осталось бы прежней ширины с содержимым
        // другого размера.
        double k = Theme.effectiveScale() / Math.max(0.01, scaleOfWindow);
        prefs.putInt("winW", (int) Math.round(frame.getWidth() * k));
        prefs.putInt("winH", (int) Math.round(frame.getHeight() * k));

        Theme.apply(Theme.isDark());       // defaultFont и прочие токены считаны через px()
        HelpWindow.closeIfOpen();          // справочник собран в старом масштабе
        JFrame old = frame;
        Replay2Gui gui = new Replay2Gui();
        gui.show();
        if (rec != null) {
            gui.session.setRecord(rec);
            gui.loadRules(rec);
            gui.session.seek(cursor);
        }
        old.dispose();
        gui.say("Масштаб интерфейса — " + Math.round(Theme.effectiveScale() * 100) + " %"
            + (Theme.userScale() == 0 ? " (подобран под экран)." : "."));
    }

    /** Масштаб, при котором собрано ЭТО окно, — от него считается пересчёт размера. */
    private final double scaleOfWindow = Theme.effectiveScale();

    // ==================== настройки приложения ====================
    private void showSettings() {
        SettingsDialog.Result r = SettingsDialog.show(frame, prefs);
        if (r.dataChanged()) {
            // Папка данных сменилась: прежние правила и карты прочитаны из старой,
            // и держать их дальше значит показывать не то, что лежит на диске.
            kelium.dataio.GameConfig.clearCache();
            setup.reloadEverything();
            preview();
        }
        if (r.scaleChanged()) {
            rebuildForScale();
        }
        // Папки раскладок могли поменяться отдельно от папки данных — список
        // «поле:» должен обновиться сразу при закрытии окна настроек, а не
        // ждать, пока пользователь случайно тронет что-то ещё (баг найден
        // дизайнером 14.08.2026). Пропускаем, если reloadEverything() уже
        // перечитал всё целиком — двойная работа ни к чему.
        if (r.foldersChanged() && !r.dataChanged()) {
            setup.reloadFields();
        }
    }

    /**
     * ПЕРЕСОБРАТЬ ЗАГОТОВКИ ТЕКСТУР — на случай, если папка потерялась целиком
     * или отдельные файлы удалили. Кладёт голые заготовки и заново считает их
     * отпечатки, чтобы приложение опять узнавало «эту картинку ещё не рисовали».
     *
     * <p>СПРАШИВАЕТ ПЕРЕД РАБОТОЙ, и не из вежливости: в папке лежит авторская
     * графика, и пересборка её касается. По умолчанию нарисованное сохраняется —
     * восстановить его было бы неоткуда, — а затирание предлагается отдельным,
     * явно названным выбором.
     */
    private void rebuildTextureStubs() {
        java.nio.file.Path folder = kelium.dataio.GameConfig.resolveDataRoot(null)
            .resolve("textures");
        Object[] options = {"Только недостающие", "Сбросить ВСЁ", "Отмена"};
        int choice = javax.swing.JOptionPane.showOptionDialog(frame,
            "<html><b>Пересобрать заготовки текстур?</b><br><br>"
            + "Папка: <code>" + folder + "</code><br><br>"
            + "<b>Только недостающие</b> — положить заготовки там, где файлов нет,<br>"
            + "и обновить отпечатки. Нарисованные текстуры останутся нетронутыми.<br><br>"
            + "<b>Сбросить ВСЁ</b> — вернуть голые заготовки поверх всего, <b>включая<br>"
            + "нарисованные</b>. Восстановить их после этого будет неоткуда.</html>",
            "Заготовки текстур", javax.swing.JOptionPane.DEFAULT_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice != 0 && choice != 1) {
            return;
        }
        boolean force = choice == 1;
        if (force && javax.swing.JOptionPane.showConfirmDialog(frame,
                "Нарисованные текстуры будут заменены пустыми заготовками.\n"
                + "Это необратимо. Точно сбросить всё?",
                "Сбросить всё?", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE)
                != javax.swing.JOptionPane.YES_OPTION) {
            return;
        }
        try {
            kelium.report.TextureStubs.Result r =
                kelium.report.TextureStubs.generate(folder, force);
            field.repaint();
            String text = "Заготовки пересобраны.\n\n"
                + "создано заново: " + r.created() + "\n"
                + "обновлено пустых: " + r.restored() + "\n"
                + (force
                    ? "затёрто нарисованных: " + r.replaced() + "\n"
                    : "сохранено нарисованных: " + r.kept() + "\n")
                + "\nпапка: " + r.folder();
            javax.swing.JOptionPane.showMessageDialog(frame, text,
                "Заготовки текстур", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            say("Заготовки текстур пересобраны: " + r.total() + " файлов.");
        } catch (java.io.IOException e) {
            javax.swing.JOptionPane.showMessageDialog(frame,
                "Не удалось пересобрать заготовки:\n" + e.getMessage(),
                "Заготовки текстур", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private JMenuItem item(String text, String accel, Runnable action) {
        JMenuItem mi = new JMenuItem(text);
        if (accel != null) {
            mi.setAccelerator(KeyStroke.getKeyStroke(accel));
        }
        mi.addActionListener(e -> action.run());
        return mi;
    }

    // ==================== клавиши ====================
    private void bindKeys() {
        JComponent root = (JComponent) frame.getContentPane();
        bind(root, "SPACE", () -> transport.togglePlay());
        bind(root, "LEFT", () -> {
            transport.stop();
            session.stepBy(-1);
        });
        bind(root, "RIGHT", () -> {
            transport.stop();
            session.stepBy(+1);
        });
        bind(root, "HOME", () -> {
            transport.stop();
            session.seek(0);
        });
        bind(root, "END", () -> {
            transport.stop();
            session.seekEnd();
        });
        bind(root, "PAGE_UP", () -> session.jumpRound(-1));
        bind(root, "PAGE_DOWN", () -> session.jumpRound(+1));
        bind(root, "B", () -> {
            if (!session.jumpBattle(+1)) {
                say("Дальше боёв нет.");
            }
        });
        bind(root, "shift B", () -> {
            if (!session.jumpBattle(-1)) {
                say("Раньше боёв не было.");
            }
        });
        bind(root, "N", () -> session.jumpSameSeatTurn(+1));
        bind(root, "shift N", () -> session.jumpSameSeatTurn(-1));
        bind(root, "ESCAPE", () -> {
            if (!"field".equals(currentCard)) {
                showStage("field");
            } else {
                setDrawer(false);
            }
        });
    }

    private void bind(JComponent root, String key, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(key), key);
        root.getActionMap().put(key, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ==================== ящик и сцена ====================
    private String currentCard = "field";

    private void openDrawer(Drawer.View v) {
        if (drawerOpen && drawer.view() == v) {
            setDrawer(false);
            return;
        }
        drawer.show(v);
        setDrawer(true);
    }

    private void setDrawer(boolean open) {
        drawerOpen = open;
        drawer.setVisible(open);
        // Ящик закрыт — разделителя нет вовсе: иначе у правого края висит полоска,
        // которая ничего не делит и только мешает.
        drawerSplit.setDividerSize(open ? Theme.px(6) : 0);
        if (open) {
            int w = prefs.getInt("drawerWidth", Theme.px(430));
            drawerSplit.setDividerLocation(Math.max(Theme.px(Theme.FIELD_MIN_W),
                drawerSplit.getWidth() - w));
        } else {
            if (drawerSplit.getWidth() > 0) {
                prefs.putInt("drawerWidth",
                    Math.max(Theme.px(280), drawerSplit.getWidth()
                        - drawerSplit.getDividerLocation()));
            }
            drawerSplit.setDividerLocation(1.0);
        }
        drawerSplit.revalidate();
    }

    private void showStage(String card) {
        currentCard = card;
        stageCards.show(stage, card);
        javax.swing.JToggleButton b = stageButtons.get(card);
        if (b != null) {
            b.setSelected(true);      // вкладка подсвечена и когда её открыли клавишей
        }
        if ("boards".equals(card) || "supers".equals(card)) {
            ReplayRecord.Frame f = session.frame();
            if (f != null) {
                boards.show(session.record(), f.snapshot);
                supers.show(session.record(), f.snapshot);
            }
            say("Планшет открыт. Esc — вернуться к полю.");
        } else if ("results".equals(card)) {
            say("Итоги партии. Esc — вернуться к полю.");
        }
    }

    private void setFieldOnly(boolean only) {
        fieldOnly = only;
        stripsScroll.setVisible(!only);
        timeline.setVisible(!only);
        if (only) {
            setDrawer(false);
            setup.setVisible(false);
        }
        frame.getContentPane().revalidate();
        say(only ? "Только сцена: полосы игроков и лента убраны. F11 — вернуть."
                 : "Обычный вид: полосы игроков и лента на месте.");
    }

    /**
     * ПЕРЕКЛЮЧЕНИЕ ТЕМЫ ЦЕЛИКОМ.
     *
     * <p>Раньше вызывался только {@code updateComponentTreeUI}, и получалась
     * полуперекрашенная программа: строка заголовка с меню оставалась цветов старой
     * темы (её рисует сама FlatLaf, а не наши панели), а наши подложки, заданные
     * при сборке окна, сохраняли прежнюю краску. Отсюда «тёмная тема в светлом
     * режиме Windows и светлая в тёмном ломаются» — жалоба дизайнера 13.08.2026.
     *
     * <p>Теперь: {@code FlatLaf.updateUI()} обновляет ВСЕ окна вместе с их
     * заголовками, а {@link #restyle()} заново красит наши собственные подложки и
     * рамки — их цвет в Swing запечён в компонент и сам не меняется.
     */
    private void toggleTheme() {
        setDarkTheme(!Theme.isDark());
    }

    private void setDarkTheme(boolean dark) {
        prefs.putBoolean("dark", dark);
        Theme.apply(dark);
        com.formdev.flatlaf.FlatLaf.updateUI();
        restyle();
        frame.repaint();
        say(dark ? "Тёмная тема." : "Светлая тема — в ней снимают картинки для правил.");
    }

    /** Наши подложки и рамки: цвет задаётся кодом, поэтому меняем его руками. */
    private void restyle() {
        frame.getContentPane().setBackground(Theme.bg());
        for (JComponent c : panelSurfaces) {
            c.setBackground(Theme.panel());
        }
        for (JComponent c : bgSurfaces) {
            c.setBackground(Theme.bg());
        }
        if (topBarPanel != null) {
            topBarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        }
        if (deckPanel != null) {
            deckPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()));
        }
        if (drawer != null) {
            drawer.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.border()));
        }
        if (setup != null) {
            setup.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        }
        context.setForeground(Theme.ink());
        status.setForeground(Theme.ink3());
        // Справочник — отдельное окно, и его цвета вписаны в статьи: перекрашиваем
        // его вместе с главным, иначе он останется в прежней теме.
        HelpWindow.restyle();
        // Цвета надписей, выставленные руками, тема сама не меняет — проходим по
        // дереву и красим по роли, помеченной на компоненте.
        restyleText(frame.getContentPane());
        // ОБЩАЯ ПЕРЕКРАСКА ПО ПАЛИТРЕ. Список поверхностей выше приходится
        // пополнять руками, и его всегда забывают: у дизайнера 14.08.2026 в
        // прежней теме оставались полоса прокрутки и экраны карт супер-заданий
        // и рынка. Здесь любая краска ИЗ НАШЕЙ ПАЛИТРЫ, оставшаяся от прошлой
        // темы, меняется на равную ей по смыслу — независимо от того, кто её
        // выставил и вспомнил ли автор про список.
        Theme.restyleTree(frame.getContentPane());
        for (java.awt.Window w : frame.getOwnedWindows()) {
            Theme.restyleTree(w);
        }
        frame.getContentPane().revalidate();
    }

    private static void restyleText(java.awt.Component c) {
        if (c instanceof JComponent jc) {
            Object role = jc.getClientProperty(Ui2.FG_ROLE);
            if (role != null) {
                jc.setForeground(Ui2.colourOf(String.valueOf(role)));
            }
        }
        // ПОВЕРХНОСТИ ЛОГА И ПЛАНШЕТА. Дерево лога, списки и текстовые панели
        // держат свой фон в самом компоненте, и смена темы его не трогает: на
        // светлой теме оставался чёрный фон лога, на тёмной — белый (жалоба
        // дизайнера 14.08.2026). Красим их по дереву, вместе с ролями текста.
        if (c instanceof javax.swing.JTree || c instanceof javax.swing.JList
                || c instanceof javax.swing.text.JTextComponent
                || c instanceof javax.swing.JViewport) {
            c.setBackground(Theme.panel());
            c.setForeground(Theme.ink());
        }
        if (c instanceof java.awt.Container box) {
            for (java.awt.Component ch : box.getComponents()) {
                restyleText(ch);
            }
        }
    }

    /** Запомнить подложку, чтобы перекрасить её при смене темы. */
    private <T extends JComponent> T panelSurface(T c) {
        c.setBackground(Theme.panel());
        c.setOpaque(true);
        panelSurfaces.add(c);
        return c;
    }

    private <T extends JComponent> T bgSurface(T c) {
        c.setBackground(Theme.bg());
        c.setOpaque(true);
        bgSurfaces.add(c);
        return c;
    }

    private void toggleBookmark() {
        boolean on = session.toggleBookmark();
        say(on ? "Закладка на шаге " + (session.cursor() + 1) + " поставлена."
               : "Закладка снята.");
        timeline.repaint();
    }

    // ==================== обновление ====================
    private void layoutStage() {
        if (drawerOpen) {
            setDrawer(true);
        } else {
            drawerSplit.setDividerLocation(1.0);
        }
    }

    private void rebuildStrips() {
        int players = session.record() == null ? 4 : session.record().players;
        stripsRow.removeAll();
        stripsRow.setLayout(new java.awt.GridLayout(1, players, Theme.px(6), 0));
        for (int i = 0; i < players; i++) {
            stripsRow.add(strips[i]);
        }
        stripsRow.revalidate();
        stripsRow.repaint();
    }

    private void refreshContext() {
        context.setText(session.contextLine());
        ReplayRecord.Thought t = session.thought();
        if (t == null) {
            thought.setText("");
        } else {
            thought.setText("«" + t.text + "»");
            thought.setForeground(Theme.seatInk(t.seat));
        }
        if ("boards".equals(currentCard) || "supers".equals(currentCard)) {
            ReplayRecord.Frame f = session.frame();
            if (f != null) {
                boards.show(session.record(), f.snapshot);
                supers.show(session.record(), f.snapshot);
            }
        }
    }

    private void refreshTitle() {
        ReplayRecord rec = session.record();
        if (rec == null) {
            frame.setTitle("Кристаллы Раздора — разбор партии");
            return;
        }
        StringBuilder sb = new StringBuilder("Кристаллы Раздора — разбор партии   ·   ");
        sb.append(rec.players).append(" игрока, сид ").append(rec.seed);
        if (rec.scenarioId != null) {
            sb.append(", поле ").append(rec.scenarioId);
        }
        // ИТОГ В ЗАГОЛОВКЕ — это спойлер. По просьбе его можно выключить.
        if (rec.winner != null && !spoilerFree) {
            sb.append("   ·   победил ").append(rec.playerName(rec.winner))
              .append(" (").append(Names.condition(rec.condition)).append(')');
        }
        frame.setTitle(sb.toString());
    }

    private void say(String text) {
        status.setText(text);
    }

    // ==================== партия ====================
    /** Показать стартовую расстановку с текущими настройками (партия не играется). */
    private void preview() {
        try {
            ReplayRecord rec = setup.buildPreview();
            session.setRecord(rec);
            loadRules(rec);
            say("Показана расстановка. Меняй настройки — поле обновляется сразу.");
        } catch (NumberFormatException e) {
            say("Сид должен быть целым числом.");
        } catch (RuntimeException e) {
            // Не «не получилось», а КУДА ИДТИ: чаще всего расстановка не собирается
            // из-за пропавшего файла данных, и починка живёт в окне настроек.
            say("Расстановку не собрать: " + human(e)
                + "  —  Ctrl+P, вкладка «Проверка данных».");
        }
    }

    private void startGame() {
        transport.stop();
        setup.setBusy(true);
        say("Играю партию…");
        new SwingWorker<ReplayRecord, String>() {
            @Override
            protected ReplayRecord doInBackground() {
                return setup.play(this::publish);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String c : chunks) {
                    say(c);
                }
            }

            @Override
            protected void done() {
                setup.setBusy(false);
                try {
                    ReplayRecord rec = get();
                    session.setRecord(rec);
                    loadRules(rec);
                    lastFile = null;
                    // Настройки сворачиваем: место нужно полю, а не форме
                    setSetupOpen(false);
                    say("Готово. Шагов: " + rec.frames.size() + ". "
                        + (spoilerFree ? "Итог спрятан — открой «Разбор → Итоги»."
                            : "Победил " + (rec.winner == null ? "никто"
                                : rec.playerName(rec.winner)))
                        + ". Пробел — играть, B — к бою, L — лог.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    say("Партия не сыграна: " + human(cause));
                    JOptionPane.showMessageDialog(frame,
                        Ui2.tip("Не получилось сыграть партию.\n\n" + human(cause)),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Правила и карточные наборы той версии, в которой сыграна партия. */
    private void loadRules(ReplayRecord rec) {
        try {
            var cfg = kelium.dataio.GameConfig.buildCached(
                rec.ruleset == null || rec.ruleset.isBlank()
                    ? kelium.dataio.GameConfig.DEFAULT_RULESET : rec.ruleset,
                Math.max(2, rec.players), 0L, null, null);
            boards.setRules(cfg.ruleset, cfg.content);
            supers.setContent(cfg.content);
            session.setContent(cfg.content);
        } catch (RuntimeException e) {
            // Не загрузилось — панели честно напишут «не задано», а не выдумают числа
            boards.setRules(null, null);
            supers.setContent(null);
            session.setContent(null);
        }
    }

    // ==================== файлы ====================
    private void openRecord() {
        Path start = lastFile != null ? lastFile : defaultDir();
        Path file = PathDialog.choose(frame, "Открыть запись партии", start, false, "json");
        if (file != null) {
            openFile(file);
        }
    }

    private void openFile(Path file) {
        transport.stop();
        say("Читаю запись " + file.getFileName() + "…");
        new SwingWorker<ReplayRecord, Void>() {
            @Override
            protected ReplayRecord doInBackground() throws Exception {
                return ReplayRecord.load(file);
            }

            @Override
            protected void done() {
                try {
                    ReplayRecord rec = get();
                    session.setRecord(rec);
                    loadRules(rec);
                    lastFile = file;
                    say("Открыта запись: " + file.getFileName()
                        + " (шагов " + rec.frames.size() + ").");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(frame,
                        Ui2.tip("Не удалось открыть запись.\n\n" + human(cause)),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                    say("Запись не открыта: " + human(cause));
                }
            }
        }.execute();
    }

    private void saveRecord() {
        if (!session.hasRecord()) {
            say("Сохранять пока нечего: партия не сыграна и не открыта.");
            return;
        }
        ReplayRecord rec = session.record();
        Path start = lastFile != null ? lastFile
            : defaultDir().resolve("partiya-" + rec.players + "p-seed" + rec.seed + ".json");
        Path file = PathDialog.choose(frame, "Сохранить запись партии", start, true, "json");
        if (file == null) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                rec.save(file);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    lastFile = file;
                    say("Запись сохранена: " + file);
                } catch (Exception e) {
                    say("Не удалось сохранить: " + human(e));
                }
            }
        }.execute();
    }

    /**
     * КАРТИНКА ПОЛЯ ФИКСИРОВАННОЙ ШИРИНЫ. В 1.0 снимок делался размером с окно,
     * поэтому качество зависело от того, как окно растянуто, — для вставки в правила
     * это не годится.
     */
    private void savePng() {
        if (!session.hasRecord()) {
            say("Сохранять нечего: партия не сыграна и не открыта.");
            return;
        }
        Path start = defaultDir().resolve("kadr-" + (session.cursor() + 1) + ".png");
        Path file = PathDialog.choose(frame, "Сохранить картинку поля", start, true, "png");
        if (file == null) {
            return;
        }
        int w = 2000;
        int h = 1400;
        Dimension was = field.getSize();
        try {
            field.setSize(w, h);
            field.fitToWindow();
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            field.paint(g);
            g.dispose();
            javax.imageio.ImageIO.write(img, "png", file.toFile());
            say("Картинка сохранена (2000 точек): " + file);
        } catch (IOException e) {
            say("Не удалось сохранить картинку: " + human(e));
        } finally {
            field.setSize(was);
            field.fitToWindow();
        }
    }

    private static Path defaultDir() {
        Path dir = Paths.get(System.getProperty("user.home"), "Кристаллы Раздора",
            "Записи партий");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (IOException e) {
            return Paths.get(System.getProperty("user.home"));
        }
        return dir;
    }

    // ==================== окно между запусками ====================
    private void restoreWindow() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        // МИНИМУМ ОКНА ЗАМЕТНО НИЖЕ ЖЕЛАЕМОГО РАЗМЕРА (просьба дизайнера
        // 15.08.2026: «чтобы можно было уменьшать окно ещё сильнее»). Всё, что
        // не влезает по ширине, уезжает в свои горизонтальные конвейеры — лента
        // настроек и ряд полос игроков, — а не обрезается за краем. Поэтому
        // держать окно широким больше незачем.
        int minW = Theme.px(760);
        int minH = Theme.px(560);
        frame.setMinimumSize(new Dimension(minW, minH));
        // РАЗМЕР И МЕСТО ЗАЖИМАЕМ В ЭКРАН. Запомненные значения могли остаться от
        // другого монитора или от окна шире экрана — тогда содержимое уезжало за
        // левый край и обрезалось (жалоба дизайнера 13.08.2026).
        int w = Math.min(screen.width, Math.max(minW,
            prefs.getInt("winW", Math.min(Theme.px(1500), screen.width - 40))));
        int h = Math.min(screen.height, Math.max(minH,
            prefs.getInt("winH", Math.min(Theme.px(980), screen.height - 40))));
        frame.setSize(w, h);
        int x = prefs.getInt("winX", Integer.MIN_VALUE);
        int y = prefs.getInt("winY", Integer.MIN_VALUE);
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE) {
            frame.setLocationRelativeTo(null);
        } else {
            frame.setLocation(
                Math.max(screen.x, Math.min(x, screen.x + screen.width - w)),
                Math.max(screen.y, Math.min(y, screen.y + screen.height - h)));
        }
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                prefs.putInt("winW", frame.getWidth());
                prefs.putInt("winH", frame.getHeight());
            }

            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                prefs.putInt("winX", frame.getX());
                prefs.putInt("winY", frame.getY());
            }
        });
    }

    // ==================== справка ====================
    /** Справочник: дерево разделов и статьи с картинками ({@link HelpWindow}). */
    private void showBook() {
        HelpWindow.show(frame, session);
    }

    private void showHelp() {
        String html = """
            <html><body style='width:560px;font-family:sans-serif'>
            <h2>Разбор партии</h2>
            <p>Это короткий листок про клавиши и устройство экрана. Полный
            <b>справочник</b> — «Справка → Справочник» или <b>F1</b>: там разобран
            каждый прибор, все значки, планшеты, слои поля и каталог всех карт.</p>
            <p>Приложение показывает уже сыгранную партию <b>шаг за шагом</b>. Один шаг —
            одно событие движка: действие, удар в бою, розыгрыш задания, конец раунда.
            Партия сперва играется целиком и записывается, поэтому её можно листать и
            вперёд, и назад.</p>

            <h3>Экран</h3>
            <p><b>Поле</b> — главный и единственный крупный вид. Под ним <b>полосы
            игроков</b> (по одной на место, все одинаковые), <b>лента времени</b> и
            <b>пульт</b>. Лог, планшет игрока, графики и журналы живут в <b>ящике
            справа</b>: он приходит по вызову и уходит по Esc.</p>

            <h3>Пульт и клавиши</h3>
            <ul>
              <li><b>Пробел</b> — играть или пауза.</li>
              <li><b>← →</b> — шаг назад и вперёд. Чем именно шагать, выбирает кнопка
                  «шаг: событие / ход / круг / раунд».</li>
              <li><b>Home / End</b> — в начало и в конец.</li>
              <li><b>Page Up / Page Down</b> — к соседнему раунду.</li>
              <li><b>B</b> и <b>Shift+B</b> — к следующему и предыдущему бою.</li>
              <li><b>N</b> и <b>Shift+N</b> — к следующему и предыдущему ходу того же
                  игрока.</li>
              <li><b>M</b> — закладка на этом шаге (видна флажком на ленте).</li>
              <li><b>L</b> — лог, <b>G</b> — график, <b>S</b> — планшеты,
                  <b>T</b> — итоги, <b>Ctrl+J</b> — журнал странностей.</li>
              <li><b>F</b> — вписать поле, <b>F11</b> — только сцена,
                  <b>1…9</b> — слои поля.</li>
            </ul>
            <p><b>Скорость</b> — кнопками «−» и «+»; щелчок по самому «1×» возвращает
            обычную. На «∞» партия прокручивается без пауз: ни один шаг не
            пропускается, но экран обновляется двадцать раз в секунду, а не на каждый
            кадр.</p>
            <p><b>Автостоп</b> — список условий, на которых показ останавливается сам:
            бой, уничтожение жетона, стройка, задание, контейнер, конец раунда,
            супер-задание и неудавшееся действие.</p>

            <h3>Лента времени</h3>
            <p>Четыре дорожки: раунды, чей ход (цветом места), события (бои, стройки,
            контейнеры, задания) и «накал» партии. Наведи мышь — увидишь лупу с
            участком ±25 шагов. <b>Ctrl+колесо</b> увеличивает ленту, колесо — листает.</p>

            <h3>Поле</h3>
            <p>Колесо — масштаб, перетаскивание — сдвиг, <b>F</b> — вписать. Всё, что не
            участвует в событии этого шага, <b>пригашается</b>: видно, кто именно
            ходил, бил и строил. Дуга — перемещение, зубец — удар, кольцо — стройка,
            серый контур — уничтоженный жетон. В углу поля — <b>титр</b> с описанием
            события. <b>Щелчок по гексу</b> открывает «что здесь происходило за партию».</p>

            <h3>Полосы игроков</h3>
            <p>Крупное число — значение, мелкое рядом — предел. Если показатель
            изменился, рядом на три шага загорается <b>дельта</b> (▲+2 / ▾−1). Щелчок
            по плитке — как этот показатель менялся всю партию; щелчок по полосе —
            подробный планшет игрока со всеми зданиями, войсками и картами приказов.</p>

            <h3>Лог</h3>
            <p>Дерево: раунд → ход игрока → события. Ход показывает сводку одной
            строкой, подробности разворачиваются. Мысли ботов от первого лица стоят
            рядом со своим решением. Фильтр — один выбор из пяти плюс метки мест и
            гекса; есть поиск. Щелчок по строке перематывает партию.</p>

            <h3>Разбор</h3>
            <p><b>График партии</b> — один показатель у всех игроков по раундам; щелчок
            по графику перематывает к этому раунду. <b>Журнал странностей</b> собирает
            шаги, где действие не получилось. <b>Поворотные моменты</b> — шаги, где
            счёт менялся сильнее всего. <b>Итоги</b> показывают разбивку очков по
            источникам и кривую по раундам.</p>

            <h3>Темы</h3>
            <p>По умолчанию тёмная: поле светлое, и на тёмной раме оно читается как
            главный объект. Светлая включается кнопкой <b>◐</b> — в ней снимают
            картинки для правил («Файл → Сохранить картинку поля», всегда 2000 точек
            шириной независимо от размера окна).</p>
            </body></html>
            """;
        javax.swing.JEditorPane pane = new javax.swing.JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        JScrollPane sc = new JScrollPane(pane);
        sc.setPreferredSize(new Dimension(Theme.px(640), Theme.px(560)));
        javax.swing.JDialog d = new javax.swing.JDialog(frame, "Как пользоваться", true);
        d.add(sc);
        d.pack();
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(frame, Ui2.tip(
            "Разбор партии «Кристаллы Раздора», версия 2.0.\n\n"
            + "Показывает сыгранную партию по шагам: поле, полосы игроков, лента "
            + "времени, лог и мысли ботов.\n"
            + "Движок вызывается как библиотека — сама игра не меняется. Отрисовка "
            + "поля общая с картинками отчётов.\n\n"
            + "Версия правил по умолчанию: "
            + kelium.dataio.GameConfig.DEFAULT_RULESET, 420),
            "О приложении", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String human(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    /** Список мест для формы настроек — вынесен, чтобы не тянуть сюда GameRecorder. */
    static List<GameRecorder.SeatOption> seatOptions(int players) {
        return new ArrayList<>(GameRecorder.seatOptions(players));
    }
}
