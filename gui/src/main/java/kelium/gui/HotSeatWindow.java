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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
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
import kelium.gui.replay2.Theme;
import kelium.report.ReplayRecord;

/**
 * НАСТОЯЩЕЕ окно для игры: hot-seat на одном компьютере, живые места по кругу,
 * боты — на остальных (заказ, §8 шаг 2, но с полем и планшетом вместо
 * консольного текста). Поле и планшет — те же {@link FieldView}/{@link
 * BoardsPanel}, что рисуют готовую запись партии в {@code replay2}: тут они
 * просто смотрят на ЖИВУЮ запись, которая растёт кадр за кадром прямо во время
 * партии ({@link GameRecorder#playWithAgents}, параметр {@code onFrame}).
 *
 * <p>Запуск: {@code kelium.gui.HotSeatWindow <players> [seed] [seat0] [seat1] ...}
 * где место — {@code human} либо имя характера бота ({@link Bots#CHARACTERS}).
 * Без аргументов — двое: место 0 человек, место 1 бот "balanced".
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

    private final int players;
    private final long seed;
    private final List<String> seatSpecs;
    final Map<Integer, InteractiveAgent> humansBySeat = new ConcurrentHashMap<>();

    private JFrame frame;
    private JLabel turnBanner;
    FieldView field;
    private BoardsPanel boards;
    JPanel decisionPanel;
    private JLabel statusLabel;
    private JTextArea handArea;
    private JToggleButton[] seatTabs;
    private int viewedSeat = 0;
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

    private void buildUi() {
        Theme.apply(true);
        frame = new JFrame("Кристаллы Раздора — hot-seat");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Theme.bg());

        turnBanner = new JLabel("Партия начинается…");
        turnBanner.setOpaque(true);
        turnBanner.setFont(Theme.font(15, Font.BOLD));
        turnBanner.setForeground(Theme.ink());
        turnBanner.setBackground(Theme.panel());
        turnBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, Theme.px(2), 0, Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(12), Theme.px(8), Theme.px(12))));
        frame.add(turnBanner, BorderLayout.NORTH);

        field = new FieldView();
        boards = new BoardsPanel();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.font(12, Font.PLAIN));
        tabs.addTab("Поле", field);
        JScrollPane boardsScroll = new JScrollPane(boards);
        boardsScroll.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        tabs.addTab("Наука и рынок", boardsScroll);
        frame.add(tabs, BorderLayout.CENTER);

        JPanel side = new JPanel(new BorderLayout());
        side.setPreferredSize(new Dimension(Theme.px(360), Theme.px(10)));
        side.setBackground(Theme.panel());
        side.setBorder(BorderFactory.createMatteBorder(0, Theme.px(1), 0, 0, Theme.border()));

        JPanel seatBar = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(6) + ", gapx " + Theme.px(4) + ", wrap 2"));
        seatBar.setBackground(Theme.panel());
        ButtonGroup group = new ButtonGroup();
        seatTabs = new JToggleButton[players];
        for (int i = 0; i < players; i++) {
            int s = i;
            JToggleButton b = new JToggleButton((i + 1) + ". " + seatSpecs.get(i));
            b.setFont(Theme.font(11, Font.BOLD));
            b.setForeground(Theme.seat(i));
            b.setFocusable(false);
            b.setSelected(i == 0);
            b.addActionListener(e -> {
                viewedSeat = s;
                refreshHandPanel();
            });
            group.add(b);
            seatTabs[i] = b;
            seatBar.add(b);
        }
        side.add(seatBar, BorderLayout.NORTH);

        handArea = new JTextArea();
        handArea.setEditable(false);
        handArea.setFont(Theme.mono(12, Font.PLAIN));
        handArea.setBackground(Theme.panel());
        handArea.setForeground(Theme.ink());
        handArea.setLineWrap(true);
        handArea.setWrapStyleWord(true);
        handArea.setBorder(BorderFactory.createEmptyBorder(Theme.px(8), Theme.px(8), Theme.px(8), Theme.px(8)));
        JScrollPane handScroll = new JScrollPane(handArea);
        handScroll.setBorder(null);
        side.add(handScroll, BorderLayout.CENTER);

        decisionPanel = new JPanel();
        decisionPanel.setLayout(new BoxLayout(decisionPanel, BoxLayout.Y_AXIS));
        decisionPanel.setBackground(Theme.panel());
        decisionPanel.setBorder(BorderFactory.createMatteBorder(Theme.px(1), 0, 0, 0, Theme.border()));
        side.add(decisionPanel, BorderLayout.SOUTH);

        frame.add(side, BorderLayout.EAST);

        statusLabel = new JLabel("Партия начинается…");
        statusLabel.setFont(Theme.font(12, Font.PLAIN));
        statusLabel.setForeground(Theme.ink2());
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Theme.panel());
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(Theme.px(1), 0, 0, 0, Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(4), Theme.px(8), Theme.px(4), Theme.px(8))));
        frame.add(statusLabel, BorderLayout.SOUTH);

        frame.setSize(Theme.px(1280), Theme.px(860));
        frame.setMinimumSize(new Dimension(Theme.px(900), Theme.px(600)));
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private void runGame() {
        GameConfig cfg = GameConfig.build(GameConfig.DEFAULT_RULESET, players, seed, null, null);
        GameState state = Setup.buildGame(cfg);
        SwingUtilities.invokeLater(() -> boards.setRules(cfg.ruleset, cfg.content));

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
                msg -> SwingUtilities.invokeLater(() -> statusLabel.setText(msg)),
                r -> SwingUtilities.invokeLater(() -> onFrame(r)));
        } catch (Throwable t) {
            SwingUtilities.invokeLater(() -> statusLabel.setText("Партия прервана ошибкой: " + t));
            return;
        }
        ReplayRecord finalRec = result;
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Партия окончена: победитель "
                + (finalRec.winner == null ? "нет (" + finalRec.condition + ")" : "место " + (finalRec.winner + 1))
                + ", раундов " + finalRec.rounds + ". Журнал: " + savedPath(finalRec));
            clearDecisionPanel();
        });
        try {
            java.nio.file.Path out = java.nio.file.Path.of("reports", "hotseat",
                "hotseat-" + seed + ".kelium-replay.json");
            finalRec.save(out);
        } catch (java.io.IOException ignored) {
            // Партия и так доиграна и видна в окне — журнал на диск не критичен для игры.
        }
    }

    private String savedPath(ReplayRecord r) {
        return java.nio.file.Path.of("reports", "hotseat", "hotseat-" + seed + ".kelium-replay.json")
            .toAbsolutePath().toString();
    }

    private void onFrame(ReplayRecord r) {
        if (this.rec != r) {
            this.rec = r;
            field.setRecord(r);
        }
        if (r.frames.isEmpty()) {
            return;
        }
        ReplayRecord.Frame f = r.frames.get(r.frames.size() - 1);
        field.setFrame(f);
        if (f.snapshot != null) {
            boards.show(r, f.snapshot);
        }
        if (f.log != null && !f.log.isBlank()) {
            statusLabel.setText(f.log);
        }
        refreshHandPanel();
        if (awaitingSeat == null) {
            updateTurnBanner(f.snapshot == null ? null : f.snapshot.active, false);
        }
    }

    /** Верхняя полоса: чей сейчас ход, и ждёт ли партия живого клика/кнопки. */
    private void updateTurnBanner(Integer activeSeat, boolean waitingOnHuman) {
        if (activeSeat == null) {
            turnBanner.setText("Общая фаза раунда");
            turnBanner.setForeground(Theme.ink2());
            return;
        }
        String who = (activeSeat + 1) + ". " + seatSpecs.get(activeSeat);
        turnBanner.setText(waitingOnHuman ? "ВАШ ХОД — " + who : "Ходит: " + who
            + ("human".equals(seatSpecs.get(activeSeat)) ? "" : " (бот думает…)"));
        turnBanner.setForeground(Theme.seat(activeSeat));
    }

    /**
     * Виды точек решения, чей {@code payload} — это ГЕКС (или сводится к нему):
     * их предлагают выбором на самом поле, а не кнопками (обзор интерфейса,
     * 24.08.2026 — список опций, где выбираешь клеточку, читается кнопочным
     * текстом хуже, чем указыванием на неё). Список сознательно не полный —
     * остальные виды (действие, рынок, наука, карты в руке и т.п.) — это
     * "выбор одного из немногих именованных вариантов", кнопки для них уместны.
     */
    private static final Set<String> HEX_TARGET_KINDS = Set.of(
        "tower_hex", "build_hex", "move_hex", "energy_hex", "combat_source", "combat_target");

    private static final Map<String, String> KIND_LABELS = Map.ofEntries(
        Map.entry("action", "выберите действие"),
        Map.entry("reveal_order", "откройте приказ"),
        Map.entry("blind_discard", "слепой сброс приказа"),
        Map.entry("build_pick", "стройка"),
        Map.entry("tower_hex", "гекс для вышки"),
        Map.entry("build_hex", "гекс для постройки"),
        Map.entry("move_hex", "гекс для перемещения"),
        Map.entry("energy_hex", "гекс для энергии"),
        Map.entry("move", "куда шагнуть"),
        Map.entry("maneuver_unit", "какой отряд поведёте"),
        Map.entry("combat_source", "откуда атаковать"),
        Map.entry("combat_target", "цель атаки"),
        Map.entry("combat_victim", "кого поразить"),
        Map.entry("neutral_victim", "какой нейтрал атаковать"),
        Map.entry("assemble", "сборка"),
        Map.entry("tuck", "положите карту"),
        Map.entry("super_pick", "выберите супер-задание"),
        Map.entry("start_objective_pick", "стартовое задание"));

    private void showDecision(int seat, InteractiveAgent.PendingDecision d) {
        viewedSeat = seat;
        awaitingSeat = seat;
        updateTurnBanner(seat, true);
        if (seat < seatTabs.length) {
            seatTabs[seat].setSelected(true);
        }
        refreshHandPanel();

        String kind = String.valueOf(d.context().get("kind"));
        InteractiveAgent agent = humansBySeat.get(seat);
        List<Choice> options = d.options();

        decisionPanel.removeAll();
        JLabel title = new JLabel("Игрок " + (seat + 1) + " — " + KIND_LABELS.getOrDefault(kind, kind));
        title.setFont(Theme.font(12, Font.BOLD));
        title.setForeground(Theme.seat(seat));
        title.setBorder(BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8), Theme.px(4), Theme.px(8)));
        decisionPanel.add(title);

        Map<String, Integer> hexToIndex = hexTargets(kind, options);
        if (hexToIndex != null) {
            JLabel hint = new JLabel("Выберите гекс на поле");
            hint.setFont(Theme.font(12, Font.PLAIN));
            hint.setForeground(Theme.ink2());
            hint.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(8), Theme.px(8), Theme.px(8)));
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
                String text = c.label() == null || c.label().isEmpty()
                    ? String.valueOf(c.payload()) : c.label();
                JButton b = new JButton(text);
                b.setFont(Theme.font(12, Font.PLAIN));
                b.setForeground(Theme.ink());
                b.setBackground(Theme.tile());
                b.setAlignmentX(Component.LEFT_ALIGNMENT);
                b.setFocusPainted(false);
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
     * Если у ВСЕХ опций этой точки решения можно вытащить id гекса — карта
     * "гекс → номер опции" для клика по полю; иначе null (тогда решение —
     * кнопками). Правило "все или ничего": если хоть один вариант не сводится к
     * гексу (например, среди опций затесалась отмена не-гексового вида), решение
     * целиком остаётся кнопочным — придумывать вариант, которого не предложил
     * движок, нельзя.
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
    }

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
        sb.append("Место ").append(viewedSeat + 1).append(" — ").append(seatSpecs.get(viewedSeat)).append("\n\n");
        int vpTotal = 0;
        for (int v : p.vp.values()) {
            vpTotal += v;
        }
        sb.append("Очки: ").append(vpTotal).append('\n');
        sb.append("Монеты: ").append(p.coin)
            .append("   Келемий: ").append(p.kelium)
            .append("   Боеприпасы: ").append(p.ammo).append('\n');
        sb.append("Обломки: ").append(p.debris).append(" / ").append(p.debrisCap).append("\n\n");

        appendHand(sb, "Задания", p.objectiveHand);
        appendHand(sb, "Приказы в руке", p.orderHand);
        appendHand(sb, "Арсенал в руке", p.arsenalHand);

        handArea.setText(sb.toString());
        handArea.setCaretPosition(0);
    }

    private void appendHand(StringBuilder sb, String title, List<String> ids) {
        sb.append(title).append(" (").append(ids.size()).append("):\n");
        for (String id : ids) {
            sb.append("  · ").append(cardName(id)).append('\n');
        }
        sb.append('\n');
    }

    private String cardName(String id) {
        return rec == null ? id : rec.cardNames.getOrDefault(id, id);
    }
}
