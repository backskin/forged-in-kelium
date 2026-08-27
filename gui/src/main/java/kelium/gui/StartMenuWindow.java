package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import kelium.agents.BotCatalog;
import kelium.core.GameState;
import kelium.dataio.GameConfig;
import kelium.engine.Setup;
import kelium.gui.kp.KpButton;
import kelium.gui.kp.KpChooser;
import kelium.gui.kp.SeedBox;
import kelium.gui.kp.Stepper;
import kelium.gui.replay2.Theme;
import kelium.report.ReplayRecord;

/**
 * «ШТАБ» — экран, с которого начинается партия (концепт
 * design-docs/КОНЦЕПТ — меню запуска партии (Штаб), принят 26.08.2026).
 *
 * <p>Не мастер с кнопками «Далее»: настольная игра начинается не с анкеты, а с
 * того, что коробку открыли и раскладывают компоненты. Поэтому всё видно сразу
 * одним экраном, любое решение меняется в любом порядке, и ничего не спрятано
 * за «дополнительно».
 *
 * <p>Герой экрана — НАСТОЯЩЕЕ поле: оно собирается тем же {@link Setup#buildGame}
 * и рисуется тем же {@link FieldView}, что и партия, поэтому игрок видит ровно
 * тот стол, за который садится. Клик по стартовому гексу выбирает своё место,
 * наведение на сектор — поворот центра управления.
 *
 * <p>Запуск: {@code java kelium.gui.StartMenuWindow}.
 */
