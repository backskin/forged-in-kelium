package kelium.gui.replay2;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import kelium.report.ReplayRecord;

/**
 * ПОДРОБНОСТИ ОДНОГО ЖЕТОНА МОДУЛЯ — увеличенная картинка и полное описание.
 *
 * <p>Заказ дизайнера 08.09.2026: «нажимаешь на жетон — открывается модальное
 * окно, подробности этого жетона с этой картинкой, но увеличенной». Открывается
 * и из каталога мешка, и щелчком по жетону, лежащему на планшете игрока.
 *
 * <p>ПЕРЕВОРОТ ЗДЕСЬ ЖЕ. Жетон на столе двусторонний, и вопрос «а что будет,
 * если его позолотить» задают, глядя именно на него, — поэтому кнопка стоит
 * рядом с картинкой, а не только в каталоге.
 */
final class ModuleZoom {

    private ModuleZoom() {
    }

    /** Показать вид из каталога мешка. */
    static void show(Window owner, МешокМодулей.Вид вид, boolean золото) {
        собрать(owner, вид, золото, null);
    }

    /**
     * Показать жетон, лежащий на планшете игрока.
     *
     * @param где   человеческое имя места («техника», «завод»)
     * @param красный красный жетон (иначе синий)
     */
    static void show(Window owner, ReplayRecord.Module m, boolean красный, String где) {
        if (m == null || m.id == null || m.id.isBlank()) {
            return;
        }
        собрать(owner, null, m.gold, new Лежит(m, красный, где));
    }

    private record Лежит(ReplayRecord.Module m, boolean красный, String где) {
    }

    private static void собрать(Window owner, МешокМодулей.Вид вид, boolean золото,
                                Лежит лежит) {
        boolean красный = вид != null ? вид.красный() : лежит.красный();
        JDialog d = new JDialog(owner, красный ? "Красный жетон модуля"
            : "Синий жетон модуля", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] gold = {золото};

        JPanel корень = new JPanel(new BorderLayout(16, 12));
        корень.setBackground(Theme.bg());
        корень.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        javax.swing.JComponent картинка = вид != null
            ? new ModuleBagWindow.ЖетонВид(вид, () -> gold[0], 280)
            : new ЖетонСоСтола(лежит.m(), красный, () -> gold[0]);
        корень.add(картинка, BorderLayout.WEST);

        JLabel имя = new JLabel(вид != null ? МешокМодулей.имя(вид)
            : лежит.m().id + " · " + лежит.где());
        имя.setFont(Theme.font(16, Font.BOLD));
        имя.setForeground(красный ? ModuleSlot.red() : ModuleSlot.blue());

        JTextArea что = new JTextArea(текст(вид, лежит, gold[0]));
        что.setEditable(false);
        что.setLineWrap(true);
        что.setWrapStyleWord(true);
        что.setOpaque(false);
        что.setFont(Theme.font(13, Font.PLAIN));
        что.setForeground(Theme.ink());

        JPanel право = new JPanel(new BorderLayout(0, 10));
        право.setOpaque(false);
        право.add(имя, BorderLayout.NORTH);
        право.add(что, BorderLayout.CENTER);

        JButton перевернуть = new JButton(подписьКнопки(gold[0]));
        перевернуть.addActionListener(e -> {
            gold[0] = !gold[0];
            перевернуть.setText(подписьКнопки(gold[0]));
            что.setText(текст(вид, лежит, gold[0]));
            картинка.repaint();
        });
        JPanel низ = new JPanel(new BorderLayout());
        низ.setOpaque(false);
        низ.add(перевернуть, BorderLayout.WEST);
        право.add(низ, BorderLayout.SOUTH);

        корень.add(право, BorderLayout.CENTER);
        d.setContentPane(корень);
        d.pack();
        d.setSize(Math.max(d.getWidth(), (int) (760 * Theme.effectiveScale())), d.getHeight());
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    private static String подписьКнопки(boolean gold) {
        return gold ? "Показать обычную сторону" : "Перевернуть на золотую сторону";
    }

    private static String текст(МешокМодулей.Вид вид, Лежит лежит, boolean gold) {
        if (вид != null) {
            return МешокМодулей.описание(вид, gold)
                + (вид.копий() > 1 ? "\n\nВ мешке таких жетонов: " + вид.копий() + "." : "");
        }
        // ЖЕТОН СО СТОЛА описывается тем же текстом, что и подсказка планшета:
        // два разных описания одного жетона рано или поздно разойдутся.
        ReplayRecord.Module m = лежит.m();
        boolean было = m.gold;
        m.gold = gold;
        String s = ModuleSlot.describe(m, лежит.красный(), лежит.где());
        m.gold = было;
        return s + (gold != было
            ? "\n\n(показана другая сторона — на столе жетон лежит "
                + (было ? "золотой" : "обычной") + " стороной)"
            : "");
    }

    /** Картинка жетона, лежащего на планшете. */
    private static final class ЖетонСоСтола extends javax.swing.JComponent {

        private static final long serialVersionUID = 1L;

        private final ReplayRecord.Module m;
        private final boolean красный;
        private final java.util.function.BooleanSupplier золото;
        private final int сторона = (int) Math.round(280 * Theme.effectiveScale());

        ЖетонСоСтола(ReplayRecord.Module m, boolean красный,
                     java.util.function.BooleanSupplier золото) {
            this.m = m;
            this.красный = красный;
            this.золото = золото;
            setOpaque(false);
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            return new java.awt.Dimension(сторона + 8, сторона + 8);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g0) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
            kelium.report.Сглаживание.включить(g);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            boolean было = m.gold;
            m.gold = золото.getAsBoolean();
            java.awt.Color край = красный ? ModuleSlot.red() : ModuleSlot.blue();
            if (!ModuleSlot.картинка(g, m, край, 2, 2, сторона)) {
                ModuleSlot.paint(g, m, край, 2, 2, сторона);
            }
            m.gold = было;
            g.dispose();
        }
    }
}
