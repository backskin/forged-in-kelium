package kelium.gui.net;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.miginfocom.swing.MigLayout;

import kelium.gui.FieldView;
import kelium.gui.kp.KpButton;
import kelium.gui.replay2.Theme;
import kelium.report.ReplayRecord;

/**
 * ОКНО ПАРТИИ У ДРУГА — ПРОТОТИП.
 *
 * <p>Движка у клиента нет, поэтому окно горячего стула (оно рисует из живого
 * {@code GameState}) здесь не годится как есть — это следующий этап
 * (см. «СЕТЬ — архитектура», §8 п.1). Пока: настоящее поле тем же
 * рисовальщиком по присланным кадрам, своя рука и ресурсы, лента, и вопрос —
 * вариантами-кнопками; варианты с гексом выбираются и щелчком по полю.
 *
 * <p>Шторки нет: у каждого свой экран. Ходит другой — строка сверху пишет
 * «Ход соперника: …», вопросов нет. Связь оборвалась — окно само
 * переподключается и получает всю запись заново.
 */
public final class NetClientWindow {

    private final NetClient client;
    private final int seat;
    private final List<String> names;
    private final Runnable onClose;

    private JFrame frame;
    private FieldView field;
    private JLabel status;
    private JLabel promptLabel;
    private JPanel optionList;
    private JTextArea hand;
    private JTextArea feed;
    private KpButton undoBtn;
    private KpButton undoAllBtn;
    private NetOverlay overlay;
    private NetChatDock chat;
    private final List<String> pendingChat = new ArrayList<>();
    private boolean closedByHost;
    private ReplayRecord rec;
    private Map<String, Object> question;
    private boolean finished;
    private volatile boolean reconnecting;
    private int fedFrames;

    NetClientWindow(NetClient client, int seat, List<String> names, Runnable onClose) {
        this.client = client;
        this.seat = seat;
        this.names = new ArrayList<>(names);
        this.onClose = onClose;
    }

    /** Собрать окно (на потоке интерфейса). */
    void show() {
        SwingUtilities.invokeLater(this::build);
    }

