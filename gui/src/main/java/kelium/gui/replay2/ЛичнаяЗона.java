package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * ЛичнаяЗона — ВСЁ ХОЗЯЙСТВО ИГРОКА.
 *
 * <p>Показывается ОДНО ИЗ ДВУХ, а не оба сразу:
 *
 * <ul>
 *   <li>есть печатные планшеты — во всю зону идёт {@link ПолосаПланшетов}:
 *       сцепка планшетов, карты в пазах, стопки заданий и супер-задания и
 *       отложенная карта приказа с трофеями. У неё своя прокрутка и своё
 *       увеличение, потому что печать разглядывают вблизи;</li>
 *   <li>печати нет (старая запись, чужой набор) — во всю зону идёт прежний
 *       рисованный лист {@link BoardSheet}.</li>
 * </ul>
 *
 * <p>Сперва они жили один над другим, но при печатных планшетах листу нечего
 * показывать: всё, что на нём было, либо напечатано, либо переехало в ленту, —
 * и внизу оставалась пустая полоса, которая вдобавок закрывала собой низ
 * (замечание дизайнера 11.09.2026: «убери вообще нижнюю часть, она же теперь
 * пустая»).
 *
 * <p>Сверху — узкая шапка: чей это планшет и сколько у него очков. Без неё при
 * ленте этого было бы негде взять.
 */
public final class ЛичнаяЗона extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final String ЛЕНТА = "лента";
    private static final String ЛИСТ = "лист";

    private final Session сессия;
    private final ПолосаПланшетов лента;
    private final BoardSheet лист;
    private final CardLayout что = new CardLayout();
    private final JPanel середина = new JPanel(что);
    private final JLabel имяМеста = new JLabel();
    private final JLabel очки = new JLabel();
    private JLabel подписьЗума;
    private String показано = "";

    public ЛичнаяЗона(Session session, int seat) {
        super(new BorderLayout());
        this.сессия = session;
        setBackground(Theme.bg());
        лист = new BoardSheet(session, seat);
        лента = new ПолосаПланшетов(session, seat, лист);

        JScrollPane лентаБокс = new JScrollPane(лента,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        лентаБокс.setBorder(null);
        лентаБокс.getHorizontalScrollBar().setUnitIncrement(Theme.px(40));
        лентаБокс.getVerticalScrollBar().setUnitIncrement(Theme.px(40));
        // ЯЩИК ТОГО ЖЕ ЦВЕТА, ЧТО И ЛЕНТА: светлое поле сбоку от планшетов
        // выглядело как часть картинки, хотя это просто пустой ящик.
        лентаБокс.setBackground(Theme.bg());
        лентаБокс.getViewport().setBackground(Theme.bg());

        JPanel сЛентой = new JPanel(new BorderLayout());
        сЛентой.setBackground(Theme.bg());
        сЛентой.add(лентаБокс, BorderLayout.CENTER);
        сЛентой.add(полосаЗума(), BorderLayout.EAST);

        JScrollPane листБокс = new JScrollPane(лист);
        листБокс.setBorder(null);
        листБокс.getVerticalScrollBar().setUnitIncrement(Theme.px(24));

        середина.setBackground(Theme.bg());
        середина.add(сЛентой, ЛЕНТА);
        середина.add(листБокс, ЛИСТ);

        add(шапка(), BorderLayout.NORTH);
        add(середина, BorderLayout.CENTER);

        лента.приЗуме(this::обновитьЗум);
        session.whenFrameChanged(s -> обновить());
        session.whenRecordChanged(s -> обновить());
        обновить();
    }

    /**
     * ШАПКА: чьё это хозяйство и сколько у него очков. Раньше эта строка стояла
     * на листе, но при печатных планшетах листа не видно вовсе.
     */
    private JPanel шапка() {
        JPanel ряд = new JPanel(new BorderLayout());
        ряд.setBackground(Theme.panel());
        ряд.setBorder(BorderFactory.createEmptyBorder(Theme.px(4), Theme.px(10),
            Theme.px(4), Theme.px(10)));
        имяМеста.setFont(Theme.font(12, Font.BOLD));
        очки.setFont(Theme.font(13, Font.BOLD));
        очки.setForeground(Theme.points());
        ряд.add(имяМеста, BorderLayout.WEST);
        ряд.add(очки, BorderLayout.EAST);
        return ряд;
    }

    /** Кнопки увеличения ленты: крупнее, мельче, обратно в натуральную. */
    private JPanel полосаЗума() {
        JPanel бок = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(4) + ", gapx " + Theme.px(2) + ", wrap 1", "[grow]"));
        бок.setBackground(Theme.panel());
        бок.add(кнопка("+", () -> лента.зум(лента.зум() * 1.25)), "growx");
        бок.add(кнопка("−", () -> лента.зум(лента.зум() / 1.25)), "growx");
        бок.add(кнопка("1:1", () -> лента.зум(1.0)), "growx");
        подписьЗума = new JLabel("100%", javax.swing.SwingConstants.CENTER);
        подписьЗума.setFont(Theme.font(10, Font.PLAIN));
        подписьЗума.setForeground(Theme.ink3());
        подписьЗума.setToolTipText(Ui2.tip("Увеличение ленты планшетов."
            + "\nКолесо с Ctrl над лентой — то же самое."));
        бок.add(подписьЗума, "growx");
        return бок;
    }

    private JButton кнопка(String текст, Runnable что) {
        JButton b = new JButton(текст);
        b.setFont(Theme.font(11, Font.BOLD));
        b.setFocusable(false);
        b.setMargin(new java.awt.Insets(0, Theme.px(4), 0, Theme.px(4)));
        b.addActionListener(e -> что.run());
        return b;
    }

    private void обновитьЗум() {
        if (подписьЗума != null) {
            подписьЗума.setText(Math.round(лента.зум() * 100) + "%");
        }
    }

    /** Что показывать сейчас и что написать в шапке. */
    private void обновить() {
        String надо = лента.естьЧтоПоказать() ? ЛЕНТА : ЛИСТ;
        if (!надо.equals(показано)) {
            показано = надо;
            что.show(середина, надо);
        }
        var запись = сессия.record();
        int место = лист.seat();
        имяМеста.setText(запись == null ? "" : запись.playerName(место));
        имяМеста.setForeground(Theme.seatInk(место));
        var кадр = сессия.frame();
        if (кадр == null || кадр.snapshot == null
                || место >= кадр.snapshot.players.size()) {
            очки.setText("");
            return;
        }
        очки.setText(кадр.snapshot.players.get(место).vp.getOrDefault("total", 0) + " ПО");
    }

    public void setSeat(int seat) {
        лента.setSeat(seat);
        лист.setSeat(seat);
        обновить();
    }

    public int seat() {
        return лист.seat();
    }

    /** Рисованный лист — прогонщикам и тестам. */
    public BoardSheet лист() {
        return лист;
    }
}
