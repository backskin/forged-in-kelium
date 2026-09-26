package kelium.gui.net;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.miginfocom.swing.MigLayout;

import kelium.dataio.AppSettings;
import kelium.gui.HotSeatWindow;
import kelium.gui.kp.KpButton;
import kelium.gui.replay2.Theme;
import kelium.report.ReplayRecord;

/**
 * ОКНО «СЕТЕВОЙ СТОЛ» — вход из «Штаба», режим «По сети».
 *
 * <p>Два пути на одном экране: «Создать стол» (стол — как собран в «Штабе»:
 * число мест, правила, поле, цвета; живые места открываются для друзей) и
 * «Войти к другу» по адресу. Дальше — лобби: места, готовность, чат, «Начать».
 * Хост после старта играет в обычном окне партии, у друга открывается
 * {@link NetClientWindow}. Само окно лобби остаётся у хоста: в нём видно,
 * кто на связи, и идёт чат; закрыть его — закрыть стол.
 */
public final class LobbyWindow {

    private static final AppSettings SET = AppSettings.of("net");
    private static final String DEFAULT_BOT = "builder:2";

    private final HotSeatWindow.Options table;
    private final JFrame menu;
    private JFrame frame;
    private JTextField nameField;
    private JPanel body;

    // ---- лобби ----
    private NetHost host;
    private NetClient client;
    private JPanel seatList;
    private JTextArea chatArea;
    private JLabel status;
    private JLabel addressLine;
    private JLabel otherAddresses;
    private String copyText;
    private KpButton mainBtn;
    private boolean myReady;
    private volatile Map<String, Object> lastLobby;
    private volatile NetClientWindow game;

    private LobbyWindow(HotSeatWindow.Options table, JFrame menu) {
        this.table = table;
        this.menu = menu;
    }

    /**
     * Открыть из «Штаба». {@code table} — стол, как он собран в меню (null —
     * только «Войти к другу»); {@code menu} — окно «Штаба», закроется при
     * старте партии.
     */
    public static void open(HotSeatWindow.Options table, JFrame menu) {
        SwingUtilities.invokeLater(() -> new LobbyWindow(table, menu).start());
    }

    public static void main(String[] args) {
        Theme.useGameScale();
        open(null, null);
    }

