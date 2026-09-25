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

    /**
     * СТОЛ ДО ПЕРВОГО ХОДА — точка, от которой партию можно проиграть заново.
     *
     * <p>На ней стоит ОТКАТ (просьба дизайнера 25.09.2026: «откатывать свои
     * действия назад в рамках хода до самого начала, в любое действие»). Движок
     * воспроизводим: та же копия стола и та же лента решений дают ту же партию.
     * Поэтому отменить можно ЛЮБОЕ решение — и бой, и рынок, и науку, и выбор
     * гекса посреди действия: партия переигрывается с этой копии по ленте до
     * нужного места за доли секунды, и игрока снова спрашивают там же.
     *
     * <p>Зерно ГСЧ закреплено: {@link GameState#deepCopy} даёт копии НОВЫЙ ГСЧ с
     * этим зерном, и каждая переигровка идёт по тому же потоку случайностей.
     */
    record StartTable(GameState table, long seed) {

        static StartTable of(GameState built) {
            long s = built.rng.nextLong();
            return new StartTable(built.deepCopy(s), s);
        }

        /** Свежая копия стола — живая партия всегда играется на копии. */
        GameState fresh() {
            return table.deepCopy(seed);
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
    private ChipLabel chipTrophy;
    /** Строка ресурсов — собирается с зоной игрока, живёт в верхней полосе. */
    private JPanel chipsPanel;
    FieldView field;
    private BoardsPanel boards;
    private BoardSheet sheet;

    /** Планшет смотрящего места — для прогонщиков и тестов. */
    BoardSheet sheetForTest() {
        return sheet;
    }
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
    ZoomCard zoom;
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
    /** Ошибка, оборвавшая партию (null — партия не ломалась). */
    private volatile Throwable failure;
    /**
     * ЛЕНТА ПРИНЯТЫХ РЕШЕНИЙ — из неё складывается сохранение партии. Пишется
     * на каждом решении любого места; см. {@link MoveLog}.
     */
    final List<Integer> moves = java.util.Collections.synchronizedList(
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

        // ПРЕЖНЯЯ НИЖНЯЯ ЗОНА (руки плашками, панель действий, кнопки ящиков)
        // на экран больше не ставится (просьба дизайнера 25.09.2026: «никаких
        // кнопок слева снизу и справа снизу»). Её детали собираются, потому
        // что на них держатся тесты и служебные пути, а на месте зоны лежит
        // СТОЛ ИГРОКА — планшеты, вскрытый приказ, карты.
        buildPlayerZone();
        frame.add(buildTopBar(), BorderLayout.NORTH);
        // СТОЛ ИГРОКА — ВО ВСЮ ШИРИНУ ОКНА: печатные планшеты широкие, и в
        // колонке поля они выходили мелкими. Вкладки ящиков и полоса хода —
        // только над столом, по бокам поля.
        JPanel upper = new JPanel(new BorderLayout());
        upper.add(buildTabStrip(), BorderLayout.WEST);
        upper.add(buildCenter(), BorderLayout.CENTER);
        upper.add(buildRail(), BorderLayout.EAST);
        table = buildTable();
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(
            javax.swing.JSplitPane.VERTICAL_SPLIT, upper, table);
        split.setResizeWeight(1.0);
        split.setBorder(null);
        split.setDividerSize(Theme.px(6));
        split.setContinuousLayout(true);
        tableSplit = split;
        frame.add(split, BorderLayout.CENTER);

        zoom = new ZoomCard();
        // Выше прежнего: под лицом карты теперь помещается весь печатный текст
        // (условие, награда, усиленная награда, утиль), а не одна строка.
        zoom.setSize(Theme.px(300), Theme.px(420));
        zoom.setVisible(false);
        frame.getLayeredPane().add(zoom, JLayeredPane.POPUP_LAYER);

        // Церемония выбора карты круга/отложенного приказа — крупными лицами.
        ceremony = new kelium.gui.kp.CardChoiceOverlay();
        ceremony.setArt(id -> {
            ReplayRecord.Player p = viewedPlayer();
            java.awt.image.BufferedImage o = orderArt(id, p == null ? null : p.orderColor);
            return o != null ? o : cardFace(id);
        });
        frame.getLayeredPane().add(ceremony, JLayeredPane.MODAL_LAYER);

        // Модальное окно необратимого — во весь слой окна, поверх всего.
        confirm = new kelium.gui.kp.ConfirmDialog();
        frame.getLayeredPane().add(confirm, JLayeredPane.MODAL_LAYER);

        // ШТОРКА ПЕРЕДАЧИ — ВЫШЕ МОДАЛОК: она прячет экран целиком, и если её
        // перекроет хоть что-нибудь, прятать будет нечего.
        cardMenu = new kelium.gui.kp.CardMenu();
        frame.getLayeredPane().add(cardMenu, JLayeredPane.MODAL_LAYER);

        spread = new kelium.gui.kp.CardSpread();
        frame.getLayeredPane().add(spread, JLayeredPane.MODAL_LAYER);

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

        // Ctrl+Z — шаг назад из любого места окна.
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "undo-step");
        frame.getRootPane().getActionMap().put("undo-step", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                undoLast();
            }
        });
        // Esc — закрыть раскрытый пузырь вариантов (на поле и на столе).
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
            "close-bubble");
        frame.getRootPane().getActionMap().put("close-bubble", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                field.bubbles.closeBubble();
                field.repaint();
                if (table != null) {
                    table.closeBubble();
                }
                if (spread != null) {
                    spread.close();
                }
            }
        });

        frame.setSize(Theme.px(1500), Theme.px(950));
        frame.setMinimumSize(new Dimension(Theme.px(1150), Theme.px(760)));
        frame.setLocationByPlatform(true);
        Offscreen.show(frame);
        placeTableDivider();
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean placed;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!placed) {
                    placed = true;
                    placeTableDivider();
                }
            }
        });
    }

    /** Стол игрока занимает свою высоту снизу, остальное — поле. */
    private void placeTableDivider() {
        if (tableSplit != null && tableSplit.getHeight() > 0) {
            tableSplit.setDividerLocation(Math.max(Theme.px(300),
                tableSplit.getHeight() - Theme.px(340)));
        }
    }

    kelium.gui.kp.PlayerTable table;
    private javax.swing.JSplitPane tableSplit;
    /** Лист планшетов для стола: свой, чтобы ящик «Планшет» листал места независимо. */
    private BoardSheet tableSheet;

    private kelium.gui.kp.PlayerTable buildTable() {
        kelium.gui.kp.PlayerTable t = new kelium.gui.kp.PlayerTable();
        t.setMinimumSize(new Dimension(Theme.px(400), Theme.px(180)));
        tableSheet = new BoardSheet(session, 0);
        t.setBoards(new kelium.gui.kp.PlayerTable.BoardsArt() {
            @Override
            public double aspect() {
                return tableSheet.tableAspect();
            }

            @Override
            public double[] storageBox() {
                return tableSheet.tableStorageBox();
            }

            @Override
            public int paint(java.awt.Graphics2D g, int x, int y, int width,
                             Map<String, java.awt.Rectangle> hits,
                             Map<String, java.awt.Shape> outlines) {
                return tableSheet.paintTableBoards(g, x, y, width, hits, outlines);
            }
        });
        t.setCards(this::anyFace, kind -> switch (kind) {
            case "arsenal" -> kelium.report.Textures.card("deck_arsenal", "deck");
            case "objective" -> kelium.report.Textures.card("deck_objectives", "deck");
            default -> null;
        }, this::cardName, this::objectiveTag);
        t.onCardHover((id, r) -> showTableZoom(id, r), () -> zoom.setVisible(false));
        t.onOpen(this::openSpread);
        return t;
    }

    /** Лицо любой карты игрока: задания, арсенал, приказы. */
    private java.awt.image.BufferedImage anyFace(String id) {
        java.awt.image.BufferedImage f = cardFace(id);
        if (f != null) {
            return f;
        }
        ReplayRecord.Player p = viewedPlayer();
        return orderArt(id, p == null ? null : p.orderColor);
    }

    /** Раскрытые карты игрока — поверх всего окна. */
    kelium.gui.kp.CardSpread spread;

    /**
     * РАСКРЫТЬ ГРУППУ КАРТ СТОЛА (просьба дизайнера 25.09.2026: «зона с
     * картами, которая раскрывается, которую можно посмотреть»). Под каждой
     * картой — то, что с ней можно сделать прямо сейчас: варианты берутся из
     * текущего решения движка, из того же списка, что обводит карты на столе.
     */
    void openSpread(String group) {
        ReplayRecord.Player p = viewedPlayer();
        if (p == null) {
            return;
        }
        List<String> ids;
        String title;
        switch (group) {
            case "objectives" -> {
                ids = p.objectiveHand;
                title = "Задания на руке";
            }
            case "super" -> {
                ids = p.superObjectives;
                title = "Супер-задания";
            }
            case "arsenal" -> {
                ids = p.arsenalHand;
                title = "Арсенал: закрытые карты";
            }
            case "installed" -> {
                ids = p.arsenalInstalled;
                title = "Арсенал: установленные карты";
            }
            case "orders" -> {
                ids = p.orderHand;
                title = "Приказы в руке";
            }
            case "played" -> {
                ids = p.orderPlayed;
                title = "Сыграно в этом раунде";
            }
            case "dump" -> {
                openDumpSpread(p);
                return;
            }
            default -> {
                return;
            }
        }
        List<kelium.gui.kp.CardSpread.Card> cards = new ArrayList<>();
        boolean any = false;
        for (String id : ids) {
            List<kelium.gui.kp.FieldBubbles.Opt> acts = table.choicesFor("card:" + id);
            any |= !acts.isEmpty();
            String note = objectiveTag(id);
            if ("installed".equals(group)) {
                note = "установлена";
            }
            cards.add(new kelium.gui.kp.CardSpread.Card(id, anyFace(id), cardName(id),
                note == null ? null : ("installed".equals(group) ? note : "выполнено " + note),
                acts));
        }
        if (cards.isEmpty()) {
            return;
        }
        zoom.setVisible(false);
        spread.setBounds(0, 0, frame.getLayeredPane().getWidth(),
            frame.getLayeredPane().getHeight());
        spread.open(title, any ? "Выберите, что сыграть, — или щёлкните мимо карт"
                : "Щёлкните мимо карт, чтобы сложить их", cards,
            Theme.seat(viewedSeat), null);
    }

    /** Свалка раскрытием: жетоны врагов трофейной стороной. */
    private void openDumpSpread(ReplayRecord.Player p) {
        List<kelium.gui.kp.CardSpread.Card> cards = new ArrayList<>();
        for (ReplayRecord.DestroyedToken t : p.destroyedCard) {
            String nm = t.building ? kelium.report.Labels.buildingName(t.type, t.level)
                : kelium.report.Labels.unitName(t.type);
            cards.add(new kelium.gui.kp.CardSpread.Card("t" + t.uid,
                kelium.report.Textures.trophySide(t.type, t.level, t.value),
                nm, nm + " · трофеев " + t.value, List.of()));
        }
        if (cards.isEmpty()) {
            return;
        }
        spread.setBounds(0, 0, frame.getLayeredPane().getWidth(),
            frame.getLayeredPane().getHeight());
        spread.open("Свалка", "Уничтоженные жетоны врагов на отложенном приказе", cards,
            Theme.seat(viewedSeat), null);
    }

    /** Печатное лицо карты по id — задания, арсенал любых наборов. */
    java.awt.image.BufferedImage cardFace(String id) {
        if (id == null) {
            return null;
        }
        for (String deck : List.of("objective", "objective_start", "objective_super",
                "arsenal", "arsenal_start", "arsenal_super", "market", "container")) {
            java.awt.image.BufferedImage img = kelium.report.Textures.cardFace(deck, id);
            if (img != null) {
                return img;
            }
        }
        return null;
    }

    /** Печатное лицо приказа (безопасность — по цвету колоды игрока). */
    private java.awt.image.BufferedImage orderArt(String id, String color) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("security")) {
            String c = color == null ? "" : color;
            String alt = "red".equals(c) ? "scarlet" : "scarlet".equals(c) ? "red" : "";
            return kelium.report.Textures.orderCard("security_" + c,
                alt.isEmpty() ? null : "security_" + alt, "security");
        }
        return kelium.report.Textures.orderCard(id);
    }

    /** Увеличенная карта со стола — печатным лицом над самой картой. */
    private void showTableZoom(String id, java.awt.Rectangle inTable) {
        if (inTable == null) {
            return;
        }
        java.awt.image.BufferedImage img = cardFace(id);
        String note = null;
        String tag = objectiveTag(id);
        if (tag != null) {
            note = "выполнено " + tag;
        }
        if (img != null) {
            zoom.showFace(img, note);
            zoom.setSize(zoom.faceSize(Theme.px(440)));
        } else {
            boolean objective = false;
            ReplayRecord.Player p = viewedPlayer();
            if (p != null) {
                objective = p.objectiveHand.contains(id);
            }
            CardTile dummy = new CardTile(id, cardName(id), Theme.points(), null, null);
            showZoom(dummy, objective ? "Задания" : "Арсенал");
            zoom.setSize(Theme.px(300), Theme.px(420));
        }
        Point p = SwingUtilities.convertPoint(table, inTable.x, inTable.y, frame.getLayeredPane());
        int x = Math.max(Theme.px(6), Math.min(p.x + inTable.width / 2 - zoom.getWidth() / 2,
            frame.getLayeredPane().getWidth() - zoom.getWidth() - Theme.px(6)));
        int y = Math.max(Theme.px(6), p.y - zoom.getHeight() - Theme.px(6));
        zoom.setLocation(x, y);
    }

    /** Игрок, на которого сейчас смотрит окно (по последнему кадру). */
    private ReplayRecord.Player viewedPlayer() {
        if (rec == null || rec.frames.isEmpty()) {
            return null;
        }
        ReplayRecord.Frame f = rec.frames.get(rec.frames.size() - 1);
        if (f.snapshot == null || viewedSeat >= f.snapshot.players.size()) {
            return null;
        }
        return f.snapshot.players.get(viewedSeat);
    }

    /** Вскрытый в этом круге приказ каждого места. */
    private final Map<Integer, String> revealed = new java.util.HashMap<>();

    /** Перерисовать стол игрока по последнему кадру. */
    private void refreshTable() {
        if (table == null) {
            return;
        }
        ReplayRecord.Player p = viewedPlayer();
        if (p == null) {
            table.setState(null);
            return;
        }
        tableSheet.setSeat(viewedSeat);
        String order = revealed.get(viewedSeat);
        java.awt.image.BufferedImage dumpBack = null;
        if (p.orderSetAside != null) {
            String c = p.orderColor == null ? "" : p.orderColor;
            String alt = "red".equals(c) ? "scarlet" : "scarlet".equals(c) ? "red" : "";
            dumpBack = kelium.report.Textures.orderCard("back_" + c,
                alt.isEmpty() ? null : "back_" + alt, "back");
            if (dumpBack == null) {
                dumpBack = kelium.report.Textures.card("deck_orders", "deck");
            }
        }
        List<kelium.gui.kp.PlayerTable.Trophy> dump = new ArrayList<>();
        int dumpValue = 0;
        for (ReplayRecord.DestroyedToken t : p.destroyedCard) {
            dump.add(new kelium.gui.kp.PlayerTable.Trophy(
                kelium.report.Textures.trophySide(t.type, t.level, t.value), t.value));
            dumpValue += t.value;
        }
        table.setState(new kelium.gui.kp.PlayerTable.State(viewedSeat, seatName(viewedSeat),
            order, order == null ? null : orderFace(order),
            order == null ? null : orderArt(order, p.orderColor),
            List.copyOf(p.objectiveHand), List.copyOf(p.superObjectives),
            List.copyOf(p.arsenalHand), List.copyOf(p.arsenalInstalled),
            List.copyOf(p.orderHand), List.copyOf(p.orderPlayed),
            dumpBack == null && p.orderSetAside != null
                ? new java.awt.image.BufferedImage(10, 16, java.awt.image.BufferedImage.TYPE_INT_RGB)
                : dumpBack,
            dump, dumpValue,
            awaitingSeat == null ? "ход соперника" : "сначала решение"));
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
        if (chipsPanel != null) {
            bar.add(chipsPanel);
        }

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
        // ЯЩИКИ — ВКЛАДКАМИ СБОКУ (25.09.2026): внизу теперь стол игрока из
        // компонентов, и кнопкам ящиков там больше не место.
        addDrawerTab(strip, "Наука и рынок");
        addDrawerTab(strip, "Планшет");
        addDrawerTab(strip, "Сброс приказов");
        addDrawerTab(strip, "Журнал");
        strip.add(javax.swing.Box.createVerticalGlue());
        return strip;
    }

    private void addDrawerTab(JPanel strip, String name) {
        KpTab tab = new KpTab(name, () -> toggleDrawer(name));
        tab.setToolTipText(switch (name) {
            case "Наука и рынок" -> "Доска науки и активная карта рынка — открываются поверх поля в любой момент";
            case "Планшет" -> "Планшеты игроков: склад, войска, трофеи, арсенал — свой и соперников";
            case "Сброс приказов" -> "Ваш личный сброс приказов: карты, разыгранные в этом раунде";
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
        // Карточка вопроса не прячется под выехавший ящик.
        field.bubbles.setDockInset(openDrawerSpan());
        field.repaint();
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
        stepsCaption = caption("ШАГИ ХОДА — щелчок отменяет шаг");
        top.add(stepsCaption);
        // ОТКАТ ВСЕГДА ПОД РУКОЙ: шаг назад и к началу хода. Отменяется
        // любое решение круга — это переигровка партии, а не заплатка.
        JPanel undoRow = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0 " + Theme.px(8) + " " + Theme.px(6) + " " + Theme.px(8)
                + ", gapx " + Theme.px(6), "[grow,fill][grow,fill]"));
        undoRow.setOpaque(false);
        undoBtn = new KpButton("Шаг назад", "Ctrl+Z", null);
        undoBtn.setToolTipText("Отменить последнее своё решение в этом круге");
        undoBtn.onClick(this::undoLast);
        undoBtn.setState(KpButton.State.DISABLED);
        undoAllBtn = new KpButton("К началу хода", "", null);
        undoAllBtn.setToolTipText("Отменить всё, что вы решили в этом круге, "
            + "включая вскрытие приказа");
        undoAllBtn.onClick(this::undoAll);
        undoAllBtn.setState(KpButton.State.DISABLED);
        undoRow.add(undoBtn, "h " + Theme.px(40) + "!");
        undoRow.add(undoAllBtn, "h " + Theme.px(40) + "!");
        undoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(undoRow);
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
        chipTrophy = new ChipLabel("TROPHY", Theme.neutral(), "трофеи");
        chipTrophy.setToolTipText("Трофеи: на складе / потолок");
        JPanel me = new JPanel();
        me.setOpaque(false);
        me.setLayout(new BoxLayout(me, BoxLayout.Y_AXIS));
        // ФИШКИ В ДВЕ СТРОКИ ПО ТРИ. Одной лентой из пяти они тянулись на
        // пол-окна и отжимали карты приказов в щель.
        // РЕСУРСЫ — ОДНОЙ СТРОКОЙ В ВЕРХНЕЙ ПОЛОСЕ (стол игрока внизу занят
        // компонентами, там цифрам не место).
        JPanel chipsRow = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(5)));
        chipsRow.setOpaque(false);
        for (ChipLabel c : List.of(chipVp, chipCoin, chipKelium, chipAmmo, chipTrophy)) {
            chipsRow.add(c);
        }
        chipsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        chipsPanel = chipsRow;
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

    /** Показать увеличенную карту — для прогонщиков и тестов. */
    void showZoomForTest(CardTile tile, String group) {
        showZoom(tile, group);
    }

    private void showZoom(CardTile tile, String group) {
        if ("Приказы".equals(group) && tile.orderFaceInfo() != null) {
            zoom.showOrder(tile.orderFaceInfo(), tile.cardName(), orderDesc(tile.cardId));
            placeZoom(tile);
            return;
        }
        String type;
        String detail;
        double progress = -1;
        if ("Задания".equals(group)) {
            type = "Задание";
            StringBuilder t = new StringBuilder();
            try {
                var card = kelium.engine.cards.CardRegistry.objective(tile.cardId);
                GameState s = liveState;
                if (card != null && s != null) {
                    var ctx = new kelium.engine.cards.EngineCardContext(s, viewedSeat);
                    progress = card.progress(ctx);
                    t.append(card.needed(ctx));
                }
            } catch (RuntimeException ignore) {
                // прогресс — украшение подсказки; без него карта всё равно видна
            }
            добавить(t, "", objectiveText(tile.cardId));
            добавить(t, "НАГРАДА: ", objectiveReward(tile.cardId));
            добавить(t, "УСИЛЕННАЯ: ", objectiveBonus(tile.cardId));
            добавить(t, "УТИЛЬ (сжечь): ", objectiveTop(tile.cardId));
            detail = t.toString();
        } else if ("Приказы".equals(group)) {
            type = "Приказ";
            detail = orderDesc(tile.cardId);
        } else {
            // АРСЕНАЛ ПОКАЗЫВАЛСЯ ПУСТЫМ: у карты бралось одно имя, а обе
            // половины — что она делает установленной и что даёт, если её
            // сжечь, — не показывались нигде, кроме меню карт.
            type = "Арсенал";
            StringBuilder t = new StringBuilder();
            добавить(t, "", cardText(tile.cardId));
            добавить(t, "РАБОТАЕТ: ", arsenalLabel(tile.cardId, "bottom"));
            добавить(t, "УТИЛЬ (сжечь): ", arsenalLabel(tile.cardId, "top"));
            detail = t.toString();
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

    /**
     * ПОКОЛЕНИЕ ПАРТИИ. Откат перезапускает движок, и у старого потока ещё могут
     * быть в пути кадры и точки решения: всё, что пришло не от текущего
     * поколения, окно молча выбрасывает.
     */
    private volatile int generation;
    /** Стол до первого хода — от него переигрывается партия при откате. */
    private volatile StartTable startTable;

    /**
     * ЗАПИСЬ О ПРИНЯТОМ РЕШЕНИИ — рядом с номером варианта в ленте. По ней
     * окно знает, чьё это решение, какого вида, в каком круге и как его назвать
     * человеку в списке шагов хода.
     */
    record Decision(int seat, String kind, String label, int round, int circle) {
    }

    /** Решения партии — параллельно ленте {@link #moves}, под её же замком. */
    final List<Decision> decisions = new ArrayList<>();

    /** Пишущая обёртка: номер варианта в ленту и запись о решении рядом. */
    private final class Journaled extends Agent {

        private final Agent inner;

        Journaled(Agent inner) {
            super(inner.seat, inner.name);
            this.inner = inner;
        }

        @Override
        public Choice choose(GameState state, List<Choice> options, Map<String, Object> context) {
            Choice picked = inner.choose(state, options, context);
            int idx = options.indexOf(picked);
            synchronized (moves) {
                moves.add(idx);
                decisions.add(new Decision(seat, String.valueOf(context.get("kind")),
                    decisionWords(String.valueOf(context.get("kind")), picked),
                    state.round, state.circle));
            }
            return picked;
        }

        @Override
        public void observeEvent(Map<String, Object> event) {
            inner.observeEvent(event);
        }

        @Override
        public void observePublicEvent(Map<String, Object> event) {
            inner.observePublicEvent(event);
        }
    }

    private void runGame() {
        playSession(generation, List.copyOf(replay));
    }

    /**
     * ХОД БОТА — НЕ ТЕЛЕПОРТОМ (концепт §1.5). Движок играет бота мгновенно,
     * и живой игрок видел только итог: поле перескакивало. Здесь поток движка
     * чуть придерживается на каждом ВИДИМОМ событии бота — стройка, шаг, удар,
     * — чтобы поле успело показать его подсветкой. При переигровке ленты
     * (откат, загрузка) пауз нет, и если за столом нет живых — тоже.
     */
    private void paceBot(ReplayRecord r) {
        if (catchingUp || humansBySeat.isEmpty() || r.frames.isEmpty()
                || Offscreen.on()) {
            return;
        }
        ReplayRecord.Frame f = r.frames.get(r.frames.size() - 1);
        if (f.seat == null || humansBySeat.containsKey(f.seat)) {
            return;
        }
        long ms = !f.highlight.isEmpty() ? 420 : "action".equals(f.type) ? 260 : 0;
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * РЕШЕНИЕ СЛОВАМИ — строка в списке шагов хода. Зовётся из потока движка,
     * поэтому берёт только неизменяемое (подписи, свод партии).
     */
    String decisionWords(String kind, Choice c) {
        Object p = c.payload();
        String raw = humanLabel(c.label() == null ? String.valueOf(p) : c.label());
        if ("pass".equals(c.kind()) && p == null) {
            return "action".equals(kind) ? "Завершил ход"
                : KIND_LABELS.getOrDefault(kind, "Решение") + ": отказ";
        }
        return switch (kind) {
            case "action" -> p instanceof String a ? ActionBar.ACTIONS.getOrDefault(a, a) : raw;
            case "reveal_order" -> "Вскрыт приказ «" + cardNameSafe(String.valueOf(p)) + "»";
            case "blind_discard" -> "Отложен приказ «" + cardNameSafe(String.valueOf(p)) + "»";
            case "build_pick" -> p instanceof Map<?, ?> m && m.get("btype") != null
                ? "Строю: " + kelium.report.Labels.buildingLabel(
                    String.valueOf(m.get("btype")).toLowerCase(java.util.Locale.ROOT),
                    m.get("level") instanceof Number n ? n.intValue() : null)
                : "Строю: " + raw;
            case "build_hex", "tower_hex", "cu_hex" -> "Гекс стройки " + hexWords(String.valueOf(p));
            case "build_facing", "cu_sides" -> "Поворот здания";
            case "move" -> p instanceof Map<?, ?> m && m.get("to") != null
                ? "Шаг на " + hexWords(String.valueOf(m.get("to"))) : "Шаг: " + raw;
            case "maneuver_hex" -> "Манёвр через " + hexWords(String.valueOf(p));
            case "combat_source" -> "Бой из " + hexWords(String.valueOf(p));
            case "attack" -> p instanceof Map<?, ?> m ? "Атака " + attackLabelRu(
                c.label() == null ? "" : c.label(), m) : "Атака";
            case "spec" -> "СПЕЦ: " + kelium.gui.kp.ChoiceWords.label(kind, c, this::cardNameSafe);
            default -> {
                String w = kelium.gui.kp.ChoiceWords.label(kind, c, this::cardNameSafe);
                yield KIND_LABELS.containsKey(kind) ? cap(KIND_LABELS.get(kind)) + ": " + w : w;
            }
        };
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Гекс словами: координаты, как их пишет правило, а не внутренний id. */
    private static String hexWords(String hexId) {
        if (hexId != null && hexId.startsWith("h")) {
            return "(" + hexId.substring(1).replace('_', ',') + ")";
        }
        return String.valueOf(hexId);
    }

    /** Имя карты без обращения к живой записи (её пишет поток Swing). */
    private String cardNameSafe(String id) {
        ReplayRecord r = rec;
        String n = r == null ? null : r.cardNames.get(id);
        return n == null ? id : n;
    }

    /**
     * ОДИН ПРОГОН ДВИЖКА. Первый прогон строит стол; каждый откат запускает
     * новый прогон того же стола, и лента {@code prefix} проигрывается до
     * места отката без вопросов игроку.
     */
    private void playSession(int gen, List<Integer> prefix) {
        GameConfig cfg = this.cfg;
        if (cfg == null) {
            // КРАСКИ МЕСТ ставятся ДО сборки партии: по ним рисуется и поле, и
            // фишки, и картинки жетонов.
            kelium.report.FieldGeometry.useSeatColors(options.seatColors());
            cfg = GameConfig.buildCached(options.rulesetId(), players, seed, null, null,
                options.scenarioId(), options.cuFacing(), options.scenarioFile());
            applyTrainingSetup(cfg);
            this.cfg = cfg;
            startTable = StartTable.of(Setup.buildGame(cfg));
            GameConfig c = cfg;
            SwingUtilities.invokeLater(() -> {
                boards.setRules(c.ruleset, c.content);
                session.setContent(c.content);
            });
        }
        GameState state = startTable.fresh();
        this.liveState = state;

        List<Agent> agents = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            String spec = seatSpecs.get(seat);
            if ("human".equals(spec)) {
                int seatFinal = seat;
                kelium.core.UndoableAgent ia = new kelium.core.UndoableAgent(
                    seat, "Игрок " + (seat + 1), state,
                    d -> SwingUtilities.invokeLater(() -> {
                        if (gen == generation) {
                            showDecision(seatFinal, d);
                        }
                    }),
                    ev -> { }, false);
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

        if (options.training() && gen == 0) {
            SwingUtilities.invokeLater(() -> feedLine(null,
                "ТРЕНИРОВОЧНАЯ ПАРТИЯ: значения подготовки заданы вручную — "
                    + trainingNote() + ". В замеры баланса такая партия не годится."));
        }

        // ЛЕНТА РЕШЕНИЙ пишется всегда: без неё партию не сохранить и не
        // откатить. Если партию продолжают (сохранение) или откатывают, поверх
        // ложится проигрывающая обёртка — она доводит стол до нужного места и
        // передаёт игру живым.
        List<Agent> playing = agents;
        catchingUp = !prefix.isEmpty();
        if (prefix.isEmpty() && gen > 0) {
            // Откат к самому первому решению партии: доигрывать нечего.
            SwingUtilities.invokeLater(() -> {
                if (gen == generation) {
                    onCaughtUp();
                }
            });
        }
        if (!prefix.isEmpty()) {
            if (gen == 0) {
                SwingUtilities.invokeLater(() -> turnLabel.setText(
                    "Доигрываем сохранённое — " + prefix.size() + " решений…"));
            }
            playing = MoveLog.playback(playing, prefix,
                () -> SwingUtilities.invokeLater(() -> {
                    if (gen == generation) {
                        onCaughtUp();
                    }
                }));
        }
        // ПИШУЩАЯ ОБЁРТКА — САМАЯ ВЕРХНЯЯ, поверх проигрывающей: иначе
        // доигранные по ленте ходы мимо неё пройдут, и сохранить или откатить
        // продолженную партию будет нечем.
        List<Agent> journaled = new ArrayList<>(playing.size());
        for (Agent a : playing) {
            journaled.add(new Journaled(a));
        }
        playing = journaled;

        ReplayRecord result;
        try {
            result = GameRecorder.playWithAgents(cfg, state, playing, labels, seed,
                options.seatColors(),
                msg -> SwingUtilities.invokeLater(() -> {
                    if (gen == generation) {
                        feedLine(null, msg);
                    }
                }),
                r -> {
                    if (gen != generation) {
                        // Прогон отменён откатом: новых кадров от него не
                        // надо, и дальше он доигрывать не должен.
                        throw new kelium.core.GameAborted("прогон отменён откатом");
                    }
                    SwingUtilities.invokeLater(() -> {
                        if (gen == generation) {
                            onFrame(r);
                        }
                    });
                    paceBot(r);
                });
        } catch (kelium.core.GameAborted e) {
            if (gen != generation) {
                return;      // это откат, а не закрытие: новый прогон уже идёт
            }
            // Игрок закрыл партию — это не поломка. Записываем то, что успело
            // случиться: журнал партии нужен дизайнеру и от недоигранной.
            saveJournal(rec, "-прервана");
            return;
        } catch (Throwable t) {
            if (gen != generation) {
                return;
            }
            failure = t;
            if (stopped) {
                return;      // окно уже закрыто, жаловаться некому
            }
            // ОБОРВАННАЯ ПАРТИЯ — НЕ ТУПИК. Журнал пишется (дизайнеру он нужен
            // именно от сломанной партии), поломка называется вслух, и из окна
            // есть выход: раньше оставалась мёртвая доска, где нечего нажать.
            saveJournal(rec, "-ошибка");
            StringBuilder где = new StringBuilder();
            for (StackTraceElement el : t.getStackTrace()) {
                if (el.getClassName().startsWith("kelium.")) {
                    где.append(el.getClassName()).append('.').append(el.getMethodName())
                        .append(" (строка ").append(el.getLineNumber()).append(')');
                    break;
                }
            }
            SwingUtilities.invokeLater(() -> {
                finished = true;
                clearDecision();
                turnLabel.setText("Партия оборвалась ошибкой");
                turnLabel.setForeground(Theme.bad());
                endBtn.setTexts("Партия оборвана", "");
                endBtn.setState(KpButton.State.DISABLED);
                feedLine(null, "ОШИБКА: " + t);
                List<String> подробности = new ArrayList<>();
                подробности.add(String.valueOf(t));
                if (где.length() > 0) {
                    подробности.add("оборвалось в " + где);
                }
                подробности.add("Журнал партии сохранён — по нему видно, "
                    + "до какого места дошло");
                confirm.open("Партия оборвалась ошибкой",
                    "Доиграть эту партию нельзя — движок остановился посреди хода",
                    подробности,
                    List.of(new kelium.gui.kp.ConfirmDialog.Option("Выйти в меню",
                        "собрать стол заново", this::closeToMenu)),
                    new kelium.gui.kp.ConfirmDialog.Option("Остаться в окне",
                        "посмотреть доску и ленту", () -> confirm.close()));
            });
            return;
        }
        if (gen != generation) {
            return;
        }
        ReplayRecord finalRec = result;
        // ЖУРНАЛ — ДО объявления «партия окончена»: иначе читатель журнала
        // (робот-тест, дизайнер сразу после партии) застаёт файл недописанным.
        saveJournal(finalRec, "");
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

    /** Строка состояния партии — для прогонщиков и тестов. */
    String turnLabelForTest() {
        return turnLabel == null ? null : turnLabel.getText();
    }

    /** Вид ожидаемого решения — для прогонщиков и тестов. */
    String pendingKindForTest() {
        return pendingKind;
    }

    /** Место с пометкой «вы» (−1 — ни у кого) — для прогонщиков и тестов. */
    int mySeatForTest() {
        return mySeat;
    }

    /** Идёт ли переигровка ленты (сохранение или откат) — для тестов. */
    boolean catchingUpForTest() {
        return catchingUp;
    }

    /** Партия доиграна или оборвана — для прогонщиков и тестов. */
    boolean finishedForTest() {
        return finished;
    }

    /** Чем оборвалась партия (null — ничем) — для прогонщиков и тестов. */
    Throwable failureForTest() {
        return failure;
    }

    /**
     * ОТВЕТИТЬ НА ТЕКУЩУЮ ТОЧКУ РЕШЕНИЯ МЕСТА ТЕМ ЖЕ ПУТЁМ, ЧТО КНОПКА ОКНА —
     * для робота-прогонщика, который играет за живого игрока целую партию
     * через настоящее окно. Звать на потоке Swing. Если движок уже ушёл с
     * этой точки, ответ молча не считается: робот спросит снова.
     */
    void answerForTest(int seat, int index) {
        kelium.core.UndoableAgent agent = humansBySeat.get(seat);
        if (agent == null || agent.pending() == null) {
            return;
        }
        agent.submitIndex(index);
        clearDecision();
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
        if (undoNote != null) {
            feedLine(null, undoNote);
            undoNote = null;
        } else {
            feedLine(null, "Сохранённая партия восстановлена — играем дальше");
        }
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
            // Новая запись — это новый прогон (откат переигрывает партию).
            this.rec = r;
            field.setRecord(r);
            sessionBound = false;
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
        refreshTable();
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
            chipTrophy.set(String.valueOf(p.trophy), String.valueOf(p.trophyCap));
        }
        if (f.snapshot != null) {
            List<kelium.gui.kp.OpponentStrip.Row> rows = new ArrayList<>();
            for (ReplayRecord.Player p : f.snapshot.players) {
                rows.add(new kelium.gui.kp.OpponentStrip.Row(p.seat, seatName(p.seat),
                    p.seat == mySeat, vpTotal(p), p.coin, p.kelium, p.ammo,
                    p.orderHand.size(), p.objectiveHand.size(), p.arsenalHand.size(),
                    p.destroyedValue));
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
                        revealed.put(f.seat, play.card);
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

    /**
     * ШАГИ ХОДА: все решения живого игрока в этом круге — каждое можно
     * отменить щелчком, и партия вернётся к моменту ПЕРЕД ним. Бой, рынок и
     * наука здесь такие же, как стройка: откат переигрывает партию, а не
     * латает состояние, поэтому ничего необратимого для него нет (просьба
     * дизайнера 25.09.2026: «забей на честность — дай отменять всё»).
     */
    private void refreshSteps() {
        List<kelium.gui.kp.TurnStepsPanel.Row> rows = new ArrayList<>();
        int seat = awaitingSeat != null ? awaitingSeat
            : turnSeat == null ? viewedSeat : turnSeat;
        if (awaitingSeat != null) {
            for (int i : undoTargets(awaitingSeat)) {
                Decision d;
                synchronized (moves) {
                    d = i < decisions.size() ? decisions.get(i) : null;
                }
                if (d == null) {
                    continue;
                }
                int idx = i;
                rows.add(new kelium.gui.kp.TurnStepsPanel.Row(d.label(),
                    kelium.gui.kp.TurnStepsPanel.Kind.UNDOABLE, () -> undoTo(idx)));
            }
            if (pendingKind != null) {
                rows.add(new kelium.gui.kp.TurnStepsPanel.Row(
                    KIND_LABELS.getOrDefault(pendingKind, "решение"),
                    kelium.gui.kp.TurnStepsPanel.Kind.CURRENT, null));
            }
        } else if (turnSeat != null && !humansBySeat.containsKey(turnSeat)) {
            for (String s : botSteps) {
                rows.add(new kelium.gui.kp.TurnStepsPanel.Row(s,
                    kelium.gui.kp.TurnStepsPanel.Kind.INFO, null));
            }
        }
        steps.setRows(seat, rows);
        refreshUndoControls();
    }

    /**
     * КУДА МОЖНО ОТКАТИТЬСЯ: номера решений этого места в ленте, принятых в
     * ТЕКУЩЕМ круге (от вскрытия приказа до этой минуты), по порядку.
     */
    List<Integer> undoTargets(int seat) {
        List<Integer> out = new ArrayList<>();
        kelium.core.UndoableAgent agent = humansBySeat.get(seat);
        InteractiveAgent.PendingDecision now = agent == null ? null : agent.pending();
        if (now == null || catchingUp) {
            return out;
        }
        int round = now.state().round;
        int circle = now.state().circle;
        synchronized (moves) {
            for (int i = decisions.size() - 1; i >= 0; i--) {
                Decision d = decisions.get(i);
                if (d.round() != round || d.circle() != circle) {
                    break;
                }
                if (d.seat() == seat) {
                    out.add(0, i);
                }
            }
        }
        return out;
    }

    /** Шаг назад: отменить последнее решение живого игрока в этом круге. */
    void undoLast() {
        if (awaitingSeat == null) {
            return;
        }
        List<Integer> t = undoTargets(awaitingSeat);
        if (!t.isEmpty()) {
            undoTo(t.get(t.size() - 1));
        }
    }

    /** К началу хода: отменить всё, что живой игрок решил в этом круге. */
    void undoAll() {
        if (awaitingSeat == null) {
            return;
        }
        List<Integer> t = undoTargets(awaitingSeat);
        if (!t.isEmpty()) {
            undoTo(t.get(0));
        }
    }

    /** Кнопки отката в полосе хода — живы, только когда есть что отменять. */
    private void refreshUndoControls() {
        if (undoBtn == null) {
            return;
        }
        int n = awaitingSeat == null ? 0 : undoTargets(awaitingSeat).size();
        undoBtn.setState(n > 0 ? KpButton.State.AVAILABLE : KpButton.State.DISABLED);
        undoAllBtn.setState(n > 1 ? KpButton.State.AVAILABLE : KpButton.State.DISABLED);
        undoBtn.setTexts("Шаг назад", n > 0 ? "Ctrl+Z" : "нечего отменять");
        undoAllBtn.setTexts("К началу хода", n > 1 ? "отменить шагов: " + n : "");
    }

    KpButton undoBtn;
    KpButton undoAllBtn;

    /**
     * ОТКАТ «ДО ТОЧКИ»: партия переигрывается с начального стола по ленте до
     * решения {@code index} (его самого — уже нет), и игрока спрашивают там же
     * заново. Старый прогон движка снимается: его агенты размыкаются, а всё,
     * что он ещё успеет прислать, отбрасывается по номеру поколения.
     */
    void undoTo(int index) {
        List<Integer> prefix;
        String what;
        synchronized (moves) {
            if (index < 0 || index >= moves.size()) {
                return;
            }
            what = decisions.get(index).label();
            prefix = new ArrayList<>(moves.subList(0, index));
            moves.clear();
            decisions.clear();
        }
        int gen = ++generation;
        for (kelium.core.UndoableAgent a : humansBySeat.values()) {
            a.abort();
        }
        clearDecision();
        catchingUp = true;
        undoNote = "Отменено: «" + what + "» и всё после него";
        feedBox.removeAll();
        journalBox.removeAll();
        lastFeedText = null;
        botSteps.clear();
        turnLabel.setText("Откат…");
        Thread engine = new Thread(() -> playSession(gen, prefix), "hotseat-engine-" + gen);
        engine.setDaemon(true);
        engine.start();
    }

    /** Что сказать в ленте, когда откат доиграет до места. */
    private String undoNote;

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
        "tower_hex", "build_hex", "move_hex", "move_source", "maneuver_hex", "energy_hex", "combat_source",
        "combat_target");

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
        Map.entry("move_source", "гекс, войска которого двигаются бесплатно"),
        Map.entry("maneuver_hex", "гекс манёвра: сначала выведите войска, потом введите"),
        Map.entry("sci_pay_kelium", "сколько заплатить келемием, остальное трофеями"),
        Map.entry("move", "куда шагнуть"),
        Map.entry("maneuver_unit", "какой отряд поведёте"),
        Map.entry("combat_source", "откуда атаковать"),
        Map.entry("combat_target", "цель атаки"),
        Map.entry("combat_victim", "кого поразить"),
        Map.entry("neutral_victim", "какой нейтрал атаковать"),
        Map.entry("attack", "атака"),
        Map.entry("mine", "добыча: что взять"),
        Map.entry("assemble", "сборка: что нанять"),
        Map.entry("tuck", "подложить карту-символ"),
        Map.entry("open_container", "вскрытие контейнера"),
        Map.entry("market_rate", "курс рынка"),
        Map.entry("sci_track", "трек науки"),
        Map.entry("super_pick", "выберите супер-задание"),
        Map.entry("start_objective_pick", "стартовое задание"),
        // все прочие точки решения движка — чтобы в шапке и в карточке вопроса
        // не всплывали внутренние коды (замер 25.09.2026: 46 видов за 6 партий)
        Map.entry("market", "рынок: сделка"),
        Map.entry("sci_exchange", "обмен науки"),
        Map.entry("energy_activation", "смена энергии: откуда и куда"),
        Map.entry("energy_place", "куда поставить энергию"),
        Map.entry("energy_loss_shift", "куда перенести кубик энергии"),
        Map.entry("energy_or_modules", "смена энергии или смена модулей"),
        Map.entry("return_unit", "кого вернуть в запас"),
        Map.entry("storage_side", "сторона жетона хранилища"),
        Map.entry("storage_discard", "что выбросить со склада"),
        Map.entry("module_keep", "какой модуль оставить"),
        Map.entry("module_place_red", "куда положить красный модуль"),
        Map.entry("module_place_blue", "куда положить синий модуль"),
        Map.entry("module_move_pick", "какой модуль переставить"),
        Map.entry("module_gild_pick", "какой модуль позолотить"),
        Map.entry("seal_move", "куда положить глухой жетон"),
        Map.entry("landing", "высадка: где и кого"),
        Map.entry("reaction", "ответ картой"),
        Map.entry("pay_power", "запитать монетами?"),
        Map.entry("order_spec", "плашка приказа"),
        Map.entry("objective_reward_action", "награда: какое действие"),
        Map.entry("destroyed_pay", "чем заплатить"),
        Map.entry("exchange_where", "где меняться"),
        Map.entry("keep_objective", "какое задание оставить"),
        Map.entry("objective_keep", "какое задание оставить"),
        Map.entry("arsenal_draw2", "какую карту арсенала оставить"),
        Map.entry("mass_open", "вскрытие находок"),
        Map.entry("cu_hex", "где поставить центр управления"),
        Map.entry("cu_sides", "поворот центра управления"),
        Map.entry("build_neutral", "где поставить нейтральное здание"),
        Map.entry("ricochet_target", "куда уходит рикошет"),
        Map.entry("discard_enemy_arsenal", "какой установленный арсенал врага удалить"),
        Map.entry("steal_arsenal", "у кого забрать карту арсенала"),
        Map.entry("steal_objectives", "у кого забрать задания"));

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
            case "blind_discard" -> "отложите приказ под уничтоженные жетоны";
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
            Integer доУсиления = indexOfSpec(opts, "spec_objective_enh", id);
            // СВОБОДНЫЙ ВЕРХ (∞, задания 1.19.0) — отдельный вариант движка:
            // он сжигается, не тратя спец-действие.
            Integer заСпец = indexOfSpec(opts, "spec_objective_burn", id);
            Integer доСожжения = заСпец != null ? заСпец
                : indexOfSpec(opts, "free_objective_burn", id);
            List<kelium.gui.kp.CardMenu.Act> acts = new ArrayList<>();
            acts.add(new kelium.gui.kp.CardMenu.Act("Выполнить задание",
                objectiveReward(id), доВыполнения != null,
                "условие ещё не выполнено",
                доВыполнения == null ? () -> { } : () -> submitSpec(agent, доВыполнения)));
            // УСИЛЕНИЕ — ОТДЕЛЬНАЯ КНОПКА (правило 16.09.2026): усиленная награда
            // даётся ВМЕСТО базовой, значит это развилка, а не галочка. Кнопки
            // нет вовсе, пока движок не предложил усиление.
            acts.add(new kelium.gui.kp.CardMenu.Act("Выполнить усиленно",
                objectiveBonus(id), доУсиления != null,
                "усиленное условие ещё не выполнено",
                доУсиления == null ? () -> { } : () -> submitSpec(agent, доУсиления)));
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

    /**
     * ОТВЕТ НА ТО САМОЕ РЕШЕНИЕ, ради которого нарисован орган управления.
     * Если движок уже спрашивает другое (двойной щелчок, щелчок по гаснущей
     * карте вскрытия, пузырь, не успевший исчезнуть), ответ не уходит: номер
     * варианта прежнего вопроса в новом значил бы совсем другой ход.
     */
    private void submit(kelium.core.UndoableAgent agent, InteractiveAgent.PendingDecision d,
                        int index) {
        if (agent == null || agent.pending() != d) {
            return;
        }
        agent.submitIndex(index);
        clearDecision();
    }

    /** Дописать кусок текста карты — пустые молча пропускаются. */
    /** Виды решений «выбери карту» и набор, откуда брать её текст. */
    private static final Map<String, String> CARD_PICKS = Map.of(
        "super_pick", "super_objectives",
        "start_objective_pick", "objectives");

    /**
     * ВЕСЬ ПЕЧАТНЫЙ ТЕКСТ КАРТЫ — то же, что показывает увеличенная карта при
     * наведении: игрок выбирает по содержанию, а не по имени.
     */
    private String cardFullText(String набор, String id) {
        StringBuilder t = new StringBuilder();
        Object печатный = cardField(набор, id, "текст_карты");
        добавить(t, "", печатный == null ? null : String.valueOf(печатный));
        Object описание = cardField(набор, id, "описание");
        добавить(t, "", описание == null ? null : String.valueOf(описание));
        if ("objectives".equals(набор)) {
            добавить(t, "НАГРАДА: ", objectiveReward(id));
            добавить(t, "УСИЛЕННАЯ: ", objectiveBonus(id));
            добавить(t, "УТИЛЬ (сжечь): ", objectiveTop(id));
        }
        return t.toString();
    }

    /** Разделитель абзацев в тексте карты — ровно один символ. */
    private static final char ПЕРЕВОД = (char) 10;

    private static void добавить(StringBuilder куда, String подпись, String текст) {
        if (текст == null || текст.isBlank()) {
            return;
        }
        if (куда.length() > 0) {
            // ПЕРЕВОД СТРОКИ РОВНО ОДИН СИМВОЛ: увеличенная карта режет текст
            // по нему, а System.lineSeparator() на Windows это два знака, и
            // лишний возврат каретки оставался внутри абзаца.
            куда.append(ПЕРЕВОД).append(ПЕРЕВОД);
        }
        куда.append(подпись).append(текст);
    }

    /** Усиленная награда задания — короткой строкой, либо null. */
    private String objectiveBonus(String id) {
        // КЛЮЧ НАБОРА — special_reward: усиленная награда за выполнение
        // усиленного условия.
        Object r = cardField("objectives", id, "special_reward");
        return r instanceof Map<?, ?> m ? rewardWords(m) : null;
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
                case "trophy" -> "трофеев";
                case "vp" -> "ПО";
                case "arsenal", "arsenal_card" -> "карта арсенала";
                case "objective", "objective_card" -> "карта задания";
                case "container", "containers" -> "контейнеров";
                case "module_red" -> "красный модуль";
                case "module_blue" -> "синий модуль";
                case "tech", "tech_step" -> "шаг науки";
                default -> k;
            };
            // Число ПОСЛЕ слова: так не надо согласовывать окончание («1 трофей»,
            // «2 трофея», «5 трофеев») — и не будет уродливого «1 трофеев».
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
        if ("reveal_order".equals(kind)) {
            // новый круг — прошлый приказ со стола убирается
            revealed.remove(seat);
            refreshTable();
        }
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
                            submit(agent, d, idx);
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

        // ВЫБОР КАРТЫ ПОДГОТОВКИ — ВЕЕРОМ ПО ЦЕНТРУ, С ЗАТЕНЕНИЕМ, как выбор
        // карты круга (просьба дизайнера 31.08.2026). Прежде супер-задание и
        // стартовое задание выбирались двумя плашками с ОДНИМ ИМЕНЕМ карты:
        // игрок выбирал вслепую, не видя ни условия, ни награды.
        if (CARD_PICKS.containsKey(kind)
                && options.stream().allMatch(c -> c.payload() instanceof String)
                && !options.isEmpty()) {
            actionBar.idle("не сейчас");
            endBtn.setTexts("Сначала решение", KIND_LABELS.getOrDefault(kind, kind));
            endBtn.setState(KpButton.State.DISABLED);
            zoom.setVisible(false);
            String набор = CARD_PICKS.get(kind);
            List<kelium.gui.kp.CardChoiceOverlay.Card> cards = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                String id = (String) options.get(i).payload();
                int idx = i;
                cards.add(new kelium.gui.kp.CardChoiceOverlay.Card(id, null,
                    cardName(id), cardFullText(набор, id), () -> {
                        ceremony.close();
                        submit(agent, d, idx);
                    }));
            }
            boolean супер = "super_pick".equals(kind);
            ceremony.open(
                супер ? "Выберите супер-задание" : "Выберите стартовое задание",
                супер ? "Оно лежит открытым всю партию: соперники видят, к чему вы идёте"
                    : "Задание отправится к вам в руку",
                cards);
            refreshSteps();
            frame.toFront();
            return;
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
                    submit(agent, d, idx);
                });
                field.setChoices(null, KIND_LABELS.get(kind),
                    "Наведите курсор на сторону гекса или крутите колесо — "
                        + "здание встаёт призраком; щелчок по гексу ставит", null,
                    Theme.seat(seat));
                refreshSteps();
                frame.toFront();
                return;
            }
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
                submit(agent, d, idx);
            });
            if (passIdx >= 0) {
                int pi = passIdx;
                endBtn.setTexts("Завершить ход",
                    d.context().get("remaining") instanceof Number n
                        ? "доступно действий: " + n : "");
                endBtn.setState(KpButton.State.AVAILABLE);
                endBtn.onClick(() -> {
                    submit(agent, d, pi);
                });
            }
            // ДЕЙСТВИЕ ВЫБИРАЕТСЯ НА САМОЙ КАРТЕ ПРИКАЗА (просьба дизайнера
            // 25.09.2026): круги напечатанных действий на вскрытой карте внизу
            // и есть кнопки. Действие, которого на карте нет, предлагается в
            // карточке вопроса над полем.
            Map<String, List<kelium.gui.kp.FieldBubbles.Opt>> onCard = new LinkedHashMap<>();
            List<kelium.gui.kp.FieldBubbles.Opt> offCard = new ArrayList<>();
            String shown = revealed.get(seat);
            List<String> printed = kelium.gui.kp.PlayerTable.actionsOf(
                shown == null ? null : orderFace(shown));
            for (var e : avail.entrySet()) {
                int idx = e.getValue();
                String nm = e.getKey();
                var opt = new kelium.gui.kp.FieldBubbles.Opt(
                    ActionBar.ACTIONS.getOrDefault(nm, nm), null, 1, () -> {
                        submit(agent, d, idx);
                    });
                if (printed.contains(nm)) {
                    onCard.put("action:" + nm, List.of(opt));
                } else {
                    offCard.add(opt);
                }
            }
            if (passIdx >= 0) {
                int pi = passIdx;
                onCard.put("end", List.of(new kelium.gui.kp.FieldBubbles.Opt("Завершить ход",
                    d.context().get("remaining") instanceof Number n
                        ? "осталось действий: " + n : null, 1, () -> {
                            submit(agent, d, pi);
                        })));
            }
            refreshTable();
            table.setChoices(onCard, Theme.seat(seat));
            field.setChoices(null, "Ваш ход: выберите действие",
                "Щёлкните действие прямо на вскрытой карте приказа внизу — "
                    + "или «Завершить ход» рядом с ней", offCard, Theme.seat(seat));
        } else {
            actionBar.idle("не сейчас");
            endBtn.setTexts("Сначала решение", KIND_LABELS.getOrDefault(kind, kind));
            endBtn.setState(KpButton.State.DISABLED);
            // Призрак здания за курсором — для стройки и переноса (§4).
            if (("build_hex".equals(kind) || "move_hex".equals(kind))
                    && d.context().get("btype") instanceof String bt) {
                field.setGhost(bt, seat);
            }
            routeOnField(seat, kind, agent, options, d);
        }
        refreshSteps();
        frame.toFront();
    }

    /**
     * РЕШЕНИЕ НА ПОЛЕ (просьба дизайнера 25.09.2026: «выборы — не кнопками
     * слева снизу, а прямо на поле, у гекса, у элемента»).
     *
     * <p>Каждому варианту ищется гекс, к которому он относится: гекс в самом
     * варианте, гекс жетона по его номеру, пометка «@гекс» в подписи, наконец
     * гекс из вопроса движка («цель», «откуда»). Варианты с гексом ложатся на
     * поле — у своего гекса; без гекса (отказ, курс рынка, трек науки) — в
     * карточку вопроса над полем. Карта из руки остаётся орган ввода самой руки.
     */
    private void routeOnField(int seat, String kind, kelium.core.UndoableAgent agent,
                              List<Choice> options, InteractiveAgent.PendingDecision d) {
        Map<String, List<kelium.gui.kp.FieldBubbles.Opt>> byHex = new LinkedHashMap<>();
        List<kelium.gui.kp.FieldBubbles.Opt> dock = new ArrayList<>();
        Map<String, Integer> cardToOption = new LinkedHashMap<>();
        Map<Integer, String> uidHex = uidHexes();
        java.util.Set<String> hexIds = new java.util.HashSet<>();
        if (rec != null) {
            for (ReplayRecord.HexInfo h : rec.hexes) {
                hexIds.add(h.id);
            }
        }
        String ctxHex = contextHex(d.context(), hexIds);
        Map<String, List<kelium.gui.kp.FieldBubbles.Opt>> onTable = new LinkedHashMap<>();
        refreshTable();
        for (int i = 0; i < options.size(); i++) {
            Choice c = options.get(i);
            int idx = i;
            boolean pass = "pass".equals(c.kind()) && c.payload() == null
                || Boolean.FALSE.equals(c.payload());
            var opt = new kelium.gui.kp.FieldBubbles.Opt(
                kelium.gui.kp.ChoiceWords.label(kind, c, this::cardName),
                kelium.gui.kp.ChoiceWords.sub(kind, c), pass ? 2 : 0, () -> {
                    submit(agent, d, idx);
                });
            // КАРТА ПЕРЕД ИГРОКОМ — выбирается на самой карте, на столе.
            if (c.payload() instanceof String id && !hexIds.contains(id) && onTableCard(id)) {
                onTable.computeIfAbsent("card:" + id, k -> new ArrayList<>()).add(opt);
                continue;
            }
            // ДЕТАЛЬ ПЛАНШЕТА — жетон здания, ячейка модуля, хранилище.
            String boardKey = pass ? null : boardKeyOf(kind, c);
            if (boardKey != null) {
                onTable.computeIfAbsent(boardKey, k -> new ArrayList<>()).add(opt);
                continue;
            }
            String hex = pass ? null : anchorOf(c, uidHex, hexIds);
            if (hex == null && !pass && ctxHex != null) {
                hex = ctxHex;
            }
            if (hex == null) {
                dock.add(opt);
            } else {
                byHex.computeIfAbsent(hex, k -> new ArrayList<>()).add(opt);
            }
        }
        String title = cap(KIND_LABELS.getOrDefault(kind, "Решение"));
        String hint;
        if (!byHex.isEmpty()) {
            boolean multi = byHex.values().stream().anyMatch(l -> l.size() > 1);
            hint = "Щёлкните подсвеченный гекс на поле"
                + (multi ? " — где стоит цифра, откроется список вариантов" : "");
        } else if (onTable.keySet().stream().anyMatch(k -> k.startsWith("card:"))) {
            hint = "Щёлкните подсвеченную карту на столе внизу";
        } else if (!onTable.isEmpty()) {
            hint = "Щёлкните подсвеченную деталь на планшете внизу";
        } else {
            hint = null;
        }
        table.setChoices(onTable, Theme.seat(seat));
        field.setChoices(byHex, title, hint, dock, Theme.seat(seat));
        if (d.context().get("source") instanceof String src && hexIds.contains(src)) {
            field.setSource(src, Theme.seat(seat));
        }
        // РЫНОК И НАУКА — С ДОСКАМИ ПЕРЕД ГЛАЗАМИ: ящик с ними выезжает сам,
        // пока идёт решение, и уезжает, когда решение принято.
        if (SCIENCE_MARKET.contains(kind)) {
            drawerCloser.stop();
            if (openDrawer != drawers.get("Наука и рынок")) {
                toggleDrawer("Наука и рынок");
                drawerAutoOpened = true;
            }
        }
    }

    /** Отложенное закрытие ящика науки, открытого окном для решения. */
    private final javax.swing.Timer drawerCloser =
        new javax.swing.Timer(450, e -> closeAutoDrawer());

    private void closeAutoDrawer() {
        if (drawerAutoOpened && (awaitingSeat == null
                || !SCIENCE_MARKET.contains(String.valueOf(pendingKind)))) {
            drawerAutoOpened = false;
            if (openDrawer == drawers.get("Наука и рынок")) {
                toggleDrawer("Наука и рынок");
            }
        }
    }

    {
        drawerCloser.setRepeats(false);
    }

    private static final java.util.Set<String> SCIENCE_MARKET = java.util.Set.of(
        "market", "sci_track", "sci_exchange", "exchange_where", "sci_pay_kelium");
    /** Ящик науки открыт окном для решения — окно его и закроет. */
    private boolean drawerAutoOpened;

    /** Лежит ли карта на столе смотрящего места (руки, стопка, пазы). */
    private boolean onTableCard(String id) {
        return table != null && table.hasCard(id);
    }

    /**
     * ДЕТАЛЬ ПЛАНШЕТА, к которой относится вариант, — ключ зоны щелчка стола
     * (см. {@code PrintedBoards.hits}), либо null. Ключ берётся, только если
     * такая деталь реально нарисована: иначе вариант уйдёт в карточку вопроса.
     */
    private String boardKeyOf(String kind, Choice c) {
        Object p = c.payload();
        String key = null;
        switch (kind) {
            case "build_pick" -> {
                if (p instanceof Map<?, ?> m && m.get("btype") != null) {
                    String bt = String.valueOf(m.get("btype")).toLowerCase(java.util.Locale.ROOT);
                    Object lvl = m.get("level");
                    key = ("miner".equals(bt) || "power_plant".equals(bt)) && lvl != null
                        ? "building:" + bt + ":" + lvl : "building:" + bt;
                }
            }
            case "module_place_red" -> {
                if (p instanceof Map<?, ?> m && m.get("unit") != null) {
                    key = "red:" + String.valueOf(m.get("unit")).toLowerCase(java.util.Locale.ROOT);
                }
            }
            case "module_place_blue" -> {
                if (p instanceof Map<?, ?> m && m.get("building") != null) {
                    key = "blue:" + String.valueOf(m.get("building"))
                        .toLowerCase(java.util.Locale.ROOT);
                }
            }
            case "module_move_pick", "module_gild_pick", "seal_move" -> {
                if (p instanceof kelium.core.UnitType u) {
                    key = "red:" + u.name().toLowerCase(java.util.Locale.ROOT);
                } else if (p instanceof kelium.core.BuildingType b) {
                    key = "blue:" + b.name().toLowerCase(java.util.Locale.ROOT);
                }
            }
            case "storage_side", "storage_discard" -> key = "storage";
            default -> {
            }
        }
        return key != null && table.hasSpot(key) ? key : null;
    }

    /** Где стоит каждый жетон: номер → гекс (по последнему кадру). */
    private Map<Integer, String> uidHexes() {
        Map<Integer, String> out = new java.util.HashMap<>();
        if (rec == null || rec.frames.isEmpty()) {
            return out;
        }
        ReplayRecord.Frame f = rec.frames.get(rec.frames.size() - 1);
        if (f.snapshot != null) {
            for (ReplayRecord.Tok t : f.snapshot.tokens) {
                if (t.hexId != null) {
                    out.put(t.uid, t.hexId);
                }
            }
        }
        return out;
    }

    private static final java.util.regex.Pattern AT_HEX =
        java.util.regex.Pattern.compile("@(h-?\\d+_-?\\d+)");

    /** Гекс, к которому относится вариант, либо null. */
    private static String anchorOf(Choice c, Map<Integer, String> uidHex,
                                   java.util.Set<String> hexIds) {
        Object p = c.payload();
        if (p instanceof String s) {
            if (hexIds.contains(s)) {
                return s;
            }
            if (s.matches("\\d+")) {
                String h = uidHex.get(Integer.parseInt(s));
                if (h != null) {
                    return h;
                }
            }
        }
        if (p instanceof Integer n && uidHex.containsKey(n)) {
            return uidHex.get(n);
        }
        if (p instanceof kelium.core.Token t && t.hexId() != null) {
            return t.hexId();
        }
        if (p instanceof Map<?, ?> m) {
            for (String k : List.of("target", "to", "hex")) {
                if (m.get(k) instanceof String s && hexIds.contains(s)) {
                    return s;
                }
            }
            for (String k : List.of("building", "to", "from", "uid")) {
                if (m.get(k) instanceof Number n && uidHex.containsKey(n.intValue())) {
                    return uidHex.get(n.intValue());
                }
            }
        }
        if (c.label() != null) {
            java.util.regex.Matcher mm = AT_HEX.matcher(c.label());
            if (mm.find() && hexIds.contains(mm.group(1))) {
                return mm.group(1);
            }
        }
        return null;
    }

    /** Гекс, о котором спрашивает сам вопрос движка (цель, источник), либо null. */
    private static String contextHex(Map<String, Object> ctx, java.util.Set<String> hexIds) {
        for (String k : List.of("target", "hex", "killer_hex", "source")) {
            if (ctx.get(k) instanceof String s && hexIds.contains(s)) {
                return s;
            }
        }
        return null;
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
                submit(agent, d, idx);
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
        field.clearChoices();
        field.clearGhost();
        field.clearFacingChoice();
        hands.clearPickable();
        if (table != null) {
            table.clearChoices();
        }
        if (spread != null && spread.isOpen()) {
            spread.close();
        }
        if (drawerAutoOpened) {
            // Закрываем не сразу: сделки рынка и шаги науки идут чередой, и
            // ящик не должен хлопать между ними.
            drawerCloser.restart();
        }
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
    /**
     * ПРИКАЗ СЛОВАМИ — то, что во всплывашке заменило картинку карты: какие
     * действия даёт верхняя половина, какие нижняя и когда та открывается.
     * Одного «описания» было мало — по нему нельзя было понять даже, что
     * играешь.
     */
    private String orderDesc(String id) {
        Map<String, Object> d = orderData(id);
        if (d == null) {
            return "";
        }
        StringBuilder t = new StringBuilder();
        if (Boolean.TRUE.equals(d.get("joker"))) {
            добавить(t, "БЕЗОПАСНОСТЬ: ", "любые два РАЗНЫХ действия игры из восьми. "
                + "Нижнего приказа нет; правилу совпадения карта не подчиняется");
        } else {
            добавить(t, "ВЕРХНИЙ ПРИКАЗ: ", половинаСловами(d.get("top"),
                "оба действия ваши; если тот же приказ вскрыли раньше вас "
                    + "в этом круге — только одно на выбор"));
            добавить(t, "НИЖНИЙ ПРИКАЗ: ", половинаСловами(d.get("bottom"),
                "одно действие, и только если этот приказ сверху вскрыл кто-то, "
                    + "чья очередь в круге прошла раньше вашей"));
        }
        if (Boolean.TRUE.equals(d.get("maneuver"))) {
            добавить(t, "МАНЁВР: ", "СПЕЦ-действие — переместить один свой жетон "
                + "войска на его скорость. Не открывает Наступление и не тратит боеприпасы");
        }
        Object о = d.get("описание");
        добавить(t, "", о == null ? null : String.valueOf(о));
        return t.toString();
    }

    /** «НАСТУПЛЕНИЕ — манёвр, бой; <как играется>» либо null, если половины нет. */
    private String половинаСловами(Object код, String какИграется) {
        if (!(код instanceof String cat) || cat.isBlank()) {
            return null;
        }
        StringBuilder t = new StringBuilder(kelium.gui.kp.ActionIcons.categoryRu(cat));
        List<String> действия = kelium.gui.kp.ActionIcons.CATEGORY_ACTIONS
            .getOrDefault(cat, List.of());
        if (!действия.isEmpty()) {
            t.append(" — ");
            for (int i = 0; i < действия.size(); i++) {
                if (i > 0) {
                    t.append(", ");
                }
                t.append(ActionBar.ACTIONS.getOrDefault(действия.get(i), действия.get(i))
                    .toLowerCase(java.util.Locale.ROOT));
            }
        }
        t.append(". ").append(какИграется);
        return t.toString();
    }
}