    private void build() {
        Theme.applyTable();
        frame = new JFrame("Кристаллы Раздора — сетевая партия · " + name(seat));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.getContentPane().setBackground(Theme.bg());
        frame.getContentPane().setLayout(new BorderLayout());

        JPanel top = new JPanel(new MigLayout("insets " + Theme.px(10) + " " + Theme.px(16)
            + ", fillx", "[grow][]"));
        top.setBackground(Theme.panel());
        top.setBorder(BorderFactory.createMatteBorder(0, 0, Theme.px(2), 0, Theme.border()));
        status = label("Ждём первый кадр стола…", 18, Font.BOLD, Theme.ink());
        top.add(status);
        top.add(label("вы — место " + (seat + 1) + " · " + name(seat), 14, Font.PLAIN,
            Theme.ink2()));
        frame.add(top, BorderLayout.NORTH);

        field = new FieldView();
        field.setTableBackdrop(true);
        frame.add(field, BorderLayout.CENTER);

        JPanel side = new JPanel(new MigLayout("insets " + Theme.px(12) + ", fill, gap "
            + Theme.px(10), "[grow,fill]", "[][" + Theme.px(220) + ":45%:,fill]["
            + Theme.px(130) + ":" + Theme.px(160) + ":,fill][" + Theme.px(120) + ":,grow,fill]"));
        side.setBackground(Theme.panel());
        side.setPreferredSize(new Dimension(Theme.px(430), Theme.px(600)));
        side.setBorder(BorderFactory.createMatteBorder(0, Theme.px(1), 0, 0, Theme.border()));
        promptLabel = label("Вопросов пока нет", 16, Font.BOLD, Theme.ink2());
        side.add(promptLabel, "wrap");
        // ОТМЕНА СВОИХ РЕШЕНИЙ — как за горячим стулом, но не глубже первого
        // чужого решения (решение Влада 26.09.2026); можно ли — говорит хост.
        undoBtn = new KpButton("Шаг назад", "Ctrl+Z", null);
        undoBtn.setToolTipText("Отменить последнее своё решение в этом круге");
        undoBtn.onClick(() -> undo(false));
        undoAllBtn = new KpButton("К началу хода", "", null);
        undoAllBtn.setToolTipText("Отменить всё, что вы решили в этом круге, "
            + "пока после вас не ходил другой игрок");
        undoAllBtn.onClick(() -> undo(true));
        side.add(undoBtn, "split 2, w " + Theme.px(180) + "!, h " + Theme.px(42) + "!");
        side.add(undoAllBtn, "w " + Theme.px(200) + "!, h " + Theme.px(42) + "!, wrap");
        refreshUndo(0);
        optionList = new JPanel(new MigLayout("insets 0, fillx, gapy " + Theme.px(6),
            "[grow,fill]"));
        optionList.setBackground(Theme.panel());
        JScrollPane os = new JScrollPane(optionList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        os.setBorder(null);
        os.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        side.add(os, "wrap");
        hand = area();
        side.add(titled("ВАША РУКА И ЗАПАСЫ", hand), "wrap");
        feed = area();
        side.add(titled("ЛЕНТА", feed), "wrap");
        frame.add(side, BorderLayout.EAST);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                finished = true;
                client.close();
                if (onClose != null) {
                    onClose.run();
                }
            }
        });
        frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "undo-step");
        frame.getRootPane().getActionMap().put("undo-step", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                undo(false);
            }
        });

        frame.setSize(Theme.px(1500), Theme.px(900));
        frame.setLocationRelativeTo(null);
        kelium.gui.Offscreen.show(frame);
        overlay = new NetOverlay(frame);
        chat = new NetChatDock(frame, client::chat);
        overlay.allow(chat);
        for (String line : pendingChat) {
            chat.add(line);
        }
        pendingChat.clear();
    }

    // ==================== от хоста (поток провода) ====================

    void record(ReplayRecord part, boolean reset, int from) {
        SwingUtilities.invokeLater(() -> {
            ReplayRecord merged = NetClient.merge(rec, part, reset, from);
            if (merged == null) {
                client.resync();          // кусок не стыкуется — всю запись заново
                return;
            }
            if (merged != rec) {
                rec = merged;
                fedFrames = 0;
                feed.setText("");
                field.setRecord(rec);
            }
            refreshFrame();
        });
    }

    void decide(Map<String, Object> q) {
        SwingUtilities.invokeLater(() -> showQuestion(q));
    }

    void over(Map<String, Object> r) {
        SwingUtilities.invokeLater(() -> {
            finished = true;
            clearQuestion();
            Object w = r.get("winner");
            status.setText("Партия окончена: " + (w instanceof Number n
                ? "победил " + name(n.intValue()) : "без победителя (" + r.get("condition") + ")")
                + " · раундов " + r.get("rounds"));
            status.setForeground(Theme.points());
            promptLabel.setText("Сид партии: " + r.get("seed") + " — её можно пересмотреть");
        });
    }

    /** Строка чата «кто: что». */
    void chat(String line) {
        SwingUtilities.invokeLater(() -> {
            if (chat == null) {
                pendingChat.add(line);
            } else {
                chat.add(line);
            }
        });
    }

    /** Игрок вышел из игры — затенение и «ждём решения хоста», без кнопок. */
    void paused(int who, String whoName, boolean waiting) {
        SwingUtilities.invokeLater(() -> {
            if (overlay == null || closedByHost) {
                return;
            }
            overlay.display("Игрок " + (who + 1) + (whoName == null ? "" : " (" + whoName + ")")
                    + " вышел из игры",
                waiting ? "Ждём решения хоста. Хост ждёт, когда игрок вернётся."
                    : "Ждём решения хоста.", List.of());
        });
    }

    /** Игрок вернулся или место отдано боту — игра продолжается. */
    void resumed(int who, String how) {
        SwingUtilities.invokeLater(() -> {
            if (overlay == null || closedByHost) {
                return;
            }
            overlay.dismiss();
            feed.append(("bot".equals(how) ? "Место игрока " + name(who) + " отдано боту"
                : name(who) + " вернулся в игру") + "\n");
        });
    }

    /** Хост закрыл партию — в том же окне: текст и «Выйти из игры». */
    void closed() {
        SwingUtilities.invokeLater(() -> {
            finished = true;
            closedByHost = true;
            clearQuestion();
            status.setText("Хост закрыл партию");
            status.setForeground(Theme.bad());
            if (overlay != null) {
                overlay.display("Хост закрыл партию", "Партия окончена без итога.",
                    List.of(new NetOverlay.Action("Выйти из игры", "закрыть окно партии",
                        () -> frame.dispose(), true, false)));
            }
        });
    }

    /** Хост не пустил обратно (место отдано боту) — то же окно, «Выйти из игры». */
    void rejected(String reason) {
        SwingUtilities.invokeLater(() -> {
            finished = true;
            closedByHost = true;
            clearQuestion();
            status.setText("Вы вне партии: " + reason);
            status.setForeground(Theme.bad());
            if (overlay != null) {
                overlay.display("Вы вне партии", cap(reason) + ".",
                    List.of(new NetOverlay.Action("Выйти из игры", "закрыть окно партии",
                        () -> frame.dispose(), true, false)));
            }
        });
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Что на шторке сейчас (для проверок); null — шторки нет. */
    String overlayTitle() {
        return overlay != null && overlay.shown() ? overlay.titleText() : null;
    }

    void disconnected() {
        if (finished || reconnecting) {
            return;
        }
        reconnecting = true;
        SwingUtilities.invokeLater(() -> {
            status.setText("Нет связи с хостом — переподключаемся…");
            status.setForeground(Theme.bad());
            clearQuestion();
        });
        Thread t = new Thread(() -> {
            for (int i = 0; i < 200 && !finished; i++) {
                try {
                    Thread.sleep(3000);
                    client.reconnect();
                    reconnecting = false;
                    SwingUtilities.invokeLater(() -> status.setForeground(Theme.ink()));
                    return;
                } catch (Exception e) {
                    // хост ещё не вернулся — пробуем дальше
                }
            }
            reconnecting = false;
        }, "net-reconnect");
        t.setDaemon(true);
        t.start();
    }

    // ==================== показ ====================

    private void refreshFrame() {
        if (rec == null || rec.frames.isEmpty()) {
            return;
        }
        ReplayRecord.Frame last = rec.frames.get(rec.frames.size() - 1);
        field.setFrame(last);
        StringBuilder sb = new StringBuilder();
        for (int i = fedFrames; i < rec.frames.size(); i++) {
            String log = rec.frames.get(i).log;
            if (log != null && !log.isBlank()) {
                sb.append(clean(log)).append('\n');
            }
        }
        fedFrames = rec.frames.size();
        if (sb.length() > 0) {
            feed.append(sb.toString());
            feed.setCaretPosition(feed.getDocument().getLength());
        }
        refreshHand(last.snapshot);
        if (question == null && !finished) {
            Integer active = last.snapshot == null ? null : last.snapshot.active;
            if (active == null) {
                status.setText("Общая фаза раунда " + last.round);
            } else if (active == seat) {
                status.setText("Ваш ход");
            } else {
                status.setText("Ход соперника: " + name(active));
            }
            status.setForeground(active != null && active != seat ? Theme.ink2() : Theme.ink());
        }
    }

    private void refreshHand(ReplayRecord.Snapshot s) {
        if (s == null) {
            return;
        }
        for (ReplayRecord.Player p : s.players) {
            if (p.seat != seat) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("монеты ").append(p.coin).append(" · келемий ").append(p.kelium)
                .append(" · боеприпасы ").append(p.ammo).append(" · трофеи ").append(p.trophy)
                .append('\n');
            sb.append("очки: ").append(p.vp.getOrDefault("total", 0)).append('\n');
            cards(sb, "Приказы", p.orderHand);
            cards(sb, "Задания", p.objectiveHand);
            cards(sb, "Арсенал", p.arsenalHand);
            if (p.orderSetAside != null) {
                sb.append("Отложен: ").append(cardName(p.orderSetAside)).append('\n');
            }
            hand.setText(sb.toString());
            hand.setCaretPosition(0);
        }
    }

    private void cards(StringBuilder sb, String title, List<String> ids) {
        sb.append(title).append(": ");
        if (ids.isEmpty()) {
            sb.append("нет");
        }
        for (int i = 0; i < ids.size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(cardName(ids.get(i)));
        }
        sb.append('\n');
    }

    private String cardName(String id) {
        String n = rec == null ? null : rec.cardNames.get(id);
        return n == null ? id : n;
    }

    @SuppressWarnings("unchecked")
    private void showQuestion(Map<String, Object> q) {
        if (finished) {
            return;
        }
        question = q;
        int seq = ((Number) q.get("seq")).intValue();
        String prompt = String.valueOf(q.get("prompt"));
        status.setText("Ваш ход — " + prompt);
        status.setForeground(Theme.accent());
        promptLabel.setText(prompt.substring(0, 1).toUpperCase() + prompt.substring(1));
        promptLabel.setForeground(Theme.ink());
        optionList.removeAll();
        Map<String, Integer> byHex = new LinkedHashMap<>();
        for (Object o : (List<Object>) q.get("options")) {
            Map<String, Object> opt = (Map<String, Object>) o;
            int i = ((Number) opt.get("i")).intValue();
            String label = clean(String.valueOf(opt.get("label")));
            String sub = opt.get("sub") == null ? "" : String.valueOf(opt.get("sub"));
            KpButton b = new KpButton(label, sub, null);
            b.setPreferredSize(new Dimension(Theme.px(380), Theme.px(sub.isBlank() ? 38 : 48)));
            b.setToolTipText(label + (sub.isBlank() ? "" : " — " + sub));
            b.onClick(() -> answer(seq, i));
            optionList.add(b, "growx, wrap");
            if (opt.get("hex") instanceof String h) {
                byHex.putIfAbsent(h, i);
            }
        }
        optionList.revalidate();
        optionList.repaint();
        refreshUndo(q.get("undo") instanceof Number n ? n.intValue() : 0);
        if (q.get("facing") instanceof Map<?, ?> fm && fm.get("hex") instanceof String fhex
                && fm.get("variants") instanceof List<?> vl) {
            // ВЫБОР СЕКТОРОВ НА ГЕКСЕ — как в окне партии: колесо вращает дугу,
            // щелчок по гексу ставит; кнопки остаются равноправным путём
            List<List<Integer>> variants = new ArrayList<>();
            for (Object v : vl) {
                List<Integer> sides = new ArrayList<>();
                for (Object o : (List<Object>) v) {
                    sides.add(((Number) o).intValue());
                }
                variants.add(sides);
            }
            if (fm.get("ghost") instanceof String ghost) {
                field.setGhost(ghost, seat);
            }
            field.clearSelectable();
            field.setFacingChoice(fhex, variants, idx -> answer(seq, idx));
        } else if (!byHex.isEmpty()) {
            field.setSelectable(byHex.keySet(), h -> {
                Integer i = byHex.get(h);
                if (i != null) {
                    answer(seq, i);
                }
            });
        } else {
            field.clearSelectable();
        }
    }

    /** Отменить своё решение: шаг назад или к началу хода. */
    private void undo(boolean all) {
        if (question == null || finished) {
            return;
        }
        int can = question.get("undo") instanceof Number k ? k.intValue() : 0;
        if (can < (all ? 2 : 1)) {
            return;
        }
        int seq = ((Number) question.get("seq")).intValue();
        client.undo(seq, all);
        clearQuestion();
        status.setText("Отмена — стол переигрывается…");
        status.setForeground(Theme.ink2());
    }

    /** Кнопки отмены живы, только когда есть что отменять. */
    private void refreshUndo(int n) {
        if (undoBtn == null) {
            return;
        }
        undoBtn.setState(n > 0 ? KpButton.State.AVAILABLE : KpButton.State.DISABLED);
        undoAllBtn.setState(n > 1 ? KpButton.State.AVAILABLE : KpButton.State.DISABLED);
        undoBtn.setTexts("Шаг назад", n > 0 ? "Ctrl+Z" : "нечего отменять");
        undoAllBtn.setTexts("К началу хода", n > 1 ? "шагов: " + n : "");
    }

    private void answer(int seq, int index) {
        if (question == null || ((Number) question.get("seq")).intValue() != seq) {
            return;
        }
        client.answer(seq, index);
        clearQuestion();
        status.setText("Ход принят — ждём стол…");
        status.setForeground(Theme.ink2());
    }

    private void clearQuestion() {
        question = null;
        if (optionList != null) {
            optionList.removeAll();
            optionList.revalidate();
            optionList.repaint();
            promptLabel.setText("Вопросов пока нет");
            promptLabel.setForeground(Theme.ink2());
            field.clearSelectable();
            field.clearFacingChoice();
            field.clearGhost();
            refreshUndo(0);
        }
    }

    /** Без внутренних кодов в скобках — как лента окна партии. */
    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("\\s*\\([a-z0-9_:>.\\-]+\\)", "").trim();
    }

    private String name(int s) {
        return s >= 0 && s < names.size() ? names.get(s) : "Игрок " + (s + 1);
    }

    private static JLabel label(String text, double size, int style, Color ink) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(size, style));
        l.setForeground(ink);
        return l;
    }

    private static JTextArea area() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(Theme.font(13, Font.PLAIN));
        a.setBackground(Theme.tile());
        a.setForeground(Theme.ink());
        a.setBorder(BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8), Theme.px(6),
            Theme.px(8)));
        return a;
    }

    private static JComponent titled(String title, JTextArea a) {
        JPanel p = new JPanel(new BorderLayout(0, Theme.px(4)));
        p.setOpaque(false);
        p.add(label(title, 12, Font.BOLD, Theme.ink3()), BorderLayout.NORTH);
        JScrollPane sc = new JScrollPane(a);
        sc.setBorder(BorderFactory.createLineBorder(Theme.border()));
        p.add(sc, BorderLayout.CENTER);
        return p;
    }
}
