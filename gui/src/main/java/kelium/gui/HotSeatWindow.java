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

    private final int players;
    private final long seed;
    private final List<String> seatSpecs;
    final Map<Integer, InteractiveAgent> humansBySeat = new ConcurrentHashMap<>();
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
    private JLayeredPane layered;
    private final Map<String, JComponent> drawers = new LinkedHashMap<>();
    final Map<String, KpTab> drawerTabs = new LinkedHashMap<>();
    private JComponent openDrawer;
    private JPanel stepsBox;
    private JLabel stepsCaption;
    private JPanel feedBox;
    private JScrollPane feedScroll;
    private JPanel journalBox;
    HandPanel hands;
    ActionBar actionBar;
    PromptOverlay prompt;
    private ZoomCard zoom;
    KpButton endBtn;
    private int viewedSeat = 0;
    private volatile GameConfig cfg;
    private volatile GameState liveState;
    private boolean sessionBound;
    private Integer stepsTurnSeat;
    private int stepNo;
    private String lastFeedText;
    volatile ReplayRecord rec;
    /** Место, для которого сейчас реально ждём клика/кнопки — иначе null. */
    volatile Integer awaitingSeat;

    HotSeatWindow(int players, long seed, List<String> seatSpecs) {
        this.players = players;
        this.seed = seed;
        this.seatSpecs = seatSpecs;
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
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Theme.bg());

        frame.add(buildTopBar(), BorderLayout.NORTH);
        frame.add(buildTabStrip(), BorderLayout.WEST);
        frame.add(buildCenter(), BorderLayout.CENTER);
        frame.add(buildRail(), BorderLayout.EAST);
        frame.add(buildPlayerZone(), BorderLayout.SOUTH);

        zoom = new ZoomCard();
        zoom.setSize(Theme.px(220), Theme.px(300));
        zoom.setVisible(false);
        frame.getLayeredPane().add(zoom, JLayeredPane.POPUP_LAYER);

        frame.setSize(Theme.px(1500), Theme.px(950));
        frame.setMinimumSize(new Dimension(Theme.px(1150), Theme.px(760)));
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
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

        chipVp = new ChipLabel("SUPER", Theme.points());
        chipCoin = new ChipLabel("COIN", Theme.points());
        chipKelium = new ChipLabel("KELIUM", Theme.kelium());
        chipAmmo = new ChipLabel("AMMO", Theme.energy());
        chipDebris = new ChipLabel("DEBRIS", Theme.neutral());
        for (ChipLabel c : List.of(chipVp, chipCoin, chipKelium, chipAmmo, chipDebris)) {
            bar.add(c);
        }
        return bar;
    }

    private JComponent buildTabStrip() {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBackground(Theme.panel());
        strip.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, Theme.px(1), Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(10), Theme.px(4), Theme.px(10), Theme.px(4))));
        addDrawerTab(strip, "Наука и рынок");
        addDrawerTab(strip, "Планшет");
        addDrawerTab(strip, "Журнал");
        strip.add(javax.swing.Box.createVerticalGlue());
        return strip;
    }

    private void addDrawerTab(JPanel strip, String name) {
        KpTab tab = new KpTab(name, () -> toggleDrawer(name));
        tab.setPreferredSize(new Dimension(Theme.px(36), Theme.px(132)));
        tab.setMaximumSize(new Dimension(Theme.px(36), Theme.px(132)));
        tab.setAlignmentX(Component.CENTER_ALIGNMENT);
        drawerTabs.put(name, tab);
        strip.add(tab);
        strip.add(javax.swing.Box.createVerticalStrut(Theme.px(8)));
    }

    private JComponent buildCenter() {
        field = new FieldView();
        field.setShowTurnCaption(false);
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
            JToggleButton b = new JToggleButton((i + 1) + " · " + seatSpecs.get(i));
            b.setFont(Theme.font(11, Font.BOLD));
            b.setForeground(Theme.seatInk(i));
            b.setFocusable(false);
            b.setSelected(i == 0);
            b.addActionListener(e -> sheet.setSeat(s));
            group.add(b);
            seatRow.add(b);
        }
        sheetWrap.add(seatRow, BorderLayout.NORTH);
        JScrollPane sheetScroll = new JScrollPane(sheet);
        sheetScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        sheetScroll.setBorder(null);
        sheetWrap.add(sheetScroll, BorderLayout.CENTER);
        drawers.put("Планшет", wrapDrawer(sheetWrap));

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
        int w = Math.min(Theme.px(DRAWER_W), Math.max(Theme.px(320), layered.getWidth() / 2));
        for (JComponent d : drawers.values()) {
            d.setBounds(0, 0, w, layered.getHeight());
        }
        layoutPrompt();
    }

    private void layoutPrompt() {
        int maxW = Math.max(Theme.px(320), layered.getWidth() - Theme.px(24)
            - (openDrawer != null && openDrawer.isVisible() ? openDrawer.getWidth() : 0));
        int x = Theme.px(12)
            + (openDrawer != null && openDrawer.isVisible() ? openDrawer.getWidth() : 0);
        prompt.setSize(new Dimension(Math.min(Theme.px(760), maxW), 10));
        Dimension pref = prompt.getPreferredSize();
        int h = Math.min(pref.height, layered.getHeight() - Theme.px(24));
        prompt.setBounds(x, layered.getHeight() - h - Theme.px(12),
            Math.min(Theme.px(760), maxW), h);
        prompt.revalidate();
    }

    private void toggleDrawer(String name) {
        JComponent target = drawers.get(name);
        for (Map.Entry<String, JComponent> e : drawers.entrySet()) {
            boolean on = e.getValue() == target && openDrawer != target;
            e.getValue().setVisible(on);
            drawerTabs.get(e.getKey()).setSelected(on);
        }
        openDrawer = openDrawer == target ? null : target;
        layoutLayers();
        layered.repaint();
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
        stepsBox = new JPanel();
        stepsBox.setLayout(new BoxLayout(stepsBox, BoxLayout.Y_AXIS));
        stepsBox.setBackground(Theme.panel());
        stepsBox.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(10), Theme.px(8), Theme.px(10)));
        top.add(stepsBox);
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
        zone.setPreferredSize(new Dimension(10, Theme.px(132)));

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
        actionBar = new ActionBar();
        actionBar.setPreferredSize(new Dimension(Theme.px(420), Theme.px(100)));
        actionBar.setMaximumSize(new Dimension(Theme.px(420), Theme.px(110)));
        right.add(actionBar);
        right.add(javax.swing.Box.createHorizontalStrut(Theme.px(14)));

        endBtn = new KpButton("Ход соперника…", "", null).primary(true);
        endBtn.setState(KpButton.State.DISABLED);
        endBtn.setPreferredSize(new Dimension(Theme.px(180), Theme.px(100)));
        endBtn.setMaximumSize(new Dimension(Theme.px(180), Theme.px(110)));
        right.add(endBtn);
        zone.add(right, BorderLayout.EAST);
        return zone;
    }

    private void showZoom(CardTile tile, String group) {
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
        Point p = SwingUtilities.convertPoint(tile, 0, 0, frame.getLayeredPane());
        int x = Math.max(Theme.px(6),
            Math.min(p.x - Theme.px(60), frame.getLayeredPane().getWidth() - zoom.getWidth() - Theme.px(6)));
        int y = Math.max(Theme.px(6), p.y - zoom.getHeight() - Theme.px(4));
        zoom.setLocation(x, y);
    }

    // ==================== партия ====================

    private void runGame() {
        GameConfig cfg = GameConfig.build(GameConfig.DEFAULT_RULESET, players, seed, null, null);
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
                InteractiveAgent ia = new InteractiveAgent(seat, "Игрок " + (seat + 1),
                    d -> SwingUtilities.invokeLater(() -> showDecision(seatFinal, d)),
                    ev -> { });
                humansBySeat.put(seat, ia);
                agents.add(ia);
                labels.add("human");
            } else {
                agents.add(Bots.create(spec, seat, new Random(seed * 131 + seat + 1), players));
                labels.add(spec);
            }
        }

        ReplayRecord result;
        try {
            result = GameRecorder.playWithAgents(cfg, state, agents, labels, seed,
                msg -> SwingUtilities.invokeLater(() -> feedLine(null, msg)),
                r -> SwingUtilities.invokeLater(() -> onFrame(r)));
        } catch (Throwable t) {
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
            clearDecision();
        });
        try {
            java.nio.file.Path out = java.nio.file.Path.of("reports", "hotseat",
                "hotseat-" + seed + ".kelium-replay.json");
            finalRec.save(out);
            SwingUtilities.invokeLater(() ->
                feedLine(null, "Журнал партии записан: " + out.toAbsolutePath()));
        } catch (java.io.IOException e) {
            SwingUtilities.invokeLater(() ->
                feedLine(null, "Не удалось записать журнал: " + e.getMessage()));
        }
    }

    // ==================== живое обновление ====================

    private void onFrame(ReplayRecord r) {
        if (this.rec != r) {
            this.rec = r;
            field.setRecord(r);
        }
        if (r.frames.isEmpty()) {
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
        roundLabel.setText("Раунд " + f.round + " · круг " + f.circle);
        Integer active = f.snapshot == null ? null : f.snapshot.active;
        if (awaitingSeat == null) {
            if (active == null) {
                turnLabel.setText("Общая фаза раунда");
                turnLabel.setForeground(Theme.ink2());
            } else {
                boolean bot = !"human".equals(seatSpecs.get(active));
                turnLabel.setText("Ходит: Игрок " + (active + 1)
                    + (bot ? " · " + seatSpecs.get(active) + " (бот)" : ""));
                turnLabel.setForeground(Theme.seatInk(active));
            }
        }
        int seat = awaitingSeat != null ? awaitingSeat
            : active != null && humansBySeat.containsKey(active) ? active : viewedSeat;
        if (f.snapshot != null && seat < f.snapshot.players.size()) {
            ReplayRecord.Player p = f.snapshot.players.get(seat);
            int vp = 0;
            for (int v : p.vp.values()) {
                vp += v;
            }
            chipVp.set(String.valueOf(vp), null);
            chipCoin.set(String.valueOf(p.coin), null);
            chipKelium.set(String.valueOf(p.kelium), String.valueOf(p.keliumCap));
            chipAmmo.set(String.valueOf(p.ammo), String.valueOf(p.ammoCap));
            chipDebris.set(String.valueOf(p.debris), String.valueOf(p.debrisCap));
        }
    }

    private void refreshHands(ReplayRecord.Frame f) {
        if (f.snapshot == null || viewedSeat >= f.snapshot.players.size()) {
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(viewedSeat);
        hands.setCards("Приказы", p.orderHand, this::cardName, orderBand(p.orderColor),
            null, null);
        hands.setCards("Задания", p.objectiveHand, this::cardName, Theme.points(),
            this::objectiveTag, Theme.kelium());
        hands.setCards("Арсенал", p.arsenalHand, this::cardName, Theme.container(),
            null, null);
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

    /** Шаги ТЕКУЩЕГО хода (витрина; откат «до точки» — этап 5 концепта). */
    private void trackSteps(ReplayRecord.Frame f) {
        if ("turn_orders".equals(f.type) && f.seat != null) {
            stepsTurnSeat = f.seat;
            stepNo = 0;
            stepsBox.removeAll();
            stepsCaption.setText("ШАГИ ХОДА — ИГРОК " + (f.seat + 1));
            addStep("Приказ вскрыт", true, f.seat);
            actionBar.turnStarted();
        } else if ("action".equals(f.type) && f.seat != null && f.seat.equals(stepsTurnSeat)) {
            String name = String.valueOf(f.log);
            addStep(name.length() > 42 ? name.substring(0, 41) + "…" : name, false, f.seat);
        } else if ("turn_end".equals(f.type) && f.seat != null && f.seat.equals(stepsTurnSeat)) {
            stepsTurnSeat = null;
        }
        stepsBox.revalidate();
        stepsBox.repaint();
    }

    private void addStep(String text, boolean locked, int seat) {
        stepNo++;
        JLabel row = new JLabel(stepNo + ". " + text + (locked ? "  🔒" : ""));
        row.setFont(Theme.font(11.5, Font.PLAIN));
        row.setForeground(locked ? Theme.ink3() : Theme.ink());
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, Theme.px(2), 0, 0, Theme.seat(seat)),
            BorderFactory.createEmptyBorder(Theme.px(3), Theme.px(6), Theme.px(3), 0)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        stepsBox.add(row);
    }

    /** Строка ленты (и её копия в ящик «Журнал»). seat null — служебное. */
    private void feedLine(Integer seat, String text) {
        if (text.equals(lastFeedText)) {
            return;
        }
        // Сырые служебные события (телеметрия ботов вида «objective_hints {…}»)
        // человеку в ленте не нужны — в полном журнале партии они остаются.
        if (text.matches("^[a-z_]+ \\{.*")) {
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
        Map.entry("attack", "залп"),
        Map.entry("mine", "добыча"),
        Map.entry("assemble", "сборка"),
        Map.entry("tuck", "подложить карту-символ"),
        Map.entry("open_container", "вскрытие контейнера"),
        Map.entry("market_rate", "курс рынка"),
        Map.entry("sci_track", "трек науки"),
        Map.entry("super_pick", "выберите супер-задание"),
        Map.entry("start_objective_pick", "стартовое задание"));

    private void showDecision(int seat, InteractiveAgent.PendingDecision d) {
        viewedSeat = seat;
        awaitingSeat = seat;
        if (rec != null && !rec.frames.isEmpty()) {
            refreshHands(rec.frames.get(rec.frames.size() - 1));
        }

        String kind = String.valueOf(d.context().get("kind"));
        InteractiveAgent agent = humansBySeat.get(seat);
        List<Choice> options = d.options();
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
                    field.setGhost(kelium.report.Labels.buildingCode(bt), Theme.seat(seat));
                }
                field.setFacingChoice(fhex, variants, idx -> {
                    agent.submitIndex(idx);
                    clearDecision();
                });
                List<PromptOverlay.Option> opts = new ArrayList<>();
                for (int i = 0; i < options.size(); i++) {
                    int idx = i;
                    opts.add(new PromptOverlay.Option(options.get(i).label(), () -> {
                        agent.submitIndex(idx);
                        clearDecision();
                    }));
                }
                prompt.showOptions(seat, title
                    + " — колесо мыши вращает дугу, клик по гексу ставит; зелёные рёбра = встанет",
                    opts);
                layoutPrompt();
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
            actionBar.showDecision(avail, idx -> {
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
                    field.setGhost(kelium.report.Labels.buildingCode(
                        (String) d.context().get("btype")), Theme.seat(seat));
                    prompt.showHint(seat, title,
                        "Призрак следует за курсором · клик по подсвеченному гексу — поставить");
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
                        rest.add(new PromptOverlay.Option(label, () -> {
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
        frame.toFront();
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
        prompt.hideAll();
        field.clearSelectable();
        field.clearGhost();
        field.clearFacingChoice();
        hands.clearPickable();
        actionBar.idle("ход соперника");
        awaitingSeat = null;
        endBtn.setTexts("Ход соперника…", "");
        endBtn.setState(KpButton.State.DISABLED);
        endBtn.onClick(null);
    }

    private String cardName(String id) {
        return rec == null ? id : rec.cardNames.getOrDefault(id, id);
    }
}
