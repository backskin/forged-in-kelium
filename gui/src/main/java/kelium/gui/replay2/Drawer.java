package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import kelium.report.ReplayRecord;

/**
 * Drawer — ЯЩИК-ИНСПЕКТОР: то, что приходит по вызову и уходит, когда ответ получен.
 *
 * <p>Главная мысль 2.0: у разбора один главный экран — поле. Лог, планшет игрока,
 * биография гекса, графики и журналы не живут на экране постоянно (в 1.0 они
 * отбирали у поля восемьдесят процентов площади), а выдвигаются справа по клавише
 * или по щелчку на то, о чём спросили.
 *
 * <p>Виды переключаются одной группой кнопок сверху. Глубже одного уровня не идём:
 * никаких вкладок внутри вкладок.
 */
public final class Drawer extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Что показывает ящик. */
    public enum View {
        LOG("Лог", "L"),
        PLAYER("Игрок", ""),
        HEX("Гекс", ""),
        CHART("График", "G"),
        ODD("Странности", ""),
        MOMENTS("Моменты", "");

        public final String label;
        public final String key;

        View(String label, String key) {
            this.label = label;
            this.key = key;
        }
    }

    private final Session session;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel head = new JLabel();
    private final Map<View, JToggleButton> switcher = new LinkedHashMap<>();
    private View view = View.LOG;
    private Runnable onClose = () -> { };

    // ---- лог
    private final DefaultMutableTreeNode logRoot = new DefaultMutableTreeNode("партия");
    private final DefaultTreeModel logModel = new DefaultTreeModel(logRoot);
    private final JTree logTree = new JTree(logModel);
    private final JTextField search = new JTextField();
    private final JComboBox<String> logFilter = new JComboBox<>(
        new String[]{"Всё", "Ходы", "Бои", "Мысли", "Итоги"});
    private final JCheckBox[] seatChips = new JCheckBox[4];
    private final JLabel hexChip = new JLabel();
    private boolean syncing;
    private boolean follow = true;

    // ---- планшет игрока
    private int sheetSeat;
    private final JLabel sheetText = new JLabel();
    private final OrdersTable orders;

    // ---- списки
    private final DefaultListModel<Session.Moment> hexModel = new DefaultListModel<>();
    private final DefaultListModel<Session.Moment> oddModel = new DefaultListModel<>();
    private final DefaultListModel<Session.Moment> momentModel = new DefaultListModel<>();
    private final Chart chart;

    public Drawer(Session session) {
        this.session = session;
        this.orders = new OrdersTable(session, 0);
        this.chart = new Chart(session);
        setOpaque(true);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.border()));

        add(header(), BorderLayout.NORTH);
        content.setOpaque(false);
        add(content, BorderLayout.CENTER);

        buildLogTab();
        session.whenRecordChanged(s -> {
            rebuildLog();
            rebuildLists();
        });
        session.whenFrameChanged(s -> {
            if (view == View.LOG) {
                selectLogRow();
            }
            if (view == View.PLAYER) {
                refreshSheet();
            }
            if (view == View.HEX) {
                rebuildHex();
            }
        });
        show(View.LOG);
    }

    /**
     * Фон берём ИЗ ТЕМЫ на каждую отрисовку, а не запоминаем при сборке: иначе при
     * переключении светлой и тёмной темы ящик оставался цвета прежней.
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        g.setColor(Theme.panel());
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    public void setOnClose(Runnable r) {
        this.onClose = r;
    }

    public View view() {
        return view;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(430), Theme.px(400));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(Theme.px(300), Theme.px(200));
    }

    // ==================== шапка ====================
    private JPanel header() {
        JPanel p = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(6) + " " + Theme.px(8) + " " + Theme.px(4) + " "
                + Theme.px(6) + ", gapx " + Theme.px(3) + ", novisualpadding", "[]push[]"));
        p.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        JPanel tabs = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(2)));
        tabs.setOpaque(false);
        for (View v : View.values()) {
            JToggleButton b = new JToggleButton(v.label);
            b.setFont(Theme.font(12, Font.PLAIN));
            b.setFocusable(false);
            b.setToolTipText(Ui2.tip(v.key.isEmpty() ? v.label
                : v.label + " (клавиша " + v.key + ")"));
            b.addActionListener(e -> show(v));
            group.add(b);
            switcher.put(v, b);
            tabs.add(b);
        }
        p.add(tabs);
        JButton close = Ui2.textButton("закрыть", "Закрыть ящик (Esc).", () -> onClose.run());
        p.add(close);
        return p;
    }

    /** Показать нужный вид. */
    public void show(View v) {
        this.view = v;
        switcher.get(v).setSelected(true);
        content.removeAll();
        switch (v) {
            case LOG -> content.add(logPanel(), BorderLayout.CENTER);
            case PLAYER -> content.add(playerPanel(), BorderLayout.CENTER);
            case HEX -> content.add(listPanel(hexModel,
                "Щёлкни по гексу на поле — здесь будет всё, что на нём происходило."),
                BorderLayout.CENTER);
            case CHART -> content.add(chartPanel(), BorderLayout.CENTER);
            case ODD -> content.add(listPanel(oddModel,
                "Странностей не нашлось: ни одного шага, где действие не получилось."),
                BorderLayout.CENTER);
            case MOMENTS -> content.add(listPanel(momentModel,
                "Поворотные моменты появятся, когда партия будет сыграна."),
                BorderLayout.CENTER);
            default -> { }
        }
        content.revalidate();
        content.repaint();
    }

    // ==================== лог ====================
    private JPanel logCard;

    private void buildLogTab() {
        logTree.setRootVisible(false);
        logTree.setShowsRootHandles(true);
        logTree.setFont(Theme.body());
        logTree.setRowHeight(Theme.px(20));
        // ТОТ ЖЕ БАГ, ЧТО В HelpWindow (18.08.2026): фон JTree по умолчанию —
        // светлый от L&F, и без явной установки он не совпадает с тёмной темой
        // за пределами занятых строк (пустое место внизу дерева, вьюпорт скролла).
        logTree.setBackground(Theme.panel());
        logTree.setCellRenderer(new LogRenderer());
        logTree.getSelectionModel().setSelectionMode(
            javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);
        logTree.addTreeSelectionListener(e -> {
            if (syncing) {
                return;
            }
            Object node = logTree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode n
                    && n.getUserObject() instanceof Row row && row.frame >= 0) {
                session.seek(row.frame);
            }
        });
        // НАВЕДЕНИЕ подсвечивает гексы события НА ПОЛЕ, но не перематывает: дёшево и
        // очень полезно при чтении лога.
        logTree.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int row = logTree.getRowForLocation(e.getX(), e.getY());
                if (row < 0) {
                    return;
                }
                Object o = logTree.getPathForRow(row).getLastPathComponent();
                if (o instanceof DefaultMutableTreeNode n
                        && n.getUserObject() instanceof Row r && r.frame >= 0) {
                    logTree.setToolTipText(Ui2.tip(r.text
                        + "\n\nЩелчок — перемотка к этому шагу."));
                }
            }
        });
        javax.swing.ToolTipManager.sharedInstance().registerComponent(logTree);
    }

    private JPanel logPanel() {
        if (logCard != null) {
            return logCard;
        }
        logCard = new JPanel(new BorderLayout(0, Theme.px(4)));
        logCard.setOpaque(false);

        JPanel top = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0 " + Theme.px(8) + " 0 " + Theme.px(8) + ", gapx " + Theme.px(4)
                + ", novisualpadding", "[]" + Theme.px(6) + "[]push[]"));
        top.setOpaque(false);
        logFilter.setFont(Theme.font(12, Font.PLAIN));
        logFilter.setFocusable(false);
        logFilter.setToolTipText(Ui2.tip("Что оставить в логе. Один выбор из пяти — "
            + "в 1.0 три галочки складывались по «или» и по «и», и предсказать это "
            + "по интерфейсу было нельзя."));
        logFilter.addActionListener(e -> rebuildLog());
        top.add(logFilter);
        JPanel chips = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(2)));
        chips.setOpaque(false);
        for (int i = 0; i < 4; i++) {
            final int seat = i;
            // Номер места ЦИФРОЙ: кружочки ①②③④ системный шрифт не рисует
            JCheckBox c = new JCheckBox(String.valueOf(i + 1));
            c.setFont(Theme.font(13, Font.BOLD));
            // Краска места — она одна и та же в любой теме, поэтому не устареет
            c.setForeground(Theme.seat(i));
            c.setFocusable(false);
            c.setToolTipText(Ui2.tip("Показывать строки места " + (seat + 1) + "."));
            c.addActionListener(e -> rebuildLog());
            seatChips[i] = c;
            chips.add(c);
        }
        top.add(chips);
        hexChip.setFont(Theme.font(11, Font.PLAIN));
        Ui2.fg(hexChip, "accent");
        hexChip.setToolTipText(Ui2.tip("Фильтр по гексу. Появляется от щелчка по полю; "
            + "снять — щелчком по этой метке."));
        hexChip.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        hexChip.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                session.selectHex(null);
                rebuildLog();
            }
        });
        top.add(hexChip);
        logCard.add(top, BorderLayout.NORTH);

        JPanel mid = new JPanel(new BorderLayout(Theme.px(4), 0));
        mid.setOpaque(false);
        mid.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(8), 0, Theme.px(8)));
        search.putClientProperty("JTextField.placeholderText", "поиск по логу");
        search.setFont(Theme.body());
        search.addActionListener(e -> rebuildLog());
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                rebuildLog();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                rebuildLog();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                rebuildLog();
            }
        });
        mid.add(search, BorderLayout.CENTER);
        JToggleButton followBtn = new JToggleButton("следить", true);
        followBtn.setFont(Theme.font(11, Font.PLAIN));
        followBtn.setFocusable(false);
        followBtn.setToolTipText(Ui2.tip("Прокручивать лог за текущим шагом."));
        followBtn.addActionListener(e -> follow = followBtn.isSelected());
        mid.add(followBtn, BorderLayout.EAST);
        JPanel head2 = new JPanel(new BorderLayout());
        head2.setOpaque(false);
        head2.add(mid, BorderLayout.CENTER);
        logCard.add(head2, BorderLayout.CENTER);

        JScrollPane sc = new JScrollPane(logTree);
        sc.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(4), 0, 0));
        sc.getVerticalScrollBar().setUnitIncrement(Theme.px(18));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(head2, BorderLayout.NORTH);
        wrap.add(sc, BorderLayout.CENTER);
        logCard.removeAll();
        logCard.add(top, BorderLayout.NORTH);
        logCard.add(wrap, BorderLayout.CENTER);
        return logCard;
    }

    /** Строка лога: к какому кадру ведёт и как выглядит. */
    private record Row(int frame, int seat, String text, Kind kind) {
        enum Kind { ROUND, TURN, EVENT, THOUGHT, COMBAT }
    }

    /**
     * ДЕРЕВО ЛОГА: раунд·круг → ход игрока → события. Ход показывает сводку одной
     * строкой, поэтому полторы тысячи строк 1.0 превращаются в сотню обозримых, а
     * подробности разворачиваются по месту.
     */
    private void rebuildLog() {
        logRoot.removeAllChildren();
        ReplayRecord rec = session.record();
        if (rec == null) {
            logModel.reload();
            return;
        }
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
        String filter = String.valueOf(logFilter.getSelectedItem());
        String hex = session.selectedHex();
        hexChip.setText(hex == null ? "" : "гекс " + hex + "  ✕");

        DefaultMutableTreeNode roundNode = null;
        DefaultMutableTreeNode turnNode = null;
        int lastRound = -1;
        int lastCircle = -1;
        List<String> turnActions = new ArrayList<>();

        for (int i = 0; i < rec.frames.size(); i++) {
            ReplayRecord.Frame f = rec.frames.get(i);
            if (f.round != lastRound || f.circle != lastCircle) {
                lastRound = f.round;
                lastCircle = f.circle;
                roundNode = new DefaultMutableTreeNode(new Row(i, -1,
                    "РАУНД " + f.round + (f.circle > 0 ? " · круг " + f.circle : ""),
                    Row.Kind.ROUND));
                logRoot.add(roundNode);
                turnNode = null;
            }
            if ("turn_orders".equals(f.type) && f.seat != null) {
                turnActions = new ArrayList<>();
                turnNode = new DefaultMutableTreeNode(new Row(i, f.seat,
                    rec.playerName(f.seat), Row.Kind.TURN));
                roundNode.add(turnNode);
            }
            DefaultMutableTreeNode parent = turnNode != null ? turnNode : roundNode;

            for (ReplayRecord.Thought t : f.thoughts) {
                Row r = new Row(i, t.seat, "«" + t.text + "»", Row.Kind.THOUGHT);
                if (keep(r, filter, q, hex, f)) {
                    parent.add(new DefaultMutableTreeNode(r));
                }
            }
            if (f.log != null && !f.log.isBlank()) {
                // Текст из движка чистим от символов, которых нет в шрифте
                Row r = new Row(i, f.seat == null ? -1 : f.seat, Names.printable(f.log),
                    f.combat ? Row.Kind.COMBAT : Row.Kind.EVENT);
                if (keep(r, filter, q, hex, f)) {
                    parent.add(new DefaultMutableTreeNode(r));
                }
                if (turnNode != null && "action".equals(f.type)) {
                    String a = firstWord(f.log);
                    if (a != null && !turnActions.contains(a)) {
                        turnActions.add(a);
                        Row t = (Row) turnNode.getUserObject();
                        turnNode.setUserObject(new Row(t.frame(), t.seat(),
                            rec.playerName(t.seat()) + " · " + String.join(", ", turnActions),
                            Row.Kind.TURN));
                    }
                }
            }
        }
        // пустые узлы убираем: иначе дерево пестрит раундами без содержимого
        prune(logRoot);
        logModel.reload();
        // РАСКРЫВАЕМ ПО ПУТЯМ, а не по номерам строк: номера сдвигаются от каждого
        // раскрытия, и раскрытие «по индексу» попадало не туда (в итоге дерево
        // оставалось свёрнутым и в логе были видны только заголовки раундов).
        List<TreePath> open = new ArrayList<>();
        for (int i = 0; i < logRoot.getChildCount(); i++) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) logRoot.getChildAt(i);
            open.add(new TreePath(new Object[]{logRoot, n}));
        }
        for (TreePath p : open) {
            logTree.expandPath(p);
        }
        selectLogRow();
    }

    private static void prune(DefaultMutableTreeNode node) {
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            DefaultMutableTreeNode ch = (DefaultMutableTreeNode) node.getChildAt(i);
            prune(ch);
            Object uo = ch.getUserObject();
            boolean group = uo instanceof Row r
                && (r.kind() == Row.Kind.ROUND || r.kind() == Row.Kind.TURN);
            if (group && ch.getChildCount() == 0) {
                node.remove(i);
            }
        }
    }

    private static String firstWord(String log) {
        String s = log.trim().replaceAll("^[▪•·\\s]+", "");
        int colon = s.indexOf(':');
        String head = colon > 0 ? s.substring(0, colon) : s;
        head = head.trim().toLowerCase(java.util.Locale.ROOT);
        return head.length() > 24 ? null : head;
    }

    private boolean keep(Row r, String filter, String q, String hex, ReplayRecord.Frame f) {
        boolean okFilter = switch (filter) {
            case "Ходы" -> r.kind() == Row.Kind.EVENT || r.kind() == Row.Kind.COMBAT;
            case "Бои" -> r.kind() == Row.Kind.COMBAT;
            case "Мысли" -> r.kind() == Row.Kind.THOUGHT;
            case "Итоги" -> "return".equals(f.type) || "game_end".equals(f.type);
            default -> true;
        };
        if (!okFilter) {
            return false;
        }
        boolean anyChip = false;
        for (JCheckBox c : seatChips) {
            if (c != null && c.isSelected()) {
                anyChip = true;
                break;
            }
        }
        if (anyChip) {
            if (r.seat() < 0 || r.seat() > 3 || !seatChips[r.seat()].isSelected()) {
                return false;
            }
        }
        if (!q.isEmpty() && !r.text().toLowerCase().contains(q)) {
            return false;
        }
        if (hex != null) {
            boolean touches = r.text().contains(hex)
                || f.highlight.builds.contains(hex) || f.highlight.damaged.contains(hex)
                || f.highlight.destroyed.contains(hex);
            for (String[] mv : f.highlight.moves) {
                touches |= hex.equals(mv[0]) || hex.equals(mv[1]);
            }
            for (String[] at : f.highlight.attacks) {
                touches |= hex.equals(at[0]) || hex.equals(at[1]);
            }
            if (!touches) {
                return false;
            }
        }
        return true;
    }

    /** Подсветить строку текущего шага и подвести к ней прокрутку. */
    private void selectLogRow() {
        if (!follow) {
            return;
        }
        int best = -1;
        for (int i = 0; i < logTree.getRowCount(); i++) {
            Object o = logTree.getPathForRow(i).getLastPathComponent();
            if (o instanceof DefaultMutableTreeNode n
                    && n.getUserObject() instanceof Row r && r.frame() <= session.cursor()) {
                best = i;
            }
        }
        if (best < 0) {
            return;
        }
        syncing = true;
        logTree.setSelectionRow(best);
        // Прокручиваем ТОЛЬКО по вертикали: обычный scrollRowToVisible тянет вид и
        // вправо, чтобы показать строку целиком, и начало строк уезжает за край.
        java.awt.Rectangle r = logTree.getRowBounds(best);
        if (r != null) {
            r.x = 0;
            r.width = 1;
            logTree.scrollRectToVisible(r);
        }
        syncing = false;
    }

    private final class LogRenderer extends DefaultTreeCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                      boolean expanded, boolean leaf, int row,
                                                      boolean focus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, focus);
            setIcon(null);
            setOpaque(false);
            Object uo = value instanceof DefaultMutableTreeNode n ? n.getUserObject() : null;
            if (!(uo instanceof Row r)) {
                return this;
            }
            setText(r.text());
            Color fg = r.seat() >= 0 ? Theme.seatInk(r.seat()) : Theme.ink2();
            switch (r.kind()) {
                case ROUND -> {
                    setFont(Theme.caption());
                    setForeground(Theme.ink3());
                }
                // ХОД ИГРОКА — плашкой цвета места: в дереве это самая частая
                // строка, и по ней глаз должен сразу находить нужного игрока.
                case TURN -> {
                    setFont(Theme.font(12, Font.BOLD));
                    setForeground(java.awt.Color.WHITE);
                    if (r.seat() >= 0) {
                        setOpaque(true);
                        setBackground(Theme.seat(r.seat()));
                    }
                }
                case THOUGHT -> {
                    setFont(Theme.italic());
                    setForeground(Theme.alpha(fg, 0.85));
                }
                case COMBAT -> {
                    setFont(Theme.font(12, Font.BOLD));
                    setForeground(Theme.damage());
                }
                default -> {
                    setFont(Theme.font(12, Font.PLAIN));
                    setForeground(r.seat() >= 0 ? fg : Theme.ink());
                }
            }
            if (sel) {
                setForeground(Theme.ink());
            }
            return this;
        }
    }

    // ==================== планшет игрока ====================
    private JPanel playerCard;

    /** Открыть подробный планшет места. */
    public void showPlayer(int seat) {
        sheetSeat = seat;
        orders.setSeat(seat);
        show(View.PLAYER);
        refreshSheet();
    }

    private JPanel playerPanel() {
        if (playerCard == null) {
            playerCard = new JPanel(new BorderLayout(0, Theme.px(6)));
            playerCard.setOpaque(false);
            playerCard.setBorder(BorderFactory.createEmptyBorder(
                Theme.px(4), Theme.px(10), Theme.px(8), Theme.px(10)));
            sheetText.setVerticalAlignment(JLabel.TOP);
            sheetText.setFont(Theme.body());
            JScrollPane sc = new JScrollPane(sheetText);
            sc.setBorder(null);
            sc.getVerticalScrollBar().setUnitIncrement(Theme.px(18));
            playerCard.add(sc, BorderLayout.CENTER);
            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);
            bottom.add(Ui2.caption("приказы этого раунда"), BorderLayout.NORTH);
            bottom.add(orders, BorderLayout.CENTER);
            bottom.setPreferredSize(new Dimension(Theme.px(300), Theme.px(220)));
            playerCard.add(bottom, BorderLayout.SOUTH);
        }
        refreshSheet();
        return playerCard;
    }

    /**
     * РАСКЛАД ИГРОКА словами: колода приказов и стороны обоих планшетов. Чего в
     * записи нет — того и в строке нет: старые записи не несли сторону склада.
     */
    private static String setupLine(ReplayRecord.Player p) {
        java.util.List<String> bits = new java.util.ArrayList<>();
        if (p.orderColor != null && !p.orderColor.isBlank()) {
            bits.add("колода приказов " + Names.orderDeck(p.orderColor));
        }
        if (p.side != null && !p.side.isBlank()) {
            bits.add("планшет войск " + p.side);
        }
        if (p.storageSide != null && !p.storageSide.isBlank()) {
            bits.add("планшет хранилища " + p.storageSide);
        }
        return bits.isEmpty() ? "расклад не записан" : String.join(" · ", bits);
    }

    private void refreshSheet() {
        ReplayRecord.Frame f = session.frame();
        if (f == null || f.snapshot == null || sheetSeat >= f.snapshot.players.size()) {
            sheetText.setText("");
            return;
        }
        ReplayRecord.Player p = f.snapshot.players.get(sheetSeat);
        ReplayRecord rec = session.record();
        StringBuilder sb = new StringBuilder("<html><body style='width:")
            .append(Theme.px(330)).append("px'>");
        sb.append("<div style='font-size:12pt'><b>").append(esc(rec.playerName(sheetSeat)))
          .append("</b></div>");
        // РАСКЛАД ИГРОКА целиком: колода приказов и ОБЕ стороны планшетов. Раньше
        // здесь была только сторона войск — ни колоды, ни склада, хотя всё это
        // определяет, чем игрок играет (замечание дизайнера 14.08.2026).
        sb.append("<div>").append(esc(setupLine(p))).append("</div><br>");
        sb.append(row("Очки", describeVp(p)));
        sb.append(row("Ресурсы", "монеты " + p.coin + " · келемий " + p.kelium
            + " · боеприпасы " + p.ammo + " · обломки " + p.debris));
        sb.append(row("Склад", "занято " + (p.kelium + p.ammo + p.debris) + " из " + p.storeCap
            + " (келемий ≤ " + p.keliumCap + ", боеприпасы ≤ " + p.ammoCap
            + ", обломок — любая ячейка)"
            + " · контейнеры " + p.containers
            + (p.containerCap >= 0 ? " из " + p.containerCap : "")));
        sb.append(row("Здания", buildings(f, sheetSeat)));
        sb.append(row("Войска", units(f, sheetSeat)));
        List<String> tech = new ArrayList<>();
        for (Map.Entry<String, Integer> e : p.tech.entrySet()) {
            tech.add(Names.track(e.getKey()) + " (" + Names.trackGives(e.getKey()) + ") — шаг "
                + e.getValue());
        }
        sb.append(row("Наука", tech.isEmpty() ? "шагов пока нет" : String.join("; ", tech)));
        sb.append(row("Модули", "красные " + p.redModules + " · синие " + p.blueModules
            + " · золотые " + p.goldModules));
        sb.append(row("Арсенал", "в руке " + p.arsenalHand.size() + " · установлено "
            + (p.arsenalInstalled.isEmpty() ? "нет" : cards(p.arsenalInstalled))));
        sb.append(row("Задания", p.objectiveHand.isEmpty() ? "рука пуста"
            : cards(p.objectiveHand)));
        sb.append(row("Супер-задание", p.superObjective == null ? "не выдано"
            : "«" + esc(Names.card(rec, p.superObjective)) + "» — внесено " + p.superProgress
              + (p.superComplete ? ", СОБРАНО" : "")));
        sb.append(row("Жетоны ЦУ", "свой " + (p.ownCuToken ? "цел" : "потерян")
            + " · захвачено чужих " + p.cuTokens));
        sb.append("</body></html>");
        sheetText.setText(sb.toString());
    }

    private static String row(String name, String value) {
        return "<div style='margin-bottom:3px'><b>" + name + ":</b> " + esc(value) + "</div>";
    }

    private String cards(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            out.add("«" + Names.card(session.record(), id) + "»");
        }
        return String.join(", ", out);
    }

    private String buildings(ReplayRecord.Frame f, int owner) {
        List<String> out = new ArrayList<>();
        for (ReplayRecord.Tok t : f.snapshot.tokens) {
            if (!t.building || t.owner != owner || t.hexId == null || !t.alive) {
                continue;
            }
            StringBuilder sb = new StringBuilder(Names.building(t.type));
            if (t.level != null) {
                sb.append(" №").append(t.level);
            }
            sb.append(" на ").append(t.hexId);
            if (t.damage > 0) {
                sb.append(" (урон ").append(t.damage).append(')');
            }
            if (t.energySlots > 0) {
                sb.append(t.energyPlaced >= t.energySlots ? " [запитан]" : " [без энергии]");
            }
            out.add(sb.toString());
        }
        return out.isEmpty() ? "на поле пусто" : String.join(", ", out);
    }

    private String units(ReplayRecord.Frame f, int owner) {
        Map<String, Integer> field = new LinkedHashMap<>();
        Map<String, Integer> reserve = new LinkedHashMap<>();
        for (ReplayRecord.Tok t : f.snapshot.tokens) {
            if (t.building || t.owner != owner || !t.alive) {
                continue;
            }
            (t.hexId != null ? field : reserve).merge(Names.unit(t.type), 1, Integer::sum);
        }
        return "на поле — " + join(field) + "; в резерве — " + join(reserve);
    }

    private static String join(Map<String, Integer> m) {
        if (m.isEmpty()) {
            return "никого";
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            out.add(e.getKey() + " " + e.getValue());
        }
        return String.join(", ", out);
    }

    private static String describeVp(ReplayRecord.Player p) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : p.vp.entrySet()) {
            if ("total".equals(e.getKey()) || e.getValue() == 0) {
                continue;
            }
            parts.add(Names.vp(e.getKey()) + " " + e.getValue());
        }
        return p.vp.getOrDefault("total", 0) + " ПО"
            + (parts.isEmpty() ? "" : " = " + String.join(" + ", parts));
    }

    // ==================== списки ====================
    private JPanel listPanel(DefaultListModel<Session.Moment> model, String emptyText) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JList<Session.Moment> list = new JList<>(model);
        list.setFont(Theme.body());
        list.setFixedCellHeight(Theme.px(22));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, value, index, sel, focus);
                setText(value instanceof Session.Moment m ? m.text() : String.valueOf(value));
                setFont(Theme.font(12, Font.PLAIN));
                setBorder(BorderFactory.createEmptyBorder(0, Theme.px(8), 0, 0));
                return this;
            }
        });
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            Session.Moment m = list.getSelectedValue();
            if (m != null) {
                session.seek(m.frame());
            }
        });
        JScrollPane sc = new JScrollPane(list);
        sc.setBorder(null);
        sc.getVerticalScrollBar().setUnitIncrement(Theme.px(18));
        p.add(sc, BorderLayout.CENTER);
        if (model.isEmpty()) {
            JLabel empty = new JLabel(Ui2.tip(emptyText, 300));
            empty.setForeground(Theme.ink3());
            empty.setBorder(BorderFactory.createEmptyBorder(Theme.px(10), Theme.px(10),
                Theme.px(10), Theme.px(10)));
            p.add(empty, BorderLayout.NORTH);
        }
        return p;
    }

    private JPanel chartPanel() {
        JPanel p = new JPanel(new BorderLayout(0, Theme.px(4)));
        p.setOpaque(false);
        JPanel top = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(4) + " " + Theme.px(8) + " 0 " + Theme.px(8)
                + ", gapx " + Theme.px(2)));
        top.setOpaque(false);
        ButtonGroup g = new ButtonGroup();
        for (Chart.Metric m : Chart.Metric.values()) {
            JToggleButton b = new JToggleButton(m.label);
            b.setFont(Theme.font(11, Font.PLAIN));
            b.setFocusable(false);
            b.setSelected(m == chart.metric());
            b.addActionListener(e -> chart.setMetric(m));
            g.add(b);
            top.add(b);
        }
        p.add(top, BorderLayout.NORTH);
        p.add(chart, BorderLayout.CENTER);
        return p;
    }

    private void rebuildLists() {
        oddModel.clear();
        for (Session.Moment m : session.oddities()) {
            oddModel.addElement(m);
        }
        momentModel.clear();
        for (Session.Moment m : session.turningPoints()) {
            momentModel.addElement(m);
        }
        rebuildHex();
    }

    private void rebuildHex() {
        hexModel.clear();
        String hex = session.selectedHex();
        if (hex == null) {
            return;
        }
        for (Session.Moment m : session.hexBiography(hex)) {
            hexModel.addElement(m);
        }
    }

    /** Открыть биографию гекса (вызывается щелчком по полю). */
    public void showHex(String hexId) {
        rebuildHex();
        show(View.HEX);
        rebuildLog();
    }

    /** Обновить журналы и моменты (после нового прогона). */
    public void refreshAll() {
        rebuildLog();
        rebuildLists();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    /** Действие «закрыть по Esc» — окно вешает его на ящик. */
    public AbstractAction closeAction() {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                onClose.run();
            }
        };
    }

    /** Развернуть все раунды дерева лога (для поиска глазами). */
    public void expandLog() {
        for (int i = 0; i < logTree.getRowCount(); i++) {
            logTree.expandRow(i);
        }
    }

    /** Путь к выбранной строке — нужен тестам и отладке. */
    TreePath selectedLogPath() {
        return logTree.getSelectionPath();
    }
}
