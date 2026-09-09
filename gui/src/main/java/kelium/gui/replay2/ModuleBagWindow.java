package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import kelium.report.ReplayRecord;

/**
 * КАТАЛОГ МЕШОЧКА С ЖЕТОНАМИ МОДУЛЕЙ.
 *
 * <p>Заказ дизайнера 08.09.2026: два мешочка, красный и синий, рядом с прочими
 * каталогами; щёлкнул — открылось окно, в нём жетоны картинками, рядом описание
 * «что этот жетончик даёт», и кнопка перевернуть на ЗОЛОТУЮ сторону.
 *
 * <p>ПЕРЕВОРАЧИВАЕТСЯ ВЕСЬ КАТАЛОГ СРАЗУ, а не отдельная карточка. Так видно
 * главное: чем золотая сторона отличается от обычной у ВСЕГО мешка — ради этого
 * сравнения каталог и открывают. Нужна одна карточка покрупнее — щёлкни по ней,
 * откроется её подробное окно ({@link ModuleZoom}).
 */
public final class ModuleBagWindow {

    private ModuleBagWindow() {
    }

    /** Показать каталог: {@code красный} — какой из двух мешков открыт. */
    static void show(Window owner, ReplayRecord record, boolean красный) {
        List<МешокМодулей.Мешок> мешки = МешокМодулей.мешки(record);
        МешокМодулей.Мешок мешок = null;
        for (var m : мешки) {
            if (m.красный() == красный) {
                мешок = m;
            }
        }
        JDialog d = new JDialog(owner, красный ? "Красный мешок — прокачка атаки"
            : "Синий мешок — прокачка найма", java.awt.Dialog.ModalityType.MODELESS);
        d.setLayout(new BorderLayout());
        if (мешок == null) {
            JLabel нет = new JLabel("Наборы модулей для этих правил не найдены.",
                javax.swing.SwingConstants.CENTER);
            нет.setForeground(Theme.ink2());
            нет.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
            d.add(нет, BorderLayout.CENTER);
            готово(d, owner);
            return;
        }

        Сетка сетка = new Сетка(мешок);
        JScrollPane прокрутка = new JScrollPane(сетка.вВерх());
        прокрутка.setBorder(BorderFactory.createEmptyBorder());
        прокрутка.getVerticalScrollBar().setUnitIncrement(24);
        прокрутка.getViewport().setBackground(Theme.bg());

        JPanel шапка = new JPanel(new BorderLayout(10, 0));
        шапка.setOpaque(true);
        шапка.setBackground(Theme.panel());
        шапка.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JLabel заголовок = new JLabel(мешок.id() + " — жетонов в мешке " + мешок.всего()
            + ", видов " + мешок.виды().size());
        заголовок.setForeground(Theme.ink2());
        заголовок.setFont(Theme.font(12, Font.BOLD));
        JButton перевернуть = new JButton("Перевернуть на золотую сторону");
        перевернуть.addActionListener(e -> {
            сетка.золото = !сетка.золото;
            перевернуть.setText(сетка.золото ? "Перевернуть на обычную сторону"
                : "Перевернуть на золотую сторону");
            сетка.перечитать();
        });
        шапка.add(заголовок, BorderLayout.WEST);
        шапка.add(перевернуть, BorderLayout.EAST);

        d.add(шапка, BorderLayout.NORTH);
        d.add(прокрутка, BorderLayout.CENTER);
        d.getContentPane().setBackground(Theme.bg());
        d.setSize((int) (940 * Theme.effectiveScale()), (int) (720 * Theme.effectiveScale()));
        готово(d, owner);
    }

    /**
     * СОДЕРЖИМОЕ КАТАЛОГА ОТДЕЛЬНО ОТ ОКНА — нужно снимку. Живое окно показывать
     * нельзя (правило 30.08.2026: прогонщики не забирают фокус), а проверять
     * раскладку карточек надо, поэтому та же сетка собирается в память.
     */
    public static JComponent панель(ReplayRecord record, boolean красный, boolean золото) {
        for (var m : МешокМодулей.мешки(record)) {
            if (m.красный() == красный) {
                Сетка с = new Сетка(m);
                с.золото = золото;
                с.перечитать();
                return с.вВерх();
            }
        }
        JLabel нет = new JLabel("наборов модулей нет");
        нет.setForeground(Theme.ink3());
        return нет;
    }

