package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;

/**
 * BoardWindow — ОТДЕЛЬНОЕ ОКНО С ПЛАНШЕТОМ ИГРОКА.
 *
 * <p>Дизайнер просил именно выносное окно (13.08.2026): планшет удобно держать на
 * втором мониторе и смотреть на него, пока партия листается в главном окне. Оно
 * живёт своей жизнью, но следит за тем же разбором: перемотал партию — планшет
 * обновился.
 *
 * <p>Окно ОДНО на все места: сверху переключатель игроков. Открывается кнопкой на
 * полосе игрока в главном окне, закрывается как обычное окно, положение и размер
 * запоминаются самой системой окон.
 */
public final class BoardWindow {

    private static BoardWindow open;

    private final JFrame frame;
    private final BoardSheet sheet;
    private final JToggleButton[] tabs;

    private BoardWindow(Session session, int seat, Window owner) {
        sheet = new BoardSheet(session, seat);
        frame = new JFrame("Планшет игрока");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        JPanel top = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(6) + " " + Theme.px(10) + " " + Theme.px(6) + " "
                + Theme.px(10) + ", gapx " + Theme.px(4)));
        top.setBackground(Theme.panel());
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        ButtonGroup group = new ButtonGroup();
        int players = session.record() == null ? 4 : session.record().players;
        tabs = new JToggleButton[players];
        for (int i = 0; i < players; i++) {
            final int s = i;
            JToggleButton b = new JToggleButton(name(session, i));
            b.setFont(Theme.font(12, Font.BOLD));
            b.setForeground(Theme.seat(i));
            b.setFocusable(false);
            b.setSelected(i == seat);
            b.addActionListener(e -> sheet.setSeat(s));
            group.add(b);
            tabs[i] = b;
            top.add(b);
        }
        frame.add(top, BorderLayout.NORTH);

        JScrollPane sc = new JScrollPane(sheet);
        sc.setBorder(null);
        sc.getVerticalScrollBar().setUnitIncrement(Theme.px(24));
        frame.add(sc, BorderLayout.CENTER);

        frame.setSize(new Dimension(Theme.px(1000), Theme.px(720)));
        frame.setMinimumSize(new Dimension(Theme.px(720), Theme.px(520)));
        frame.setLocationRelativeTo(owner);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                open = null;
            }
        });
    }

    private static String name(Session session, int seat) {
        if (session.record() == null || seat >= session.record().seatLabels.size()) {
            return "Игрок " + (seat + 1);
        }
        return (seat + 1) + " · " + session.record().seatLabels.get(seat);
    }

    /**
     * Показать планшет места. Окно ОДНО: второй вызов не плодит копии, а
     * переключает уже открытое и поднимает его наверх.
     */
    public static void show(Session session, int seat, Window owner) {
        if (open == null) {
            open = new BoardWindow(session, seat, owner);
        }
        open.sheet.setSeat(seat);
        if (seat < open.tabs.length) {
            open.tabs[seat].setSelected(true);
        }
        open.frame.setVisible(true);
        open.frame.toFront();
        open.frame.requestFocus();
    }

    /** Перекрасить открытое окно при смене темы. */
    public static void restyle() {
        if (open != null) {
            com.formdev.flatlaf.FlatLaf.updateUI();
            open.frame.repaint();
        }
    }
}
