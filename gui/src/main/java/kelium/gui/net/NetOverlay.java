package kelium.gui.net;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import kelium.gui.kp.KpButton;
import kelium.gui.replay2.Theme;

/**
 * ШТОРКА СЕТЕВОЙ ПАУЗЫ — поверх окна партии (и у хоста, и у игрока):
 * полупрозрачная тёмная подложка во всё окно и карточка по центру —
 * заголовок, пояснение и кнопки (у хоста три выхода, у игроков — ни одной
 * или «Выйти из игры»).
 *
 * <p>Под шторкой ничего не нажимается: мышь глотает подложка, клавиатуру —
 * перехватчик окна. Исключение — то, что разрешено {@link #allow} (чат:
 * пока ждём, поговорить можно).
 */
final class NetOverlay extends JComponent {

    /** Кнопка карточки. {@code active} — выбранный сейчас вариант («ждём»). */
    record Action(String title, String sub, Runnable run, boolean primary, boolean active) {
    }

    private final JFrame frame;
    private final JPanel card;
    private final JLabel title;
    private final JTextArea text;
    private final JPanel buttons;
    private final List<Component> allowed = new ArrayList<>();
    private final KeyEventDispatcher keys;

    NetOverlay(JFrame frame) {
        this.frame = frame;
        setOpaque(false);
        setVisible(false);
        setLayout(new GridBagLayout());
        card = new JPanel(new MigLayout("insets " + Theme.px(24) + " " + Theme.px(28)
            + ", fillx, gapy " + Theme.px(10), "[grow,fill]"));
        card.setBackground(Theme.panel());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(Theme.px(4), 0, 0, 0, Theme.accent()),
            BorderFactory.createLineBorder(Theme.border())));
        title = new JLabel(" ");
        title.setFont(Theme.font(22, Font.BOLD));
        title.setForeground(Theme.ink());
        card.add(title, "wrap");
        text = new JTextArea();
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.font(16, Font.PLAIN));
        text.setForeground(Theme.ink2());
        text.setBorder(BorderFactory.createEmptyBorder());
        card.add(text, "growx, w " + Theme.px(500) + "!, wrap");
        buttons = new JPanel(new MigLayout("insets 0, fillx, gapy " + Theme.px(8), "[grow,fill]"));
        buttons.setOpaque(false);
        card.add(buttons, "growx, gaptop " + Theme.px(8));
        card.setPreferredSize(new Dimension(Theme.px(560), card.getPreferredSize().height));
        add(card);

        // подложка глотает мышь: под шторкой ничего не нажимается
        MouseAdapter swallow = new MouseAdapter() { };
        addMouseListener(swallow);
        addMouseMotionListener(swallow);
        addMouseWheelListener(e -> { });

        JLayeredPane lp = frame.getLayeredPane();
        lp.add(this, Integer.valueOf(JLayeredPane.DRAG_LAYER + 10));
        setBounds(0, 0, lp.getWidth(), lp.getHeight());
        lp.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                setBounds(0, 0, lp.getWidth(), lp.getHeight());
                revalidate();
            }
        });

        // клавиатура окна — тоже мимо, кроме карточки и разрешённого (чат)
        keys = e -> isVisible() && e.getComponent() != null
            && SwingUtilities.getWindowAncestor(e.getComponent()) == frame
            && !SwingUtilities.isDescendingFrom(e.getComponent(), this)
            && allowed.stream().noneMatch(a -> SwingUtilities.isDescendingFrom(e.getComponent(), a));
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
            }
        });
    }

    /** Не гасить под шторкой этот компонент (чат). */
    void allow(Component c) {
        allowed.add(c);
    }

    /** Показать (или обновить) карточку. Поток интерфейса. */
    void display(String head, String body, List<Action> actions) {
        title.setText(head);
        text.setText(body);
        buttons.removeAll();
        for (Action a : actions) {
            KpButton b = new KpButton(a.title(), a.sub(), null).primary(a.primary());
            b.setPreferredSize(new Dimension(Theme.px(500), Theme.px(52)));
            b.setState(a.active() ? KpButton.State.ACTIVE : KpButton.State.AVAILABLE);
            b.onClick(a.run());
            buttons.add(b, "growx, h " + Theme.px(52) + "!, wrap");
        }
        buttons.setVisible(!actions.isEmpty());
        card.setPreferredSize(null);
        Dimension d = card.getPreferredSize();
        card.setPreferredSize(new Dimension(Theme.px(560), d.height));
        setVisible(true);
        revalidate();
        repaint();
    }

    /** Убрать шторку. Поток интерфейса. */
    void dismiss() {
        setVisible(false);
    }

    /** Показана ли шторка (для тестов). */
    boolean shown() {
        return isVisible();
    }

    /** Заголовок карточки (для тестов). */
    String titleText() {
        return title.getText();
    }

    /** Кнопки карточки (для тестов). */
    List<KpButton> buttonList() {
        List<KpButton> out = new ArrayList<>();
        for (Component c : buttons.getComponents()) {
            if (c instanceof KpButton b) {
                out.add(b);
            }
        }
        return out;
    }

    @Override
    protected void paintComponent(Graphics g) {
        // затенение игры: тёмная полупрозрачная подложка во всё окно
        g.setColor(Theme.alpha(Theme.darken(Theme.bg(), 0.75), 0.68));
        g.fillRect(0, 0, getWidth(), getHeight());
    }
}