    private void start() {
        Theme.applyTable();
        frame = new JFrame("Кристаллы Раздора — сетевой стол");
        frame.setDefaultCloseOperation(menu == null ? WindowConstants.EXIT_ON_CLOSE
            : WindowConstants.DISPOSE_ON_CLOSE);
        frame.getContentPane().setBackground(Theme.bg());
        frame.getContentPane().setLayout(new BorderLayout());
        frame.add(head(), BorderLayout.NORTH);
        body = new JPanel(new BorderLayout());
        body.setBackground(Theme.bg());
        frame.add(body, BorderLayout.CENTER);
        showEntry();
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (host != null) {
                    host.close();
                }
                if (client != null && game == null) {
                    client.close();
                }
            }
        });
        frame.setSize(Theme.px(900), Theme.px(700));
        frame.setLocationRelativeTo(menu);
        frame.setVisible(true);
    }

    private JComponent head() {
        JPanel bar = new JPanel(new MigLayout("insets " + Theme.px(12) + " " + Theme.px(16)
            + ", fillx", "[][grow][]"));
        bar.setBackground(Theme.panel());
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, Theme.px(2), 0, Theme.border()));
        bar.add(label("СЕТЕВОЙ СТОЛ", 20, Font.BOLD, Theme.ink()));
        bar.add(label("играть с друзьями на разных компьютерах", 14, Font.PLAIN, Theme.ink2()),
            "gapleft " + Theme.px(16));
        JPanel nm = new JPanel(new MigLayout("insets 0", "[][" + Theme.px(200) + "!]"));
        nm.setOpaque(false);
        nm.add(label("ваше имя", 14, Font.PLAIN, Theme.ink2()));
        nameField = field(SET.get("name", System.getProperty("user.name", "Игрок")));
        nm.add(nameField, "growx");
        bar.add(nm);
        return bar;
    }

    // ==================== вход ====================

    private void showEntry() {
        JPanel p = new JPanel(new MigLayout("insets " + Theme.px(24) + ", gap " + Theme.px(24)
            + ", fill", "[grow,fill][grow,fill]", "[top]"));
        p.setBackground(Theme.bg());

        JPanel create = card("СОЗДАТЬ СТОЛ");
        JTextField port = field(String.valueOf(SET.getInt("port", NetProtocol.DEFAULT_PORT)));
        create.add(label("порт", 14, Font.PLAIN, Theme.ink2()), "split 2, growx 0");
        create.add(port, "w " + Theme.px(120) + "!, growx 0, wrap");
        create.add(multiline(table == null
            ? "Стол собирается в «Штабе»: закройте это окно, выберите число мест, правила "
                + "и поле, затем снова «По сети»."
            : tableWords(table)), "growx, wrap");
        KpButton go = new KpButton("Создать стол", "друзья войдут по адресу", null).primary(true);
        go.setPreferredSize(new Dimension(Theme.px(260), Theme.px(48)));
        go.setState(table == null ? KpButton.State.DISABLED : KpButton.State.AVAILABLE);
        go.onClick(() -> createTable(port.getText()));
        create.add(go, "gaptop " + Theme.px(12));
        p.add(create);

        JPanel join = card("ВОЙТИ К ДРУГУ");
        join.add(label("адрес стола (его показывает хост)", 14, Font.PLAIN, Theme.ink2()), "wrap");
        JTextField addr = field(SET.get("address", ""));
        join.add(addr, "growx, wrap");
        join.add(multiline("В одной сети — адрес вида 192.168.1.5:47100. Через интернет "
            + "без настройки роутера — поставьте оба Radmin VPN или ZeroTier и возьмите "
            + "адрес из него."), "growx, wrap");
        KpButton in = new KpButton("Войти", "к столу друга", null).primary(true);
        in.setPreferredSize(new Dimension(Theme.px(260), Theme.px(48)));
        in.onClick(() -> joinTable(addr.getText()));
        join.add(in, "gaptop " + Theme.px(12));
        p.add(join);

        status = label(" ", 15, Font.PLAIN, Theme.ink2());
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.bg());
        wrap.add(p, BorderLayout.CENTER);
        JPanel foot = new JPanel(new MigLayout("insets " + Theme.px(8) + " " + Theme.px(24)));
        foot.setBackground(Theme.bg());
        foot.add(status);
        wrap.add(foot, BorderLayout.SOUTH);
        setBody(wrap);
    }

    private static String tableWords(HotSeatWindow.Options t) {
        long open = t.seatSpecs().stream().filter("human"::equals).count() - 1;
        return t.players() + " места · правила " + t.rulesetId()
            + (t.scenarioId() == null ? "" : " · поле " + t.scenarioId())
            + ". Мест для друзей: " + Math.max(0, open)
            + " (остальные — боты; в лобби любое место можно отдать боту или открыть).";
    }

    private String myName() {
        String n = nameField.getText().trim();
        if (n.isEmpty()) {
            n = "Игрок";
        }
        SET.put("name", n);
        return n;
    }

    private void createTable(String portText) {
        int port;
        try {
            port = Integer.parseInt(portText.trim());
        } catch (NumberFormatException e) {
            status.setText("Порт — число, например " + NetProtocol.DEFAULT_PORT);
            return;
        }
        NetHost h = new NetHost(table, myName());
        try {
            port = h.listen(port);
        } catch (java.io.IOException e) {
            status.setText("Порт " + port + " занят или закрыт: " + e.getMessage());
            status.setForeground(Theme.bad());
            return;
        }
        SET.putInt("port", port);
        host = h;
        int p = port;
        h.onChange = () -> SwingUtilities.invokeLater(() -> renderLobby(h.lobbyMessage()));
        h.onChat = line -> SwingUtilities.invokeLater(() -> addChat(line));
        h.log = line -> SwingUtilities.invokeLater(() -> addChat("· " + line));
        showLobby();
        List<String> addrs = NetHost.localAddresses();
        List<String> full = new ArrayList<>();
        for (String a : addrs) {
            full.add(a + ":" + p);
        }
        copyText = full.get(0);
        addressLine.setText("Адрес для друзей: " + full.get(0));
        otherAddresses.setText(full.size() < 2 ? " "
            : "другие адреса этого компьютера (другая сеть, Radmin/ZeroTier): "
                + String.join(" · ", full.subList(1, full.size())));
        renderLobby(h.lobbyMessage());
    }

    private void joinTable(String address) {
        if (address.isBlank()) {
            status.setText("Впишите адрес, который показал хост");
            return;
        }
        SET.put("address", address.trim());
        String name = myName();
        NetClient c = new NetClient(name, new NetClient.Listener() {
            @Override
            public void welcome(int seat, int players, String hostName) {
                SwingUtilities.invokeLater(() -> {
                    if (seatList == null) {
                        showLobby();
                    }
                    addressLine.setText("Стол у " + hostName + " · ваше место " + (seat + 1));
                });
            }

            @Override
            public void reject(String reason) {
                NetClientWindow g = game;
                if (g != null) {
                    g.rejected(reason);      // посреди партии: место отдано боту
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    client = null;
                    if (seatList != null) {
                        seatList = null;
                        showEntry();
                    }
                    status.setText("Не пустили: " + reason);
                    status.setForeground(Theme.bad());
                });
            }

            @Override
            public void lobby(Map<String, Object> l) {
                SwingUtilities.invokeLater(() -> renderLobby(l));
            }

            @Override
            public void chat(String from, String text) {
                SwingUtilities.invokeLater(() -> addChat(from + ": " + text));
                NetClientWindow g = game;
                if (g != null) {
                    g.chat(from + ": " + text);
                }
            }

            @Override
            public void paused(int seat, String name, boolean waiting) {
                NetClientWindow g = game;
                if (g != null) {
                    g.paused(seat, name, waiting);
                }
            }

            @Override
            public void resumed(int seat, String how) {
                NetClientWindow g = game;
                if (g != null) {
                    g.resumed(seat, how);
                }
            }

            @Override
            public void closed() {
                NetClientWindow g = game;
                if (g != null) {
                    g.closed();
                }
            }

            @Override
            public void start(int seat, List<String> names) {
                NetClientWindow g = game;
                if (g == null) {
                    g = new NetClientWindow(client, seat, names, LobbyWindow.this::backFromGame);
                    game = g;
                    g.show();
                    SwingUtilities.invokeLater(() -> {
                        frame.setVisible(false);
                        if (menu != null) {
                            menu.dispose();
                        }
                    });
                }
            }

            @Override
            public void record(ReplayRecord part, boolean reset, int from) {
                NetClientWindow g = game;
                if (g != null) {
                    g.record(part, reset, from);
                }
            }

            @Override
            public void decide(Map<String, Object> q) {
                NetClientWindow g = game;
                if (g != null) {
                    g.decide(q);
                }
            }

            @Override
            public void over(Map<String, Object> r) {
                NetClientWindow g = game;
                if (g != null) {
                    g.over(r);
                }
            }

            @Override
            public void error(String text) {
                SwingUtilities.invokeLater(() -> addChat("! " + text));
            }

            @Override
            public void disconnected() {
                NetClientWindow g = game;
                if (g != null) {
                    g.disconnected();
                } else {
                    SwingUtilities.invokeLater(() -> {
                        client = null;
                        seatList = null;
                        showEntry();
                        status.setText("Связь с хостом оборвалась");
                    });
                }
            }
        });
        status.setText("Подключаемся к " + address.trim() + "…");
        client = c;
        new Thread(() -> {
            try {
                c.connect(address);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    client = null;
                    status.setText("Не подключиться к " + address.trim() + ": " + e.getMessage()
                        + ". Проверьте адрес и что у хоста стол создан (брандмауэр — «Разрешить»).");
                    status.setForeground(Theme.bad());
                });
            }
        }, "net-connect").start();
    }

    private void backFromGame() {
        SwingUtilities.invokeLater(() -> frame.dispose());
    }

    // ==================== лобби ====================

    private void showLobby() {
        JPanel p = new JPanel(new MigLayout("insets " + Theme.px(20) + ", fill, gap " + Theme.px(10),
            "[grow,fill]", "[][][grow,fill][]"));
        p.setBackground(Theme.bg());
        addressLine = label(" ", 16, Font.BOLD, Theme.accent());
        KpButton copy = new KpButton("Скопировать", "адрес — отправьте другу", null);
        copy.setPreferredSize(new Dimension(Theme.px(220), Theme.px(44)));
        copy.onClick(() -> {
            if (copyText != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(copyText), null);
                copy.setTexts("Скопировано", copyText);
            }
        });
        JPanel top = new JPanel(new MigLayout("insets 0, fillx, gapy 0", "[grow][]"));
        top.setOpaque(false);
        top.add(addressLine);
        if (host != null) {
            top.add(copy, "spany 2, wrap, w " + Theme.px(240) + "!, h " + Theme.px(50) + "!");
        } else {
            top.add(new JLabel(), "wrap");
        }
        otherAddresses = label(" ", 12.5, Font.PLAIN, Theme.ink3());
        top.add(otherAddresses);
        p.add(top, "wrap");

        seatList = new JPanel(new MigLayout("insets 0, fillx, gapy " + Theme.px(6), "[grow,fill]"));
        seatList.setOpaque(false);
        JPanel seatCard = card("МЕСТА ЗА СТОЛОМ");
        seatCard.add(seatList, "growx");
        p.add(seatCard, "wrap");

        JPanel chatCard = card("ЧАТ");
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(Theme.font(14, Font.PLAIN));
        chatArea.setBackground(Theme.tile());
        chatArea.setForeground(Theme.ink());
        JScrollPane sc = new JScrollPane(chatArea);
        sc.setBorder(BorderFactory.createLineBorder(Theme.border()));
        chatCard.add(sc, "grow, push, wrap, h " + Theme.px(120) + ":" + Theme.px(200) + ":");
        JTextField say = field("");
        say.addActionListener(e -> {
            String t = say.getText().trim();
            if (!t.isEmpty()) {
                if (host != null) {
                    host.say(t);
                } else if (client != null) {
                    client.chat(t);
                }
                say.setText("");
            }
        });
        chatCard.add(say, "split 2, growx");
        chatCard.add(label("Enter — отправить", 12, Font.PLAIN, Theme.ink3()));
        p.add(chatCard, "grow, wrap");

        JPanel foot = new JPanel(new MigLayout("insets 0, fillx", "[grow][]"));
        foot.setOpaque(false);
        status = label(" ", 15, Font.PLAIN, Theme.ink2());
        foot.add(status);
        mainBtn = new KpButton(host != null ? "Начать партию" : "Готов", "", null).primary(true);
        mainBtn.setPreferredSize(new Dimension(Theme.px(260), Theme.px(52)));
        mainBtn.onClick(this::mainAction);
        foot.add(mainBtn, "w " + Theme.px(280) + "!, h " + Theme.px(54) + "!");
        p.add(foot);
        setBody(p);
    }

    private void mainAction() {
        if (host != null) {
            if (host.whyNotStart() != null || host.started()) {
                return;
            }
            HotSeatWindow.Options o = host.begin();
            if (menu != null) {
                menu.dispose();
            }
            mainBtn.setTexts("Партия идёт", "окно лобби — связь и чат; закрыть — закрыть стол");
            mainBtn.setState(KpButton.State.DISABLED);
            HotSeatWindow.open(o);
        } else if (client != null) {
            myReady = !myReady;
            client.ready(myReady);
            mainBtn.setTexts(myReady ? "Не готов" : "Готов",
                myReady ? "ждём остальных" : "нажмите, когда будете готовы");
        }
    }

    @SuppressWarnings("unchecked")
    private void renderLobby(Map<String, Object> l) {
        lastLobby = l;
        if (seatList == null) {
            return;
        }
        seatList.removeAll();
        int mine = client != null ? client.seat() : host != null ? host.hostSeat() : -1;
        for (Object o : (List<Object>) l.getOrDefault("seats", List.of())) {
            Map<String, Object> s = (Map<String, Object>) o;
            seatList.add(seatRow(s, mine), "growx, wrap");
        }
        seatList.revalidate();
        seatList.repaint();
        if (host != null && mainBtn != null && !host.started()) {
            String why = host.whyNotStart();
            mainBtn.setState(why == null ? KpButton.State.AVAILABLE : KpButton.State.DISABLED);
            mainBtn.setTexts("Начать партию", why == null ? "все на местах" : "");
            status.setText(why == null ? "Можно начинать" : why);
            status.setForeground(why == null ? Theme.good() : Theme.ink2());
        } else if (client != null && status != null) {
            boolean can = Boolean.TRUE.equals(l.get("canStart"));
            status.setText(can ? "Все готовы — ждём, когда хост начнёт" : "Ждём остальных");
        }
    }

    private JComponent seatRow(Map<String, Object> s, int mine) {
        int seat = ((Number) s.get("seat")).intValue();
        int color = s.get("color") instanceof Number n ? n.intValue() : seat;
        String kind = String.valueOf(s.get("kind"));
        String name = s.get("name") == null ? null : String.valueOf(s.get("name"));
        boolean ready = Boolean.TRUE.equals(s.get("ready"));
        boolean online = Boolean.TRUE.equals(s.get("online"));

        JPanel row = new JPanel(new MigLayout("insets " + Theme.px(8) + " " + Theme.px(10)
            + ", fillx", "[" + Theme.px(22) + "!][" + Theme.px(90) + "!][grow][][]"));
        row.setBackground(seat == mine ? Theme.seatWash(color, 0.16) : Theme.tile());
        row.setBorder(BorderFactory.createLineBorder(seat == mine ? Theme.seat(color)
            : Theme.border()));
        row.add(new Dot(Theme.seat(color), !"open".equals(kind)));
        row.add(label("Место " + (seat + 1), 15, Font.BOLD, Theme.ink2()));
        String who = switch (kind) {
            case "host" -> name + " (хост)";
            case "bot" -> "Бот · " + name;
            case "open" -> "свободно";
            default -> name;
        };
        if (seat == mine) {
            who += " — вы";
        }
        row.add(label(who, 16, "open".equals(kind) ? Font.PLAIN : Font.BOLD,
            "open".equals(kind) ? Theme.ink3() : Theme.ink()));
        String state = switch (kind) {
            case "open" -> "ждём друга";
            case "remote" -> !online ? "нет связи" : ready ? "готов" : "не готов";
            default -> "готов";
        };
        row.add(label(state, 14, Font.PLAIN, "remote".equals(kind) && !online ? Theme.bad()
            : ready ? Theme.good() : Theme.ink2()));
        JPanel acts = new JPanel(new MigLayout("insets 0, gap " + Theme.px(6)));
        acts.setOpaque(false);
        boolean started = Boolean.TRUE.equals(lastLobby == null ? null : lastLobby.get("started"));
        if (host != null && !host.started()) {
            if ("open".equals(kind)) {
                acts.add(small("Боту", () -> host.setBot(seat, DEFAULT_BOT)));
            }
            if ("bot".equals(kind) || "remote".equals(kind)) {
                acts.add(small("Открыть", () -> host.setOpen(seat)));
            }
        } else if (client != null && !started && "open".equals(kind)) {
            acts.add(small("Сесть сюда", () -> client.takeSeat(seat)));
        }
        row.add(acts);
        return row;
    }

    private void addChat(String line) {
        if (chatArea == null) {
            return;
        }
        chatArea.append(line + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // ==================== мелочи оформления ====================

    private void setBody(JComponent c) {
        body.removeAll();
        body.add(c, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    private static JLabel label(String text, double size, int style, Color ink) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(size, style));
        l.setForeground(ink);
        return l;
    }

    private static JTextField field(String text) {
        JTextField f = new JTextField(text);
        f.setFont(Theme.font(15, Font.PLAIN));
        f.setBackground(Theme.tile());
        f.setForeground(Theme.ink());
        f.setCaretColor(Theme.ink());
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8), Theme.px(6), Theme.px(8))));
        return f;
    }

    private static JComponent multiline(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setOpaque(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setFont(Theme.font(14, Font.PLAIN));
        a.setForeground(Theme.ink2());
        a.setBorder(BorderFactory.createEmptyBorder(Theme.px(8), 0, 0, 0));
        return a;
    }

    private static JPanel card(String title) {
        JPanel c = new JPanel(new MigLayout("insets " + Theme.px(16) + ", fillx", "[grow,fill]"));
        c.setBackground(Theme.panel());
        c.setBorder(BorderFactory.createLineBorder(Theme.border()));
        c.add(label(title, 13, Font.BOLD, Theme.ink3()), "wrap, gapbottom " + Theme.px(8));
        return c;
    }

    private static KpButton small(String title, Runnable r) {
        KpButton b = new KpButton(title, "", null);
        b.setPreferredSize(new Dimension(Theme.px(120), Theme.px(34)));
        b.onClick(r);
        return b;
    }

    /** Кружок цвета места: сплошной — место занято, контур — свободно. */
    private static final class Dot extends JComponent {
        private final Color c;
        private final boolean filled;

        Dot(Color c, boolean filled) {
            this.c = c;
            this.filled = filled;
            setPreferredSize(new Dimension(Theme.px(18), Theme.px(18)));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - 2;
            g.setColor(c);
            if (filled) {
                g.fillOval(1, 1, d, d);
            } else {
                g.setStroke(new java.awt.BasicStroke(Theme.pxf(1.6)));
                g.drawOval(1, 1, d, d);
            }
            g.dispose();
        }
    }
}
