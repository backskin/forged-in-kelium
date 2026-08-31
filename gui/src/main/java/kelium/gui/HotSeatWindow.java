package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import kelium.agents.Bots;
import kelium.core.Agent;
import kelium.core.Choice;
import kelium.core.GameState;
import kelium.core.InteractiveAgent;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.gui.kp.ActionBar;
import kelium.gui.kp.CardTile;
import kelium.gui.kp.ChipLabel;
import kelium.gui.kp.HandPanel;
import kelium.gui.kp.KpButton;
import kelium.gui.kp.KpTab;
import kelium.gui.kp.PromptOverlay;
import kelium.gui.kp.ZoomCard;
import kelium.gui.replay2.BoardSheet;
import kelium.gui.replay2.Session;
import kelium.gui.replay2.Theme;
import kelium.report.ReplayRecord;

/**
 * «КОМАНДНЫЙ ПУНКТ» — живое окно партии (hot-seat + боты) по утверждённому
 * концепту (design-docs/КОНЦЕПТ — игровой интерфейс цифровой версии).
 *
 * <p>Постоянная зона игрока (замечание дизайнера 24.08: органы управления
 * НЕ появляются и не исчезают): три руки карточками ({@link HandPanel}, при
 * наведении — увеличенная карта), панель из восьми всегда видимых плиток
 * действий ({@link ActionBar}), большая кнопка «Завершить ход». Контекстные
 * варианты точек решения — плавающая панель поверх поля ({@link PromptOverlay}),
 * гексовые решения — кликом по самому полю. Ящики поверх поля — готовые панели
 * replay2 (наука/рынок, планшет, журнал).
 *
 * <p>Запуск: {@code kelium.gui.HotSeatWindow <players> [seed] [seat0] ...},
 * место — {@code human} либо имя характера бота ({@link Bots#CHARACTERS}).
 */
public final class HotSeatWindow {

