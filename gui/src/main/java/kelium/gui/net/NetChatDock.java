package kelium.gui.net;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;

import net.miginfocom.swing.MigLayout;

import kelium.gui.kp.KpButton;
import kelium.gui.replay2.Theme;

/**
 * ЧАТ В ОКНЕ ПАРТИИ — компактный, в углу окна (и у хоста, и у игрока).
 *
 * <p>Свёрнутый — одна кнопка «Чат». Пришло новое сообщение, а чат свёрнут —
 * кнопка загорается и пишет в подписи, сколько новых и само последнее
 * сообщение: его видно, не открывая чата. Развёрнутый — лента сообщений и
 * строка ввода (Enter — отправить). Лежит выше шторки паузы: пока ждём
 * вышедшего игрока, поговорить можно.
 */
final class NetChatDock extends JPanel {

    private static final int GAP = 12;

    private final JFrame frame;
    private final Consumer<String> send;
    private final KpButton toggle;
    private final JPanel open;
    private final JTextArea history;
    private final JTextField input;
    private boolean expanded;
    private int unread;
    private String last;
    private java.awt.Component anchor;

    NetChatDock(JFrame frame, Consumer<String> send) {
        super(new BorderLayout());
        this.frame = frame;
        this.send = send;
        setOpaque(false);

        toggle = new KpButton("Чат", "написать соперникам", null);
        toggle.onClick(() -> setExpanded(!expanded));

        open = new JPanel(new MigLayout("insets " + Theme.px(10) + ", fill, gap " + Theme.px(6),
            "[grow,fill][]", "[][grow,fill][]"));
        open.setBackground(Theme.panel());
        open.setBorder(BorderFactory.createLineBorder(Theme.border()));
        JLabel head = new JLabel("ЧАТ");
        head.setFont(Theme.font(14, Font.BOLD));
        head.setForeground(Theme.ink3());
        open.add(head);
        KpButton hide = new KpButton("Свернуть", "", null);
        hide.setPreferredSize(new Dimension(Theme.px(120), Theme.px(32)));
        hide.onClick(() -> setExpanded(false));
        open.add(hide, "h " + Theme.px(32) + "!, w " + Theme.px(120) + "!, wrap");
        history = new JTextArea();
        history.setEditable(false);
        history.setLineWrap(true);
        history.setWrapStyleWord(true);
        history.setFont(Theme.font(15, Font.PLAIN));
        history.setBackground(Theme.tile());
        history.setForeground(Theme.ink());
        history.setBorder(BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8), Theme.px(6),
            Theme.px(8)));
        JScrollPane sc = new JScrollPane(history, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sc.setBorder(BorderFactory.createLineBorder(Theme.border()));
        open.add(sc, "span 2, grow, wrap");
        input = new JTextField();
        input.setFont(Theme.font(15, Font.PLAIN));
        input.setBackground(Theme.tile());
        input.setForeground(Theme.ink());
        input.setCaretColor(Theme.ink());
        input.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.border()),
            BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(8), Theme.px(6), Theme.px(8))));
        input.addActionListener(e -> {
            String t = input.getText().trim();
            if (!t.isEmpty()) {
                send.accept(t);
                input.setText("");
            }
        });
        // Esc в строке ввода — свернуть чат
        input.registerKeyboardAction(e -> setExpanded(false),
            javax.swing.KeyStroke.getKeyStroke("ESCAPE"), WHEN_FOCUSED);
        open.add(input, "span 2, growx");
        JLabel hint = new JLabel("Enter — отправить");
        hint.setFont(Theme.font(12, Font.PLAIN));
        hint.setForeground(Theme.ink3());
        open.add(hint, "newline, span 2");

        add(toggle, BorderLayout.CENTER);

        JLayeredPane lp = frame.getLayeredPane();
        lp.add(this, Integer.valueOf(JLayeredPane.DRAG_LAYER + 20));
        lp.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                place();
            }
        });
        place();
    }

    /** Новая строка «кто: что». Поток интерфейса. */
    void add(String line) {
        history.append(line + "\n");
        history.setCaretPosition(history.getDocument().getLength());
        if (!expanded) {
            unread++;
            last = line;
            refreshToggle();
        }
    }

    /** Открыт ли чат (для тестов). */
    boolean expanded() {
        return expanded;
    }

    /** Весь текст ленты чата (для тестов). */
    String historyText() {
        return history.getText();
    }

    void setExpanded(boolean on) {
        expanded = on;
        removeAll();
        add(on ? open : toggle, BorderLayout.CENTER);
        if (on) {
            unread = 0;
            last = null;
            refreshToggle();
            input.requestFocusInWindow();
        }
        place();
        revalidate();
        repaint();
    }

    private void refreshToggle() {
        if (unread > 0) {
            toggle.setTexts("Чат · новых: " + unread, last);
            toggle.primary(true);
        } else {
            toggle.setTexts("Чат", "написать соперникам");
            toggle.primary(false);
        }
        place();
    }

    /**
     * Стоять над этим компонентом, а не у нижнего края окна: в окне партии
     * хоста снизу стол игрока — чат встаёт над ним, в угол поля.
     */
    void anchorAbove(java.awt.Component c) {
        anchor = c;
        c.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                place();
            }

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                place();
            }
        });
        place();
    }

    /** В левом нижнем углу окна (или над опорой); развёрнутый — выше и шире. */
    private void place() {
        JLayeredPane lp = frame.getLayeredPane();
        int w = expanded ? Theme.px(460) : Theme.px(unread > 0 ? 420 : 200);
        int h = expanded ? Theme.px(340) : Theme.px(52);
        int g = Theme.px(GAP);
        int bottom = lp.getHeight();
        int left = g;
        if (anchor != null && anchor.getParent() != null && anchor.isShowing()) {
            java.awt.Point p = javax.swing.SwingUtilities.convertPoint(anchor.getParent(),
                anchor.getLocation(), lp);
            bottom = p.y;
            left = p.x + g;
        }
        setBounds(left, Math.max(g, bottom - h - g), w, h);
    }
}
