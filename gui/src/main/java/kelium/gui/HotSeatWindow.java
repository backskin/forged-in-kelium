package kelium.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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
import kelium.gui.replay2.BoardSheet;
import kelium.gui.replay2.Session;
import kelium.gui.replay2.Theme;
import kelium.report.ReplayRecord;

/**
 * «КОМАНДНЫЙ ПУНКТ» — живое окно партии (hot-seat + боты). Каркас по
 * утверждённому концепту (design-docs/КОНЦЕПТ — игровой интерфейс цифровой
 * версии (Командный пункт).md, §2, этап 1 из §10):
 *
 * <ul>
 *   <li>ПОЛЕ — якорь: центр экрана, никаких вкладок, что его прячут;</li>
 *   <li>ЯЩИКИ слева ПОВЕРХ поля: «Наука и рынок» ({@link BoardsPanel}),
 *       «Планшет» ({@link BoardSheet} по местам), «Журнал» (полная лента) —
 *       всё готовые панели replay2, ящик — только обёртка;</li>
 *   <li>полоса хода сверху: раунд/круг, чей ход, ресурсы с потолками;</li>
 *   <li>справа: шаги текущего хода (пока без отката — этап 5) и лента;</li>
 *   <li>снизу: руки (пока текстом — карточки в этапе 3), панель решений,
 *       кнопка «Завершить ход» с остатком действий.</li>
 * </ul>
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

    /** Русские названия действий движка — для плиток и шагов хода. */
    private static final Map<String, String> ACTION_RU = Map.of(
        "build", "Стройка", "mining", "Добыча", "movement", "Манёвр",
        "combat", "Бой", "market", "Рынок", "science", "Наука",
        "assembly", "Сборка", "energy_swap", "Энергия");

    private static final int DRAWER_W = 480;
    private static final int RAIL_W = 260;

    private final int players;
    private final long seed;
    private final List<String> seatSpecs;
    final Map<Integer, InteractiveAgent> humansBySeat = new ConcurrentHashMap<>();
    private final Session session = new Session();

    JFrame frame;
    private JLabel roundLabel;
    private JLabel turnLabel;
    private JLabel chipVp;
    private JLabel chipCoin;
    private JLabel chipKelium;
    private JLabel chipAmmo;
    private JLabel chipDebris;
    FieldView field;
    private BoardsPanel boards;
    private BoardSheet sheet;
    private JToggleButton[] sheetSeatTabs;
    private JLayeredPane layered;
    private final Map<String, JComponent> drawers = new LinkedHashMap<>();
    final Map<String, JToggleButton> drawerTabs = new LinkedHashMap<>();
    private JComponent openDrawer;
    private JPanel stepsBox;
    private JLabel stepsCaption;
    private JPanel feedBox;
    private JScrollPane feedScroll;
    private JPanel journalBox;
    private JTextArea handArea;
    JPanel decisionPanel;
    JButton endBtn;
    private JLabel endHint;
    private int viewedSeat = 0;
    private volatile GameConfig cfg;
    private boolean sessionBound;
    private Integer stepsTurnSeat;
    private int stepNo;
    volatile ReplayRecord rec;
    /** Место, для которого сейчас реально ждём клика/кнопки — иначе null. */
    volatile Integer awaitingSeat;
    /** Ответ «пас» текущей точки решения (для кнопки «Завершить ход»). */
    private Integer pendingPassIndex;
    private InteractiveAgent pendingAgent;

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
        Theme.apply(true);
        frame = new JFrame("Кристаллы Раздора — Командный пункт");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Theme.bg());

        frame.add(buildTopBar(), BorderLayout.NORTH);
        frame.add(buildTabStrip(), BorderLayout.WEST);
        frame.add(buildCenter(), BorderLayout.CENTER);
        frame.add(buildRail(), BorderLayout.EAST);
        frame.add(buildBottom(), BorderLayout.SOUTH);

        frame.setSize(Theme.px(1500), Theme.px(950));
        frame.setMinimumSize(new Dimension(Theme.px(1100), Theme.px(720)));
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(8) + " " + Theme.px(12) + " " + Theme.px(8) + " " + Theme.px(12)
                + ", gapx " + Theme.px(14), "[][]push[][][][][]"));
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

        chipVp = chip(bar);
        chipCoin = chip(bar);
        chipKelium = chip(bar);
        chipAmmo = chip(bar);
        chipDebris = chip(bar);
        return bar;
    }

    private JLabel chip(JPanel bar) {
        JLabel c = new JLabel(" ");
        c.setFont(Theme.mono(12, Font.PLAIN));
        c.setForeground(Theme.ink());
        c.setOpaque(true);
        c.setBackground(Theme.tile());
        c.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(3), Theme.px(8), Theme.px(3), Theme.px(8))));
        bar.add(c);
        return c;
    }

    /** Корешки ящиков: клик открывает панель ПОВЕРХ поля, повторный — закрывает. */
    private JComponent buildTabStrip() {
        JPanel strip = new JPanel();
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBackground(Theme.panel());
        strip.setBorder(BorderFactory.createMatteBorder(0, 0, 0, Theme.px(1), Theme.border()));
        addDrawerTab(strip, "Наука и рынок");
        addDrawerTab(strip, "Планшет");
        addDrawerTab(strip, "Журнал");
        return strip;
    }

    private void addDrawerTab(JPanel strip, String name) {
        JToggleButton b = new JToggleButton(name);
        b.setFont(Theme.font(11, Font.BOLD));
        b.setForeground(Theme.ink2());
        b.setFocusable(false);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.addActionListener(e -> toggleDrawer(name));
        drawerTabs.put(name, b);
        strip.add(b);
        strip.add(javax.swing.Box.createVerticalStrut(Theme.px(4)));
    }

    private JComponent buildCenter() {
        field = new FieldView();
        // «Чей ход» поверх поля дублирует полосу хода сверху — в живом окне
        // эту подпись рисуем только там (концепт §2: одна зона — один вопрос).
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
        sheetSeatTabs = new JToggleButton[players];
        for (int i = 0; i < players; i++) {
            int s = i;
            JToggleButton b = new JToggleButton((i + 1) + " · " + seatSpecs.get(i));
            b.setFont(Theme.font(11, Font.BOLD));
            b.setForeground(Theme.seatInk(i));
            b.setFocusable(false);
            b.setSelected(i == 0);
            b.addActionListener(e -> sheet.setSeat(s));
            group.add(b);
            sheetSeatTabs[i] = b;
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

        // Раскладка руками: поле всегда во весь центр, открытый ящик — слева
        // поверх него. LayoutManager у JLayeredPane нарочно нет.
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

    private void layoutLayers() {
        field.setBounds(0, 0, layered.getWidth(), layered.getHeight());
        int w = Math.min(Theme.px(DRAWER_W), Math.max(Theme.px(320), layered.getWidth() / 2));
        for (JComponent d : drawers.values()) {
            d.setBounds(0, 0, w, layered.getHeight());
        }
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

    private JComponent buildBottom() {
        JPanel bottom = new JPanel(new BorderLayout(Theme.px(10), 0));
        bottom.setBackground(Theme.panel());
        bottom.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(Theme.px(1), 0, 0, 0, Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(12), Theme.px(8), Theme.px(12))));
        bottom.setPreferredSize(new Dimension(10, Theme.px(190)));

        handArea = new JTextArea();
        handArea.setEditable(false);
        handArea.setFont(Theme.mono(11.5, Font.PLAIN));
        handArea.setBackground(Theme.tile());
        handArea.setForeground(Theme.ink());
        handArea.setBorder(BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8), Theme.px(6), Theme.px(8)));
        JScrollPane handScroll = new JScrollPane(handArea);
        handScroll.setBorder(BorderFactory.createLineBorder(Theme.border()));
        handScroll.setPreferredSize(new Dimension(Theme.px(360), 10));
        bottom.add(handScroll, BorderLayout.WEST);

        decisionPanel = new JPanel();
        decisionPanel.setLayout(new BoxLayout(decisionPanel, BoxLayout.Y_AXIS));
        decisionPanel.setBackground(Theme.panel());
        JScrollPane decScroll = new JScrollPane(decisionPanel);
        decScroll.setBorder(null);
        decScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(20));
        bottom.add(decScroll, BorderLayout.CENTER);

        JPanel end = new JPanel();
        end.setLayout(new BoxLayout(end, BoxLayout.Y_AXIS));
        end.setBackground(Theme.panel());
        endBtn = new JButton("Ход соперника…");
        endBtn.setFont(Theme.font(14, Font.BOLD));
        endBtn.setFocusPainted(false);
        endBtn.setEnabled(false);
        endBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        endBtn.addActionListener(e -> {
            InteractiveAgent a = pendingAgent;
            Integer pass = pendingPassIndex;
            if (a != null && pass != null) {
                a.submitIndex(pass);
                clearDecisionPanel();
            }
        });
        endHint = new JLabel(" ");
        endHint.setFont(Theme.font(11, Font.PLAIN));
        endHint.setForeground(Theme.ink3());
        endHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        end.add(javax.swing.Box.createVerticalGlue());
        end.add(endBtn);
        end.add(javax.swing.Box.createVerticalStrut(Theme.px(6)));
        end.add(endHint);
        end.add(javax.swing.Box.createVerticalGlue());
        end.setPreferredSize(new Dimension(Theme.px(190), 10));
        bottom.add(end, BorderLayout.EAST);
        return bottom;
    }

    // ==================== партия ====================

    private void runGame() {
        GameConfig cfg = GameConfig.build(GameConfig.DEFAULT_RULESET, players, seed, null, null);
        this.cfg = cfg;
        GameState state = Setup.buildGame(cfg);
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
            endBtn.setEnabled(false);
            endBtn.setText("Партия окончена");
            clearDecisionPanel();
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
        refreshHandPanel();
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
            chipVp.setText("ПО " + vp);
            chipVp.setForeground(Theme.points());
            chipCoin.setText("монеты " + p.coin);
            chipKelium.setText("келемий " + p.kelium + "/" + p.keliumCap);
            chipKelium.setForeground(Theme.kelium());
            chipAmmo.setText("БП " + p.ammo + "/" + p.ammoCap);
            chipAmmo.setForeground(Theme.energy());
            chipDebris.setText("обломки " + p.debris + "/" + p.debrisCap);
        }
    }

    /** Шаги ТЕКУЩЕГО хода (пока витрина; откат «до точки» — этап 5 концепта). */
    private void trackSteps(ReplayRecord.Frame f) {
        if ("turn_orders".equals(f.type) && f.seat != null) {
            stepsTurnSeat = f.seat;
            stepNo = 0;
            stepsBox.removeAll();
            stepsCaption.setText("ШАГИ ХОДА — ИГРОК " + (f.seat + 1));
            addStep("Приказ вскрыт", true, f.seat);
        } else if ("action".equals(f.type) && f.seat != null && f.seat.equals(stepsTurnSeat)) {
            Object detail = f.log;
            String name = String.valueOf(detail);
            // Из строки лога имя действия не выковырять надёжно — берём короткую
            // форму: тип события знает только "action", имя лежит в самом логе.
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

    private String lastFeedText;

    /** Строка ленты (и её копия в ящик «Журнал»). seat null — служебное. */
    private void feedLine(Integer seat, String text) {
        // Подряд идущие одинаковые строки (движок шлёт по событию на повтор)
        // схлопываются: лента — для чтения человеком, а не для полноты (полнота
        // — в журнале партии на диске).
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
        // Ширина прописана в html: без неё JLabel не переносит строки и лента
        // уезжает за край колонки горизонтальной прокруткой.
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

    /**
     * Виды точек решения, чей payload — гекс (или сводится к нему): выбираются
     * кликом по полю (концепт §3, семья A). Правило «всё или ничего» — см.
     * {@link #hexTargets}.
     */
    private static final Set<String> HEX_TARGET_KINDS = Set.of(
        "tower_hex", "build_hex", "move_hex", "energy_hex", "combat_source", "combat_target");

    private static final Map<String, String> KIND_LABELS = Map.ofEntries(
        Map.entry("action", "выберите действие"),
        Map.entry("spec", "СПЕЦ-действие"),
        Map.entry("reveal_order", "выберите карту круга"),
        Map.entry("blind_discard", "отложите приказ (место для трофеев)"),
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
        refreshHandPanel();

        String kind = String.valueOf(d.context().get("kind"));
        InteractiveAgent agent = humansBySeat.get(seat);
        List<Choice> options = d.options();

        turnLabel.setText("ВАШ ХОД — Игрок " + (seat + 1) + ": "
            + KIND_LABELS.getOrDefault(kind, kind));
        turnLabel.setForeground(Theme.seatInk(seat));

        // «Завершить ход» = вариант "пас" точки решения вида action.
        pendingAgent = agent;
        pendingPassIndex = null;
        int passIdx = -1;
        for (int i = 0; i < options.size(); i++) {
            if ("pass".equals(options.get(i).kind()) && options.get(i).payload() == null) {
                passIdx = i;
                break;
            }
        }
        if ("action".equals(kind) && passIdx >= 0) {
            pendingPassIndex = passIdx;
            endBtn.setEnabled(true);
            endBtn.setText("Завершить ход");
            Object remaining = d.context().get("remaining");
            endHint.setText(remaining instanceof Number n
                ? "доступно действий: " + n : " ");
        } else {
            endBtn.setEnabled(false);
            endBtn.setText("Ход соперника…".equals(endBtn.getText()) || awaitingSeat != null
                ? "Сначала решение слева" : endBtn.getText());
            endHint.setText(" ");
        }

        decisionPanel.removeAll();
        Map<String, Integer> hexToIndex = hexTargets(kind, options);
        if (hexToIndex != null) {
            JLabel hint = new JLabel("Выберите гекс на поле — подсвечены допустимые");
            hint.setFont(Theme.font(12, Font.PLAIN));
            hint.setForeground(Theme.ink2());
            hint.setBorder(BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(8), Theme.px(8), Theme.px(8)));
            decisionPanel.add(hint);
            field.setSelectable(hexToIndex.keySet(), hexId -> {
                Integer idx = hexToIndex.get(hexId);
                if (idx != null) {
                    agent.submitIndex(idx);
                    clearDecisionPanel();
                }
            });
        } else {
            for (int i = 0; i < options.size(); i++) {
                Choice c = options.get(i);
                if ("action".equals(kind) && i == passIdx) {
                    continue; // пас хода живёт в большой кнопке справа
                }
                String raw = c.label() == null || c.label().isEmpty()
                    ? String.valueOf(c.payload()) : c.label();
                String text = "action".equals(kind)
                    ? ACTION_RU.getOrDefault(String.valueOf(c.payload()), raw) : raw;
                JButton b = new JButton(text);
                b.setFont(Theme.font(12, Font.PLAIN));
                b.setForeground(Theme.ink());
                b.setBackground(Theme.tile());
                b.setAlignmentX(Component.LEFT_ALIGNMENT);
                b.setFocusPainted(false);
                b.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, Theme.px(1), 0, Theme.border()),
                    BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8), Theme.px(6), Theme.px(8))));
                int idx = i;
                b.addActionListener(e -> {
                    agent.submitIndex(idx);
                    clearDecisionPanel();
                });
                decisionPanel.add(b);
            }
        }
        decisionPanel.revalidate();
        decisionPanel.repaint();
        frame.toFront();
    }

    /**
     * Если у ВСЕХ опций точки решения извлекается id гекса — карта «гекс →
     * номер опции» для клика по полю; иначе null (решение кнопками). Правило
     * «всё или ничего»: придумывать вариант, которого движок не предлагал,
     * нельзя.
     */
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

    private void clearDecisionPanel() {
        decisionPanel.removeAll();
        decisionPanel.revalidate();
        decisionPanel.repaint();
        field.clearSelectable();
        awaitingSeat = null;
        pendingAgent = null;
        pendingPassIndex = null;
        endBtn.setEnabled(false);
        endBtn.setText("Ход соперника…");
        endHint.setText(" ");
    }

    // ==================== руки (этап 3 заменит на карточки) ====================

    private void refreshHandPanel() {
        if (rec == null || rec.frames.isEmpty()) {
            return;
        }
        ReplayRecord.Frame f = rec.frames.get(rec.frames.size() - 1);
        if (f.snapshot == null || viewedSeat >= f.snapshot.players.size()) {
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(viewedSeat);
        StringBuilder sb = new StringBuilder();
        sb.append("Игрок ").append(viewedSeat + 1).append(" — ")
            .append(seatSpecs.get(viewedSeat)).append('\n');
        appendHand(sb, "Приказы", p.orderHand);
        appendHand(sb, "Задания", p.objectiveHand);
        appendHand(sb, "Арсенал", p.arsenalHand);
        handArea.setText(sb.toString());
        handArea.setCaretPosition(0);
    }

    private void appendHand(StringBuilder sb, String title, List<String> ids) {
        sb.append(title).append(" (").append(ids.size()).append("): ");
        for (int i = 0; i < ids.size(); i++) {
            sb.append(i > 0 ? " · " : "").append(cardName(ids.get(i)));
        }
        sb.append('\n');
    }

    private String cardName(String id) {
        return rec == null ? id : rec.cardNames.getOrDefault(id, id);
    }
}