    public static void main(String[] args) {
        int players = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : new Random().nextLong();
        List<String> seatSpecs = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            seatSpecs.add(args.length > 2 + seat ? args[2 + seat]
                : (seat == 0 ? "human" : "balanced"));
        }
        SwingUtilities.invokeLater(() -> new HotSeatWindow(players, seed, seatSpecs).start());
    }

    private static final int RAIL_W = 260;
    private static final int DRAWER_W = 480;
    /** Ящики с планшетами и досками — шире: там печатные компоненты. */
    private static final int WIDE_DRAWER_W = 920;

    /**
     * НАСТРОЙКИ ПАРТИИ — всё, что решается ДО первого хода. Их собирает меню
     * запуска ({@link StartMenuWindow}); из командной строки берутся умолчания.
     *
     * <p>{@code startCoins}/{@code startKelium}/{@code startAmmo} — не null
     * только у ТРЕНИРОВОЧНОЙ партии: значения подготовки берутся из свода, и
     * если игрок их поправил, партия помечается меткой, которая идёт в журнал.
     */
    public record Options(String rulesetId, int players, long seed, List<String> seatSpecs,
                           String scenarioId, java.nio.file.Path scenarioFile,
                           List<Integer> cuFacing, List<Integer> seatColors,
                           Integer startCoins, Integer startKelium, Integer startAmmo) {

        /** Партия с правкой значений подготовки — не обычная. */
        public boolean training() {
            return startCoins != null || startKelium != null || startAmmo != null;
        }

        public static Options simple(int players, long seed, List<String> seatSpecs) {
            return new Options(GameConfig.DEFAULT_RULESET, players, seed, seatSpecs,
                null, null, null, null, null, null, null);
        }
    }

    private final Options options;
    private final int players;
    private final long seed;
    private final List<String> seatSpecs;
    final Map<Integer, kelium.core.UndoableAgent> humansBySeat = new ConcurrentHashMap<>();
    private final Session session = new Session();

    JFrame frame;
    private JLabel roundLabel;
    private JLabel turnLabel;
    private ChipLabel chipVp;
    private ChipLabel chipCoin;
    private ChipLabel chipKelium;
    private ChipLabel chipAmmo;
    private ChipLabel chipDebris;
    FieldView field;
    private BoardsPanel boards;
    private BoardSheet sheet;
    /** Кнопки выбора места в ящике «Планшет» — их приходится запирать. */
    private final Map<Integer, JToggleButton> sheetSeatBtns = new LinkedHashMap<>();
    private JScrollPane sheetScroll;

    /** Прокрутка ящика «Планшет» — нужна прогонщикам для снимков. */
    JScrollPane sheetScroll() {
        return sheetScroll;
    }
    private JLayeredPane layered;
    private final Map<String, JComponent> drawers = new LinkedHashMap<>();
    private JPanel discardBox;
    private List<String> discardShown = List.of();
    final Map<String, KpTab> drawerTabs = new LinkedHashMap<>();
    /** Кнопки ящиков в нижней зоне игрока (планшет, наука и рынок). */
    final Map<String, KpButton> drawerBtns = new LinkedHashMap<>();
    private JComponent openDrawer;
    kelium.gui.kp.TurnStepsPanel steps;
    private JLabel stepsCaption;
    /** Замороженные («запёкшиеся») шаги текущего хода — до точек отката. */
    private final List<String> lockedSteps = new ArrayList<>();
    /** Шаги хода бота (просто витрина, некликабельно). */
    private final List<String> botSteps = new ArrayList<>();
    private Integer turnSeat;
    private String pendingKind;
    /** Имя необратимого действия, выбранного кликом, — запечётся по факту. */
    private String pendingBakeName;
    private JPanel feedBox;
    private JScrollPane feedScroll;
    private JPanel journalBox;
    HandPanel hands;
    ActionBar actionBar;
    PromptOverlay prompt;
    private ZoomCard zoom;
    kelium.gui.kp.ConfirmDialog confirm;
    kelium.gui.kp.CardChoiceOverlay ceremony;
    /** Шторка передачи устройства — только когда за столом больше одного живого. */
    kelium.gui.kp.HandoverCurtain curtain;
    /** Меню карт: задания и арсенал раскладываются перед игроком. */
    kelium.gui.kp.CardMenu cardMenu;
    /** Кому в прошлый раз отдавали ход: сменился — поднимаем шторку. */
    private int lastServedHuman = -1;
    private kelium.gui.kp.OpponentStrip opponents;
    KpButton endBtn;
    KpButton objMenuBtn;
    KpButton arsMenuBtn;
    /** Выезд контекстной панели снизу (120–180 мс по скиллу интерфейса). */
    private final kelium.gui.kp.Anim promptSlide = new kelium.gui.kp.Anim();
    /** Выезд ящика слева. */
    private final kelium.gui.kp.Anim drawerSlide = new kelium.gui.kp.Anim();
    /** Подписи точек отката на прошлой перерисовке — ловим «запекание». */
    private List<String> lastAgentLabels = new ArrayList<>();
    private int viewedSeat = 0;
    /**
     * МЕСТО ЖИВОГО ИГРОКА — то, что подписано «вы»; −1, когда такого места нет.
     * Прежде эту пометку носило место, на которое СЕЙЧАС СМОТРЯТ, и в ход бота
     * «вы» переезжало на бота.
     *
     * <p>«Вы» есть, только если живой за столом ОДИН, а прочие места заняты
     * ботами. Живых несколько — компьютер просто передаёт ход каждому по
     * очереди, и который из них «вы», не значит ничего: пометки нет ни у кого.
     */
    private int mySeat = -1;
    private volatile GameConfig cfg;
    private volatile GameState liveState;
    private boolean sessionBound;
    private String lastFeedText;
    volatile ReplayRecord rec;
    /** Место, для которого сейчас реально ждём клика/кнопки — иначе null. */
    volatile Integer awaitingSeat;
    /** Окно закрыто игроком: живые обновления больше не нужны. */
    private volatile boolean stopped;
    /** Партия доиграна до конца — закрывать её можно без вопросов. */
    private volatile boolean finished;
    /**
     * ЛЕНТА ПРИНЯТЫХ РЕШЕНИЙ — из неё складывается сохранение партии. Пишется
     * на каждом решении любого места; см. {@link MoveLog}.
     */
    private final List<Integer> moves = java.util.Collections.synchronizedList(
        new ArrayList<>());
    /** Лента загруженного сохранения: её надо доиграть, прежде чем спрашивать игрока. */
    private final List<Integer> replay = new ArrayList<>();
    /** Партия ещё догоняет сохранение — окно не мешает и ничего не спрашивает. */
    private volatile boolean catchingUp;

    HotSeatWindow(int players, long seed, List<String> seatSpecs) {
        this(Options.simple(players, seed, seatSpecs));
    }

    public HotSeatWindow(Options options) {
        this.options = options;
        this.players = options.players();
        this.seed = options.seed();
        this.seatSpecs = options.seatSpecs();
    }

    /** Открыть окно партии по собранным настройкам (зовёт меню запуска). */
    public static void open(Options options) {
        SwingUtilities.invokeLater(() -> new HotSeatWindow(options).start());
    }

    /**
     * ПРОДОЛЖИТЬ СОХРАНЁННУЮ ПАРТИЮ. Она доигрывается с первого хода по ленте
     * записанных решений — быстро, не спрашивая ни игрока, ни ботов, — и на
     * месте сохранения возвращается к живой игре.
     */
    public static void open(GameSave save) {
        SwingUtilities.invokeLater(() -> {
            HotSeatWindow w = new HotSeatWindow(save.options);
            w.replay.addAll(save.moves);
            w.start();
        });
    }

    void start() {
        buildUi();
        Thread engine = new Thread(this::runGame, "hotseat-engine");
        engine.setDaemon(true);
        engine.start();
    }

    // ==================== сборка окна ====================

    private void buildUi() {
        // Светлая тема — просьба дизайнера 24.08.2026 («работай пока со светлой»).
        Theme.apply(false);
        frame = new JFrame("Кристаллы Раздора — Командный пункт");
        // Закрытие окна НЕ гасит программу: партию всегда можно закрыть и
        // вернуться в меню, а гасит программу уже само меню.
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                askClose();
            }
        });
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Theme.bg());

        frame.add(buildTopBar(), BorderLayout.NORTH);
        frame.add(buildTabStrip(), BorderLayout.WEST);
        frame.add(buildCenter(), BorderLayout.CENTER);
        frame.add(buildRail(), BorderLayout.EAST);
        frame.add(buildPlayerZone(), BorderLayout.SOUTH);

        zoom = new ZoomCard();
        zoom.setSize(Theme.px(236), Theme.px(348));
        zoom.setVisible(false);
        frame.getLayeredPane().add(zoom, JLayeredPane.POPUP_LAYER);

        // Церемония выбора карты круга/отложенного приказа — крупными лицами.
        ceremony = new kelium.gui.kp.CardChoiceOverlay();
        frame.getLayeredPane().add(ceremony, JLayeredPane.MODAL_LAYER);

        // Модальное окно необратимого — во весь слой окна, поверх всего.
        confirm = new kelium.gui.kp.ConfirmDialog();
        frame.getLayeredPane().add(confirm, JLayeredPane.MODAL_LAYER);

        // ШТОРКА ПЕРЕДАЧИ — ВЫШЕ МОДАЛОК: она прячет экран целиком, и если её
        // перекроет хоть что-нибудь, прятать будет нечего.
        cardMenu = new kelium.gui.kp.CardMenu();
        frame.getLayeredPane().add(cardMenu, JLayeredPane.MODAL_LAYER);

        curtain = new kelium.gui.kp.HandoverCurtain();
        frame.getLayeredPane().add(curtain, JLayeredPane.DRAG_LAYER);
        frame.getLayeredPane().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                confirm.setBounds(0, 0, frame.getLayeredPane().getWidth(),
                    frame.getLayeredPane().getHeight());
                ceremony.setBounds(0, 0, frame.getLayeredPane().getWidth(),
                    frame.getLayeredPane().getHeight());
                curtain.setBounds(0, 0, frame.getLayeredPane().getWidth(),
                    frame.getLayeredPane().getHeight());
                cardMenu.setBounds(0, 0, frame.getLayeredPane().getWidth(),
                    frame.getLayeredPane().getHeight());
            }
        });
        confirm.setBounds(0, 0, frame.getLayeredPane().getWidth(),
            frame.getLayeredPane().getHeight());
        curtain.setBounds(0, 0, frame.getLayeredPane().getWidth(),
            frame.getLayeredPane().getHeight());
        cardMenu.setBounds(0, 0, frame.getLayeredPane().getWidth(),
            frame.getLayeredPane().getHeight());
        ceremony.setBounds(0, 0, frame.getLayeredPane().getWidth(),
            frame.getLayeredPane().getHeight());

        frame.setSize(Theme.px(1500), Theme.px(950));
        frame.setMinimumSize(new Dimension(Theme.px(1150), Theme.px(760)));
        frame.setLocationByPlatform(true);
        Offscreen.show(frame);
    }

    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(8) + " " + Theme.px(12) + " " + Theme.px(8) + " " + Theme.px(12)
                + ", gapx " + Theme.px(12), "[][]push[][][][][]"));
        bar.setBackground(Theme.panel());
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, Theme.px(2), 0, Theme.border()));

        roundLabel = new JLabel("Подготовка…");
        roundLabel.setFont(Theme.font(13, Font.PLAIN));
        roundLabel.setForeground(Theme.ink2());
        bar.add(roundLabel);

        turnLabel = new JLabel("Партия начинается…");
        turnLabel.setFont(Theme.font(16, Font.BOLD));
        turnLabel.setForeground(Theme.ink());
        bar.add(turnLabel);

        // ВЫЙТИ ИЗ ПАРТИИ МОЖНО ВСЕГДА (просьба дизайнера 26.08): закрыли —
        // вернулись в «Штаб» и собрали стол заново.
        KpButton saveBtn = new KpButton("Сохранить", "продолжить потом", null);
        saveBtn.setPreferredSize(new Dimension(Theme.px(130), Theme.px(38)));
        saveBtn.setToolTipText("Записать партию, чтобы продолжить её из меню. "
            + "Сохраняются настройки стола и все принятые решения");
        saveBtn.onClick(this::saveGame);
        bar.add(saveBtn);

        KpButton toMenu = new KpButton("В меню", "закрыть партию", null);
        toMenu.setPreferredSize(new Dimension(Theme.px(126), Theme.px(38)));
        toMenu.setToolTipText("Закрыть партию и вернуться в «Штаб». "
            + "Недоигранная партия всё равно попадёт в журнал");
        toMenu.onClick(this::askClose);
        bar.add(toMenu);

        // Полоса всех мест — открытый счёт стола (блокер приёмки №1).
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBackground(Theme.panel());
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(bar);
        opponents = new kelium.gui.kp.OpponentStrip();
        opponents.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel oppWrap = new JPanel(new BorderLayout());
        oppWrap.setBackground(Theme.panel());
        oppWrap.setBorder(BorderFactory.createMatteBorder(Theme.px(1), 0, Theme.px(2), 0,
            Theme.border()));
        oppWrap.add(opponents, BorderLayout.CENTER);
        oppWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(oppWrap);
        return north;
    }

    private JComponent buildTabStrip() {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBackground(Theme.panel());
        strip.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, Theme.px(1), Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(10), Theme.px(4), Theme.px(10), Theme.px(4))));
        // Замечание дизайнера 25.08: вся информация игрока живёт ВНИЗУ, в его
        // зоне; сбоку остаётся только журнал («ладно, оставь его сбоку»).
        addDrawerTab(strip, "Журнал");
        strip.add(javax.swing.Box.createVerticalGlue());
        return strip;
    }

    private void addDrawerTab(JPanel strip, String name) {
        KpTab tab = new KpTab(name, () -> toggleDrawer(name));
        tab.setToolTipText(switch (name) {
            case "Наука и рынок" -> "Доска науки и активная карта рынка — открываются поверх поля в любой момент";
            case "Планшет" -> "Планшеты игроков: склад, войска, трофеи, арсенал — свой и соперников";
            default -> "Полная лента событий партии";
        });
        tab.setPreferredSize(new Dimension(Theme.px(36), Theme.px(132)));
        tab.setMaximumSize(new Dimension(Theme.px(36), Theme.px(132)));
        tab.setAlignmentX(Component.CENTER_ALIGNMENT);
        drawerTabs.put(name, tab);
        strip.add(tab);
        strip.add(javax.swing.Box.createVerticalStrut(Theme.px(8)));
    }

    /** Имя места для игрока: без сырых «human»/«balanced» (блокер приёмки №4). */
    private String seatName(int seat) {
        String spec = seatSpecs.get(seat);
        return "human".equals(spec) ? "Игрок " + (seat + 1)
            : kelium.agents.BotCatalog.label(spec);
    }

    private JComponent buildCenter() {
        field = new FieldView();
        field.setShowTurnCaption(false);
        // Отладочные подписи гексов игроку не показываются; для наведения
        // работает подсказка гекса, для решений — подсветка целей.
        field.setShowIds(false);
        boards = new BoardsPanel();
        sheet = new BoardSheet(session, 0);

        layered = new JLayeredPane();
        layered.add(field, JLayeredPane.DEFAULT_LAYER);

        JScrollPane boardsScroll = new JScrollPane(boards);
        boardsScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        drawers.put("Наука и рынок", wrapDrawer(boardsScroll));

        JPanel sheetWrap = new JPanel(new BorderLayout());
        sheetWrap.setBackground(Theme.panel());
        JPanel seatRow = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(6) + ", gapx " + Theme.px(4)));
        seatRow.setBackground(Theme.panel());
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < players; i++) {
            int s = i;
            JToggleButton b = new JToggleButton(seatName(i));
            b.setFont(Theme.font(11, Font.BOLD));
            b.setForeground(Theme.seatInk(i));
            b.setFocusable(false);
            b.setSelected(i == 0);
            b.addActionListener(e -> sheet.setSeat(s));
            group.add(b);
            seatRow.add(b);
            sheetSeatBtns.put(i, b);
        }
        sheetWrap.add(seatRow, BorderLayout.NORTH);
        JScrollPane sheetScroll = new JScrollPane(sheet);
        sheetScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        sheetScroll.setBorder(null);
        sheetWrap.add(sheetScroll, BorderLayout.CENTER);
        this.sheetScroll = sheetScroll;
        drawers.put("Планшет", wrapDrawer(sheetWrap));

        // СБРОС ПРИКАЗОВ — ЛИЧНЫЙ. Разыгранные в круге приказы уходят в свой
        // сброс и возвращаются в руку в начале следующего раунда; пока раунд
        // идёт, по сбросу видно, что уже потрачено (просьба дизайнера
        // 30.08.2026: «сброс карт приказов можно открывать кнопкой»).
        discardBox = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(12) + ", wrap 3, gapx " + Theme.px(8)
                + ", gapy " + Theme.px(8)));
        discardBox.setBackground(Theme.panel());
        JScrollPane discardScroll = new JScrollPane(discardBox);
        discardScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        discardScroll.setBorder(null);
        drawers.put("Сброс приказов", wrapDrawer(discardScroll));

        journalBox = new JPanel();
        journalBox.setLayout(new BoxLayout(journalBox, BoxLayout.Y_AXIS));
        journalBox.setBackground(Theme.panel());
        JScrollPane journalScroll = new JScrollPane(journalBox);
        journalScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        journalScroll.setBorder(null);
        drawers.put("Журнал", wrapDrawer(journalScroll));

        prompt = new PromptOverlay();
        layered.add(prompt, JLayeredPane.MODAL_LAYER);

        layered.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutLayers();
            }
        });
        return layered;
    }

    private JComponent wrapDrawer(JComponent content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.panel());
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, Theme.px(1), Theme.divider()));
        p.add(content, BorderLayout.CENTER);
        p.setVisible(false);
        layered.add(p, JLayeredPane.PALETTE_LAYER);
        return p;
    }

    void layoutLayers() {
        field.setBounds(0, 0, layered.getWidth(), layered.getHeight());
        int w = drawerWidth();
        // Открытый ящик ВЫЕЗЖАЕТ слева: доля выезда — из аниматора, прерванное
        // движение продолжается с текущего места (правило скилла).
        int x = (int) Math.round((drawerSlide.value() - 1) * w);
        for (JComponent d : drawers.values()) {
            d.setBounds(x, 0, w, layered.getHeight());
        }
        layoutPrompt();
    }

    /**
     * Ширина ящика — ПО ЕГО СОДЕРЖИМОМУ, а не одна на всех. Планшету нужен
     * простор: в нём лежат печатные планшеты игрока во всю ширину, и в узкой
     * полосе они превращаются в марку. «Науке и рынку» — тоже (в узком ящике
     * резались карты, замечание приёмки). Журналу хватает узкой ленты.
     */
    private int drawerWidth() {
        int want = openDrawer == drawers.get("Журнал")
            ? Theme.px(DRAWER_W) : Theme.px(WIDE_DRAWER_W);
        return Math.min(want, Math.max(Theme.px(320), (int) (layered.getWidth() * 0.72)));
    }

    private int openDrawerSpan() {
        return openDrawer != null && openDrawer.isVisible()
            ? (int) Math.round(drawerWidth() * drawerSlide.value()) : 0;
    }

    private void layoutPrompt() {
        int span = openDrawerSpan();
        int maxW = Math.max(Theme.px(320), layered.getWidth() - Theme.px(24) - span);
        int x = Theme.px(12) + span;
        prompt.setSize(new Dimension(Math.min(Theme.px(760), maxW), 10));
        Dimension pref = prompt.getPreferredSize();
        int h = Math.min(pref.height, layered.getHeight() - Theme.px(24));
        // Панель ПОДЪЕЗЖАЕТ снизу: доля подъезда — из своего аниматора.
        int lift = (int) Math.round((1 - promptSlide.value()) * Theme.px(20));
        prompt.setBounds(x, layered.getHeight() - h - Theme.px(12) + lift,
            Math.min(Theme.px(760), maxW), h);
        prompt.revalidate();
    }

    /** Показ контекстной панели с подъездом снизу. */
    private void promptIn() {
        promptSlide.snap(0);
        promptSlide.play(1, 150, v -> layoutPrompt(), null);
    }

    private void toggleDrawer(String name) {
        JComponent target = drawers.get(name);
        boolean closing = openDrawer == target;
        for (Map.Entry<String, JComponent> e : drawers.entrySet()) {
            boolean on = e.getValue() == target && !closing;
            if (e.getValue() != target) {
                e.getValue().setVisible(false);
            }
            KpTab tab = drawerTabs.get(e.getKey());
            if (tab != null) {
                tab.setSelected(on);
            }
            KpButton btn = drawerBtns.get(e.getKey());
            if (btn != null) {
                btn.setState(on ? KpButton.State.ACTIVE : KpButton.State.AVAILABLE);
            }
        }
        if (closing) {
            JComponent t = target;
            drawerSlide.play(0, 140, v -> {
                layoutLayers();
                layered.repaint();
            }, () -> {
                t.setVisible(false);
                openDrawer = null;
                layoutLayers();
            });
        } else {
            openDrawer = target;
            target.setVisible(true);
            drawerSlide.snap(openDrawerSpan() > 0 ? drawerSlide.value() : 0);
            drawerSlide.play(1, 160, v -> {
                layoutLayers();
                layered.repaint();
            }, null);
        }
    }

    private JComponent buildRail() {
        JPanel rail = new JPanel(new BorderLayout());
        rail.setPreferredSize(new Dimension(Theme.px(RAIL_W), Theme.px(10)));
        rail.setBackground(Theme.panel());
        rail.setBorder(BorderFactory.createMatteBorder(0, Theme.px(1), 0, 0, Theme.border()));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBackground(Theme.panel());
        stepsCaption = caption("ШАГИ ХОДА");
        top.add(stepsCaption);
        steps = new kelium.gui.kp.TurnStepsPanel();
        steps.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(steps);
        rail.add(top, BorderLayout.NORTH);

        JPanel feedWrap = new JPanel(new BorderLayout());
        feedWrap.setBackground(Theme.panel());
        feedWrap.add(caption("ЛЕНТА ПАРТИИ"), BorderLayout.NORTH);
        feedBox = new JPanel();
        feedBox.setLayout(new BoxLayout(feedBox, BoxLayout.Y_AXIS));
        feedBox.setBackground(Theme.panel());
        feedScroll = new JScrollPane(feedBox);
        feedScroll.setBorder(BorderFactory.createMatteBorder(Theme.px(1), 0, 0, 0, Theme.border()));
        feedScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(20));
        feedWrap.add(feedScroll, BorderLayout.CENTER);
        rail.add(feedWrap, BorderLayout.CENTER);
        return rail;
    }

    private JLabel caption(String text) {
        JLabel c = new JLabel(text);
        c.setFont(Theme.caption());
        c.setForeground(Theme.ink3());
        c.setBorder(BorderFactory.createEmptyBorder(Theme.px(10), Theme.px(10), Theme.px(6), Theme.px(10)));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    /** ПОСТОЯННАЯ ЗОНА ИГРОКА: руки карточками · панель действий · завершить ход. */
    private JComponent buildPlayerZone() {
        JPanel zone = new JPanel(new BorderLayout(Theme.px(14), 0));
        zone.setBackground(Theme.panel());
        zone.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(Theme.px(1), 0, 0, 0, Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(12), Theme.px(8), Theme.px(12))));
        // ВЫСОТА ПОД КАРТУ ЦЕЛИКОМ. Карты приказов — главный орган управления
        // ходом, и они не должны быть обрезаны снизу (замечание дизайнера
        // 30.08.2026: «не видно карты приказов, нельзя их даже пролистать»).
        zone.setPreferredSize(new Dimension(10, Theme.px(200)));

        // ВСЯ ИНФОРМАЦИЯ ИГРОКА В ОДНОМ МЕСТЕ (замечание дизайнера 25.08):
        // ресурсы и ПО — здесь же, внизу, рядом с кнопками планшета и науки.
        chipVp = new ChipLabel("SUPER", Theme.points(), "ПО");
        chipVp.setToolTipText("Победные очки (сумма всех источников)");
        chipCoin = new ChipLabel("COIN", Theme.points(), "монеты");
        chipCoin.setToolTipText("Монеты");
        chipKelium = new ChipLabel("KELIUM", Theme.kelium(), "келемий");
        chipKelium.setToolTipText("Келемий: на складе / потолок склада");
        chipAmmo = new ChipLabel("AMMO", Theme.energy(), "БПР");
        chipAmmo.setToolTipText("Боеприпасы: на складе / потолок склада");
        chipDebris = new ChipLabel("DEBRIS", Theme.neutral(), "обломки");
        chipDebris.setToolTipText("Обломки: на складе / потолок");
        JPanel me = new JPanel();
        me.setOpaque(false);
        me.setLayout(new BoxLayout(me, BoxLayout.Y_AXIS));
        // ФИШКИ В ДВЕ СТРОКИ ПО ТРИ. Одной лентой из пяти они тянулись на
        // пол-окна и отжимали карты приказов в щель.
        JPanel chipsRow = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, wrap 3, gapx " + Theme.px(5) + ", gapy " + Theme.px(4)));
        chipsRow.setOpaque(false);
        for (ChipLabel c : List.of(chipVp, chipCoin, chipKelium, chipAmmo, chipDebris)) {
            chipsRow.add(c);
        }
        chipsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        me.add(chipsRow);
        me.add(javax.swing.Box.createVerticalStrut(Theme.px(6)));
        // ЯЩИКИ — ДВА НА ДВА, узкими кнопками. Ряд из четырёх по 128 пикселей
        // уходил за 560 и съедал место у руки.
        JPanel btnRow = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, wrap 2, gapx " + Theme.px(5) + ", gapy " + Theme.px(5)));
        btnRow.setOpaque(false);
        KpButton boardBtn = new KpButton("Планшет", "склад · войска", null);
        boardBtn.setToolTipText(
            "Планшеты игроков: склад, войска, трофеи, арсенал — свой и соперников");
        boardBtn.setPreferredSize(new Dimension(Theme.px(118), Theme.px(34)));
        boardBtn.onClick(() -> toggleDrawer("Планшет"));
        boardBtn.setState(KpButton.State.AVAILABLE);
        drawerBtns.put("Планшет", boardBtn);
        // ВЫЗОВ МЕНЮ КАРТ (просьба дизайнера 27.08): руку заданий и зону
        // арсенала раскладывают перед собой и разбирают. Кнопки горят только
        // тогда, когда движок реально предлагает СПЕЦ-действие — иначе
        // разбирать нечего, и обещать действие нельзя.
        objMenuBtn = new KpButton("Задания", "выполнить · сжечь", null);
        objMenuBtn.setPreferredSize(new Dimension(Theme.px(118), Theme.px(34)));
        objMenuBtn.setToolTipText("Разложить руку заданий: выполнить выполнимое "
            + "или сжечь карту ради верхнего эффекта");
        objMenuBtn.onClick(this::openObjectiveMenu);
        objMenuBtn.setState(KpButton.State.DISABLED);
        btnRow.add(objMenuBtn);

        arsMenuBtn = new KpButton("Арсенал", "полка · рука", null);
        arsMenuBtn.setPreferredSize(new Dimension(Theme.px(118), Theme.px(34)));
        arsMenuBtn.setToolTipText("Зона арсенала: что стоит на полке и что можно "
            + "поставить или сжечь");
        arsMenuBtn.onClick(this::openArsenalMenu);
        arsMenuBtn.setState(KpButton.State.DISABLED);
        btnRow.add(arsMenuBtn);

        KpButton sciBtn = new KpButton("Наука и рынок", "доска · курс", null);
        sciBtn.setToolTipText(
            "Доска науки и активная карта рынка — открываются поверх поля в любой момент");
        sciBtn.setPreferredSize(new Dimension(Theme.px(118), Theme.px(34)));
        sciBtn.onClick(() -> toggleDrawer("Наука и рынок"));
        sciBtn.setState(KpButton.State.AVAILABLE);
        drawerBtns.put("Наука и рынок", sciBtn);
        btnRow.add(boardBtn);
        btnRow.add(sciBtn);

        KpButton discardBtn = new KpButton("Сброс", "разыграно в раунде", null);
        discardBtn.setPreferredSize(new Dimension(Theme.px(118), Theme.px(34)));
        discardBtn.setToolTipText("Ваш личный сброс приказов: карты, разыгранные "
            + "в этом раунде. Вернутся в руку в начале следующего");
        discardBtn.onClick(() -> toggleDrawer("Сброс приказов"));
        discardBtn.setState(KpButton.State.AVAILABLE);
        drawerBtns.put("Сброс приказов", discardBtn);
        btnRow.add(discardBtn);
        me.setMaximumSize(new Dimension(Theme.px(260), Integer.MAX_VALUE));
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        me.add(btnRow);
        zone.add(me, BorderLayout.WEST);

        hands = new HandPanel(new HandPanel.HoverSink() {
            @Override
            public void onHover(CardTile tile, String group) {
                showZoom(tile, group);
            }

            @Override
            public void onHoverOff() {
                zoom.setVisible(false);
            }
        });
        JScrollPane handScroll = new JScrollPane(hands,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScroll.setBorder(null);
        handScroll.getViewport().setOpaque(false);
        handScroll.setOpaque(false);
        zone.add(handScroll, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        // ПАНЕЛЬ ДЕЙСТВИЙ РАСТЁТ ПО СОДЕРЖИМОМУ: пока действий нет, она узкая,
        // и вся ширина достаётся руке. Жёсткие 420 пикселей держались всегда,
        // даже в чужой ход.
        actionBar = new ActionBar();
        right.add(actionBar);
        right.add(javax.swing.Box.createHorizontalStrut(Theme.px(12)));

        endBtn = new KpButton("Ход соперника…", "", null).primary(true);
        endBtn.setToolTipText("Завершить ход — пас по действиям; СПЕЦ-действие может остаться доступным");
        endBtn.setState(KpButton.State.DISABLED);
        endBtn.setPreferredSize(new Dimension(Theme.px(148), Theme.px(96)));
        endBtn.setMaximumSize(new Dimension(Theme.px(148), Theme.px(120)));
        right.add(endBtn);
        zone.add(right, BorderLayout.EAST);
        return zone;
    }

    private void showZoom(CardTile tile, String group) {
        if ("Приказы".equals(group) && tile.orderFaceInfo() != null) {
            zoom.showOrder(tile.orderFaceInfo(), tile.cardName(), orderDesc(tile.cardId));
            placeZoom(tile);
            return;
        }
        String type;
        String detail = "";
        double progress = -1;
        if ("Задания".equals(group)) {
            type = "Задание";
            try {
                var card = kelium.engine.cards.CardRegistry.objective(tile.cardId);
                GameState s = liveState;
                if (card != null && s != null) {
                    var ctx = new kelium.engine.cards.EngineCardContext(s, viewedSeat);
                    progress = card.progress(ctx);
                    detail = String.valueOf(card.needed(ctx));
                }
            } catch (RuntimeException ignore) {
                // прогресс — украшение подсказки; без него карта всё равно видна
            }
        } else if ("Приказы".equals(group)) {
            type = "Приказ";
        } else {
            type = "Арсенал";
        }
        zoom.show(tile.cardName(), type, tile.bandColor(), detail, progress);
        placeZoom(tile);
    }

    private void placeZoom(CardTile tile) {
        Point p = SwingUtilities.convertPoint(tile, 0, 0, frame.getLayeredPane());
        int x = Math.max(Theme.px(6),
            Math.min(p.x - Theme.px(60), frame.getLayeredPane().getWidth() - zoom.getWidth() - Theme.px(6)));
        int y = Math.max(Theme.px(6), p.y - zoom.getHeight() - Theme.px(4));
        zoom.setLocation(x, y);
    }

    // ==================== партия ====================

    private void runGame() {
        // КРАСКИ МЕСТ ставятся ДО сборки партии: по ним рисуется и поле, и
        // фишки, и картинки жетонов.
        kelium.report.FieldGeometry.useSeatColors(options.seatColors());
        GameConfig cfg = GameConfig.buildCached(options.rulesetId(), players, seed, null, null,
            options.scenarioId(), options.cuFacing(), options.scenarioFile());
        applyTrainingSetup(cfg);
        this.cfg = cfg;
        GameState state = Setup.buildGame(cfg);
        this.liveState = state;
        SwingUtilities.invokeLater(() -> {
            boards.setRules(cfg.ruleset, cfg.content);
            session.setContent(cfg.content);
        });

        List<Agent> agents = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            String spec = seatSpecs.get(seat);
            if ("human".equals(spec)) {
                int seatFinal = seat;
                kelium.core.UndoableAgent ia = new kelium.core.UndoableAgent(
                    seat, "Игрок " + (seat + 1), state,
                    d -> SwingUtilities.invokeLater(() -> showDecision(seatFinal, d)),
                    ev -> { });
                humansBySeat.put(seat, ia);
                if (humansBySeat.size() == 1) {
                    viewedSeat = seat;
                }
                agents.add(ia);
                labels.add("human");
            } else {
                // КОГО САЖАТЬ — РЕШАЕТ СПРАВОЧНИК БОТОВ, один на всю программу:
                // он же разбирает уровень умения («punisher:4»). Прежний прямой
                // Bots.create принимал только имя характера и на составе с
                // уровнем падал, пытаясь открыть файл с двоеточием в имени.
                agents.add(kelium.agents.BotCatalog.create(spec, seat,
                    new Random(seed * 131 + seat + 1), players));
                labels.add(spec);
            }
        }
        mySeat = meSeat(seatSpecs);

        if (options.training()) {
            SwingUtilities.invokeLater(() -> feedLine(null,
                "ТРЕНИРОВОЧНАЯ ПАРТИЯ: значения подготовки заданы вручную — "
                    + trainingNote() + ". В замеры баланса такая партия не годится."));
        }

        // ЛЕНТА РЕШЕНИЙ пишется всегда: без неё партию не сохранить. Если
        // партию продолжают, поверх ложится проигрывающая обёртка — она доводит
        // стол до места сохранения и передаёт игру живым.
        List<Agent> playing = agents;
        if (!replay.isEmpty()) {
            catchingUp = true;
            SwingUtilities.invokeLater(() -> turnLabel.setText(
                "Доигрываем сохранённое — " + replay.size() + " решений…"));
            playing = MoveLog.playback(playing, replay,
                () -> SwingUtilities.invokeLater(this::onCaughtUp));
        }
        // ПИШУЩАЯ ОБЁРТКА — САМАЯ ВЕРХНЯЯ, поверх проигрывающей: иначе
        // доигранные по сохранению ходы мимо ленты пройдут, и сохранить
        // продолженную партию будет нечем.
        playing = MoveLog.recording(playing, moves);

        ReplayRecord result;
        try {
            result = GameRecorder.playWithAgents(cfg, state, playing, labels, seed,
                options.seatColors(),
                msg -> SwingUtilities.invokeLater(() -> feedLine(null, msg)),
                r -> SwingUtilities.invokeLater(() -> onFrame(r)));
        } catch (kelium.core.GameAborted e) {
            // Игрок закрыл партию — это не поломка. Записываем то, что успело
            // случиться: журнал партии нужен дизайнеру и от недоигранной.
            saveJournal(rec, "-прервана");
            return;
        } catch (Throwable t) {
            if (stopped) {
                return;      // окно уже закрыто, жаловаться некому
            }
            SwingUtilities.invokeLater(() -> {
                turnLabel.setText("Партия прервана ошибкой");
                feedLine(null, String.valueOf(t));
            });
            return;
        }
        ReplayRecord finalRec = result;
        SwingUtilities.invokeLater(() -> {
            turnLabel.setText("Партия окончена: "
                + (finalRec.winner == null ? "без победителя (" + finalRec.condition + ")"
                    : "победил Игрок " + (finalRec.winner + 1))
                + " · раундов " + finalRec.rounds);
            turnLabel.setForeground(finalRec.winner == null
                ? Theme.ink() : Theme.seatInk(finalRec.winner));
            endBtn.setTexts("Партия окончена", "");
            endBtn.setState(KpButton.State.DISABLED);
            finished = true;
            clearDecision();
        });
        saveJournal(finalRec, "");
    }

    /** Записать журнал партии на диск. {@code suffix} — пометка в имени файла. */
    /**
     * ЧЬЁ МЕСТО ПОДПИСАТЬ «ВЫ» — или −1, если ничьё.
     *
     * <p>Пометка имеет смысл ровно в одном случае: живой за столом ОДИН, а
     * прочие места заняты ботами — тогда «вы» отличает вас от соперников.
     * Живых несколько — компьютер просто передаёт ход каждому по очереди, и
     * который из них «я», не значит ничего: отмечать некого.
     */
    public static int meSeat(List<String> seatSpecs) {
        int found = -1;
        for (int i = 0; i < seatSpecs.size(); i++) {
            if ("human".equals(seatSpecs.get(i))) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return found;
    }

    /** Вид ожидаемого решения — для прогонщиков и тестов. */
    String pendingKindForTest() {
        return pendingKind;
    }

    /** Место с пометкой «вы» (−1 — ни у кого) — для прогонщиков и тестов. */
    int mySeatForTest() {
        return mySeat;
    }

    private void saveJournal(ReplayRecord r, String suffix) {
        if (r == null) {
            return;
        }
        try {
            java.nio.file.Path out = java.nio.file.Path.of("reports", "hotseat",
                "hotseat-" + seed + suffix + ".kelium-replay.json");
            r.save(out);
            SwingUtilities.invokeLater(() ->
                feedLine(null, "Журнал партии записан: " + out.toAbsolutePath()));
        } catch (java.io.IOException e) {
            SwingUtilities.invokeLater(() ->
                feedLine(null, "Не удалось записать журнал: " + e.getMessage()));
        }
    }

    // ==================== живое обновление ====================

    /** Что окно сейчас говорит о партии — для прогонщиков и тестов. */
    String statusForTest() {
        return turnLabel == null ? "?" : turnLabel.getText();
    }

    /** Последние строки ленты — для прогонщиков и тестов. */
    String lastFeedForTest() {
        return lastFeedText == null ? "" : lastFeedText;
    }

    /** Зарядить ленту сохранения до start() — для прогонщиков и тестов. */
    void loadForTest(List<Integer> moves) {
        replay.clear();
        replay.addAll(moves);
    }

    /** Сохранение доиграно: дальше партия живая. */
    private void onCaughtUp() {
        catchingUp = false;
        // ПЕРЕРИСОВАТЬ ВСЁ ЦЕЛИКОМ: пока доигрывали, кадры пропускались ради
        // скорости, и последний из них мог оказаться пропущенным — окно тогда
        // встречает игрока пустым полем, хотя партия уже идёт.
        if (rec != null && !rec.frames.isEmpty()) {
            onFrame(rec);
        }
        feedLine(null, "Сохранённая партия восстановлена — играем дальше");
    }

    /**
     * СОХРАНИТЬ ПАРТИЮ. Пишутся настройки стола и лента принятых решений —
     * этого хватает, чтобы повторить партию до этого места в точности.
     */
    void saveGame() {
        List<Integer> snapshot;
        synchronized (moves) {
            snapshot = new ArrayList<>(moves);
        }
        ReplayRecord r = rec;
        int round = r == null || r.frames.isEmpty() ? 0
            : r.frames.get(r.frames.size() - 1).round;
        int circle = r == null || r.frames.isEmpty() ? 0
            : r.frames.get(r.frames.size() - 1).circle;
        String when = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String name = "партия " + seed;
        GameSave save = new GameSave(name, options, snapshot, options.rulesetId(),
            GameSave.contentVersionsOf(cfg), when, round, circle);
        try {
            java.nio.file.Path file = GameSave.fileFor(name);
            save.save(file);
            feedLine(null, "Партия сохранена: " + file.toAbsolutePath()
                + " (решений " + snapshot.size() + ")");
        } catch (java.io.IOException e) {
            feedLine(null, "Не удалось сохранить партию: " + e.getMessage());
        }
    }

    /**
     * ЗАКРЫТЬ ПАРТИЮ И ВЕРНУТЬСЯ В «ШТАБ». Недоигранную партию спрашиваем: она
     * пропадёт, и сказать об этом надо ДО, а не после.
     */
    void askClose() {
        if (stopped || finished) {
            closeToMenu();
            return;
        }
        confirm.open("Закрыть партию?",
            "Партия не доиграна — вернуться к ней будет нельзя",
            List.of("Всё, что успело случиться, останется в журнале партии",
                "Стол в «Штабе» соберётся заново с теми же настройками"),
            List.of(new kelium.gui.kp.ConfirmDialog.Option("Закрыть и выйти в меню",
                "вернуться в «Штаб»", this::closeToMenu)),
            new kelium.gui.kp.ConfirmDialog.Option("Продолжить играть", "остаться в партии",
                () -> confirm.close()));
    }

    /**
     * Снять движок с недоигранной партии и открыть меню.
     *
     * <p>Движок синхронный и сейчас ждёт ответа игрока, поэтому он размыкается
     * {@link kelium.core.UndoableAgent#abort} — поток выходит из точки решения
     * сам, ничего не решая за игрока. Если сейчас думает бот, поток закончит
     * его ход и выйдет на следующей точке живого игрока.
     */
    private void closeToMenu() {
        stopped = true;
        cardMenu.close();
        curtain.drop();
        confirm.close();
        for (kelium.core.UndoableAgent a : humansBySeat.values()) {
            a.abort();
        }
        frame.dispose();
        StartMenuWindow.open(options);
    }

    /**
     * ТРЕНИРОВОЧНЫЕ ЗНАЧЕНИЯ ПОДГОТОВКИ. Правится КОПИЯ свода этой партии
     * ({@code buildCached} отдаёт копию нарочно), файлы правил не трогаются.
     */
    private void applyTrainingSetup(GameConfig cfg) {
        if (options.startCoins() != null) {
            List<Integer> coins = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                coins.add(options.startCoins());
            }
            cfg.ruleset.override("setup.start_coins", coins);
        }
        if (options.startKelium() != null) {
            cfg.ruleset.override("setup.start_kelium", options.startKelium());
        }
        if (options.startAmmo() != null) {
            cfg.ruleset.override("setup.start_ammo", options.startAmmo());
        }
    }

    /** Чем тренировочная партия отличается от обычной — словами для журнала. */
    private String trainingNote() {
        List<String> parts = new ArrayList<>();
        if (options.startCoins() != null) {
            parts.add("монет " + options.startCoins());
        }
        if (options.startKelium() != null) {
            parts.add("келемия " + options.startKelium());
        }
        if (options.startAmmo() != null) {
            parts.add("боеприпасов " + options.startAmmo());
        }
        return String.join(", ", parts);
    }

    private void onFrame(ReplayRecord r) {
        if (stopped) {
            return;          // окно закрыто — движку уже некуда рисовать
        }
        // ЗАПИСЬ ПРИВЯЗЫВАЕТСЯ ВСЕГДА, даже пока доигрываем сохранение: это не
        // перерисовка, а связь окна с партией. Пропускать её было ошибкой —
        // восстановленная партия открывалась «пустой», хотя уже шла.
        if (this.rec != r) {
            this.rec = r;
            field.setRecord(r);
        }
        if (r.frames.isEmpty()) {
            return;
        }
        if (catchingUp && r.frames.size() % 40 != 0) {
            // Доигрывание сохранения идёт сотнями кадров в секунду: перерисовывать
            // каждый — только тормозить. Показываем каждый сороковой, чтобы было
            // видно, что дело движется.
            return;
        }
        int last = r.frames.size() - 1;
        ReplayRecord.Frame f = r.frames.get(last);
        field.setFrame(f);
        if (!sessionBound) {
            sessionBound = true;
            session.setRecord(r);
        }
        session.seek(last);
        if (f.snapshot != null) {
            boards.show(r, f.snapshot);
        }
        if (f.log != null && !f.log.isBlank()) {
            feedLine(f.seat, f.log);
        }
        trackSteps(f);
        refreshHands(f);
        refreshTopBar(f);
    }

    private void refreshTopBar(ReplayRecord.Frame f) {
        int circles = 4;
        GameConfig c = cfg;
        if (c != null) {
            try {
                circles = c.ruleset.getInt("rounds.circles_per_round");
            } catch (RuntimeException ignore) {
                // часы — украшение, партия важнее
            }
        }
        roundLabel.setText("Раунд " + f.round + " · "
            + (f.circle <= 0 ? "подготовка круга" : "круг " + f.circle + " из " + circles));
        Integer active = f.snapshot == null ? null : f.snapshot.active;
        if (awaitingSeat == null) {
            if (active == null) {
                turnLabel.setText("Общая фаза раунда");
                turnLabel.setForeground(Theme.ink2());
            } else {
                boolean bot = !"human".equals(seatSpecs.get(active));
                turnLabel.setText("Ходит: " + seatName(active) + (bot ? " (бот)" : ""));
                turnLabel.setForeground(Theme.seatInk(active));
            }
        }
        int seat = awaitingSeat != null ? awaitingSeat
            : active != null && humansBySeat.containsKey(active) ? active : viewedSeat;
        if (f.snapshot != null && seat < f.snapshot.players.size()) {
            ReplayRecord.Player p = f.snapshot.players.get(seat);
            chipVp.set(String.valueOf(vpTotal(p)), null);
            chipCoin.set(String.valueOf(p.coin), null);
            chipKelium.set(String.valueOf(p.kelium), String.valueOf(p.keliumCap));
            chipAmmo.set(String.valueOf(p.ammo), String.valueOf(p.ammoCap));
            chipDebris.set(String.valueOf(p.debris), String.valueOf(p.debrisCap));
        }
        if (f.snapshot != null) {
            List<kelium.gui.kp.OpponentStrip.Row> rows = new ArrayList<>();
            for (ReplayRecord.Player p : f.snapshot.players) {
                rows.add(new kelium.gui.kp.OpponentStrip.Row(p.seat, seatName(p.seat),
                    p.seat == mySeat, vpTotal(p), p.coin, p.kelium, p.ammo,
                    p.orderHand.size(), p.objectiveHand.size(), p.arsenalHand.size(),
                    p.trophyPoints));
            }
            opponents.update(rows);
        }
    }

    private static int vpTotal(ReplayRecord.Player p) {
        return p.vp.getOrDefault("total",
            p.vp.values().stream().mapToInt(Integer::intValue).sum());
    }

    private void refreshHands(ReplayRecord.Frame f) {
        if (f.snapshot == null || viewedSeat >= f.snapshot.players.size()) {
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(viewedSeat);
        hands.setCards("Приказы", p.orderHand, this::cardName, orderBand(p.orderColor),
            null, null, this::orderFace);
        hands.setCards("Задания", p.objectiveHand, this::cardName, Theme.points(),
            this::objectiveTag, Theme.kelium(), null);
        hands.setCards("Арсенал", p.arsenalHand, this::cardName, Theme.container(),
            null, null, null);
        refreshDiscard(p);
    }

    /** Личный сброс приказов смотрящего места — лицами карт, как в руке. */
    private void refreshDiscard(ReplayRecord.Player p) {
        if (discardBox == null || discardShown.equals(p.orderPlayed)) {
            return;                          // не пересобирать неизменившееся
        }
        discardShown = List.copyOf(p.orderPlayed);
        discardBox.removeAll();
        JLabel cap = new JLabel(discardShown.isEmpty()
            ? "СБРОС ПРИКАЗОВ — пусто: в этом раунде вы ещё ничего не разыграли"
            : "СБРОС ПРИКАЗОВ — разыграно в этом раунде: " + discardShown.size());
        cap.setFont(Theme.font(10, Font.BOLD));
        cap.setForeground(Theme.ink3());
        discardBox.add(cap, "span 3, wrap");
        for (String id : discardShown) {
            CardTile t = new CardTile(id, cardName(id), orderBand(null),
                tile -> showZoom(tile, "Приказы"), () -> zoom.setVisible(false));
            t.setPreferredSize(new Dimension(Theme.px(96), Theme.px(134)));
            t.orderFace(orderFace(id));
            t.setToolTipText(cardName(id));
            discardBox.add(t);
        }
        discardBox.revalidate();
        discardBox.repaint();
    }

    private Color orderBand(String color) {
        if (color == null) {
            return Theme.accent();
        }
        return switch (color) {
            case "red" -> new Color(0xC75450);
            case "green" -> new Color(0x4E9E5F);
            case "blue" -> new Color(0x4A8ACD);
            case "yellow" -> new Color(0xC9A23B);
            default -> Theme.accent();
        };
    }

    private String objectiveTag(String cardId) {
        try {
            var card = kelium.engine.cards.CardRegistry.objective(cardId);
            GameState s = liveState;
            if (card == null || s == null) {
                return null;
            }
            double pr = card.progress(new kelium.engine.cards.EngineCardContext(s, viewedSeat));
            return Math.round(pr * 100) + "%";
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** События хода → источники ленты шагов (концепт §5). */
    private void trackSteps(ReplayRecord.Frame f) {
        if ("turn_orders".equals(f.type) && f.seat != null) {
            turnSeat = f.seat;
            lockedSteps.clear();
            botSteps.clear();
            pendingBakeName = null;
            lastAgentLabels = new ArrayList<>();
            lockedSteps.add("Приказ вскрыт");
            stepsCaption.setText("ШАГИ ХОДА — ИГРОК " + (f.seat + 1));
            actionBar.turnStarted();
            // ВСКРЫТЫЙ ПРИКАЗ — К КНОПКАМ ДЕЙСТВИЙ: действия берутся с этой
            // карты, и держать её в голове игрок не должен.
            actionBar.setOrderCard(null, false);
            if (rec != null) {
                for (int i = rec.orderPlays.size() - 1; i >= 0; i--) {
                    var play = rec.orderPlays.get(i);
                    if (play.seat == f.seat) {
                        actionBar.setOrderCard(orderFace(play.card), play.bottomOpen);
                        break;
                    }
                }
            }
        } else if ("action".equals(f.type) && f.seat != null && f.seat.equals(turnSeat)
                && !humansBySeat.containsKey(turnSeat)) {
            String name = String.valueOf(f.log);
            botSteps.add(name.length() > 40 ? name.substring(0, 39) + "…" : name);
        } else if ("turn_end".equals(f.type) && f.seat != null && f.seat.equals(turnSeat)) {
            turnSeat = null;
            actionBar.setOrderCard(null, false);
        }
        refreshSteps();
    }

    /** Собрать ленту: запёкшееся → точки отката агента → текущая точка. */
    private void refreshSteps() {
        // ЗАПЕКАНИЕ ПО ФАКТУ: точки исчезли из агента (первый залп/рынок/наука
        // очистили стек) — их подписи переезжают в замки вместе с именем
        // необратимого действия, запомненным при клике по плитке.
        kelium.core.UndoableAgent bakeAgent =
            turnSeat == null ? null : humansBySeat.get(turnSeat);
        if (bakeAgent != null) {
            List<String> now = new ArrayList<>();
            for (String l : bakeAgent.checkpointLabels()) {
                now.add(ActionBar.ACTIONS.getOrDefault(l, l));
            }
            if (now.isEmpty() && !lastAgentLabels.isEmpty() && pendingBakeName != null) {
                lockedSteps.addAll(lastAgentLabels);
            }
            if (now.isEmpty() && pendingBakeName != null) {
                lockedSteps.add(pendingBakeName);
                pendingBakeName = null;
            }
            lastAgentLabels = now;
        }

        List<kelium.gui.kp.TurnStepsPanel.Row> rows = new ArrayList<>();
        int seat = turnSeat == null ? viewedSeat : turnSeat;
        for (String s : lockedSteps) {
            rows.add(new kelium.gui.kp.TurnStepsPanel.Row(s,
                kelium.gui.kp.TurnStepsPanel.Kind.LOCKED, null));
        }
        kelium.core.UndoableAgent agent =
            turnSeat == null ? null : humansBySeat.get(turnSeat);
        if (agent != null) {
            // Откат безопасен только на границе действий: пока движок стоит на
            // точке вида action этого же хода (см. javadoc UndoableAgent).
            boolean undoNow = awaitingSeat != null && awaitingSeat.equals(turnSeat)
                && "action".equals(pendingKind);
            List<String> labels = agent.checkpointLabels();
            for (int i = 0; i < labels.size(); i++) {
                String ru = ActionBar.ACTIONS.getOrDefault(labels.get(i), labels.get(i));
                int idx = i;
                rows.add(new kelium.gui.kp.TurnStepsPanel.Row(ru,
                    undoNow ? kelium.gui.kp.TurnStepsPanel.Kind.UNDOABLE
                        : kelium.gui.kp.TurnStepsPanel.Kind.INFO,
                    undoNow ? () -> doUndoTo(agent, idx) : null));
            }
        } else {
            for (String s : botSteps) {
                rows.add(new kelium.gui.kp.TurnStepsPanel.Row(s,
                    kelium.gui.kp.TurnStepsPanel.Kind.INFO, null));
            }
        }
        if (awaitingSeat != null && awaitingSeat.equals(turnSeat) && pendingKind != null) {
            rows.add(new kelium.gui.kp.TurnStepsPanel.Row(
                KIND_LABELS.getOrDefault(pendingKind, pendingKind),
                kelium.gui.kp.TurnStepsPanel.Kind.CURRENT, null));
        }
        steps.setRows(seat, rows);
    }

    /**
     * ОТКАТ «ДО ТОЧКИ»: партия возвращается к моменту перед шагом, экран
     * перерисовывается сразу (движок молчит — он всё ещё ждёт наш ответ),
     * след отката остаётся и в ленте, и в журнале партии.
     */
    private void doUndoTo(kelium.core.UndoableAgent agent, int idx) {
        String ru = ActionBar.ACTIONS.getOrDefault(
            agent.checkpointLabels().get(idx), agent.checkpointLabels().get(idx));
        agent.undoTo(idx);
        List<String> left = agent.checkpointLabels();
        actionBar.setPlayed(left);
        GameState s = liveState;
        if (rec != null && s != null) {
            ReplayRecord.Frame f = new ReplayRecord.Frame();
            f.type = "undo";
            f.round = s.round;
            f.circle = s.circle;
            f.seat = agent.seat;
            f.log = "Игрок " + (agent.seat + 1) + " вернулся к моменту перед шагом «"
                + ru + "»";
            f.snapshot = ReplayRecord.snapshotOf(s, agent.seat);
            rec.frames.add(f);
            onFrame(rec);
        } else {
            refreshSteps();
        }
    }

    /**
     * ПОДПИСЬ ДЛЯ ЧЕЛОВЕКА: без внутренних кодов в скобках — «Колосс (koloss)»
     * превращается в «Колосс». Той же меркой чистится лента партии: игроку эти
     * коды не нужны нигде, а в журнале на диске они остаются.
     */
    static String humanLabel(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s*\\([a-z0-9_:>.\\-]+\\)", "").trim();
    }

    /** Строка ленты (и её копия в ящик «Журнал»). seat null — служебное. */
    private void feedLine(Integer seat, String text) {
        // Сырые служебные события (телеметрия ботов вида «objective_hints {…}»)
        // человеку в ленте не нужны — в полном журнале партии они остаются.
        if (text.matches("^[a-z_]+ \\{.*")) {
            return;
        }
        // ВНУТРЕННИЕ ИДЕНТИФИКАТОРЫ в скобках — (yellow_dev), (security_4) —
        // игроку не нужны никогда (блокер приёмки №2); журнал партии на диске
        // их сохраняет. Длинные протокольные записи сокращаются.
        text = text.replaceAll("\\s*\\([a-z0-9_:>.\\-]+\\)", "")
            .replaceAll("\\s{2,}", " ").trim();
        // Человеческое место называется «Игрок N», и playerName записи склеивает
        // «Игрок 1 · Игрок 1» — второй повтор игроку не нужен.
        text = text.replaceAll("(Игрок \\d+) · \\1", "$1");
        if (text.length() > 160) {
            text = text.substring(0, 159) + "…";
        }
        // Повторы сверяем ПОСЛЕ чистки: сырые строки могли отличаться только
        // внутренними скобками, и лента забивалась дюжиной одинаковых строк.
        if (text.equals(lastFeedText)) {
            return;
        }
        lastFeedText = text;
        feedBox.add(feedRow(seat, text));
        journalBox.add(feedRow(seat, text));
        while (feedBox.getComponentCount() > 250) {
            feedBox.remove(0);
        }
        feedBox.revalidate();
        journalBox.revalidate();
        SwingUtilities.invokeLater(() -> {
            var bar = feedScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private JComponent feedRow(Integer seat, String text) {
        JLabel row = new JLabel("<html><body style='width:" + Theme.px(196) + "px'>"
            + text + "</body></html>");
        row.setFont(Theme.font(11, Font.PLAIN));
        row.setForeground(Theme.ink2());
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, Theme.px(2), 0, 0,
                seat == null ? Theme.border() : Theme.seat(seat)),
            BorderFactory.createEmptyBorder(Theme.px(2), Theme.px(6), Theme.px(2), Theme.px(6))));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    // ==================== точки решения ====================

    /** Семья A концепта §3 — цель на поле, payload сводится к гексу. */
    private static final Set<String> HEX_TARGET_KINDS = Set.of(
        "tower_hex", "build_hex", "move_hex", "energy_hex", "combat_source", "combat_target");

    private static final Map<String, String> KIND_LABELS = Map.ofEntries(
        Map.entry("action", "выберите действие"),
        Map.entry("spec", "СПЕЦ-действие"),
        Map.entry("reveal_order", "выберите карту круга"),
        Map.entry("blind_discard", "отложите приказ — место для трофеев"),
        Map.entry("build_pick", "стройка: что строим"),
        Map.entry("build_facing", "какими секторами поставить"),
        Map.entry("tower_hex", "гекс для вышки"),
        Map.entry("build_hex", "гекс для постройки"),
        Map.entry("move_hex", "гекс для переноса"),
        Map.entry("energy_hex", "гекс для энергии"),
        Map.entry("move", "куда шагнуть"),
        Map.entry("maneuver_unit", "какой отряд поведёте"),
        Map.entry("combat_source", "откуда атаковать"),
        Map.entry("combat_target", "цель атаки"),
        Map.entry("combat_victim", "кого поразить"),
        Map.entry("neutral_victim", "какой нейтрал атаковать"),
        Map.entry("attack", "атака"),
        Map.entry("mine", "добыча"),
        Map.entry("assemble", "сборка"),
        Map.entry("tuck", "подложить карту-символ"),
        Map.entry("open_container", "вскрытие контейнера"),
        Map.entry("market_rate", "курс рынка"),
        Map.entry("sci_track", "трек науки"),
        Map.entry("super_pick", "выберите супер-задание"),
        Map.entry("start_objective_pick", "стартовое задание"));

    /**
     * ТОЧКА РЕШЕНИЯ ЖИВОГО ИГРОКА. Если за столом несколько людей и ход
     * переходит к другому — сперва ШТОРКА: пока новый игрок не сказал «я на
     * месте», на экране не должно появиться ни его руки, ни чужой.
     *
     * <p>Порядок важен: сначала поднять шторку, и только потом трогать
     * {@code viewedSeat} и руки. Наоборот — рука успеет мелькнуть.
     */
    private void showDecision(int seat, InteractiveAgent.PendingDecision d) {
        if (needsCurtain(seat)) {
            String why = String.valueOf(d.context().get("kind"));
            curtain.raise(seatName(seat), Theme.seatInk(seat), curtainReason(why),
                () -> {
                    lastServedHuman = seat;
                    showDecisionNow(seat, d);
                });
            frame.toFront();
            return;
        }
        lastServedHuman = seat;
        showDecisionNow(seat, d);
    }

    /**
     * Нужна ли шторка перед этой точкой решения. Один живой за столом — нет:
     * прятать не от кого, а лишний экран между ходами только злит.
     */
    private boolean needsCurtain(int seat) {
        return humansBySeat.size() > 1 && seat != lastServedHuman && !stopped;
    }

    /** Зачем зовут игрока — короткой строкой на шторке. */
    private String curtainReason(String kind) {
        return switch (kind) {
            case "reveal_order" -> "вскрываем приказ круга";
            case "blind_discard" -> "отложите приказ под трофеи";
            case "combat_victim", "neutral_victim" -> "по вам ударили — выберите жертву";
            case "action" -> "ваш ход";
            default -> KIND_LABELS.getOrDefault(kind, "ваш ход");
        };
    }

    /**
     * ЧУЖОЙ ПЛАНШЕТ ЗА ОБЩИМ СТОЛОМ НЕ ПОКАЗЫВАЕМ. На планшете лежит скрытое —
     * отложенный приказ, подсунутые карты, — и за столом чужой планшет в руки
     * не берут. Пока живой игрок один, смотреть можно всё: прятать не от кого,
     * а разбирать партию удобнее целиком.
     */
    private void refreshSheetSeats() {
        boolean общийСтол = humansBySeat.size() > 1;
        sheetSeatBtns.forEach((s, b) -> {
            boolean свой = !общийСтол || s == viewedSeat;
            b.setEnabled(свой);
            b.setToolTipText(свой ? null
                : "Чужой планшет за общим столом не смотрят — там скрытые карты");
            if (общийСтол && s == viewedSeat) {
                b.setSelected(true);
            }
        });
        if (общийСтол) {
            sheet.setSeat(viewedSeat);
        }
    }

    /**
     * Кнопки меню карт горят только на СПЕЦ-действии: только там движок и
     * предлагает выполнить задание, сжечь его или тронуть арсенал.
     */
    private void refreshCardMenus() {
        if (objMenuBtn == null) {
            return;
        }
        boolean live = specMenuOptions != null;
        objMenuBtn.setState(live ? KpButton.State.AVAILABLE : KpButton.State.DISABLED);
        arsMenuBtn.setState(live ? KpButton.State.AVAILABLE : KpButton.State.DISABLED);
        objMenuBtn.setTexts("Задания", live ? "выполнить · сжечь" : "не сейчас");
        arsMenuBtn.setTexts("Арсенал", live ? "полка · рука" : "не сейчас");
    }

    /** Варианты текущего СПЕЦ-действия — из них строится меню карт. */
    private List<Choice> specMenuOptions;
    private kelium.core.UndoableAgent specMenuAgent;

    /**
     * МЕНЮ КАРТ ЗАДАНИЙ. Раскладывает руку заданий перед игроком: пролистал,
     * выбрал, увидел печатный текст — и либо выполнил, либо сжёг ради верхнего
     * (утиль) эффекта.
     *
     * <p>ЧТО ДОСТУПНО, РЕШАЕТ ДВИЖОК: он присылает {@code spec_objective} только
     * для тех заданий, что выполнимы прямо сейчас, и {@code spec_objective_burn}
     * только для тех, у кого есть верхний эффект. Окно ничего не проверяет само
     * — иначе оно рано или поздно разошлось бы с правилами.
     */
    void openObjectiveMenu() {
        List<Choice> opts = specMenuOptions;
        var agent = specMenuAgent;
        if (opts == null || agent == null || rec == null || rec.frames.isEmpty()) {
            return;
        }
        ReplayRecord.Frame f = rec.frames.get(rec.frames.size() - 1);
        if (f.snapshot == null || viewedSeat >= f.snapshot.players.size()) {
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(viewedSeat);
        List<kelium.gui.kp.CardMenu.Card> cards = new ArrayList<>();
        for (String id : p.objectiveHand) {
            Integer доВыполнения = indexOfSpec(opts, "spec_objective", id);
            Integer доСожжения = indexOfSpec(opts, "spec_objective_burn", id);
            List<kelium.gui.kp.CardMenu.Act> acts = new ArrayList<>();
            acts.add(new kelium.gui.kp.CardMenu.Act("Выполнить задание",
                objectiveReward(id), доВыполнения != null,
                "условие ещё не выполнено",
                доВыполнения == null ? () -> { } : () -> submitSpec(agent, доВыполнения)));
            String утиль = objectiveTop(id);
            acts.add(new kelium.gui.kp.CardMenu.Act("Сжечь ради утиля",
                утиль == null ? "" : утиль, доСожжения != null,
                утиль == null ? "у карты нет верхнего эффекта" : "сейчас нельзя",
                доСожжения == null ? () -> { } : () -> submitSpec(agent, доСожжения)));
            cards.add(new kelium.gui.kp.CardMenu.Card(id, cardName(id),
                objectiveTag(id), objectiveText(id), objectiveReward(id), утиль,
                Theme.points(), acts));
        }
        if (cards.isEmpty()) {
            return;
        }
        cardMenu.open("Задания — выполнить или сжечь", cards, null);
        frame.toFront();
    }

    /**
     * МЕНЮ ЗОНЫ АРСЕНАЛА. Три места под установленные карты, плюс то, что лежит
     * в руке: карту можно поставить или сжечь ради её эффекта.
     */
    void openArsenalMenu() {
        List<Choice> opts = specMenuOptions;
        var agent = specMenuAgent;
        if (opts == null || agent == null || rec == null || rec.frames.isEmpty()) {
            return;
        }
        ReplayRecord.Frame f = rec.frames.get(rec.frames.size() - 1);
        if (f.snapshot == null || viewedSeat >= f.snapshot.players.size()) {
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(viewedSeat);
        List<kelium.gui.kp.CardMenu.Card> cards = new ArrayList<>();
        for (String id : p.arsenalInstalled) {
            cards.add(new kelium.gui.kp.CardMenu.Card(id, cardName(id), "установлена",
                cardText(id), arsenalLabel(id, "bottom"), arsenalLabel(id, "top"),
                Theme.container(),
                List.of(new kelium.gui.kp.CardMenu.Act("Уже работает", "стоит на полке",
                    false, "установленную карту снимают только правила карт", () -> { }))));
        }
        for (String id : p.arsenalHand) {
            Integer поставить = indexOfSpec(opts, "spec_arsenal_install", id);
            Integer сжечь = indexOfSpec(opts, "spec_arsenal_burn", id);
            cards.add(new kelium.gui.kp.CardMenu.Card(id, cardName(id), "в руке",
                cardText(id), arsenalLabel(id, "bottom"), arsenalLabel(id, "top"),
                Theme.container(),
                List.of(
                    new kelium.gui.kp.CardMenu.Act("Установить", "займёт место на полке",
                        поставить != null, "сейчас нельзя",
                        поставить == null ? () -> { } : () -> submitSpec(agent, поставить)),
                    new kelium.gui.kp.CardMenu.Act("Сжечь ради эффекта", "карта уйдёт в сброс",
                        сжечь != null, "сейчас нельзя",
                        сжечь == null ? () -> { } : () -> submitSpec(agent, сжечь)))));
        }
        if (cards.isEmpty()) {
            return;
        }
        cardMenu.open("Зона арсенала", cards, null);
        frame.toFront();
    }

    /** Номер варианта такого вида для этой карты, либо null. */
    private static Integer indexOfSpec(List<Choice> opts, String kind, String cardId) {
        for (int i = 0; i < opts.size(); i++) {
            Choice c = opts.get(i);
            if (kind.equals(c.kind()) && cardId.equals(String.valueOf(c.payload()))) {
                return i;
            }
        }
        return null;
    }

    private void submitSpec(kelium.core.UndoableAgent agent, int index) {
        pendingBakeName = "СПЕЦ-действие";
        agent.submitIndex(index);
        clearDecision();
    }

    /** Печатный текст карты задания. */
    private String objectiveText(String id) {
        Object t = cardField("objectives", id, "описание");
        return t == null ? "" : String.valueOf(t);
    }

    /** Награда за выполнение — короткой строкой под кнопкой. */
    private String objectiveReward(String id) {
        Object r = cardField("objectives", id, "base_reward");
        return r instanceof Map<?, ?> m ? rewardWords(m) : "";
    }

    /** Подпись верхнего (утиль) эффекта, либо null — его нет. */
    private String objectiveTop(String id) {
        Object top = cardField("objectives", id, "top");
        if (top instanceof Map<?, ?> m && m.get("label") != null) {
            return String.valueOf(m.get("label"));
        }
        return top instanceof Map<?, ?> ? "верхний эффект" : null;
    }

    /**
     * Подпись половины карты арсенала: {@code bottom} — что она делает,
     * пока установлена, {@code top} — что даёт, если её сжечь.
     */
    private String arsenalLabel(String id, String half) {
        Object h = cardField("arsenal", id, half);
        if (h instanceof Map<?, ?> m && m.get("label") != null) {
            return String.valueOf(m.get("label"));
        }
        return null;
    }

    private String cardText(String id) {
        Object t = cardField("arsenal", id, "описание");
        return t == null ? "" : String.valueOf(t);
    }

    /** Поле карты из набора партии (null — набора нет или поля нет). */
    private Object cardField(String set, String id, String field) {
        GameConfig c = cfg;
        if (c == null || id == null) {
            return null;
        }
        try {
            Object raw = c.content.get(set).byId(id);
            return raw instanceof Map<?, ?> m ? m.get(field) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Награда словами: «3 боеприпаса · карта арсенала». */
    private static String rewardWords(Map<?, ?> m) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String k = String.valueOf(e.getKey());
            // СЫРЫЕ КЛЮЧИ ИГРОКУ НЕ ПОКАЗЫВАЕМ: «1 objective_card» в награде —
            // это не текст игры, а внутреннее имя поля.
            String слово = switch (k) {
                case "ammo" -> "боеприпасов";
                case "kelium" -> "келемия";
                case "coin", "coins" -> "монет";
                case "debris" -> "обломков";
                case "vp" -> "ПО";
                case "arsenal", "arsenal_card" -> "карта арсенала";
                case "objective", "objective_card" -> "карта задания";
                case "container", "containers" -> "контейнеров";
                case "module_red" -> "красный модуль";
                case "module_blue" -> "синий модуль";
                case "tech", "tech_step" -> "шаг науки";
                default -> k;
            };
            // Число ПОСЛЕ слова: так не надо согласовывать окончание («1 обломок»,
            // «2 обломка», «5 обломков») — и не будет уродливого «1 обломков».
            boolean безЧисла = слово.startsWith("карта") || слово.startsWith("шаг")
                || слово.endsWith("модуль");
            parts.add(безЧисла ? слово : слово + " " + e.getValue());
        }
        return String.join(" · ", parts);
    }

    private void showDecisionNow(int seat, InteractiveAgent.PendingDecision d) {
        viewedSeat = seat;
        refreshSheetSeats();
        awaitingSeat = seat;
        if (rec != null && !rec.frames.isEmpty()) {
            refreshHands(rec.frames.get(rec.frames.size() - 1));
        }

        String kind = String.valueOf(d.context().get("kind"));
        pendingKind = kind;
        kelium.core.UndoableAgent agent = humansBySeat.get(seat);
        List<Choice> options = d.options();
        specMenuOptions = "spec".equals(kind) ? options : null;
        specMenuAgent = "spec".equals(kind) ? agent : null;
        refreshCardMenus();
        String title = "Игрок " + (seat + 1) + " — " + KIND_LABELS.getOrDefault(kind, kind);

        turnLabel.setText("ВАШ ХОД — Игрок " + (seat + 1) + ": "
            + KIND_LABELS.getOrDefault(kind, kind));
        turnLabel.setForeground(Theme.seatInk(seat));

        // «Завершить ход» = вариант "пас" точки вида action.
        int passIdx = -1;
        for (int i = 0; i < options.size(); i++) {
            if ("pass".equals(options.get(i).kind()) && options.get(i).payload() == null) {
                passIdx = i;
                break;
            }
        }

        hands.clearPickable();
        field.clearSelectable();
        field.clearGhost();
        field.clearFacingChoice();
        prompt.hideAll();

        // ЦЕРЕМОНИЯ КАРТ КРУГА (просьба дизайнера 24.08): выбор карты круга и
        // отложенного приказа — крупными лицами по центру, с печатным
        // описанием под наведённой картой.
        if ("reveal_order".equals(kind) || "blind_discard".equals(kind)) {
            boolean allCards = options.stream().allMatch(c -> c.payload() instanceof String);
            if (allCards && !options.isEmpty()) {
                actionBar.idle("не сейчас");
                endBtn.setTexts("Сначала решение", KIND_LABELS.getOrDefault(kind, kind));
                endBtn.setState(KpButton.State.DISABLED);
                zoom.setVisible(false);
                List<kelium.gui.kp.CardChoiceOverlay.Card> cards = new ArrayList<>();
                for (int i = 0; i < options.size(); i++) {
                    String id = (String) options.get(i).payload();
                    int idx = i;
                    cards.add(new kelium.gui.kp.CardChoiceOverlay.Card(id,
                        orderFace(id), cardName(id), orderDesc(id), () -> {
                            ceremony.close();
                            agent.submitIndex(idx);
                            clearDecision();
                        }));
                }
                boolean reveal = "reveal_order".equals(kind);
                ceremony.open(
                    reveal ? "Выберите карту круга"
                        : "Отложите приказ — место для трофеев",
                    reveal
                        ? "Верхний приказ сыграете вы; нижний откроется, если ту же карту вскроет соперник"
                        : "Отложенная карта лежит рубашкой вверх весь раунд и принимает трофеи",
                    cards);
                refreshSteps();
                frame.toFront();
                return;
            }
        }

        // ВЫБОР ДУГИ СЕКТОРОВ (концепт §4): движок уже назвал гекс и варианты,
        // колесо мыши вращает дугу, клик по гексу ставит. Плашки-варианты внизу
        // остаются как равноправный путь.
        if ("build_facing".equals(kind) && d.context().get("hex") instanceof String fhex) {
            List<List<Integer>> variants = new ArrayList<>();
            boolean allLists = true;
            for (Choice c : options) {
                if (c.payload() instanceof List<?> l) {
                    List<Integer> sides = new ArrayList<>();
                    for (Object o : l) {
                        sides.add(((Number) o).intValue());
                    }
                    variants.add(sides);
                } else {
                    allLists = false;
                    break;
                }
            }
            if (allLists && !variants.isEmpty()) {
                actionBar.idle("не сейчас");
                endBtn.setTexts("Сначала решение", KIND_LABELS.get(kind));
                endBtn.setState(KpButton.State.DISABLED);
                if (d.context().get("btype") instanceof String bt) {
                    field.setGhost(bt, seat);
                }
                field.setFacingChoice(fhex, variants, idx -> {
                    agent.submitIndex(idx);
                    clearDecision();
                });
                List<PromptOverlay.Option> opts = new ArrayList<>();
                for (int i = 0; i < options.size(); i++) {
                    int idx = i;
                    opts.add(new PromptOverlay.Option(humanLabel(options.get(i).label()), () -> {
                        agent.submitIndex(idx);
                        clearDecision();
                    }));
                }
                prompt.showOptions(seat, title
                    + " — колесо мыши вращает дугу, клик по гексу ставит; зелёные рёбра = встанет",
                    opts);
                layoutPrompt();
                promptIn();
                refreshSteps();
                frame.toFront();
                return;
            }
        }

        // НЕОБРАТИМОЕ РЕШЕНИЕ БОЯ — модальное окно с предпросмотром (концепт §6).
        if ("attack".equals(kind) || "combat_victim".equals(kind)
                || "neutral_victim".equals(kind)) {
            actionBar.idle("не сейчас");
            endBtn.setTexts("Сначала решение", KIND_LABELS.getOrDefault(kind, kind));
            endBtn.setState(KpButton.State.DISABLED);
            showCombatDialog(seat, kind, agent, options, d);
            refreshSteps();
            frame.toFront();
            return;
        }

        if ("action".equals(kind)) {
            Map<String, Integer> avail = new LinkedHashMap<>();
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).payload() instanceof String name) {
                    avail.put(name, i);
                }
            }
            // ОТКУДА ЭТИ ДЕЙСТВИЯ — с верхней половины карты или с нижней:
            // панель ставит их в ту же половину, что и печатная карта.
            String half = d.context().get("half") instanceof String h ? h : null;
            String orderCat = d.context().get("order") instanceof String o ? o : null;
            actionBar.showDecision(avail, half, orderCat, idx -> {
                // Необратимое действие: запомнить, что запечь в ленте шагов,
                // КОГДА оно доиграет (событие action) — концепт §5.
                String name = null;
                for (var e : avail.entrySet()) {
                    if (e.getValue().equals(idx)) {
                        name = e.getKey();
                        break;
                    }
                }
                if (name != null && !kelium.core.UndoableAgent.SAFE_ACTIONS.contains(name)) {
                    pendingBakeName = ActionBar.ACTIONS.getOrDefault(name, name);
                }
                agent.submitIndex(idx);
                clearDecision();
            });
            if (passIdx >= 0) {
                int pi = passIdx;
                endBtn.setTexts("Завершить ход",
                    d.context().get("remaining") instanceof Number n
                        ? "доступно действий: " + n : "");
                endBtn.setState(KpButton.State.AVAILABLE);
                endBtn.onClick(() -> {
                    agent.submitIndex(pi);
                    clearDecision();
                });
            }
        } else {
            actionBar.idle("не сейчас");
            endBtn.setTexts("Сначала решение", KIND_LABELS.getOrDefault(kind, kind));
            endBtn.setState(KpButton.State.DISABLED);

            Map<String, Integer> hexToIndex = hexTargets(kind, options);
            if (hexToIndex != null) {
                // Призрак здания за курсором — для стройки и переноса (§4).
                boolean ghost = ("build_hex".equals(kind) || "move_hex".equals(kind))
                    && d.context().get("btype") instanceof String;
                if (ghost) {
                    field.setGhost((String) d.context().get("btype"), seat);
                    prompt.showHint(seat, title,
                        "Здание встаёт под курсор — наведись на сектор, клик по подсвеченному гексу ставит");
                } else {
                    prompt.showHint(seat, title, "Выберите гекс на поле — допустимые подсвечены");
                }
                field.setSelectable(hexToIndex.keySet(), hexId -> {
                    Integer idx = hexToIndex.get(hexId);
                    if (idx != null) {
                        agent.submitIndex(idx);
                        clearDecision();
                    }
                });
            } else {
                // Карты из руки — рука сама орган ввода; остальное — плашки.
                Map<String, Integer> cardToOption = new LinkedHashMap<>();
                List<PromptOverlay.Option> rest = new ArrayList<>();
                for (int i = 0; i < options.size(); i++) {
                    Choice c = options.get(i);
                    int idx = i;
                    if (c.payload() instanceof String id && inAnyHand(id)) {
                        cardToOption.put(id, i);
                    } else {
                        String label = c.label() == null || c.label().isEmpty()
                            ? String.valueOf(c.payload()) : c.label();
                        rest.add(new PromptOverlay.Option(humanLabel(label), () -> {
                            agent.submitIndex(idx);
                            clearDecision();
                        }));
                    }
                }
                if (!cardToOption.isEmpty()) {
                    hands.setPickable(cardToOption, (cardId, idx) -> {
                        agent.submitIndex(idx);
                        clearDecision();
                    });
                    prompt.showOptions(seat, title + " — карта в руке подсвечена", rest);
                } else {
                    prompt.showOptions(seat, title, rest);
                }
            }
        }
        layoutPrompt();
        promptIn();
        refreshSteps();
        frame.toFront();
    }

    /**
     * Модальное окно залпа/жертвы: затемнение, предпросмотр (кто стоит в цели,
     * расход БПР), варианты красными плашками, «Прекратить бой» — серой; Esc —
     * тоже отказ. Первый залп честно предупреждает, что запечёт откат.
     */
    private void showCombatDialog(int seat, String kind, kelium.core.UndoableAgent agent,
                                   List<Choice> options, InteractiveAgent.PendingDecision d) {
        zoom.setVisible(false);   // увеличенная карта не должна висеть под модалкой
        String target = d.context().get("target") instanceof String t ? t : null;
        String title = switch (kind) {
            case "attack" -> "Бой — атака" + (target == null ? "" : " по гексу " + target);
            case "combat_victim" -> "Кого поразить"
                + (target == null ? "" : " в гексе " + target);
            default -> "Какой нейтрал" + (target == null ? "" : " в гексе " + target);
        };
        String warn = "attack".equals(kind) && agent.canUndo()
            ? "Первая атака сделает откат шагов этого хода недоступным"
            : "Бой необратим";

        List<String> info = new ArrayList<>();
        int dmg = 1;
        if (cfg != null) {
            try {
                dmg = cfg.ruleset.getInt("combat_model.all_attacks_damage");
            } catch (RuntimeException ignore) {
                // печатный урон недоступен — остаётся правило по умолчанию
            }
        }
        int enemies = 0;
        int lastEnemyLeft = -1;
        if (target != null && rec != null && !rec.frames.isEmpty()) {
            ReplayRecord.Frame f = rec.frames.get(rec.frames.size() - 1);
            if (f.snapshot != null) {
                int listed = 0;
                for (ReplayRecord.Tok t : f.snapshot.tokens) {
                    if (target.equals(t.hexId)) {
                        if (t.owner != seat) {
                            enemies++;
                            lastEnemyLeft = t.hp - t.damage;
                        }
                        if (listed < 4) {
                            String nm = t.building
                                ? kelium.report.Labels.buildingLabel(t.type, t.level)
                                : kelium.report.Labels.unitName(t.type);
                            info.add("В цели: " + nm + " · прочность "
                                + (t.hp - t.damage) + "/" + t.hp
                                + " · " + seatName(t.owner));
                            listed++;
                        }
                    }
                }
            }
        }
        // ПРОГНОЗ — предпросмотр последствий до подтверждения (приёмка №9):
        // урон печатный, из свода партии, не пересчёт «на глазок».
        info.add("Атака снимает " + dmg + " прочности (печатное правило свода)");
        if ("attack".equals(kind) && enemies == 1 && lastEnemyLeft > 0
                && lastEnemyLeft <= dmg) {
            info.add("Эта атака УНИЧТОЖИТ цель");
        }
        info.add("После боя пострадавшие могут ответить своим боем");

        List<kelium.gui.kp.ConfirmDialog.Option> opts = new ArrayList<>();
        kelium.gui.kp.ConfirmDialog.Option cancel = null;
        for (int i = 0; i < options.size(); i++) {
            Choice c = options.get(i);
            int idx = i;
            Runnable pick = () -> {
                confirm.close();
                agent.submitIndex(idx);
                clearDecision();
            };
            if ("pass".equals(c.kind()) && c.payload() == null) {
                cancel = new kelium.gui.kp.ConfirmDialog.Option(
                    "Прекратить бой", "выйти без атаки — ничего не потеряно", pick);
                continue;
            }
            String label = c.label() == null ? "" : c.label();
            String sub = null;
            if (c.payload() instanceof Map<?, ?> pl) {
                label = attackLabelRu(label, pl);
                if (pl.get("ammo") instanceof Number n) {
                    sub = "расход БПР: " + n;
                }
            }
            opts.add(new kelium.gui.kp.ConfirmDialog.Option(label, sub, pick));
        }
        confirm.open(title, warn, info, opts, cancel);
    }

    /** «infantry.universal->units» → «Пехота · универсальная атака → по войскам». */
    private String attackLabelRu(String raw, Map<?, ?> payload) {
        int dot = raw.indexOf('.');
        int arrow = raw.indexOf("->");
        if (dot <= 0 || arrow <= dot) {
            return raw;
        }
        String unit = kelium.report.Labels.unitName(raw.substring(0, dot));
        String row = switch (raw.substring(dot + 1, arrow)) {
            case "universal" -> "универсальная атака";
            case "special", "specialized" -> "спец-атака";
            default -> "атака «" + raw.substring(dot + 1, arrow) + "»";
        };
        String tcat = Boolean.TRUE.equals(payload.get("neutral")) ? "снос нейтрала"
            : switch (String.valueOf(payload.get("tcat"))) {
                case "infantry" -> "по пехоте";
                case "vehicle" -> "по технике";
                case "aircraft" -> "по авиации";
                case "units" -> "по войскам";
                case "buildings_towers" -> "по зданиям и вышкам";
                case "any" -> "по любой цели";
                default -> "по " + payload.get("tcat");
            };
        String cap = unit.isEmpty() ? raw.substring(0, dot) : unit;
        return Character.toUpperCase(cap.charAt(0)) + cap.substring(1)
            + " · " + row + " → " + tcat;
    }

    private boolean inAnyHand(String id) {
        if (rec == null || rec.frames.isEmpty()) {
            return false;
        }
        ReplayRecord.Frame f = rec.frames.get(rec.frames.size() - 1);
        if (f.snapshot == null || viewedSeat >= f.snapshot.players.size()) {
            return false;
        }
        ReplayRecord.Player p = f.snapshot.players.get(viewedSeat);
        return p.orderHand.contains(id) || p.objectiveHand.contains(id)
            || p.arsenalHand.contains(id);
    }

    /** «Всё или ничего»: см. концепт §3, семья A. */
    Map<String, Integer> hexTargets(String kind, List<Choice> options) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < options.size(); i++) {
            String hexId = hexIdOf(kind, options.get(i));
            if (hexId == null) {
                return null;
            }
            out.put(hexId, i);
        }
        return out.isEmpty() ? null : out;
    }

    String hexIdOf(String kind, Choice c) {
        if (HEX_TARGET_KINDS.contains(kind) && c.payload() instanceof String s) {
            return s;
        }
        if ("move".equals(kind) && c.payload() instanceof Map<?, ?> m
            && m.get("to") instanceof String s) {
            return s;
        }
        if ("maneuver_unit".equals(kind) && c.label() != null) {
            int at = c.label().indexOf('@');
            if (at >= 0) {
                return c.label().substring(at + 1);
            }
        }
        return null;
    }

    private void clearDecision() {
        confirm.close();
        ceremony.close();
        cardMenu.close();
        specMenuOptions = null;
        specMenuAgent = null;
        refreshCardMenus();
        zoom.setVisible(false);
        prompt.hideAll();
        field.clearSelectable();
        field.clearGhost();
        field.clearFacingChoice();
        hands.clearPickable();
        actionBar.idle("ход соперника");
        awaitingSeat = null;
        pendingKind = null;
        endBtn.setTexts("Ход соперника…", "");
        endBtn.setState(KpButton.State.DISABLED);
        endBtn.onClick(null);
        refreshSteps();
    }

    private String cardName(String id) {
        return rec == null ? id : rec.cardNames.getOrDefault(id, id);
    }

    /** Данные карты приказа из контента партии (null — не приказ/нет данных). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> orderData(String id) {
        GameConfig c = cfg;
        if (c == null || id == null) {
            return null;
        }
        try {
            return (Map<String, Object>) c.content.get("orders").byId(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private kelium.gui.kp.OrderCardFace.Info orderFace(String id) {
        return kelium.gui.kp.OrderCardFace.Info.of(id, orderData(id));
    }

    /** Печатное «описание» карты приказа — как она играется. */
    private String orderDesc(String id) {
        Map<String, Object> d = orderData(id);
        Object t = d == null ? null : d.get("описание");
        return t == null ? "" : String.valueOf(t);
    }
}
