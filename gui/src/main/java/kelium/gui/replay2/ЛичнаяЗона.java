package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * ЛичнаяЗона — ВСЁ ХОЗЯЙСТВО ИГРОКА: лента печатных планшетов и лист под ней.
 *
 * <p>Заказ дизайнера 11.09.2026: «отдельно необходимо сделать ленту
 * пролистывания полосы с планшетами; уметь зумить содержимое — не помещается на
 * экране». Отсюда и разделение на две части:
 *
 * <ul>
 *   <li>СВЕРХУ — {@link ПолосаПланшетов}: сцепка планшетов, стопка заданий и
 *       карта трофеев. У неё своя прокрутка вбок и своё увеличение, потому что
 *       печать разглядывают вблизи, а вся она в окно не влезает;</li>
 *   <li>СНИЗУ — {@link BoardSheet}: то, чего на печати нет вовсе, — здания
 *       списком, модули в запасе, отложенный приказ.</li>
 * </ul>
 *
 * <p>Прежде это был один лист, и прокрутка вбок таскала всё сразу: чтобы
 * увидеть правый край планшета, приходилось увозить за край экрана и карты, и
 * модули.
 */
public final class ЛичнаяЗона extends JPanel {

    private static final long serialVersionUID = 1L;

    private final ПолосаПланшетов лента;
    private final BoardSheet лист;
    private final JScrollPane лентаБокс;
    private final JPanel верх;
    private JLabel подписьЗума;

    public ЛичнаяЗона(Session session, int seat) {
        super(new BorderLayout());
        setBackground(Theme.bg());
        лента = new ПолосаПланшетов(session, seat);
        лист = new BoardSheet(session, seat);

        лентаБокс = new JScrollPane(лента,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        лентаБокс.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()));
        лентаБокс.getHorizontalScrollBar().setUnitIncrement(Theme.px(40));

        верх = new JPanel(new BorderLayout());
        верх.setBackground(Theme.bg());
        верх.add(лентаБокс, BorderLayout.CENTER);
        верх.add(полосаЗума(), BorderLayout.EAST);

        JScrollPane листБокс = new JScrollPane(лист);
        листБокс.setBorder(null);
        листБокс.getVerticalScrollBar().setUnitIncrement(Theme.px(24));

        add(верх, BorderLayout.NORTH);
        add(листБокс, BorderLayout.CENTER);
        лента.приЗуме(this::обновитьЗум);
        обновитьВысотуЛенты();
        session.whenRecordChanged(s -> обновитьВысотуЛенты());
    }

    /** Кнопки увеличения ленты: «мельче», сколько сейчас, «крупнее». */
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
        обновитьВысотуЛенты();
    }

    /**
     * ВЫСОТА ЛЕНТЫ — ПО САМОЙ ЛЕНТЕ, но не больше половины окна: планшеты
     * разглядывают, а не живут в них, и лист под ними должен оставаться виден.
     * Планшетов нет вовсе — ленты нет тоже.
     */
    private void обновитьВысотуЛенты() {
        boolean есть = лента.естьЧтоПоказать();
        if (верх.isVisible() != есть) {
            верх.setVisible(есть);
            revalidate();
        }
        if (!есть) {
            return;
        }
        int надо = лента.getPreferredSize().height
            + лентаБокс.getHorizontalScrollBar().getPreferredSize().height + Theme.px(6);
        // ПОТОЛОК — ДВЕ ТРЕТИ ОКНА: планшеты разглядывают, но лист под ними
        // должен оставаться виден. Пока окна нет (снимок в файл), потолка нет
        // тоже — иначе на снимке лента обрезалась бы посередине.
        int потолок = getHeight() > 0
            ? Math.max(Theme.px(240), getHeight() * 2 / 3) : надо;
        Dimension хочу = new Dimension(10, Math.min(надо, потолок));
        // РАЗМЕР СТАВИТСЯ ТОЛЬКО КОГДА ОН И ПРАВДА СМЕНИЛСЯ: revalidate внутри
        // раскладки иначе зацикливает её саму на себя.
        if (!хочу.equals(лентаБокс.getPreferredSize())) {
            лентаБокс.setPreferredSize(хочу);
            revalidate();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        обновитьВысотуЛенты();
    }

    public void setSeat(int seat) {
        лента.setSeat(seat);
        лист.setSeat(seat);
        обновитьВысотуЛенты();
    }

    public int seat() {
        return лист.seat();
    }

    /** Лист под лентой — прогонщикам и тестам. */
    public BoardSheet лист() {
        return лист;
    }
}