public final class StartMenuWindow {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StartMenuWindow().start());
    }

    /**
     * ВЕРНУТЬСЯ В МЕНЮ ПОСЛЕ ПАРТИИ, не собирая стол заново: настройки той
     * партии восстанавливаются, и поменять достаточно то, что хотелось.
     * {@code previous} может быть null — тогда стол чистый.
     */
    public static void open(HotSeatWindow.Options previous) {
        SwingUtilities.invokeLater(() -> {
            StartMenuWindow w = new StartMenuWindow();
            w.restore(previous);
            w.start();
        });
    }

    /** Взять настройки прошлой партии за исходные. */
    private void restore(HotSeatWindow.Options o) {
        if (o == null) {
            return;
        }
        rulesetId = o.rulesetId();
        players = o.players();
        seed = o.seed();
        restoreSpecs = List.copyOf(o.seatSpecs());
        restoreMapId = o.scenarioId();
        restoreMapFile = o.scenarioFile();
        restoreColors = o.seatColors();
        restoreCoins = o.startCoins();
        restoreKelium = o.startKelium();
        restoreAmmo = o.startAmmo();
        // Место игрока и поворот ЦУ — из состава мест прошлой партии.
        for (int i = 0; i < restoreSpecs.size(); i++) {
            if (HUMAN.equals(restoreSpecs.get(i))) {
                restoreSeat = i;
                break;
            }
        }
        if (o.cuFacing() != null && restoreSeat != null
                && restoreSeat < o.cuFacing().size()) {
            restoreFacing = o.cuFacing().get(restoreSeat);
        }
        mode = restoreSpecs.stream().filter(HUMAN::equals).count() > 1
            ? Mode.HOTSEAT : Mode.BOTS;
    }

    private List<String> restoreSpecs;
    private String restoreMapId;
    private java.nio.file.Path restoreMapFile;
    private Integer restoreSeat;
    private Integer restoreFacing;
    private List<Integer> restoreColors;
    private Integer restoreCoins;
    private Integer restoreKelium;
    private Integer restoreAmmo;

    private static final int RAIL_W = 320;

    /** Режимы стола. Сеть и общий компьютер ждут своих экранов. */
    private enum Mode { BOTS, HOTSEAT, NET }

    JFrame frame;
    FieldView field;
    private JPanel seatStrip;
    private JLabel hint;
    private JLabel readyLine;
    KpButton startBtn;
    KpChooser rulesBox;
    KpChooser mapBox;
    SeedBox seedBox;
    private final Map<String, KpButton> modeTiles = new LinkedHashMap<>();
    private final Map<Integer, KpButton> countTiles = new LinkedHashMap<>();
    final List<KpChooser> seatBoxes = new ArrayList<>();
    private Stepper coinStep;
    private Stepper keliumStep;
    private Stepper ammoStep;

    private Mode mode = Mode.BOTS;
    private int players = 2;
    private long seed = 100000 + new Random().nextInt(900000);
    private String rulesetId = GameConfig.DEFAULT_RULESET;
    private FieldOption map = new FieldOption(null, "любая — по сиду", null);
    /** Место игрока за столом; null — ещё не выбрано. */
    Integer mySeat;
    /** Поворот ЦУ моего места (номер первой из пары стенок); null — на усмотрение. */
    Integer myFacing;
    private final List<String> bots = new ArrayList<>();
    /**
     * ЦВЕТ КАЖДОГО МЕСТА — номер краски 0..3. По умолчанию цвет идёт за номером
     * места, как на столе; игрок может взять себе любой, и соперникам тоже.
     */
    private final List<Integer> colors = new ArrayList<>(List.of(0, 1, 2, 3));

    /** Раскладка поля: авторская по id или своя из файла. */
    record FieldOption(String id, String label, java.nio.file.Path file) {
    }

    /** Стартовые гексы построенного предпросмотра: место → id гекса. */
    private final Map<Integer, String> startHexes = new LinkedHashMap<>();
    private GameConfig previewCfg;

    // ==================== сборка окна ====================

    void start() {
        Theme.apply(false);
        frame = new JFrame("Кристаллы Раздора — Штаб");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Theme.bg());

        frame.add(buildHead(), BorderLayout.NORTH);
        frame.add(buildRail(), BorderLayout.WEST);
        frame.add(buildStage(), BorderLayout.CENTER);
        frame.add(buildFoot(), BorderLayout.SOUTH);

        frame.setSize(Theme.px(1280), Theme.px(860));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        applyRestored();
        rebuildPreview();
    }

    /** Разложить настройки прошлой партии по органам уже собранного окна. */
    private void applyRestored() {
        if (restoreSpecs == null) {
            return;
        }
        countTiles.forEach((k, b) -> b.setState(k == players
            ? KpButton.State.ACTIVE : KpButton.State.AVAILABLE));
        modeTiles.forEach((k, t) -> {
            if (t.state() != KpButton.State.DISABLED) {
                t.setState(k.equals(mode.name())
                    ? KpButton.State.ACTIVE : KpButton.State.AVAILABLE);
            }
        });
        rulesBox.select(rulesetId);
        seedBox.set(seed);
        ensureBots();
        for (int i = 0; i < restoreSpecs.size() && i < bots.size(); i++) {
            bots.set(i, restoreSpecs.get(i));
        }
        refreshMaps();
        for (FieldOption f : maps) {
            if (restoreMapId != null && restoreMapId.equals(f.id())
                    || restoreMapId == null && restoreMapFile == null && f.id() == null) {
                map = f;
                mapBox.select(f.label());
                break;
            }
        }
        if (restoreColors != null) {
            for (int i = 0; i < restoreColors.size() && i < colors.size(); i++) {
                if (restoreColors.get(i) != null) {
                    colors.set(i, restoreColors.get(i));
                }
            }
        }
        mySeat = restoreSeat;
        myFacing = restoreFacing;
        if (restoreCoins != null) {
            coinStep.step(restoreCoins - coinStep.value());
        }
        if (restoreKelium != null) {
            keliumStep.step(restoreKelium - keliumStep.value());
        }
        if (restoreAmmo != null) {
            ammoStep.step(restoreAmmo - ammoStep.value());
        }
    }

    private JComponent buildHead() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.panel());
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, Theme.px(2), 0, Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(10), Theme.px(16),
                Theme.px(10), Theme.px(16))));
        JLabel name = new JLabel("ШТАБ");
        name.setFont(Theme.font(19, Font.BOLD));
        name.setForeground(Theme.ink());
        bar.add(name, BorderLayout.WEST);
        JLabel sub = new JLabel("Соберите стол: режим, правила, поле, своё место и соперников");
        sub.setFont(Theme.font(13, Font.PLAIN));
        sub.setForeground(Theme.ink2());
        sub.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(16), 0, 0));
        bar.add(sub, BorderLayout.CENTER);
        return bar;
    }

    // ---------- левая колонка решений ----------

    private JComponent buildRail() {
        JPanel rail = new JPanel();
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBackground(Theme.panel());
        rail.setPreferredSize(new Dimension(Theme.px(RAIL_W), Theme.px(10)));
        rail.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, Theme.px(1), Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(12), Theme.px(12),
                Theme.px(12), Theme.px(12))));

        rail.add(section("ПРОДОЛЖИТЬ", buildContinue()));
        rail.add(javax.swing.Box.createVerticalStrut(Theme.px(14)));
        rail.add(section("РЕЖИМ", buildModes()));
        rail.add(javax.swing.Box.createVerticalStrut(Theme.px(14)));
        rail.add(section("СТОЛ", buildTable()));
        rail.add(javax.swing.Box.createVerticalStrut(Theme.px(14)));
        rail.add(section("СТАРТОВЫЕ ЗНАЧЕНИЯ", buildTraining()));
        rail.add(javax.swing.Box.createVerticalGlue());
        return rail;
    }

    private JComponent section(String title, JComponent body) {
        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel cap = new JLabel(title);
        cap.setFont(Theme.caption());
        cap.setForeground(Theme.ink3());
        cap.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.px(6), 0));
        box.add(cap, BorderLayout.NORTH);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(body, BorderLayout.CENTER);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
        return box;
    }

    /**
     * ПРОДОЛЖИТЬ СОХРАНЁННУЮ ПАРТИЮ — первым делом на экране: у кого партия
     * начата, тому не стол собирать, а вернуться в неё.
     *
     * <p>Сохранений нет — строка честно говорит об этом и не притворяется
     * кнопкой. Сохранение, снятое на других правилах, тоже видно: оно в списке,
     * но с причиной, почему по нему уже не сыграть.
     */
    private JComponent buildContinue() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        List<GameSave> saves = GameSave.all();
        if (saves.isEmpty()) {
            JLabel none = new JLabel("сохранённых партий нет");
            none.setFont(Theme.font(12, Font.PLAIN));
            none.setForeground(Theme.ink3());
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(none);
            return col;
        }
        List<KpChooser.Item> items = new ArrayList<>();
        for (GameSave sv : saves) {
            String why = sv.checkCompatible();
            items.add(new KpChooser.Item(sv.name, sv.name,
                why == null ? sv.describe() : "не подходит: " + why));
        }
        saveBox = new KpChooser(null, items, it -> {
            for (GameSave sv : saves) {
                if (sv.name.equals(it.value())) {
                    chosenSave = sv;
                    break;
                }
            }
            refreshContinue();
        });
        chosenSave = saves.get(0);
        saveBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(30)));
        col.add(saveBox);
        col.add(javax.swing.Box.createVerticalStrut(Theme.px(6)));

        continueBtn = new KpButton("Продолжить партию", "", null);
        continueBtn.setPreferredSize(new Dimension(Theme.px(280), Theme.px(40)));
        continueBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(40)));
        continueBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        continueBtn.onClick(this::continueSaved);
        col.add(continueBtn);
        refreshContinue();
        return col;
    }

    private KpChooser saveBox;
    private KpButton continueBtn;
    private GameSave chosenSave;

    /** Годится ли выбранное сохранение — и что об этом сказать игроку. */
    private void refreshContinue() {
        if (continueBtn == null || chosenSave == null) {
            return;
        }
        String why = chosenSave.checkCompatible();
        if (why == null) {
            continueBtn.setTexts("Продолжить партию", chosenSave.describe());
            continueBtn.setState(KpButton.State.AVAILABLE);
        } else {
            continueBtn.setTexts("Продолжить нельзя", why);
            continueBtn.setState(KpButton.State.DISABLED);
        }
        continueBtn.setToolTipText(why == null
            ? "Партия доиграется по записанным решениям до места сохранения и "
                + "вернётся к вам живой"
            : why);
    }

    /** Открыть окно партии и доиграть его до места сохранения. */
    private void continueSaved() {
        if (chosenSave == null || chosenSave.checkCompatible() != null) {
            return;
        }
        frame.dispose();
        HotSeatWindow.open(chosenSave);
    }

    private JComponent buildModes() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        addMode(col, Mode.BOTS, "Один против ботов", "вы и до трёх соперников-программ");
        addMode(col, Mode.HOTSEAT, "Общий компьютер", "живые игроки по очереди за одним экраном");
        addMode(col, Mode.NET, "По сети", "вы поднимаете стол, к нему подключаются");
        return col;
    }

    private void addMode(JPanel col, Mode m, String title, String sub) {
        KpButton b = new KpButton(title, sub, null);
        b.setPreferredSize(new Dimension(Theme.px(280), Theme.px(44)));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(44)));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Сетевой стол в движке есть (GameServer/Lobby), а ЭКРАНА лобби ещё нет —
        // плитка честно говорит об этом, вместо того чтобы вести в никуда.
        boolean ready = m != Mode.NET;
        b.setState(!ready ? KpButton.State.DISABLED
            : m == mode ? KpButton.State.ACTIVE : KpButton.State.AVAILABLE);
        if (!ready) {
            b.setTexts(title, "экран лобби ещё не сделан");
        }
        b.onClick(() -> {
            if (!ready) {
                return;
            }
            mode = m;
            modeTiles.forEach((k, t) -> {
                if (t.state() != KpButton.State.DISABLED) {
                    t.setState(k.equals(m.name())
                        ? KpButton.State.ACTIVE : KpButton.State.AVAILABLE);
                }
            });
            // «Общий компьютер» — стол живых людей: места становятся живыми, и
            // игрок сам решает, какие отдать ботам.
            for (int i = 0; i < bots.size(); i++) {
                bots.set(i, m == Mode.HOTSEAT ? HUMAN
                    : BotCatalog.ХАРАКТЕРЫ.get(i % BotCatalog.ХАРАКТЕРЫ.size()).id()
                        + ":" + DEFAULT_LEVEL);
            }
            refreshSeats();
            refreshReady();
        });
        b.setToolTipText(switch (m) {
            case BOTS -> "Играете вы один, остальные места занимают боты";
            case HOTSEAT -> "Несколько живых игроков ходят по очереди на этом компьютере; "
                + "свободные места добираются ботами";
            default -> "Стол поднимается на этом компьютере, соперники подключаются по сети";
        });
        modeTiles.put(m.name(), b);
        col.add(b);
        col.add(javax.swing.Box.createVerticalStrut(Theme.px(5)));
    }

    private JComponent buildTable() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JPanel counts = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(5)));
        counts.setOpaque(false);
        counts.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel cl = new JLabel("игроков");
        cl.setFont(Theme.font(12, Font.PLAIN));
        cl.setForeground(Theme.ink2());
        counts.add(cl);
        for (int n = 2; n <= 4; n++) {
            int nn = n;
            KpButton b = new KpButton(String.valueOf(n), "", null);
            b.setPreferredSize(new Dimension(Theme.px(40), Theme.px(30)));
            b.setState(n == players ? KpButton.State.ACTIVE : KpButton.State.AVAILABLE);
            b.onClick(() -> setPlayers(nn));
            countTiles.put(n, b);
            counts.add(b);
        }
        col.add(counts);
        col.add(javax.swing.Box.createVerticalStrut(Theme.px(8)));

        rulesBox = new KpChooser("правила", rulesetList(), it -> {
            rulesetId = it.value();
            refreshMaps();
            rebuildPreview();
        });
        rulesBox.select(rulesetId);
        rulesBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        rulesBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(40)));
        col.add(rulesBox);
        col.add(javax.swing.Box.createVerticalStrut(Theme.px(8)));

        seedBox = new SeedBox(seed, v -> {
            seed = v;
            rebuildPreview();
        });
        seedBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        seedBox.setToolTipText("Одинаковый сид, та же раскладка и тот же состав соперников "
            + "дают ровно ту же партию. Щёлкните по числу, чтобы набрать своё");
        col.add(seedBox);
        return col;
    }

    private JComponent buildTraining() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        // Пояснение — ОБЫЧНЫМИ строками, а не html с заданной шириной: ширина
        // колонки зависит от масштаба темы, и подогнанное html-число на другом
        // масштабе обрезало строку краем панели.
        for (String line : List.of("Правите значения — партия станет ТРЕНИРОВОЧНОЙ,",
                "и метка уйдёт в журнал: в замеры баланса", "такая партия не годится.")) {
            JLabel note = new JLabel(line);
            note.setFont(Theme.font(11, Font.PLAIN));
            note.setForeground(Theme.ink3());
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(note);
        }
        col.add(javax.swing.Box.createVerticalStrut(Theme.px(6)));

        int[] print = printedStart();
        coinStep = new Stepper("монеты", "COIN", print[0], 0, 30, v -> refreshReady());
        keliumStep = new Stepper("келемий", "KELIUM", print[1], 0, 20, v -> refreshReady());
        ammoStep = new Stepper("боеприпасы", "AMMO", print[2], 0, 20, v -> refreshReady());
        for (Stepper s : List.of(coinStep, keliumStep, ammoStep)) {
            s.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(s);
            col.add(javax.swing.Box.createVerticalStrut(Theme.px(3)));
        }
        return col;
    }

    /** Печатные значения подготовки из выбранного свода. */
    private int[] printedStart() {
        try {
            var rs = GameConfig.buildCached(rulesetId, players, seed, null, null).ruleset;
            Object coins = rs.get("setup.start_coins", null);
            int c = coins instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Number n
                ? n.intValue() : Setup.START_COINS[0];
            int k = rs.get("setup.start_kelium", null) instanceof Number kn
                ? kn.intValue() : Setup.START_KELIUM;
            int a = rs.get("setup.start_ammo", null) instanceof Number an
                ? an.intValue() : Setup.START_AMMO;
            return new int[]{c, k, a};
        } catch (RuntimeException e) {
            return new int[]{Setup.START_COINS[0], Setup.START_KELIUM, Setup.START_AMMO};
        }
    }

    // ---------- поле ----------

    private JComponent buildStage() {
        JPanel stage = new JPanel(new BorderLayout(0, Theme.px(8)));
        stage.setBackground(Theme.bg());
        stage.setBorder(BorderFactory.createEmptyBorder(Theme.px(12), Theme.px(14),
            Theme.px(10), Theme.px(14)));

        JPanel top = new JPanel(new BorderLayout(Theme.px(10), 0));
        top.setOpaque(false);
        mapBox = new KpChooser("раскладка поля", mapList(), it -> {
            map = optionByLabel(it.value());
            mySeat = null;
            myFacing = null;
            rebuildPreview();
        });
        mapBox.setPreferredSize(new Dimension(Theme.px(320), Theme.px(40)));
        top.add(mapBox, BorderLayout.WEST);

        hint = new JLabel("Кликните стартовый гекс — это будет ваше место");
        hint.setFont(Theme.font(13, Font.BOLD));
        hint.setForeground(Theme.accent());
        hint.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        top.add(hint, BorderLayout.CENTER);
        stage.add(top, BorderLayout.NORTH);

        field = new FieldView();
        field.setShowIds(false);
        field.setShowTurnCaption(false);
        stage.add(field, BorderLayout.CENTER);
        return stage;
    }

    // ---------- полоса стола ----------

    private JComponent buildFoot() {
        JPanel foot = new JPanel(new BorderLayout(Theme.px(14), 0));
        foot.setBackground(Theme.panel());
        foot.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(Theme.px(1), 0, 0, 0, Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(10), Theme.px(14),
                Theme.px(10), Theme.px(14))));

        seatStrip = new JPanel();
        seatStrip.setOpaque(false);
        seatStrip.setLayout(new BoxLayout(seatStrip, BoxLayout.X_AXIS));
        foot.add(seatStrip, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        readyLine = new JLabel(" ");
        readyLine.setFont(Theme.font(11.5, Font.PLAIN));
        readyLine.setForeground(Theme.ink2());
        readyLine.setAlignmentX(Component.RIGHT_ALIGNMENT);
        right.add(readyLine);
        right.add(javax.swing.Box.createVerticalStrut(Theme.px(4)));

        startBtn = new KpButton("Начать партию", "", null).primary(true);
        startBtn.setPreferredSize(new Dimension(Theme.px(220), Theme.px(52)));
        startBtn.setMaximumSize(new Dimension(Theme.px(220), Theme.px(52)));
        startBtn.setAlignmentX(Component.RIGHT_ALIGNMENT);
        startBtn.onClick(this::launch);
        right.add(startBtn);
        foot.add(right, BorderLayout.EAST);
        refreshSeats();
        return foot;
    }

    // ==================== состав стола ====================

    private void setPlayers(int n) {
        players = n;
        countTiles.forEach((k, b) -> b.setState(k == n
            ? KpButton.State.ACTIVE : KpButton.State.AVAILABLE));
        if (mySeat != null && mySeat >= n) {
            mySeat = null;
            myFacing = null;
        }
        refreshMaps();
        refreshSeats();
        rebuildPreview();
    }

    /** Соперники по умолчанию: ровные середняки, чтобы стол был играбельным сразу. */
    private void ensureBots() {
        while (bots.size() < 4) {
            bots.add(BotCatalog.ХАРАКТЕРЫ.get(bots.size() % BotCatalog.ХАРАКТЕРЫ.size()).id()
                + ":" + DEFAULT_LEVEL);
        }
    }

    private void refreshSeats() {
        ensureBots();
        seatStrip.removeAll();
        seatBoxes.clear();
        for (int i = 0; i < players; i++) {
            seatStrip.add(seatCard(i));
            seatStrip.add(javax.swing.Box.createHorizontalStrut(Theme.px(8)));
        }
        seatStrip.revalidate();
        seatStrip.repaint();
        refreshReady();
    }

    private JComponent seatCard(int seat) {
        boolean mine = mySeat != null && mySeat == seat;
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.tile());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(Theme.px(3), 0, 0, 0,
                mine ? Theme.seat(seat) : Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8),
                Theme.px(7), Theme.px(8))));
        card.setPreferredSize(new Dimension(Theme.px(230), Theme.px(74)));
        card.setMaximumSize(new Dimension(Theme.px(260), Theme.px(74)));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.X_AXIS));
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel who = new JLabel(mine ? "ВЫ · место " + (seat + 1) : "место " + (seat + 1));
        who.setFont(Theme.font(11, Font.BOLD));
        who.setForeground(mine ? Theme.seatInk(seat) : Theme.ink3());
        head.add(who);
        head.add(javax.swing.Box.createHorizontalGlue());
        head.add(colorPicker(seat));
        card.add(head);

        if (mine) {
            JLabel me = new JLabel(myFacing == null ? "живой игрок · ЦУ не повёрнут"
                : "живой игрок · ЦУ стенками " + (myFacing + 1) + "–" + ((myFacing + 1) % 6 + 1));
            me.setFont(Theme.font(12, Font.PLAIN));
            me.setForeground(Theme.ink2());
            me.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(javax.swing.Box.createVerticalStrut(Theme.px(4)));
            card.add(me);
            seatBoxes.add(null);
        } else {
            // ДВА СПИСКА, А НЕ ОДИН ИЗ ШЕСТНАДЦАТИ (решение дизайнера 25.08.2026,
            // так же сделано в проигрывателе): за столом это два разных вопроса —
            // КЕМ играет соперник и НАСКОЛЬКО хорошо он это делает.
            JPanel who2 = new JPanel();
            who2.setOpaque(false);
            who2.setLayout(new BoxLayout(who2, BoxLayout.X_AXIS));
            who2.setAlignmentX(Component.LEFT_ALIGNMENT);

            KpChooser kind = new KpChooser(null, kindList(), it -> {
                bots.set(seat, HUMAN.equals(it.value()) ? HUMAN
                    : it.value() + ":" + levelOf(bots.get(seat)));
                refreshSeats();
            });
            kind.select(HUMAN.equals(bots.get(seat)) ? HUMAN : kindOf(bots.get(seat)));
            kind.setPreferredSize(new Dimension(Theme.px(116), Theme.px(30)));
            kind.setMaximumSize(new Dimension(Theme.px(116), Theme.px(30)));
            kind.setToolTipText(HUMAN.equals(bots.get(seat))
                ? "За этим местом сидит человек — ходит по очереди на этом компьютере"
                : "Кем играет соперник");
            who2.add(kind);
            who2.add(javax.swing.Box.createHorizontalStrut(Theme.px(4)));

            KpChooser level = new KpChooser(null, levelList(), it -> {
                bots.set(seat, kindOf(bots.get(seat)) + ":" + it.value());
                refreshSeats();
            });
            level.select(levelOf(bots.get(seat)));
            level.setPreferredSize(new Dimension(Theme.px(96), Theme.px(30)));
            level.setMaximumSize(new Dimension(Theme.px(96), Theme.px(30)));
            level.setToolTipText("Насколько хорошо он это делает");
            // Человек за местом — уровень выбирать не у кого.
            level.setVisible(!HUMAN.equals(bots.get(seat)));
            who2.add(level);

            card.add(javax.swing.Box.createVerticalStrut(Theme.px(3)));
            card.add(who2);
            seatBoxes.add(kind);
        }
        return card;
    }

    /**
     * ВЫБОР КРАСКИ МЕСТА — четыре кружка своих цветов; занятый чужим местом
     * пригашен. Цвет к правилам отношения не имеет, но за столом играют своим
     * цветом, а не «местом номер два», и в журнал партии он попадает вместе с ней.
     */
    private JComponent colorPicker(int seat) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        for (int c = 0; c < 4; c++) {
            row.add(new ColorDot(seat, c));
            if (c < 3) {
                row.add(javax.swing.Box.createHorizontalStrut(Theme.px(3)));
            }
        }
        return row;
    }

    /** Кружок краски: свой — с ободком, чужой занятый — пригашен. */
    private final class ColorDot extends JComponent {
        private final int seat;
        private final int color;

        ColorDot(int seat, int color) {
            this.seat = seat;
            this.color = color;
            setOpaque(false);
            int d = Theme.px(15);
            setPreferredSize(new Dimension(d, d));
            setMaximumSize(new Dimension(d, d));
            int other = takenBy(color);
            setToolTipText(COLOR_NAMES[color]
                + (other >= 0 && other != seat ? " — сейчас у места " + (other + 1)
                    + ", возьмёте — поменяетесь" : ""));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    pickColor(seat, color);
                }
            });
        }

        @Override
        protected void paintComponent(java.awt.Graphics g0) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - Theme.px(2);
            boolean own = colors.get(seat) == color;
            int other = takenBy(color);
            boolean busy = other >= 0 && other != seat;
            java.awt.Color base = SEAT_PAINT[color];
            g.setColor(busy ? Theme.alpha(base, 0.28) : base);
            g.fillOval(Theme.px(1), Theme.px(1), d, d);
            g.setColor(own ? Theme.ink() : Theme.alpha(Theme.border(), busy ? 0.5 : 1));
            g.setStroke(new java.awt.BasicStroke(own ? Theme.pxf(2.2) : 1f));
            g.drawOval(Theme.px(1), Theme.px(1), d, d);
            g.dispose();
        }
    }

    /** Краски гнёзд как они напечатаны на жетонах — для кружков выбора. */
    private static final java.awt.Color[] SEAT_PAINT = {
        java.awt.Color.decode(kelium.report.FieldGeometry.SEAT_TOKEN[0]),
        java.awt.Color.decode(kelium.report.FieldGeometry.SEAT_TOKEN[1]),
        java.awt.Color.decode(kelium.report.FieldGeometry.SEAT_TOKEN[2]),
        java.awt.Color.decode(kelium.report.FieldGeometry.SEAT_TOKEN[3])};

    /** Чьё это гнездо цвета сейчас (−1 — ничьё). */
    private int takenBy(int color) {
        for (int i = 0; i < players && i < colors.size(); i++) {
            if (colors.get(i) == color) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Взять месту краску. Занятая краска МЕНЯЕТСЯ МЕСТАМИ с прежним владельцем —
     * так и за столом делают, и двух одинаковых цветов на поле не возникает.
     */
    private void pickColor(int seat, int color) {
        int other = takenBy(color);
        if (other == seat) {
            return;
        }
        if (other >= 0) {
            colors.set(other, colors.get(seat));
        }
        colors.set(seat, color);
        rebuildPreview();
    }

    /** Значение места, за которым сидит человек. */
    static final String HUMAN = "human";

    /** Краски мест в порядке гнёзд — так они называются на столе. */
    static final String[] COLOR_NAMES = {"синий", "оранжевый", "зелёный", "лиловый"};

    /** Первый список: кем играет соперник (плюс «живой игрок» в общем столе). */
    private List<KpChooser.Item> kindList() {
        List<KpChooser.Item> out = new ArrayList<>();
        if (mode == Mode.HOTSEAT) {
            out.add(new KpChooser.Item(HUMAN, "живой игрок",
                "ходит по очереди на этом же компьютере"));
        }
        for (BotCatalog.Entry e : BotCatalog.ХАРАКТЕРЫ) {
            out.add(new KpChooser.Item(e.id(), e.label(), e.tip()));
        }
        return out;
    }

    /** Второй список: насколько хорошо он это делает. */
    private List<KpChooser.Item> levelList() {
        List<KpChooser.Item> out = new ArrayList<>();
        for (BotCatalog.Entry e : BotCatalog.УРОВНИ) {
            out.add(new KpChooser.Item(e.id(), e.label(), e.tip()));
        }
        return out;
    }

    /** Характер из полного имени бота («punisher:3» → «punisher»). */
    private static String kindOf(String spec) {
        if (spec == null || HUMAN.equals(spec)) {
            return BotCatalog.ХАРАКТЕРЫ.get(0).id();
        }
        int at = spec.indexOf(':');
        return at < 0 ? spec : spec.substring(0, at);
    }

    /**
     * Уровень из полного имени бота. По умолчанию МАСТЕР, а не гроссмейстер:
     * гроссмейстер доигрывает копию партии на каждом выборе приказа и думает
     * заметно дольше, а сесть играть хочется сразу.
     */
    private static String levelOf(String spec) {
        if (spec == null || HUMAN.equals(spec)) {
            return DEFAULT_LEVEL;
        }
        int at = spec.indexOf(':');
        return at < 0 ? DEFAULT_LEVEL : spec.substring(at + 1);
    }

    /** Уровень соперника по умолчанию — мастер. */
    static final String DEFAULT_LEVEL = "3";

    private List<KpChooser.Item> rulesetList() {
        List<KpChooser.Item> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            for (String r : GameConfig.availableRulesets(null)) {
                if (seen.add(r)) {
                    out.add(new KpChooser.Item(r, "свод " + r));
                }
            }
        } catch (RuntimeException ignore) {
            // список не прочитан — остаётся действующий свод
        }
        if (out.isEmpty()) {
            out.add(new KpChooser.Item(GameConfig.DEFAULT_RULESET,
                "свод " + GameConfig.DEFAULT_RULESET));
        }
        return out;
    }

    // ==================== раскладки поля ====================

    private final List<FieldOption> maps = new ArrayList<>();

    private List<KpChooser.Item> mapList() {
        maps.clear();
        maps.add(new FieldOption(null, "любая — по сиду", null));
        String version = "1.0.0";
        try {
            version = GameConfig.buildCached(rulesetId, players, seed, null, null)
                .ruleset.getStr("content_versions.scenarios", "1.0.0");
        } catch (RuntimeException ignore) {
            // версия сценариев не прочитана — берём базовую
        }
        Set<String> authors = new LinkedHashSet<>();
        try {
            for (Map<String, Object> v : kelium.engine.Scenario.loadAllVariants(
                    players, version, GameConfig.resolveDataRoot(null))) {
                String id = String.valueOf(v.get("id"));
                authors.add(id);
                maps.add(new FieldOption(id, "авторская · " + id, null));
            }
        } catch (RuntimeException ignore) {
            // авторских нет — остаются свои и «любая»
        }
        try {
            List<String> problems = new ArrayList<>();
            for (kelium.engine.LayoutLibrary.Entry e
                    : kelium.engine.LayoutLibrary.scan(players, problems)) {
                if (!authors.contains(e.id())) {
                    maps.add(new FieldOption(e.id(), "своя · " + e.id(), e.file()));
                }
            }
        } catch (RuntimeException ignore) {
            // библиотека раскладок недоступна — не беда
        }
        List<KpChooser.Item> out = new ArrayList<>();
        for (FieldOption f : maps) {
            out.add(new KpChooser.Item(f.label(), f.label()));
        }
        return out;
    }

    private FieldOption optionByLabel(String label) {
        for (FieldOption f : maps) {
            if (f.label().equals(label)) {
                return f;
            }
        }
        return maps.isEmpty() ? new FieldOption(null, "любая — по сиду", null) : maps.get(0);
    }

    private void refreshMaps() {
        String keep = map == null ? null : map.label();
        mapBox.setItems(mapList(), keep);
        map = optionByLabel(mapBox.selected() == null ? null : mapBox.selected().value());
    }

    // ==================== предпросмотр поля ====================

    /**
     * СОБРАТЬ НАСТОЯЩУЮ ПАРТИЮ И ПОКАЗАТЬ ЕЁ ПОЛЕ. Ровно то же
     * {@link Setup#buildGame}, что и в игре, поэтому предпросмотр не «похож» на
     * стол, а и есть он: те же стартовые гексы, те же ЦУ, та же сборка блоков.
     */
    void rebuildPreview() {
        startHexes.clear();
        try {
            List<Integer> facing = null;
            if (mySeat != null && myFacing != null) {
                facing = new ArrayList<>();
                for (int i = 0; i < players; i++) {
                    facing.add(i == mySeat ? myFacing : null);
                }
            }
            // Краски ставим ДО сборки: по ним рисуются жетоны предпросмотра.
            kelium.report.FieldGeometry.useSeatColors(colors.subList(0, players));
            GameConfig cfg = GameConfig.buildCached(rulesetId, players, seed, null, null,
                map == null ? null : map.id(), facing, map == null ? null : map.file());
            previewCfg = cfg;
            GameState state = Setup.buildGame(cfg);
            for (int seat = 0; seat < players; seat++) {
                startHexes.put(seat, state.player(seat).startHex);
            }
            ReplayRecord rec = new ReplayRecord();
            rec.players = players;
            rec.seed = seed;
            rec.ruleset = rulesetId;
            for (int i = 0; i < players; i++) {
                rec.seatLabels.add(i == (mySeat == null ? -1 : mySeat) ? "вы" : "соперник");
                rec.sides.add(state.player(i).board.troop.side);
            }
            rec.seatColors.addAll(colors.subList(0, players));
            GameRecorder.fillTableAndField(rec, cfg, state);
            ReplayRecord.Frame f = new ReplayRecord.Frame();
            f.type = "setup";
            f.round = 0;
            f.circle = 0;
            f.snapshot = ReplayRecord.snapshotOf(state, mySeat == null ? 0 : mySeat);
            rec.frames.add(f);
            field.setRecord(rec);
            field.setFrame(f);
        } catch (RuntimeException e) {
            hint.setText("Эту раскладку собрать не удалось: " + e.getMessage());
            hint.setForeground(Theme.bad());
            return;
        }
        applyFieldPicking();
        refreshSeats();
    }

    /** Что сейчас кликается на поле: своё место, потом поворот ЦУ. */
    private void applyFieldPicking() {
        field.clearSelectable();
        field.clearFacingChoice();
        if (mySeat == null) {
            field.setSelectable(new LinkedHashSet<>(startHexes.values()), hexId -> {
                for (Map.Entry<Integer, String> e : startHexes.entrySet()) {
                    if (e.getValue().equals(hexId)) {
                        mySeat = e.getKey();
                        myFacing = null;
                        rebuildPreview();
                        return;
                    }
                }
            });
            hint.setText("Кликните стартовый гекс — это будет ваше место");
            hint.setForeground(Theme.accent());
            return;
        }
        // ПАРЫ СТЕНОК ПОД ЦУ. Он занимает две соседние стороны, поэтому вариант
        // номер i — это пара (i, i+1). Движок берёт первую из пары.
        List<List<Integer>> variants = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            variants.add(List.of(i, (i + 1) % 6));
        }
        field.setFacingChoice(startHexes.get(mySeat), variants, i -> {
            myFacing = i;
            rebuildPreview();
        });
        hint.setText(myFacing == null
            ? "Наведите на сектор своего гекса — так встанет центр управления"
            : "Место выбрано, ЦУ повёрнут — можно начинать");
        hint.setForeground(myFacing == null ? Theme.accent() : Theme.ink2());
    }

    // ==================== готовность и запуск ====================

    private void refreshReady() {
        if (readyLine == null || startBtn == null) {
            return;
        }
        boolean training = coinStep != null
            && (coinStep.changed() || keliumStep.changed() || ammoStep.changed());
        if (mySeat == null) {
            readyLine.setText("осталось: выбрать своё место на поле");
            startBtn.setState(KpButton.State.DISABLED);
        } else {
            readyLine.setText((training ? "ТРЕНИРОВОЧНАЯ · " : "")
                + players + " места · свод " + rulesetId + " · сид " + seed);
            startBtn.setState(KpButton.State.AVAILABLE);
        }
        readyLine.setForeground(training ? Theme.points() : Theme.ink2());
    }

    /** Собрать настройки и открыть окно партии. */
    void launch() {
        HotSeatWindow.Options opts = optionsNow();
        if (opts == null) {
            return;
        }
        frame.dispose();
        HotSeatWindow.open(opts);
    }

    /** Настройки стола, как он собран сейчас; null — место ещё не выбрано. */
    HotSeatWindow.Options optionsNow() {
        if (mySeat == null) {
            return null;
        }
        List<String> specs = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            // «Общий компьютер»: все места живые, кроме тех, что игрок отдал ботам.
            specs.add(i == mySeat || HUMAN.equals(bots.get(i)) ? HUMAN : bots.get(i));
        }
        List<Integer> facing = null;
        if (myFacing != null) {
            facing = new ArrayList<>();
            for (int i = 0; i < players; i++) {
                facing.add(i == mySeat ? myFacing : null);
            }
        }
        boolean training = coinStep.changed() || keliumStep.changed() || ammoStep.changed();
        return new HotSeatWindow.Options(rulesetId, players, seed,
            specs, map == null ? null : map.id(), map == null ? null : map.file(), facing,
            List.copyOf(colors.subList(0, players)),
            training && coinStep.changed() ? coinStep.value() : null,
            training && keliumStep.changed() ? keliumStep.value() : null,
            training && ammoStep.changed() ? ammoStep.value() : null);
    }

    /** Взять месту краску — для прогонщиков и тестов, то же что клик по кружку. */
    void pickColorForTest(int seat, int color) {
        pickColor(seat, color);
    }

    /** Краски мест сейчас — для прогонщиков и тестов. */
    List<Integer> colorsForTest() {
        return List.copyOf(colors.subList(0, players));
    }

    /** Счётчики стартовых значений — для прогонщиков и тестов. */
    List<Stepper> trainSteppers() {
        return List.of(coinStep, keliumStep, ammoStep);
    }

    /** Настройки, как их видит меню сейчас, — для прогонщиков и тестов. */
    GameConfig previewConfig() {
        return previewCfg;
    }

    Map<Integer, String> startHexes() {
        return Map.copyOf(startHexes);
    }
}
