package kelium.gui.replay2;

import java.awt.Dimension;
import java.awt.Font;
import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * ScaleDialog — СВОЙ МАСШТАБ ИНТЕРФЕЙСА: ползунок, чтобы подобрать на глаз, и
 * поле с числом, чтобы поставить ровно столько же, сколько в прошлый раз.
 *
 * <p>Число видно ВСЕГДА и в двух видах: проценты понятны без объяснений, а
 * коэффициент — это ровно то, что лежит в {@code kelium.cfg}. Увидел ×1,15 —
 * знаешь, что искать в файле настроек и что вписать на другой машине.
 */
public final class ScaleDialog {

    private ScaleDialog() {
    }

    /** Показать окно. Вернёт долю (1.0 = 100 %) или null, если человек передумал. */
    public static Double show(java.awt.Component parent, double current) {
        int start = (int) Math.round(Math.max(0.6, Math.min(2.0, current)) * 100);

        JSlider slider = new JSlider(60, 200, start);
        slider.setMajorTickSpacing(20);
        slider.setMinorTickSpacing(5);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setFont(Theme.font(10, Font.PLAIN));
        slider.setPreferredSize(new Dimension(Theme.px(380),
            slider.getPreferredSize().height));

        JSpinner spin = new JSpinner(new SpinnerNumberModel(start, 60, 200, 1));
        spin.setFont(Theme.mono(15, Font.BOLD));
        spin.setPreferredSize(new Dimension(Theme.px(80),
            spin.getPreferredSize().height));

        JLabel coeff = new JLabel();
        coeff.setFont(Theme.body());
        Runnable sync = () -> coeff.setText("коэффициент  ×" + String.format(
            Locale.forLanguageTag("ru"), "%.2f",
            ((Number) spin.getValue()).intValue() / 100.0));

        // Ползунок и поле — ОДНО И ТО ЖЕ ЧИСЛО: правка в любом видна в другом.
        // Флажок нужен, чтобы взаимные обновления не гоняли друг друга по кругу.
        boolean[] busy = {false};
        slider.addChangeListener(e -> {
            if (!busy[0]) {
                busy[0] = true;
                spin.setValue(slider.getValue());
                busy[0] = false;
                sync.run();
            }
        });
        spin.addChangeListener(e -> {
            if (!busy[0]) {
                busy[0] = true;
                slider.setValue(((Number) spin.getValue()).intValue());
                busy[0] = false;
                sync.run();
            }
        });
        sync.run();

        JPanel box = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets " + Theme.px(12) + ", gapy " + Theme.px(8), "[grow,fill]"));
        box.add(Ui2.label("Общий размер интерфейса: шрифтов, плиток, отступов, пульта."),
            "wrap");
        box.add(slider, "wrap");

        JPanel row = new JPanel(new net.miginfocom.swing.MigLayout(
            "insets 0, gapx " + Theme.px(8)));
        row.setOpaque(false);
        row.add(spin);
        row.add(Ui2.label("%"));
        row.add(coeff, "gapx " + Theme.px(18));
        box.add(row, "wrap");

        JLabel note = new JLabel("<html>Обычный — 100 %. Экран пересобирается сразу, "
            + "партия и место в ней не теряются. Настройка помнится между "
            + "запусками.</html>");
        note.setFont(Theme.note(11));
        note.setForeground(Theme.ink3());
        box.add(note, "wrap");

        int ok = JOptionPane.showConfirmDialog(parent, box, "Свой масштаб интерфейса",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return ok == JOptionPane.OK_OPTION
            ? ((Number) spin.getValue()).intValue() / 100.0 : null;
    }
}