    private static void готово(JDialog d, Window owner) {
        if (d.getWidth() == 0) {
            d.pack();
        }
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    /** Сетка карточек «картинка + описание». */
    private static final class Сетка extends JPanel {

        private static final long serialVersionUID = 1L;

        private final МешокМодулей.Мешок мешок;
        boolean золото;

        /**
         * ОБЁРТКА, ПРИЖИМАЮЩАЯ СЕТКУ КВЕРХУ. {@link GridLayout} делит ВСЮ
         * высоту между рядами поровну: на высоком окне шесть карточек
         * растягивались каждая на треть экрана, и под текстом зияла пустота.
         */
        JPanel вВерх() {
            JPanel обёртка = new JPanel(new BorderLayout());
            обёртка.setBackground(Theme.bg());
            обёртка.add(this, BorderLayout.NORTH);
            return обёртка;
        }

        Сетка(МешокМодулей.Мешок мешок) {
            super(new GridLayout(0, 2, 12, 12));
            this.мешок = мешок;
            setBackground(Theme.bg());
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            перечитать();
        }

        void перечитать() {
            removeAll();
            for (МешокМодулей.Вид в : мешок.виды()) {
                add(карточка(в));
            }
            revalidate();
            repaint();
        }

        private JComponent карточка(МешокМодулей.Вид в) {
            JPanel card = new JPanel(new BorderLayout(12, 0));
            card.setBackground(Theme.panel());
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.border()),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

            ЖетонВид пик = new ЖетонВид(в, () -> золото);
            card.add(пик, BorderLayout.WEST);

            JPanel текст = new JPanel();
            текст.setOpaque(false);
            текст.setLayout(new BoxLayout(текст, BoxLayout.Y_AXIS));
            JLabel имя = new JLabel(МешокМодулей.имя(в)
                + (в.копий() > 1 ? "   ×" + в.копий() : ""));
            имя.setFont(Theme.font(13, Font.BOLD));
            имя.setForeground(в.красный() ? ModuleSlot.red() : ModuleSlot.blue());
            имя.setAlignmentX(0f);
            JTextArea что = new JTextArea(МешокМодулей.чтоДаёт(в, золото));
            что.setEditable(false);
            что.setLineWrap(true);
            что.setWrapStyleWord(true);
            что.setOpaque(false);
            что.setFont(Theme.font(12, Font.PLAIN));
            что.setForeground(Theme.ink());
            что.setAlignmentX(0f);
            текст.add(имя);
            текст.add(Box.createVerticalStrut(6));
            текст.add(что);
            card.add(текст, BorderLayout.CENTER);

            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    ModuleZoom.show(SwingUtilities.getWindowAncestor(Сетка.this), в, золото);
                }
            });
            card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            return card;
        }
    }

    /**
     * КАРТИНКА ЖЕТОНА — печатная, с блоком тени. Печати нет (жетоны
     * характеристик ещё не нарисованы) — рисуется прежний цветной квадратик,
     * чтобы каталог не зиял пустотой.
     */
    static final class ЖетонВид extends JComponent {

        private static final long serialVersionUID = 1L;

        private final МешокМодулей.Вид вид;
        private final java.util.function.BooleanSupplier золото;
        private final int сторона;

        ЖетонВид(МешокМодулей.Вид вид, java.util.function.BooleanSupplier золото) {
            this(вид, золото, 118);
        }

        ЖетонВид(МешокМодулей.Вид вид, java.util.function.BooleanSupplier золото, int сторона) {
            this.вид = вид;
            this.золото = золото;
            this.сторона = (int) Math.round(сторона * Theme.effectiveScale());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(сторона + 8, сторона + 8);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            kelium.report.Сглаживание.включить(g);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            boolean gold = золото.getAsBoolean();
            Color край = вид.красный() ? ModuleSlot.red() : ModuleSlot.blue();
            BufferedImage art = вид.арт(gold);
            double s = сторона;
            if (!kelium.report.ModuleArt.paint(g, art, край, 2, 2, s)) {
                ReplayRecord.Module m = new ReplayRecord.Module();
                m.id = вид.id();
                m.gold = gold;
                ModuleSlot.paint(g, m, край, 2, 2, s);
            }
            g.dispose();
        }
    }
}
